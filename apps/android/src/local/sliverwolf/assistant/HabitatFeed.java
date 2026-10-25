package local.sliverwolf.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Locale;

/** A bounded view of public session events, never a synthetic reasoning or memory feed. */
final class HabitatFeed {
    final AttentionQueue.Source source=AttentionQueue.Source.TASK;
    final String title, trace, phase;
    final long sequence;
    final boolean searching;
    HabitatFeed(String title,String trace,long sequence,boolean searching) {
        this(title,trace,sequence,searching,"idle");
    }
    HabitatFeed(String title,String trace,long sequence,boolean searching,String phase) {
        this.title=title;this.trace=trace;this.sequence=sequence;this.searching=searching;this.phase=phase;
    }
    static HabitatFeed from(Conversation conversation) {
        ArrayDeque<String> lines=new ArrayDeque<>();
        java.util.Map<String,String> memoryCalls=new java.util.HashMap<>();
        String title="等待下一次交互";long sequence=-1;boolean searching=false;String phase="idle";
        for(JSONObject event:conversation.events.values()) {
            JSONObject data=event.optJSONObject("data");if(data==null)continue;
            String type=event.optString("type"),line="";
            if(type.equals("turn/start")) {lines.clear();phase="thinking";title="任务进行中";searching=false;line="TURN / started";}
            else if(type.equals("tool/call")) {
                phase="working";String name=clean(data.optString("name"),100);
                // Tool names describe activity, not a verified provider/network health state.
                String lower=name.toLowerCase(Locale.ROOT);
                if(lower.startsWith("mnemon_"))memoryCalls.put(data.optString("callId"),name);
                searching=lower.contains("search")||lower.contains("browse")||lower.contains("fetch");
                title=searching?"检索工具运行中":"工具运行中";
                if(lower.startsWith("mnemon_")) title=(lower.contains("recall")||lower.contains("search")||lower.contains("bodies"))?"正在查阅记忆":"正在整理记忆";
                line="CALL / "+name;
            } else if(type.equals("tool/result")) {
                phase="thinking";line="RESULT / "+excerpt(data.optJSONObject("message"));
                title="工具已返回";searching=false;
                JSONObject meta=data.optJSONObject("meta");
                JSONObject message=data.optJSONObject("message"),messageSource=message==null?null:message.optJSONObject("source");
                String callId=messageSource==null?"":messageSource.optString("callId");
                if(memoryCalls.remove(callId)!=null && !data.has("error") && meta!=null && "dsh-mnemon.activity/v1".equals(meta.optString("schema"))) {
                    boolean committed=meta.optBoolean("committed");
                    title=committed?"记忆已保存":"记忆工具已返回";
                    line="MEMORY / "+(committed?"committed":"returned")+" / "+clean(meta.optString("sourceTypeId"),50);
                    JSONArray items=meta.optJSONArray("items");
                    JSONObject item="write".equals(meta.optString("kind"))&&committed?meta.optJSONObject("item"):
                        "read".equals(meta.optString("kind"))&&items!=null&&items.length()>0&&items.length()<=8?items.optJSONObject(0):null;
                    if(item!=null && !item.optString("id").isEmpty() && !item.optString("title").isEmpty()) {
                        title=committed?"整理了一段记忆":"想起一段记忆";
                        line=(committed?"记忆整理 / ":"记忆召回 / ")+clean(item.optString("excerpt",item.optString("title")),200)
                            +" · 来源 "+clean(item.optString("id"),70);
                    }
                }
            } else if(type.equals("assistant/chunk")) {
                JSONObject chunk=data.optJSONObject("chunk");
                if(chunk!=null&&"text-delta".equals(chunk.optString("type"))&&!chunk.optString("text").isEmpty()){phase="streaming";title="正在输出";}
            } else if(type.equals("turn/end")) {
                JSONObject reason=data.optJSONObject("reason");String kind=reason==null?"unknown":reason.optString("kind","unknown");
                phase="completed".equals(kind)?"done":("failed".equals(kind)||"error".equals(kind))?"error":"idle";
                line="TURN / "+clean(kind,60);title="completed".equals(kind)?"本轮已完成":"本轮结束 · "+clean(kind,40);searching=false;
            }
            if(!line.isEmpty()) {lines.addLast(line);while(lines.size()>5)lines.removeFirst();sequence=event.optLong("seq",-1);}
        }
        if (!conversation.liveText().isEmpty()) { phase="streaming"; title="正在输出"; searching=false; }
        return new HabitatFeed(title,String.join("\n",lines),sequence,searching,phase);
    }
    private static String excerpt(JSONObject message) {
        if(message==null)return "内容见文字记录";
        JSONArray blocks=message.optJSONArray("content");StringBuilder text=new StringBuilder();
        if(blocks!=null)for(int i=0;i<blocks.length()&&text.length()<260;i++) {
            JSONObject block=blocks.optJSONObject(i);if(block==null)continue;
            if("text".equals(block.optString("type")))text.append(block.optString("text")).append(' ');
            if("tool-result".equals(block.optString("type"))) {
                JSONArray nested=block.optJSONArray("content");
                if(nested!=null)for(int j=0;j<nested.length()&&text.length()<260;j++) {
                    JSONObject part=nested.optJSONObject(j);
                    if(part!=null&&"text".equals(part.optString("type")))text.append(part.optString("text")).append(' ');
                }
            }
        }
        String result=clean(text.toString(),240);return result.isEmpty()?"内容见文字记录":result;
    }
    private static String clean(String text,int limit) {
        String value=text.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        return value.length()<=limit?value:value.substring(0,limit)+"…";
    }
}
