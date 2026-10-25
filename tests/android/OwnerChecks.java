package local.sliverwolf.assistant.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;

/** Public model-author fixtures and isolated test storage; never enrolls the actual owner. */
public final class OwnerChecks extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Method method(Class<?> type,String name,Class<?>... args)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    @Override public void onStart() {
        Bundle result=new Bundle();int code=1;Object speaker=null,store=null;Class<?> speakerType=null,storeType=null;
        File file=new File(getTargetContext().getCacheDir(),"owner-isolated-check.bin");
        try {
            ClassLoader loader=getTargetContext().getClassLoader();
            Class<?> voiceType=loader.loadClass("local.sliverwolf.assistant.VoiceInput");
            Method retry=method(voiceType,"shouldRetry",int.class,boolean.class,long.class,int.class);
            if(!(Boolean)retry.invoke(null,6,false,2000L,0))throw new AssertionError("Initial silence is not retried");
            if((Boolean)retry.invoke(null,6,true,2000L,0)||(Boolean)retry.invoke(null,6,false,10000L,0)
                ||(Boolean)retry.invoke(null,6,false,2000L,2)||(Boolean)retry.invoke(null,3,false,2000L,0))throw new AssertionError("Retry escaped its bounds");
            Class<?> wakeType=loader.loadClass("local.sliverwolf.assistant.WakeDetector");
            Method quality=method(wakeType,"learningAudioQuality",float[].class);
            float[] clipped=new float[16000];java.util.Arrays.fill(clipped,1f);
            if((Boolean)quality.invoke(null,(Object)clipped))throw new AssertionError("Clipped learning audio accepted");
            if((Boolean)quality.invoke(null,(Object)new float[100]))throw new AssertionError("Short learning audio accepted");
            speakerType=loader.loadClass("local.sliverwolf.assistant.OwnerSpeaker");
            Constructor<?> constructor=speakerType.getDeclaredConstructor(android.content.Context.class);constructor.setAccessible(true);speaker=constructor.newInstance(getTargetContext());
            float[][] embeddings=new float[3][];float[][] shortEmbeddings=new float[3][];
            String[] names={"speaker1_a_cn_16k.wav","speaker1_b_cn_16k.wav","speaker2_a_cn_16k.wav"};
            Class<?> waveType=loader.loadClass("com.k2fsa.sherpa.onnx.WaveReader");
            for(int i=0;i<3;i++) {
                File fixture=new File(getTargetContext().getCacheDir(),"owner-public-fixture.wav");
                try {
                    try(InputStream in=getContext().getAssets().open(names[i]);FileOutputStream out=new FileOutputStream(fixture)) {
                        byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                    }
                    Object wave=waveType.getConstructor(String.class).newInstance(fixture.toString());
                    float[] samples=(float[])waveType.getMethod("getSamples").invoke(wave);
                    if((Integer)waveType.getMethod("getSampleRate").invoke(wave)!=16000)throw new AssertionError("Fixture rate mismatch");
                    embeddings[i]=(float[])method(speakerType,"embedding",float[].class,int.class).invoke(speaker,samples,12000);
                    int begin=(samples.length-19200)/2;
                    shortEmbeddings[i]=(float[])method(speakerType,"embedding",float[].class,int.class).invoke(speaker,java.util.Arrays.copyOfRange(samples,begin,begin+19200),1);
                }finally{fixture.delete();}
            }
            Class<?> backendType=loader.loadClass("local.sliverwolf.assistant.OwnerBackend");
            String backendReport=(String)method(backendType,"compare",float[][].class,float[][].class,float[][].class,float[][].class)
                .invoke(null,new float[][]{embeddings[0],shortEmbeddings[0]},new float[][]{embeddings[2],shortEmbeddings[2]},
                    new float[][]{embeddings[1],shortEmbeddings[1]},new float[0][]);
            result.putString("public_backend_comparison",backendReport);
            // Training and validation utterances remain disjoint; crops never cross the split.
            // There is no independent second recording for speaker 2 in the pinned assets.
            float[][] enrolled={embeddings[0],embeddings[0],embeddings[0]};
            Class<?> managerType=loader.loadClass("com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager");
            Object manager=managerType.getConstructor(int.class).newInstance(embeddings[0].length);
            StringBuilder comparison=new StringBuilder();
            try {
                managerType.getMethod("add",String.class,float[][].class).invoke(manager,"owner",enrolled);
                Method verify=managerType.getMethod("verify",String.class,float[].class,float.class);
                for(int i=1;i<3;i++)for(boolean shortClip:new boolean[]{false,true}) {
                    float[] vector=shortClip?shortEmbeddings[i]:embeddings[i];
                    int accepted=0;for(int level=1;level<=100;level++) {
                        if((Boolean)verify.invoke(manager,"owner",vector,level/100f))accepted=level;else break;
                    }
                    comparison.append(i==1?"same":"different").append(shortClip?"_1.2s=":"_full=").append(accepted/100f).append("; ");
                }
            }finally{managerType.getMethod("release").invoke(manager);}
            result.putString("public_fixture_similarity_floor",comparison.toString());
            Object adapted=method(backendType,"adapt",float[][].class,float[][].class).invoke(null,enrolled,new float[][]{embeddings[2],shortEmbeddings[2],embeddings[2]});
            Field predictorField=adapted.getClass().getDeclaredField("predictor");predictorField.setAccessible(true);
            Object predictor=predictorField.get(adapted);
            Method accepts=method(loader.loadClass("local.sliverwolf.assistant.OwnerBackend$Predictor"),"accepts",float[].class);
            if(!(Boolean)accepts.invoke(predictor,(Object)embeddings[1]))throw new AssertionError("Adapted backend rejected independent public owner");
            if((Boolean)accepts.invoke(predictor,(Object)embeddings[2]))throw new AssertionError("Adapted backend accepted training negative");
            Method matches=method(speakerType,"matches",float[][].class,float[].class);
            if(!(Boolean)matches.invoke(speaker,enrolled,embeddings[1]))throw new AssertionError("Public same-speaker fixture rejected");
            if((Boolean)matches.invoke(speaker,enrolled,shortEmbeddings[1]))throw new AssertionError("Low-similarity wake sample passed strict learning admission");
            if((Boolean)matches.invoke(speaker,enrolled,embeddings[2]))throw new AssertionError("Public different-speaker fixture accepted");
            boolean silence=false;try{method(speakerType,"embedding",float[].class,int.class).invoke(speaker,new float[16000],12000);}
            catch(InvocationTargetException expected){silence=expected.getCause().getClass().getSimpleName().equals("InsufficientSpeech");}
            if(!silence)throw new AssertionError("Silence was accepted as speech");
            storeType=loader.loadClass("local.sliverwolf.assistant.OwnerVoiceStore");
            Constructor<?> storeConstructor=storeType.getDeclaredConstructor(File.class,String.class);storeConstructor.setAccessible(true);
            store=storeConstructor.newInstance(file,"sliver-wolf-owner-check-"+System.nanoTime());
            method(storeType,"save",float[][].class).invoke(store,(Object)enrolled);
            float[][] restored=(float[][])method(storeType,"load").invoke(store);
            if(!java.util.Arrays.equals(restored[0],enrolled[0]))throw new AssertionError("Encrypted voiceprint roundtrip mismatch");
            Constructor<?> boundConstructor=storeType.getDeclaredConstructor(File.class,String.class,String.class);boundConstructor.setAccessible(true);
            java.lang.reflect.Field aliasField=storeType.getDeclaredField("alias");aliasField.setAccessible(true);
            Object wrongBinding=boundConstructor.newInstance(file,(String)aliasField.get(store),"different-wake-phrase");
            boolean nameRejected=false;try{method(storeType,"load").invoke(wrongBinding);}
            catch(InvocationTargetException expected){nameRejected=expected.getCause().getClass().getSimpleName().equals("BindingMismatch");}
            if(!nameRejected)throw new AssertionError("Different phrase binding accepted");
            if(!java.util.Arrays.equals(((float[][])method(storeType,"load").invoke(store))[0],enrolled[0]))throw new AssertionError("Binding check changed stored record");
            Field minimumField=storeType.getDeclaredField("minimum"),maximumField=storeType.getDeclaredField("maximum");
            minimumField.setAccessible(true);maximumField.setAccessible(true);minimumField.setInt(store,1);maximumField.setInt(store,9);
            method(storeType,"save",float[][].class).invoke(store,(Object)new float[][]{embeddings[0]});
            if(((float[][])method(storeType,"load").invoke(store)).length!=1)throw new AssertionError("First learned sample cannot roundtrip");
            float[][] tooMany=new float[10][];java.util.Arrays.fill(tooMany,embeddings[0]);boolean bounded=false;
            try{method(storeType,"save",float[][].class).invoke(store,(Object)tooMany);}catch(InvocationTargetException expected){bounded=expected.getCause() instanceof IOException;}
            if(!bounded||((float[][])method(storeType,"load").invoke(store)).length!=1)throw new AssertionError("Learning cap did not preserve prior record");
            minimumField.setInt(store,3);maximumField.setInt(store,3);method(storeType,"save",float[][].class).invoke(store,(Object)enrolled);
            byte[] bytes=Files.readAllBytes(file.toPath());bytes[bytes.length-1]^=1;Files.write(file.toPath(),bytes);
            boolean rejected=false;try{method(storeType,"load").invoke(store);}catch(InvocationTargetException expected){rejected=expected.getCause() instanceof javax.crypto.AEADBadTagException;}
            if(!rejected)throw new AssertionError("Tampered voiceprint accepted");
            result.putString("result","OWNER_NATIVE_VAD_PUBLIC_SPEAKER_AND_KEYSTORE_CHECKS_PASS; no real owner enrolled");code=0;
        }catch(Throwable failure){Throwable cause=failure instanceof InvocationTargetException?failure.getCause():failure;result.putString("error",cause.toString());}
        finally {
            try{if(speaker!=null)method(speakerType,"close").invoke(speaker);}catch(Exception ignored){}
            try{if(store!=null)method(storeType,"remove").invoke(store);}catch(Exception ignored){}
        }
        finish(code,result);
    }
}
