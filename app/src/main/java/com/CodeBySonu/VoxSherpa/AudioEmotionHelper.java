package com.CodeBySonu.VoxSherpa;

import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioEmotionHelper {

    private static final Random random = new Random();
    
    // 🚀 NEW: Static flag to track if a speaker change happened
    private static volatile boolean speakerGapTriggered = false;

    public static void triggerSpeakerGap() {
        speakerGapTriggered = true;
    }
    

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

    // Retrieves the active sample rate safely without using global static state
    private static int getActiveSampleRate() {
        if (KokoroEngine.getInstance().isReady()) {
            int sr = KokoroEngine.getInstance().getSampleRate();
            return sr > 0 ? sr : 24000;
        } else if (VoiceEngine.getInstance().isReady()) {
            int sr = VoiceEngine.getInstance().getSampleRate();
            return sr > 0 ? sr : 22050;
        }
        return 24000; // Default fallback
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
            float baseVolume,
            float silenceScaleUI 
    ) {
        try (ByteArrayOutputStream finalAudioStream = new ByteArrayOutputStream()) {

            // Thread-safe local variables (Replaces the unsafe static variables)
            float localTargetVolume = baseVolume;
            int activeSampleRate = getActiveSampleRate();
            int bytesPerSecond = activeSampleRate * 2;
            
            // 🚀 PERFECT SLIDER MATH: 50% slider (0.5f) = 1.0x (Normal behavior)
            float multiplier = silenceScaleUI * 2.0f;
            
            // --- SPEAKER CHANGE GAP LOGIC ---
            boolean applySpeakerGap = speakerGapTriggered;
            if (applySpeakerGap) {
                speakerGapTriggered = false; 
                int baseSpeakerGapMs = 500; // Normal gap between two different speakers
                int gapMs = (int)(baseSpeakerGapMs * multiplier); 
                
                if (gapMs > 0) {
                    finalAudioStream.write(createSilence(gapMs, bytesPerSecond));
                }
            }
            
            EmotionProfile currentProfile = new EmotionProfile(baseVolume, baseSpeed, basePitch, 1500);

            String regex = "(\\[[a-zA-Z]+\\]|\\.\\.\\.|[.,!?।])";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(inputText);

            int lastEnd = 0;

            while (matcher.find()) {
                String textChunk = inputText.substring(lastEnd, matcher.start()).trim();
                
                if (!textChunk.isEmpty() && !textChunk.matches("\\[[a-zA-Z]+\\]")) {
                    byte[] chunkAudio = generateWithEngine(textChunk, currentProfile, localTargetVolume, activeSampleRate);
                    if (chunkAudio != null) {
                        finalAudioStream.write(chunkAudio);
                    }
                    localTargetVolume = currentProfile.volume; 
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
                    // --- PUNCTUATION SILENCE LOGIC ---
                    if (isPunctOn) {
                        int baseSilenceMs = 0;

                        switch (token) {
                            case ",": baseSilenceMs = 150; break;
                            case "!": baseSilenceMs = 200; break;
                            case "?": baseSilenceMs = 250; break;
                            case ".": case "।": baseSilenceMs = 300; break;
                            case "...": baseSilenceMs = 450; break;
                        }

                        if (baseSilenceMs > 0) {
                            // Apply the exact same multiplier as speaker gap
                            baseSilenceMs = (int)(baseSilenceMs * multiplier);

                            if (baseSilenceMs > 0) {
                                baseSilenceMs = (int)(baseSilenceMs / currentProfile.speed);

                                int jitterRange = (int)(baseSilenceMs * 0.10f);
                                int finalSilenceMs = baseSilenceMs;

                                if (jitterRange > 0) {
                                    finalSilenceMs += (random.nextInt(jitterRange * 2) - jitterRange);
                                }
                                
                                if (finalSilenceMs < 0) finalSilenceMs = 0;

                                finalAudioStream.write(createSilence(finalSilenceMs, bytesPerSecond));
                            }
                        }
                    }
                }

                lastEnd = matcher.end();
            }

            String remainingText = inputText.substring(lastEnd).trim();
            if (!remainingText.isEmpty() && !remainingText.matches("\\[[a-zA-Z]+\\]")) {
                byte[] chunkAudio = generateWithEngine(remainingText, currentProfile, localTargetVolume, activeSampleRate);
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
    private static byte[] generateWithEngine(String text, EmotionProfile profile, float startVol, int sampleRate) {
        
        byte[] rawPcm = null;

        // Directly generate using the active engine
        if (KokoroEngine.getInstance().isReady()) {
            rawPcm = KokoroEngine.getInstance().generateAudioPCM(text, profile.speed, profile.pitch);
        } else if (VoiceEngine.getInstance().isReady()) {
            rawPcm = VoiceEngine.getInstance().generateAudioPCM(text, profile.speed, profile.pitch);
        }

        if (rawPcm == null || rawPcm.length == 0) return null;

        // Skip processing if everything is default
        if (startVol == 1.0f && profile.volume == 1.0f) {
            return rawPcm;
        }

        // --- LONG ATTACK ENVELOPE (Gradual Volume Shift) ---
        int totalSamples = rawPcm.length / 2;
        int transitionSamples = (sampleRate * profile.attackTimeMs) / 1000; 
        
        // Ensure transition doesn't exceed total samples
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
    private static byte[] createSilence(int durationMs, int bytesPerSecond) {
        if (durationMs <= 0) return new byte[0];
        int bytesNeeded = (bytesPerSecond * durationMs) / 1000;
        // Ensure even number of bytes for 16-bit alignment
        if (bytesNeeded % 2 != 0) bytesNeeded++;
        return new byte[bytesNeeded]; 
    }
}
