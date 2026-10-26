package local.sliverwolf.web;

import android.content.Context;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;

/** Adapts the upstream SpeakerIdentification example; extractor/manager stay upstream. */
final class OwnerSpeaker implements AutoCloseable {
    static final float THRESHOLD=.6f;
    private final SpeakerEmbeddingExtractor extractor;
    private final Vad vad;
    int speechSamples;
    float[] acceptedEmbedding;
    String verificationReason="not_checked";
    OwnerSpeaker(Context context)throws Exception {
        File model=asset(context,"speaker.onnx",OwnerVoiceStore.MODEL);
        File silence=asset(context,"silero_vad.onnx","2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f");
        LibraryLoader.setAutoLoadEnabled(false);System.loadLibrary("sherpa-onnx-jni");
        extractor=new SpeakerEmbeddingExtractor(SpeakerEmbeddingExtractorConfig.builder().setModel(model.toString()).setNumThreads(1).setDebug(false).build());
        try{vad=new Vad(VadModelConfig.builder().setSileroVadModelConfig(SileroVadModelConfig.builder().setModel(silence.toString())
            .setMinSpeechDuration(.25f).setMaxSpeechDuration(12).build()).setNumThreads(1).setDebug(false).build());}
        catch(RuntimeException|LinkageError failure){extractor.release();throw failure;}
    }
    private static File asset(Context context,String name,String hash)throws Exception {
        File root=new File(context.getFilesDir(),"owner-models");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Owner model storage unavailable");
        File file=new File(root,name);
        if(!file.isFile()||!PetStore.hash(file).equals(hash)) {
            File part=new File(root,name+".part");
            try(InputStream in=context.getAssets().open("wake/"+name);FileOutputStream out=new FileOutputStream(part)) {
                byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();
            }
            if(!PetStore.hash(part).equals(hash)||!part.renameTo(file))throw new IOException("Owner model integrity failed");
        }
        return file;
    }
    float[] embedding(float[] pcm,int minimumSpeechSamples)throws Exception {
        if(pcm.length>192000)throw new IOException("Owner sample exceeds 12 seconds");
        speechSamples=0;vad.reset();
        for(int offset=0;offset<pcm.length;offset+=512)vad.acceptWaveform(Arrays.copyOfRange(pcm,offset,Math.min(pcm.length,offset+512)));
        vad.flush();List<float[]> parts=new ArrayList<>();int count=0;
        while(!vad.empty()){float[] part=vad.front().getSamples();parts.add(part);count+=part.length;vad.pop();}
        speechSamples=count;
        if(count<minimumSpeechSamples)throw new InsufficientSpeech();
        float[] speech=new float[count];int cursor=0;for(float[] part:parts){System.arraycopy(part,0,speech,cursor,part.length);cursor+=part.length;}
        OnlineStream stream=extractor.createStream();
        try {
            stream.acceptWaveform(speech,16000);stream.inputFinished();
            if(!extractor.isReady(stream))throw new InsufficientSpeech();
            return extractor.compute(stream);
        }finally{stream.release();}
    }
    float[][] background(Context context)throws Exception {
        String[] names={"speaker1_a_cn_16k.wav","speaker1_b_cn_16k.wav","speaker2_a_cn_16k.wav"};
        float[][] result=new float[names.length][];
        for(int i=0;i<names.length;i++) {
            File temp=File.createTempFile("public-background-",".wav",context.getCacheDir());
            try {
                try(InputStream in=context.getAssets().open("wake/"+names[i]);FileOutputStream out=new FileOutputStream(temp)) {
                    byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
                }
                WaveReader wave=new WaveReader(temp.toString());
                if(wave.getSampleRate()!=16000)throw new IOException("Background sample rate mismatch");
                result[i]=embedding(wave.getSamples(),12000);
            }finally{temp.delete();}
        }
        return result;
    }
    boolean matches(float[][] owner,float[] voice)throws Exception {
        if(owner.length!=3)throw new IOException("Owner enrollment is incomplete");
        SpeakerEmbeddingManager manager=new SpeakerEmbeddingManager(extractor.getDim());
        try {
            for(float[] vector:owner)if(vector.length!=extractor.getDim())throw new IOException("Owner embedding model mismatch");
            if(!manager.add("owner",owner))throw new IOException("Cannot load owner embeddings");
            return manager.verify("owner",voice,THRESHOLD);
        }finally{manager.release();}
    }
    boolean verify(OwnerBackend.Predictor owner,float[] pcm)throws Exception {
        try {
            acceptedEmbedding=null;float[] voice=embedding(pcm,12000);
            boolean accepted=owner.accepts(voice);if(accepted)acceptedEmbedding=voice;
            verificationReason=accepted?"accepted":"speaker_mismatch";return accepted;
        }catch(InsufficientSpeech insufficient){verificationReason="insufficient_speech";return false;}
    }
    boolean verify(float[][] owner,float[] pcm)throws Exception {
        try {
            boolean accepted=matches(owner,embedding(pcm,12000));
            verificationReason=accepted?"accepted":"speaker_mismatch";
            return accepted;
        }catch(InsufficientSpeech notEnough){verificationReason="insufficient_speech";return false;}
    }
    void checkEnrollment(float[][] vectors,float[] heldOut)throws Exception {
        for(int i=0;i<3;i++)if(!matches(new float[][]{vectors[i],vectors[i],vectors[i]},vectors[(i+1)%3]))
            throw new IOException("多次朗读的声音不一致，请在安静环境重新录入");
        if(!matches(vectors,heldOut))throw new IOException("唤醒短句未通过主人声纹验证，请重新录入");
    }
    static final class InsufficientSpeech extends IOException {InsufficientSpeech(){super("有效人声太短，请清楚朗读提示内容");}}
    @Override public void close(){vad.release();extractor.release();}
}
