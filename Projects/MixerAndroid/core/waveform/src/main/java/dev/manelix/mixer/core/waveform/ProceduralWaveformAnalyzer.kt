package dev.manelix.mixer.core.waveform

import dev.manelix.mixer.core.waveform.model.WaveformProgress
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Phase-6 waveform pipeline placeholder.
 * Generates deterministic pseudo-waveform data from URI with progressive callbacks.
 */
class ProceduralWaveformAnalyzer : WaveformAnalyzer {
    override fun generateWaveform(
        sourceUri: String,
        sampleCount: Int,
        onProgress: (WaveformProgress) -> Unit,
    ): FloatArray {
        require(sampleCount > 0) { "sampleCount must be > 0" }

        val random = Random(sourceUri.hashCode())
        val buckets = FloatArray(sampleCount)
        var runningMax = 0.000001f
        val phaseA = random.nextDouble(0.0, Math.PI * 2.0)
        val phaseB = random.nextDouble(0.0, Math.PI * 2.0)
        val phaseC = random.nextDouble(0.0, Math.PI * 2.0)
        val punch = random.nextDouble(0.08, 0.36)

        var index = 0
        while (index < sampleCount) {
            val x = index.toDouble() / max(sampleCount - 1, 1)
            val low = abs(sin((x * 5.5) + phaseA)) * 0.52
            val mid = abs(sin((x * 17.0) + phaseB)) * 0.33
            val high = abs(sin((x * 71.0) + phaseC)) * punch
            val shaped = ((low + mid + high) * random.nextDouble(0.88, 1.12)).coerceIn(0.0, 1.0)
            val sample = shaped.pow(0.95).toFloat()
            buckets[index] = sample
            runningMax = max(runningMax, sample)
            if ((index + 1) % 64 == 0 || index == sampleCount - 1) {
                val normalizedSnapshot = FloatArray(sampleCount)
                for (i in 0..index) {
                    normalizedSnapshot[i] = (buckets[i] / runningMax).coerceIn(0.0f, 1.0f)
                }
                onProgress(
                    WaveformProgress(
                        samples = normalizedSnapshot,
                        completedBuckets = index + 1,
                        totalBuckets = sampleCount,
                    ),
                )
            }
            index += 1
        }

        val floorAndScale = normalizationWindow(buckets)
        val normalized = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            normalized[i] = ((buckets[i] - floorAndScale.floor) / floorAndScale.scale).coerceIn(0.0f, 1.0f)
        }
        onProgress(
            WaveformProgress(
                samples = normalized.copyOf(),
                completedBuckets = sampleCount,
                totalBuckets = sampleCount,
            ),
        )
        return normalized
    }

    private fun normalizationWindow(samples: FloatArray): FloorScale {
        if (samples.isEmpty()) return FloorScale(0.0f, 1.0f)
        val sorted = samples.copyOf().apply { sort() }
        val floorIndex = ((sorted.size - 1) * 0.10).toInt().coerceIn(0, sorted.size - 1)
        val ceilingIndex = ((sorted.size - 1) * 0.985).toInt().coerceIn(floorIndex, sorted.size - 1)
        val floor = sorted[floorIndex]
        val ceiling = sorted[ceilingIndex]
        return FloorScale(floor, max(ceiling - floor, 0.000001f))
    }
}

private data class FloorScale(
    val floor: Float,
    val scale: Float,
)
