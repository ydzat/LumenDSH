package local.sliverwolf.web;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.IOException;
import java.util.concurrent.*;

/** Explicit foreground enrollment; raw audio lives only in bounded memory buffers. */
final class OwnerEnrollment {
    interface Listener {void progress(String message);void complete(float[][] vectors);void failed(String reason);default void comparison(String report){}}
    static volatile boolean active;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    OwnerEnrollment(Context context){this.context=context.getApplicationContext();}
    void start(String agentName,Listener listener) {start(agentName,false,false,listener);}
    void start(String agentName,boolean wakeCalibration,Listener listener) {start(agentName,wakeCalibration,false,listener);}
    void start(String agentName,boolean wakeCalibration,boolean comparison,Listener listener) {
        active=true;
        worker.execute(()->{
            try(OwnerSpeaker speaker=new OwnerSpeaker(context)) {
                float[][] background=speaker.background(context);
                int trainingCount=comparison?4:3,total=comparison?6:4;
                String[] prompts={"这是我的语音助手，我希望它只回应我的声音。","现在我正在录入声纹，请记住我自然说话的声音。",
                    "嗨，"+agentName+"。请准备好，听到我的呼唤时回应。","嗨，"+agentName};
                float[][] vectors=new float[trainingCount][],validation=new float[total-trainingCount][];long recorded=0;
                for(int i=0;i<total;i++) {
                    check();String phrase=(comparison||wakeCalibration)?"嗨，"+agentName:prompts[i];
                    String message=(i<trainingCount?"训练录入 "+(i+1)+" / "+trainingCount:"独立验证 "+(i-trainingCount+1)+" / "+validation.length)
                        +" · 请自然朗读一次\n\n"+phrase+"\n\n本段采集 6 秒，读完后保持安静。";
                    main.post(()->{if(!cancelled)listener.progress("准备下一段，请先保持安静，等朗读文字出现后再说话。");});
                    Thread.sleep(700);float[] pcm=record(6,()->{if(!cancelled)listener.progress(message);});recorded=System.nanoTime();check();
                    float[] embedding=speaker.embedding(pcm,i<trainingCount&&!wakeCalibration&&!comparison?32000:12000);
                    if(i<trainingCount)vectors[i]=embedding;else validation[i-trainingCount]=embedding;
                }
                check();
                if(comparison) {
                    String report=OwnerBackend.compare(vectors,new float[][]{background[0],background[2]},validation,new float[][]{background[1]});
                    report+="Final recording to result: "+((System.nanoTime()-recorded)/1000000.0)+" ms\n"
                        +"4 training recordings; 2 independent owner recordings; 2 public background speakers. No wake model activated.";
                    final String finished=report;main.post(()->{if(!cancelled)listener.comparison(finished);});
                }else {
                    OwnerBackend.Adapted adapted=OwnerBackend.adapt(vectors,background);
                    if(!adapted.predictor.accepts(validation[0]))throw new IOException("独立验证未通过，原声纹保留；请在日常使用距离录入。");
                    main.post(()->{if(!cancelled)listener.complete(vectors);});
                }
            }catch(Exception|LinkageError error){main.post(()->{if(!cancelled)listener.failed(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());});}
            finally{active=false;worker.shutdown();}
        });
    }
    private void check()throws InterruptedException {if(cancelled)throw new InterruptedException("Enrollment cancelled");}
    private float[] record(int seconds,Runnable ready)throws Exception {
        check();int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(minimum<=0)throw new IOException("Microphone format unavailable");
        AudioRecord recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum*2,6400));
        try {
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone unavailable");
            recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Microphone did not start");
            main.post(ready);float[] samples=new float[seconds*16000];short[] buffer=new short[1600];int offset=0;
            while(offset<samples.length) {
                check();if(context.getSystemService(AudioManager.class).getMode()!=AudioManager.MODE_NORMAL)throw new IOException("通话期间停止声纹录入");
                int count=recorder.read(buffer,0,Math.min(buffer.length,samples.length-offset));
                if(count<=0)throw new IOException("Microphone read failed");
                for(int i=0;i<count;i++)samples[offset++]=buffer[i]/32768f;
            }
            return samples;
        }finally{try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();}
    }
    void cancel(){cancelled=true;worker.shutdown();}
}
