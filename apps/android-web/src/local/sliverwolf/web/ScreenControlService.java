package local.sliverwolf.web;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;

/**
 * Optional accessibility service that lets the agent operate the phone UI:
 * read the visible text tree, tap by coordinates, click by text, type into the
 * focused field, scroll and navigate. Disabled until the user enables it once in
 * system settings.
 */
public final class ScreenControlService extends AccessibilityService {
    private static volatile ScreenControlService instance;
    private volatile String foregroundPackage = "", foregroundActivity = "", foregroundTitle = "";
    private volatile long foregroundAt = 0;

    static boolean enabled() { return instance != null; }

    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Real-time foreground awareness: a window-state change is the only no-root
        // signal that arrives when the user moves between apps.
        try {
            if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
            if (pkg.isEmpty() || pkg.equals(getPackageName())) return;
            String title = "";
            java.util.List<CharSequence> texts = event.getText();
            if (texts != null) for (CharSequence text : texts) {
                if (text != null && text.length() > 0) { title = text.toString(); break; }
            }
            CharSequence cls = event.getClassName();
            foregroundPackage = pkg;
            foregroundActivity = cls == null ? "" : cls.toString();
            foregroundTitle = title.length() > 120 ? title.substring(0, 120) : title;
            foregroundAt = System.currentTimeMillis();
        } catch (Exception ignored) { /* awareness must never crash the service */ }
    }

    /** Last observed foreground app; empty fields when the service is off. */
    static JSONObject foreground() throws Exception {
        ScreenControlService service = instance;
        JSONObject result = new JSONObject();
        result.put("enabled", service != null);
        if (service == null) return result;
        result.put("package", service.foregroundPackage);
        result.put("activity", service.foregroundActivity);
        result.put("title", service.foregroundTitle);
        result.put("at", service.foregroundAt);
        return result;
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }

    private static AccessibilityNodeInfo root() {
        ScreenControlService service = instance;
        return service == null ? null : service.getRootInActiveWindow();
    }

    /** Bounded text tree of the active window, for the agent to inspect. */
    static JSONObject dump(int limit) throws Exception {
        JSONObject result = new JSONObject();
        AccessibilityNodeInfo root = root();
        if (root == null) { result.put("ok", false); result.put("error", "no active window"); return result; }
        JSONArray nodes = new JSONArray();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        while (!queue.isEmpty() && count < limit) {
            AccessibilityNodeInfo node = queue.poll();
            String text = trim(node.getText());
            String desc = trim(node.getContentDescription());
            if (!text.isEmpty() || !desc.isEmpty() || node.isClickable() || node.isEditable()) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                JSONObject item = new JSONObject();
                item.put("text", text);
                item.put("desc", desc);
                item.put("package", trim(node.getPackageName()));
                item.put("clickable", node.isClickable());
                item.put("editable", node.isEditable());
                item.put("focused", node.isFocused());
                item.put("bounds", new JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom));
                nodes.put(item);
                count++;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        result.put("ok", true);
        result.put("nodes", nodes);
        return result;
    }

    static JSONObject clickText(String needle) throws Exception {
        AccessibilityNodeInfo root = root();
        if (root == null) return error("no active window");
        AccessibilityNodeInfo target = find(root, needle);
        if (target == null) return error("text not found: " + needle);
        AccessibilityNodeInfo clickable = target;
        while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
        boolean ok = (clickable != null ? clickable : target).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        JSONObject result = new JSONObject();
        result.put("ok", ok);
        result.put("clicked", trim(target.getText()).isEmpty() ? trim(target.getContentDescription()) : trim(target.getText()));
        return result;
    }

    static JSONObject tap(float x, float y) throws Exception {
        ScreenControlService service = instance;
        if (service == null) return error("accessibility service disabled");
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
        final boolean[] done = {false};
        service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) { done[0] = true; }
        }, null);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("dispatched", done[0]);
        return result;
    }

    static JSONObject type(String text) throws Exception {
        AccessibilityNodeInfo root = root();
        if (root == null) return error("no active window");
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) return error("no focused input");
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        JSONObject result = new JSONObject();
        result.put("ok", focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments));
        return result;
    }

    static JSONObject global(String action) throws Exception {
        ScreenControlService service = instance;
        if (service == null) return error("accessibility service disabled");
        int code = "back".equals(action) ? GLOBAL_ACTION_BACK
            : "home".equals(action) ? GLOBAL_ACTION_HOME
            : "recents".equals(action) ? GLOBAL_ACTION_RECENTS : GLOBAL_ACTION_BACK;
        JSONObject result = new JSONObject();
        result.put("ok", service.performGlobalAction(code));
        return result;
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String needle) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(node);
        int seen = 0;
        while (!queue.isEmpty() && seen < 2000) {
            AccessibilityNodeInfo current = queue.poll();
            seen++;
            String text = trim(current.getText());
            String desc = trim(current.getContentDescription());
            if (text.contains(needle) || desc.contains(needle)) return current;
            for (int i = 0; i < current.getChildCount(); i++) {
                AccessibilityNodeInfo child = current.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private static String trim(CharSequence value) { return value == null ? "" : value.toString().trim(); }

    private static JSONObject error(String message) throws Exception {
        JSONObject result = new JSONObject();
        result.put("ok", false);
        result.put("error", message);
        return result;
    }
}
