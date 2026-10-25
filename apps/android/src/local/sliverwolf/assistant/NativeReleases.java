package local.sliverwolf.assistant;

import android.content.*;
import android.content.pm.*;
import android.app.PendingIntent;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static local.sliverwolf.assistant.DshTransport.object;

/** User-confirmed Android installation; the build/signing runtime remains in Termux. */
final class NativeReleases {
    private final Context context;private final File root;private final AtomicFile journal;
    NativeReleases(Context context)throws Exception{
        this.context=context.getApplicationContext();root=new File(context.getFilesDir(),"native-release");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Native release storage unavailable");
        journal=new AtomicFile(new File(root,"receipt.json"));
    }
    private JSONObject read()throws Exception{
        if(!journal.getBaseFile().exists()&&!new File(root,"receipt.json.bak").exists())return object("state","empty");
        return new JSONObject(new String(journal.readFully(),StandardCharsets.UTF_8));
    }
    private void write(JSONObject record)throws Exception{
        FileOutputStream stream=null;try{stream=journal.startWrite();stream.write(record.toString().getBytes(StandardCharsets.UTF_8));journal.finishWrite(stream);}
        catch(Exception error){if(stream!=null)journal.failWrite(stream);throw error;}
    }
    private PackageInfo installed()throws Exception{return context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES);}
    private static java.util.Set<String> signers(PackageInfo info)throws Exception{
        if(info.signingInfo==null)throw new IOException("Missing signing identity");
        java.util.Set<String> result=new java.util.TreeSet<>();
        for(Signature signer:info.signingInfo.getApkContentsSigners())result.add(DeviceOperations.hex(java.security.MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())));
        return result;
    }
    synchronized JSONObject status()throws Exception{
        JSONObject r=read();long current=installed().getLongVersionCode();
        if(r.has("targetCode")&&current==r.getLong("targetCode")&&PetStore.hash(new File(context.getApplicationInfo().sourceDir)).equals(r.getString("apkSha256"))){
            r.put("state","installed").put("detail","Installed version and APK hash matched on client startup");write(r);
        }
        if(java.util.Arrays.asList("install-requested","user-confirmation").contains(r.optString("state"))&&context.getPackageManager().getPackageInstaller().getSessionInfo(r.optInt("sessionId",-1))==null){
            r.put("state","failed").put("detail","Installer session ended without matching installed APK; candidate retained");write(r);
        }
        return new JSONObject(r.toString()).put("installedCode",current).put("canRequestInstalls",context.getPackageManager().canRequestPackageInstalls());
    }
    synchronized JSONObject stage(File artifact,JSONObject receipt)throws Exception{
        DeviceBridge.arguments(receipt,"releaseId","baseCode","targetCode","apkSha256","sourceSha256","dexSha256");
        String id=receipt.getString("releaseId");if(!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))throw new IllegalArgumentException("Invalid release ID");
        for(String field:new String[]{"apkSha256","sourceSha256","dexSha256"})if(!receipt.getString(field).matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid build digest");
        JSONObject previous=read();
        if(id.equals(previous.optString("releaseId"))){if(!previous.getString("receiptDigest").equals(PresentationSettings.digest(receipt.toString())))throw new IllegalStateException("Conflicting release ID");return status();}
        if(java.util.Arrays.asList("install-requested","user-confirmation").contains(previous.optString("state")))throw new IllegalStateException("Resolve existing installation first");
        PackageInfo current=installed();
        if(current.getLongVersionCode()!=receipt.getLong("baseCode"))throw new IllegalArgumentException("Stale installed base");
        if(!PetStore.hash(artifact).equals(receipt.getString("apkSha256")))throw new IOException("APK hash mismatch");
        PackageInfo target=context.getPackageManager().getPackageArchiveInfo(artifact.toString(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES);
        if(target==null||!context.getPackageName().equals(target.packageName)||target.getLongVersionCode()!=receipt.getLong("targetCode")||target.getLongVersionCode()<=current.getLongVersionCode())throw new IOException("Invalid package or non-increasing version");
        if(!signers(current).equals(signers(target)))throw new IOException("APK signing identity mismatch");
        if(target.applicationInfo.minSdkVersion>android.os.Build.VERSION.SDK_INT)throw new IOException("Unsupported Android version");
        File staged=new File(root,"candidate.apk");
        java.nio.file.Files.copy(artifact.toPath(),staged.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if(!PetStore.hash(staged).equals(receipt.getString("apkSha256")))throw new IOException("Staged APK readback mismatch");
        JSONObject record=new JSONObject(receipt.toString()).put("receiptDigest",PresentationSettings.digest(receipt.toString())).put("state","staged").put("detail","Package, version, signer and payload checked; Android performs final installation validation");write(record);return status();
    }
    synchronized JSONObject install(boolean safe)throws Exception{
        JSONObject r=read();if(!"staged".equals(r.optString("state")))return status();
        if(!safe)throw new IllegalStateException("Open client and finish voice interaction before installing");
        if(!context.getPackageManager().canRequestPackageInstalls())throw new IllegalStateException("Allow installation from LumenDSH in the local update screen first");
        if(installed().getLongVersionCode()!=r.getLong("baseCode"))throw new IllegalStateException("Stale installed base");
        File apk=new File(root,"candidate.apk");if(!PetStore.hash(apk).equals(r.getString("apkSha256")))throw new IOException("Candidate APK changed");
        PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(context.getPackageName());params.setSize(apk.length());
        int id=installer.createSession(params);r.put("sessionId",id).put("state","install-requested");write(r);
        try(PackageInstaller.Session session=installer.openSession(id)){
            try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,apk.length())){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}
            Intent callback=new Intent(context,InstallResultReceiver.class).setAction(context.getPackageName()+".INSTALL_RESULT");
            session.commit(PendingIntent.getBroadcast(context,id,callback,PendingIntent.FLAG_UPDATE_CURRENT).getIntentSender());
        }catch(Exception error){installer.abandonSession(id);r.put("state","failed").put("detail",error.getMessage());write(r);throw error;}
        return status();
    }
    synchronized void result(Intent intent)throws Exception{
        JSONObject r=read();if(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)!=r.optInt("sessionId",-2))return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            r.put("state","user-confirmation");write(r);Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation==null)throw new IOException("Missing system confirmation");confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirmation);
        }else{r.put("state",status==PackageInstaller.STATUS_SUCCESS?"installed":"failed").put("detail",intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));write(r);}
    }
}
