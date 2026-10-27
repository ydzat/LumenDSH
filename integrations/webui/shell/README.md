# @lumendsh/webui-shell

Mobile controls rendered by the official page through `shell.overlay`:

- `⌂` calls `window.LumenShell.home()` (finish the WebView activity, back to habitat).
- `🎤` calls `window.LumenShell.voice()` (native SpeechRecognizer; transcript is posted
  to the companion composer queue).

The app injects `LumenShell` with `addJavascriptInterface`. On desktop browsers the
interface is absent and the controls render nothing.
