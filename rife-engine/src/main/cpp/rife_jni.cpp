#include <jni.h>
#include <android/log.h>
#include <string>
#include "rife_engine.h"

#define LOG_TAG "RifeJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::string jstringToStd(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(jstr, chars);
    return result;
}
} // namespace

extern "C" {

// Returns an opaque native handle (as jlong) or 0 on failure.
// Kotlin side owns the handle's lifetime and must call nativeRelease exactly once.
JNIEXPORT jlong JNICALL
Java_com_devson_nvplayer_rife_RifeInterpolator_nativeInit(
        JNIEnv* env, jclass /*clazz*/,
        jstring modelDir, jint gpuId, jboolean ttaSpatial, jboolean ttaTemporal,
        jboolean uhdMode, jint numThreads) {
    auto* engine = new RifeEngine();
    RifeEngine::InitParams params;
    params.modelDir = jstringToStd(env, modelDir);
    params.gpuId = gpuId;
    params.ttaSpatial = (ttaSpatial == JNI_TRUE);
    params.ttaTemporal = (ttaTemporal == JNI_TRUE);
    params.uhdMode = (uhdMode == JNI_TRUE);
    params.numThreads = numThreads;

    if (engine->init(params) != 0) {
        LOGE("RifeEngine init failed for model=%s", params.modelDir.c_str());
        delete engine;
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

// frameA/frameB/outFrame MUST be direct ByteBuffers (see RifeInterpolator.kt -
// ByteBuffer.allocateDirect), each sized width*height*3/2 bytes (NV12).
// Returns 0 on success, matching RifeEngine's error codes otherwise.
JNIEXPORT jint JNICALL
Java_com_devson_nvplayer_rife_RifeInterpolator_nativeInterpolate(
        JNIEnv* env, jobject /*thiz*/, jlong handle,
        jobject frameA, jobject frameB, jint width, jint height,
        jfloat timestep, jobject outFrame) {
    if (handle == 0) return -2;
    auto* engine = reinterpret_cast<RifeEngine*>(handle);

    auto* a = static_cast<uint8_t*>(env->GetDirectBufferAddress(frameA));
    auto* b = static_cast<uint8_t*>(env->GetDirectBufferAddress(frameB));
    auto* out = static_cast<uint8_t*>(env->GetDirectBufferAddress(outFrame));
    if (!a || !b || !out) {
        LOGE("nativeInterpolate: buffers must be direct ByteBuffers");
        return -3;
    }
    return engine->interpolate(a, b, width, height, timestep, out);
}

JNIEXPORT void JNICALL
Java_com_devson_nvplayer_rife_RifeInterpolator_nativeRelease(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    if (handle == 0) return;
    auto* engine = reinterpret_cast<RifeEngine*>(handle);
    engine->release();
    delete engine;
}

// Declared in mpv_rife_stream_source.cpp - stubbed out (returns -100) unless
// RIFE_ENGINE_HAS_MPV_STREAM_CB=1, i.e. unless the mpv-android-patch
// prerequisite has been applied and its headers vendored. See that file's
// header comment and /mpv-android-patch/README.md.
int rife_register_mpv_stream_source(void* mpvHandle);

JNIEXPORT jint JNICALL
Java_com_devson_nvplayer_rife_realtime_RifeFrameSource_nativeRegisterStreamSource(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong mpvHandle, jstring /*modelFolder*/, jint /*scaleFactor*/) {
    return rife_register_mpv_stream_source(reinterpret_cast<void*>(mpvHandle));
}

} // extern "C"
