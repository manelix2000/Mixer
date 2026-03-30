#include <jni.h>

#include <algorithm>
#include <cmath>
#include <vector>

#if MIXER_AUBIO_ENABLED
#include <aubio.h>
#endif

namespace {
constexpr int kNativeResultSize = 2;
constexpr int kBpmIndex = 0;
constexpr int kConfidenceIndex = 1;
constexpr uint_t kOutputSize = 1;
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_manelix_mixer_core_dsp_NativeTempoBridge_nativeIsAubioLinked(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
#if MIXER_AUBIO_ENABLED
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_manelix_mixer_core_dsp_NativeTempoBridge_nativeDetectTempo(
    JNIEnv* env,
    jobject thiz,
    jfloatArray samples,
    jdouble sampleRate,
    jstring method,
    jint windowSize,
    jint hopSize
) {
    (void)thiz;
    jdoubleArray output = env->NewDoubleArray(kNativeResultSize);
    if (output == nullptr) {
        return nullptr;
    }

    jdouble values[kNativeResultSize] = {NAN, 0.0};

#if MIXER_AUBIO_ENABLED
    if (samples == nullptr || sampleRate <= 0.0 || windowSize <= 0 || hopSize <= 0) {
        env->SetDoubleArrayRegion(output, 0, kNativeResultSize, values);
        return output;
    }

    const char* methodChars = method != nullptr ? env->GetStringUTFChars(method, nullptr) : nullptr;
    const char* detectorMethod = (methodChars != nullptr && methodChars[0] != '\0') ? methodChars : "default";

    aubio_tempo_t* tempo = new_aubio_tempo(detectorMethod, static_cast<uint_t>(windowSize), static_cast<uint_t>(hopSize), static_cast<uint_t>(sampleRate));
    if (tempo == nullptr) {
        if (methodChars != nullptr) env->ReleaseStringUTFChars(method, methodChars);
        env->SetDoubleArrayRegion(output, 0, kNativeResultSize, values);
        return output;
    }

    jsize sampleCount = env->GetArrayLength(samples);
    std::vector<jfloat> input(static_cast<size_t>(sampleCount));
    env->GetFloatArrayRegion(samples, 0, sampleCount, input.data());

    fvec_t* inBuffer = new_fvec(static_cast<uint_t>(hopSize));
    fvec_t* outBuffer = new_fvec(kOutputSize);
    if (inBuffer == nullptr || outBuffer == nullptr) {
        if (inBuffer != nullptr) del_fvec(inBuffer);
        if (outBuffer != nullptr) del_fvec(outBuffer);
        del_aubio_tempo(tempo);
        if (methodChars != nullptr) env->ReleaseStringUTFChars(method, methodChars);
        env->SetDoubleArrayRegion(output, 0, kNativeResultSize, values);
        return output;
    }

    uint_t cursor = 0;
    while (cursor < static_cast<uint_t>(sampleCount)) {
        for (uint_t i = 0; i < static_cast<uint_t>(hopSize); ++i) {
            const uint_t index = cursor + i;
            const jfloat sample = index < static_cast<uint_t>(sampleCount) ? input[index] : 0.0f;
            fvec_set_sample(inBuffer, static_cast<smpl_t>(sample), i);
        }
        aubio_tempo_do(tempo, inBuffer, outBuffer);
        cursor += static_cast<uint_t>(hopSize);
    }

    const smpl_t bpm = aubio_tempo_get_bpm(tempo);
    const smpl_t confidence = aubio_tempo_get_confidence(tempo);
    if (std::isfinite(bpm) && bpm > 0.0f) {
        values[kBpmIndex] = static_cast<jdouble>(bpm);
        values[kConfidenceIndex] = std::clamp(static_cast<jdouble>(confidence), 0.0, 1.0);
    }

    del_fvec(inBuffer);
    del_fvec(outBuffer);
    del_aubio_tempo(tempo);
    if (methodChars != nullptr) env->ReleaseStringUTFChars(method, methodChars);
#endif

    env->SetDoubleArrayRegion(output, 0, kNativeResultSize, values);
    return output;
}
