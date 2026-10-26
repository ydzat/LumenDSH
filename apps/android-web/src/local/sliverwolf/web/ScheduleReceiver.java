package local.sliverwolf.web;

import android.content.*;

/** Only system boot and app-owned alarm PendingIntents reach this non-exported receiver. */
public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            PendingResult pending=goAsync();
            new Thread(()->{try{ScheduleAlarms.restore(context);}
                catch(Exception error){context.getSharedPreferences("schedules",0).edit().putString("error","Restore failed: "+error.getClass().getSimpleName()).apply();}
                finally{pending.finish();}},"schedule-restore").start();
        } else if(intent.getStringExtra("taskId")!=null) {
            context.startForegroundService(new Intent(context,ScheduleDeliveryService.class)
                .putExtra("taskId",intent.getStringExtra("taskId"))
                .putExtra("triggerAt",intent.getLongExtra("triggerAt",-1)));
        }
    }
}
