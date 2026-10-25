package local.sliverwolf.assistant;

import org.json.JSONObject;

/** Synthetic event correlation checks, not microphone or playback acceptance. */
public final class VoiceReplyChecks {
    private static void add(Conversation c,String value)throws Exception{c.add(new JSONObject(value));}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        Conversation c=new Conversation();
        add(c,"{seq:1,type:'turn/start',data:{turn:1}}");
        add(c,"{seq:2,type:'user/message',data:{source:{rpcId:'voice-a'}}}");
        add(c,"{seq:3,type:'assistant/message',data:{turn:1,message:{content:[{type:'text',text:'reply a'}]}}}");
        require(VoiceReply.find(c,"voice-a")==null,"Do not play unfinished replies");
        add(c,"{seq:4,type:'turn/end',data:{turn:1,reason:{kind:'completed'}}}");
        require("reply a".equals(VoiceReply.find(c,"voice-a").text),"Select completed matching request");
        require(VoiceReply.find(c,"unknown")==null,"Do not speak unrelated history");
        add(c,"{seq:5,type:'turn/start',data:{turn:2}}");
        add(c,"{seq:6,type:'user/message',data:{source:{rpcId:'voice-b'}}}");
        add(c,"{seq:7,type:'tool/call',data:{turn:2,name:'mcp__sliver_wolf__assistant_speak'}}");
        add(c,"{seq:8,type:'assistant/message',data:{turn:2,message:{content:[{type:'text',text:'reply b'}]}}}");
        add(c,"{seq:9,type:'turn/end',data:{turn:2,reason:{kind:'completed'}}}");
        require(VoiceReply.find(c,"voice-b").text.isEmpty(),"Explicit speech suppresses duplicate readout");
        require("reply a".equals(VoiceReply.find(c,"voice-a").text),"Later turns cannot replace earlier correlated reply");
        System.out.println("Voice reply correlation: 5 checks passed (synthetic events)");
    }
}
