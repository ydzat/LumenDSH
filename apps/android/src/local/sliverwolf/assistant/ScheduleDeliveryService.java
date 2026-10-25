package local.sliverwolf.assistant;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import org.json.*;
import static local.sliverwolf.assistant.DshTransport.object;

/** Alarm execution ownership follows upstream; submission uses the authenticated Session Remote. */
public final class ScheduleDeliveryService extends Service {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){
        super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("schedule-delivery","定时任务提交",NotificationManager.IMPORTANCE_LOW));
        startForeground(47,new Notification.Builder(this,"schedule-delivery").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LumenDSH · 提交已设置的定时任务").setContentText("提交到设置时选择的会话").build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        String id=intent.getStringExtra("taskId");long at=intent.getLongExtra("triggerAt",-1);
        worker.execute(()->{try{deliver(id,at);}finally{stopSelf(startId);}});
        return START_NOT_STICKY;
    }
    private void result(String id,long at,String status)throws Exception {
        if(!getSharedPreferences("schedules",0).edit().putString(id,status).putLong(id+":at",at).commit())throw new java.io.IOException("Cannot save delivery record");
    }
    private void deliver(String id,long at) {
        if(id==null||at<=0)return;
        boolean submissionStarted=false;
        try {
            ScheduledTaskStore store=new ScheduledTaskStore(getFilesDir());
            long now=System.currentTimeMillis();
            ScheduledTaskStore.Task task=store.claim(id,at,now);
            if(task==null)return;
            // Claim persists before any I/O. A consumed occurrence is never automatically replayed.
            result(id,at,"delivery-unknown · reserved before submission");
            ScheduledTaskStore.Task next=store.get(id);
            if(next!=null&&!store.isPaused(id))ScheduleAlarms.register(this,next);
            if(now-at>300000){result(id,at,"missed · over five minutes late · not submitted");return;}
            DshTransport wire=new DshTransport(getSharedPreferences("session",0));
            JSONArray sessions=wire.call("session.list",object()).getJSONArray("items");
            boolean found=false,busy=false;
            for(int i=0;i<sessions.length();i++)if(task.sessionId.equals(sessions.getJSONObject(i).optString("sessionId"))) {
                found=true;busy=sessions.getJSONObject(i).optBoolean("running");
            }
            if(!found){result(id,at,"session missing · not submitted");return;}
            if(id.startsWith("companion-")) {
                if(busy){result(id,at,"skipped · current agent is busy");return;}
                // Reserve before submission so a crash or unknown receipt cannot spend
                // the daily activity budget twice, even across different reflection tasks.
                synchronized(ScheduledTaskStore.LOCK) {
                    android.content.SharedPreferences budgets=getSharedPreferences("schedules",0);
                    long last=budgets.getLong("companion:lastReservedAt",0);
                    if(last>0 && now-last<86400000L){result(id,at,"skipped · daily reflection budget");return;}
                    if(!budgets.edit().putLong("companion:lastReservedAt",now).commit())
                        throw new java.io.IOException("Cannot reserve reflection budget");
                }
            }
            String requestId=java.util.UUID.nameUUIDFromBytes((id+":"+at).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            result(id,at,"delivery-unknown · request "+requestId);
            submissionStarted=true;
            JSONObject reply=wire.call("session.prompt",object("sessionId",task.sessionId,"mode","queue",
                "content",new JSONArray().put(object("type","text","text",task.text))),requestId);
            result(id,at,reply.getBoolean("accepted")?"accepted · completion belongs to DSH":"delivery-unknown · missing acceptance");
        }catch(Exception error){
            String state=error instanceof DshTransport.Refused?"refused · not retried":submissionStarted?"delivery-unknown · not retried":"unavailable · not submitted";
            try{result(id,at,state+" · "+error.getClass().getSimpleName());}catch(Exception ignored){/* The durable claim still fences replay. */}
        }
    }
    @Override public void onDestroy(){worker.shutdown();stopForeground(true);super.onDestroy();}
}
