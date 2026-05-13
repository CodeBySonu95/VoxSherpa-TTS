package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;

public class KokoroEngine {

    static {
        try {
            System.loadLibrary("sherpa-onnx-jni");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static volatile KokoroEngine instance;
    private OfflineTts tts;
    private String activeModelUri = "";
    // Tracks the current language context to prevent unnecessary reloads
    // Plus tokens/voices-bin paths and context so [setSilenceScale] can
    // rebuild the OfflineTts mid-session against the same model files.
    private String activeTokensUri = "";
    private String activeVoicesBinUri = "";
    private Context activeContext = null;
    private String activeLangCode = "";
    private String espeakDataPath = "";
    private int activeSpeakerId = 31;
    private volatile boolean cancelRequested = false;

    /** Within-sentence silence scale — controls the engine-level pause
     *  applied around commas and mid-phrase punctuation. Default 0.2f
     *  matches the previously-hardcoded behavior; consumers can drive
     *  it from a UI slider (e.g. storyvox's punctuation-cadence
     *  control) via [setSilenceScale]. Setting it on a loaded engine
     *  rebuilds the OfflineTts so the change takes effect mid-session
     *  on the next generated sentence — mirrors VoiceEngine's
     *  [VoiceEngine.setNoiseScale] pattern. */
    public static final float DEFAULT_SILENCE_SCALE = 0.2f;
    private volatile float silenceScale = DEFAULT_SILENCE_SCALE;

    private KokoroEngine() {}

    // ── Singleton — thread-safe double-checked locking ───────────────────────
    public static KokoroEngine getInstance() {
        if (instance == null) {
            synchronized (KokoroEngine.class) {
                if (instance == null) {
                    instance = new KokoroEngine();
                }
            }
        }
        return instance;
    }

    // ── Cancel ───────────────────────────────────────────────────────────────
    public void cancel() {
        cancelRequested = true;
    }

    // ── Smart thread count ───────────────────────────────────────────────────
    // [explicitNumThreads] overrides the auto heuristic when set to a positive
    // value via [loadModel]'s 5-arg overload. Defaults to 0 (use auto) so the
    // existing 4-arg [loadModel] callers see no behavior change.
    private int explicitNumThreads = 0;

    private int effectiveNumThreads() {
        return explicitNumThreads > 0 ? explicitNumThreads : getOptimalThreadCount();
    }

    private int getOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 8) return 4;
        if (cores >= 6) return 3;
        if (cores >= 4) return 2;
        return 1;
    }

    // ── espeak-ng-data extract ───────────────────────────────────────────────
    private synchronized void extractEspeakData(Context context) {
        if (context == null) return;
        File destDir = new File(context.getFilesDir(), "espeak-ng-data");
        String[] existing = destDir.list();

        if (!destDir.exists() || existing == null || existing.length == 0) {
            destDir.mkdirs();
            try (InputStream is = context.getAssets().open("espeak-ng-data.zip");
                 ZipInputStream zis = new ZipInputStream(is)) {

                ZipEntry ze;
                byte[] buffer = new byte[32768];
                while ((ze = zis.getNextEntry()) != null) {
                    File newFile = new File(destDir, ze.getName());
                    if (ze.isDirectory()) {
                        newFile.mkdirs();
                    } else {
                        File parent = newFile.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                        }
                    }
                    zis.closeEntry();
                }
            } catch (Exception ignored) {}
        }

