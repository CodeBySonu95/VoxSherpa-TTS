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
    private String espeakDataPath = "";
    private volatile boolean cancelRequested = false;

    private VoiceEngine() {}

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
                vits.setNoiseScale(0.35f);
                vits.setNoiseScaleW(0.667f);
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

            // FIX 2: Float → Short (With Anti-Clipping Logic for safety)
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
        }
    }
}
