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
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;

public class VoiceEngine {

    static {
        try {
            System.loadLibrary("sherpa-onnx-jni");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static volatile VoiceEngine instance;
    private OfflineTts tts;
    private String activeModelUri = "";
    private String activeTokensUri = "";
    private String espeakDataPath = "";
    private volatile boolean cancelRequested = false;

    // ── VITS sampling knobs ──────────────────────────────────────────────────
    // VoxSherpa's "calmed" defaults — lower than sherpa-onnx upstream
    // (0.667 / 0.8) for steadier, more deterministic Piper output. Consumers
    // can flip these via setNoiseScale[W]() to opt into more expressive
    // (but less reproducible) synthesis.
    public static final float DEFAULT_NOISE_SCALE = 0.35f;
    public static final float DEFAULT_NOISE_SCALE_W = 0.667f;
    private volatile float noiseScale = DEFAULT_NOISE_SCALE;
    private volatile float noiseScaleW = DEFAULT_NOISE_SCALE_W;

    /**
     * Public constructor — added 2026-05-09 (jphein fork) to enable
     * multi-instance parallelism. Each instance owns its own
     * onnxruntime session via the [tts] field; calls into
     * [generateAudioPCM] are serialized per-instance via the
     * synchronized monitor on `this`. Two instances loaded against
     * the same model file → two independent OrtSessions, generate
     * runs in parallel.
     *
     * Memory cost: each loaded Piper-high session is ~150 MB resident
     * (1.5–2× the on-disk model size). Multi-instance use should
     * verify the device has enough RAM; on a 3 GB Helio P22T two
     * instances fit but three may LMK-kill.
     *
     * Existing callers continue to use [getInstance] for the singleton
     * path; the storyvox Tier 3 (parallel synth) path constructs
     * additional instances as `new VoiceEngine()`.
     */
    public VoiceEngine() {}

    // ── Singleton — thread-safe double-checked locking ───────────────────────
    public static VoiceEngine getInstance() {
        if (instance == null) {
            synchronized (VoiceEngine.class) {
                if (instance == null) {
                    instance = new VoiceEngine();
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
    private int getOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 8) return 4;
        if (cores >= 6) return 3;
        if (cores >= 4) return 2;
        return 1;
    }

    // ── espeak-ng-data extract ───────────────────────────────────────────────
    private synchronized void extractEspeakData(Context context) {
        File destDir = new File(context.getFilesDir(), "espeak-ng-data");
        String[] existing = destDir.list();

        if (destDir.exists() && existing != null && existing.length > 0) {
            espeakDataPath = destDir.getAbsolutePath();
            return;
        }

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
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception ignored) {}

        espeakDataPath = destDir.getAbsolutePath();
    }

    // ── Provider fallback: XNNPACK → CPU ────────────────────────────────────
    private OfflineTts _createTtsWithFallback(String modelPath, String tokensPath) {
        String[] providers = {"xnnpack", "cpu"};

        for (String provider : providers) {
            try {
                OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
                vits.setModel(modelPath);
                vits.setTokens(tokensPath);
                vits.setDataDir(espeakDataPath);
                vits.setNoiseScale(noiseScale);
                vits.setNoiseScaleW(noiseScaleW);
                vits.setLengthScale(1.0f);

                OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
                modelConfig.setVits(vits);
                modelConfig.setNumThreads(getOptimalThreadCount());
                modelConfig.setProvider(provider);
                modelConfig.setDebug(false);

                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(modelConfig);
                config.setMaxNumSentences(5);

                OfflineTts candidate = new OfflineTts(null, config);

                // 🚀 FIX 1: "..." ki jagah "Hello" use kiya taaki engine safe state me initialize ho
                GeneratedAudio test = candidate.generate("Hello", 0, 1.0f);
                if (test != null && test.getSamples() != null && test.getSamples().length > 0) {
                    return candidate;
                }

                try { candidate.release(); } catch (Throwable ignored) {}

            } catch (Throwable ignored) {}
        }

        return null;
    }

    // ── Load model ───────────────────────────────────────────────────────────
    public synchronized String loadModel(Context context, String modelPath, String tokensPath) {
        cancelRequested = false; // Reset on new load
        if (tts != null && activeModelUri.equals(modelPath)) return "Success";

        if (modelPath == null || modelPath.isEmpty())   return "Error: Model path is empty.";
        if (tokensPath == null || tokensPath.isEmpty()) return "Error: Tokens path is empty.";

        File modelFile  = new File(modelPath);
        File tokensFile = new File(tokensPath);

        if (!modelFile.exists()  || modelFile.length() == 0)  return "Error: Model file missing.";
        if (!tokensFile.exists() || tokensFile.length() == 0) return "Error: Tokens file missing.";

        try {
            destroy();
            extractEspeakData(context);

            if (espeakDataPath == null || espeakDataPath.isEmpty()) {
                return "Error: espeak-ng-data extraction failed.";
            }

            tts = _createTtsWithFallback(modelPath, tokensPath);

            if (tts == null) return "Error: Model load failed on all providers.";

            activeModelUri = modelPath;
            activeTokensUri = tokensPath;
            return "Success";

        } catch (Throwable t) {
            activeModelUri = "";
            tts = null;
            return "Error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    // ── Generate audio PCM ───────────────────────────────────────────────────
    public byte[] generateAudioPCM(String inputText, float speedValue, float pitchValue) {
        if (cancelRequested) return null;
        if (inputText == null || inputText.trim().isEmpty()) return null;

        OfflineTts localTts;
        synchronized (this) {
            if (tts == null) return null;
            localTts = tts;
        }

        try {
            if (cancelRequested) return null;

            GeneratedAudio audio = localTts.generate(inputText.trim(), 0, speedValue);

            if (cancelRequested) return null;

            if (audio == null) return null;

            float[] audioFloats = audio.getSamples();
            if (audioFloats == null || audioFloats.length == 0) return null;
            short[] shortSamples = new short[audioFloats.length];
            for (int i = 0; i < audioFloats.length; i++) {
                float f = audioFloats[i];
                if (f > 1.0f) f = 1.0f;
                if (f < -1.0f) f = -1.0f;
                shortSamples[i] = (short) (f * 32767.0f);
            }

            if (pitchValue != 1.0f) {
                if (cancelRequested) return null;
                int sampleRate = localTts.sampleRate();
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
            }

            if (cancelRequested) return null;

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
        return tts.sampleRate();
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
        }
    }

    // ── Noise-scale tuning (VITS / Piper / Matcha) ───────────────────────────
    //
    // `noise_scale` and `noise_scale_w` are sampling knobs on the VITS prior:
    //  • Lower values → drier, more deterministic output. Identical input text
    //    re-renders sound nearly identical between runs.
    //  • Higher values → the model samples more from its prior, giving slightly
    //    different prosody on each generation.
    //
    // VoxSherpa's calmed defaults are (0.35, 0.667). sherpa-onnx upstream's
    // "Vanilla" Piper defaults are (0.667, 0.8). Consumers (e.g. storyvox's
    // Voice Determinism toggle) flip between presets.
    //
    // Behavior:
    //  • If a model is currently loaded, the engine destroys + reconstructs
    //    `OfflineTts` with the new config. This blocks for ~1-3s on Piper.
    //  • If no model is loaded, the new value is stored and applied on the
    //    next loadModel() call.
    //  • Calling during synthesis: the synchronized monitor on the engine
    //    serializes against loadModel/destroy, but a thread already inside
    //    `generateAudioPCM` past the synchronized block holds a `localTts`
    //    reference and may finish a generation against the *old* config
    //    before observing the swap. cancelRequested is raised so cooperating
    //    callers bail early. This matches `loadModel`'s existing semantics.
    //
    // No-op if the supplied value equals the currently-active value, so
    // settings UIs can call freely without forcing a reload.
    public synchronized void setNoiseScale(float scale) {
        if (this.noiseScale == scale) return;
        this.noiseScale = scale;
        _reloadIfActive();
    }

    public synchronized void setNoiseScaleW(float scaleW) {
        if (this.noiseScaleW == scaleW) return;
        this.noiseScaleW = scaleW;
        _reloadIfActive();
    }

    public float getNoiseScale() {
        return noiseScale;
    }

    public float getNoiseScaleW() {
        return noiseScaleW;
    }

    // Internal: rebuild OfflineTts with the current (model, tokens, noise*)
    // values. Caller must hold the monitor on `this`. No-op if no model is
    // loaded.
    private void _reloadIfActive() {
        if (tts == null || activeModelUri.isEmpty() || activeTokensUri.isEmpty()) {
            return;
        }
        cancelRequested = true;

        String model = activeModelUri;
        String tokens = activeTokensUri;

        try { tts.release(); } catch (Throwable ignored) {}
        tts = null;
        activeModelUri = "";
        activeTokensUri = "";

        cancelRequested = false;

        OfflineTts rebuilt = _createTtsWithFallback(model, tokens);
        if (rebuilt != null) {
            tts = rebuilt;
            activeModelUri = model;
            activeTokensUri = tokens;
        }
    }
}
