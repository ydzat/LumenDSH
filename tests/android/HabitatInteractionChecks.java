package local.sliverwolf.assistant.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.util.ArrayList;

/** Real installed View/Android recognizer checks. Callback capture does not test DSH delivery. */
public final class HabitatInteractionChecks extends Instrumentation {
    private Activity activity;
    private View habitat,scene,narration,bubble;
    private final ArrayList<String> touches=new ArrayList<>();
    private Throwable failure;
    private Object originalCallback;
    private float originalZoom;
    private Object field(Object owner,String name)throws Exception{
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private void set(Object owner,String name,Object value)throws Exception{
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void ui(Runnable runnable){runOnMainSync(()->{try{runnable.run();}catch(Throwable error){failure=error;}});if(failure!=null)throw new AssertionError(failure);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            activity=startActivitySync(new android.content.Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.MainActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(2500);
            ui(()->{try{
                habitat=(View)field(activity,"habitat");scene=(View)field(habitat,"scene");narration=(View)field(habitat,"narration");bubble=(View)field(habitat,"bubble");
                originalZoom=(Float)field(scene,"zoom");originalCallback=field(scene,"touchAction");
                set(scene,"touchAction",(java.util.function.Consumer<String>)touches::add);
                check(find(habitat,"文字记录")==null&&find(habitat,"靠近角色")==null&&find(habitat,"停止语音")==null,"Redundant controls remain");
            }catch(Exception e){throw new RuntimeException(e);}});
            int minSpan=getTargetContext().getResources().getIdentifier("config_minScalingSpan","dimen","android");
            if(minSpan!=0)result.putString("platformMinScalingSpanPx",Float.toString(getTargetContext().getResources().getDimension(minSpan)));
            float[] point=new float[2];
            for(float region:new float[]{.25f,.55f,.70f,.92f}){
                ui(()->{try{android.graphics.RectF r=(android.graphics.RectF)field(scene,"characterBounds");float d=scene.getResources().getDisplayMetrics().density;point[0]=r.centerX()*d;point[1]=(r.top+r.height()*region)*d;}catch(Exception e){throw new RuntimeException(e);}});
                tap(point[0],point[1]);SystemClock.sleep(400);
            }
            check(touches.size()==4,"Single-tap count");
            check(touches.get(0).contains("头")&&touches.get(1).contains("肩")&&touches.get(2).contains("腰")&&touches.get(3).contains("脚"),"Body-region mapping");
            touches.clear();tap(2,2);SystemClock.sleep(400);check(touches.isEmpty(),"Background touch submitted");
            tap(point[0],point[1]);SystemClock.sleep(70);tap(point[0],point[1]);SystemClock.sleep(450);
            check(touches.isEmpty(),"Double tap submitted");
            ui(()->{try{set(scene,"zoom",0f);}catch(Exception e){throw new RuntimeException(e);}});
            pinch(point[0],point[1],100,420);SystemClock.sleep(450);
            ui(()->{try{check((Float)field(scene,"zoom")>.5f,"Pinch did not enlarge; zoom="+field(scene,"zoom"));}catch(Exception e){throw new RuntimeException(e);}});
            pinch(point[0],point[1],420,100);SystemClock.sleep(450);
            ui(()->{try{check((Float)field(scene,"zoom")<.4f,"Pinch did not shrink; zoom="+field(scene,"zoom"));}catch(Exception e){throw new RuntimeException(e);}});
            check(touches.isEmpty(),"Pinch submitted a touch");
            ui(()->narration.performClick());SystemClock.sleep(350);ui(()->check(bubble.getAlpha()>.99f,"Reveal failed"));
            int hold=12;
            try{Object presentation=field(habitat,"presentation");Field f=presentation.getClass().getDeclaredField("holdSeconds");f.setAccessible(true);hold=f.getInt(presentation);}catch(Exception e){throw new RuntimeException(e);}
            SystemClock.sleep((hold+1)*1000L);
            ui(()->check(bubble.getVisibility()==View.INVISIBLE&&bubble.getAlpha()<.01f,"Idle fade failed"));
            ui(()->narration.performClick());SystemClock.sleep(350);
            ui(()->{check(bubble.getVisibility()==View.VISIBLE,"Hidden region did not reveal");find(bubble,"展开会话").performClick();});
            ui(()->{try{
                check(((View)field(activity,"transcriptPage")).getVisibility()==View.VISIBLE,"Expand did not open chat");
                check(((android.widget.TextView)field(activity,"history")).isTextSelectable(),"Copy selection unavailable");
                check(((View)field(activity,"draft")).isShown(),"Composer unavailable");
            }catch(Exception e){throw new RuntimeException(e);}});
            result.putString("result","installed-gesture-regions-double-pinch-fade-expand-copy-PASS");
            result.putString("scope","Real Android Views; touch callback captured; DSH delivery requires separate live check");
        }catch(Throwable error){result.putString("error",error.toString());}
        finally{
            if(scene!=null)runOnMainSync(()->{try{set(scene,"touchAction",originalCallback);set(scene,"zoom",originalZoom);getTargetContext().getSharedPreferences("appearance",0).edit().putFloat("zoom",originalZoom).commit();}catch(Exception e){result.putString("restoreError",e.toString());}});
        }
        finish(result.containsKey("error")||result.containsKey("restoreError")?1:0,result);
    }
    private View find(View view,String text){
        if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;
    }
    private void tap(float x,float y){long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,new float[]{x},new float[]{y});SystemClock.sleep(40);event(down,MotionEvent.ACTION_UP,new float[]{x},new float[]{y});}
    private void pinch(float x,float y,float from,float to){
        long down=SystemClock.uptimeMillis();event(down,0,new float[]{x-from},new float[]{y});
        event(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{x-from,x+from},new float[]{y,y});
        for(int i=1;i<=12;i++){SystemClock.sleep(20);float span=from+(to-from)*i/12;event(down,MotionEvent.ACTION_MOVE,new float[]{x-span,x+span},new float[]{y,y});}
        event(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{x-to,x+to},new float[]{y,y});
        event(down,MotionEvent.ACTION_UP,new float[]{x-to},new float[]{y});
    }
    private void event(long down,int action,float[] x,float[] y){
        MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[x.length];MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[x.length];
        for(int i=0;i<x.length;i++){pp[i]=new MotionEvent.PointerProperties();pp[i].id=i;pp[i].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[i]=new MotionEvent.PointerCoords();pc[i].x=x[i];pc[i].y=y[i];pc[i].pressure=1;pc[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x.length,pp,pc,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        ui(()->scene.dispatchTouchEvent(event));event.recycle();
    }
}
