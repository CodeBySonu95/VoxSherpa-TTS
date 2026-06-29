package com.CodeBySonu.VoxSherpa;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import org.junit.Test;

public class MmsCatalogTest {

    // ── URL construction ──────────────────────────────────────────────────

    @Test
    public void buildModelUrl_constructsCorrectPath() {
        assertEquals(
            "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/eng/model.onnx",
            MmsCatalog.buildModelUrl("eng")
        );
    }

    @Test
    public void buildModelUrl_handlesScriptVariants() {
        assertEquals(
            "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/azj-script_cyrillic/model.onnx",
            MmsCatalog.buildModelUrl("azj-script_cyrillic")
        );
    }

    @Test
    public void buildTokensUrl_constructsCorrectPath() {
        assertEquals(
            "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/abi/tokens.txt",
            MmsCatalog.buildTokensUrl("abi")
        );
    }

    @Test
    public void buildSampleUrl_constructsCorrectPath() {
        assertEquals(
            "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/eng/sample.wav",
            MmsCatalog.buildSampleUrl("eng")
        );
    }

    // ── parseEntries ──────────────────────────────────────────────────────

    @Test
    public void parseEntries_returnsEmptyForNullJson() {
        assertTrue(MmsCatalog.parseEntries(null).isEmpty());
    }

    @Test
    public void parseEntries_returnsEmptyForEmptyJson() {
        assertTrue(MmsCatalog.parseEntries("").isEmpty());
        assertTrue(MmsCatalog.parseEntries("  ").isEmpty());
    }

    @Test
    public void parseEntries_returnsEmptyForMalformedJson() {
        assertTrue(MmsCatalog.parseEntries("{not valid json}").isEmpty());
    }

    @Test
    public void parseEntries_includesEntryWithOnnxExists() {
        String json = "[{\"Iso Code\":\"eng\",\"Language Name\":\"English\","
            + "\"ONNX Exists\":true,\"Sample Exists\":true}]";

        List<HashMap<String, Object>> entries = MmsCatalog.parseEntries(json);
        assertEquals(1, entries.size());

        HashMap<String, Object> entry = entries.get(0);
        assertEquals("English (MMS)", entry.get("name"));
        assertEquals("MMS", entry.get("type"));
        assertEquals("English", entry.get("language"));
        assertEquals("Single", entry.get("gender"));
        assertEquals("MMS", entry.get("quality"));
    }

    @Test
    public void parseEntries_excludesEntryWithoutOnnx() {
        String json = "[{\"Iso Code\":\"amh\",\"Language Name\":\"Amharic\","
            + "\"ONNX Exists\":false,\"Sample Exists\":false}]";

        assertTrue(MmsCatalog.parseEntries(json).isEmpty());
    }

    @Test
    public void parseEntries_mixesIncludedAndExcluded() {
        String json = "["
            + "{\"Iso Code\":\"eng\",\"Language Name\":\"English\",\"ONNX Exists\":true,\"Sample Exists\":true},"
            + "{\"Iso Code\":\"amh\",\"Language Name\":\"Amharic\",\"ONNX Exists\":false,\"Sample Exists\":false},"
            + "{\"Iso Code\":\"abi\",\"Language Name\":\"Abidji\",\"ONNX Exists\":true,\"Sample Exists\":false}"
            + "]";

        List<HashMap<String, Object>> entries = MmsCatalog.parseEntries(json);
        assertEquals(2, entries.size());
    }

    @Test
    public void parseEntries_buildsModelAndTokensUrls() {
        String json = "[{\"Iso Code\":\"abi\",\"Language Name\":\"Abidji\","
            + "\"ONNX Exists\":true,\"Sample Exists\":true}]";

        HashMap<String, Object> entry = MmsCatalog.parseEntries(json).get(0);
        assertEquals(MmsCatalog.buildModelUrl("abi"), entry.get("model_url"));
        assertEquals(MmsCatalog.buildTokensUrl("abi"), entry.get("tokens_url"));
    }

    @Test
    public void parseEntries_addsSempleWhenSampleExists() {
        String json = "[{\"Iso Code\":\"eng\",\"Language Name\":\"English\","
            + "\"ONNX Exists\":true,\"Sample Exists\":true}]";

        HashMap<String, Object> entry = MmsCatalog.parseEntries(json).get(0);
        assertTrue(entry.containsKey("semple"));
        assertEquals(MmsCatalog.buildSampleUrl("eng"), entry.get("semple"));
    }

    @Test
    public void parseEntries_omitsSempleWhenSampleMissing() {
        String json = "[{\"Iso Code\":\"ace\",\"Language Name\":\"Aceh\","
            + "\"ONNX Exists\":true,\"Sample Exists\":false}]";

        HashMap<String, Object> entry = MmsCatalog.parseEntries(json).get(0);
        assertFalse(entry.containsKey("semple"));
    }

    @Test
    public void parseEntries_setsDownloadDefaults() {
        String json = "[{\"Iso Code\":\"eng\",\"Language Name\":\"English\","
            + "\"ONNX Exists\":true,\"Sample Exists\":true}]";

        HashMap<String, Object> entry = MmsCatalog.parseEntries(json).get(0);
        assertEquals("false", entry.get("is_downloading"));
        assertEquals("0", entry.get("download_progress"));
    }

    @Test
    public void parseEntries_fallsBackToIsoCodeForMissingLanguageName() {
        String json = "[{\"Iso Code\":\"xyz\",\"ONNX Exists\":true,\"Sample Exists\":false}]";

        HashMap<String, Object> entry = MmsCatalog.parseEntries(json).get(0);
        assertEquals("xyz (MMS)", entry.get("name"));
        assertEquals("xyz", entry.get("language"));
    }

    @Test
    public void parseEntries_handlesScriptVariantIsoCodes() {
        String json = "[{\"Iso Code\":\"azj-script_latin\",\"Language Name\":\"Azerbaijani, North\","
            + "\"ONNX Exists\":true,\"Sample Exists\":true}]";

        HashMap<String, Object> entry = MmsCatalog.parseEntries(json).get(0);
        assertEquals(
            "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/azj-script_latin/model.onnx",
            entry.get("model_url")
        );
    }
}
