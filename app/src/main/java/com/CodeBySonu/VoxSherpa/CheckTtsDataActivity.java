package com.CodeBySonu.VoxSherpa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.content.SharedPreferences;
import java.util.ArrayList;

public class CheckTtsDataActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        String[] isoLang = TtsLocaleHelper.getTtsLanguageArray(rawLanguage);
        
        String localeString = isoLang[0];
        if (isoLang[1] != null && !isoLang[1].isEmpty()) {
            localeString += "-" + isoLang[1];
        }

        ArrayList<String> availableVoices = new ArrayList<>();
        availableVoices.add(localeString);

        Intent returnData = new Intent();
        returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
        returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, new ArrayList<String>());

        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnData);
        finish();
    }
}
