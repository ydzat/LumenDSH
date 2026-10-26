package local.sliverwolf.web;

import org.json.JSONArray;
import org.json.JSONObject;

/** Selects only the completed server turn caused by one submitted voice RPC. */
final class VoiceReply {
    final String text,notice;
    private VoiceReply(String text,String notice){this.text=text;this.notice=notice;}
    static VoiceReply find(Conversation conversation,String request) {
        int current=-1,target=-1;String reply="";boolean explicit=false;
        for(JSONObject event:conversation.events.values()) {
            JSONObject data=event.optJSONObject("data");if(data==null)continue;
            String type=event.optString("type");
            if("turn/start".equals(type))current=data.optInt("turn",-1);
            JSONObject source=data.optJSONObject("source");
            if("user/message".equals(type)&&source!=null&&request.equals(source.optString("rpcId")))target=current;
            if(target<0||data.optInt("turn",-1)!=target)continue;
            if("tool/call".equals(type)&&"mcp__sliver_wolf__assistant_speak".equals(data.optString("name")))explicit=true;
            if("assistant/message".equals(type)) {
                JSONObject message=data.optJSONObject("message");JSONArray blocks=message==null?null:message.optJSONArray("content");
                StringBuilder text=new StringBuilder();
                if(blocks!=null)for(int i=0;i<blocks.length();i++) {
                    JSONObject block=blocks.optJSONObject(i);
                    if(block!=null&&"text".equals(block.optString("type")))text.append(block.optString("text")).append('\n');
                }
                if(text.length()>0)reply=text.toString().trim();
            }
            if("turn/end".equals(type)) {
                JSONObject reason=data.optJSONObject("reason");
                if(reason==null||!"completed".equals(reason.optString("kind")))return new VoiceReply("","任务未正常完成，请查看文字记录");
                if(explicit||reply.isEmpty())return new VoiceReply("","回复完成，无需重复播报");
                if(reply.length()>1000)return new VoiceReply("","回复过长，请查看文字记录");
                return new VoiceReply(reply,"");
            }
        }
        return null;
    }
}
