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

// Model and Helper Imports
import com.CodeBySonu.VoxSherpa.VoiceEngine;
import com.CodeBySonu.VoxSherpa.KokoroEngine;
import com.CodeBySonu.VoxSherpa.system.TtsLocaleHelper;
import com.CodeBySonu.VoxSherpa.KokoroVoiceHelper;
import com.CodeBySonu.VoxSherpa.AudioEmotionHelper;

public class VoxSherpaTtsService extends TextToSpeechService {

    private String _lastLoadedKokoroModel = "";
    private int _lastLoadedSpeakerId      = -1;
    private String _lastLoadedVoiceModel  = "";
    
    // Volatile flag to handle system interruption across different threads safely
    private volatile boolean isSynthesisCancelled = false;

    // Retrieves the dynamic active language from SharedPreferences
    private String getRawActiveLanguage() {
        SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
        String activeModelType = sp.getString("active_model_type", "");
        String rawLanguage = ""; 

        if ("kokoro".equals(activeModelType)) {
            int speakerId = sp.getInt("active_kokoro_speaker", 31);
            KokoroVoiceHelper.VoiceItem voice = KokoroVoiceHelper.getById(speakerId);
            if (voice != null && voice.language != null) {
                rawLanguage = voice.language; 
            }
        } else {
            String saved = sp.getString("active_language", "");
            if (saved != null && !saved.isEmpty()) {
                rawLanguage = saved;
            }
        }
        return rawLanguage;
    }

    @Override
    protected String[] onGetLanguage() {
        return TtsLocaleHelper.getTtsLanguageArray(getRawActiveLanguage());
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        String[] currentActive = TtsLocaleHelper.getTtsLanguageArray(getRawActiveLanguage());
        if (currentActive != null && currentActive[0] != null && currentActive[0].equalsIgnoreCase(lang)) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        String[] currentActive = TtsLocaleHelper.getTtsLanguageArray(getRawActiveLanguage());
        if (currentActive != null && currentActive[0] != null && currentActive[0].equalsIgnoreCase(lang)) {
            return "VoxSherpa_Active";
        }
        return null;
    }

    @Override
    public List<Voice> onGetVoices() {
        List<Voice> voiceList = new ArrayList<>();
        String[] currentActive = TtsLocaleHelper.getTtsLanguageArray(getRawActiveLanguage());
        
        String langCode    = (currentActive[0] != null && !currentActive[0].isEmpty()) ? currentActive[0] : "eng";
        String countryCode = (currentActive[1] != null) ? currentActive[1] : "";

        Locale locale = new Locale(langCode, countryCode);
        Set<String> emptyFeatures = new HashSet<>();
        
        Voice activeVoice = new Voice(
            "VoxSherpa_Active", 
            locale, 
            Voice.QUALITY_VERY_HIGH, 
            Voice.LATENCY_NORMAL, 
            false, 
            emptyFeatures
        );
        
        voiceList.add(activeVoice);
        return voiceList;
    }

