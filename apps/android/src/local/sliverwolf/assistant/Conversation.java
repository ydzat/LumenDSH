package local.sliverwolf.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Immutable server sequence IDs reconcile history and live frames without duplicate display. */
final class Conversation {
    final TreeMap<Long, JSONObject> events = new TreeMap<>();
    boolean hasMore;
    long oldest = Long.MAX_VALUE;
    private String attempt = "";
    private long revision = -1, nextIndex;
    private final StringBuilder live = new StringBuilder();
    String liveText() { return live.toString(); }
    void clear() { events.clear(); hasMore = false; oldest = Long.MAX_VALUE; attempt = ""; live.setLength(0); revision = -1; nextIndex = 0; }
    void liveSnapshot(JSONObject baseline) throws Exception {
        attempt = ""; live.setLength(0); nextIndex = 0;
        revision = baseline == null ? -1 : baseline.getLong("revision");
        JSONObject active = baseline == null ? null : baseline.optJSONObject("activeAttempt");
        if (active == null) return;
        attempt = active.getString("attemptId"); nextIndex = active.getLong("nextIndex");
        JSONArray stream = active.getJSONArray("stream");
        for (int i = 0; i < stream.length(); i++) {
            JSONObject record = stream.getJSONObject(i);
            if ("text-chunks".equals(record.optString("type"))) {
                JSONArray texts = record.getJSONArray("texts");
                for (int j = 0; j < texts.length(); j++) appendLive(texts.getString(j));
            } else if ("chunk".equals(record.optString("type"))) liveChunk(record.getJSONObject("chunk"));
        }
    }
    void liveFrame(JSONObject frame) throws Exception {
        long incoming = frame.getLong("revision");
        if (incoming <= revision) return;
        if (revision >= 0 && incoming != revision + 1) throw new IllegalStateException("Assistant stream gap");
        revision = incoming;
        String type = frame.getString("type"), id = frame.getString("attemptId");
        if ("start".equals(type)) { attempt = id; nextIndex = 0; live.setLength(0); return; }
        if (!attempt.equals(id) || frame.getLong("index") != nextIndex) throw new IllegalStateException("Assistant attempt gap");
        if ("chunk".equals(type)) { liveChunk(frame.getJSONObject("chunk")); nextIndex++; }
        else if ("end".equals(type)) { attempt = ""; live.setLength(0); }
        else throw new IllegalArgumentException("Unknown assistant frame");
    }
    private void liveChunk(JSONObject chunk) {
        if ("text-delta".equals(chunk.optString("type"))) appendLive(chunk.optString("text"));
    }
    private void appendLive(String text) {
        if (live.length() + text.length() > 2 * 1024 * 1024) throw new IllegalStateException("Assistant text limit");
        live.append(text);
    }
    void add(JSONObject event) throws Exception {
        long seq = event.getLong("seq");
        if (seq < 0) throw new IllegalArgumentException("Negative sequence");
        // Never silently discard events: ask the user to reload a bounded history window.
        if (!events.containsKey(seq) && events.size() >= 30000) throw new IllegalStateException("History window full; reconnect to reload");
        events.putIfAbsent(seq, event);
        oldest = Math.min(oldest, seq);
    }
    boolean containsRequest(String id) {
        for (JSONObject event : events.values()) {
            JSONObject data = event.optJSONObject("data");
            if (data == null) continue;
            if ("agent/inbox/spliced".equals(event.optString("type"))) {
                JSONArray inserted = data.optJSONArray("inserted");
                if (inserted != null) for (int i = 0; i < inserted.length(); i++) {
                    JSONObject message = inserted.optJSONObject(i);
                    JSONObject origin = message == null ? null : message.optJSONObject("source");
                    if (origin != null && "user".equals(origin.optString("kind")) && id.equals(origin.optString("rpcId"))) return true;
                }
            }
            JSONObject source = data.optJSONObject("source");
            if ("user/message".equals(event.optString("type")) && source != null && id.equals(source.optString("rpcId"))) return true;
        }
        return false;
    }
    String terminal() {
        for (JSONObject event : events.descendingMap().values()) {
            String type = event.optString("type");
            if ("turn/start".equals(type)) return "running";
            if ("turn/end".equals(type)) {
                JSONObject reason = event.optJSONObject("data").optJSONObject("reason");
                String kind = reason == null ? "unknown" : reason.optString("kind", "unknown");
                return "completed".equals(kind) ? "idle · completed" : kind;
            }
        }
        return "idle";
    }
    String text() {
        StringBuilder out = new StringBuilder();
        Map<String, StringBuilder> partial = new LinkedHashMap<>();
        for (JSONObject event : events.values()) {
            String type = event.optString("type");
            JSONObject data = event.optJSONObject("data");
            if (data == null) continue;
            String step = data.optInt("turn") + ":" + data.optInt("step");
            if (event.optJSONObject("surfaceOp") != null) {
                out.append("[Server history replacement; original transcript retained]\n");
                continue;
            }
            if ("user/message".equals(type)) {
                JSONObject source = data.optJSONObject("source");
                if (source != null && "user".equals(source.optString("kind"))) appendMessage(out, "You", data);
                else out.append("[Backend context updated]\n");
            }
            else if ("assistant/message".equals(type)) {
                partial.remove(step);
                appendMessage(out, "Assistant", data.optJSONObject("message"));
            } else if ("assistant/chunk".equals(type)) {
                JSONObject chunk = data.optJSONObject("chunk");
                if (chunk != null && "text-delta".equals(chunk.optString("type")))
                    partial.computeIfAbsent(step, key -> new StringBuilder()).append(chunk.optString("text"));
            } else if ("tool/call".equals(type)) out.append("Tool: ").append(data.optString("name")).append('\n');
            else if ("tool/result".equals(type)) appendMessage(out, "Tool result", data.optJSONObject("message"));
            // Replacement records are not interpreted as new human input.
        }
        for (StringBuilder text : partial.values()) if (text.length() > 0) out.append("Assistant (streaming)\n").append(text).append("\n\n");
        if (live.length() > 0) out.append("Assistant (streaming)\n").append(live).append("\n\n");
        return out.length() == 0 ? "No messages in this history window." : out.toString();
    }
    private void appendMessage(StringBuilder out, String role, JSONObject message) {
        if (message == null) return;
        JSONArray content = message.optJSONArray("content");
        if (content == null) return;
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && "text".equals(block.optString("type"))) body.append(block.optString("text")).append('\n');
            if (block != null && "tool-result".equals(block.optString("type"))) {
                JSONArray nested = block.optJSONArray("content");
                if (nested != null) for (int j = 0; j < nested.length(); j++) {
                    JSONObject part = nested.optJSONObject(j);
                    if (part != null && "text".equals(part.optString("type"))) body.append(part.optString("text")).append('\n');
                }
            }
        }
        if (body.length() > 0) out.append(role).append('\n').append(body).append('\n');
    }
}
