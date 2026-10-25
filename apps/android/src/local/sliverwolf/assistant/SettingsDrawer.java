package local.sliverwolf.assistant;

import android.app.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.*;
import java.util.ArrayDeque;
import java.util.function.*;

/** Modal side panel composed from platform widgets; no competing gesture recognizer. */
final class SettingsDrawer extends Dialog {
    private final Activity activity;
    private final LinearLayout body;
    private final TextView heading;
    private final ArrayDeque<Runnable> stack=new ArrayDeque<>();
    private Runnable page;
    SettingsDrawer(Activity activity,Runnable closed) {
        super(activity,android.R.style.Theme_Material_NoActionBar);
        this.activity=activity;
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setBackgroundColor(0xff131723);
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(HabitatView.iconControl(activity,"ic_chevron_down","返回",this::onBackPressed),new LinearLayout.LayoutParams(dp(48),dp(56)));
        heading=new TextView(activity);heading.setTextColor(0xffece9f5);heading.setTextSize(21);
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(HabitatView.iconControl(activity,"ic_x","关闭设置",this::dismiss),new LinearLayout.LayoutParams(dp(48),dp(56)));
        root.addView(bar);ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);
        body=new LinearLayout(activity);body.setOrientation(1);body.setPadding(dp(20),0,dp(20),dp(28));
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        Window window=getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff131723));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.48f);
        window.setGravity(Gravity.END);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setCanceledOnTouchOutside(true);setOnDismissListener(d->closed.run());
    }
    int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    void open(Runnable first){show();getWindow().setLayout(Math.min(dp(420),(int)(activity.getResources().getDisplayMetrics().widthPixels*.92f)),-1);go(first);getWindow().getDecorView().setTranslationX(dp(48));getWindow().getDecorView().animate().translationX(0).setDuration(180).start();}
    void go(Runnable next){if(page!=null)stack.push(page);page=next;next.run();}
    boolean isPage(String title){return isShowing()&&title.contentEquals(heading.getText());}
    void refresh(){if(page!=null)page.run();}
    @Override public void onBackPressed(){if(stack.isEmpty())dismiss();else{page=stack.pop();page.run();}}
    void title(String title){heading.setText(title);body.removeAllViews();}
    void section(String text){TextView v=text(text,12,0xffb8a4e8);v.setPadding(0,dp(22),0,dp(8));body.addView(v);}
    TextView note(String value){TextView v=text(value,13,0xffa5a9ba);v.setPadding(0,dp(8),0,dp(12));body.addView(v);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);return v;}
    private LinearLayout line(String title,String summary){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(64));row.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xff1c2130);shape.setCornerRadius(dp(12));
        row.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33b59be8),shape,null));
        LinearLayout labels=new LinearLayout(activity);labels.setOrientation(1);labels.addView(text(title,16,0xffe5e2ed));
        if(summary!=null&&!summary.isEmpty())labels.addView(text(summary,12,0xffa8adbf));
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);body.addView(row,lp);return row;
    }
    void row(String title,String summary,Runnable action){LinearLayout row=line(title,summary);TextView chevron=text("›",24,0xffb7a6db);chevron.setPadding(dp(10),0,0,0);row.addView(chevron);row.setOnClickListener(v->action.run());}
    void toggle(String title,String summary,boolean value,Consumer<Boolean> changed){LinearLayout row=line(title,summary);Switch control=new Switch(activity);control.setContentDescription(title);control.setChecked(value);row.addView(control);control.setOnCheckedChangeListener((b,on)->changed.accept(on));row.setOnClickListener(v->control.setChecked(!control.isChecked()));}
    void choice(String title,String summary,String[] labels,int selected,IntConsumer changed){row(title,summary,()->go(()->{title(title);RadioGroup group=new RadioGroup(activity);body.addView(group);for(int i=0;i<labels.length;i++){final int index=i;RadioButton option=new RadioButton(activity);option.setId(View.generateViewId());option.setText(labels[i]);option.setTextSize(16);option.setTextColor(0xffe5e2ed);option.setPadding(dp(8),dp(12),dp(8),dp(12));group.addView(option);option.setChecked(i==selected);option.setOnClickListener(v->{changed.accept(index);onBackPressed();});}}));}
    EditText input(String title,String value,boolean multiline){section(title);EditText field=new EditText(activity);field.setText(value);field.setTextColor(0xffe5e2ed);field.setHintTextColor(0xff9c97a8);field.setSingleLine(!multiline);body.addView(field);return field;}
    void slider(String title,float value,float min,float max,float step,Consumer<Float> changed){section(title);TextView current=text(String.format(java.util.Locale.ROOT,"%.2f",value),14,0xffd2c5ed);body.addView(current);SeekBar slider=new SeekBar(activity);slider.setMax(Math.round((max-min)/step));slider.setProgress(Math.round((value-min)/step));slider.setContentDescription(title);body.addView(slider);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){current.setText(String.format(java.util.Locale.ROOT,"%.2f",min+n*step));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){changed.accept(min+s.getProgress()*step);}});}
}
