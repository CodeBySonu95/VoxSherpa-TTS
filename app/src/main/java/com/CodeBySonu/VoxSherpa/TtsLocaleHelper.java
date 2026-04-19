package com.CodeBySonu.VoxSherpa;

import java.util.Locale;

public class TtsLocaleHelper {

    /**
     * Converts a raw language name (e.g., "English", "Hindi", "Marathi") to a valid Android Locale.
     */
    public static Locale getLocaleFromName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty() || rawName.equalsIgnoreCase("Unknown")) {
            return null;
        }

        String search = rawName.trim().toLowerCase();

        // 1. FAST OVERRIDES: 
        if (search.contains("english")) return Locale.US;
        if (search.contains("hindi")) return new Locale("hi", "IN");
        if (search.contains("chinese") || search.contains("mandarin")) return Locale.CHINA;
        if (search.contains("french")) return Locale.FRANCE;
        if (search.contains("spanish")) return new Locale("es", "ES");
        if (search.contains("german")) return Locale.GERMANY;
        if (search.contains("italian")) return Locale.ITALY;
        if (search.contains("japanese")) return Locale.JAPAN;
        if (search.contains("korean")) return Locale.KOREA;
        if (search.contains("russian")) return new Locale("ru", "RU");
        if (search.contains("portuguese")) return new Locale("pt", "BR");

        // 2. DYNAMIC MATCHING:
        Locale[] availableLocales = Locale.getAvailableLocales();
        for (Locale locale : availableLocales) {
            String displayLang = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase();
            if (!displayLang.isEmpty() && search.equals(displayLang)) {
                return locale;
            }
        }

        for (Locale locale : availableLocales) {
            String displayLang = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase();
            if (!displayLang.isEmpty() && search.contains(displayLang)) {
                return locale;
            }
        }

        // If it is not possible, then return null return null;
    }

    public static String[] getTtsLanguageArray(String rawName) {
        Locale locale = getLocaleFromName(rawName);
        
        if (locale == null) {
            return new String[] {"eng", "USA", ""};
        }

        try {
            String isoLang = locale.getISO3Language();
            String isoCountry = locale.getISO3Country();
            return new String[] { isoLang, isoCountry, "" };
        } catch (Exception e) {
            // Safe fallback
            return new String[] {"eng", "USA", ""};
        }
    }
}
