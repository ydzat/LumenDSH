package local.sliverwolf.web;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import java.util.ArrayList;

/** One bounded user request; retries before speech never create a second submission. */
final class VoiceInput {
    interface Listener {void state(String state);void result(String text);default void ready(){}}
    private final Context context;private final Listener listener;private final AudioManager audio;
    private final Handler main=new Handler();
    private SpeechRecognizer recognizer;
    private final LocalVoiceInput local;
    private int generation,attempt,retries;
    private boolean running,heard,cued;
    private long readyAt;
    VoiceInput(Context context,Listener listener){this.context=context;this.listener=listener;audio=context.getSystemService(AudioManager.class);
        local=new LocalVoiceInput(context,new LocalVoiceInput.Listener(){
            public void state(String state){listener.state(state);}
            public void result(String text){listener.result(text);}
        });}
    boolean active(){return running||local.active();}
    void start(){start("zh-CN");}
    void start(String language) {
        stop();
        // Local engine first: it runs entirely on this phone and needs no network.
        // The system recognizer stays as the fallback until the model is present.
        if (SpeechModels.ready(context)) { local.start(); return; }
        if(!SpeechRecognizer.isRecognitionAvailable(context)){listener.state("Recognition service unavailable");return;}
        if(audio.getMode()!=AudioManager.MODE_NORMAL){listener.state("Voice unavailable during a call");return;}
        running=true;heard=false;cued=false;readyAt=0;retries=0;int epoch=generation;
        listen(epoch,language);
        main.postDelayed(new Runnable(){public void run(){
            if(epoch!=generation||!running)return;
            if(audio.getMode()!=AudioManager.MODE_NORMAL){stop();listener.state("Microphone stopped: communication audio became active");}
            else main.postDelayed(this,500);
        }},500);
        main.postDelayed(()->{if(epoch==generation&&running){stop();listener.state("Recognition timed out; microphone released");}},30000);
    }
    private void releaseRecognizer(){SpeechRecognizer old=recognizer;recognizer=null;if(old!=null){old.cancel();old.destroy();}}
    private void listen(int epoch,String language) {
        if(epoch!=generation||!running)return;
        final int turn=++attempt;
        try {
            recognizer=SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean current(){return epoch==generation&&turn==attempt&&running;}
                public void onReadyForSpeech(Bundle params){if(!current())return;
                    if(readyAt==0)readyAt=SystemClock.elapsedRealtime();
                    listener.state("正在聆听，请慢慢说…");if(!cued){cued=true;listener.ready();}
                }
                public void onBeginningOfSpeech(){if(current()){heard=true;listener.state("正在听你说…");}}
                public void onRmsChanged(float rms){}
                public void onBufferReceived(byte[] buffer){}
                public void onEndOfSpeech(){if(current())listener.state("正在识别…");}
                public void onError(int error){
                    if(!current())return;
                    if(readyAt>0&&shouldRetry(error,heard,SystemClock.elapsedRealtime()-readyAt,retries)) {
                        retries++;attempt++;releaseRecognizer();listener.state("我还在听，请说话…");
                        main.postDelayed(()->listen(epoch,language),350);return;
                    }
                    stop();listener.state("Recognition failed (Android code "+error+")");
                }
                public void onResults(Bundle results){if(!current())return;
                    ArrayList<String> items=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);stop();
                    if(items==null||items.isEmpty()||items.get(0).trim().isEmpty())listener.state("No speech recognized");
                    else listener.result(items.get(0));
                }
                public void onPartialResults(Bundle results){}
                public void onEvent(int type,Bundle params){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,language).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,10000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,2000L);
            listener.state("正在准备麦克风…");recognizer.startListening(intent);
        }catch(RuntimeException error){stop();listener.state("Recognition unavailable: "+error.getClass().getSimpleName());}
    }
    static boolean shouldRetry(int error,boolean heard,long elapsed,int retries) {
        return !heard&&elapsed<10000&&retries<2&&(error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT||error==SpeechRecognizer.ERROR_NO_MATCH);
    }
    void stop(){generation++;attempt++;running=false;local.stop();main.removeCallbacksAndMessages(null);releaseRecognizer();}
}
