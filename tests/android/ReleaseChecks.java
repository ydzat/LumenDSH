package local.sliverwolf.assistant.tests;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Real Android storage with isolated preferences; interrupted journals are injected, not process kills. */
public final class ReleaseChecks extends Instrumentation {
    private Class<?> type; private Object releases;
    private Object call(String name,Class<?>[] types,Object... args)throws Exception {
        Method method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(releases,args);
    }
    private JSONObject stage(JSONObject m)throws Exception{return (JSONObject)call("stage",new Class<?>[]{JSONObject.class},m);}
    private JSONObject action(String name,String id,boolean safe)throws Exception{return (JSONObject)call(name,new Class<?>[]{String.class,boolean.class},id,safe);}
    private void healthy(String id)throws Exception{call("confirm",new Class<?>[]{String.class,boolean.class,String.class},id,true,"Isolated health acknowledgment");}
    private JSONObject snapshot()throws Exception{return (JSONObject)call("snapshot",new Class<?>[]{});}
    private static void require(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    private static String hash(String text)throws Exception {
        StringBuilder value=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))value.append(String.format("%02x",b&255));return value.toString();
    }
    private JSONObject manifest(String id,String base,String payload)throws Exception {
        return new JSONObject().put("schemaVersion",1).put("releaseId",id).put("componentId","presentation").put("baseVersion",base)
            .put("targetVersion",hash(payload)).put("payload",payload).put("minClientCode",35).put("activationMode","reload").put("verificationNote","Isolated storage fixture");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=1;
        try{
            final Context target=getTargetContext();final File root=Files.createTempDirectory(target.getCacheDir().toPath(),"release-check-").toFile();
            final String prefix=root.getName();
            Context isolated=new ContextWrapper(target){
                @Override public Context getApplicationContext(){return this;}
                @Override public File getFilesDir(){return root;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences(prefix+name,mode);}
            };
            type=target.getClassLoader().loadClass("local.sliverwolf.assistant.ContentReleases");
            Constructor<?> constructor=type.getDeclaredConstructor(Context.class);constructor.setAccessible(true);releases=constructor.newInstance(isolated);
            String original=snapshot().getString("currentPayload"),base=hash(original);
            String candidate=new JSONObject(original).put("tag","M5 CHECK").toString();
            require(stage(manifest("bad-hash",base,candidate).put("targetVersion",base)).getString("state").equals("rejected"),"Hash rejection");
            require(stage(manifest("bad-base",hash("stale"),candidate)).getString("state").equals("rejected"),"Base rejection");
            require(stage(manifest("bad-client",base,candidate).put("minClientCode",Integer.MAX_VALUE)).getString("state").equals("rejected"),"Compatibility rejection");
            require(snapshot().getString("currentVersion").equals(base),"Rejection preserved active version");
            JSONObject good=manifest("good",base,candidate);
            require(stage(good).getString("state").equals("staged"),"Stage");
            require(stage(good).getString("state").equals("staged"),"Idempotent stage");
            boolean conflict=false;try{stage(manifest("good",base,original));}catch(InvocationTargetException expected){conflict=true;}require(conflict,"Conflicting release ID");
            require(action("activate","good",false).getString("state").equals("staged"),"Audio boundary");
            require(action("activate","good",true).getString("state").equals("verifying"),"Activation waits for runtime health");healthy("good");
            require(snapshot().getString("currentPayload").equals(candidate),"Active payload readback");
            require(action("activate","good",true).getString("state").equals("active"),"Idempotent activation");
            require(action("rollback","good",true).getString("state").equals("recovered"),"Rollback");
            require(snapshot().getString("currentPayload").equals(original),"Exact previous version restored");
            stage(manifest("race",base,candidate));stage(manifest("winner",base,candidate));action("activate","winner",true);healthy("winner");
            require(action("activate","race",true).getString("state").equals("rejected"),"Stale base rechecked on activation");
            File journal=new File(root,"content-releases/winner.json");
            JSONObject interrupted=new JSONObject(new String(Files.readAllBytes(journal.toPath()),StandardCharsets.UTF_8)).put("state","verifying");
            Files.write(journal.toPath(),interrupted.toString().getBytes(StandardCharsets.UTF_8));
            releases=constructor.newInstance(isolated);call("recover",new Class<?>[]{});
            require(snapshot().getString("currentPayload").equals(original),"Injected interrupted journal restores previous version");
            call("recover",new Class<?>[]{});require(snapshot().getString("currentVersion").equals(base),"Recovery idempotency");
            stage(manifest("unhealthy",base,candidate));action("activate","unhealthy",true);
            call("confirm",new Class<?>[]{String.class,boolean.class,String.class},"unhealthy",false,"Injected renderer failure");
            require(snapshot().getString("currentVersion").equals(base),"Health failure restores previous version");
            call("discard",new Class<?>[]{String.class},"winner");require(!journal.exists(),"Terminal receipt discard");
            result.putString("result","RELEASE_CHECKS_PASS: Android atomic storage, staging, integrity, compatibility, retry, safe boundary, stale base, rollback and injected journal recovery; NOT process-death or renderer-health validation");code=-1;
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finish(code,result);
    }
}
