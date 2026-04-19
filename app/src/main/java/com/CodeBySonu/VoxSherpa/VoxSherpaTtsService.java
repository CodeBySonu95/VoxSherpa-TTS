package com.CodeBySonu.VoxSherpa;

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

import com.CodeBySonu.VoxSherpa.TtsLocaleHelper;
import com.CodeBySonu.VoxSherpa.KokoroVoiceHelper;

public class VoxSherpaTtsService extends TextToSpeechService {

    private String _lastLoadedKokoroModel = "";
    private int _lastLoadedSpeakerId      = -1;
    private String _lastLoadedVoiceModel  = "";
    private String getRawActiveLanguage() {
        SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
        String activeModelType = sp.getString("active_model_type", "");
        String rawLanguage = "English"; 

        if ("kokoro".equals(activeModelType)) {
            int speakerId = sp.getInt("active_kokoro_speaker", 31);
            KokoroVoiceHelper.VoiceItem voice = KokoroVoiceHelper.getById(speakerId);
            if (voice != null) {
                rawLanguage = voice.language; 
            }
        } else {
            rawLanguage = sp.getString("active_language", "English");
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
        if (currentActive[0].equalsIgnoreCase(lang)) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    public List<Voice> onGetVoices() {
        List<Voice> voiceList = new ArrayList<>();
        String[] currentActive = TtsLocaleHelper.getTtsLanguageArray(getRawActiveLanguage());
        
        Locale locale = new Locale(currentActive[0], currentActive[1]);
        Voice activeVoice = new Voice(
            "VoxSherpa_Active", 
            locale, 
            Voice.QUALITY_VERY_HIGH, 
            Voice.LATENCY_NORMAL, 
            false, 
            null
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
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        try {
            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            String modelType = sp.getString("active_model_type", "");

            if (modelType.isEmpty()) {
                _sendEmptyAudio(callback, 22050);
                return;
            }

            CharSequence charText = request.getCharSequenceText();
            if (charText == null) {
                _sendEmptyAudio(callback, 22050);
                return;
            }
            
            String text = charText.toString().trim();
            if (text.isEmpty()) {
                _sendEmptyAudio(callback, 22050);
                return;
            }

            Thread synthThread = new Thread(() -> {
                try {
                    byte[] pcm;
                    int sampleRate;

                    if (modelType.equals("kokoro")) {
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
                                _sendEmptyAudio(callback, 24000);
                                return;
                            }

                            if (engine.isReady() && _lastLoadedKokoroModel.equals(onnx)) {
                                engine.setActiveSpeakerId(currentSpeakerId);
                            } else {
                                String loadResult = engine.loadModel(VoxSherpaTtsService.this, onnx, tokens, voicesBin);
                                if (!"Success".equals(loadResult)) {
                                    _sendEmptyAudio(callback, 24000);
                                    return;
                                }
                                engine.setActiveSpeakerId(currentSpeakerId);
                            }

                            _lastLoadedKokoroModel = onnx;
                            _lastLoadedSpeakerId = currentSpeakerId;
                        }

                        pcm        = engine.generateAudioPCM(text, 1.0f, 1.0f);
                        sampleRate = engine.getSampleRate();
                        if (sampleRate <= 0) sampleRate = 24000;

                    } else {
                        VoiceEngine engine = VoiceEngine.getInstance();
                        String onnx   = sp.getString("active_model", "");
                        String tokens = sp.getString("active_tokens", "");

                        boolean needsLoad = !engine.isReady() || !_lastLoadedVoiceModel.equals(onnx);

                        if (needsLoad) {
                            if (onnx.isEmpty() || tokens.isEmpty()) {
                                _sendEmptyAudio(callback, 22050);
                                return;
                            }

                            String loadResult = engine.loadModel(VoxSherpaTtsService.this, onnx, tokens);
                            if (!"Success".equals(loadResult)) {
                                _sendEmptyAudio(callback, 22050);
                                return;
                            }

                            _lastLoadedVoiceModel = onnx;
                        }

                        pcm        = engine.generateAudioPCM(text, 1.0f, 1.0f);
                        sampleRate = engine.getSampleRate();
                        if (sampleRate <= 0) sampleRate = 22050;
                    }

                    if (pcm == null || pcm.length == 0) {
                        _sendEmptyAudio(callback, sampleRate > 0 ? sampleRate : 22050);
                        return;
                    }

                    _streamAudioChunks(pcm, sampleRate, callback);

                } catch (Throwable t) {
                    _sendEmptyAudio(callback, 22050);
                }
            });

            synthThread.setName("VoxSherpa-Synth-Prod");
            synthThread.setPriority(Thread.MAX_PRIORITY);
            synthThread.start();

        } catch (Throwable t) {
            _sendEmptyAudio(callback, 22050);
        }
    }

    private void _sendEmptyAudio(SynthesisCallback callback, int sampleRate) {
        try {
            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
            callback.done();
        } catch (Throwable ignored) {
        }
    }

    private void _streamAudioChunks(byte[] pcm, int sampleRate, SynthesisCallback callback) {
        try {
            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
            int chunkSize = 8192;
            for (int offset = 0; offset < pcm.length; offset += chunkSize) {
                int end = Math.min(offset + chunkSize, pcm.length);
                callback.audioAvailable(pcm, offset, end - offset);
            }
            callback.done();
        } catch (Throwable ignored) {
        }
    }
}
