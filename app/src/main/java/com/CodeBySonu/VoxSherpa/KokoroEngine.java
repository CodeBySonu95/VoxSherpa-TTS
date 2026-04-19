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
    private String espeakDataPath = "";
    private int activeSpeakerId = 31;
    private volatile boolean cancelRequested = false;

    private KokoroEngine() {}

    // ── Singleton — thread-safe double-checked locking ───────────────────────
    public static KokoroEngine getInstance() {
        if (instance == null) {
            synchronized (KokoroEngine.class) {
                if (instance == null) instance = new KokoroEngine();
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
    private OfflineTts createTtsWithFallback(String onnxPath,
                                              String tokensPath,
                                              String voicesBinPath) {
        String[] providers = {"xnnpack", "cpu"};

        for (String provider : providers) {
            try {
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
                modelConfig.setNumThreads(getOptimalThreadCount());
                modelConfig.setProvider(provider);
                modelConfig.setDebug(false);

                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(modelConfig);
                config.setMaxNumSentences(3);
                config.setSilenceScale(0.2f);

                OfflineTts candidate = new OfflineTts(null, config);

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
    public synchronized String loadModel(Context context, String onnxPath,
                                          String tokensPath, String voicesBinPath) {
        cancelRequested = false; // Reset on new load

        if (tts != null && activeModelUri.equals(onnxPath)) return "Success";

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
            return "Success";

        } catch (Throwable t) {
            activeModelUri = "";
            tts = null;
            return "Error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    // ── Generate audio PCM ───────────────────────────────────────────────────
    public byte[] generateAudioPCM(String inputText, float speedValue, float pitchValue) {
        // 🚀 Cancel check — lock se pehle, instant return
        if (cancelRequested) return null;
        if (inputText == null || inputText.trim().isEmpty()) return null;

        OfflineTts localTts;
        synchronized (this) {
            if (tts == null) return null;
            localTts = tts; // Local reference — lock ke bahar use karenge
        }

        try {
            // Cancel check — native call se ek baar aur pehle
            if (cancelRequested) return null;

            GeneratedAudio audio = localTts.generate(inputText.trim(), activeSpeakerId, speedValue);

            if (cancelRequested) return null;

            if (audio == null) return null;

            float[] audioFloats = audio.getSamples();
            if (audioFloats == null || audioFloats.length == 0) return null;

            short[] shortSamples = new short[audioFloats.length];
            for (int i = 0; i < audioFloats.length; i++) {
                int val = (int) (audioFloats[i] * 32767.0f);
                if (val > 32767)  val = 32767;
                if (val < -32768) val = -32768;
                shortSamples[i] = (short) val;
            }

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
                    }
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
        }
    }
}
