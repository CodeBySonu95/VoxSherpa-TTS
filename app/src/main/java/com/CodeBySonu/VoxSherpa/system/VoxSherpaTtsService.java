package com.CodeBySonu.VoxSherpa.system;

import android.speech.tts.TextToSpeechService;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.SynthesisCallback;
import android.media.AudioFormat;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

import com.CodeBySonu.VoxSherpa.VoiceEngine;
import com.CodeBySonu.VoxSherpa.KokoroEngine;
import com.CodeBySonu.VoxSherpa.KokoroVoiceHelper;
import com.CodeBySonu.VoxSherpa.AudioEmotionHelper;

public class VoxSherpaTtsService extends TextToSpeechService {

    private String _lastLoadedKokoroModel = "";
    private int _lastLoadedSpeakerId      = -1;
    private String _lastLoadedVoiceModel  = "";
    private String _lastLoadedModelType   = ""; 
    private volatile boolean isSynthesisCancelled = false;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private String getTokensPath(java.util.HashMap<String, Object> m) {
        if (m.containsKey("tokens_path") && m.get("tokens_path") != null) {
            String p = m.get("tokens_path").toString();
            if (!p.isEmpty()) return p;
        }
        if (m.containsKey("lexicon_path") && m.get("lexicon_path") != null) {
            String p = m.get("lexicon_path").toString();
            if (!p.isEmpty()) return p;
        }
        if (m.containsKey("config_path") && m.get("config_path") != null) {
            String p = m.get("config_path").toString();
            if (!p.isEmpty()) return p;
        }
        return "";
    }

    private String getAppDefaultLanguage() {
        SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
        String allData = sp.getString("models_data", "[]");
        java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
            new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

        if (downloadedModels != null && !downloadedModels.isEmpty()) {
            for (java.util.HashMap<String, Object> m : downloadedModels) {
                if (m.containsKey("type") && m.get("type").toString().contains("Kokoro")) {
                    return "English"; 
                } else if (m.containsKey("language") && m.get("language") != null) {
                    return m.get("language").toString();
                }
            }
        }
        return "English";
    }

