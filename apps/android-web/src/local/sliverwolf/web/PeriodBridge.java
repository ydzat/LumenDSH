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

    /** Fallback: evaluate the tide plugin's own policy route when the plugin UI is not mounted. */
    static void refresh(android.content.Context context) {
        new Thread(() -> {
            try {
                android.content.SharedPreferences session = context.getSharedPreferences("session", 0);
                String cookie = session.getString("backendCookie", "");
                int port = session.getInt("backendPort", 3080);
                if (cookie.isEmpty()) return;
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/liangwen-tide/policy").openConnection();
                c.setRequestProperty("Cookie", cookie);
                c.setConnectTimeout(5000); c.setReadTimeout(5000);
                if (c.getResponseCode() != 200) return;
                StringBuilder text = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line; while ((line = r.readLine()) != null) text.append(line);
                }
                c.disconnect();
                JSONObject policy = new JSONObject(text.toString());
                org.json.JSONArray windows = policy.optJSONArray("windows");
                if (windows == null) return;
                boolean weekendAllValley = policy.optBoolean("weekendAllValley", false);
                long now = System.currentTimeMillis();
                String phase = phase(windows, weekendAllValley, now);
                long flipAt = 0;
                for (long t = now + 60000; t < now + 48L * 3600000L; t += 60000) {
                    if (!phase(windows, weekendAllValley, t).equals(phase)) { flipAt = t; break; }
                }
                if (phase.isEmpty()) return;
                store(context).edit().putString("phase", phase).putLong("flipAt", flipAt)
                    .putLong("reportedAt", now).putString("source", "policy").commit();
                postFrom(phase, flipAt, context);
            } catch (Exception ignored) { }
        }, "period-policy").start();
    }

    private static String phase(org.json.JSONArray windows, boolean weekendAllValley, long ms) {
        java.util.Date date = new java.util.Date(ms);
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(date);
        int day = cal.get(java.util.Calendar.DAY_OF_WEEK);
        if (weekendAllValley && (day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY)) return "valley";
        int minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        for (int i = 0; i < windows.length(); i++) {
            JSONObject w = windows.optJSONObject(i);
            if (w == null) continue;
            int start = w.optInt("start", -1), end = w.optInt("end", -1);
            if (start < 0 || end < 0) continue;
            boolean inside = start < end ? minutes >= start && minutes < end : minutes >= start || minutes < end;
            if (inside) return "peak";
        }
        return "valley";
    }

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

    static void postFrom(String phase, long flipAt, android.content.Context context) {
        new Thread(() -> { try { new PeriodBridge(context).post(phase, flipAt); } catch (Exception ignored) { } }).start();
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
