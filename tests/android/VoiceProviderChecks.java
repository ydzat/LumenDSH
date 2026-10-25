package local.sliverwolf.assistant.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Real provider catalogs, encrypted storage and installed settings UI. No synthetic speech backend. */
public final class VoiceProviderChecks extends Instrumentation {
    private Activity activity;private Throwable failure;private Bundle args;
    interface Checked{void run()throws Exception;}
    private Object call(Object o,String name,Class<?>[] types,Object...values)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,values);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void ui(Checked action){failure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    @Override public void onCreate(Bundle args){this.args=args;super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1600);
            ui(()->{call(activity,"showHabitatMenu",new Class[]{});call(activity,"settingsVoice",new Class[]{});});
            if(args!=null&&args.getBoolean("menu",false)){result.putString("result","Voice settings visible");finish(Activity.RESULT_OK,result);return;}
            Class<?> type=Class.forName("local.sliverwolf.assistant.VoiceProviders",true,activity.getClassLoader());
            Constructor<?> ctor=type.getDeclaredConstructor(Context.class);ctor.setAccessible(true);Object store=ctor.newInstance(getTargetContext());
            JSONObject local=(JSONObject)call(store,"catalog",new Class[]{String.class,String.class},"local",null);
            check(local.getJSONArray("data").length()==2,"Both installed local engines discoverable");
            List<?> providers=(List<?>)call(store,"list",new Class[]{});String remote="";
            for(Object p:providers)if(!"local".equals(field(p,"id")))remote=(String)field(p,"id");
            check(!remote.isEmpty(),"Remote provider saved");
            JSONObject models=(JSONObject)call(store,"catalog",new Class[]{String.class,String.class},remote,null);
            check(models.getJSONArray("data").length()>0,"HTTPS model discovery");
            JSONObject voices=(JSONObject)call(store,"catalog",new Class[]{String.class,String.class},remote,"gpt-sovits-v2pro");
            check(voices.getJSONArray("voices").length()==3,"Actual remote voice discovery");
            String record=getTargetContext().getSharedPreferences("voice-providers",0).getString("provider."+remote,"");
            JSONObject saved=new JSONObject(record);String encrypted=saved.getString("key");
            check(encrypted.contains(":"),"Keystore ciphertext envelope");
            String engine=(String)call(store,"select",new Class[]{String.class,String.class,String.class},remote,"gpt-sovits-v2pro","silver-wolf");
            Object restored=ctor.newInstance(getTargetContext());Object selection=call(restored,"resolve",new Class[]{String.class},engine);
            check("silver-wolf".equals(field(selection,"voice")),"Selection restored by a new store instance");
            String localEngine=(String)call(store,"select",new Class[]{String.class,String.class,String.class},"local","hoyotts","silver-wolf");
            String gptEngine=(String)call(store,"select",new Class[]{String.class,String.class,String.class},"local","gpt-sovits","silver-wolf");
            result.putString("remoteEngine",engine);result.putString("localEngine",localEngine);result.putString("gptEngine",gptEngine);
            final String id=remote;ui(()->call(activity,"settingsProviderVoices",new Class[]{String.class,String.class},id,"gpt-sovits-v2pro"));SystemClock.sleep(1800);
            result.putString("result","PASS: real local/HTTPS catalogs, three voices, encrypted credentials and restored selection");code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("error",e.toString());}
        finish(code,result);
    }
}
