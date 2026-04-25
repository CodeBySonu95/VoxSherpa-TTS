package com.CodeBySonu.VoxSherpa;

import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioEmotionHelper {

    private static int SAMPLE_RATE = 22050;
    private static int BYTES_PER_SECOND = 44100;
    
    private static float lastTargetVolume = 1.0f;
    private static final Random random = new Random();

    // ==========================================
    // 1. EmotionProfile Core System
    // ==========================================
    public static class EmotionProfile {
        public float volume;
        public float speed;
        public float pitch; 
        public int attackTimeMs; 

        public EmotionProfile(float volume, float speed, float pitch, int attackTimeMs) {
            this.volume = volume;
            this.speed = speed;
            this.pitch = pitch;
            this.attackTimeMs = attackTimeMs;
        }
    }

    // ==========================================
    // 2. Main Processing Method
    // ==========================================
    public static byte[] processAndGenerate(
            String inputText,
            boolean isPunctOn,
            boolean isEmotionOn,
            float baseSpeed,
            float basePitch, 
            float baseVolume 
    ) {
        try (ByteArrayOutputStream finalAudioStream = new ByteArrayOutputStream()) {

            // Reset state variables for a fresh new generation
            lastTargetVolume = baseVolume;
            
            EmotionProfile currentProfile = new EmotionProfile(baseVolume, baseSpeed, basePitch, 1500);

            // Regex: Finds Tags like [sad], [angry] AND Punctuation like ..., ., ,, !, ?, ।
            String regex = "(\\[[a-zA-Z]+\\]|\\.\\.\\.|[.,!?।])";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(inputText);

            int lastEnd = 0;

            while (matcher.find()) {
                // Generate Voice for the text BEFORE the special token
                String textChunk = inputText.substring(lastEnd, matcher.start()).trim();
                
                if (!textChunk.isEmpty() && !textChunk.matches("\\[[a-zA-Z]+\\]")) {
                    byte[] chunkAudio = generateWithEngine(textChunk, currentProfile, lastTargetVolume);
                    if (chunkAudio != null) {
                        finalAudioStream.write(chunkAudio);
                    }
                    lastTargetVolume = currentProfile.volume; // Update for the next chunk
                }

                String token = matcher.group();

                if (token.startsWith("[")) {
                    if (isEmotionOn) {
                        String tag = token.toLowerCase();
                        switch (tag) {
                            case "[whispers]":
                            case "[whisper]":
                                currentProfile = new EmotionProfile(0.65f, baseSpeed * 0.95f, basePitch * 1.05f, 2500);
                                break;
                            case "[angry]":
                                currentProfile = new EmotionProfile(1.15f, baseSpeed * 1.05f, basePitch * 0.95f, 1500);
                                break;
                            case "[sad]":
                                currentProfile = new EmotionProfile(0.80f, baseSpeed * 0.92f, basePitch * 0.98f, 2500);
                                break;
                            case "[sarcastically]":
                            case "[sarcastic]":
                                currentProfile = new EmotionProfile(1.0f, baseSpeed * 1.02f, basePitch * 0.95f, 1500);
                                break;
                            case "[giggles]":
                            case "[giggle]":
                                currentProfile = new EmotionProfile(1.10f, baseSpeed * 1.05f, basePitch * 1.10f, 1000);
                                break;
                            case "[normal]":
                            case "[]":
                                currentProfile = new EmotionProfile(baseVolume, baseSpeed, basePitch, 1500);
                                break;
                            default:
                                currentProfile = new EmotionProfile(baseVolume, baseSpeed, basePitch, 1500);
                                break;
                        }
                    }
                } else {
                    // --- PUNCTUATION SILENCE & JITTER LOGIC ---
                    if (isPunctOn) {
                        int baseSilenceMs = 0;

                        switch (token) {
                            case ",":
                                baseSilenceMs = 140;
                                break;
                            case "!":
                                baseSilenceMs = 190;
                                break;
                            case "?":
                                baseSilenceMs = 230;
                                break;
                            case ".":
                            case "।":
                                baseSilenceMs = 280;
                                break;
                            case "...":
                                baseSilenceMs = 380;
                                break;
                        }

                        if (baseSilenceMs > 0) {
                            baseSilenceMs = (int)(baseSilenceMs / currentProfile.speed);

                            int jitterRange = (int)(baseSilenceMs * 0.10f);
                            int finalSilenceMs = baseSilenceMs;

                            if (jitterRange > 0) {
                                finalSilenceMs += (random.nextInt(jitterRange * 2) - jitterRange);
                            }

                            if (finalSilenceMs < 60) finalSilenceMs = 60;
                            if (finalSilenceMs > 600) finalSilenceMs = 600;

                            byte[] silenceBytes = createSilence(finalSilenceMs);
                            finalAudioStream.write(silenceBytes);
                        }
                    }
                }

                lastEnd = matcher.end();
            }

            String remainingText = inputText.substring(lastEnd).trim();
            if (!remainingText.isEmpty() && !remainingText.matches("\\[[a-zA-Z]+\\]")) {
                byte[] chunkAudio = generateWithEngine(remainingText, currentProfile, lastTargetVolume);
                if (chunkAudio != null) {
                    finalAudioStream.write(chunkAudio);
                }
            }

            return finalAudioStream.toByteArray();

        } catch (Exception e) {
            return null;
        }
    }

    // ==========================================
    // 3. Engine Connection & DSP Modification
    // ==========================================
    private static byte[] generateWithEngine(String text, EmotionProfile profile, float startVol) {
        
        byte[] rawPcm;

        // Dynamic Engine selection + Sample Rate update
        if (KokoroEngine.getInstance().isReady()) {
            SAMPLE_RATE = KokoroEngine.getInstance().getSampleRate();
            if(SAMPLE_RATE == 0) SAMPLE_RATE = 24000;
            BYTES_PER_SECOND = SAMPLE_RATE * 2;
            rawPcm = KokoroEngine.getInstance().generateAudioPCM(text, profile.speed, profile.pitch);
        } else if (VoiceEngine.getInstance().isReady()) {
            SAMPLE_RATE = VoiceEngine.getInstance().getSampleRate();
            if(SAMPLE_RATE == 0) SAMPLE_RATE = 22050;
            BYTES_PER_SECOND = SAMPLE_RATE * 2;
            rawPcm = VoiceEngine.getInstance().generateAudioPCM(text, profile.speed, profile.pitch);
        } else {
             return null;
        }

        if (rawPcm == null || rawPcm.length == 0) return null;

        // Skip processing if everything is default
        if (startVol == 1.0f && profile.volume == 1.0f) {
            return rawPcm;
        }

        // --- LONG ATTACK ENVELOPE (Dheere-Dheere Volume Shift) ---
        int totalSamples = rawPcm.length / 2;
        int transitionSamples = (SAMPLE_RATE * profile.attackTimeMs) / 1000; 
        
        transitionSamples = Math.min(transitionSamples, totalSamples); 
        
        float volumeStep = 0f;
        if (transitionSamples > 0) {
            volumeStep = (profile.volume - startVol) / transitionSamples;
        }

        float currentVol = startVol;

        for (int i = 0; i < rawPcm.length; i += 2) {
            // Reconstruct 16-bit short value
            int lower = rawPcm[i] & 0xFF;
            int upper = rawPcm[i + 1] << 8;
            int sample = (short) (lower | upper);

            // Calculate gradual volume for the current sample
            int sampleIndex = i / 2;
            if (sampleIndex < transitionSamples) {
                currentVol = startVol + (volumeStep * sampleIndex);
            } else {
                currentVol = profile.volume; 
            }

            // Apply volume
            sample = (int) (sample * currentVol);

            // Clip to prevent distortion noise
            if (sample > 32767) sample = 32767;
            if (sample < -32768) sample = -32768;

            // Convert back to 2 bytes
            rawPcm[i] = (byte) (sample & 0xFF);
            rawPcm[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return rawPcm;
    }

    // Creates an array filled with Zeros to physically stop the speaker
    private static byte[] createSilence(int durationMs) {
        if (durationMs <= 0) return new byte[0];
        int bytesNeeded = (BYTES_PER_SECOND * durationMs) / 1000;
        // Ensure even number of bytes for 16-bit alignment
        if (bytesNeeded % 2 != 0) bytesNeeded++;
        return new byte[bytesNeeded]; 
    }
}
