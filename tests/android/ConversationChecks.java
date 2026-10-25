package local.sliverwolf.assistant;

import org.json.JSONObject;

/** Synthetic parser checks only; these do not satisfy device acceptance scenarios. */
public final class ConversationChecks {
    private static JSONObject json(String value) throws Exception { return new JSONObject(value); }
    private static void require(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        Conversation conversation = new Conversation();
        JSONObject user = json("{type:'user/message',seq:1,data:{source:{kind:'user',rpcId:'request-1'},content:[{type:'text',text:'hello'}]}}");
        conversation.add(user);
        conversation.add(user);
        require(conversation.events.size() == 1, "snapshot/live overlap must deduplicate");
        require(conversation.containsRequest("request-1"), "delivery must reconcile from source rpcId");
        require(!conversation.containsRequest("unknown"), "unknown delivery must stay unknown");
        conversation.add(json("{type:'assistant/chunk',seq:3,data:{turn:1,step:1,chunk:{type:'text-delta',text:'partial'}}}"));
        require(conversation.text().contains("partial"), "text delta must render before completion");
        conversation.add(json("{type:'turn/start',seq:0,data:{turn:1}}"));
        conversation.add(json("{type:'user/message',seq:2,data:{source:{kind:'plugin'},content:[{type:'text',text:'internal context'}]}}"));
        require(!conversation.text().contains("internal context"), "backend injection must not be shown as user text");
        conversation.add(json("{type:'assistant/message',seq:4,data:{turn:1,step:1,message:{content:[{type:'text',text:'final'}]}}}"));
        require(!conversation.text().contains("partial"), "assembled message must replace partial stream");
        require(conversation.text().contains("final"), "assembled response must render");
        conversation.add(json("{type:'turn/end',seq:5,data:{turn:1,reason:{kind:'aborted'}}}"));
        require(conversation.terminal().equals("aborted"), "cancelled is not completed");
        conversation.add(json("{type:'user/message',seq:6,surfaceOp:{op:'replace',start:1,end:4},data:{source:{kind:'plugin'},content:[{type:'text',text:'compaction copy'}]}}"));
        require(!conversation.text().contains("compaction copy"), "replacement is not a new user message");
        require(conversation.events.firstKey() == 0, "late history must merge in server order");
        System.out.println("ConversationChecks: 10 assertions passed");
    }
}
