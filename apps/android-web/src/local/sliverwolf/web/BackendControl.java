package local.sliverwolf.web;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.UUID;

/** Fixed lifecycle operations through Termux 0.118.3's official RUN_COMMAND contract. */
public final class BackendControl extends BroadcastReceiver {
    static final String PERMISSION="com.termux.permission.RUN_COMMAND";
    private static final String HOME="/data/data/com.termux/files/home",BIN="/data/data/com.termux/files/usr/bin/";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("backend-control",0);}
    static boolean allowed(Context c){return c.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED;}
    static void run(Context c,boolean start){
        SharedPreferences p=prefs(c);
        if(!allowed(c))throw new IllegalStateException("请先授予 Termux 命令权限");
        if(p.getLong("deadline",0)>System.currentTimeMillis())throw new IllegalStateException("上一项操作仍在等待结果");
        String id=UUID.randomUUID().toString();
        String script="set -eu\n"+(start?"command -v agent-start >/dev/null || exit 127\nagent-start >/dev/null 2>&1\n":"")
            +"if tmux has-session -t =dsh 2>/dev/null; then echo DSH_SESSION_PRESENT; else echo DSH_SESSION_ABSENT; fi\n"
            +"curl -s -o /dev/null -w 'DSH_HTTP:%{http_code}\\n' --max-time 5 http://127.0.0.1:3080/ || true\n";
        Intent result=new Intent(c,BackendControl.class).setAction(id);
        // Mutable only for the explicit one-shot callback: Termux supplies the result bundle.
        PendingIntent callback=PendingIntent.getBroadcast(c,0,result,PendingIntent.FLAG_ONE_SHOT|(Build.VERSION.SDK_INT>=31?0x02000000:0));
        Intent command=new Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH",BIN+"bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{HOME+"/agent-env.sh","bash","-c",script})
            .putExtra("com.termux.RUN_COMMAND_WORKDIR",HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback);
        p.edit().putString("id",id).putBoolean("start",start).putLong("deadline",System.currentTimeMillis()+45000)
            .putString("state","等待 Termux 执行"+(start?"启动":"检查")).commit();
        try{if(c.startService(command)==null)throw new IllegalStateException("Termux 服务不可用");}
        catch(RuntimeException e){callback.cancel();p.edit().putLong("deadline",0).putString("state","未能调用 Termux，请检查权限或打开 Termux 后重试").apply();throw e;}
    }
    static String status(Context c){
        SharedPreferences p=prefs(c);long deadline=p.getLong("deadline",0);
        if(deadline>0&&deadline<System.currentTimeMillis()){
            p.edit().putLong("deadline",0).putString("id","").putString("state","Termux 未返回结果；检查 allow-external-apps=true 与后台限制，然后重新检测。不会自动重发启动命令。").apply();
        }
        long at=p.getLong("completedAt",0);
        return (allowed(c)?"命令权限：已授权":"命令权限：未授权")+"\n"+(at==0?"":"上次命令回执 · "+android.text.format.DateFormat.format("MM-dd HH:mm:ss",at)+"\n")
            +p.getString("state","尚未检查 Termux 运行状态")+"\n当前服务状态以连接检测为准。";
    }
    @Override public void onReceive(Context c,Intent intent){
        SharedPreferences p=prefs(c);String expected=p.getString("id","");if(expected.isEmpty()||!expected.equals(intent.getAction()))return;
        Bundle result=intent.getBundleExtra("result");String state;
        if(result==null)state="Termux 未返回有效结果";
        else if(result.getInt("err",-1)!=-1)state="Termux 拒绝执行；确认 allow-external-apps=true，并检查 Termux 提示";
        else if(result.getInt("exitCode",-1)!=0)state="Termux 命令退出码："+result.getInt("exitCode")+"；检查 agent-start 和 agent-env.sh 是否安装";
        else{
            String out=result.getString("stdout","");
            state=out.contains("DSH_SESSION_PRESENT")?"tmux：DSH 会话存在":"tmux：未发现 DSH 会话";
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("DSH_HTTP:([0-9]{3})").matcher(out);
            if(m.find())state+="\n本机端口 HTTP："+m.group(1)+(m.group(1).equals("000")?"（不可达或尚在启动）":"（认证与事件连接另行检测）");
        }
        p.edit().putString("state",state).putString("id","").putLong("deadline",0).putLong("completedAt",System.currentTimeMillis()).apply();
    }
}
