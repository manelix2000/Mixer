#include <jni.h>

#include <cmath>

namespace {
constexpr int kNativeResultSize = 2;
constexpr int kBpmIndex = 0;
constexpr int kConfidenceIndex = 1;
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
    (void)samples;
    (void)sampleRate;
    (void)method;
    (void)windowSize;
    (void)hopSize;

    jdoubleArray output = env->NewDoubleArray(kNativeResultSize);
    if (output == nullptr) {
        return nullptr;
    }

    jdouble values[kNativeResultSize] = {NAN, 0.0};
#if MIXER_AUBIO_ENABLED
    // Phase 9 only wires the native bridge and static aubio linkage.
    // Real tempo processing is integrated in Phase 10.
    values[kBpmIndex] = NAN;
    values[kConfidenceIndex] = 0.0;
#endif
    env->SetDoubleArrayRegion(output, 0, kNativeResultSize, values);
    return output;
}
