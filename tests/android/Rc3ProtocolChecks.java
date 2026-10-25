package local.sliverwolf.assistant;

import org.json.*;
import static local.sliverwolf.assistant.DshTransport.object;

/** Parser regression checks; these are not evidence of live DSH behavior. */
public final class Rc3ProtocolChecks {
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    public static void main(String[] args) throws Exception {
        Rc3Protocol protocol = new Rc3Protocol();
        check(protocol.arguments("session.list", object(), "r").getJSONObject("args").has("_request"), "named list argument");
        JSONObject prompt = protocol.arguments("session.prompt", object("sessionId", "s", "mode", "queue"), "stable-id");
        check("stable-id".equals(prompt.getJSONObject("args").getJSONObject("request").getString("requestId")), "stable prompt ID");
        protocol.frame(false, "s", object("type", "snapshot", "cursor", 23));
        JSONObject page = protocol.arguments("session.history", object("sessionId", "s", "beforeSeq", 10), "r").getJSONObject("args").getJSONObject("request");
        check(page.getLong("throughSeq") == 23 && page.getLong("beforeSeq") == 10 && !page.has("sessionId"), "page cursor");
        protocol.frame(true, "s", object("type", "ready", "clientId", "generation-one"));
        JSONObject decision = protocol.frame(true, "s", object("type", "waterfall", "event", "approval/request", "eventId", "decision",
            "agentId", "s", "request", object("toolName", "Bash")));
        check("approval/requested".equals(decision.getJSONObject("payload").getString("type")), "decision mapping");
        JSONObject answer = protocol.answer("decision", object("sessionId", "s", "outcome", "allowed-once"));
        check("allowed-once".equals(answer.getJSONObject("outcome").getString("value")), "upstream approval value");
        check("generation-one".equals(answer.getString("clientId")), "decision generation");
        protocol.frame(true, "s", object("type", "cancel", "eventId", "decision"));
        boolean refused = false;
        try { protocol.answer("decision", object("sessionId", "s", "outcome", "allowed-once")); } catch (java.io.IOException expected) { refused = true; }
        check(refused, "cancelled decision cannot be answered");
        JSONObject projection = protocol.control("s", object("type", "projection", "sessionId", "s", "seq", 25,
            "key", "userQuestions", "value", object("active", new JSONArray(), "settled", new JSONArray())));
        check(projection.getJSONObject("payload").getLong("asOfSeq") == 25, "question projection retains watermark");
        JSONObject late = protocol.arguments("userQuestions.answer", object("agentId", "s", "callId", "c", "answer", object("answers", new JSONArray())), "r");
        check(late.getJSONObject("args").getString("agentId").equals("s") && !late.getJSONObject("args").has("request"), "late answer named arguments");
        Conversation c = new Conversation();
        c.liveSnapshot(object("revision", 0));
        c.liveFrame(object("type", "start", "revision", 1, "attemptId", "a"));
        c.liveFrame(object("type", "chunk", "revision", 2, "attemptId", "a", "index", 0, "chunk", object("type", "text-delta", "text", "Hello")));
        c.liveFrame(object("type", "chunk", "revision", 2, "attemptId", "a", "index", 0, "chunk", object("type", "text-delta", "text", "Hello")));
        check("Hello".equals(c.liveText()) && c.events.isEmpty(), "live chunks have no invented durable sequence");
        c.liveSnapshot(object("revision", 8, "activeAttempt", object("attemptId", "a", "nextIndex", 4,
            "stream", new JSONArray().put(object("type", "text-chunks", "texts", new JSONArray().put("Hello").put(" again"))))));
        check("Hello again".equals(c.liveText()), "reconnect restores active attempt");
        c.liveFrame(object("type", "end", "revision", 9, "attemptId", "a", "index", 4));
        check(c.liveText().isEmpty(), "terminal clears transient text");
        System.out.println("RC3_PROTOCOL_CHECKS_PASSED");
    }
}
