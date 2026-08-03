package com.CodeBySonu.VoxSherpa.system;

import java.util.Locale;

public class TtsLocaleHelper {

    public static Locale getLocaleFromName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty() || rawName.equalsIgnoreCase("Unknown")) {
            return null;
        }

        String search = rawName.trim().toLowerCase();

        // FAST OVERRIDES: Sirf Language set hai, Country ko completely nikal diya gaya hai
        if (search.contains("english")) return Locale.ENGLISH;
        if (search.contains("hindi")) return new Locale("hi");
        if (search.contains("chinese") || search.contains("mandarin")) return Locale.CHINESE;
        if (search.contains("french")) return Locale.FRENCH;
        if (search.contains("spanish")) return new Locale("es");
        if (search.contains("german")) return Locale.GERMAN;
        if (search.contains("italian")) return Locale.ITALIAN;
        if (search.contains("japanese")) return Locale.JAPANESE;
        if (search.contains("korean")) return Locale.KOREAN;
        if (search.contains("russian")) return new Locale("ru");
        if (search.contains("portuguese")) return new Locale("pt");

        Locale[] availableLocales = Locale.getAvailableLocales();
        for (Locale locale : availableLocales) {
            String displayLang = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase();
            if (!displayLang.isEmpty() && search.equals(displayLang)) {
                return new Locale(locale.getLanguage()); // No Country
            }
        }

        for (Locale locale : availableLocales) {
            String displayLang = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase();
            if (!displayLang.isEmpty() && search.contains(displayLang)) {
                return new Locale(locale.getLanguage()); // No Country
            }
        }

        return null;
    }

    public static String[] getTtsLanguageArray(String rawName) {
        Locale locale = getLocaleFromName(rawName);
        
        // 🚀 STRICT: Agar language nahi hai, toh bilkul khali bhej do, koi fake English nahi
        if (locale == null) {
            return new String[] {"", "", ""};
        }

        try {
            // Sirf aur sirf ISO-3 Language Code return hoga, conflict khtam.
            String isoLang = locale.getISO3Language();
            return new String[] { isoLang, "", "" };
        } catch (Exception e) {
            return new String[] {"", "", ""};
        }
    }
}
