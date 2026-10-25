package local.sliverwolf.assistant;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import static local.sliverwolf.assistant.DshTransport.object;

/** Serialized operation journal. Persist acceptance before side effects; never replay after process loss. */
final class DeviceOperations {
    private final SharedPreferences preferences;
    DeviceOperations(SharedPreferences preferences) { this.preferences = preferences; }
    synchronized JSONObject all() throws Exception { return new JSONObject(preferences.getString("operations", "{}")); }
    static void validateKey(String key) {
        if (!key.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("invalid-input: operationKey");
    }
    static String digest(String input) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    }
    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    synchronized JSONObject get(String key) throws Exception {
        validateKey(key); JSONObject result = all().optJSONObject(key);
        return result == null ? object("state", "unknown", "detail", "No retained operation") : result;
    }
    synchronized JSONObject previous(String key, String kind, String fingerprint) throws Exception {
        validateKey(key); JSONObject record = all().optJSONObject(key);
        if (record == null) return null;
        // Preview records were notification-only and did not store an argument fingerprint.
        String previousKind = record.optString("kind", "show");
        String previousInput = record.optString("inputDigest", digest("notification"));
        if (!kind.equals(previousKind) || !fingerprint.equals(previousInput))
            throw new IllegalArgumentException("conflict: operationKey already belongs to different arguments");
        return record;
    }
    synchronized JSONObject reserve(String key, String kind, String fingerprint) throws Exception {
        JSONObject existing = previous(key, kind, fingerprint);
        if (existing != null) return existing;
        JSONObject records = all(); long now = System.currentTimeMillis();
        JSONArray keys = records.names();
        if (keys != null) for (int i = 0; i < keys.length(); i++) {
            JSONObject record = records.getJSONObject(keys.getString(i));
            if (now - record.optLong("createdAt") > 86400000L
                    && (!"upload".equals(record.optString("kind")) || "cancelled".equals(record.optString("state")) || "failed".equals(record.optString("state")))
                    && !"pending".equals(record.optString("state")) && !"running".equals(record.optString("state"))) records.remove(keys.getString(i));
        }
        if (records.length() >= 64) throw new IllegalStateException("busy: operation retention limit reached");
        JSONObject record = object("operationId", UUID.randomUUID().toString(), "kind", kind,
            "inputDigest", fingerprint, "createdAt", now, "state", "pending", "detail", "Accepted; not completed");
        records.put(key, record); save(records); return record;
    }
    synchronized void update(String key, JSONObject record) throws Exception {
        JSONObject records = all(); records.put(key, record); save(records);
    }
    synchronized void transition(String key, String state, String detail) throws Exception {
        JSONObject record = get(key); record.put("state", state).put("detail", detail); update(key, record);
    }
    synchronized void recover() throws Exception {
        JSONObject records = all(); JSONArray keys = records.names(); boolean changed = false;
        if (keys != null) for (int i = 0; i < keys.length(); i++) {
            JSONObject record = records.getJSONObject(keys.getString(i));
            if ("pending".equals(record.optString("state")) || "running".equals(record.optString("state"))) {
                record.put("state", "unknown").put("detail", "Client process ended before completion was recorded; not replayed"); changed = true;
            }
        }
        if (changed) save(records);
    }
    private void save(JSONObject records) {
        if (!preferences.edit().putString("operations", records.toString()).commit())
            throw new IllegalStateException("storage-failed: cannot persist operation state");
    }
}