        File nestedDir = new File(destDir, "espeak-ng-data");
        espeakDataPath = new File(nestedDir, "phontab").exists()
                ? nestedDir.getAbsolutePath()
                : destDir.getAbsolutePath();
    }

    // ── Provider fallback: XNNPACK → CPU ────────────────────────────────────
    private OfflineTts createTtsWithFallback(String onnxPath, String tokensPath, String voicesBinPath) {
        String[] providers = {"xnnpack", "cpu"};

        for (String provider : providers) {
            try {
                // Cancel check before initialization
                if (cancelRequested) return null;

                KokoroVoiceHelper.VoiceItem currentVoice = KokoroVoiceHelper.getById(activeSpeakerId);
                String langCode = (currentVoice != null) ? currentVoice.languageCode : "en";

                OfflineTtsKokoroModelConfig kokoroConfig = new OfflineTtsKokoroModelConfig();
                kokoroConfig.setModel(onnxPath);
                kokoroConfig.setTokens(tokensPath);
                kokoroConfig.setVoices(voicesBinPath);
                kokoroConfig.setDataDir(espeakDataPath);
                kokoroConfig.setLang(langCode);

                OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
                modelConfig.setKokoro(kokoroConfig);
                modelConfig.setNumThreads(effectiveNumThreads());
                modelConfig.setProvider(provider);
                modelConfig.setDebug(false);

                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(modelConfig);
                // Keep upstream's maxNumSentences=1 (System TTS prefers
                // per-sentence inference for snappy progress callbacks).
                // The PR's contribution is the silenceScale FIELD — let
                // it drive the value instead of the hardcoded 0.2f.
                config.setMaxNumSentences(1);
                config.setSilenceScale(silenceScale);

                OfflineTts candidate = new OfflineTts(null, config);

                // As confirmed by the user, Kokoro supports punctuations perfectly.
                GeneratedAudio test = candidate.generate("...", activeSpeakerId, 1.0f);
                if (test != null && test.getSamples() != null && test.getSamples().length > 0) {
                    return candidate;
                }

                try { candidate.release(); } catch (Throwable ignored) {}

            } catch (Throwable ignored) {}
        }

        return null;
    }

    // ── Load model ───────────────────────────────────────────────────────────
    /**
     * Overload that lets the caller override the numThreads passed to
     * sherpa-onnx. Pass [numThreads] = 0 for the auto heuristic (identical
     * behavior to the 4-arg [loadModel]); pass a positive value to override
     * the [getOptimalThreadCount] table — useful on thermally-constrained
     * devices where the auto value sustains too much load.
     */
    public synchronized String loadModel(Context context, String onnxPath,
                                          String tokensPath, String voicesBinPath,
                                          int numThreads) {
        this.explicitNumThreads = numThreads;
        return loadModel(context, onnxPath, tokensPath, voicesBinPath);
    }

    public synchronized String loadModel(Context context, String onnxPath, String tokensPath, String voicesBinPath) {
        cancelRequested = false;

        KokoroVoiceHelper.VoiceItem currentVoice = KokoroVoiceHelper.getById(activeSpeakerId);
        String targetLangCode = (currentVoice != null) ? currentVoice.languageCode : "en";

        // Avoid reloading if the exact same model and language code are already active
        if (tts != null && activeModelUri.equals(onnxPath) && activeLangCode.equals(targetLangCode)) {
            return "Success";
        }

        if (onnxPath == null || onnxPath.isEmpty())           return "Error: ONNX path is empty.";
        if (tokensPath == null || tokensPath.isEmpty())       return "Error: Tokens path is empty.";
        if (voicesBinPath == null || voicesBinPath.isEmpty()) return "Error: voices.bin path is empty.";

        File fOnnx   = new File(onnxPath);
        File fTokens = new File(tokensPath);
        File fVoices = new File(voicesBinPath);

        if (!fOnnx.exists()   || fOnnx.length() == 0)   return "Error: ONNX file missing.";
        if (!fTokens.exists() || fTokens.length() == 0) return "Error: Tokens file missing.";
        if (!fVoices.exists() || fVoices.length() == 0) return "Error: voices.bin missing.";

        try {
            destroy();
            extractEspeakData(context);

            if (espeakDataPath == null || espeakDataPath.isEmpty())
                return "Error: espeak-ng-data extraction failed.";

            tts = createTtsWithFallback(onnxPath, tokensPath, voicesBinPath);

            if (tts == null) return "Error: Model load failed on all providers.";

            activeModelUri = onnxPath;
            activeTokensUri = tokensPath;
            activeVoicesBinUri = voicesBinPath;
            activeContext = context.getApplicationContext();
            activeLangCode = targetLangCode;
            return "Success";

        } catch (Throwable t) {
            activeModelUri = "";
            activeLangCode = "";
            tts = null;
            return "Error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    // ── Generate audio PCM ───────────────────────────────────────────────────
    public byte[] generateAudioPCM(String inputText, float speedValue, float pitchValue) {
        // Immediate cancel check
        if (cancelRequested) return null;
        if (inputText == null || inputText.trim().isEmpty()) return null;

        OfflineTts localTts;
        synchronized (this) {
            if (tts == null) return null;
            localTts = tts; 
        }

        try {
            if (cancelRequested) return null;

            GeneratedAudio audio = localTts.generate(inputText.trim(), activeSpeakerId, speedValue);

            if (cancelRequested) return null;
            if (audio == null) return null;

            float[] audioFloats = audio.getSamples();
            if (audioFloats == null || audioFloats.length == 0) return null;

            // Float to Short conversion with anti-clipping bounds
            short[] shortSamples = new short[audioFloats.length];
            for (int i = 0; i < audioFloats.length; i++) {
                float f = audioFloats[i];
                if (f > 1.0f) f = 1.0f;
                if (f < -1.0f) f = -1.0f;
                shortSamples[i] = (short) (f * 32767.0f);
            }

            // Sonic pitch shifting
            if (pitchValue != 1.0f) {
                if (cancelRequested) return null;
                int sampleRate = localTts.sampleRate();
                if (sampleRate > 0) {
                    try {
                        com.CodeBySonu.VoxSherpa.Sonic sonic = new com.CodeBySonu.VoxSherpa.Sonic(sampleRate, 1);
                        sonic.setPitch(pitchValue);
                        sonic.writeShortToStream(shortSamples, shortSamples.length);
                        sonic.flushStream();
                        int available = sonic.samplesAvailable();
                        if (available > 0) {
                            short[] outSamples = new short[available];
                            sonic.readShortFromStream(outSamples, available);
                            shortSamples = outSamples;
                        }
                    } catch (Throwable ignored) {
                        // Fallback to original samples if Sonic fails
                    }
                }
            }

            if (cancelRequested) return null;

            // Short to PCM byte array (Little Endian format required by AudioTrack)
            byte[] pcmData = new byte[shortSamples.length * 2];
            for (int i = 0; i < shortSamples.length; i++) {
                pcmData[i * 2]     = (byte) (shortSamples[i] & 0xff);
                pcmData[i * 2 + 1] = (byte) ((shortSamples[i] >> 8) & 0xff);
            }

            return pcmData;

        } catch (Throwable t) {
            return null;
        }
    }

    // ── Sample rate ──────────────────────────────────────────────────────────
    public synchronized int getSampleRate() {
        if (tts == null) return 0;
        try {
            return tts.sampleRate();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ── Speaker / Voice control ──────────────────────────────────────────────
    public void setActiveSpeakerId(int speakerId) {
        this.activeSpeakerId = speakerId;
    }

    public int getActiveSpeakerId() {
        return activeSpeakerId;
    }

    public String getActiveVoiceName() {
        try {
            KokoroVoiceHelper.VoiceItem voice = KokoroVoiceHelper.getById(activeSpeakerId);
            return voice != null ? voice.getFullLabel() : "Unknown Voice";
        } catch (Throwable ignored) {
            return "Unknown Voice";
        }
    }

    public int getNumSpeakers() {
        try {
            return KokoroVoiceHelper.getAllVoices().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ── Silence scale (within-sentence pauses) ──────────────────────────────
    // The OfflineTtsConfig.silenceScale parameter controls the engine's
    // pause around commas and mid-phrase punctuation. Surfacing it as a
    // public knob lets consumers (e.g. storyvox's punctuation-cadence
    // slider) drive the cadence without forking the engine.
    //
    // Setting it on a loaded engine triggers an immediate rebuild of
    // the OfflineTts so the next [generateAudioPCM] call honors the new
    // scale — mirrors VoiceEngine's setNoiseScale + _reloadIfActive
    // pattern. The no-op fast-path skips both the assignment and the
    // reload when the value matches the active scale, so settings UIs
    // can call freely on every recomposition.
    public synchronized void setSilenceScale(float scale) {
        if (this.silenceScale == scale) return;
        this.silenceScale = scale;
        _reloadIfActive();
    }

    public float getSilenceScale() {
        return silenceScale;
    }

    // Internal: rebuild OfflineTts with the current (model, tokens,
    // voicesBin, silenceScale) values. Caller must hold the monitor on
    // `this`. No-op if no model is loaded. Mirrors the same pattern
    // VoiceEngine uses for its noise-scale setters.
    private void _reloadIfActive() {
        if (tts == null
                || activeModelUri.isEmpty()
                || activeTokensUri.isEmpty()
                || activeVoicesBinUri.isEmpty()
                || activeContext == null) {
            return;
        }
        cancelRequested = true;
        OfflineTts old = tts;
        OfflineTts replacement = createTtsWithFallback(
                activeModelUri, activeTokensUri, activeVoicesBinUri);
        if (replacement != null) {
            tts = replacement;
            try { old.release(); } catch (Throwable ignored) {}
        }
        // If replacement fails, we keep the old engine — better to
        // play with the previous silence scale than to drop into a
        // null state mid-session. cancelRequested gets reset on the
        // next loadModel call.
    }

    // ── State ────────────────────────────────────────────────────────────────
    public synchronized boolean isReady() {
        return tts != null;
    }

    public synchronized void destroy() {
        cancelRequested = false;
        if (tts != null) {
            try { tts.release(); } catch (Throwable ignored) {}
            tts = null;
            activeModelUri = "";
            activeTokensUri = "";
            activeVoicesBinUri = "";
            activeContext = null;
            activeLangCode = "";
        }
    }
}
