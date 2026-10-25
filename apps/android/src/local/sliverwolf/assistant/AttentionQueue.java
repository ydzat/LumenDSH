package local.sliverwolf.assistant;

import java.util.ArrayDeque;

/** Presentation backlog only: complete content remains in the server-owned transcript. */
final class AttentionQueue {
    enum Source {
        REPLY("reply",""), PRESET("preset","预设台词 · "),
        TASK("task","任务状态 · "), NOTIFICATION("notification","完成提醒 · "),
        MEMORY("memory","记忆 · ");
        final String key,label;
        Source(String key,String label){this.key=key;this.label=label;}
    }
    static final long LIFETIME_MS=12000;
    long lifetimeMs=LIFETIME_MS;
    private long auxiliarySequence=-2;
    static final class Item {
        final long sequence;
        final Source source;
        final String reference;
        String text;
        Item(long sequence,String text) {this(sequence,text,Source.REPLY,"event:"+sequence);}
        Item(long sequence,String text,Source source,String reference) {
            this.sequence=sequence;this.text=text;this.source=source;this.reference=reference;
        }
    }
    private final int[] weights={4,1,2,2,1},credits=new int[Source.values().length];
    void configure(int[] values){System.arraycopy(values,0,weights,0,weights.length);java.util.Arrays.fill(credits,0);}
    private final ArrayDeque<Item> waiting=new ArrayDeque<>();
    private Item current;
    private long newest=-1, visibleTime;
    private int overflow;
    private boolean reading;
    void offer(long sequence,String text) {
        if(current!=null&&current.sequence==sequence) {current.text=text;return;}
        for(Item item:waiting)if(item.sequence==sequence){item.text=text;return;}
        if(sequence<=newest||text.isEmpty()) return;
        newest=sequence;Item item=new Item(sequence,text);
        if(current==null) {current=item;visibleTime=0;}
        else if(waiting.size()<8) waiting.addLast(item);
        else overflow++;
    }
    Item current() {return current;}
    void offerPreset(String text) {
        offerSource(Source.PRESET,"preset:"+auxiliarySequence,text);
    }
    void offerSource(Source source,String reference,String text) {
        if(source==Source.REPLY)throw new IllegalArgumentException("Replies require server sequence");
        if(reference==null||reference.trim().isEmpty())throw new IllegalArgumentException("Source reference required");
        if(text==null||text.isEmpty())return;
        if(current!=null&&current.source==source&&current.reference.equals(reference)){current.text=text;return;}
        for(Item queued:waiting)if(queued.source==source&&queued.reference.equals(reference)){queued.text=text;return;}
        Item item=new Item(auxiliarySequence--,text,source,reference);
        if(current==null){current=item;visibleTime=0;}
        else if(waiting.size()<8)waiting.addLast(item);
        else overflow++;
    }
    boolean reading() {return reading;}
    int pending() {return waiting.size();}
    int overflow() {return overflow;}
    void acknowledgeOverflow() {overflow=0;}
    void pin(boolean value) {reading=value&&current!=null;visibleTime=0;}
    void next() {
        // Smooth weighted service between eligible sources; FIFO within each source.
        // Never preempt a pinned/current item or change approval and speech ownership.
        boolean[] present=new boolean[weights.length];for(Item item:waiting)present[item.source.ordinal()]=true;
        int selected=-1,total=0;
        for(int i=0;i<weights.length;i++) {
            if(!present[i]){credits[i]=0;continue;}
            total+=weights[i];credits[i]+=weights[i];
            if(selected<0||credits[i]>credits[selected])selected=i;
        }
        current=null;
        if(selected>=0){credits[selected]-=total;java.util.Iterator<Item> items=waiting.iterator();
            while(items.hasNext()){Item item=items.next();if(item.source.ordinal()==selected){current=item;items.remove();break;}}}
        reading=false;visibleTime=0;
    }
    boolean tick(long elapsed,boolean blocked) {
        if(current==null||reading||blocked) return false;
        visibleTime+=Math.max(0,Math.min(1000,elapsed));
        if(visibleTime<lifetimeMs) return false;
        next();return true;
    }
    void clear() {waiting.clear();current=null;reading=false;newest=-1;visibleTime=0;overflow=0;java.util.Arrays.fill(credits,0);}
}
