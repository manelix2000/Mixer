package dev.manelix.mixer.core.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dev.manelix.mixer.core.waveform.model.WaveformProgress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates waveform data from decoded track audio.
 * Falls back to deterministic procedural data when decoding is unavailable.
 */
class ProceduralWaveformAnalyzer : WaveformAnalyzer {
    override fun generateWaveform(
        sourceUri: String,
        sampleCount: Int,
        appContext: Context?,
        onProgress: (WaveformProgress) -> Unit,
    ): FloatArray {
        require(sampleCount > 0) { "sampleCount must be > 0" }

        val decoded = appContext?.let {
            runCatching { generateDecodedWaveform(it, sourceUri, sampleCount, onProgress) }.getOrNull()
        }
        if (decoded != null && decoded.isNotEmpty()) return decoded
        return generateProceduralWaveform(sourceUri, sampleCount, onProgress)
    }

    private fun generateDecodedWaveform(
        appContext: Context,
        sourceUri: String,
        sampleCount: Int,
        onProgress: (WaveformProgress) -> Unit,
    ): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (!setExtractorDataSource(extractor, appContext, sourceUri)) {
                return FloatArray(0)
            }
            val trackIndex = selectAudioTrack(extractor) ?: return FloatArray(0)
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val peaks = decodeToWindowPeaks(codec, extractor)
            if (peaks.isEmpty()) return FloatArray(0)

            val rawBuckets = maxPoolToSampleCount(peaks, sampleCount)
            val normalized = normalizeBuckets(rawBuckets)
            onProgress(
                WaveformProgress(
                    samples = normalized.copyOf(),
                    completedBuckets = sampleCount,
                    totalBuckets = sampleCount,
                ),
            )
            return normalized
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun setExtractorDataSource(
        extractor: MediaExtractor,
        appContext: Context,
        sourceUri: String,
    ): Boolean {
        val parsedUri = runCatching { Uri.parse(sourceUri) }.getOrNull()
        val withContext = parsedUri?.let { uri ->
            runCatching { extractor.setDataSource(appContext, uri, null) }.isSuccess
        } ?: false
        if (withContext) return true
        return runCatching { extractor.setDataSource(sourceUri) }.isSuccess
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return index
            }
        }
        return null
    }

    private fun decodeToWindowPeaks(
        codec: MediaCodec,
        extractor: MediaExtractor,
    ): FloatArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val peaks = ArrayList<Float>(2048)
        var inputDone = false
        var outputDone = false
        var channelCount = 1
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        var windowPeak = 0f
        var samplesInWindow = 0
        val windowSize = 512

        fun appendAmplitude(amplitude: Float) {
            if (amplitude > windowPeak) windowPeak = amplitude
            samplesInWindow += 1
            if (samplesInWindow >= windowSize) {
                peaks.add(windowPeak.coerceIn(0f, 1f))
                windowPeak = 0f
                samplesInWindow = 0
            }
        }

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            when (outputIndex) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    channelCount = outputFormat.getIntegerOrElse(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
                    pcmEncoding = outputFormat.getIntegerOrElse(
                        MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                }
                else -> {
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val slice = outputBuffer.duplicate().apply {
                                position(bufferInfo.offset)
                                limit(bufferInfo.offset + bufferInfo.size)
                                order(ByteOrder.LITTLE_ENDIAN)
                            }
                            consumePcm(slice, channelCount, pcmEncoding, ::appendAmplitude)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        }

        if (samplesInWindow > 0) peaks.add(windowPeak.coerceIn(0f, 1f))
        return peaks.toFloatArray()
    }

    private fun consumePcm(
        buffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
        onAmplitude: (Float) -> Unit,
    ) {
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                while (buffer.remaining() >= 4 * channelCount) {
                    var sum = 0f
                    for (channel in 0 until channelCount) {
                        sum += abs(buffer.float.coerceIn(-1f, 1f))
                    }
                    onAmplitude((sum / channelCount.toFloat()).coerceIn(0f, 1f))
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (buffer.remaining() >= channelCount) {
                    var sum = 0f
                    for (channel in 0 until channelCount) {
                        val unsigned = buffer.get().toInt() and 0xFF
                        val normalized = ((unsigned - 128) / 128f).coerceIn(-1f, 1f)
                        sum += abs(normalized)
                    }
                    onAmplitude((sum / channelCount.toFloat()).coerceIn(0f, 1f))
                }
            }
            else -> {
                while (buffer.remaining() >= 2 * channelCount) {
                    var sum = 0f
                    for (channel in 0 until channelCount) {
                        sum += abs((buffer.short / 32768f).coerceIn(-1f, 1f))
                    }
                    onAmplitude((sum / channelCount.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
    }

    private fun maxPoolToSampleCount(
        peaks: FloatArray,
        sampleCount: Int,
    ): FloatArray {
        if (peaks.isEmpty()) return FloatArray(sampleCount)
        val output = FloatArray(sampleCount)
        for (bucket in 0 until sampleCount) {
            val start = (bucket.toLong() * peaks.size.toLong() / sampleCount.toLong()).toInt().coerceIn(0, peaks.lastIndex)
            val exclusiveEnd = (((bucket + 1).toLong() * peaks.size.toLong() / sampleCount.toLong()).toInt())
                .coerceIn(start + 1, peaks.size)
            var localMax = 0f
            for (index in start until exclusiveEnd) {
                if (peaks[index] > localMax) localMax = peaks[index]
            }
            output[bucket] = localMax
        }
        return output
    }

    private fun normalizeBuckets(samples: FloatArray): FloatArray {
        val floorAndScale = normalizationWindow(samples)
        val normalized = FloatArray(samples.size)
        for (i in samples.indices) {
            normalized[i] = ((samples[i] - floorAndScale.floor) / floorAndScale.scale).coerceIn(0.0f, 1.0f)
        }
        return normalized
    }

    private fun generateProceduralWaveform(
        sourceUri: String,
        sampleCount: Int,
        onProgress: (WaveformProgress) -> Unit,
    ): FloatArray {
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
        return normalizeBuckets(buckets)
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

private fun MediaFormat.getIntegerOrElse(
    key: String,
    fallback: Int,
): Int = runCatching { getInteger(key) }.getOrDefault(fallback)
