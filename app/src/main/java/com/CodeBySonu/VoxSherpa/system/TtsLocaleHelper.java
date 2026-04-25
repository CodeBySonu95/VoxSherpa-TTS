package com.CodeBySonu.VoxSherpa.system;

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

        // 1. FAST OVERRIDES: Direct fast return for major languages
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

        // 2. DYNAMIC MATCHING: Iterates through available Android locales for an exact match
        Locale[] availableLocales = Locale.getAvailableLocales();
        for (Locale locale : availableLocales) {
            String displayLang = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase();
            if (!displayLang.isEmpty() && search.equals(displayLang)) {
                return locale;
            }
        }

        // 3. PARTIAL MATCH: Matches partial strings, e.g., finding "nepali" from "Nepali (India)"
        for (Locale locale : availableLocales) {
            String displayLang = locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase();
            if (!displayLang.isEmpty() && search.contains(displayLang)) {
                return locale;
            }
        }

        // Return null if no match is found
        return null;
    }

    /**
     * Used directly in TextToSpeechService's onGetLanguage() method.
     * Returns array in format: { "ISO3Language", "ISO3Country", "Variant" }
     */
    public static String[] getTtsLanguageArray(String rawName) {
        Locale locale = getLocaleFromName(rawName);
        
        if (locale == null) {
            // If no model is selected or the language is unknown,
            // return empty strings instead of defaulting to English.
            return new String[] {"", "", ""};
        }

        try {
            // Android TTS requires ISO 3-letter codes (e.g., "eng", "hin", "mar")
            String isoLang = locale.getISO3Language();
            String isoCountry = locale.getISO3Country();
            return new String[] { isoLang, isoCountry, "" };
        } catch (Exception e) {
            // Safe fallback returning empty strings
            return new String[] {"", "", ""};
        }
    }
}
