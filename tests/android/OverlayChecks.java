package local.sliverwolf.assistant.tests;

/** Geometry probe against the enabled installed overlay; no user content. */
public final class OverlayChecks extends android.app.Instrumentation {
    private android.os.Bundle arguments;
    @Override public void onCreate(android.os.Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart() {
        android.os.Bundle result=new android.os.Bundle();
        getTargetContext().startForegroundService(new android.content.Intent().setClassName(getTargetContext(),"local.sliverwolf.assistant.CompanionOverlayService"));
        getContext().startActivity(new android.content.Intent(getContext(),OverlaySurface.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("keyboard",true));
        android.os.SystemClock.sleep(5000);
        runOnMainSync(()->{try {
            Class<?> type=Class.forName("local.sliverwolf.assistant.CompanionOverlayService",true,getTargetContext().getClassLoader());
            java.lang.reflect.Field current=type.getDeclaredField("current");current.setAccessible(true);
            Object service=((java.lang.ref.WeakReference<?>)current.get(null)).get();
            if(service==null)throw new IllegalStateException("Overlay is not running");
            java.lang.reflect.Field field=type.getDeclaredField("panel");field.setAccessible(true);
            android.view.View panel=(android.view.View)field.get(service);
            java.lang.reflect.Field probeField=type.getDeclaredField("viewportProbe");probeField.setAccessible(true);
            android.view.View probe=(android.view.View)probeField.get(service);
            android.graphics.Rect frame=new android.graphics.Rect();probe.getWindowVisibleDisplayFrame(frame);
            result.putString("visibleFrame",frame.toShortString());
            result.putString("insets",String.valueOf(panel.getRootWindowInsets()));
            int[] xy=new int[2];panel.getLocationOnScreen(xy);
            result.putString("panel",xy[0]+","+xy[1]+","+panel.getWidth()+","+panel.getHeight());
            if(xy[1]<frame.top||xy[1]+panel.getHeight()>frame.bottom)throw new AssertionError("Overlay crosses visible keyboard bounds");
        }catch(Exception error){result.putString("error",error.toString());}});
        if(arguments!=null&&arguments.containsKey("restoreX")&&arguments.containsKey("restoreY"))
            getTargetContext().getSharedPreferences("overlay",0).edit()
                .putInt("x",Integer.parseInt(arguments.getString("restoreX")))
                .putInt("y",Integer.parseInt(arguments.getString("restoreY"))).commit();
        finish(result.containsKey("error")?1:0,result);
    }
}
