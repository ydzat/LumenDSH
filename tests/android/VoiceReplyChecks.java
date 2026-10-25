package local.sliverwolf.assistant.tests;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.TextView;
import java.lang.reflect.*;
import org.json.JSONObject;

/** Real DSH and installed TTS, entering at the recognized-text callback (not an ASR test). */
public final class VoiceReplyChecks extends Instrumentation {
    private Activity activity;private Throwable failure;
    interface Checked{void run()throws Exception;}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private Object call(Object o,String n,Class<?>[] types,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,a);}
    private void ui(Checked action){failure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);}
    private void check(boolean v,String label){if(!v)throw new AssertionError(label);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        SharedPreferences prefs=getTargetContext().getSharedPreferences("interaction",0);
        boolean oldWake=prefs.getBoolean("homeWake",false);String oldReply=prefs.getString("homeReply","voice");
        Object[] controller={null};String[] original={""};
        try{
            result.putString("initialPolicy",oldReply+"; homeWake="+oldWake);
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2000);
            ui(()->{controller[0]=field(activity,"controller");original[0]=(String)field(controller[0],"selected");
                prefs.edit().putString("homeReply","voice").putBoolean("homeWake",true).commit();
                call(activity,"showTranscript",new Class[]{boolean.class},false);call(controller[0],"create",new Class[]{});});
            Class<?> wake=Class.forName("local.sliverwolf.assistant.WakeVoiceService",true,activity.getClassLoader());
            Method busy=wake.getDeclaredMethod("microphoneBusy");busy.setAccessible(true);
            long deadline=SystemClock.elapsedRealtime()+30000;boolean[] ready={false};
            ui(()->{call(activity,"syncWakeScope",new Class[]{});call(activity,"ensureWakeService",new Class[]{});});
            while(SystemClock.elapsedRealtime()<deadline){ui(()->ready[0]=!original[0].equals(field(controller[0],"selected"))&&(Boolean)call(controller[0],"canSend",new Class[]{})&&(Boolean)busy.invoke(null));if(ready[0])break;SystemClock.sleep(150);}
            check(ready[0],"Real keyword microphone active before submission");
            String[] request={""};
            ui(()->{set(activity,"voiceSession",field(controller[0],"selected"));set(activity,"voiceFromHome",true);set(activity,"voiceAutoSend",true);
                call(activity,"acceptVoice",new Class[]{String.class},"这是语音回复集成检查。请只回复：语音自动朗读测试成功。不要调用工具，不要写入记忆。");request[0]=(String)field(activity,"voiceRequest");});
            check(!request[0].isEmpty(),"Recognized text submitted");
            JSONObject[] operation={null};String[] error={""};
            deadline=SystemClock.elapsedRealtime()+150000;
            while(SystemClock.elapsedRealtime()<deadline){
                ui(()->{Object bridge=field(activity,"deviceBridge");Object journal=field(bridge,"operations");
                    try{operation[0]=(JSONObject)call(journal,"get",new Class[]{String.class},"voice-"+request[0]);
                        if("No retained operation".equals(operation[0].optString("detail")))operation[0]=null;
                    }catch(InvocationTargetException missing){}
                    String status=((TextView)field(activity,"voiceStatus")).getText().toString();if(status.contains("播放失败"))error[0]=status;
                });
                if(operation[0]!=null&&java.util.Arrays.asList("succeeded","failed","cancelled").contains(operation[0].optString("state")))break;
                if(!error[0].isEmpty())break;SystemClock.sleep(100);
            }
            check(operation[0]!=null,"Automatic speech operation missing: "+error[0]);
            check("succeeded".equals(operation[0].optString("state")),"Automatic speech failed: "+operation[0].optString("detail"));
            deadline=SystemClock.elapsedRealtime()+15000;ready[0]=false;
            while(SystemClock.elapsedRealtime()<deadline){ui(()->ready[0]=(Boolean)busy.invoke(null));if(ready[0])break;SystemClock.sleep(150);}
            check(ready[0],"Keyword microphone resumes after playback");
            result.putString("result","PASS: keyword active -> real DSH response -> automatic character playback -> standby");code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("error",e.toString());}
        finally{if(activity!=null)try{ui(()->{call(activity,"stopVoice",new Class[]{String.class},"检查完成");prefs.edit().putBoolean("homeWake",oldWake).putString("homeReply",oldReply).commit();if(!original[0].isEmpty())call(controller[0],"select",new Class[]{String.class},original[0]);call(activity,"syncWakeScope",new Class[]{});});}catch(Throwable e){result.putString("restoreError",e.toString());code=Activity.RESULT_CANCELED;}}
        finish(code,result);
    }
}
