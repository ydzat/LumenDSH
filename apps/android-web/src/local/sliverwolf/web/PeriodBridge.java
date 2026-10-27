package local.sliverwolf.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Receives the tide plugin's own client state from the WebView and forwards it to the companion host. */
public final class PeriodBridge {
    private final Context context;
    PeriodBridge(Context context) { this.context = context; }
    static SharedPreferences store(Context c) { return c.getSharedPreferences("period", 0); }

    @JavascriptInterface public void report(String json) {
        try {
            JSONObject value = new JSONObject(json);
            String phase = value.optString("phase", "");
            if (!"peak".equals(phase) && !"valley".equals(phase)) return;
            long flipAt = value.optLong("flipAt", 0);
            store(context).edit().putString("phase", phase).putLong("flipAt", flipAt)
                .putLong("reportedAt", System.currentTimeMillis()).commit();
            new Thread(() -> post(phase, flipAt), "period-report").start();
        } catch (Exception ignored) { }
    }

    private void post(String phase, long flipAt) {
        try {
            SharedPreferences session = context.getSharedPreferences("session", 0);
            String cookie = session.getString("backendCookie", "");
            int port = session.getInt("backendPort", 3080);
            if (cookie.isEmpty()) return;
            String body = "{\"phase\":\"" + phase + "\",\"flipAt\":" + flipAt + "}";
            HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + "/sliver-wolf/companion/period").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes("UTF-8")); }
            connection.getResponseCode();
            connection.disconnect();
        } catch (Exception ignored) { }
    }
}
