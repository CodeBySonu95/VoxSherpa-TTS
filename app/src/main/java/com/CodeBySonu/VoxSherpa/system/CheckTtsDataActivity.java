package com.CodeBySonu.VoxSherpa.system;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.content.SharedPreferences;
import java.util.ArrayList;
import com.CodeBySonu.VoxSherpa.VoiceEngine;
import com.CodeBySonu.VoxSherpa.KokoroEngine;
import com.CodeBySonu.VoxSherpa.KokoroVoiceHelper;

public class CheckTtsDataActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<String> availableVoices = new ArrayList<>();
        ArrayList<String> unavailableVoices = new ArrayList<>();

        try {
            // Retrieve the currently active language from SharedPreferences
            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            String activeModelType = sp.getString("active_model_type", "");
            String rawLanguage = ""; 

            // Read specific language based on the active model type
            if ("kokoro".equals(activeModelType)) {
                int speakerId = sp.getInt("active_kokoro_speaker", 31);
                KokoroVoiceHelper.VoiceItem voice = KokoroVoiceHelper.getById(speakerId);
                if (voice != null && voice.language != null && !voice.language.isEmpty()) {
                    rawLanguage = voice.language; 
                }
            } else {
                rawLanguage = sp.getString("active_language", "");
            }

            // Convert to ISO-3 format using the helper (e.g., "hin", "IND")
            String[] isoLang = TtsLocaleHelper.getTtsLanguageArray(rawLanguage);
            
            // Format for Android System: "language-country" (e.g., "hin-IND" or "eng-USA")
            if (isoLang != null && isoLang[0] != null && !isoLang[0].isEmpty()) {
                String localeString = isoLang[0];
                if (isoLang[1] != null && !isoLang[1].isEmpty()) {
                    localeString += "-" + isoLang[1];
                }
                availableVoices.add(localeString);
            }

            // Send data back to the Android Settings UI
            Intent returnData = new Intent();
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableVoices);

            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnData);

        } catch (Throwable t) {
            // Safe fallback in case of an unexpected error, returning empty lists to prevent settings crash
            Intent fallback = new Intent();
            fallback.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
            fallback.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableVoices);
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, fallback);
        }

        finish();
    }
}
