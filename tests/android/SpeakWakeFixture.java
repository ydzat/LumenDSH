package local.sliverwolf.assistant.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Explicit controlled acoustic diagnostic, run only by ADB; not human wake acceptance. */
public final class SpeakWakeFixture extends Instrumentation {
    private boolean command;
    @Override public void onCreate(Bundle args){super.onCreate(args);command="true".equals(args.getString("command"));start();}
    @Override public void onStart() {
        TextToSpeech[] engine=new TextToSpeech[1];Bundle result=new Bundle();
        CountDownLatch ready=new CountDownLatch(1),done=new CountDownLatch(1);int[] init={-1};boolean[] played={false};
        try {
            runOnMainSync(()->engine[0]=new TextToSpeech(getTargetContext(),code->{init[0]=code;ready.countDown();}));
            if(!ready.await(15,TimeUnit.SECONDS)||init[0]!=0)throw new IllegalStateException("Fixture TTS unavailable");
            runOnMainSync(()->{
                engine[0].setLanguage(java.util.Locale.SIMPLIFIED_CHINESE);
                engine[0].setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    public void onStart(String id){}public void onDone(String id){played[0]=true;done.countDown();}
                    public void onError(String id){done.countDown();}
                });
                if(engine[0].speak(command?"请只回复：后台语音测试成功":"嗨，银狼",TextToSpeech.QUEUE_FLUSH,new Bundle(),"controlled-wake")!=0)done.countDown();
            });
            if(!done.await(15,TimeUnit.SECONDS)||!played[0])throw new IllegalStateException("Fixture playback failed");
            result.putString("result","CONTROLLED_WAKE_PHRASE_PLAYED; inspect independent detector counters");finish(0,result);
        }catch(Exception e){result.putString("error",e.toString());finish(1,result);}
        finally{runOnMainSync(()->{if(engine[0]!=null)engine[0].shutdown();});}
    }
}
