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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Generates waveform data from decoded track audio.
 */
class ProceduralWaveformAnalyzer : WaveformAnalyzer {
    override fun generateWaveform(
        sourceUri: String,
        sampleCount: Int,
        appContext: Context?,
        onProgress: (WaveformProgress) -> Unit,
    ): FloatArray {
        require(sampleCount > 0) { "sampleCount must be > 0" }

        return appContext?.let {
            runCatching { generateDecodedWaveform(it, sourceUri, sampleCount, onProgress) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: FloatArray(0)
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
            val sampleRateHz = inputFormat.getIntegerOrElse(MediaFormat.KEY_SAMPLE_RATE, 44_100).coerceAtLeast(1)
            val durationUs = inputFormat.getLongOrElse(MediaFormat.KEY_DURATION, 0L).coerceAtLeast(0L)
            val estimatedTotalFrames = if (durationUs > 0L) {
                ((durationUs / 1_000_000.0) * sampleRateHz.toDouble()).toLong().coerceAtLeast(1L)
            } else {
                sampleCount.toLong()
            }
            val framesPerBucket = max(ceil(estimatedTotalFrames.toDouble() / sampleCount.toDouble()).toInt(), 1)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val rawBuckets = decodeToBuckets(codec, extractor, sampleCount, framesPerBucket, onProgress)
            if (rawBuckets.isEmpty()) return FloatArray(0)
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

    private fun decodeToBuckets(
        codec: MediaCodec,
        extractor: MediaExtractor,
        sampleCount: Int,
        framesPerBucket: Int,
        onProgress: (WaveformProgress) -> Unit,
    ): FloatArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val buckets = FloatArray(sampleCount)
        var bucketIndex = 0
        var runningMax = 0.000001f
        var inputDone = false
        var outputDone = false
        var channelCount = 1
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        var currentBucketPeak = 0f
        var currentBucketSquareSum = 0f
        var framesInBucket = 0

        fun appendAmplitude(amplitude: Float) {
            if (bucketIndex >= sampleCount) return
            if (amplitude > currentBucketPeak) currentBucketPeak = amplitude
            currentBucketSquareSum += amplitude * amplitude
            framesInBucket += 1
            if (framesInBucket >= framesPerBucket) {
                val bucketValue = finalizeBucketValue(
                    peak = currentBucketPeak,
                    squareSum = currentBucketSquareSum,
                    frameCount = framesInBucket,
                )
                buckets[bucketIndex] = bucketValue
                runningMax = max(runningMax, bucketValue)
                bucketIndex += 1
                currentBucketPeak = 0f
                currentBucketSquareSum = 0f
                framesInBucket = 0

                if (bucketIndex % 64 == 0 || bucketIndex == sampleCount) {
                    onProgress(
                        WaveformProgress(
                            samples = makeProgressSnapshot(
                                buckets = buckets,
                                completed = bucketIndex,
                                sampleCount = sampleCount,
                                runningMax = runningMax,
                            ),
                            completedBuckets = bucketIndex,
                            totalBuckets = sampleCount,
                        ),
                    )
                }
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

        if (framesInBucket > 0 && bucketIndex < sampleCount) {
            val bucketValue = finalizeBucketValue(
                peak = currentBucketPeak,
                squareSum = currentBucketSquareSum,
                frameCount = framesInBucket,
            )
            buckets[bucketIndex] = bucketValue
            runningMax = max(runningMax, bucketValue)
            bucketIndex += 1
        }
        if (bucketIndex <= 0) return FloatArray(0)
        return buckets
    }

    private fun normalizeBuckets(samples: FloatArray): FloatArray {
        val floorAndScale = normalizationWindow(samples)
        val normalized = FloatArray(samples.size)
        for (i in samples.indices) {
            normalized[i] = ((samples[i] - floorAndScale.floor) / floorAndScale.scale).coerceIn(0.0f, 1.0f)
        }
        return normalized
    }

    private fun finalizeBucketValue(
        peak: Float,
        squareSum: Float,
        frameCount: Int,
    ): Float {
        if (frameCount <= 0) return 0f
        val rms = sqrt(squareSum / frameCount.toFloat())
        val blended = (rms * 0.82f) + (peak * 0.18f)
        return max(blended, 0f).pow(0.95f)
    }

    private fun makeProgressSnapshot(
        buckets: FloatArray,
        completed: Int,
        sampleCount: Int,
        runningMax: Float,
    ): FloatArray {
        val normalization = max(runningMax, 0.000001f)
        val snapshot = FloatArray(sampleCount)
        val safeCompleted = completed.coerceIn(0, sampleCount)
        for (i in 0 until safeCompleted) {
            snapshot[i] = (buckets[i] / normalization).coerceIn(0f, 1f)
        }
        return snapshot
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

private fun MediaFormat.getLongOrElse(
    key: String,
    fallback: Long,
): Long = runCatching { getLong(key) }.getOrDefault(fallback)
