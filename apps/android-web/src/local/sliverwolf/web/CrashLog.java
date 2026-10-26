package local.sliverwolf.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Forwards uncaught exceptions to the companion host so they can be read from Termux. */
final class CrashLog {
    private CrashLog() { }

    static void install(final Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { post(context, error); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static void mark(Context context, String message) {
        new Thread(() -> { try { postText(context, message); } catch (Throwable ignored) { } }, "app-mark").start();
    }

    private static void post(Context context, Throwable error) {
        postText(context, "CRASH " + Log.getStackTraceString(error));
    }

    private static void postText(Context context, String message) {
        SharedPreferences session = context.getSharedPreferences("session", 0);
        String cookie = session.getString("backendCookie", "");
        int port = session.getInt("backendPort", 3080);
        if (cookie.isEmpty()) return;
        String body = "{\"message\":" + JSONObject.quote(message) + "}";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/sliver-wolf/companion/log").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes("UTF-8")); }
            connection.getResponseCode();
        } catch (Throwable ignored) {
        } finally { if (connection != null) connection.disconnect(); }
    }
}
