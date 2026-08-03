package com.CodeBySonu.VoxSherpa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiperVoiceHelper {

    // Regex pattern to extract numerical IDs from tags like [speaker1], [speaker99], [speaker900]
    private static final Pattern SPEAKER_PATTERN = Pattern.compile("\\[speaker(\\d+)\\]", Pattern.CASE_INSENSITIVE);

    /**
     * Processes the input text to find a speaker tag, updates the VoiceEngine state,
     * and returns the clean text without the tag so the engine does not read it aloud.
     */
    public static String processAndSetSpeaker(String text, boolean isKokoro) {
        Matcher matcher = SPEAKER_PATTERN.matcher(text);
        
        if (matcher.find()) {
            try {
                // Extract the numerical speaker ID from the tag
                int speakerId = Integer.parseInt(matcher.group(1));
                
                // Remove the tag from the text completely
                text = matcher.replaceAll("").trim();
                
                // Apply the speaker ID only if the active engine is Piper (not Kokoro)
                if (!isKokoro) {
                    VoiceEngine.getInstance().setActiveSpeakerId(speakerId);
                }
                
                // 🚀 NAYA LOGIC: Trigger a natural gap in AudioEmotionHelper
                AudioEmotionHelper.triggerSpeakerGap();
                
            } catch (Exception ignored) {
                // Ignore parsing exceptions to prevent app crashes
            }
        }
        
        return text;
    }
}
