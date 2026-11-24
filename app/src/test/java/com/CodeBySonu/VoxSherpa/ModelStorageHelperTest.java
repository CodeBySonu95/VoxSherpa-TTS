package com.CodeBySonu.VoxSherpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Verifies multi-voice download, installation, persistence under filters,
 * deletion, and re-download flows in {@link ModelStorageHelper}.
 */
public class ModelStorageHelperTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private HashMap<String, Object> catalogModel(String name, String language, String url, String type) {
        HashMap<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("language", language);
        m.put("gender", "Female");
        m.put("size", "60 MB");
        m.put("type", type);
        m.put("model_url", url);
        m.put("tokens_url", url.replace("model.onnx", "tokens.txt"));
        m.put("is_downloading", "false");
        m.put("download_progress", "0");
        return m;
    }

    @Test
    public void evaluateDownloadBatch_waitsForAllFilesBeforeReportingSuccess() {
        // ONNX done, tokens still running -> must stay RUNNING (prevents missing tokens.txt)
        assertEquals(
            ModelStorageHelper.BatchDownloadState.RUNNING,
            ModelStorageHelper.evaluateDownloadBatch(
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_RUNNING,
                null
            )
        );

        // Kokoro: ONNX and tokens done, voices.bin still pending -> must stay RUNNING
        assertEquals(
            ModelStorageHelper.BatchDownloadState.RUNNING,
            ModelStorageHelper.evaluateDownloadBatch(
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_PENDING
            )
        );

        // Any sibling failure -> FAILED
        assertEquals(
            ModelStorageHelper.BatchDownloadState.FAILED,
            ModelStorageHelper.evaluateDownloadBatch(
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_FAILED,
                null
            )
        );
        assertEquals(
            ModelStorageHelper.BatchDownloadState.FAILED,
            ModelStorageHelper.evaluateDownloadBatch(
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_FAILED
            )
        );

        // All required files succeeded -> SUCCESSFUL
        assertEquals(
            ModelStorageHelper.BatchDownloadState.SUCCESSFUL,
            ModelStorageHelper.evaluateDownloadBatch(
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_SUCCESSFUL,
                null
            )
        );
        assertEquals(
            ModelStorageHelper.BatchDownloadState.SUCCESSFUL,
            ModelStorageHelper.evaluateDownloadBatch(
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_SUCCESSFUL,
                ModelStorageHelper.STATUS_SUCCESSFUL
            )
        );
    }

    @Test
    public void installDownloadedFiles_installsFirstAndSecondVoiceIndependently() throws Exception {
        File downloadsDir = temp.newFolder("downloads");
        File secureDir = temp.newFolder("secure_models");

        // Voice 1 (English Piper)
        Files.write(new File(downloadsDir, "model_1.onnx").toPath(), new byte[]{1, 2, 3});
        Files.write(new File(downloadsDir, "tokens_1.txt").toPath(), "a 1\n".getBytes());

        ModelStorageHelper.InstalledModelFiles first = ModelStorageHelper.installDownloadedFiles(
            downloadsDir, secureDir, "model_1.onnx", "tokens_1.txt", "", false
        );
        assertTrue(first.onnxFile.exists() && first.onnxFile.length() == 3);
        assertTrue(first.tokensFile.exists() && first.tokensFile.length() > 0);
        assertNull(first.voicesBinFile);
        assertFalse(new File(downloadsDir, "model_1.onnx").exists());
        assertFalse(new File(downloadsDir, "tokens_1.txt").exists());

        // Voice 2 (German Piper)
        Files.write(new File(downloadsDir, "model_2.onnx").toPath(), new byte[]{4, 5, 6, 7});
        Files.write(new File(downloadsDir, "tokens_2.txt").toPath(), "b 2\n".getBytes());

        ModelStorageHelper.InstalledModelFiles second = ModelStorageHelper.installDownloadedFiles(
            downloadsDir, secureDir, "model_2.onnx", "tokens_2.txt", "", false
        );
        assertTrue(second.onnxFile.exists() && second.onnxFile.length() == 4);
        assertTrue(second.tokensFile.exists() && second.tokensFile.length() > 0);
        // First voice files remain intact
        assertTrue(first.onnxFile.exists());
        assertTrue(first.tokensFile.exists());
    }

    @Test
    public void installDownloadedFiles_rejectsMissingTokensAndCleansUpPartialDest() throws Exception {
        File downloadsDir = temp.newFolder("downloads");
        File secureDir = temp.newFolder("secure_models");

        Files.write(new File(downloadsDir, "model_2.onnx").toPath(), new byte[]{1, 2, 3});
        // tokens_2.txt intentionally missing (simulating incomplete download)

        try {
            ModelStorageHelper.installDownloadedFiles(
                downloadsDir, secureDir, "model_2.onnx", "tokens_2.txt", "", false
            );
            fail("Expected IOException when tokens file is missing");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("tokens"));
        }

        assertFalse(new File(secureDir, "model_2.onnx").exists());
        assertFalse(new File(secureDir, "tokens_2.txt").exists());
    }

    @Test
    public void mergeDownloadedModel_preservesOtherVoicesEvenWhenUiWasFiltered() {
        List<HashMap<String, Object>> master = new ArrayList<>();
        HashMap<String, Object> english = catalogModel("English Amy", "English", "https://example.com/en/model.onnx", "Piper");
        HashMap<String, Object> german = catalogModel("German Thorsten", "German", "https://example.com/de/model.onnx", "Piper");
        HashMap<String, Object> french = catalogModel("French Siwis", "French", "https://example.com/fr/model.onnx", "Piper");
        master.add(english);
        master.add(german);
        master.add(french);

        // Download Voice 1 (English)
        HashMap<String, Object> downloadedEn = new HashMap<>(english);
        downloadedEn.put("onnx_path", "/data/secure_models/model_1.onnx");
        downloadedEn.put("tokens_path", "/data/secure_models/tokens_1.txt");
        downloadedEn.put("onnx_download_id", "101");
        downloadedEn.put("handler_running", "true");
        master = ModelStorageHelper.mergeDownloadedModel(master, downloadedEn);

        // User filters to German only, then downloads Voice 2 (German)
        List<HashMap<String, Object>> filtered = ModelStorageHelper.filterAndSort(master, "all_models", "German");
        assertEquals(1, filtered.size());

        HashMap<String, Object> downloadedDe = new HashMap<>(filtered.get(0));
        downloadedDe.put("onnx_path", "/data/secure_models/model_2.onnx");
        downloadedDe.put("tokens_path", "/data/secure_models/tokens_2.txt");
        downloadedDe.put("onnx_download_id", "102");
        master = ModelStorageHelper.mergeDownloadedModel(master, downloadedDe);

        // All 3 catalog entries must remain, with BOTH English and German installed
        assertEquals(3, master.size());
        List<HashMap<String, Object>> installed = ModelStorageHelper.filterAndSort(master, "installed", "All Languages");
        assertEquals(2, installed.size());
        assertEquals("/data/secure_models/model_1.onnx", master.get(0).get("onnx_path"));
        assertEquals("/data/secure_models/model_2.onnx", master.get(1).get("onnx_path"));
        assertFalse(master.get(0).containsKey("handler_running"));
        assertFalse(master.get(0).containsKey("onnx_download_id"));
        assertFalse(master.get(1).containsKey("onnx_download_id"));
    }

    @Test
    public void deleteVoiceAndDownloadNewVoice_clearsStaleStateAndPreservesCatalog() {
        List<HashMap<String, Object>> master = new ArrayList<>();
        HashMap<String, Object> voice1 = catalogModel("English Amy", "English", "https://example.com/en/model.onnx", "Piper");
        voice1.put("onnx_path", "/data/secure_models/model_1.onnx");
        voice1.put("tokens_path", "/data/secure_models/tokens_1.txt");
        voice1.put("onnx_download_id", "101");
        voice1.put("tokens_download_id", "102");
        voice1.put("target_onnx_name", "model_1.onnx");
        voice1.put("download_progress", "100");
        voice1.put("handler_running", "true");

        HashMap<String, Object> voice2 = catalogModel("Spanish Dave", "Spanish", "https://example.com/es/model.onnx", "Piper");
        master.add(voice1);
        master.add(voice2);

        // Delete Voice 1
        master = ModelStorageHelper.removeDownloadedModel(master, voice1);

        assertEquals(2, master.size());
        HashMap<String, Object> resetVoice1 = master.get(0);
        assertFalse(resetVoice1.containsKey("onnx_path"));
        assertFalse(resetVoice1.containsKey("tokens_path"));
        assertFalse(resetVoice1.containsKey("onnx_download_id"));
        assertFalse(resetVoice1.containsKey("tokens_download_id"));
        assertFalse(resetVoice1.containsKey("target_onnx_name"));
        assertFalse(resetVoice1.containsKey("handler_running"));
        assertEquals("false", resetVoice1.get("is_downloading"));
        assertEquals("0", resetVoice1.get("download_progress"));

        // Now download Voice 2
        HashMap<String, Object> downloadedVoice2 = new HashMap<>(master.get(1));
        downloadedVoice2.put("onnx_path", "/data/secure_models/model_2.onnx");
        downloadedVoice2.put("tokens_path", "/data/secure_models/tokens_2.txt");
        master = ModelStorageHelper.mergeDownloadedModel(master, downloadedVoice2);

        List<HashMap<String, Object>> installed = ModelStorageHelper.filterAndSort(master, "installed", "All Languages");
        assertEquals(1, installed.size());
        assertEquals("Spanish Dave", installed.get(0).get("name"));
        assertNotNull(installed.get(0).get("onnx_path"));
    }

    @Test
    public void removeDownloadedModel_matchesByUrlWhenNamesAreMissing() {
        List<HashMap<String, Object>> master = new ArrayList<>();
        HashMap<String, Object> unnamed1 = catalogModel("", "English", "https://example.com/v1/model.onnx", "Piper");
        unnamed1.put("onnx_path", "/data/secure_models/v1.onnx");
        HashMap<String, Object> unnamed2 = catalogModel("", "German", "https://example.com/v2/model.onnx", "Piper");
        unnamed2.put("onnx_path", "/data/secure_models/v2.onnx");
        master.add(unnamed1);
        master.add(unnamed2);

        // Deleting unnamed2 must NOT touch unnamed1
        master = ModelStorageHelper.removeDownloadedModel(master, unnamed2);

        assertEquals("/data/secure_models/v1.onnx", master.get(0).get("onnx_path"));
        assertFalse(master.get(1).containsKey("onnx_path"));
    }
}
