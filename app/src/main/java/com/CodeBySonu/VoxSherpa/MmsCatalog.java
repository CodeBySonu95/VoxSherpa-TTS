package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MmsCatalog {

    private static final String BASE_URL =
        "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main";

    // ── URL construction ──────────────────────────────────────────────────────
    public static String buildModelUrl(String isoCode) {
        return BASE_URL + "/" + isoCode + "/model.onnx";
    }

    public static String buildTokensUrl(String isoCode) {
        return BASE_URL + "/" + isoCode + "/tokens.txt";
    }

    public static String buildSampleUrl(String isoCode) {
        return BASE_URL + "/" + isoCode + "/sample.wav";
    }

    // ── Parse languages-supported.json into model entries ─────────────────────
    //
    // Returns a list of HashMap entries matching the app's model schema:
    //   name, type("MMS"), language, gender, size, quality,
    //   model_url, tokens_url, semple (when available)
    //
    // Only entries where "ONNX Exists" is true are included.
    @SuppressWarnings("unchecked")
    public static List<HashMap<String, Object>> parseEntries(String json) {
        List<HashMap<String, Object>> entries = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return entries;

        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : array) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                boolean onnxExists = obj.has("ONNX Exists") && obj.get("ONNX Exists").getAsBoolean();
                if (!onnxExists) continue;

                String isoCode = obj.has("Iso Code") ? obj.get("Iso Code").getAsString() : "";
                String langName = obj.has("Language Name") ? obj.get("Language Name").getAsString() : isoCode;
                boolean sampleExists = obj.has("Sample Exists") && obj.get("Sample Exists").getAsBoolean();

                HashMap<String, Object> entry = new HashMap<>();
                entry.put("name", langName + " (MMS)");
                entry.put("type", "MMS");
                entry.put("language", langName);
                entry.put("gender", "Single");
                entry.put("size", "~63 MB");
                entry.put("quality", "MMS");
                entry.put("model_url", buildModelUrl(isoCode));
                entry.put("tokens_url", buildTokensUrl(isoCode));
                if (sampleExists) {
                    entry.put("semple", buildSampleUrl(isoCode));
                }
                entry.put("is_downloading", "false");
                entry.put("download_progress", "0");

                entries.add(entry);
            }
        } catch (Exception ignored) {}

        return entries;
    }

    // ── Load from assets ──────────────────────────────────────────────────────
    public static List<HashMap<String, Object>> loadModelEntries(Context context) {
        if (context == null) return new ArrayList<>();

        try (InputStream is = context.getAssets().open("languages-supported.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[32768];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return parseEntries(bos.toString("UTF-8"));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
