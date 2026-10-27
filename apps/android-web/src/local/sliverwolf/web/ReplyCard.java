package local.sliverwolf.web;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Conversation strip reusing the upstream card's follow-tail/touch policy.
 * Adapted from OverlayService, a5af7b1, MIT (c) 2026 zhou-gu24. */
final class ReplyCard extends LinearLayout {
    interface Listener { void activity(boolean touching); }
    private final TextView body;
    private final ScrollView scroll;
    private final Listener listener;
    private boolean touching,followTail=true;
    private String content=null;
    ReplyCard(Context context,Listener listener){
        super(context);this.listener=listener;setOrientation(VERTICAL);
        body=new TextView(context);body.setTextSize(13);body.setTextColor(0xffe0e5f0);
        body.setPadding(dp(14),0,dp(14),dp(8));body.setLineSpacing(dp(3),1);
        scroll=new ScrollView(context);scroll.addView(body);
        addView(scroll,new LayoutParams(-1,0,1));
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(touching)followTail=isAtBottom();});
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private boolean isAtBottom(){return body.getBottom()-(scroll.getScrollY()+scroll.getHeight())<=dp(24);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)touching=true;
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;followTail=isAtBottom();}
        listener.activity(touching);return super.dispatchTouchEvent(event);
    }
    void colors(int background,int text){body.setTextColor(text);}
    void showContent(String text){
        if(text.equals(content))return;
        boolean follow=followTail&&!touching;
        content=text;
        // The expanded conversation retains the full loaded history; this preview follows its tail.
        int start=Math.max(0,text.length()-6000);
        if(start>0&&Character.isLowSurrogate(text.charAt(start)))start++;
        if (text.isEmpty()) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        body.setText(text.substring(start));
        if(follow)scroll.post(()->{if(followTail&&!touching)scroll.fullScroll(View.FOCUS_DOWN);});
    }
}
