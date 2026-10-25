package local.sliverwolf.assistant;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Arrays;
import static local.sliverwolf.assistant.DshTransport.object;

/** Opaque, bounded, authenticated uploads. No caller path, extraction, or activation. */
final class ArtifactInbox {
    private static final long MAX_FILE = 8L * 1024 * 1024, MAX_TOTAL = 32L * 1024 * 1024;
    private final File directory;
    private final DeviceOperations operations;
    ArtifactInbox(Context context, DeviceOperations operations) throws Exception {
        this.operations = operations;
        directory = new File(context.getFilesDir(), "artifact-inbox");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("storage-failed: artifact directory");
        JSONObject all = operations.all(); JSONArray keys = all.names();
        if (keys != null) for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i); JSONObject record = all.getJSONObject(key);
            if (!"upload".equals(record.optString("kind")) || !"unknown".equals(record.optString("state"))) continue;
            File complete = file(record, ".bin"), partial = file(record, ".part");
            if (complete.isFile() && valid(complete, record)) {
                record.put("state", "succeeded").put("detail", "Verified artifact recovered after client restart");
            } else {
                if (partial.exists() && !partial.delete()) throw new IllegalStateException("storage-failed: cannot remove incomplete upload");
                record.put("state", "failed").put("detail", "Interrupted upload discarded; use a new operationKey");
            }
            operations.update(key, record);
        }
    }
    JSONObject request(String method, JSONObject args) throws Exception {
        String key = args.getString("operationKey"); DeviceOperations.validateKey(key);
        if ("artifact/begin".equals(method)) {
            DeviceBridge.arguments(args, "operationKey", "size", "sha256", "purpose");
            long size = args.getLong("size"); String digest = args.getString("sha256");
            boolean apk="native-apk".equals(args.optString("purpose"));
            if(args.has("purpose")&&!apk)throw new IllegalArgumentException("Unknown artifact purpose");
            if (size < 1 || size > (apk?256L*1024*1024:MAX_FILE) || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid-input: artifact size/digest");
            String fingerprint = DeviceOperations.digest(size + ":" + digest);
            JSONObject previous = operations.previous(key, "upload", fingerprint);
            if (previous != null) return view(previous);
            long reserved = 0; int count = 0;
            JSONObject records = operations.all(); JSONArray keys = records.names();
            if (keys != null) for (int i = 0; i < keys.length(); i++) {
                JSONObject r = records.getJSONObject(keys.getString(i));
                if ("upload".equals(r.optString("kind")) && (file(r, ".bin").exists() || file(r, ".part").exists())) { reserved += r.optLong("size"); count++; }
            }
            // Include orphaned bytes in the physical limit even if an old journal record expired.
            long physical = 0; File[] files = directory.listFiles();
            if (files == null) throw new IllegalStateException("storage-failed: cannot inspect inbox");
            for (File entry : files) physical += entry.length();
            if (count >= 8 || files.length >= 8 || Math.max(reserved, physical) + size > (apk?512L*1024*1024:MAX_TOTAL)) throw new IllegalStateException("busy: artifact quota reached; discard an owned artifact");
            JSONObject record = operations.reserve(key, "upload", fingerprint);
            record.put("size", size).put("sha256", digest).put("received", 0);
            operations.update(key, record);
            if (!file(record, ".part").createNewFile()) throw new IllegalStateException("storage-failed: artifact already exists");
            return view(record);
        }
        JSONObject record = operations.get(key);
        if (!"upload".equals(record.optString("kind"))) throw new IllegalArgumentException("not-found: artifact operation");
        if ("artifact/discard".equals(method)) {
            DeviceBridge.arguments(args, "operationKey");
            for (String suffix : new String[]{".part", ".bin"}) {
                File target = file(record, suffix);
                if (target.exists() && !target.delete()) throw new IllegalStateException("storage-failed: cannot discard artifact");
            }
            record.put("state", "cancelled").put("detail", "Artifact discarded"); operations.update(key, record); return view(record);
        }
        if ("artifact/finish".equals(method)) {
            DeviceBridge.arguments(args, "operationKey");
            if ("succeeded".equals(record.optString("state"))) return view(record);
            if (!"pending".equals(record.optString("state"))) throw new IllegalStateException("invalid-state: upload is not pending");
            File partial = file(record, ".part");
            if (!valid(partial, record)) {
                if (partial.exists() && !partial.delete()) throw new IllegalStateException("storage-failed: cannot discard invalid artifact");
                record.put("state", "failed").put("detail", "Size or integrity mismatch"); operations.update(key, record);
                throw new IllegalArgumentException("integrity-failed: artifact rejected");
            }
            if (!partial.renameTo(file(record, ".bin"))) throw new IllegalStateException("storage-failed: cannot finalize artifact");
            record.put("state", "succeeded").put("detail", "Verified opaque artifact stored; not activated");
            operations.update(key, record); return view(record);
        }
        if (!"artifact/chunk".equals(method)) throw new IllegalArgumentException("unsupported-capability: artifact method");
        DeviceBridge.arguments(args, "operationKey", "offset", "base64");
        if (!"pending".equals(record.optString("state"))) throw new IllegalStateException("invalid-state: upload is not pending");
        String encoded = args.getString("base64");
        if (encoded.length() > 43692 || !encoded.matches("[A-Za-z0-9+/]+={0,2}")) throw new IllegalArgumentException("invalid-input: chunk encoding/size");
        byte[] chunk = Base64.decode(encoded, Base64.NO_WRAP); long offset = args.getLong("offset");
        if (chunk.length == 0 || chunk.length > 32768 || offset < 0 || offset + chunk.length > record.getLong("size")) throw new IllegalArgumentException("invalid-input: chunk bounds");
        try (RandomAccessFile output = new RandomAccessFile(file(record, ".part"), "rw")) {
            if (offset == output.length()) { output.seek(offset); output.write(chunk); output.getFD().sync(); }
            else if (offset + chunk.length <= output.length()) {
                byte[] prior = new byte[chunk.length]; output.seek(offset); output.readFully(prior);
                if (!Arrays.equals(prior, chunk)) throw new IllegalArgumentException("conflict: different bytes at previously accepted offset");
            } else throw new IllegalArgumentException("invalid-input: out-of-order chunk");
            record.put("received", output.length());
        }
        operations.update(key, record); return view(record);
    }
    File verified(String operationKey)throws Exception{
        JSONObject record=operations.get(operationKey);
        if(!"upload".equals(record.optString("kind"))||!"succeeded".equals(record.optString("state")))throw new IllegalStateException("Artifact is not complete");
        File result=file(record,".bin");if(!valid(result,record))throw new IllegalStateException("Artifact integrity mismatch");return result;
    }
    private File file(JSONObject record, String suffix) throws Exception {
        String id = record.getString("operationId");
        if (!id.matches("[0-9a-f-]{36}")) throw new IllegalStateException("storage-failed: invalid internal artifact ID");
        return new File(directory, id + suffix);
    }
    private boolean valid(File file, JSONObject record) throws Exception {
        if (!file.isFile() || file.length() != record.getLong("size")) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[32768];
        try (FileInputStream input = new FileInputStream(file)) { int count; while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count); }
        return DeviceOperations.hex(digest.digest()).equals(record.getString("sha256"));
    }
    private JSONObject view(JSONObject record) throws Exception {
        return object("artifactId", record.getString("operationId"), "state", record.getString("state"),
            "size", record.optLong("size"), "received", record.optLong("received"),
            "sha256", record.optString("sha256"), "detail", record.optString("detail"));
    }
}
