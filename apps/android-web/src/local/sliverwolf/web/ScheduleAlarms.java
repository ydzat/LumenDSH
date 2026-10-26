package local.sliverwolf.web;

import android.app.*;
import android.content.*;
import android.net.Uri;

/** Alarm identity/cancellation adapted from a5af7b1 (MIT, 2026 zhou-gu24). */
final class ScheduleAlarms {
    private static PendingIntent pending(Context context,ScheduledTaskStore.Task task) {
        Intent intent=new Intent(context,ScheduleReceiver.class)
            .setData(Uri.parse("sliverwolf://schedule/"+Uri.encode(task.id)))
            .putExtra("taskId",task.id).putExtra("triggerAt",task.triggerAt);
        return PendingIntent.getBroadcast(context,task.id.hashCode(),intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void register(Context context,ScheduledTaskStore.Task task) {
        AlarmManager manager=context.getSystemService(AlarmManager.class);
        // API 29 target: permission failures remain visible rather than silently changing timing.
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,task.triggerAt,pending(context,task));
    }
    static void cancel(Context context,ScheduledTaskStore.Task task) {
        PendingIntent intent=pending(context,task);
        context.getSystemService(AlarmManager.class).cancel(intent);intent.cancel();
    }
    static void restore(Context context)throws Exception {
        ScheduledTaskStore store=new ScheduledTaskStore(context.getFilesDir());
        long now=System.currentTimeMillis();
        for(ScheduledTaskStore.Task task:store.list()) {
            if(store.isPaused(task.id))continue;
            if(task.triggerAt<=now) {
                // Reopening the app must not silently catch up an old task.
                if(store.claim(task.id,task.triggerAt,now)!=null)
                    context.getSharedPreferences("schedules",0).edit().putString(task.id,"missed · not submitted").putLong(task.id+":at",task.triggerAt).commit();
                task=store.get(task.id);
            }
            if(task!=null&&!store.isPaused(task.id))register(context,task);
        }
    }
}
