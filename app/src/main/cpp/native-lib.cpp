#include <jni.h>
#include <oboe/Oboe.h>
#include <sherpa-onnx/c-api/c-api.h>

extern "C" JNIEXPORT void JNICALL
Java_com_CodeBySonu_VoxSherpa_MainActivity_initAudio(JNIEnv* env, jobject /* this */) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    oboe::AudioStream* stream;
    builder.openStream(&stream);
    stream->requestStart();
}

extern "C" JNIEXPORT void JNICALL
Java_com_CodeBySonu_VoxSherpa_MainActivity_triggerHaptic(JNIEnv* env, jobject thiz) {
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID methodId = env->GetMethodID(clazz, "performHaptic", "()V");
    if(methodId) {
        env->CallVoidMethod(thiz, methodId);
    }
}

void processAudio(float* buffer, int frames) {
    for (int i = 0; i < frames; i++) {
        buffer[i] *= 1.2f; // simple compressor/gain
        if (buffer[i] > 1.0f) buffer[i] = 1.0f;
        if (buffer[i] < -1.0f) buffer[i] = -1.0f;
    }
}