    @Override
    protected String[] onGetLanguage() {
        return TtsLocaleHelper.getTtsLanguageArray(getAppDefaultLanguage());
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
        String allData = sp.getString("models_data", "[]");
        java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
            new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

        if (downloadedModels != null) {
            boolean isKokoroDownloaded = false;
            for (java.util.HashMap<String, Object> m : downloadedModels) {
                String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                if (!onnxPath.isEmpty()) {
                    boolean isKokoroType = m.containsKey("type") && m.get("type").toString().contains("Kokoro");
                    if (isKokoroType) {
                        isKokoroDownloaded = true;
                    } else {
                        String rawLanguage = m.containsKey("language") && m.get("language") != null ? m.get("language").toString() : "";
                        if (!rawLanguage.isEmpty()) {
                            Locale loc = TtsLocaleHelper.getLocaleFromName(rawLanguage);
                            if (loc != null) {
                                if (loc.getISO3Language().equalsIgnoreCase(lang) || loc.getLanguage().equalsIgnoreCase(lang)) {
                                    return TextToSpeech.LANG_AVAILABLE; 
                                }
                            }
                        }
                    }
                }
            }

            if (isKokoroDownloaded) {
                java.util.List<String> kokoroLangs = KokoroVoiceHelper.getAvailableLanguages();
                for (String rawLang : kokoroLangs) {
                    Locale loc = TtsLocaleHelper.getLocaleFromName(rawLang);
                    if (loc != null) {
                        if (loc.getISO3Language().equalsIgnoreCase(lang) || loc.getLanguage().equalsIgnoreCase(lang)) {
                            return TextToSpeech.LANG_AVAILABLE; 
                        }
                    }
                }
            }
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

            @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        try {
            // Android OS ke rules ke hisaab se strict check ko safe banaya (< 0 means not supported)
            if (onIsLanguageAvailable(lang, country, variant) < TextToSpeech.LANG_AVAILABLE) {
                return null;
            }

            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            SharedPreferences sp5 = getSharedPreferences("sp5", MODE_PRIVATE);
            
            String allData = sp.getString("models_data", "[]");
            if (allData == null || allData.isEmpty()) allData = "[]";

            java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
                new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

            if (downloadedModels != null) {
                String matchedRawLang = "";
                java.util.HashSet<String> allRawLangs = new java.util.HashSet<>();
                
                boolean hasKokoro = false;
                for (java.util.HashMap<String, Object> m : downloadedModels) {
                    if (m.containsKey("type") && m.get("type").toString().contains("Kokoro")) {
                        hasKokoro = true;
                    } else {
                        if (m.containsKey("language") && m.get("language") != null) {
                            allRawLangs.add(m.get("language").toString());
                        }
                    }
                }
                if (hasKokoro) {
                    allRawLangs.addAll(KokoroVoiceHelper.getAvailableLanguages());
                }

                for (String raw : allRawLangs) {
                    Locale loc = TtsLocaleHelper.getLocaleFromName(raw);
                    if (loc != null) {
                        try {
                            if (loc.getISO3Language().equalsIgnoreCase(lang) || loc.getLanguage().equalsIgnoreCase(lang)) {
                                matchedRawLang = raw;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (!matchedRawLang.isEmpty()) {
                    String sysDataJson = sp5.getString("sys_tts_" + matchedRawLang, "");
                    if (!sysDataJson.isEmpty()) {
                        org.json.JSONObject sysJson = new org.json.JSONObject(sysDataJson);
                        String targetType = sysJson.optString("model_type", "");
                        String targetOnnx = sysJson.optString("onnx_path", "");
                        int targetSpeakerId = sysJson.optInt("speaker_id", -1);

                        if (targetType.equals("kokoro")) {
                            java.util.List<KokoroVoiceHelper.VoiceItem> kVoices = KokoroVoiceHelper.getAllVoices();
                            if (kVoices != null) {
                                for (KokoroVoiceHelper.VoiceItem kv : kVoices) {
                                    if (kv.speakerId == targetSpeakerId) {
                                        return kv.displayName + " (Kokoro)"; 
                                    }
                                }
                            }
                        } else if (targetType.equals("vits")) {
                            for (java.util.HashMap<String, Object> m : downloadedModels) {
                                String op = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                                if (op.equals(targetOnnx)) {
                                    return m.containsKey("name") && m.get("name") != null ? m.get("name").toString() : null;
                                }
                            }
                        }
                    }
                }
            }

            // Fallback Engine
            List<Voice> availableVoices = onGetVoices();
            if (availableVoices != null) {
                for (Voice voice : availableVoices) {
                    Locale voiceLocale = voice.getLocale();
                    if (voiceLocale != null) {
                        try {
                            if (voiceLocale.getISO3Language().equalsIgnoreCase(lang) || 
                                voiceLocale.getLanguage().equalsIgnoreCase(lang)) {
                                return voice.getName();
                            }
                        } catch (Exception ignored) {
                            if (voiceLocale.getLanguage().equalsIgnoreCase(lang)) {
                                return voice.getName();
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Android ke Binder ko crash hone se bachane ke liye unbreakable shield
        }
        return null;
    }

    @Override
    public List<Voice> onGetVoices() {
        List<Voice> voiceList = new ArrayList<>();
        try {
            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            String allData = sp.getString("models_data", "[]");
            if (allData == null || allData.isEmpty()) allData = "[]";
            
            java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
                new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

            if (downloadedModels != null) {
                boolean isKokoroDownloaded = false;
                for (java.util.HashMap<String, Object> m : downloadedModels) {
                    String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                    if (!onnxPath.isEmpty()) {
                        boolean isKokoroType = m.containsKey("type") && m.get("type").toString().contains("Kokoro");
                        
                        String rawLanguage = m.containsKey("language") && m.get("language") != null ? m.get("language").toString() : "";
                        Locale loc = null;
                        if (!rawLanguage.isEmpty()) {
                            loc = TtsLocaleHelper.getLocaleFromName(rawLanguage);
                        }
                        if (loc == null) loc = new Locale("eng");

                        if (isKokoroType) {
                            isKokoroDownloaded = true;
                        } else {
                            String name = m.containsKey("name") && m.get("name") != null ? m.get("name").toString() : "Unknown";
                            Set<String> emptyFeatures = new HashSet<>();
                            voiceList.add(new Voice(name, loc, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, emptyFeatures));
                        }
                    }
                }

                if (isKokoroDownloaded) {
                    java.util.List<KokoroVoiceHelper.VoiceItem> allKVoices = KokoroVoiceHelper.getAllVoices();
                    if (allKVoices != null) {
                        for (KokoroVoiceHelper.VoiceItem kv : allKVoices) {
                            Locale loc = TtsLocaleHelper.getLocaleFromName(kv.language);
                            if (loc != null) {
                                Set<String> emptyFeatures = new HashSet<>();
                                String voiceId = kv.displayName + " (Kokoro)"; 
                                voiceList.add(new Voice(voiceId, loc, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, emptyFeatures));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Unbreakable shield to ensure TTS Settings never disconnects
        }
        return voiceList;
    }
    
    @Override
    public int onIsValidVoiceName(String voiceName) {
        if (voiceName != null) {
            // Accept any name passed, validation is handled in onSynthesizeText
            return TextToSpeech.SUCCESS;
        }
        return TextToSpeech.ERROR;
    }

    @Override
    public int onLoadVoice(String voiceName) {
        return onIsValidVoiceName(voiceName);
    }

    @Override
    protected void onStop() {
        isSynthesisCancelled = true;
        try {
            VoiceEngine.getInstance().cancel();
            KokoroEngine.getInstance().cancel();
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        try {
            VoiceEngine.getInstance().cancel();
            KokoroEngine.getInstance().cancel();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isSynthesisCancelled = false;
        boolean hasError = false; 
        boolean emittedAudio = false; 
        
        try {
            SharedPreferences sp3 = getSharedPreferences("sp3", MODE_PRIVATE);
            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            SharedPreferences sp5 = getSharedPreferences("sp5", MODE_PRIVATE);
            
            CharSequence charText = request.getCharSequenceText();
            if (charText == null || charText.toString().trim().isEmpty()) {
                int startResult = callback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
                if (startResult != TextToSpeech.SUCCESS) {
                    callback.error();
                    hasError = true;
                }
                return; 
            }
            String text = charText.toString().trim();

            int systemSpeechRate = request.getSpeechRate();
            int systemPitch      = request.getPitch();

            final float engineSpeed = (systemSpeechRate > 0) ? (systemSpeechRate / 100.0f) : 1.0f;
            final float enginePitch = (systemPitch > 0)      ? (systemPitch / 100.0f)      : 1.0f;

            String reqIsoLang = request.getLanguage(); 
            String reqVoice = request.getVoiceName();

            String allData = sp.getString("models_data", "[]");
            java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
                new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

            if (downloadedModels == null || downloadedModels.isEmpty()) {
                callback.error();
                hasError = true;
                return;
            }

            String targetModelType = "";
            String targetOnnx = "";
            String targetTokens = "";
            String targetVoicesBin = "";
            int targetSpeakerId = -1;
            boolean voiceFound = false;
            String matchedRawLang = "";

            if (reqVoice != null) {
                if (reqVoice.startsWith("VoxSherpa_")) {
                    // Internal generic language request from Native Android settings
                    reqIsoLang = reqVoice.replace("VoxSherpa_", "");
                } else {
                    // Specific model request from 3rd party apps without prefix
                    if (reqVoice.contains("(Kokoro)")) {
                        String targetKokoroName = reqVoice.replace(" (Kokoro)", "");
                        java.util.List<KokoroVoiceHelper.VoiceItem> allKVoices = KokoroVoiceHelper.getAllVoices();
                        for (KokoroVoiceHelper.VoiceItem kv : allKVoices) {
                            if (kv.displayName.equals(targetKokoroName)) {
                                targetSpeakerId = kv.speakerId;
                                matchedRawLang = kv.language;
                                
                                for (java.util.HashMap<String, Object> m : downloadedModels) {
                                    if (m.containsKey("type") && m.get("type").toString().contains("Kokoro")) {
                                        String op = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                                        String tk = getTokensPath(m);
                                        String vb = m.containsKey("voices_bin_path") && m.get("voices_bin_path") != null ? m.get("voices_bin_path").toString() : "";
                                        if (!op.isEmpty() && !tk.isEmpty() && !vb.isEmpty()) {
                                            targetModelType = "kokoro";
                                            targetOnnx = op;
                                            targetTokens = tk;
                                            targetVoicesBin = vb;
                                            voiceFound = true;
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        String targetVitsName = reqVoice;
                        for (java.util.HashMap<String, Object> m : downloadedModels) {
                            String mName = m.containsKey("name") && m.get("name") != null ? m.get("name").toString() : "";
                            if (targetVitsName.equals(mName)) {
                                String op = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                                String tk = getTokensPath(m);
                                if (!op.isEmpty() && !tk.isEmpty()) {
                                    targetModelType = "vits";
                                    targetOnnx = op;
                                    targetTokens = tk;
                                    voiceFound = true;
                                    matchedRawLang = m.containsKey("language") && m.get("language") != null ? m.get("language").toString() : "";
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (!voiceFound) {
                if (reqIsoLang == null || reqIsoLang.isEmpty()) reqIsoLang = "eng";

                java.util.HashSet<String> allAvailableRawLangs = new java.util.HashSet<>();
                for (java.util.HashMap<String, Object> m : downloadedModels) {
                    if (!m.containsKey("type") || !m.get("type").toString().contains("Kokoro")) {
                        String raw = m.containsKey("language") && m.get("language") != null ? m.get("language").toString() : "";
                        if (!raw.isEmpty()) allAvailableRawLangs.add(raw);
                    }
                }
                boolean kokoroExists = false;
                for (java.util.HashMap<String, Object> m : downloadedModels) {
                    if (m.containsKey("type") && m.get("type").toString().contains("Kokoro")) {
                        kokoroExists = true; break;
                    }
                }
                if (kokoroExists) {
                    allAvailableRawLangs.addAll(KokoroVoiceHelper.getAvailableLanguages());
                }

                if (matchedRawLang.isEmpty()) {
                    for (String raw : allAvailableRawLangs) {
                        Locale loc = TtsLocaleHelper.getLocaleFromName(raw);
                        if (loc != null) {
                            try {
                                if (loc.getISO3Language().equalsIgnoreCase(reqIsoLang) || loc.getLanguage().equalsIgnoreCase(reqIsoLang)) {
                                    matchedRawLang = raw; 
                                    break;
                                }
                            } catch (Exception ignored){}
                        }
                    }
                }

                String sysDataJson = sp5.getString("sys_tts_" + matchedRawLang, "");
                
                if (!sysDataJson.isEmpty()) {
                    try {
                        org.json.JSONObject sysJson = new org.json.JSONObject(sysDataJson);
                        targetModelType = sysJson.optString("model_type", "");
                        targetOnnx = sysJson.optString("onnx_path", "");
                        targetTokens = sysJson.optString("tokens_path", "");
                        targetVoicesBin = sysJson.optString("voices_bin_path", "");
                        String speakerStr = sysJson.optString("speaker_id", "-1");
                        targetSpeakerId = Integer.parseInt(speakerStr);
                        
                        if (!targetOnnx.isEmpty() && !targetTokens.isEmpty()) {
                            voiceFound = true;
                        }
                    } catch (Exception ignored) {}
                }

                if (!voiceFound) {
                    for (java.util.HashMap<String, Object> m : downloadedModels) {
                        String op = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                        String tk = getTokensPath(m);
                        
                        if (!op.isEmpty() && !tk.isEmpty()) {
                            if (m.containsKey("type") && m.get("type").toString().contains("Kokoro")) {
                                String vb = m.containsKey("voices_bin_path") && m.get("voices_bin_path") != null ? m.get("voices_bin_path").toString() : "";
                                if (!vb.isEmpty()) {
                                    targetModelType = "kokoro";
                                    targetOnnx = op;
                                    targetTokens = tk;
                                    targetVoicesBin = vb;
                                    targetSpeakerId = 31; 
                                    voiceFound = true; break;
                                }
                            } else {
                                targetModelType = "vits";
                                targetOnnx = op;
                                targetTokens = tk;
                                voiceFound = true; break;
                            }
                        }
                    }
                }
            }

            if (!voiceFound) {
                callback.error();
                hasError = true;
                return;
            }

            boolean isPunctOn = sp3.getBoolean("smart_punct", false);
            boolean isEmotionOn = sp3.getBoolean("emotion_tags", false);

            int sampleRate = 22050;
            boolean isKokoroTarget = targetModelType.equals("kokoro");

            boolean needHardReset = false;
            if (!_lastLoadedModelType.equals(targetModelType)) {
                needHardReset = true;
            } else if (isKokoroTarget) {
                if (!_lastLoadedKokoroModel.equals(targetOnnx) || _lastLoadedSpeakerId != targetSpeakerId) {
                    needHardReset = true;
                }
            } else {
                if (!_lastLoadedVoiceModel.equals(targetOnnx)) {
                    needHardReset = true;
                }
            }

            if (needHardReset) {
                try { VoiceEngine.getInstance().destroy(); } catch (Throwable ignored) {}
                try { KokoroEngine.getInstance().destroy(); } catch (Throwable ignored) {}
                _lastLoadedModelType = targetModelType;
            }

            if (isKokoroTarget) {
                KokoroEngine engine = KokoroEngine.getInstance();
                
                if (targetOnnx.isEmpty() || targetTokens.isEmpty() || targetVoicesBin.isEmpty()) {
                    callback.error();
                    hasError = true; return;
                }

                engine.setActiveSpeakerId(targetSpeakerId);

                String loadResult = engine.loadModel(this, targetOnnx, targetTokens, targetVoicesBin);
                if (!"Success".equals(loadResult)) {
                    callback.error();
                    hasError = true; return;
                }
                
                _lastLoadedKokoroModel = targetOnnx;
                _lastLoadedSpeakerId = targetSpeakerId;
                sampleRate = engine.getSampleRate();
                if (sampleRate <= 0) sampleRate = 24000;

            } else {
                VoiceEngine engine = VoiceEngine.getInstance();
                
                if (targetOnnx.isEmpty() || targetTokens.isEmpty()) {
                    callback.error();
                    hasError = true; return;
                }

                String loadResult = engine.loadModel(this, targetOnnx, targetTokens);
                if (!"Success".equals(loadResult)) {
                    callback.error();
                    hasError = true; return;
                }
                
                _lastLoadedVoiceModel = targetOnnx;
                sampleRate = engine.getSampleRate();
                if (sampleRate <= 0) sampleRate = 22050;
            }

            List<String> sentences = new ArrayList<>();
            String[] parts = text.split("(?<=[.!?\\n|।])\\s+");
            for (String part : parts) {
                if (!part.trim().isEmpty()) sentences.add(part.trim());
            }
            if (sentences.isEmpty()) sentences.add(text);

            boolean hasAudioStarted = false; 

            for (String sentence : sentences) {
                if (isSynthesisCancelled) break;

                byte[] chunkPcm = null;
                
                if (isPunctOn || isEmotionOn) {
                    if (isKokoroTarget) {
                        chunkPcm = KokoroEngine.getInstance().generateAudioPCM(sentence, engineSpeed, enginePitch);
                    } else {
                        chunkPcm = AudioEmotionHelper.processAndGenerate(sentence, isPunctOn, isEmotionOn, engineSpeed, enginePitch, 1.0f);
                    }
                } else {
                    if (isKokoroTarget) {
                        chunkPcm = KokoroEngine.getInstance().generateAudioPCM(sentence, engineSpeed, enginePitch);
                    } else {
                        chunkPcm = VoiceEngine.getInstance().generateAudioPCM(sentence, engineSpeed, enginePitch);
                    }
                }

                if (isSynthesisCancelled) break;

                if (chunkPcm != null && chunkPcm.length > 0) {
                    if (!hasAudioStarted) {
                        int startResult = callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
                        if (startResult != TextToSpeech.SUCCESS) {
                            callback.error();
                            hasError = true;
                            return;
                        }
                        hasAudioStarted = true;
                    }

                    StreamResult result = _streamAudioChunks(chunkPcm, callback);
                    if (result == StreamResult.PARTIAL_FAILURE) {
                        callback.error();
                        hasError = true;
                        return;
                    }
                    if (result == StreamResult.COMPLETE) {
                        emittedAudio = true;
                    }
                }
            }

            if (!emittedAudio && !isSynthesisCancelled) {
                callback.error();
                hasError = true;
            }

        } catch (Exception e) {
            callback.error();
            hasError = true;
        } finally {
            if (!hasError) {
                callback.done();
            }
        }
    }
    
    private enum StreamResult {
        NO_AUDIO,
        PARTIAL_FAILURE,
        COMPLETE
    }

    private StreamResult _streamAudioChunks(byte[] pcm, SynthesisCallback callback) {
        boolean wroteAny = false;
        try {
            int maxBufferSize = callback.getMaxBufferSize();
            int chunkSize = (maxBufferSize > 0) ? maxBufferSize : 8192; 
            for (int offset = 0; offset < pcm.length; offset += chunkSize) {
                if (isSynthesisCancelled) break;
                
                int end = Math.min(offset + chunkSize, pcm.length);
                int writeStatus = callback.audioAvailable(pcm, offset, end - offset);
                
                if (writeStatus != TextToSpeech.SUCCESS) {
                    return wroteAny ? StreamResult.PARTIAL_FAILURE : StreamResult.NO_AUDIO;
                }
                wroteAny = true;
            }
        } catch (Exception ignored) {
            return wroteAny ? StreamResult.PARTIAL_FAILURE : StreamResult.NO_AUDIO;
        }
        return wroteAny ? StreamResult.COMPLETE : StreamResult.NO_AUDIO;
    }
}
