package local.sliverwolf.web;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Downloads the recognizer model from the local speech module exactly once.
 *
 * The host keeps the single verified copy (integrations/speech); the app fetches
 * it over the existing loopback entrance with the same session cookie, verifies
 * the SHA-256 and only then installs it. No model bytes are bundled in the APK.
 */
final class SpeechModels {
    interface Listener {void progress(long received, long total);}

    private SpeechModels() {}

    static boolean ready(Context context) {return SpeechEngine.ready(context);}

    /** Fetch both model files if they are missing or fail verification. */
    static void ensure(Context context, Listener listener) throws Exception {
        File dir = SpeechEngine.modelDir(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Speech storage unavailable");
        copy(context, dir, SpeechEngine.MODEL_FILE, SpeechEngine.MODEL_HASH, listener);
        copy(context, dir, SpeechEngine.TOKENS_FILE, SpeechEngine.TOKENS_HASH, listener);
    }

    private static void copy(Context context, File dir, String name, String hash, Listener listener) throws Exception {
        File target = new File(dir, name);
        if (target.isFile() && hash.equals(sha256(target))) return;
        String cookie = context.getSharedPreferences("session", 0).getString("backendCookie", "");
        if (cookie.isEmpty()) throw new IOException("No local connection yet");
        URL url = new URL("http://127.0.0.1:3080/sliver-wolf/speech/models/" + SpeechEngine.MODEL_ID + "/" + name);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Cookie", cookie);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(120000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Model download failed: HTTP " + connection.getResponseCode());
            long total = connection.getContentLengthLong();
            File part = new File(dir, name + ".part");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[65536];
                long received = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    received += n;
                    if (listener != null) listener.progress(received, total);
                }
                out.getFD().sync();
            }
            if (!hash.equals(sha256(part)) || !part.renameTo(target)) throw new IOException("Model integrity failed: " + name);
        } finally {
            connection.disconnect();
        }
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : digest.digest()) text.append(String.format("%02x", value));
        return text.toString();
    }
}
