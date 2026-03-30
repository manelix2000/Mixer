package dev.manelix.mixer.core.dsp

import dev.manelix.mixer.core.dsp.model.BpmResult
import dev.manelix.mixer.core.dsp.model.TempoInputBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight pure-Kotlin fallback detector used when native aubio is unavailable.
 * It estimates BPM from envelope autocorrelation in the 60..200 BPM range.
 */
class HeuristicTempoDetector : TempoDetector {
    override fun detectTempo(input: TempoInputBuffer): BpmResult {
        if (input.samples.isEmpty()) {
            return BpmResult.Unavailable(reason = "Heuristic detector: empty input")
        }
        if (input.sampleRate <= 0.0 || input.channelCount <= 0) {
            return BpmResult.Unavailable(reason = "Heuristic detector: invalid input format")
        }

        val mono = downmixToMono(input)
        if (mono.size < 2_048) {
            return BpmResult.Unavailable(reason = "Heuristic detector: not enough samples")
        }

        val envelopeData = buildEnvelope(mono, input.sampleRate)
        val envelope = envelopeData.values
        if (envelope.size < 128) {
            return BpmResult.Unavailable(reason = "Heuristic detector: envelope too short")
        }

        val minBpm = 60.0
        val maxBpm = 200.0
        val envRate = envelopeData.envelopeSampleRate
        val minLag = max(1, (envRate * 60.0 / maxBpm).toInt())
        val maxLag = min(envelope.lastIndex, (envRate * 60.0 / minBpm).toInt())
        if (maxLag <= minLag) {
            return BpmResult.Unavailable(reason = "Heuristic detector: lag window invalid")
        }

        val centered = center(envelope)
        var bestLag = -1
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY

        for (lag in minLag..maxLag) {
            var corr = 0.0
            var normA = 0.0
            var normB = 0.0
            var i = 0
            while (i + lag < centered.size) {
                val a = centered[i]
                val b = centered[i + lag]
                corr += a * b
                normA += a * a
                normB += b * b
                i += 1
            }
            if (normA <= 1e-9 || normB <= 1e-9) continue
            val score = corr / kotlin.math.sqrt(normA * normB)
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestLag = lag
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        if (bestLag <= 0 || !bestScore.isFinite()) {
            return BpmResult.Unavailable(reason = "Heuristic detector: no stable tempo")
        }

        val bpm = (60.0 * envRate / bestLag).coerceIn(minBpm, maxBpm)
        val margin = if (secondScore.isFinite()) (bestScore - secondScore).coerceAtLeast(0.0) else bestScore
        val confidence = (bestScore * 0.75 + margin * 0.25).coerceIn(0.0, 1.0)
        return BpmResult.Detected(bpm = bpm, confidence = confidence)
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
                for (ch in 0 until input.channelCount) {
                    sum += input.samples[base + ch]
                }
                mono[frame] = sum / input.channelCount.toFloat()
            }
        } else {
            for (frame in 0 until frameCount) {
                var sum = 0f
                for (ch in 0 until input.channelCount) {
                    sum += input.samples[ch * frameCount + frame]
                }
                mono[frame] = sum / input.channelCount.toFloat()
            }
        }
        return mono
    }

    private fun buildEnvelope(
        samples: FloatArray,
        sampleRate: Double,
    ): EnvelopeData {
        val targetRate = 200.0
        val window = max(32, (sampleRate / targetRate).toInt())
        val count = samples.size / window
        if (count <= 0) return EnvelopeData(values = doubleArrayOf(), envelopeSampleRate = targetRate)
        val envelope = DoubleArray(count)
        var cursor = 0
        for (i in 0 until count) {
            var acc = 0.0
            for (j in 0 until window) {
                acc += abs(samples[cursor + j].toDouble())
            }
            envelope[i] = acc / window
            cursor += window
        }
        return EnvelopeData(
            values = envelope,
            envelopeSampleRate = sampleRate / window,
        )
    }

    private fun center(values: DoubleArray): DoubleArray {
        if (values.isEmpty()) return values
        val mean = values.sum() / values.size
        return DoubleArray(values.size) { index -> values[index] - mean }
    }

    private data class EnvelopeData(
        val values: DoubleArray,
        val envelopeSampleRate: Double,
    )
}
