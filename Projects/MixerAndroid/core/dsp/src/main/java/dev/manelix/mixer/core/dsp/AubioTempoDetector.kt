package dev.manelix.mixer.core.dsp

import dev.manelix.mixer.core.dsp.model.BpmResult
import dev.manelix.mixer.core.dsp.model.TempoDetectorConfiguration
import dev.manelix.mixer.core.dsp.model.TempoInputBuffer

class AubioTempoDetector(
    private val configuration: TempoDetectorConfiguration = TempoDetectorConfiguration(),
) : TempoDetector {
    override fun detectTempo(input: TempoInputBuffer): BpmResult {
        if (input.samples.isEmpty()) {
            return BpmResult.Unavailable(reason = "Tempo detection failed: empty input buffer.")
        }
        if (input.sampleRate <= 0.0) {
            return BpmResult.Unavailable(reason = "Tempo detection failed: invalid sample rate.")
        }
        if (input.channelCount <= 0) {
            return BpmResult.Unavailable(reason = "Tempo detection failed: invalid channel count.")
        }
        if (configuration.windowSize <= 0 || configuration.hopSize <= 0) {
            return BpmResult.Unavailable(reason = "Tempo detection failed: invalid detector configuration.")
        }

        if (!NativeTempoBridge.isBackendReady()) {
            return BpmResult.Unavailable(
                reason = "Tempo detection unavailable: aubio backend is not linked in this build.",
            )
        }

        val monoSamples = downmixToMono(input)
        if (monoSamples.isEmpty()) {
            return BpmResult.Unavailable(reason = "Tempo detection failed: no samples after downmix.")
        }

        val nativeResult = runCatching {
            NativeTempoBridge.nativeDetectTempo(
                samples = monoSamples,
                sampleRate = input.sampleRate,
                method = configuration.method,
                windowSize = configuration.windowSize,
                hopSize = configuration.hopSize,
            )
        }.getOrNull()

        val bpm = nativeResult?.getOrNull(0) ?: Double.NaN
        val confidence = nativeResult?.getOrNull(1) ?: Double.NaN

        return if (bpm.isFinite() && bpm > 0.0) {
            BpmResult.Detected(
                bpm = bpm,
                confidence = confidence.coerceIn(0.0, 1.0).takeIf(Double::isFinite) ?: 0.0,
            )
        } else {
            BpmResult.Unavailable(
                reason = "Tempo detection unavailable: aubio backend linked, processing wired in phase 10.",
            )
        }
    }

    private fun downmixToMono(input: TempoInputBuffer): FloatArray {
        if (input.channelCount == 1) return input.samples

        val frameCount = input.samples.size / input.channelCount
        if (frameCount <= 0) return FloatArray(0)

        val mono = FloatArray(frameCount)
        if (input.isInterleaved) {
            for (frame in 0 until frameCount) {
                var sum = 0f
                val base = frame * input.channelCount
                for (channel in 0 until input.channelCount) {
                    sum += input.samples[base + channel]
                }
                mono[frame] = sum / input.channelCount.toFloat()
            }
        } else {
            for (frame in 0 until frameCount) {
                var sum = 0f
                for (channel in 0 until input.channelCount) {
                    val index = channel * frameCount + frame
                    sum += input.samples[index]
                }
                mono[frame] = sum / input.channelCount.toFloat()
            }
        }
        return mono
    }
}
