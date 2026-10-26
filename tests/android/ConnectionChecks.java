package local.sliverwolf.assistant.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;

/** Real installed DSH, authentication rejection and closed-port checks; no fake server. */
public final class ConnectionChecks extends Instrumentation {
    private Activity activity;private Throwable failure;private Bundle args;
    interface Checked{void run()throws Exception;}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object call(Object o,String n,Class<?>[] types,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,a);}
    private void ui(Checked c){failure=null;runOnMainSync(()->{try{c.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);}
    private void check(boolean v,String label){if(!v)throw new AssertionError(label);}
    @Override public void onCreate(Bundle b){args=b;super.onCreate(b);start();}
    @Override public void onStart(){Bundle out=new Bundle();Object[] test={null};int code=Activity.RESULT_CANCELED;
        SharedPreferences temporary=getTargetContext().getSharedPreferences("connection-checks",0);
        try{
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1500);
            ui(()->{call(activity,"showHabitatMenu",new Class[]{});Object drawer=field(activity,"settingsDrawer");call(drawer,"go",new Class[]{Runnable.class},(Runnable)()->{try{call(activity,"settingsSystem",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});});
            Class<?> control=Class.forName("local.sliverwolf.assistant.BackendControl",true,activity.getClassLoader());
            if("permission".equals(args.getString("mode"))){
                ui(()->activity.requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"},88));
                out.putString("result","Permission prompt requested");SystemClock.sleep(25000);finish(Activity.RESULT_OK,out);return;
            }
            if("models".equals(args.getString("mode"))){
                ui(()->call(activity,"showModelSettings",new Class[]{}));SystemClock.sleep(18000);
                out.putString("result","Model settings opened against real DSH for visual inspection");finish(Activity.RESULT_OK,out);return;
            }
            if("demo".equals(args.getString("mode"))){SystemClock.sleep(14000);finish(Activity.RESULT_OK,out);return;}
            if("cold-start".equals(args.getString("mode"))) {
                Method launch=control.getDeclaredMethod("run",Context.class,boolean.class);launch.setAccessible(true);
                ui(()->launch.invoke(null,activity,true));Object actual=field(activity,"controller");
                await(actual,"connected",true,45000);out.putString("result","PASS: App RUN_COMMAND started stopped DSH and authenticated original session");
                finish(Activity.RESULT_OK,out);return;
            }
            SharedPreferences live=getTargetContext().getSharedPreferences("session",0);
            temporary.edit().clear().putString("backendCookie",live.getString("backendCookie","")).putInt("backendPort",live.getInt("backendPort",3080)).putString("session",live.getString("session","")).commit();
            Class<?> type=Class.forName("local.sliverwolf.assistant.SessionController",true,activity.getClassLoader());Class<?> listener=Class.forName(type.getName()+"$Listener",true,activity.getClassLoader());
            Constructor<?> constructor=type.getDeclaredConstructor(SharedPreferences.class,listener);constructor.setAccessible(true);
            ui(()->{test[0]=constructor.newInstance(temporary,Proxy.newProxyInstance(activity.getClassLoader(),new Class[]{listener},(o,m,a)->null));call(test[0],"connect",new Class[]{});});
            await(test[0],"connected",true,25000);String selected=live.getString("session","");
            if("agent".equals(args.getString("mode"))){agentRoundTrip(test[0]);out.putString("result","PASS: actual DSH Agent invoked App status through MCP and received appVersion");finish(Activity.RESULT_OK,out);return;}

            ui(()->{call(test[0],"disconnect",new Class[]{});temporary.edit().putString("backendCookie","owner=invalid-for-controlled-test").commit();call(test[0],"connect",new Class[]{});});
            awaitCode(test[0],"HTTP_401");
            ui(()->{call(test[0],"disconnect",new Class[]{});temporary.edit().putInt("backendPort",3099).commit();call(test[0],"connect",new Class[]{});});awaitCode(test[0],"SERVICE_UNREACHABLE");
            ui(()->{call(test[0],"disconnect",new Class[]{});temporary.edit().putInt("backendPort",live.getInt("backendPort",3080)).putString("backendCookie",live.getString("backendCookie","")).commit();call(test[0],"connect",new Class[]{});});await(test[0],"connected",true,25000);
            check(selected.equals(temporary.getString("session","")),"Reconnect preserved original session");
            Object actual=field(activity,"controller");ui(()->call(actual,"disconnect",new Class[]{}));SystemClock.sleep(600);
            ui(()->check(((android.widget.TextView)field(activity,"connectionDetails")).getText().toString().contains("已手动断开"),"Visible drawer updates without reopening"));
            ui(()->call(actual,"connect",new Class[]{}));await(actual,"connected",true,25000);
            check(selected.equals(live.getString("session","")),"Production selection unchanged");
            Method run=control.getDeclaredMethod("run",Context.class,boolean.class);run.setAccessible(true);
            ui(()->run.invoke(null,activity,true));SharedPreferences state=getTargetContext().getSharedPreferences("backend-control",0);long limit=SystemClock.elapsedRealtime()+30000;
            while(state.getLong("deadline",0)!=0&&SystemClock.elapsedRealtime()<limit)SystemClock.sleep(200);
            check(state.getLong("deadline",0)==0&&state.getString("state","").contains("会话存在"),"Official Termux start callback: "+state.getString("state",""));
            out.putString("result","PASS: real authenticated streams, HTTP 401, closed-port classification, live drawer, original-session recovery and Termux start callback");code=Activity.RESULT_OK;
        }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(test[0]!=null)ui(()->call(test[0],"destroy",new Class[]{}));temporary.edit().clear().commit();if(activity!=null)try{ui(()->call(field(activity,"controller"),"connect",new Class[]{}));}catch(Throwable ignored){}}
        finish(code,out);
    }
    private void agentRoundTrip(Object controller)throws Exception{
        String previous=(String)field(controller,"selected");ui(()->call(controller,"create",new Class[]{}));
        long until=SystemClock.elapsedRealtime()+20000;boolean[] ready={false};
        while(SystemClock.elapsedRealtime()<until){ui(()->ready[0]=!previous.equals(field(controller,"selected"))&&(Boolean)call(controller,"canSend",new Class[]{}));if(ready[0])break;SystemClock.sleep(100);}check(ready[0],"Controlled session ready");
        ui(()->check((Boolean)call(controller,"send",new Class[]{String.class},"Controlled connection test. Call mcp__sliver_wolf__assistant_get_status with empty arguments once. Do not use other tools or write memory. Then reply CONNECTION_OK."),"Submit test"));
        until=SystemClock.elapsedRealtime()+120000;boolean[] success={false};java.util.Set<String> answered=new java.util.HashSet<>();
        while(SystemClock.elapsedRealtime()<until){ui(()->{
            java.util.Map<String,org.json.JSONObject> decisions=(java.util.Map<String,org.json.JSONObject>)field(controller,"decisions");
            for(java.util.Map.Entry<String,org.json.JSONObject> entry:new java.util.ArrayList<>(decisions.entrySet())){
                org.json.JSONObject payload=entry.getValue().getJSONObject("payload");
                if("approval/requested".equals(payload.optString("type"))&&"mcp__sliver_wolf__assistant_get_status".equals(payload.optString("toolName"))&&answered.add(entry.getKey()))
                    call(controller,"answer",new Class[]{String.class,org.json.JSONObject.class},entry.getKey(),new org.json.JSONObject().put("sessionId",field(controller,"selected")).put("approvalId",payload.getString("approvalId")).put("outcome","allowed-once"));
            }
            java.util.Map<Long,org.json.JSONObject> events=(java.util.Map<Long,org.json.JSONObject>)field(field(controller,"conversation"),"events");
            for(org.json.JSONObject event:events.values())if("tool/result".equals(event.optString("type"))){org.json.JSONObject message=event.getJSONObject("data").getJSONObject("message");if(!message.optBoolean("isError")&&message.toString().contains("appVersion"))success[0]=true;}
        });if(success[0])break;SystemClock.sleep(200);}
        check(success[0],"No successful App status result from actual Agent");
    }
    private void await(Object o,String name,boolean expected,long timeout)throws Exception{long limit=SystemClock.elapsedRealtime()+timeout;boolean[] ok={false};while(SystemClock.elapsedRealtime()<limit){ui(()->ok[0]=expected==(Boolean)field(o,name));if(ok[0])return;SystemClock.sleep(100);}throw new AssertionError("Timeout: "+name);}
    private void awaitCode(Object o,String code)throws Exception{long limit=SystemClock.elapsedRealtime()+15000;boolean[] ok={false};while(SystemClock.elapsedRealtime()<limit){ui(()->ok[0]=code.equals(field(o,"lastFailure")));if(ok[0])return;SystemClock.sleep(100);}throw new AssertionError("Missing failure classification: "+code);}
}
