package com.CodeBySonu.VoxSherpa;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Pure-Java helper for model catalog persistence, filtering, deletion, and
 * multi-file download verification/installation. Keeping this logic independent
 * of Android UI classes allows direct JVM unit testing of multi-voice flows.
 */
public final class ModelStorageHelper {

    public enum BatchDownloadState {
        RUNNING,
        SUCCESSFUL,
        FAILED
    }

    public static final class InstalledModelFiles {
        public final File onnxFile;
        public final File tokensFile;
        public final File voicesBinFile;

        public InstalledModelFiles(File onnxFile, File tokensFile, File voicesBinFile) {
            this.onnxFile = onnxFile;
            this.tokensFile = tokensFile;
            this.voicesBinFile = voicesBinFile;
        }
    }

    // Mirrors android.app.DownloadManager status constants so JVM tests don't need Android stubs.
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_RUNNING = 2;
    public static final int STATUS_PAUSED = 4;
    public static final int STATUS_SUCCESSFUL = 8;
    public static final int STATUS_FAILED = 16;

    private static final String[] TRANSIENT_UI_KEYS = {
        "handler_running",
        "is_playing",
        "is_buffering"
    };

    private static final String[] DOWNLOAD_STATE_KEYS = {
        "onnx_download_id",
        "tokens_download_id",
        "voices_bin_download_id",
        "target_onnx_name",
        "target_tokens_name",
        "target_voices_name",
        "handler_running"
    };

    private ModelStorageHelper() {}

    /**
     * Evaluates the combined status of all files belonging to a single model download
     * (ONNX model, tokens.txt, and optionally voices.bin for Kokoro).
     */
    public static BatchDownloadState evaluateDownloadBatch(int onnxStatus, int tokensStatus, Integer voicesStatus) {
        if (onnxStatus == STATUS_FAILED || tokensStatus == STATUS_FAILED
                || (voicesStatus != null && voicesStatus == STATUS_FAILED)) {
            return BatchDownloadState.FAILED;
        }

        boolean onnxDone = onnxStatus == STATUS_SUCCESSFUL;
        boolean tokensDone = tokensStatus == STATUS_SUCCESSFUL;
        boolean voicesDone = voicesStatus == null || voicesStatus == STATUS_SUCCESSFUL;

        if (onnxDone && tokensDone && voicesDone) {
            return BatchDownloadState.SUCCESSFUL;
        }
        return BatchDownloadState.RUNNING;
    }

    /**
     * Verifies all required downloaded files exist and are non-empty, copies them
     * into {@code destDir}, validates the copies, and removes the temporary sources.
     * On any error, cleans up any partially copied destination files and throws.
     */
    public static InstalledModelFiles installDownloadedFiles(
            File sourceDir,
            File destDir,
            String onnxName,
            String tokensName,
            String voicesName,
            boolean isKokoro) throws IOException {
        if (sourceDir == null || destDir == null) {
            throw new IOException("Storage directories are unavailable.");
        }
        if (isEmpty(onnxName) || isEmpty(tokensName) || (isKokoro && isEmpty(voicesName))) {
            throw new IOException("Downloaded file names are missing.");
        }

        File sourceOnnx = new File(sourceDir, onnxName);
        File sourceTokens = new File(sourceDir, tokensName);
        File sourceVoices = isKokoro ? new File(sourceDir, voicesName) : null;

        if (!isNonEmptyFile(sourceOnnx)) {
            throw new IOException("Downloaded ONNX file is missing or empty.");
        }
        if (!isNonEmptyFile(sourceTokens)) {
            throw new IOException("Downloaded tokens file is missing or empty.");
        }
        if (isKokoro && !isNonEmptyFile(sourceVoices)) {
            throw new IOException("Downloaded voices.bin file is missing or empty.");
        }

        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Failed to create internal models directory.");
        }

        File destOnnx = new File(destDir, onnxName);
        File destTokens = new File(destDir, tokensName);
        File destVoices = isKokoro ? new File(destDir, voicesName) : null;

        try {
            copyFile(sourceOnnx, destOnnx);
            copyFile(sourceTokens, destTokens);
            if (isKokoro) {
                copyFile(sourceVoices, destVoices);
            }

            if (!isNonEmptyFile(destOnnx) || !isNonEmptyFile(destTokens)
                    || (isKokoro && !isNonEmptyFile(destVoices))) {
                throw new IOException("Installed model files failed verification.");
            }
        } catch (IOException e) {
            deleteQuietly(destOnnx);
            deleteQuietly(destTokens);
            deleteQuietly(destVoices);
            throw e;
        }

        deleteQuietly(sourceOnnx);
        deleteQuietly(sourceTokens);
        deleteQuietly(sourceVoices);

