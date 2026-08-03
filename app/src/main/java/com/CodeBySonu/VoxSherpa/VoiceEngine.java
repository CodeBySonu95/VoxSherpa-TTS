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
    private String activeLanguage = "";
    private String espeakDataPath = "";
    private String chineseHelperDataPath = "";

    private volatile boolean cancelRequested = false;

    // Engine state tracking
    private volatile boolean isGenerating = false;

    // Lock object to prevent native memory release while generating
    private final Object nativeLock = new Object();

    // Dynamic speaker selection
    private volatile int activeSpeakerId = 0;

    // Tracks whether the currently-loaded model was treated as an MMS model.
    private volatile boolean activeModelIsMms = false;

    public static final float DEFAULT_NOISE_SCALE = 0.35f;
    public static final float DEFAULT_NOISE_SCALE_W = 0.667f;
    private volatile float noiseScale = DEFAULT_NOISE_SCALE;
    private volatile float noiseScaleW = DEFAULT_NOISE_SCALE_W;

    private VoiceEngine() {}

    // Singleton implementation
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

    public void cancel() {
        cancelRequested = true;
    }

    public void resetCancelState() {
        cancelRequested = false;
    }

    public boolean isBusy() {
        return isGenerating;
    }

    public boolean isActiveModelMms() {
        return activeModelIsMms;
    }

    private int getOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 8) return 4;
        if (cores >= 6) return 3;
        if (cores >= 4) return 2;
        return 1;
    }

    // espeak-ng-data extraction with nested directory protection
    private synchronized void extractEspeakData(Context context) {
        if (context == null) return;
        File destDir = new File(context.getFilesDir(), "espeak-ng-data");
        File nestedDir = new File(destDir, "espeak-ng-data");

        File phontabDirect = new File(destDir, "phontab");
        File phontabNested = new File(nestedDir, "phontab");

        if (phontabDirect.exists() && phontabDirect.length() > 0) {
            espeakDataPath = destDir.getAbsolutePath();
            return;
        } else if (phontabNested.exists() && phontabNested.length() > 0) {
            espeakDataPath = nestedDir.getAbsolutePath();
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

        if (new File(nestedDir, "phontab").exists()) {
            espeakDataPath = nestedDir.getAbsolutePath();
        } else {
            espeakDataPath = destDir.getAbsolutePath();
        }
    }

    // Chinese Helper extract
    private synchronized void extractChineseHelperData(Context context) {
        if (context == null) return;
        File destDir = new File(context.getFilesDir(), "chinese_helper_data");

        File phoneFst = new File(destDir, "phone.fst");

        if (destDir.exists() && phoneFst.exists() && phoneFst.length() > 0) {
            chineseHelperDataPath = destDir.getAbsolutePath();
            return;
        }

        destDir.mkdirs();

        try (InputStream is = context.getAssets().open("chinese_helper.zip");
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

        chineseHelperDataPath = destDir.getAbsolutePath();
    }

    // Provider fallback: XNNPACK to CPU
    private OfflineTts _createTtsWithFallback(String modelPath, String tokensPath, String language, boolean isMms) {
        String[] providers = {"xnnpack", "cpu"};

        for (String provider : providers) {
            try {
                OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
                vits.setModel(modelPath);
                vits.setTokens(tokensPath);

                boolean isChinese = language != null && (language.toLowerCase().contains("zh") || language.toLowerCase().contains("chinese"));

                if (isChinese) {
                    vits.setDataDir("");
                    if (chineseHelperDataPath != null && !chineseHelperDataPath.isEmpty()) {
                        vits.setLexicon(new File(chineseHelperDataPath, "lexicon.txt").getAbsolutePath());
                        vits.setDictDir(new File(chineseHelperDataPath, "dict").getAbsolutePath());
                    }
                } else {
                    if (isMms) {
                        vits.setDataDir("");
                    } else {
                        vits.setDataDir(espeakDataPath);
                    }
                    vits.setLexicon("");
                    vits.setDictDir("");
                }

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

                if (isChinese && chineseHelperDataPath != null && !chineseHelperDataPath.isEmpty()) {
                    String phone = new File(chineseHelperDataPath, "phone.fst").getAbsolutePath();
                    String date = new File(chineseHelperDataPath, "date.fst").getAbsolutePath();
                    String number = new File(chineseHelperDataPath, "number.fst").getAbsolutePath();
                    String heteronym = new File(chineseHelperDataPath, "new_heteronym.fst").getAbsolutePath();

                    config.setRuleFsts(phone + "," + date + "," + number + "," + heteronym);
                }

                OfflineTts candidate = new OfflineTts(null, config);

                String testText;
                if (isChinese) {
                    testText = "测试";
                } else if (isMms) {
                    testText = firstUsableToken(tokensPath);
                } else {
                    testText = "Hello";
                }

                GeneratedAudio test = candidate.generate(testText, 0, 1.0f);
                if (test != null && test.getSamples() != null && test.getSamples().length > 0) {
                    return candidate;
                }

                try { candidate.release(); } catch (Throwable ignored) {}

            } catch (Throwable ignored) {}
        }

        return null;
    }

    private String firstUsableToken(String tokensPath) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(tokensPath), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sep = line.lastIndexOf(' ');
                String symbol = sep > 0 ? line.substring(0, sep) : line;
                symbol = symbol.trim();
                if (!symbol.isEmpty() && Character.isLetter(symbol.charAt(0))) {
                    return symbol;
                }
            }
        } catch (Exception ignored) {}
        return "a";
    }

    // Load model (Original Signature - Default to Piper logic)
    public synchronized String loadModel(Context context, String modelPath, String tokensPath, String language) {
        return loadModel(context, modelPath, tokensPath, language, false);
    }

    // Load model (New Overload - Explicitly define if it is an MMS model)
    public synchronized String loadModel(Context context, String modelPath, String tokensPath, String language, Boolean explicitIsMms) {
        cancelRequested = false;
        if (tts != null && activeModelUri.equals(modelPath) && activeLanguage.equals(language)) return "Success";

        if (modelPath == null || modelPath.isEmpty())   return "Error: Model path is empty.";
        if (tokensPath == null || tokensPath.isEmpty()) return "Error: Tokens path is empty.";

        File modelFile  = new File(modelPath);
        File tokensFile = new File(tokensPath);

        if (!modelFile.exists()  || modelFile.length() == 0)  return "Error: Model file missing.";
        if (!tokensFile.exists() || tokensFile.length() == 0) return "Error: Tokens file missing.";

        try {
            destroy();

            boolean isChinese = language != null && (language.toLowerCase().contains("zh") || language.toLowerCase().contains("chinese"));
            
            // If explicitIsMms is not provided by the developer, safely default to Piper (false)
            boolean isMms = (explicitIsMms != null) ? explicitIsMms : false;

            if (isChinese) {
                extractChineseHelperData(context);
                if (chineseHelperDataPath == null || chineseHelperDataPath.isEmpty()) {
                    return "Error: Chinese helper data extraction failed.";
                }
            } else {
                extractEspeakData(context);
                if (espeakDataPath == null || espeakDataPath.isEmpty()) {
                    return "Error: espeak-ng-data extraction failed.";
                }
            }

            tts = _createTtsWithFallback(modelPath, tokensPath, language, isMms);

            if (tts == null) return "Error: Model load failed on all providers.";

            activeModelUri = modelPath;
            activeTokensUri = tokensPath;
            activeLanguage = language != null ? language : "";
            activeModelIsMms = isMms;

            return "Success";

        } catch (Throwable t) {
            activeModelUri = "";
            activeLanguage = "";
            activeModelIsMms = false;
            tts = null;
            return "Error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    // Generate audio PCM
    public byte[] generateAudioPCM(String inputText, float speedValue, float pitchValue) {
        if (cancelRequested) {
            return null;
        }
        if (inputText == null || inputText.trim().isEmpty()) {
            return null;
        }

        float[] audioFloats = null;
        int sampleRate = 0;

        synchronized (this) {
            if (tts == null || cancelRequested || isGenerating) {
                return null;
            }
            isGenerating = true;
        }

        try {
            try {
                GeneratedAudio audio = null;
                synchronized (nativeLock) {
                    if (tts != null && !cancelRequested) {
                        audio = tts.generate(inputText.trim(), activeSpeakerId, speedValue);
                        if (audio != null) {
                            sampleRate = tts.sampleRate();
                        }
                    }
                }

                if (audio != null) {
                    audioFloats = audio.getSamples();
                }
            } catch (Throwable t) {
                return null;
            }

            if (cancelRequested || audioFloats == null || audioFloats.length == 0) {
                return null;
            }

            short[] shortSamples = new short[audioFloats.length];
            for (int i = 0; i < audioFloats.length; i++) {
                float f = audioFloats[i];
                if (f > 1.0f) f = 1.0f;
                if (f < -1.0f) f = -1.0f;
                shortSamples[i] = (short) (f * 32767.0f);
            }

            if (pitchValue != 1.0f) {
                if (cancelRequested) {
                    return null;
                }
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

            if (cancelRequested) {
                return null;
            }

            byte[] pcmData = new byte[shortSamples.length * 2];
            for (int i = 0; i < shortSamples.length; i++) {
                pcmData[i * 2]     = (byte) (shortSamples[i] & 0xff);
                pcmData[i * 2 + 1] = (byte) ((shortSamples[i] >> 8) & 0xff);
            }

            return pcmData;

        } catch (Throwable t) {
            return null;
        } finally {
            isGenerating = false;
        }
    }

    public synchronized int getSampleRate() {
        if (tts == null) return 0;
        return tts.sampleRate();
    }

    public synchronized boolean isReady() {
        return tts != null;
    }

    public void destroy() {
        cancelRequested = false;
        synchronized (nativeLock) {
            if (tts != null) {
                try { tts.release(); } catch (Throwable ignored) {}
                tts = null;
                activeModelUri = "";
                activeTokensUri = "";
                activeLanguage = "";
                activeModelIsMms = false;
            }
        }
    }

    public void setActiveSpeakerId(int speakerId) {
        this.activeSpeakerId = speakerId;
    }

    public synchronized int getNumSpeakers() {
        if (tts == null) return 1;
        try {
            return tts.numSpeakers();
        } catch (Throwable ignored) {
            return 1;
        }
    }

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

    private void _reloadIfActive() {
        if (tts == null || activeModelUri.isEmpty() || activeTokensUri.isEmpty()) {
            return;
        }
        cancelRequested = true;

        String model = activeModelUri;
        String tokens = activeTokensUri;
        String language = activeLanguage;
        boolean isMms = activeModelIsMms;

        synchronized (nativeLock) {
            try { tts.release(); } catch (Throwable ignored) {}
            tts = null;
            activeModelUri = "";
            activeTokensUri = "";
            activeLanguage = "";
        }

        cancelRequested = false;

        OfflineTts rebuilt = _createTtsWithFallback(model, tokens, language, isMms);

        synchronized (nativeLock) {
            if (rebuilt != null) {
                tts = rebuilt;
                activeModelUri = model;
                activeTokensUri = tokens;
                activeLanguage = language;
                activeModelIsMms = isMms;
            }
        }
    }
}
