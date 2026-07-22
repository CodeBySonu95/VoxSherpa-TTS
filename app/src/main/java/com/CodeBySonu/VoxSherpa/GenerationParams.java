package com.CodeBySonu.VoxSherpa;

public class GenerationParams {
    // Added language to the string parameters
    public final String text, onnxModel, tokens, modelType, voicesBin, language;
    public final int kokoroVoiceId;
    public final float speed, pitch;
    public final boolean punctOn, emotionOn;

    public GenerationParams(String text, String onnxModel, String tokens,
                     String modelType, String voicesBin, String language, int kokoroVoiceId,
                     float speed, float pitch, boolean punctOn, boolean emotionOn) {
        this.text = text;
        this.onnxModel = onnxModel;
        this.tokens = tokens;
        this.modelType = modelType;
        this.voicesBin = voicesBin;
        this.language = language; // Store the language properly
        this.kokoroVoiceId = kokoroVoiceId;
        this.speed = speed;
        this.pitch = pitch;
        this.punctOn = punctOn;
        this.emotionOn = emotionOn;
    }
}
