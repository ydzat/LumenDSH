package local.sliverwolf.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Activity and hands-free service lease the same MCP/TTS owner. Main-thread only. */
final class SharedDeviceBridge {
    private static DeviceBridge bridge;
    private static int users;
    private static final Handler main=new Handler(Looper.getMainLooper());
    static DeviceBridge acquire(Context context) {
        main.removeCallbacks(close);
        if(bridge==null){bridge=new DeviceBridge(context.getApplicationContext());bridge.start();}
        users++;return bridge;
    }
    static void release(){if(--users==0)main.postDelayed(close,2000);}
    private static final Runnable close=()->{
        if(users!=0||bridge==null)return;
        DeviceBridge previous=bridge;bridge=null;previous.closeDevices();
        new Thread(()->{try{previous.stop(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"device-tools-stop").start();
    };
}
