package local.sliverwolf.assistant.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Base64;

/** Explicitly invoked device-test input; never submits a prompt or grants a decision. */
public final class TextInput extends Instrumentation {
    private Bundle arguments;
    private int remainingField;
    @Override public void onCreate(Bundle arguments) { this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String action = arguments.getString("action", "");
            if(action.equals("icon-voice-stop")) {
                click("开始说话");awaitText("结束说话");click("结束说话");awaitText("开始说话");
                result.putString("result","icon-microphone-stop-state-verified");finish(0,result);return;
            }
            if(action.equals("share-fixture")) {
                android.net.Uri uri=android.net.Uri.parse("content://local.sliverwolf.assistant.tests.share/"+arguments.getString("kind","normal"));
                android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").setClassName("local.sliverwolf.assistant","local.sliverwolf.assistant.MainActivity")
                    .putExtra(android.content.Intent.EXTRA_STREAM,uri)
                    .putExtra(android.content.Intent.EXTRA_TEXT,"Read the shared text attachment using assistant_receive_share, then reply only with its first line. Do not modify or execute files.")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(android.content.ClipData.newRawUri("Controlled fixture",uri));
                getTargetContext().grantUriPermission("local.sliverwolf.assistant",uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getTargetContext().startActivity(intent);
                // Allow the receiver to consume transient grants before test process teardown.
                SystemClock.sleep(3000);
                result.putString("result","real-content-share-launched");finish(0,result);return;
            }
            if (action.equals("voice-stop") || action.equals("acquire-focus")) {
                if (action.equals("voice-stop")) {
                    click("Speak (Chinese)");
                    awaitText("Listening… speak now");
                    click("Stop voice"); awaitText("Voice stopped");
                    SystemClock.sleep(1500); awaitText("Voice stopped");
                } else {
                    android.media.AudioManager audio = getTargetContext().getSystemService(android.media.AudioManager.class);
                    android.media.AudioFocusRequest focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()).build();
                    try {
                        if (audio.requestAudioFocus(focus) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                            throw new IllegalStateException("Competing process did not receive actual audio focus");
                        SystemClock.sleep(2000);
                    } finally { audio.abandonAudioFocusRequest(focus); }
                }
                result.putString("result", action + "-verified-on-device"); finish(0, result); return;
            }
            if ("observe-stream".equals(arguments.getString("action"))) {
                for (int attempt = 0; attempt < 150; attempt++) {
                    AccessibilityNodeInfo window = getUiAutomation().getRootInActiveWindow();
                    if (window != null && hasStreamingText(window)) {
                        result.putString("result", "live-streaming-text-observed"); finish(0, result); return;
                    }
                    SystemClock.sleep(200);
                }
                throw new IllegalStateException("No live streaming text observed within 30 seconds");
            }
            String text = new String(Base64.decode(arguments.getString("textBase64"), Base64.DEFAULT), "UTF-8");
            AccessibilityNodeInfo root = null;
            for (int attempt = 0; attempt < 20 && root == null; attempt++) {
                root = getUiAutomation().getRootInActiveWindow();
                if (root == null) SystemClock.sleep(200);
            }
            if (root == null) throw new IllegalStateException("No active window");
            remainingField=Integer.parseInt(arguments.getString("fieldIndex","0"));
            AccessibilityNodeInfo input = find(root);
            if (input == null) throw new IllegalStateException("No client input field");
            Bundle value = new Bundle();
            value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value)) throw new IllegalStateException("Set text refused");
            SystemClock.sleep(200);
            input.refresh();
            if (!text.contentEquals(input.getText())) throw new IllegalStateException("Text differs after input");
            result.putString("result", "exact-text-set");
            finish(0, result);
        } catch (Exception error) {
            result.putString("error", error.toString()); finish(1, result);
        }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node) {
        if ("local.sliverwolf.assistant".contentEquals(node.getPackageName() == null ? "" : node.getPackageName())
                && "android.widget.EditText".contentEquals(node.getClassName())) {if(remainingField--==0)return node;}
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { AccessibilityNodeInfo found = find(child); if (found != null) return found; }
        }
        return null;
    }
    private AccessibilityNodeInfo exact(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if ("local.sliverwolf.assistant".contentEquals(node.getPackageName() == null ? "" : node.getPackageName())
                && (text.contentEquals(node.getText() == null ? "" : node.getText())
                ||text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = exact(node.getChild(i), text); if (found != null) return found;
        }
        return null;
    }
    private void awaitText(String text) {
        for (int i = 0; i < 40; i++) {
            if (exact(getUiAutomation().getRootInActiveWindow(), text) != null) return;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("Expected voice state not observed: " + text);
    }
    private void click(String text) {
        awaitText(text);
        AccessibilityNodeInfo target = exact(getUiAutomation().getRootInActiveWindow(), text);
        if (target == null || !target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new IllegalStateException("Control unavailable: " + text);
    }
    private boolean hasStreamingText(AccessibilityNodeInfo node) {
        if ("local.sliverwolf.assistant".contentEquals(node.getPackageName() == null ? "" : node.getPackageName())
                && node.getText() != null && node.getText().toString().contains("Assistant (streaming)\n")) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && hasStreamingText(child)) return true;
        }
        return false;
    }
}
