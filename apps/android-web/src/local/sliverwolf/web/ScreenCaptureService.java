package local.sliverwolf.web;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Holds a MediaProjection so the agent can capture the screen after one consent.
 * Runs as a foreground service; captures are handed to the companion host.
 */
public final class ScreenCaptureService extends Service {
    private static final String CHANNEL = "screen-capture";
    private static final int NOTIFICATION_ID = 4101;
    private static volatile ScreenCaptureService instance;

    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private int width, height, density;

    static boolean ready() { return instance != null && instance.projection != null; }

    static byte[] capture() {
        ScreenCaptureService service = instance;
        if (service == null) return null;
        return service.grab();
    }

    static void start(Context context, Intent result) {
        Intent intent = new Intent(context, ScreenCaptureService.class).putExtra("result", result);
        context.startForegroundService(intent);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("result") && projection == null) {
            createNotification();
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(Activity.RESULT_OK, (Intent) intent.getParcelableExtra("result"));
            setup();
        }
        return START_STICKY;
    }

    private void setup() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        width = metrics.widthPixels; height = metrics.heightPixels; density = metrics.densityDpi;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { teardown(); }
        }, new Handler(Looper.getMainLooper()));
        display = projection.createVirtualDisplay("screen-capture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        instance = this;
    }

    private synchronized byte[] grab() {
        if (reader == null) return null;
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return null;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out);
            bitmap.recycle(); cropped.recycle();
            return out.toByteArray();
        } catch (Exception error) {
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    private void teardown() {
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        projection = null; instance = null;
    }

    private void createNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW));
        }
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setContentTitle("LumenDSH 屏幕读取")
            .setContentText("允许助手截屏识别界面")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { teardown(); super.onDestroy(); }
}
