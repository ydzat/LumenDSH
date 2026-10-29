package local.sliverwolf.web;

import android.content.Context;
import com.k2fsa.sherpa.onnx.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * On-device SenseVoice recognizer.
 *
 * The app already links the sherpa-onnx JNI runtime for the wake word and owner
 * voiceprint, so speech-to-text adds no dependency, only a model. The model is
 * downloaded once from the local host (integrations/speech) and verified before
 * use; nothing leaves the phone.
 *
 * Two threads is deliberate: this phone is big.LITTLE and 4 or 8 threads made
 * SenseVoice about 2.4x slower than 2 threads in measurement.
 */
final class SpeechEngine implements AutoCloseable {
    static final String MODEL_ID = "sensevoice-small";
    static final String MODEL_FILE = "model.int8.onnx";
    static final String TOKENS_FILE = "tokens.txt";
    static final String MODEL_HASH = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51";
    static final String TOKENS_HASH = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc";
    private static final long IDLE_RELEASE_MS = 5 * 60 * 1000;
    private static SpeechEngine shared;
    private static long idleSince;

    private final OfflineRecognizer recognizer;
    private final Vad vad;

    private SpeechEngine(Context context) throws Exception {
        // The wake word and owner voiceprint already load this library; loading it
        // again is idempotent, and doing it here keeps the recognizer usable even
        // when the wake path never ran in this process.
        LibraryLoader.setAutoLoadEnabled(false);
        System.loadLibrary("sherpa-onnx-jni");
        File dir = modelDir(context);
        OfflineSenseVoiceModelConfig sense = OfflineSenseVoiceModelConfig.builder()
            .setModel(new File(dir, MODEL_FILE).getAbsolutePath())
            .setLanguage("zh")
            .setInverseTextNormalization(true)
            .build();
        OfflineModelConfig model = OfflineModelConfig.builder()
            .setSenseVoice(sense)
            .setTokens(new File(dir, TOKENS_FILE).getAbsolutePath())
            .setNumThreads(2)
            .setDebug(false)
            .build();
        OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
            .setOfflineModelConfig(model)
            .setDecodingMethod("greedy_search")
            .build();
        recognizer = new OfflineRecognizer(config);
        vad = new Vad(VadModelConfig.builder().setSileroVadModelConfig(SileroVadModelConfig.builder()
            .setModel(asset(context, "silero_vad.onnx", "2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f").getAbsolutePath())
            .setMinSpeechDuration(.25f).setMaxSpeechDuration(30)
            .build()).setNumThreads(1).setDebug(false).build());
    }

    private static File asset(Context context, String name, String hash) throws Exception {
        File root = new File(context.getFilesDir(), "owner-models");
        if (!root.isDirectory() && !root.mkdirs()) throw new java.io.IOException("Owner model storage unavailable");
        File file = new File(root, name);
        if (!file.isFile() || !PetStore.hash(file).equals(hash)) {
            File part = new File(root, name + ".part");
            try (java.io.InputStream in = context.getAssets().open("wake/" + name);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            }
            if (!PetStore.hash(part).equals(hash) || !part.renameTo(file)) throw new java.io.IOException("Owner model integrity failed");
        }
        return file;
    }

    static File modelDir(Context context) {
        return new File(context.getFilesDir(), "speech/sensevoice");
    }

    static boolean ready(Context context) {
        File dir = modelDir(context);
        return new File(dir, MODEL_FILE).isFile() && new File(dir, TOKENS_FILE).isFile();
    }

    static synchronized SpeechEngine obtain(Context context) {
        if (shared == null) {
            try { shared = new SpeechEngine(context); }
            catch (Exception | LinkageError failure) { return null; }
        }
        idleSince = 0;
        return shared;
    }

    /** Release the recognizer after an idle period; call from a scheduled check. */
    static synchronized void releaseIfIdle() {
        if (shared == null) return;
        if (idleSince == 0) { idleSince = System.currentTimeMillis(); return; }
        if (System.currentTimeMillis() - idleSince < IDLE_RELEASE_MS) return;
        shared.close();
        shared = null;
    }

    /** Trim silence with the existing VAD, then decode the remaining speech. */
    String transcribe(float[] pcm) throws Exception {
        float[] speech = trim(pcm);
        if (speech.length == 0) return "";
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(speech, 16000);
            recognizer.decode(stream);
            OfflineRecognizerResult result = recognizer.getResult(stream);
            return result == null ? "" : result.getText().trim();
        } finally {
            stream.release();
        }
    }

    private float[] trim(float[] pcm) {
        vad.reset();
        for (int offset = 0; offset < pcm.length; offset += 512) {
            vad.acceptWaveform(Arrays.copyOfRange(pcm, offset, Math.min(pcm.length, offset + 512)));
        }
        vad.flush();
        List<float[]> parts = new ArrayList<>();
        int count = 0;
        while (!vad.empty()) {
            float[] part = vad.front().getSamples();
            parts.add(part);
            count += part.length;
            vad.pop();
        }
        float[] speech = new float[count];
        int cursor = 0;
        for (float[] part : parts) { System.arraycopy(part, 0, speech, cursor, part.length); cursor += part.length; }
        return speech;
    }

    @Override
    public void close() {
        recognizer.release();
        vad.release();
    }
}
