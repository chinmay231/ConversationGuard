// app/src/main/cpp/whisper_jni.cpp

#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <mutex>
#include <thread>
#include <android/log.h>

extern "C" {
#include "whisper.h"
}

#define LOG_TAG "ConversationGuardJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Single global context (service-style)
static whisper_context* g_ctx = nullptr;
static std::mutex g_mutex;

// Helper: jstring -> std::string (UTF-8)
static std::string jstring_to_utf8(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* utf = env->GetStringUTFChars(js, nullptr);
    if (!utf) return {};
    std::string out(utf);
    env->ReleaseStringUTFChars(js, utf);
    return out;
}

// Pick a reasonable number of threads based on hardware
static int choose_num_threads() {
    unsigned int hw = std::thread::hardware_concurrency();
    if (hw == 0) {
        hw = 2;
    }
    // Don’t go crazy; 2–4 threads is usually fine on mobile
    int t = static_cast<int>(hw);
    if (t < 2) t = 2;
    if (t > 4) t = 4;
    return t;
}

// ------------ nativeInit(modelPath: String): Boolean ------------

extern "C"
JNIEXPORT jboolean JNICALL
Java_ai_guard_native_WhisperBridge_nativeInit(
        JNIEnv* env,
        jclass /* clazz */,
        jstring jModelPath) {

    std::lock_guard<std::mutex> lock(g_mutex);

    // If already initialized, free and re-init
    if (g_ctx) {
        LOGI("Releasing previous Whisper context before init");
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    if (!jModelPath) {
        LOGE("nativeInit: null model path");
        return JNI_FALSE;
    }

    std::string modelPath = jstring_to_utf8(env, jModelPath);
    if (modelPath.empty()) {
        LOGE("nativeInit: empty model path");
        return JNI_FALSE;
    }

    LOGI("Loading Whisper model from: %s", modelPath.c_str());
    g_ctx = whisper_init_from_file(modelPath.c_str());
    if (!g_ctx) {
        LOGE("whisper_init_from_file() failed");
        return JNI_FALSE;
    }

    const char* sys_info = whisper_print_system_info();
    LOGI("Whisper system info: %s", sys_info);

    return JNI_TRUE;
}

// ------------ nativeRelease(): void ------------

extern "C"
JNIEXPORT void JNICALL
Java_ai_guard_native_WhisperBridge_nativeRelease(
        JNIEnv* /* env */,
        jclass /* clazz */) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        LOGI("Releasing Whisper context");
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}

// ------------ nativeProcess(pcm: ShortArray, length: Int): String? ------------

extern "C"
JNIEXPORT jstring JNICALL
Java_ai_guard_native_WhisperBridge_nativeProcess(
        JNIEnv* env,
        jclass /* clazz */,
        jshortArray jPcm,
        jint length) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx) {
        LOGE("nativeProcess: g_ctx is null – model not initialized");
        return env->NewStringUTF("");
    }

    if (!jPcm || length <= 0) {
        LOGE("nativeProcess: invalid PCM array or length=%d", length);
        return env->NewStringUTF("");
    }

    const jint n_samples = length;

    // Copy short PCM into float [-1, 1] as Whisper expects
    std::vector<float> pcm(static_cast<size_t>(n_samples));

    jshort* buf = env->GetShortArrayElements(jPcm, nullptr);
    if (!buf) {
        LOGE("nativeProcess: GetShortArrayElements returned null");
        return env->NewStringUTF("");
    }

    for (jint i = 0; i < n_samples; ++i) {
        pcm[static_cast<size_t>(i)] = buf[i] / 32768.0f;
    }

    env->ReleaseShortArrayElements(jPcm, buf, JNI_ABORT);

    // Set up Whisper params
    struct whisper_full_params wparams =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.print_special    = false;

    wparams.translate        = false;   // just transcribe
    wparams.no_context       = true;    // no previous text as context
    wparams.single_segment   = false;   // allow multiple segments

    int threads = choose_num_threads();
    wparams.n_threads        = threads;

    wparams.offset_ms        = 0;
    wparams.duration_ms      = 0;       // 0 = full audio

    LOGI("nativeProcess: n_samples=%d, using %d threads", n_samples, threads);

    const int ret = whisper_full(
            g_ctx,
            wparams,
            pcm.data(),
            n_samples
    );

    if (ret != 0) {
        LOGE("whisper_full() failed, ret=%d", ret);
        return env->NewStringUTF("");
    }

    // Collect transcription segments
    const int n_segments = whisper_full_n_segments(g_ctx);
    std::ostringstream oss;

    for (int i = 0; i < n_segments; ++i) {
        const char* text = whisper_full_get_segment_text(g_ctx, i);
        if (text && text[0] != '\0') {
            if (i > 0) {
                oss << ' ';
            }
            oss << text;
        }
    }

    const std::string result = oss.str();
    LOGI("nativeProcess: got transcript length=%zu", result.size());

    return env->NewStringUTF(result.c_str());
}
