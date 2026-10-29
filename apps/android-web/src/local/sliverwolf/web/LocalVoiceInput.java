package local.sliverwolf.web;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utterance capture for the on-device recognizer: records 16 kHz mono until the
 * speaker has been quiet for a moment, then decodes with SpeechEngine.
 *
 * The end-of-speech rule is an energy gate here (cheap, no second model) while
 * SpeechEngine trims the recording with Silero VAD before decoding, so trailing
 * silence never reaches the recognizer.
 */
final class LocalVoiceInput {
    interface Listener {void state(String state);void result(String text);}
    private static final int RATE = 16000;
    private static final int CHUNK = 320;            // 20 ms
    private static final long MAX_MS = 20000;
    private static final long MIN_SPEECH_MS = 300;
    private static final long SILENCE_MS = 1200;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile AudioRecord record;
    private Thread worker;

    LocalVoiceInput(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    boolean active() {return running;}

    void start() {
        stop();
        running = true;
        listener.state("正在准备本地识别…");
        worker = new Thread(this::capture, "local-voice");
        worker.start();
    }

    private void capture() {
        int minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = null;
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            record = recorder;
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microphone unavailable");
            recorder.startRecording();
            listener.state("正在聆听，请慢慢说…");
            List<short[]> chunks = new ArrayList<>();
            long started = System.currentTimeMillis(), lastSpeech = 0, speechMs = 0;
            double floor = 0;
            int seen = 0;
            short[] buffer = new short[CHUNK];
            while (running) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                short[] chunk = new short[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                chunks.add(chunk);
                double rms = 0;
                for (int i = 0; i < read; i++) rms += (double) buffer[i] * buffer[i];
                rms = Math.sqrt(rms / Math.max(1, read));
                if (seen++ < 25) floor = Math.max(floor, rms);
                double threshold = Math.max(300, floor * 1.8);
                long now = System.currentTimeMillis();
                if (rms > threshold) { lastSpeech = now; speechMs += read * 1000L / RATE; }
                else if (lastSpeech > 0 && now - lastSpeech > SILENCE_MS) break;
                if (now - started > MAX_MS) break;
                if (lastSpeech == 0 && now - started > 8000) break;
            }
            running = false;
            if (speechMs < MIN_SPEECH_MS) { main.post(() -> listener.state("没有听到语音")); return; }
            listener.state("正在识别…");
            int total = 0;
            for (short[] chunk : chunks) total += chunk.length;
            float[] pcm = new float[total];
            int cursor = 0;
            for (short[] chunk : chunks) {
                for (short sample : chunk) pcm[cursor++] = sample / 32768.0f;
            }
            SpeechEngine engine = SpeechEngine.obtain(context);
            if (engine == null) { main.post(() -> listener.state("本地识别引擎不可用")); return; }
            String text;
            try { text = engine.transcribe(pcm); }
            catch (Exception error) { main.post(() -> listener.state("识别失败：" + error.getClass().getSimpleName())); return; }
            SpeechEngine.releaseIfIdle();
            if (text.isEmpty()) main.post(() -> listener.state("没有听清"));
            else main.post(() -> listener.result(text));
        } catch (Exception | LinkageError error) {
            running = false;
            main.post(() -> listener.state("麦克风不可用：" + error.getClass().getSimpleName()));
        } finally {
            record = null;
            if (recorder != null) {
                try { recorder.stop(); } catch (RuntimeException ignored) {}
                recorder.release();
            }
        }
    }

    void stop() {
        running = false;
        AudioRecord recorder = record;
        record = null;
        if (recorder != null) {
            try { recorder.stop(); } catch (RuntimeException ignored) {}
            recorder.release();
        }
    }
}
