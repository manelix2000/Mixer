package dev.manelix.mixer.core.dsp

internal object NativeTempoBridge {
    private const val NativeLibraryName = "mixer_aubio_jni"

    val isNativeLibraryLoaded: Boolean by lazy {
        runCatching {
            System.loadLibrary(NativeLibraryName)
            true
        }.getOrDefault(false)
    }

    fun isBackendReady(): Boolean {
        if (!isNativeLibraryLoaded) return false
        return runCatching { nativeIsAubioLinked() }.getOrDefault(false)
    }

    external fun nativeIsAubioLinked(): Boolean

    external fun nativeDetectTempo(
        samples: FloatArray,
        sampleRate: Double,
        method: String,
        windowSize: Int,
        hopSize: Int,
    ): DoubleArray?
}