    @Override
    public int onIsValidVoiceName(String voiceName) {
        if ("VoxSherpa_Active".equals(voiceName)) {
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
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            VoiceEngine.getInstance().cancel();
            KokoroEngine.getInstance().cancel();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        isSynthesisCancelled = false;
        boolean hasError = false; // The Fix: Track errors to avoid duplicate or conflicting callback triggers
        
        try {
            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            SharedPreferences sp3 = getSharedPreferences("sp3", MODE_PRIVATE);
            
            String modelType = sp.getString("active_model_type", "");
            CharSequence charText = request.getCharSequenceText();
            
            // 1. Handle Empty Text Gracefully
            if (modelType.isEmpty() || charText == null || charText.toString().trim().isEmpty()) {
                // OS Rule: start() MUST be called before done().
                // We call start with default format, then return so the finally block safely calls done().
                callback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);
                return; 
            }
            
            String text = charText.toString().trim();

            boolean isPunctOn = sp3.getBoolean("smart_punct", false);
            boolean isEmotionOn = sp3.getBoolean("emotion_tags", false);

            int systemSpeechRate = request.getSpeechRate();
            int systemPitch      = request.getPitch();

            final float engineSpeed = (systemSpeechRate > 0) ? (systemSpeechRate / 100.0f) : 1.0f;
            final float enginePitch = (systemPitch > 0)      ? (systemPitch / 100.0f)      : 1.0f;

            int sampleRate = 22050;
            boolean isKokoro = modelType.equals("kokoro");

            // 2. Load Required Engine Model
            if (isKokoro) {
                KokoroEngine engine = KokoroEngine.getInstance();
                String onnx      = sp.getString("active_model", "");
                String tokens    = sp.getString("active_tokens", "");
                String voicesBin = sp.getString("active_voices_bin", "");
                int currentSpeakerId = sp.getInt("active_kokoro_speaker", 31);

                boolean needsLoad = !engine.isReady() 
                                    || !_lastLoadedKokoroModel.equals(onnx)
                                    || _lastLoadedSpeakerId != currentSpeakerId;

                if (needsLoad) {
                    if (onnx.isEmpty() || tokens.isEmpty() || voicesBin.isEmpty()) {
                        callback.error();
                        hasError = true;
                        return;
                    }

                    if (engine.isReady() && _lastLoadedKokoroModel.equals(onnx)) {
                        engine.setActiveSpeakerId(currentSpeakerId);
                    } else {
                        String loadResult = engine.loadModel(this, onnx, tokens, voicesBin);
                        if (!"Success".equals(loadResult)) {
                            callback.error();
                            hasError = true;
                            return;
                        }
                        engine.setActiveSpeakerId(currentSpeakerId);
                    }

                    _lastLoadedKokoroModel = onnx;
                    _lastLoadedSpeakerId = currentSpeakerId;
                }

                sampleRate = engine.getSampleRate();
                if (sampleRate <= 0) sampleRate = 24000;

            } else {
                VoiceEngine engine = VoiceEngine.getInstance();
                String onnx   = sp.getString("active_model", "");
                String tokens = sp.getString("active_tokens", "");

                boolean booleanNeedsLoad = !engine.isReady() || !_lastLoadedVoiceModel.equals(onnx);

                if (booleanNeedsLoad) {
                    if (onnx.isEmpty() || tokens.isEmpty()) {
                        callback.error();
                        hasError = true;
                        return;
                    }

                    String loadResult = engine.loadModel(this, onnx, tokens);
                    if (!"Success".equals(loadResult)) {
                        callback.error();
                        hasError = true;
                        return;
                    }

                    _lastLoadedVoiceModel = onnx;
                }

                sampleRate = engine.getSampleRate();
                if (sampleRate <= 0) sampleRate = 22050;
            }

            // 3. Start Audio Stream with the Android System Framework
            int startResult = callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
            if (startResult != TextToSpeech.SUCCESS) {
                callback.error();
                hasError = true;
                return;
            }

            // 4. Sentence Splitting for Memory Efficiency
            List<String> sentences = new ArrayList<>();
            String[] parts = text.split("(?<=[.!?\\n|।])\\s+");
            for (String part : parts) {
                if (!part.trim().isEmpty()) sentences.add(part.trim());
            }
            if (sentences.isEmpty()) sentences.add(text);

            // 5. Generate Audio and Stream to System OS
            for (String sentence : sentences) {
                if (isSynthesisCancelled) break;

                byte[] chunkPcm = null;
                
                if (isPunctOn || isEmotionOn) {
                    chunkPcm = AudioEmotionHelper.processAndGenerate(
                        sentence, isPunctOn, isEmotionOn, engineSpeed, enginePitch, 1.0f
                    );
                } else {
                    if (isKokoro) {
                        chunkPcm = KokoroEngine.getInstance().generateAudioPCM(sentence, engineSpeed, enginePitch);
                    } else {
                        chunkPcm = VoiceEngine.getInstance().generateAudioPCM(sentence, engineSpeed, enginePitch);
                    }
                }

                if (isSynthesisCancelled) break;

                if (chunkPcm != null && chunkPcm.length > 0) {
                    _streamAudioChunks(chunkPcm, callback);
                }
            }

        } catch (Exception e) {
            callback.error();
            hasError = true;
        } finally {
            // The Fix: Guarantees done() is only called if error() was NOT called.
            if (!hasError) {
                callback.done();
            }
        }
    }

    // Dynamic Chunk Streaming logic mapped to OS Buffer Limits
    private void _streamAudioChunks(byte[] pcm, SynthesisCallback callback) {
        try {
            int maxBufferSize = callback.getMaxBufferSize();
            int chunkSize = (maxBufferSize > 0) ? maxBufferSize : 8192; 

            for (int offset = 0; offset < pcm.length; offset += chunkSize) {
                if (isSynthesisCancelled) {
                    break;
                }
                
                int end = Math.min(offset + chunkSize, pcm.length);
                int writeStatus = callback.audioAvailable(pcm, offset, end - offset);
                
                if (writeStatus != TextToSpeech.SUCCESS) {
                    break; 
                }
            }
        } catch (Exception ignored) {
        }
    }
}
