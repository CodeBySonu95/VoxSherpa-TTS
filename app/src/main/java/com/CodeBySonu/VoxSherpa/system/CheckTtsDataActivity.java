package com.CodeBySonu.VoxSherpa.system;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.CodeBySonu.VoxSherpa.KokoroVoiceHelper;

public class CheckTtsDataActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<String> availableVoices = new ArrayList<>();
        ArrayList<String> unavailableVoices = new ArrayList<>();

        try {
            SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
            String allData = sp.getString("models_data", "[]");
            java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
                new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

            Set<String> uniqueLocales = new HashSet<>();

            if (downloadedModels != null) {
                boolean isKokoroDownloaded = false;
                for (java.util.HashMap<String, Object> m : downloadedModels) {
                    String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                    if (!onnxPath.isEmpty()) {
                        boolean isKokoroType = m.containsKey("type") && m.get("type").toString().contains("Kokoro");
                        if (isKokoroType) {
                            isKokoroDownloaded = true;
                        } else {
                            String rawLanguage = m.containsKey("language") ? m.get("language").toString() : "";
                            if (!rawLanguage.isEmpty()) {
                                String[] isoLang = TtsLocaleHelper.getTtsLanguageArray(rawLanguage);
                                if (isoLang != null && isoLang[0] != null && !isoLang[0].isEmpty()) {
                                    uniqueLocales.add(isoLang[0]); // ONLY LANGUAGE CODE
                                }
                            }
                        }
                    }
                }

                if (isKokoroDownloaded) {
                    java.util.List<String> kokoroLangs = KokoroVoiceHelper.getAvailableLanguages();
                    for (String lang : kokoroLangs) {
                        String[] isoLang = TtsLocaleHelper.getTtsLanguageArray(lang);
                        if (isoLang != null && isoLang[0] != null && !isoLang[0].isEmpty()) {
                            uniqueLocales.add(isoLang[0]); // ONLY LANGUAGE CODE
                        }
                    }
                }
            }

            availableVoices.addAll(uniqueLocales);

            Intent returnData = new Intent();
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableVoices);

            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnData);

        } catch (Throwable t) {
            Intent fallback = new Intent();
            fallback.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
            fallback.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableVoices);
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, fallback);
        }

        finish();
    }
}
