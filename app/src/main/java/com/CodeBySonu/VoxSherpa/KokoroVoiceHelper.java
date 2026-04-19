package com.CodeBySonu.VoxSherpa;

import java.util.ArrayList;
import java.util.List;

public class KokoroVoiceHelper {

    // ── Data class ──────────────────────────────────────────────────────────
    public static class VoiceItem {
        public final int    speakerId;
        public final String voiceKey;
        public final String displayName;
        public final String language;
        public final String languageCode;
        public final String gender;
        public final String flag;

        public VoiceItem(int speakerId, String voiceKey,
                         String displayName, String language,
                         String languageCode, String gender, String flag) {
            this.speakerId    = speakerId;
            this.voiceKey     = voiceKey;
            this.displayName  = displayName;
            this.language     = language;
            this.languageCode = languageCode;
            this.gender       = gender;
            this.flag         = flag;
        }

        public String getSubtitle() {
            return language + " • " + gender;
        }

        public String getFullLabel() {
            return flag + " " + displayName + " (" + language + " " + gender + ")";
        }
    }

    // ── Master voice list (kokoro-multi-lang-v1_0 — 53 speakers) ────────────
    private static final List<VoiceItem> ALL_VOICES = new ArrayList<VoiceItem>() {{

        // 🇺🇸 American Female
        add(new VoiceItem(0,  "af_alloy",    "Alloy",    "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(1,  "af_aoede",    "Aoede",    "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(2,  "af_bella",    "Bella",    "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(3,  "af_heart",    "Heart",    "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(4,  "af_jessica",  "Jessica",  "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(5,  "af_kore",     "Kore",     "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(6,  "af_nicole",   "Nicole",   "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(7,  "af_nova",     "Nova",     "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(8,  "af_river",    "River",    "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(9,  "af_sarah",    "Sarah",    "English", "en", "Female", "🇺🇸"));
        add(new VoiceItem(10, "af_sky",      "Sky",      "English", "en", "Female", "🇺🇸"));

        // 🇺🇸 American Male
        add(new VoiceItem(11, "am_adam",     "Adam",     "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(12, "am_echo",     "Echo",     "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(13, "am_eric",     "Eric",     "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(14, "am_fenrir",   "Fenrir",   "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(15, "am_liam",     "Liam",     "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(16, "am_michael",  "Michael",  "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(17, "am_onyx",     "Onyx",     "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(18, "am_puck",     "Puck",     "English", "en", "Male",   "🇺🇸"));
        add(new VoiceItem(19, "am_santa",    "Santa",    "English", "en", "Male",   "🇺🇸"));

        // 🇬🇧 British Female
        add(new VoiceItem(20, "bf_alice",    "Alice",    "English", "en", "Female", "🇬🇧"));
        add(new VoiceItem(21, "bf_emma",     "Emma",     "English", "en", "Female", "🇬🇧"));
        add(new VoiceItem(22, "bf_isabella", "Isabella", "English", "en", "Female", "🇬🇧"));
        add(new VoiceItem(23, "bf_lily",     "Lily",     "English", "en", "Female", "🇬🇧"));

        // 🇬🇧 British Male
        add(new VoiceItem(24, "bm_daniel",   "Daniel",   "English", "en", "Male",   "🇬🇧"));
        add(new VoiceItem(25, "bm_fable",    "Fable",    "English", "en", "Male",   "🇬🇧"));
        add(new VoiceItem(26, "bm_george",   "George",   "English", "en", "Male",   "🇬🇧"));
        add(new VoiceItem(27, "bm_lewis",    "Lewis",    "English", "en", "Male",   "🇬🇧"));

        // 🇪🇸 Spanish
        add(new VoiceItem(28, "ef_dora",     "Dora",     "Spanish", "es", "Female", "🇪🇸"));
        add(new VoiceItem(29, "em_alex",     "Alex",     "Spanish", "es", "Male",   "🇪🇸"));

        // 🇫🇷 French
        add(new VoiceItem(30, "ff_siwis",    "Siwis",    "French",  "fr", "Female", "🇫🇷"));

        // 🇮🇳 Hindi
        add(new VoiceItem(31, "hf_alpha",    "Alpha",    "Hindi",   "hi", "Female", "🇮🇳"));
        add(new VoiceItem(32, "hf_beta",     "Beta",     "Hindi",   "hi", "Female", "🇮🇳"));
        add(new VoiceItem(33, "hm_omega",    "Omega",    "Hindi",   "hi", "Male",   "🇮🇳"));
        add(new VoiceItem(34, "hm_psi",      "Psi",      "Hindi",   "hi", "Male",   "🇮🇳"));

        // 🇮🇹 Italian
        add(new VoiceItem(35, "if_sara",     "Sara",     "Italian", "it", "Female", "🇮🇹"));
        add(new VoiceItem(36, "im_nicola",   "Nicola",   "Italian", "it", "Male",   "🇮🇹"));

        // 🇯🇵 Japanese
        add(new VoiceItem(37, "jf_alpha",      "Alpha",      "Japanese", "ja", "Female", "🇯🇵"));
        add(new VoiceItem(38, "jf_gongitsune", "Gongitsune", "Japanese", "ja", "Female", "🇯🇵"));
        add(new VoiceItem(39, "jf_nezumi",     "Nezumi",     "Japanese", "ja", "Female", "🇯🇵"));
        add(new VoiceItem(40, "jf_tebukuro",   "Tebukuro",   "Japanese", "ja", "Female", "🇯🇵"));
        add(new VoiceItem(41, "jm_kumo",       "Kumo",       "Japanese", "ja", "Male",   "🇯🇵"));

        // 🇵🇹 Portuguese
        add(new VoiceItem(42, "pf_dora",     "Dora",     "Portuguese", "pt", "Female", "🇵🇹"));
        add(new VoiceItem(43, "pm_alex",     "Alex",     "Portuguese", "pt", "Male",   "🇵🇹"));
        add(new VoiceItem(44, "pm_santa",    "Santa",    "Portuguese", "pt", "Male",   "🇵🇹"));

        // 🇨🇳 Chinese
        add(new VoiceItem(45, "zf_xiaobei",  "Xiaobei",  "Chinese", "zh", "Female", "🇨🇳"));
        add(new VoiceItem(46, "zf_xiaoni",   "Xiaoni",   "Chinese", "zh", "Female", "🇨🇳"));
        add(new VoiceItem(47, "zf_xiaoxiao", "Xiaoxiao", "Chinese", "zh", "Female", "🇨🇳"));
        add(new VoiceItem(48, "zf_xiaoyi",   "Xiaoyi",   "Chinese", "zh", "Female", "🇨🇳"));
        add(new VoiceItem(49, "zm_yunjian",  "Yunjian",  "Chinese", "zh", "Male",   "🇨🇳"));
        add(new VoiceItem(50, "zm_yunxi",    "Yunxi",    "Chinese", "zh", "Male",   "🇨🇳"));
        add(new VoiceItem(51, "zm_yunxia",   "Yunxia",   "Chinese", "zh", "Male",   "🇨🇳"));
        add(new VoiceItem(52, "zm_yunyang",  "Yunyang",  "Chinese", "zh", "Male",   "🇨🇳"));
    }};

    // ── Public query methods ─────────────────────────────────────────────────

    /** Sab voices return karo */
    public static List<VoiceItem> getAllVoices() {
        return ALL_VOICES;
    }

    /** Specific language ki voices: getByLanguage("hi") → 4 Hindi voices */
    public static List<VoiceItem> getByLanguage(String languageCode) {
        List<VoiceItem> result = new ArrayList<>();
        for (VoiceItem v : ALL_VOICES) {
            if (v.languageCode.equals(languageCode)) result.add(v);
        }
        return result;
    }

    /** Gender filter: getByGender("Female") */
    public static List<VoiceItem> getByGender(String gender) {
        List<VoiceItem> result = new ArrayList<>();
        for (VoiceItem v : ALL_VOICES) {
            if (v.gender.equals(gender)) result.add(v);
        }
        return result;
    }

    /** Language + Gender combo filter */
    public static List<VoiceItem> getByLanguageAndGender(String languageCode, String gender) {
        List<VoiceItem> result = new ArrayList<>();
        for (VoiceItem v : ALL_VOICES) {
            if (v.languageCode.equals(languageCode) && v.gender.equals(gender)) result.add(v);
        }
        return result;
    }

    /** Speaker ID se VoiceItem lo */
    public static VoiceItem getById(int speakerId) {
        for (VoiceItem v : ALL_VOICES) {
            if (v.speakerId == speakerId) return v;
        }
        return null;
    }

    public static VoiceItem getByKey(String voiceKey) {
        for (VoiceItem v : ALL_VOICES) {
            if (v.voiceKey.equals(voiceKey)) return v;
        }
        return null;
    }

    public static List<String> getAvailableLanguages() {
        List<String> langs = new ArrayList<>();
        for (VoiceItem v : ALL_VOICES) {
            if (!langs.contains(v.language)) langs.add(v.language);
        }
        return langs;
    }

    public static VoiceItem getDefaultVoice() {
        return getById(31);
    }
}
