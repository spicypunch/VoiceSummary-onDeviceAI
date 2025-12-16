#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_kr_jm_voicesummary_core_whisper_WhisperLib_initContext(
    JNIEnv *env,
    jobject thiz,
    jstring model_path_str
) {
    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Loading model from: %s", model_path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *context = whisper_init_from_file_with_params(model_path, cparams);

    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (context == nullptr) {
        LOGE("Failed to load model");
        return 0;
    }

    LOGI("Model loaded successfully");
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_kr_jm_voicesummary_core_whisper_WhisperLib_freeContext(
    JNIEnv *env,
    jobject thiz,
    jlong context_ptr
) {
    auto *context = (struct whisper_context *) context_ptr;
    if (context != nullptr) {
        whisper_free(context);
        LOGI("Context freed");
    }
}

JNIEXPORT jstring JNICALL
Java_kr_jm_voicesummary_core_whisper_WhisperLib_transcribe(
    JNIEnv *env,
    jobject thiz,
    jlong context_ptr,
    jfloatArray audio_data,
    jstring language_str
) {
    auto *context = (struct whisper_context *) context_ptr;
    if (context == nullptr) {
        LOGE("Context is null");
        return env->NewStringUTF("");
    }

    // Get audio data
    jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
    jsize audio_length = env->GetArrayLength(audio_data);

    // Get language
    const char *language = env->GetStringUTFChars(language_str, nullptr);

    LOGI("Transcribing %d samples, language: %s", audio_length, language);

    // Setup parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language;
    params.n_threads = 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    // Run transcription
    int result = whisper_full(context, params, audio, audio_length);

    env->ReleaseStringUTFChars(language_str, language);
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    // Get result text
    std::string text;
    int n_segments = whisper_full_n_segments(context);
    for (int i = 0; i < n_segments; i++) {
        const char *segment_text = whisper_full_get_segment_text(context, i);
        text += segment_text;
    }

    LOGI("Transcription complete: %d segments", n_segments);
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jboolean JNICALL
Java_kr_jm_voicesummary_core_whisper_WhisperLib_isModelLoaded(
    JNIEnv *env,
    jobject thiz,
    jlong context_ptr
) {
    return context_ptr != 0;
}

JNIEXPORT jstring JNICALL
Java_kr_jm_voicesummary_core_whisper_WhisperLib_getSystemInfo(
    JNIEnv *env,
    jobject thiz
) {
    const char *sysinfo = whisper_print_system_info();
    return env->NewStringUTF(sysinfo);
}

}
