package local.sliverwolf.assistant;

import org.json.JSONObject;

/** Event projection checks with synthetic inputs; actual UI acceptance is separate. */
public final class HabitatChecks {
    private static void add(Conversation c,String json) throws Exception {c.add(new JSONObject(json));}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Conversation c=new Conversation();
        add(c,"{seq:0,type:'turn/start',data:{}}");
        add(c,"{seq:1,type:'tool/call',data:{name:'web_search'}}");
        require(HabitatFeed.from(c).searching,"Search tool activity derives from a real event name");
        add(c,"{seq:2,type:'tool/result',data:{message:{content:[{type:'reasoning',text:'DO_NOT_RENDER'},{type:'tool-result',content:[{type:'text',text:'PUBLIC_RESULT'}]}]}}}");
        HabitatFeed feed=HabitatFeed.from(c);
        require(feed.trace.contains("PUBLIC_RESULT")&&!feed.trace.contains("DO_NOT_RENDER"),"Only ordinary public result text is projected");
        require(!feed.searching&&feed.sequence==2,"Result settles activity");
        Conversation memory=new Conversation();
        add(memory,"{seq:0,type:'tool/call',data:{name:'mnemon_recall',callId:'recall-1'}}");
        add(memory,"{seq:1,type:'tool/result',data:{message:{source:{callId:'recall-1'}},meta:{schema:'dsh-mnemon.activity/v1',kind:'read',sourceTypeId:'memory-spaces',items:[{id:'fact-1',title:'Controlled fact',excerpt:'A sourced shared experience'}]}}}");
        require(HabitatFeed.from(memory).trace.contains("A sourced shared experience")&&HabitatFeed.from(memory).trace.contains("fact-1"),"Memory excerpts preserve upstream record identity");
        add(memory,"{seq:2,type:'tool/result',data:{message:{source:{callId:'unknown'}},meta:{schema:'dsh-mnemon.activity/v1',kind:'read',items:[{id:'fake',title:'DO_NOT_ACCEPT_METADATA'}]}}}");
        require(!HabitatFeed.from(memory).trace.contains("DO_NOT_ACCEPT_METADATA"),"Unmatched memory metadata is not a trusted source");
        add(c,"{seq:3,type:'assistant/chunk',data:{chunk:{type:'reasoning-delta',text:'PRIVATE'}}}");
        require(HabitatFeed.from(c).sequence==2,"Reasoning does not create visual activity");
        for(int i=4;i<14;i++)add(c,"{seq:"+i+",type:'tool/call',data:{name:'bounded_tool'}}");
        require(HabitatFeed.from(c).trace.split("\\n").length==5,"Latest five activity lines only");
        add(c,"{seq:14,type:'turn/start',data:{}}");
        require(!HabitatFeed.from(c).trace.contains("bounded_tool"),"New turn clears old background work");
        add(c,"{seq:15,type:'turn/end',data:{reason:{kind:'failed'}}}");
        require(HabitatFeed.from(c).trace.contains("failed"),"Failure remains failure");
        HabitatState state=new HabitatState();state.accept("done",15,0);
        require(state.visible(true,false,false,false,1).equals("idle"),"Historical completion is not celebrated");
        state.accept("thinking",16,10);state.accept("streaming",16,20);state.accept("done",20,30);
        require(state.visible(true,false,false,false,100).equals("done"),"Live completion holds");
        state.accept("done",20,2000);
        require(state.visible(true,false,false,false,2630).equals("idle"),"Reconciliation cannot extend completion");
        require(state.visible(false,false,false,false,100).equals("offline"),"Disconnect differs from task error");
        state.accept("error",21,3000);
        require(state.visible(true,false,false,false,3001).equals("error"),"Task failure remains explicit");
        require(state.visible(true,true,false,false,3001).equals("waiting"),"Decision owns interaction");
        PresentationSettings defaults=PresentationSettings.parse(PresentationSettings.defaults());
        require(defaults.size==1&&defaults.phrases.length==0,"Default presentation has no retired placeholder dialogue");
        require(defaults.contentWeights[AttentionQueue.Source.REPLY.ordinal()]==4,"Old profiles migrate source weights");
        JSONObject kinds=new JSONObject(PresentationSettings.defaults());
        kinds.put("contentWeights",new JSONObject("{reply:2,preset:3,notification:1}"));
        PresentationSettings kindSettings=PresentationSettings.parse(kinds.toString());
        require(kindSettings.contentWeights[1]==3,"Source weights parsed");
        AttentionQueue queue=new AttentionQueue();queue.configure(kindSettings.contentWeights);
        queue.offer(10,"Reply");queue.pin(true);queue.offerPreset("Preset");
        queue.offerSource(AttentionQueue.Source.NOTIFICATION,"receipt:1","Notice");
        require(!queue.tick(100000,false)&&queue.current().source==AttentionQueue.Source.REPLY,"Reading cannot be preempted by another source");
        queue.pin(false);require(!queue.tick(100000,true),"Occupied attention pauses expiry");
        queue.next();require(queue.current().source==AttentionQueue.Source.PRESET,"Weighted eligible source selection");
        queue.next();require(queue.current().source==AttentionQueue.Source.NOTIFICATION,"Other queued sources remain available");
        queue.offerSource(AttentionQueue.Source.NOTIFICATION,"receipt:1","Updated notice");
        require(queue.pending()==0&&queue.current().text.equals("Updated notice"),"Same source reference reconciles in place");
        queue.clear();queue.offer(10,"Reply");queue.next();queue.offer(10,"Replay");
        require(queue.current()==null,"Dismissed server reply is not resurrected");
        boolean invalidKind=false;kinds.put("contentWeights",new JSONObject("{thought:1}"));
        try{PresentationSettings.parse(kinds.toString());}catch(Exception expected){invalidKind=true;}
        require(invalidKind,"Unknown source kinds reject");
        JSONObject settings=new JSONObject(PresentationSettings.defaults());settings.put("size",5);
        boolean rejected=false;try{PresentationSettings.parse(settings.toString());}catch(Exception expected){rejected=true;}
        require(rejected,"Oversized character setting rejected before storage");
        settings.put("size",1);settings.put("schemaVersion",2);rejected=false;
        try{PresentationSettings.parse(settings.toString());}catch(Exception expected){rejected=true;}
        require(rejected,"Unknown schema rejected");
        JSONObject extended=new JSONObject(PresentationSettings.defaults());
        // Old imported profiles may still contain weighted phrases; defaults do not.
        extended.put("phrases",new org.json.JSONArray("[\"Legacy first\",\"Legacy second\"]"));
        extended.put("animationSpeed",.5);extended.put("phraseWeights",new org.json.JSONArray("[1,3]"));
        PresentationSettings weighted=PresentationSettings.parse(extended.toString());
        require(weighted.animationSpeed==.5f&&weighted.phraseWeights[1]==3,"Optional presentation fields parse");
        java.util.Random chooser=new java.util.Random(7);int second=0;for(int i=0;i<4000;i++)if(weighted.phrase(chooser).equals(weighted.phrases[1]))second++;
        require(second>2800&&second<3200,"Weighted phrase choice follows declared distribution");
        extended.put("phraseWeights",new org.json.JSONArray("[0,3]"));rejected=false;
        try{PresentationSettings.parse(extended.toString());}catch(Exception expected){rejected=true;}require(rejected,"Zero weights rejected");
        extended.remove("phraseWeights");extended.put("script","do not execute");rejected=false;
        try{PresentationSettings.parse(extended.toString());}catch(Exception expected){rejected=true;}require(rejected,"Unknown executable fields rejected");
        if(args.length>0) {
            java.io.File history=new java.io.File(args[0],"presentation-checks");String original=PresentationSettings.defaults();
            String id=PresentationSettings.archive(history,original);require(PresentationSettings.readVersion(history,id).equals(original),"Version roundtrip");
            require(PresentationSettings.archive(history,original).equals(id),"Repeated save is same immutable version");
            JSONObject next=new JSONObject(original);next.put("animationSpeed",.75);String secondId=PresentationSettings.archive(history,next.toString());
            require(!secondId.equals(id)&&PresentationSettings.readVersion(history,id).equals(original),"New version preserves old version");
            java.nio.file.Files.write(new java.io.File(history,id+".json").toPath(),next.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            rejected=false;try{PresentationSettings.readVersion(history,id);}catch(Exception expected){rejected=true;}require(rejected,"Tampered version rejected");
        }
        System.out.println("HABITAT_PROJECTION_CHECKS_PASS (synthetic event mapping, not device acceptance)");
    }
}
