package local.sliverwolf.assistant;

/** Native adaptation of whale-shota tick's completion hold, MIT (c) 2026 zhou-gu24. */
final class HabitatState {
    private String previous="idle";
    private long terminal=-1, doneUntil;
    private boolean initialized;
    String phase="idle";
    void accept(String next,long sequence,long now) {
        if(initialized&&"done".equals(next)&&!"done".equals(previous)&&sequence>terminal
                &&("thinking".equals(previous)||"working".equals(previous)||"streaming".equals(previous)))
            doneUntil=now+2600;
        if("done".equals(next))terminal=Math.max(terminal,sequence);
        if(!"done".equals(next))doneUntil=0;
        previous=next;phase=next;initialized=true;
    }
    String visible(boolean connected,boolean decision,boolean listening,boolean speaking,long now) {
        if(!connected)return "offline";
        if(decision)return "waiting";
        if(listening)return "listening";
        if(speaking)return "speaking";
        return "done".equals(phase)?(now<doneUntil?"done":"idle"):phase;
    }
    void reset(){previous="idle";phase="idle";terminal=-1;doneUntil=0;initialized=false;}
}
