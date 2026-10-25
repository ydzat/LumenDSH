package local.sliverwolf.assistant.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Real installed APK/JNI and Android ICU checks; not microphone wake acceptance. */
public final class WakeChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private static Method method(Class<?> type,String name,Class<?>... args)throws Exception {
        Method value=type.getDeclaredMethod(name,args);value.setAccessible(true);return value;
    }
    @Override public void onStart() {
        Bundle result=new Bundle();Object detector=null;Class<?> detectorType=null;
        try {
            ClassLoader loader=getTargetContext().getClassLoader();
            detectorType=loader.loadClass("local.sliverwolf.assistant.WakeDetector");
            Constructor<?> constructor=detectorType.getDeclaredConstructor(android.content.Context.class);constructor.setAccessible(true);
            detector=constructor.newInstance(getTargetContext());method(detectorType,"prepare",String.class).invoke(detector,"银狼");
            Field field=detectorType.getDeclaredField("vocabulary");field.setAccessible(true);
            Object vocabulary=field.get(detector);
            Class<?> phraseType=loader.loadClass("local.sliverwolf.assistant.WakePhrase");
            Method compile=method(phraseType,"compile",String.class,Set.class);
            Field display=phraseType.getDeclaredField("display"),encoded=phraseType.getDeclaredField("encoded");display.setAccessible(true);encoded.setAccessible(true);
            Object wolf=compile.invoke(null,"银狼",vocabulary),other=compile.invoke(null,"小明",vocabulary);
            if(!"嗨，银狼".equals(display.get(wolf))||!encoded.get(wolf).toString().startsWith("h āi y ín l áng :1.5 #0.25 @wake\n"))throw new AssertionError("Default phrase mismatch: "+encoded.get(wolf));
            String[] variants=encoded.get(wolf).toString().split("\n");
            if(variants.length!=4)throw new AssertionError("Greeting variants missing");
            for(String variant:variants)if(!variant.endsWith(" y ín l áng :1.5 #0.25 @wake"))throw new AssertionError("Incomplete wake phrase");
            if(!"嗨，小明".equals(display.get(other))||encoded.get(other).equals(encoded.get(wolf)))throw new AssertionError("Name did not change tokens");
            boolean rejected=false;try{compile.invoke(null,"Silver Wolf",vocabulary);}catch(InvocationTargetException expected){rejected=expected.getCause() instanceof IllegalArgumentException;}
            if(!rejected)throw new AssertionError("Unsupported name was silently accepted");
            Field engineField=detectorType.getDeclaredField("spotter");engineField.setAccessible(true);Object engine=engineField.get(detector);
            Object stream=engine.getClass().getMethod("createStream").invoke(engine);
            try {
                stream.getClass().getMethod("acceptWaveform",float[].class,int.class).invoke(stream,new float[16000],16000);
                Method ready=engine.getClass().getMethod("isReady",stream.getClass()),decode=engine.getClass().getMethod("decode",stream.getClass());
                while((Boolean)ready.invoke(engine,stream))decode.invoke(engine,stream);
                Object output=engine.getClass().getMethod("getResult",stream.getClass()).invoke(engine,stream);
                if(!"".equals(output.getClass().getMethod("getKeyword").invoke(output)))throw new AssertionError("Silence triggered wake");
            }finally{stream.getClass().getMethod("release").invoke(stream);}
            java.io.File fixture=new java.io.File(getTargetContext().getCacheDir(),"wake-controlled-tts.wav");
            android.speech.tts.TextToSpeech[] tts=new android.speech.tts.TextToSpeech[1];
            java.util.concurrent.CountDownLatch initialized=new java.util.concurrent.CountDownLatch(1),rendered=new java.util.concurrent.CountDownLatch(1);
            int[] ttsStatus={-1};boolean[] synthesized={false};
            try {
                runOnMainSync(()->{tts[0]=new android.speech.tts.TextToSpeech(getTargetContext(),code->{ttsStatus[0]=code;initialized.countDown();});});
                if(!initialized.await(15,java.util.concurrent.TimeUnit.SECONDS)||ttsStatus[0]!=0)throw new AssertionError("TTS fixture engine unavailable");
                runOnMainSync(()->{
                    tts[0].setLanguage(java.util.Locale.SIMPLIFIED_CHINESE);
                    tts[0].setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                        public void onStart(String id){} public void onDone(String id){synthesized[0]=true;rendered.countDown();}
                        public void onError(String id){rendered.countDown();}
                    });
                    if(tts[0].synthesizeToFile("嗨，银狼",new Bundle(),fixture,"wake-fixture")!=0)rendered.countDown();
                });
                if(!rendered.await(20,java.util.concurrent.TimeUnit.SECONDS)||!synthesized[0])throw new AssertionError("Controlled synthesis failed");
                Class<?> waveType=loader.loadClass("com.k2fsa.sherpa.onnx.WaveReader");Object wave=waveType.getConstructor(String.class).newInstance(fixture.toString());
                float[] samples=(float[])waveType.getMethod("getSamples").invoke(wave);int rate=(Integer)waveType.getMethod("getSampleRate").invoke(wave);
                samples=java.util.Arrays.copyOf(samples,samples.length+rate);
                Object voiced=engine.getClass().getMethod("createStream").invoke(engine);boolean hit=false;
                try {
                    for(int offset=0;offset<samples.length;offset+=1600) {
                        float[] block=java.util.Arrays.copyOfRange(samples,offset,Math.min(offset+1600,samples.length));
                        voiced.getClass().getMethod("acceptWaveform",float[].class,int.class).invoke(voiced,block,rate);
                        if((Boolean)method(detectorType,"decodeReady",engine.getClass(),voiced.getClass()).invoke(null,engine,voiced)){hit=true;break;}
                    }
                    result.putString("controlledTtsKeyword",String.valueOf(hit));
                }finally{voiced.getClass().getMethod("release").invoke(voiced);}
            }finally{runOnMainSync(()->{if(tts[0]!=null)tts[0].shutdown();});fixture.delete();}
            method(detectorType,"prepare",String.class).invoke(detector,"小明");
            Field engineName=detectorType.getDeclaredField("engineName");engineName.setAccessible(true);
            if(!"小明".equals(engineName.get(detector)))throw new AssertionError("Engine retained old name");
            result.putString("result","REAL_APK_JNI_ICU_WAKE_PHRASE_PASS; native/ICU checks; controlled TTS diagnostic separate from user wake acceptance");finish(0,result);
        }catch(Throwable error){Throwable cause=error instanceof InvocationTargetException?error.getCause():error;result.putString("error",cause.toString());finish(1,result);}
        finally{try{if(detector!=null)method(detectorType,"close").invoke(detector);}catch(Exception ignored){}}
    }
}
