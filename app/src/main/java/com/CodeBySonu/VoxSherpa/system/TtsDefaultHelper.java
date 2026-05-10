package com.CodeBySonu.VoxSherpa.system;

import android.content.Context;
import android.content.SharedPreferences;

public class TtsDefaultHelper {

    // Synchronizes downloaded models with the OS default TTS settings (sp5) quietly in the background
    public static void syncDefaultVoices(Context context) {
        if (context == null) return;

        SharedPreferences sp1 = context.getSharedPreferences("sp1", Context.MODE_PRIVATE);
        SharedPreferences sp5 = context.getSharedPreferences("sp5", Context.MODE_PRIVATE);

        String allData = sp1.getString("models_data", "[]");
        if (allData.equals("[]")) return;

        try {
            java.util.ArrayList<java.util.HashMap<String, Object>> downloadedModels = 
                new com.google.gson.Gson().fromJson(allData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());

            if (downloadedModels == null || downloadedModels.isEmpty()) return;

            boolean isKokoroDownloaded = false;
            String globalKokoroOnnx = "";
            String globalKokoroTokens = "";
            String globalKokoroVoices = "";
            
            java.util.HashMap<String, java.util.HashMap<String, Object>> firstVitsPerLang = new java.util.HashMap<>();

            // Fast loop to extract core paths without any UI overhead
            for (java.util.HashMap<String, Object> m : downloadedModels) {
                String onnxPath = m.containsKey("onnx_path") && m.get("onnx_path") != null ? m.get("onnx_path").toString() : "";
                if (!onnxPath.isEmpty()) {
                    boolean isKokoroType = m.containsKey("type") && m.get("type").toString().contains("Kokoro");
                    if (isKokoroType) {
                        isKokoroDownloaded = true;
                        globalKokoroOnnx = onnxPath;
                        globalKokoroTokens = m.containsKey("tokens_path") && m.get("tokens_path") != null ? m.get("tokens_path").toString() : "";
                        globalKokoroVoices = m.containsKey("voices_bin_path") && m.get("voices_bin_path") != null ? m.get("voices_bin_path").toString() : "";
                    } else {
                        String lang = m.containsKey("language") && m.get("language") != null ? m.get("language").toString() : "";
                        // Capture only the very first VITS model for each language as fallback
                        if (!lang.isEmpty() && !firstVitsPerLang.containsKey(lang)) {
                            firstVitsPerLang.put(lang, m);
                        }
                    }
                }
            }

            SharedPreferences.Editor sp5Editor = sp5.edit();
            boolean dataChanged = false;

            // Process Kokoro languages first
            if (isKokoroDownloaded) {
                java.util.List<String> kokoroLangs = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAvailableLanguages();
                java.util.List<com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem> allKVoices = com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.getAllVoices();

                for (String lang : kokoroLangs) {
                    String existingDefault = sp5.getString("sys_tts_" + lang, "");
                    if (existingDefault.isEmpty()) {
                        // Find the first Kokoro speaker ID for this missing language
                        for (com.CodeBySonu.VoxSherpa.KokoroVoiceHelper.VoiceItem kv : allKVoices) {
                            if (kv.language.equals(lang)) {
                                org.json.JSONObject sysJson = new org.json.JSONObject();
                                sysJson.put("model_type", "kokoro");
                                sysJson.put("onnx_path", globalKokoroOnnx);
                                sysJson.put("tokens_path", globalKokoroTokens);
                                sysJson.put("voices_bin_path", globalKokoroVoices);
                                sysJson.put("speaker_id", String.valueOf(kv.speakerId));
                                
                                sp5Editor.putString("sys_tts_" + lang, sysJson.toString());
                                dataChanged = true;
                                break; 
                            }
                        }
                    }
                }
            }

            // Process VITS languages
            for (String lang : firstVitsPerLang.keySet()) {
                String existingDefault = sp5.getString("sys_tts_" + lang, "");
                if (existingDefault.isEmpty()) {
                    java.util.HashMap<String, Object> vits = firstVitsPerLang.get(lang);
                    String tk = "";
                    if (vits.containsKey("tokens_path") && vits.get("tokens_path") != null) tk = vits.get("tokens_path").toString();
                    else if (vits.containsKey("lexicon_path") && vits.get("lexicon_path") != null) tk = vits.get("lexicon_path").toString();
                    else if (vits.containsKey("config_path") && vits.get("config_path") != null) tk = vits.get("config_path").toString();

                    org.json.JSONObject sysJson = new org.json.JSONObject();
                    sysJson.put("model_type", "vits");
                    sysJson.put("onnx_path", vits.containsKey("onnx_path") ? vits.get("onnx_path").toString() : "");
                    sysJson.put("tokens_path", tk);
                    sysJson.put("voices_bin_path", "");
                    sysJson.put("speaker_id", "-1");
                    
                    sp5Editor.putString("sys_tts_" + lang, sysJson.toString());
                    dataChanged = true;
                }
            }

            // Commit all changes in one batch to save CPU cycles
            if (dataChanged) {
                sp5Editor.apply();
            }

        } catch (Exception ignored) {
            // Failsafe to prevent any app crashes during bootup or download callbacks
        }
    }
}
