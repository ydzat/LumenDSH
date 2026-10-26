package local.sliverwolf.web;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static local.sliverwolf.web.DshTransport.object;

/** Live DSH catalog; default edits and session edits have separate server operations. */
final class ModelPicker {
    private final Activity activity;
    private final SessionController controller;
    private final DshTransport wire;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final LinearLayout body;
    private final AlertDialog dialog;
    private final String session;
    private final long backendRevision;
    private final android.content.SharedPreferences preferences;
    private boolean closed,busy;
    private JSONObject catalog,current;
    ModelPicker(Activity activity,SessionController controller) {
        this.activity=activity;this.controller=controller;session=controller.selected;
        preferences=activity.getSharedPreferences("session",0);backendRevision=preferences.getLong("backendRevision",0);
        wire=new DshTransport(preferences);
        body=new LinearLayout(activity);body.setOrientation(1);body.setPadding(32,16,32,16);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle("模型与上下文").setView(scroll).setNegativeButton("返回",null).create();
        dialog.setOnDismissListener(d->{closed=true;io.shutdown();});dialog.show();load();
    }
    private boolean valid(){return !closed&&!activity.isFinishing()&&session.equals(controller.selected)&&backendRevision==preferences.getLong("backendRevision",0);}
    private TextView note(String text){TextView v=new TextView(activity);v.setText(text);v.setTextIsSelectable(true);v.setPadding(0,12,0,12);body.addView(v);return v;}
    private void button(String label,Runnable run){Button b=new Button(activity);b.setText(label);b.setOnClickListener(v->run.run());body.addView(b);}
    private static String label(JSONObject x){return x==null?"未选择":x.optString("provider")+" / "+x.optString("model");}
    private void load(){
        if(busy||closed)return;busy=true;body.removeAllViews();note("正在从 DSH 读取模型…");
        io.execute(()->{try{
            JSONObject c=wire.call("session.modelCatalog",object());
            JSONObject metadata=null;
            try{if(!session.isEmpty())metadata=wire.model(session);}catch(Exception unavailable){/* A stale model must not prevent choosing a replacement. */}
            final JSONObject m=metadata;
            main.post(()->{busy=false;if(!valid()){expired();return;}catalog=c;current=m;render();});
        }catch(Exception e){main.post(()->{busy=false;if(closed)return;body.removeAllViews();note("读取失败 · "+DshTransport.failureCode(e));button("重新读取",this::load);});}});
    }
    private void expired(){if(closed)return;body.removeAllViews();note("连接或会话已变化，请关闭后重新打开。");}
    private void render(){
        body.removeAllViews();
        note("模型列表来自 DSH。默认模型用于新会话；当前会话的选择独立保存。");
        button("默认模型："+label(catalog.optJSONObject("default")),()->choose("default"));
        if(!session.isEmpty()){
            button("当前会话："+label(current),()->choose("session"));
            button("当前会话改用目前的默认模型",()->save("session",object("useDefault",true)));
            note("切换不打断正在执行的请求，从后续请求生效。");
            note(ModelSettings.describe(current,"模型信息不可用"));
        }
        JSONArray failures=catalog.optJSONArray("failures");
        if(failures!=null)for(int i=0;i<failures.length();i++)note("供应商目录不可用："+failures.optJSONObject(i).optString("name"));
        button("刷新模型目录",this::load);
    }
    private void choose(String scope){
        if(busy||!valid()){expired();return;}
        ArrayList<JSONObject> options=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        JSONArray groups=catalog.optJSONArray("groups");JSONObject selected="default".equals(scope)?catalog.optJSONObject("default"):current;int checked=-1;
        if(groups!=null)for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);JSONArray models=group.optJSONArray("models");if(models==null)continue;
            for(int j=0;j<models.length();j++){
                JSONObject model=models.optJSONObject(j);JSONObject value=object("provider",group.optString("id"),"model",model.optString("id"));
                if(selected!=null&&label(selected).equals(label(value)))checked=options.size();
                options.add(value);labels.add(group.optString("name")+" · "+model.optString("name")+"\n"+model.optString("id"));
            }
        }
        if(options.isEmpty()){note("DSH 未提供可用模型，请先在 DSH 中配置供应商。");return;}
        new AlertDialog.Builder(activity).setTitle("default".equals(scope)?"新会话默认模型":"仅当前会话模型")
            .setSingleChoiceItems(labels.toArray(new String[0]),checked,(d,n)->{d.dismiss();save(scope,options.get(n));}).setNegativeButton("取消",null).show();
    }
    private void save(String scope,JSONObject target){
        if(busy||!valid()){expired();return;}busy=true;body.removeAllViews();note("正在保存到 DSH…");
        io.execute(()->{try{
            JSONObject request=new JSONObject(target.toString());request.put("scope",scope);if("session".equals(scope))request.put("sessionId",session);
            wire.companion("model-selection",request);
            main.post(()->{busy=false;if(!valid()){expired();return;}controller.refreshModel();load();});
        }catch(Exception e){main.post(()->{busy=false;if(closed)return;body.removeAllViews();note("保存未确认 · "+DshTransport.failureCode(e)+"。请先刷新查看服务器状态，不会自动重复提交。");button("刷新确认",this::load);});}});
    }
}
