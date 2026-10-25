package local.sliverwolf.assistant.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import org.json.*;

/** Actual background service and DSH, entering at final recognized text; not an acoustic wake test. */
public final class BackgroundVoiceChecks extends Instrumentation {
    private Activity activity;private Throwable failure;
    interface Checked{void run()throws Exception;}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private Object call(Object o,String n,Class<?>[] t,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}
    private void ui(Checked f){failure=null;runOnMainSync(()->{try{f.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);}
    private void check(boolean v,String label){if(!v)throw new AssertionError(label);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();int code=Activity.RESULT_CANCELED;
        SharedPreferences wake=getTargetContext().getSharedPreferences("wake",0);boolean enabled=wake.getBoolean("enabled",false);
        Object[] controller={null},service={null};String[] original={""},request={""};
        try{
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2000);
            Class<?> type=Class.forName("local.sliverwolf.assistant.WakeVoiceService",true,activity.getClassLoader());Field current=type.getDeclaredField("current");current.setAccessible(true);
            ui(()->{controller[0]=field(activity,"controller");original[0]=(String)field(controller[0],"selected");call(controller[0],"create",new Class[]{});wake.edit().putBoolean("enabled",true).commit();call(activity,"ensureWakeService",new Class[]{});activity.moveTaskToBack(true);});
            long deadline=SystemClock.elapsedRealtime()+30000;boolean[] ready={false};
            while(SystemClock.elapsedRealtime()<deadline){ui(()->{service[0]=current.get(null);if(service[0]!=null){Object session=field(service[0],"session");ready[0]=!original[0].equals(field(session,"selected"))&&(Boolean)call(session,"canSend",new Class[]{})&&"standby".equals(field(service[0],"mode"));}});if(ready[0])break;SystemClock.sleep(200);}
            check(ready[0],"Background standby in controlled session");
            ui(()->{call(service[0],"pause",new Class[]{String.class},"Controlled background reply check");set(service[0],"nextWake",SystemClock.elapsedRealtime()+60000);});SystemClock.sleep(1000);
            ui(()->{Object session=field(service[0],"session");set(service[0],"voiceSession",field(session,"selected"));Object input=field(service[0],"input"),listener=field(input,"listener");
                call(listener,"result",new Class[]{String.class},"请只回复：后台远程语音正常。不要使用工具，不要写入记忆。");request[0]=(String)field(service[0],"request");});check(!request[0].isEmpty(),"Background submission accepted");
            deadline=SystemClock.elapsedRealtime()+150000;JSONObject[] op={null};
            while(SystemClock.elapsedRealtime()<deadline){ui(()->{Object bridge=field(service[0],"bridge"),journal=field(bridge,"operations");op[0]=(JSONObject)call(journal,"get",new Class[]{String.class},"voice-"+request[0]);});
                if(java.util.Arrays.asList("succeeded","failed","cancelled").contains(op[0].optString("state")))break;SystemClock.sleep(150);}
            check(op[0]!=null&&"succeeded".equals(op[0].optString("state")),"Background reply playback: "+op[0]);
            deadline=SystemClock.elapsedRealtime()+15000;ready[0]=false;
            while(SystemClock.elapsedRealtime()<deadline){ui(()->ready[0]="standby".equals(field(service[0],"mode")));if(ready[0])break;SystemClock.sleep(200);}
            check(ready[0],"Background standby resumed");out.putString("result","PASS: background recognized-text callback -> real DSH -> provider playback -> standby");code=Activity.RESULT_OK;
        }catch(Throwable e){out.putString("error",e.toString());}
        finally{if(activity!=null)try{ui(()->{if(service[0]!=null)call(service[0],"pause",new Class[]{String.class},"Checks complete");wake.edit().putBoolean("enabled",enabled).commit();if(!original[0].isEmpty())call(controller[0],"select",new Class[]{String.class},original[0]);activity.startActivity(new Intent(activity,activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));});}catch(Throwable e){out.putString("restoreError",e.toString());}}
        finish(code,out);
    }
}
