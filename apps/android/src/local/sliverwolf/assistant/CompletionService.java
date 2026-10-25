package local.sliverwolf.assistant;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import org.json.*;

/** Read-only session observer; notification policy adapted from turn-notify (MIT, zhou-gu24). */
public final class CompletionService extends Service {
    static boolean foreground;
    private SessionController observer;
    private android.content.SharedPreferences prefs;
    private String session="";
    private String observerStatus="";
    private boolean initialized,wasRunning;
    private long startedAt;
    private NotificationManager manager;
    private static final String CHANNEL="turn-completion",SERVICE="completion-monitor";
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate() {
        super.onCreate();prefs=getSharedPreferences("completion",0);manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(SERVICE,"任务完成监听",NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"任务完成提醒",NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(45,new Notification.Builder(this,SERVICE).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LumenDSH · 任务完成监听").setContentText("仅监听已选会话；在应用菜单中关闭")
            .setContentIntent(open("monitor","")).setOngoing(true).build());
        observer=new SessionController(getSharedPreferences("session",0),this::changed);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(!prefs.getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
        String selected=getSharedPreferences("session",0).getString("session","");
        if(selected.isEmpty()){stopSelf();return START_NOT_STICKY;}
        if(!session.equals(selected)){session=selected;initialized=false;wasRunning=false;observer.select(selected);}
        if(!observer.connected)observer.connect();
        return START_STICKY;
    }
    private PendingIntent open(String id,String selected) {
        Intent intent=new Intent(this,MainActivity.class).setData(android.net.Uri.parse("sliverwolf://completion/"+id))
            .putExtra("notificationSession",selected).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void changed() {
        if(observer!=null){
            String status=observer.connection+(observer.historyBusy?" · Loading history":"");
            if(!status.equals(observerStatus)){observerStatus=status;prefs.edit().putString("observer",status).apply();}
        }
        if(observer==null||!observer.connected||observer.historyBusy||session.isEmpty()||observer.conversation.events.isEmpty())return;
        JSONObject terminal=null;boolean running=false;String reply="";
        for(JSONObject event:observer.conversation.events.values()) {
            String type=event.optString("type");JSONObject data=event.optJSONObject("data");if(data==null)continue;
            if(type.equals("turn/start")){running=true;terminal=null;reply="";}
            if(type.equals("assistant/message")) {
                JSONObject message=data.optJSONObject("message");JSONArray blocks=message==null?null:message.optJSONArray("content");
                StringBuilder text=new StringBuilder();if(blocks!=null)for(int i=0;i<blocks.length();i++) {
                    JSONObject block=blocks.optJSONObject(i);if(block!=null&&"text".equals(block.optString("type")))text.append(block.optString("text"));
                }
                if(text.length()>0)reply=text.toString();
            }
            if(type.equals("turn/end")){running=false;terminal=event;}
        }
        long now=android.os.SystemClock.elapsedRealtime();
        if(!initialized){initialized=true;wasRunning=running;if(running)startedAt=now;return;}
        if(running&&!wasRunning)startedAt=now;
        boolean ended=wasRunning&&!running;wasRunning=running;
        if(!ended||terminal==null)return;
        long sequence=terminal.optLong("seq",-1);String key="seen:"+session;
        if(sequence<=prefs.getLong(key,-1))return;
        // Persist before posting: a crash may lose a notice, but cannot duplicate it on replay.
        if(!prefs.edit().putLong(key,sequence).commit())return;
        JSONObject reason=terminal.optJSONObject("data").optJSONObject("reason");
        if(reason==null||!"completed".equals(reason.optString("kind"))){outcome("未提醒：任务未正常完成");return;}
        long wall=System.currentTimeMillis();
        if(foreground){outcome("未提醒：应用在前台");return;}
        if(now-startedAt<20000){outcome("未提醒：观察到的任务时长不足20秒");return;}
        if(wall-prefs.getLong("lastPosted",0)<60000){outcome("未提醒：60秒冷却期内");return;}
        if(!manager.areNotificationsEnabled()||manager.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE){outcome("未提醒：系统通知已关闭");return;}
        String body=reply.replaceAll("\\s+"," ").trim();if(body.length()>140)body=body.substring(0,140)+"…";
        if(body.isEmpty())body="任务已完成，本轮没有文字回复。";
        try {
            String record=NotificationArchive.save(getFilesDir(),"任务完成",body,session);
            prefs.edit().putLong("lastPosted",wall).apply();
            manager.notify(record,46,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("LumenDSH · 任务完成").setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(open(record,session)).setAutoCancel(true).build());
            outcome("已发送任务完成提醒");
        }catch(Exception error){prefs.edit().putString("error","Notification delivery failed: "+error.getClass().getSimpleName()).apply();}
    }
    private void outcome(String message){prefs.edit().putString("outcome",message).apply();}
    @Override public void onDestroy(){if(observer!=null)observer.destroy();stopForeground(true);super.onDestroy();}
}
