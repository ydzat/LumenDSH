package local.sliverwolf.web;

import android.app.PendingIntent;
import android.content.*;
import android.os.*;
import java.util.UUID;

/**
 * Fetches the DSH entrance connection (port + cookie) through Termux's official
 * RUN_COMMAND contract, so the WebView can authenticate without a separate
 * device-bridge pairing. Runs only when the app has no cookie yet.
 */
public final class ConnectionBootstrap extends BroadcastReceiver {
    static final String ACTION_READY = "local.sliverwolf.web.CONNECTION_READY";
    private static final String HOME = "/data/data/com.termux/files/home";
    private static final String BIN = "/data/data/com.termux/files/usr/bin/";
    private static final String FILE = HOME + "/.local/state/sliver-wolf-backend/connection-3080.json";
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";

    static SharedPreferences session(Context c) { return c.getSharedPreferences("session", 0); }

    static void run(Context c) {
        if (c.checkSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            c.sendBroadcast(new Intent(ACTION_READY).setPackage(c.getPackageName())
                .putExtra("error", "请在系统中授予 Termux 命令权限"));
            return;
        }
        String id = UUID.randomUUID().toString();
        Intent callback = new Intent(c, ConnectionBootstrap.class).setAction(id);
        PendingIntent pending = PendingIntent.getBroadcast(c, 0, callback,
            PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0));
        Intent command = new Intent("com.termux.RUN_COMMAND")
            .setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", BIN + "bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{HOME + "/agent-env.sh", "bash", "-c",
                "cat " + FILE})
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
        try {
            if (c.startService(command) == null) throw new IllegalStateException("Termux 服务不可用");
        } catch (RuntimeException error) {
            pending.cancel();
            c.sendBroadcast(new Intent(ACTION_READY).setPackage(c.getPackageName())
                .putExtra("error", "无法调用 Termux：" + error.getMessage()));
        }
    }

    @Override public void onReceive(Context c, Intent intent) {
        Bundle result = intent.getBundleExtra("result");
        String error = null;
        if (result == null) error = "Termux 没有返回结果";
        else if (result.getInt("err", -1) != -1) error = "Termux 拒绝执行（检查 allow-external-apps）";
        else if (result.getInt("exitCode", -1) != 0) error = "读取连接文件失败";
        else {
            String out = result.getString("stdout", "");
            try {
                org.json.JSONObject json = new org.json.JSONObject(out.trim());
                int port = json.optInt("port", 3080);
                String cookie = json.optString("cookie", "");
                if (cookie.isEmpty()) throw new IllegalStateException("cookie 为空");
                session(c).edit().putInt("backendPort", port).putString("backendCookie", cookie)
                    .putString("backendProtocol", json.optString("protocol", "0.2.1-rc.3")).commit();
            } catch (Exception parse) {
                error = "连接文件格式异常：" + parse.getMessage();
            }
        }
        Intent ready = new Intent(ACTION_READY).setPackage(c.getPackageName());
        if (error != null) ready.putExtra("error", error);
        c.sendBroadcast(ready);
    }
}
