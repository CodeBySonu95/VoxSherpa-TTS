# Consumers of this AAR must not strip the engine entry points or the
# sherpa-onnx native bridge classes. The engines are loaded reflectively
# via getInstance() and the sherpa-onnx classes register native methods.
-keep class com.CodeBySonu.VoxSherpa.KokoroEngine { *; }
-keep class com.CodeBySonu.VoxSherpa.VoiceEngine { *; }
-keep class com.CodeBySonu.VoxSherpa.AudioEmotionHelper { *; }
-keep class com.CodeBySonu.VoxSherpa.Sonic { *; }
-keep class com.CodeBySonu.VoxSherpa.KokoroVoiceHelper { *; }
-keep class com.CodeBySonu.VoxSherpa.GenerationParams { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
