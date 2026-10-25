package local.sliverwolf.assistant;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** One serial owner for native inference and PCM; callbacks run after microphone release. */
final class WakeDetector {
    interface Listener {void ready(String phrase);void detected();void rejected(String reason,int speechSamples);void failed(String reason);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile int generation;
    private volatile boolean busy;
    volatile long samplesRead;
    volatile double peakRms;
    volatile String ownerProfile="long_readings";
    private KeywordSpotter spotter;
    private OwnerSpeaker speaker;
    private float[][] background;
    volatile long ownerFitMillis;
    volatile int learnedSamples;
    volatile String learningStatus="idle";
    private float[] pendingSample;
    private OwnerVoiceStore pendingStore;
    private int pendingEpoch;
    private String engineName="";
    private Set<String> vocabulary;
    WakeDetector(Context context){this.context=context.getApplicationContext();}
    boolean busy(){return busy;}
    void stop(){generation++;}
    void start(String name,Listener listener) {
        final int epoch=++generation;busy=true;samplesRead=0;peakRms=0;
        worker.execute(()->{
            AudioRecord recorder=null;OnlineStream stream=null;boolean hit=false;String error=null;
            try {
                pendingSample=null;pendingStore=null;prepare(name);WakePhrase phrase=WakePhrase.compile(name,vocabulary);
                float[][] owner=new OwnerVoiceStore(context).load();ownerProfile="long_readings";
                OwnerVoiceStore shortProfile=OwnerVoiceStore.wake(context,name);
                if(shortProfile.exists()) {
                    try{owner=shortProfile.load();ownerProfile="wake_phrase";}catch(OwnerVoiceStore.BindingMismatch renamed){/* Keep the original owner profile after a rename. */}
                }
                if(speaker==null)speaker=new OwnerSpeaker(context);
                if(background==null)background=speaker.background(context);
                float[][] anchors=owner;
                OwnerVoiceStore learnedStore=OwnerVoiceStore.learned(context,name);
                float[][] learned=new float[0][];
                if(learnedStore.exists())try{learned=learnedStore.load();}catch(OwnerVoiceStore.BindingMismatch changed){}
                learnedSamples=learned.length;
                float[][] training=Arrays.copyOf(owner,owner.length+learned.length);
                System.arraycopy(learned,0,training,owner.length,learned.length);
                OwnerBackend.Adapted adapted=OwnerBackend.adapt(training,background);
                ownerProfile+="/"+adapted.name;ownerFitMillis=adapted.elapsedNanos/1000000;
                if(epoch!=generation)return;
                stream=spotter.createStream();
                int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(minimum<=0)throw new IOException("16 kHz microphone format unavailable");
                recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,
                    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(6400,minimum*2));
                if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone initialization failed");
                recorder.startRecording();
                if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Microphone did not start");
                main.post(()->{if(epoch==generation)listener.ready(phrase.display);});
                short[] pcm=new short[1600];float[] recent=new float[64000];int cursor=0,filled=0;
                while(epoch==generation) {
                    int count=recorder.read(pcm,0,pcm.length);
                    if(count<=0)throw new IOException("Microphone read failed: "+count);
                    float[] samples=new float[count];double energy=0;
                    for(int i=0;i<count;i++){samples[i]=pcm[i]/32768f;energy+=samples[i]*samples[i];}
                    samplesRead+=count;peakRms=Math.max(peakRms,Math.sqrt(energy/count));
                    for(float sample:samples){recent[cursor]=sample;cursor=(cursor+1)%recent.length;filled=Math.min(recent.length,filled+1);}
                    stream.acceptWaveform(samples,16000);
                    if(decodeReady(spotter,stream)) {
                        // KWS can emit before the utterance ends. Keep 600 ms of trailing
                        // microphone audio for speaker verification, without feeding KWS again.
                        for(int remaining=9600;remaining>0&&epoch==generation;) {
                            int tail=recorder.read(pcm,0,Math.min(pcm.length,remaining));
                            if(tail<=0)throw new IOException("Microphone tail read failed: "+tail);
                            remaining-=tail;samplesRead+=tail;
                            for(int i=0;i<tail;i++){recent[cursor]=pcm[i]/32768f;cursor=(cursor+1)%recent.length;filled=Math.min(recent.length,filled+1);}
                        }
                        if(epoch!=generation)break;
                        float[] candidate=new float[filled];int start=(cursor-filled+recent.length)%recent.length;
                        for(int i=0;i<filled;i++)candidate[i]=recent[(start+i)%recent.length];
                        if(speaker.verify(adapted.predictor,candidate)){
                            // Admission is stricter than wake recognition and uses unchanged enrollment anchors.
                            if(speaker.speechSamples>=12000&&learningAudioQuality(candidate)&&speaker.matches(anchors,speaker.acceptedEmbedding)) {
                                pendingSample=speaker.acceptedEmbedding.clone();pendingStore=learnedStore;pendingEpoch=epoch;learningStatus="awaiting_command";
                            }else learningStatus="below_learning_confidence";
                            hit=true;break;
                        }
                        final String rejection=speaker.verificationReason;final int speechCount=speaker.speechSamples;
                        main.post(()->{if(epoch==generation)listener.rejected(rejection,speechCount);});
                        spotter.reset(stream);filled=0;cursor=0;
                    }
                }
            } catch(Exception|LinkageError failure){error=failure.getClass().getSimpleName()+": "+failure.getMessage();}
            finally {
                if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();}
                if(stream!=null)stream.release();busy=false;
                final boolean detected=hit;final String reason=error;
                main.post(()->{if(epoch!=generation)return;if(reason!=null)listener.failed(reason);else if(detected)listener.detected();});
            }
        });
    }
    static boolean learningAudioQuality(float[] pcm) {
        if(pcm.length<12000)return false;
        int clipped=0;for(float value:pcm){if(!Float.isFinite(value))return false;if(Math.abs(value)>=.99f)clipped++;}
        return clipped*100L<=pcm.length;
    }
    void confirmLearning() {
        worker.execute(()->{
            if(pendingSample==null||pendingEpoch!=generation)return;
            float[] sample=pendingSample;OwnerVoiceStore store=pendingStore;pendingSample=null;pendingStore=null;
            try {
                float[][] previous=new float[0][];
                if(store.exists())try{previous=store.load();}catch(OwnerVoiceStore.BindingMismatch changed){}
                for(float[] old:previous) {
                    double[] a=OwnerBackend.normalized(old),b=OwnerBackend.normalized(sample);double similarity=0;
                    for(int i=0;i<a.length;i++)similarity+=a[i]*b[i];
                    if(similarity>.995){learningStatus="duplicate_skipped";return;}
                }
                int keep=Math.min(previous.length,8);float[][] next=new float[keep+1][];
                System.arraycopy(previous,previous.length-keep,next,0,keep);next[keep]=sample;
                store.save(next);learnedSamples=next.length;learningStatus="sample_saved";
            }catch(Exception error){learningStatus="save_failed";}
        });
    }
    // Follow upstream SherpaOnnxKws: inspect the result after every decode step.
    static boolean decodeReady(KeywordSpotter engine,OnlineStream stream) {
        while(engine.isReady(stream)) {
            engine.decode(stream);
            if(!engine.getResult(stream).getKeyword().isEmpty())return true;
        }
        return false;
    }
    private void prepare(String agentName)throws Exception {
        if(spotter!=null&&engineName.equals(agentName))return;
        if(spotter!=null){spotter.release();spotter=null;}
        File root=new File(context.getFilesDir(),"wake-1.13.8");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Wake storage unavailable");
        JSONObject hashes=new JSONObject(readAsset("files.json"));
        for(String name:new String[]{"encoder.onnx","decoder.onnx","joiner.onnx","tokens.txt"}) {
            File file=new File(root,name);String hash=hashes.getString(name);
            if(!file.isFile()||!PetStore.hash(file).equals(hash)) {
                File part=new File(root,name+".part");
                try(InputStream in=context.getAssets().open("wake/"+name);FileOutputStream out=new FileOutputStream(part)) {
                    byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();
                }
                if(!PetStore.hash(part).equals(hash)||!part.renameTo(file))throw new IOException("Wake model integrity/activation failed");
            }
        }
        vocabulary=new HashSet<>();for(String line:new String(Files.readAllBytes(new File(root,"tokens.txt").toPath()),StandardCharsets.UTF_8).split("\n"))
            vocabulary.add(line.trim().split("\\s+")[0]);
        WakePhrase phrase=WakePhrase.compile(agentName,vocabulary);
        File keywords=new File(root,"keywords.txt");
        Files.write(keywords.toPath(),(phrase.encoded+"\n").getBytes(StandardCharsets.UTF_8));
        LibraryLoader.setAutoLoadEnabled(false);System.loadLibrary("sherpa-onnx-jni");
        OnlineTransducerModelConfig transducer=OnlineTransducerModelConfig.builder()
            .setEncoder(new File(root,"encoder.onnx").toString()).setDecoder(new File(root,"decoder.onnx").toString())
            .setJoiner(new File(root,"joiner.onnx").toString()).build();
        OnlineModelConfig model=OnlineModelConfig.builder().setTransducer(transducer)
            .setTokens(new File(root,"tokens.txt").toString()).setNumThreads(1).setDebug(false).build();
        spotter=new KeywordSpotter(KeywordSpotterConfig.builder().setOnlineModelConfig(model).setKeywordsFile(keywords.toString()).build());
        engineName=agentName;
    }
    private String readAsset(String name)throws IOException {
        try(InputStream in=context.getAssets().open("wake/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    void close(){stop();worker.execute(()->{if(spotter!=null){spotter.release();spotter=null;}if(speaker!=null){speaker.close();speaker=null;}});worker.shutdown();}
}
