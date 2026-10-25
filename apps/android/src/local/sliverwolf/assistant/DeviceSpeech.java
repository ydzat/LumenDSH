package local.sliverwolf.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import org.json.JSONObject;
import java.util.Locale;
import static local.sliverwolf.assistant.DshTransport.object;

/** Shared TTS owner for explicit tools and opted-in, correlated voice replies. */
final class DeviceSpeech {
    private final DeviceOperations operations;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final TextToSpeech tts;
    private final CharacterSpeech character;
    private String readiness = "initializing", activeKey = "", activeId = "";
    private boolean closed, profileChanging;
    private String initializedEngine="", activeText="", notice="";
    String notice(){return notice;}
    boolean synthesizing(){return localVoice()&&!activeKey.isEmpty()&&!character.playing();}
    private AssistantProfile.Voice profile;
    java.util.function.BooleanSupplier microphoneActive = () -> false;
    DeviceSpeech(Context context, DeviceOperations operations) {
        this.operations = operations;
        character=new CharacterSpeech(context);
        audio = context.getSystemService(AudioManager.class);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {
                if (change < 0) stopLocal("Audio focus lost");
            }, main).setAcceptsDelayedFocusGain(false).build();
        tts = new TextToSpeech(context, result -> main.post(() -> {
            if (closed) return;
            readiness = result == TextToSpeech.SUCCESS ? "ready" : "unavailable";
            if(result==TextToSpeech.SUCCESS) captureEngine();
        }));
        tts.setAudioAttributes(attributes);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { event(id, "running", "Playback started", false); }
            @Override public void onDone(String id) { event(id, "succeeded", "TTS playback completion callback received", true); }
            @Override public void onError(String id) { event(id, "failed", "TTS engine error", true); }
            @Override public void onError(String id, int code) { event(id, "failed", "TTS engine error " + code, true); }
            @Override public void onStop(String id, boolean interrupted) { event(id, "cancelled", "Playback stopped", true); }
        });
    }
    private void captureEngine() {initializedEngine=tts.getDefaultEngine();}
    String spokenText() {return activeKey.isEmpty()?"":activeText;}
    boolean profileReady() { return localVoice()?character.configured():"ready".equals(readiness); }
    private boolean localVoice(){return profile!=null&&CharacterSpeech.ENGINE.equals(profile.engine);}
    boolean characterConfigured(){return character.configured();}
    String systemEngine(){return initializedEngine;}
    void pairCharacter(String token){if(!activeKey.isEmpty())throw new IllegalStateException("Finish speech before pairing");character.pair(token);}
    void holdProfile(boolean hold) { profileChanging=hold; }
    AssistantProfile.Voice voiceProfile() {
        if(!profileReady()) throw new IllegalStateException("Speech engine is not ready");
        return profile!=null?profile:new AssistantProfile.Voice(initializedEngine,"zh-CN",1,1);
    }
    void beginProfile(AssistantProfile.Voice candidate) {
        if(!profileReady()) throw new IllegalStateException("Speech engine is not ready");
        if(microphoneActive.getAsBoolean()||!activeKey.isEmpty()||tts.isSpeaking())
            throw new IllegalStateException("Finish current voice interaction before switching profiles");
        if(CharacterSpeech.ENGINE.equals(candidate.engine)){
            if(!character.configured())throw new IllegalArgumentException("Character voice has not been paired");
            profileChanging=true;return;
        }
        if(!candidate.engine.equals(initializedEngine)) throw new IllegalArgumentException("Profile requires a different TTS engine");
        if(tts.isLanguageAvailable(Locale.forLanguageTag(candidate.language))<0)
            throw new IllegalArgumentException("Profile language is unavailable");
        AssistantProfile.Voice old=voiceProfile();
        try {
            if(tts.setSpeechRate(candidate.rate)!=TextToSpeech.SUCCESS||tts.setPitch(candidate.pitch)!=TextToSpeech.SUCCESS)
                throw new IllegalStateException("Speech engine rejected profile parameters");
        } finally {tts.setSpeechRate(old.rate);tts.setPitch(old.pitch);}
        profileChanging=true;
    }
    void finishProfile(AssistantProfile.Voice candidate) {if(candidate!=null) profile=candidate; profileChanging=false;}
    boolean isSpeaking() { return !activeKey.isEmpty() || tts.isSpeaking(); }
    JSONObject status() {
        return object("state", profileReady()?"ready":readiness, "engine", localVoice()?CharacterSpeech.ENGINE:(tts.getDefaultEngine() == null ? "unknown" : tts.getDefaultEngine()),
            "activeOperationId", activeId, "speaking", localVoice()?character.playing():tts.isSpeaking(), "synthesizing", localVoice()&&!activeKey.isEmpty()&&!character.playing(), "profileChanging", profileChanging,
            "rate",profile==null?1:profile.rate,"pitch",profile==null?1:profile.pitch,"language",profile==null?"zh-CN":profile.language);
    }
    JSONObject speak(JSONObject args) throws Exception {
        DeviceBridge.arguments(args, "operationKey", "text", "language");
        String key = args.getString("operationKey"), text = args.getString("text");
        String language = args.optString("language", "zh-CN");
        if (text.trim().isEmpty() || text.length() > 1000 || !(language.equals("zh-CN") || language.equals("en-US")))
            throw new IllegalArgumentException("invalid-input: text length 1..1000 and language zh-CN/en-US required");
        String fingerprint = DeviceOperations.digest(language + "\u0000" + text);
        JSONObject previous = operations.previous(key, "speak", fingerprint);
        if (previous != null) return previous;
        if(profileChanging) throw new IllegalStateException("busy: voice profile is changing");
        if (microphoneActive.getAsBoolean()) throw new IllegalStateException("busy: microphone recognition active");
        if (!profileReady()) throw new IllegalStateException("service-unavailable: TTS " + readiness);
        if (!activeKey.isEmpty()) throw new IllegalStateException("busy: speech already active");
        if (audio.getMode() != AudioManager.MODE_NORMAL) throw new IllegalStateException("busy: communication audio active");
        if (!localVoice() && tts.setLanguage(Locale.forLanguageTag(language)) < 0) throw new IllegalStateException("unsupported-capability: TTS language data unavailable");
        if(!localVoice() && (tts.setSpeechRate(profile==null?1:profile.rate)!=TextToSpeech.SUCCESS||tts.setPitch(profile==null?1:profile.pitch)!=TextToSpeech.SUCCESS))
            throw new IllegalStateException("Speech engine rejected voice parameters");
        JSONObject record = operations.reserve(key, "speak", fingerprint);
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            operations.transition(key, "failed", "Audio focus denied"); return operations.get(key);
        }
        activeKey = key; activeId = record.getString("operationId"); activeText=text; notice=localVoice()?"正在本地合成银狼语音…":"正在朗读";
        if(localVoice()){
            operations.transition(key,"running","Synthesizing character voice locally");
            try {character.speak(activeId,text,profile.rate,profile.pitch,this::event);}
            catch(Exception error){operations.transition(key,"failed",error.getMessage());clearActive();}
            return operations.get(key);
        }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, activeId) != TextToSpeech.SUCCESS) {
            operations.transition(key, "failed", "TTS queue rejected"); clearActive();
        }
        return operations.get(key);
    }
    JSONObject stop(JSONObject args) throws Exception {
        DeviceBridge.arguments(args, "operationKey", "operationId");
        String key = args.getString("operationKey"), target = args.optString("operationId", "");
        if (target.length() > 80) throw new IllegalArgumentException("invalid-input: operationId");
        String fingerprint = DeviceOperations.digest(target);
        JSONObject previous = operations.previous(key, "stop-speaking", fingerprint);
        if (previous != null) return previous;
        if (!target.isEmpty() && !target.equals(activeId)) throw new IllegalArgumentException("not-active: requested speech operation");
        JSONObject result = operations.reserve(key, "stop-speaking", fingerprint);
        boolean active = !activeKey.isEmpty();
        boolean stopped = stopLocal("Stopped by user or device tool");
        result.put("state", stopped ? "succeeded" : "unknown").put("detail", !stopped ? "TTS stop result uncertain" : active ? "Playback stopped" : "No active playback");
        operations.update(key, result); return result;
    }
    boolean stopLocal(String reason) {
        if (activeKey.isEmpty()) return true;
        notice="";character.stop();
        int result = tts.stop();
        try { operations.transition(activeKey, result == TextToSpeech.SUCCESS ? "cancelled" : "unknown", reason); }
        catch (Exception error) { readiness = "storage-failed"; }
        clearActive();
        return result == TextToSpeech.SUCCESS;
    }
    private void event(String id, String state, String detail, boolean terminal) {
        main.post(() -> {
            if (!id.equals(activeId)) return;
            notice="failed".equals(state)?"语音播放失败："+detail:terminal?"":"正在朗读";
            try { operations.transition(activeKey, state, detail); }
            catch (Exception error) { readiness = "storage-failed"; character.stop(); tts.stop(); clearActive(); return; }
            if (terminal) clearActive();
        });
    }
    private void clearActive() { activeKey = ""; activeId = ""; activeText=""; audio.abandonAudioFocusRequest(focus); }
    void close() { closed = true; stopLocal("Client closed"); tts.shutdown(); character.close(); }
}
