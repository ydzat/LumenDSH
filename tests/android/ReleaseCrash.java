package local.sliverwolf.assistant.tests;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
/** Explicit device-only process death after a durable release transition. */
public final class ReleaseCrash extends Instrumentation {
    private String action;
    @Override public void onCreate(Bundle args){super.onCreate(args);action=args.getString("action","crash");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(action.equals("upgrade-before")||action.equals("upgrade-after")){
            android.content.Context c=getTargetContext();JSONObject snapshot=new JSONObject();
            snapshot.put("session",c.getSharedPreferences("session",0).getString("session",""));
            snapshot.put("autoSend",c.getSharedPreferences("voice",0).getBoolean("autoSend",false));
            snapshot.put("wake",c.getSharedPreferences("wake",0).getBoolean("enabled",false));
            snapshot.put("overlay",c.getSharedPreferences("overlay",0).getBoolean("enabled",false));
            snapshot.put("presentation",c.getSharedPreferences("presentation",0).getString("json",""));
            for(String name:new String[]{"owner-voice.bin","owner-wake.bin","owner-learned.bin","scheduled-tasks.json","appearances/active.json"}){
                File f=new File(c.getFilesDir(),name);if(f.isFile()){
                    byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath()));snapshot.put(name,android.util.Base64.encodeToString(digest,android.util.Base64.NO_WRAP));
                }
            }
            File baseline=new File(c.getCacheDir(),"upgrade-preservation-check.json");
            if(action.equals("upgrade-before")){Files.write(baseline.toPath(),snapshot.toString().getBytes(StandardCharsets.UTF_8));result.putString("result","UPGRADE_BASELINE_SAVED_WITHOUT_EXPORTING_PRIVATE_DATA");}
            else{
                JSONObject expected=new JSONObject(new String(Files.readAllBytes(baseline.toPath()),StandardCharsets.UTF_8));
                java.util.Iterator<String> names=expected.keys();while(names.hasNext()){String name=names.next();if(!expected.get(name).equals(snapshot.opt(name)))throw new AssertionError("Upgrade changed "+name);}
                Class<?> owner=c.getClassLoader().loadClass("local.sliverwolf.assistant.OwnerVoiceStore");Constructor<?> ctor=owner.getDeclaredConstructor(android.content.Context.class);ctor.setAccessible(true);Method load=owner.getDeclaredMethod("load");load.setAccessible(true);load.invoke(ctor.newInstance(c));
                result.putString("result","UPGRADE_DATA_AND_OWNER_DECRYPTION_PRESERVED");
            }
            finish(-1,result);return;
        }
        if(action.equals("signatures")){
            android.content.pm.PackageManager pm=getTargetContext().getPackageManager();int flags=android.content.pm.PackageManager.GET_SIGNATURES|android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
            android.content.pm.PackageInfo current=pm.getPackageInfo(getTargetContext().getPackageName(),flags);
            result.putString("installed","modern="+(current.signingInfo!=null)+" legacy="+(current.signatures==null?0:current.signatures.length));
            File root=new File(getTargetContext().getFilesDir(),"artifact-inbox");for(File file:root.listFiles())if(file.length()>8000000){
                android.content.pm.PackageInfo pkg=pm.getPackageArchiveInfo(file.toString(),flags);
                android.content.pm.PackageInfo modern=pm.getPackageArchiveInfo(file.toString(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                result.putString("modern-only-archive",modern==null?"null":"signingInfo="+(modern.signingInfo!=null));
                result.putString("archive",pkg==null?"null":"modern="+(pkg.signingInfo!=null)+" legacy="+(pkg.signatures==null?0:pkg.signatures.length));
            }
            finish(-1,result);return;
        }
        Class<?> type=getTargetContext().getClassLoader().loadClass("local.sliverwolf.assistant.ContentReleases");Constructor<?> ctor=type.getDeclaredConstructor(android.content.Context.class);ctor.setAccessible(true);Object store=ctor.newInstance(getTargetContext());
        Method snapshot=type.getDeclaredMethod("snapshot"),stage=type.getDeclaredMethod("stage",JSONObject.class),activate=type.getDeclaredMethod("activate",String.class,boolean.class);snapshot.setAccessible(true);stage.setAccessible(true);activate.setAccessible(true);
        File evidence=new File(getTargetContext().getCacheDir(),"release-crash-check.json");
        JSONObject before=(JSONObject)snapshot.invoke(store);
        if(action.equals("verify")){
            JSONObject expected=new JSONObject(new String(Files.readAllBytes(evidence.toPath()),StandardCharsets.UTF_8));
            if(!expected.getString("base").equals(before.getString("currentVersion")))throw new AssertionError("Previous content not restored");
            boolean recovered=false;for(int i=0;i<before.getJSONArray("releases").length();i++){JSONObject r=before.getJSONArray("releases").getJSONObject(i);if(r.getString("releaseId").equals(expected.getString("id")))recovered=r.getString("state").equals("recovered");}
            if(!recovered)throw new AssertionError("Recovery receipt missing");result.putString("result","ACTUAL_PROCESS_DEATH_RECOVERY_PASS");finish(-1,result);return;
        }
        String id="crash-"+System.currentTimeMillis(),payload=new JSONObject(before.getString("currentPayload")).put("tag","CRASH CHECK").toString();
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));
        JSONObject manifest=new JSONObject().put("schemaVersion",1).put("releaseId",id).put("componentId","presentation").put("baseVersion",before.getString("currentVersion")).put("targetVersion",hash.toString()).put("payload",payload).put("minClientCode",36).put("activationMode","reload").put("verificationNote","Explicit real process-death check");
        Files.write(evidence.toPath(),new JSONObject().put("id",id).put("base",before.getString("currentVersion")).toString().getBytes(StandardCharsets.UTF_8));
        if(!((JSONObject)stage.invoke(store,manifest)).getString("state").equals("staged"))throw new AssertionError("Stage failed");
        runOnMainSync(()->{try{if(!((JSONObject)activate.invoke(store,id,true)).getString("state").equals("verifying"))throw new AssertionError("Not verifying");Bundle status=new Bundle();status.putString("checkpoint","Durable verifying; terminating real app process now");sendStatus(1,status);android.os.Process.killProcess(android.os.Process.myPid());}catch(Exception error){throw new RuntimeException(error);}});
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(1,result);}}
}