        return new InstalledModelFiles(destOnnx, destTokens, destVoices);
    }

    /**
     * Merges a newly downloaded (or updated) model entry into the full master catalog
     * without losing entries that may currently be hidden by UI filters.
     */
    public static List<HashMap<String, Object>> mergeDownloadedModel(
            List<HashMap<String, Object>> masterList,
            HashMap<String, Object> downloadedItem) {
        List<HashMap<String, Object>> result = new ArrayList<>();
        if (masterList != null) {
            for (HashMap<String, Object> item : masterList) {
                if (item != null) {
                    result.add(new HashMap<>(item));
                }
            }
        }

        if (downloadedItem == null) {
            return sanitizeForPersistence(result);
        }

        HashMap<String, Object> cleanItem = new HashMap<>(downloadedItem);
        clearDownloadState(cleanItem);
        cleanItem.put("is_downloading", "false");
        cleanItem.put("download_progress", "100");

        int idx = findMatchingIndex(result, cleanItem);
        if (idx >= 0) {
            HashMap<String, Object> existing = result.get(idx);
            existing.putAll(cleanItem);
            clearDownloadState(existing);
            existing.put("is_downloading", "false");
            existing.put("download_progress", "100");
        } else {
            result.add(cleanItem);
        }

        return sanitizeForPersistence(result);
    }

    /**
     * Removes a downloaded model from the master catalog. Catalog models (with a
     * non-empty {@code model_url}) are reset to an un-downloaded state with all
     * stale download metadata cleared; imported local models are removed entirely.
     */
    public static List<HashMap<String, Object>> removeDownloadedModel(
            List<HashMap<String, Object>> masterList,
            HashMap<String, Object> itemToDelete) {
        List<HashMap<String, Object>> result = new ArrayList<>();
        if (masterList != null) {
            for (HashMap<String, Object> item : masterList) {
                if (item != null) {
                    result.add(new HashMap<>(item));
                }
            }
        }

        if (itemToDelete == null) {
            return sanitizeForPersistence(result);
        }

        int idx = findMatchingIndex(result, itemToDelete);
        if (idx >= 0) {
            HashMap<String, Object> target = result.get(idx);
            String modelUrl = getString(target, "model_url");
            if (!modelUrl.isEmpty()) {
                target.remove("onnx_path");
                target.remove("tokens_path");
                target.remove("voices_bin_path");
                clearDownloadState(target);
                target.put("is_downloading", "false");
                target.put("download_progress", "0");
            } else {
                result.remove(idx);
            }
        }

        return sanitizeForPersistence(result);
    }

    /**
     * Filters and sorts a master model list without mutating it.
     */
    public static List<HashMap<String, Object>> filterAndSort(
            List<HashMap<String, Object>> masterList,
            String sortPreference,
            String languageFilter) {
        List<HashMap<String, Object>> filtered = new ArrayList<>();
        if (masterList == null) {
            return filtered;
        }

        String activeSort = isEmpty(sortPreference) ? "all_models" : sortPreference;
        String activeLang = isEmpty(languageFilter) ? "All Languages" : languageFilter;

        for (HashMap<String, Object> item : masterList) {
            if (item == null) continue;
            String onnxPath = getString(item, "onnx_path");
            boolean isInstalled = !onnxPath.isEmpty();

            String itemLang = getString(item, "language");
            boolean languageMatch = "All Languages".equals(activeLang) || activeLang.equals(itemLang);

            if (languageMatch) {
                if ("download".equals(activeSort)) {
                    if (!isInstalled) filtered.add(item);
                } else if ("installed".equals(activeSort)) {
                    if (isInstalled) filtered.add(item);
                } else {
                    filtered.add(item);
                }
            }
        }

        if ("newest".equals(activeSort)) {
            Collections.reverse(filtered);
        }

        return filtered;
    }

    /**
     * Strips transient UI/runtime keys so persisted {@code models_data} never
     * gets stuck with stale {@code handler_running} or playback flags.
     */
    public static List<HashMap<String, Object>> sanitizeForPersistence(List<HashMap<String, Object>> list) {
        List<HashMap<String, Object>> clean = new ArrayList<>();
        if (list == null) return clean;
        for (HashMap<String, Object> item : list) {
            if (item == null) continue;
            HashMap<String, Object> copy = new HashMap<>(item);
            for (String key : TRANSIENT_UI_KEYS) {
                copy.remove(key);
            }
            clean.add(copy);
        }
        return clean;
    }

    /**
     * Finds the matching model entry by {@code model_url} first, then {@code onnx_path},
     * then non-empty {@code name}.
     */
    public static int findMatchingIndex(List<HashMap<String, Object>> list, HashMap<String, Object> target) {
        if (list == null || target == null) return -1;

        String targetUrl = getString(target, "model_url");
        if (!targetUrl.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (targetUrl.equals(getString(list.get(i), "model_url"))) {
                    return i;
                }
            }
        }

        String targetOnnx = getString(target, "onnx_path");
        if (!targetOnnx.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (targetOnnx.equals(getString(list.get(i), "onnx_path"))) {
                    return i;
                }
            }
        }

        String targetName = getString(target, "name");
        if (!targetName.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (targetName.equals(getString(list.get(i), "name"))) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static void clearDownloadState(HashMap<String, Object> item) {
        if (item == null) return;
        for (String key : DOWNLOAD_STATE_KEYS) {
            item.remove(key);
        }
    }

    public static String getString(HashMap<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return "";
        }
        return map.get(key).toString();
    }

    public static int parseIntSafe(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            String s = value.toString().trim();
            if (s.isEmpty()) return fallback;
            if (s.contains(".")) {
                return (int) Double.parseDouble(s);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long parseLongSafe(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            String s = value.toString().trim();
            if (s.isEmpty()) return fallback;
            if (s.contains(".")) {
                return (long) Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isNonEmptyFile(File f) {
        return f != null && f.exists() && f.isFile() && f.length() > 0;
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            f.delete();
        }
    }
}
