package local.sliverwolf.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import static local.sliverwolf.assistant.DshTransport.object;

/** Adapter for the inspected 0.2.1-rc.3 Session Remote and owner event stream. */
final class Rc3Protocol {
    private volatile long cursor = -1;
    private volatile String clientId = "";
    private final ConcurrentHashMap<String, JSONObject> pending = new ConcurrentHashMap<>();

    JSONObject arguments(String method, JSONObject input, String requestId) throws Exception {
        JSONObject request = new JSONObject(input.toString());
        if ("userQuestions.answer".equals(method)) return object("args", request);
        if ("session.list".equals(method)) return object("args", object("_request", request));
        if ("session.prompt".equals(method)) request.put("requestId", requestId);
        if ("session.history".equals(method)) {
            if (cursor < 0) throw new IOException("Session snapshot is not ready");
            request.put("address", object("kind", "session", "sessionId", request.getString("sessionId")));
            request.remove("sessionId");
            request.put("throughSeq", cursor);
        }
        return object("args", object("request", request));
    }

    JSONObject result(String method, JSONObject value) throws Exception {
        if ("session.history".equals(method)) return object("events", value.getJSONArray("records"), "hasMore", value.getBoolean("hasMore"));
        return value;
    }

    JSONObject open(boolean host, String session) {
        JSONObject args = host ? object() : object("request", object("address", object("kind", "session", "sessionId", session),
            "maxMessages", 50, "assistantStream", true));
        return object("type", "open", "streamId", "main", "endpoint", host ? "$events" : "session/follow", "payload", object("args", args));
    }

    JSONObject controlOpen() { return object("type", "open", "streamId", "control", "endpoint", "session/control", "payload", object("args", object())); }
    JSONObject control(String session, JSONObject value) throws Exception {
        JSONObject values = null;
        long seq = -1;
        if ("baseline".equals(value.optString("type"))) {
            JSONObject projection = value.getJSONObject("value").getJSONObject("projections").optJSONObject(session);
            if (projection != null) { values = projection.getJSONObject("values"); seq = projection.getLong("asOfSeq"); }
        } else if ("projection".equals(value.optString("type")) && session.equals(value.optString("sessionId")) &&
                ("userQuestions".equals(value.optString("key")) || "modelSelection".equals(value.optString("key")) || "contextPressure".equals(value.optString("key")))) {
            values = object(value.getString("key"), value.get("value"));
            seq = value.getLong("seq");
        }
        return values == null ? null : event("", object("type", "session/projections", "sessionId", session, "values", values, "asOfSeq", seq));
    }

    JSONObject frame(boolean host, String session, JSONObject value) throws Exception {
        String type = value.getString("type");
        if (!host) {
            if ("snapshot".equals(type)) {
                cursor = value.getLong("cursor");
                return event("", object("type", "session/snapshot", "sessionId", session, "snapshot", value));
            }
            if ("event".equals(type)) {
                JSONObject durable = value.getJSONObject("event");
                cursor = Math.max(cursor, durable.getLong("seq"));
                return event("", object("type", "session/event", "sessionId", session, "event", durable));
            }
            if ("assistant-stream".equals(type)) return event("", object("type", "session/assistant-stream", "sessionId", session, "frame", value.getJSONObject("frame")));
            throw new IOException("Unknown session frame");
        }
        if ("ready".equals(type)) { clientId = value.getString("clientId"); return null; }
        if ("waterfall".equals(type)) {
            String name = value.getString("event"), id = value.getString("eventId");
            if (!"approval/request".equals(name) && !"user-questions/request".equals(name)) throw new IOException("Unsupported decision");
            JSONObject request = new JSONObject(value.getJSONObject("request").toString());
            request.put("type", "approval/request".equals(name) ? "approval/requested" : "question/requested");
            request.put("sessionId", value.getString("agentId"));
            request.put("approvalId", id);
            pending.put(id, object("clientId", clientId, "event", name, "sessionId", value.getString("agentId")));
            return event(id, request);
        }
        if ("cancel".equals(type)) {
            String id = value.getString("eventId");
            JSONObject removed = pending.remove(id);
            if (removed == null) return null;
            return event(id, object("type", "question/resolved", "sessionId", removed.getString("sessionId"), "questionRpcId", id));
        }
        if ("emit".equals(type)) {
            String name = value.getString("event");
            JSONArray args = value.getJSONArray("args");
            if (!name.startsWith("api-session/")) return null;
            String action = name.substring("api-session/".length());
            String id = "added".equals(action) ? args.getJSONObject(0).getString("sessionId") : args.getString(0);
            JSONObject payload = object("type", "host/session-" + action, "sessionId", id);
            if ("status".equals(action)) payload.put("running", args.getBoolean(1));
            if ("error".equals(action)) { payload.put("type", "host/agent-error"); payload.put("message", "DSH reported a task error"); }
            return event("", payload);
        }
        throw new IOException("Unknown host frame");
    }

    JSONObject answer(String id, JSONObject value) throws Exception {
        JSONObject request = pending.get(id);
        if (request == null || !clientId.equals(request.getString("clientId"))) throw new IOException("Decision stream expired");
        if (!request.getString("sessionId").equals(value.getString("sessionId"))) throw new IOException("Decision session mismatch");
        Object answer = "approval/request".equals(request.getString("event")) ? value.getString("outcome") : value.getJSONObject("answer");
        return object("clientId", request.getString("clientId"), "eventId", id, "outcome", object("kind", "result", "value", answer));
    }

    private JSONObject event(String id, JSONObject payload) { return object("type", "server-request", "rpcId", id, "payload", payload); }
}
