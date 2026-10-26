package local.sliverwolf.web;
import android.content.*;
/** Explicit PackageInstaller status callback, including mandatory system confirmation. */
public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){try{new NativeReleases(context).result(intent);}catch(Exception error){android.util.Log.e("NativeRelease","Installation callback failed",error);}}
}
