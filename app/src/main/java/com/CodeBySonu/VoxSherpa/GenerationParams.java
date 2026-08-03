package com.CodeBySonu.VoxSherpa;

public class GenerationParams {
    public final String text, onnxModel, tokens, modelType, voicesBin;
    public final int kokoroVoiceId;
    public final float speed, pitch;
    public final boolean punctOn, emotionOn;

    public GenerationParams(String text, String onnxModel, String tokens,
                     String modelType, String voicesBin, int kokoroVoiceId,
                     float speed, float pitch, boolean punctOn, boolean emotionOn) {
        this.text = text;
        this.onnxModel = onnxModel;
        this.tokens = tokens;
        this.modelType = modelType;
        this.voicesBin = voicesBin;
        this.kokoroVoiceId = kokoroVoiceId;
        this.speed = speed;
        this.pitch = pitch;
        this.punctOn = punctOn;
        this.emotionOn = emotionOn;
    }
}