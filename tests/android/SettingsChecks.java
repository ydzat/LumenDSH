package local.sliverwolf.assistant.tests;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.view.*;
import java.lang.reflect.*;

/** Installed Android settings and draft isolation checks; no audio/LLM results are fabricated. */
public final class SettingsChecks extends Instrumentation {
    private Activity activity;private Throwable failure;
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private Object call(Object owner,String name,Class<?>[] types,Object...args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private boolean text(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    private void ui(Checked action){runOnMainSync(()->{try{action.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);}
    private interface Checked{void run()throws Exception;}
    private void liveDelivery()throws Exception{
        Object controller=field(activity,"controller");String original=(String)field(controller,"selected");
        SharedPreferences prefs=getTargetContext().getSharedPreferences("interaction",0);String reply=prefs.getString("homeReply","voice");
        EditText draft=(EditText)field(activity,"draft");String[] savedDraft={""};String[] request={""};
        try{
            ui(()->{savedDraft[0]=draft.getText().toString();draft.setText("Controlled preserved draft");prefs.edit().putString("homeReply","text").commit();call(controller,"create",new Class[]{});});
            long deadline=SystemClock.elapsedRealtime()+20000;boolean[] ready={false};
            while(SystemClock.elapsedRealtime()<deadline){ui(()->ready[0]=!original.equals(field(controller,"selected"))&&(Boolean)call(controller,"canSend",new Class[]{}));if(ready[0])break;SystemClock.sleep(200);}
            check(ready[0],"Controlled DSH session creation");
            ui(()->{set(activity,"voiceSession",field(controller,"selected"));set(activity,"voiceFromHome",true);set(activity,"voiceAutoSend",true);
                call(activity,"acceptVoice",new Class[]{String.class},"Controlled settings integration test. Reply only SETTINGS_OK. Do not use tools or write memory.");
                request[0]=(String)field(activity,"voiceRequest");check(!request[0].isEmpty(),"Home recognition result submitted automatically");
                check("Controlled preserved draft".contentEquals(draft.getText()),"Auto-send preserved chat draft");});
            deadline=SystemClock.elapsedRealtime()+90000;boolean[] complete={false};
            while(SystemClock.elapsedRealtime()<deadline){ui(()->{Object conversation=field(controller,"conversation");Class<?> type=Class.forName("local.sliverwolf.assistant.VoiceReply",true,activity.getClassLoader());Method find=type.getDeclaredMethod("find",conversation.getClass(),String.class);find.setAccessible(true);Object replyResult=find.invoke(null,conversation,request[0]);complete[0]=replyResult!=null&&!((String)field(replyResult,"text")).trim().isEmpty();});if(complete[0])break;SystemClock.sleep(300);}
            check(complete[0],"Correlated real DSH reply completed");
        }finally{ui(()->{call(activity,"stopVoice",new Class[]{String.class},"检查完成");prefs.edit().putString("homeReply",reply).commit();draft.setText(savedDraft[0]);if(!original.isEmpty())call(controller,"select",new Class[]{String.class},original);});}
    }
    private void foregroundWakeScope()throws Exception{
        SharedPreferences prefs=getTargetContext().getSharedPreferences("interaction",0);boolean old=prefs.getBoolean("homeWake",false);
        Class<?> wake=Class.forName("local.sliverwolf.assistant.WakeVoiceService",true,activity.getClassLoader());
        Method diagnostics=wake.getDeclaredMethod("diagnostics");diagnostics.setAccessible(true);Method busy=wake.getDeclaredMethod("microphoneBusy");busy.setAccessible(true);
        try{
            ui(()->{prefs.edit().putBoolean("homeWake",true).commit();call(activity,"syncWakeScope",new Class[]{});call(activity,"ensureWakeService",new Class[]{});});
            long deadline=SystemClock.elapsedRealtime()+30000;boolean[] capturing={false};
            while(SystemClock.elapsedRealtime()<deadline){ui(()->{org.json.JSONObject state=(org.json.JSONObject)diagnostics.invoke(null);capturing[0]="standby".equals(state.optString("state"))&&state.optLong("samplesRead")>0;});if(capturing[0])break;SystemClock.sleep(250);}
            check(capturing[0],"Foreground keyword service reads microphone");
            ui(()->call(activity,"showHabitatMenu",new Class[]{}));deadline=SystemClock.elapsedRealtime()+5000;boolean[] released={false};
            while(SystemClock.elapsedRealtime()<deadline){ui(()->released[0]=!(Boolean)busy.invoke(null));if(released[0])break;SystemClock.sleep(100);}
            check(released[0],"Drawer releases keyword microphone");
        }finally{ui(()->{prefs.edit().putBoolean("homeWake",old).commit();Dialog drawer=(Dialog)field(activity,"settingsDrawer");if(drawer!=null)drawer.dismiss();call(activity,"syncWakeScope",new Class[]{});call(activity,"ensureWakeService",new Class[]{});});}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();
        try{
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2500);
            runOnMainSync(()->{try{
                Object policy=field(activity,"interaction");SharedPreferences prefs=(SharedPreferences)field(policy,"values");
                String oldHome=prefs.getString("homeReply","voice"),oldChat=prefs.getString("chatReply","follow");
                try{
                    for(String mode:new String[]{"voice","text","follow"}){
                        prefs.edit().putString("homeReply",mode).putString("chatReply",mode).commit();
                        for(boolean home:new boolean[]{false,true})for(boolean voice:new boolean[]{false,true})
                            check((Boolean)call(policy,"speak",new Class[]{boolean.class,boolean.class},home,voice)==(mode.equals("voice")||(mode.equals("follow")&&voice)),"Reply policy matrix");
                    }
                }finally{prefs.edit().putString("homeReply",oldHome).putString("chatReply",oldChat).commit();}
                call(activity,"showHabitatMenu",new Class[]{});Dialog drawer=(Dialog)field(activity,"settingsDrawer");
                check(text(drawer.getWindow().getDecorView(),"交互与声音"),"Drawer grouped navigation");
                check(!text(drawer.getWindow().getDecorView(),"语音自动发送与朗读"),"Legacy coupled option removed");
                call(activity,"settingsInteraction",new Class[]{});check(text(drawer.getWindow().getDecorView(),"说完自动发送"),"Home auto-send control");
                check(text(drawer.getWindow().getDecorView(),"回复方式"),"Independent reply control");
                check(text(drawer.getWindow().getDecorView(),"主人声纹"),"Owner entry retained");drawer.dismiss();
                EditText draft=(EditText)field(activity,"draft");String original=draft.getText().toString();
                try{
                    draft.setText("Controlled chat draft");Object controller=field(activity,"controller");
                    set(activity,"voiceSession",field(controller,"selected"));set(activity,"voiceFromHome",true);set(activity,"voiceAutoSend",false);
                    call(activity,"acceptVoice",new Class[]{String.class},"Controlled voice confirmation");
                    check("Controlled chat draft".contentEquals(draft.getText()),"Home voice preserves chat draft");
                    AlertDialog confirm=(AlertDialog)field(activity,"voiceConfirmation");check(confirm!=null&&confirm.isShowing(),"Confirmation stays on home");confirm.dismiss();
                }finally{draft.setText(original);}
            }catch(Throwable error){failure=error;}});
            if(failure!=null)throw new AssertionError(failure);
            liveDelivery();foregroundWakeScope();
            result.putString("result","PASS: foreground keyword capture and drawer microphone release; real DSH auto-send/completed reply and original-session restoration; installed drawer, independent reply policy matrix, owner entry, home confirmation and chat-draft isolation; no acoustic claim");finish(-1,result);
        }catch(Throwable error){result.putString("failure",error.toString());finish(0,result);}
    }
}
