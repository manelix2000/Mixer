package dev.manelix.mixer.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import dev.manelix.mixer.core.audio.model.AudioEngineError
import dev.manelix.mixer.core.audio.model.AudioPerformanceMetrics
import dev.manelix.mixer.core.audio.model.MicrophoneCaptureFrame
import dev.manelix.mixer.core.common.model.AudioPlaybackState
import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckRole
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * Phase-4 Android playback skeleton that mirrors the iOS controller contract/state transitions.
 * Real decoding/output routing is added in later phases.
 */
class SkeletonAudioEngineController(
    private val appContext: Context? = null,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val defaultDurationSeconds: Double = 180.0,
) : AudioEngineController, AudioEngineRoutingProvider, AudioEnginePerformanceProvider {
    private val stateLock = ReentrantLock()

    private var running = false
    private var loadedSourceUri: String? = null
    private var internalPlaybackState: AudioPlaybackState = AudioPlaybackState.IDLE
    private var lastKnownCurrentTimeSeconds = 0.0
    private var internalTotalDurationSeconds = 0.0
    private var internalVolume = 1.0f
    private var internalPan = 0.0f
    private var internalPlaybackRate = 1.0f
    private var internalSplitDeckRole: SplitDeckRole? = null
    private var internalPanControlRange: PanControlRange = PanControlRange.Standard
    private var isScratchModeActive = false
    private var scratchWasPlayingBeforeGesture = false
    private var isMicCaptureRunning = false
    private var micCallback: ((MicrophoneCaptureFrame) -> Unit)? = null
    private var micSimulationThread: Thread? = null
    private var micFramesProcessed: Long = 0L
    private var micCallbacksOverBudget: Long = 0L
    private var micCallbackTotalNanos: Long = 0L
    private var micCallbackMaxNanos: Long = 0L
    private var micCallbackLastNanos: Long = 0L
    private var playbackStartOffsetSeconds = 0.0
    private var playbackStartMonotonicNanos = 0L
    private var mediaPlayer: MediaPlayer? = null

    override val isRunning: Boolean
        get() = stateLock.withLock { running }

    override val playbackState: AudioPlaybackState
        get() = stateLock.withLock { internalPlaybackState }

    override val currentTimeSeconds: Double
        get() = stateLock.withLock { resolvedCurrentTimeLocked() }

    override val totalDurationSeconds: Double
        get() = stateLock.withLock { internalTotalDurationSeconds }

    override val volume: Float
        get() = stateLock.withLock { internalVolume }

    override val pan: Float
        get() = stateLock.withLock { internalPan }

    override val playbackRate: Float
        get() = stateLock.withLock { internalPlaybackRate }

    override val splitDeckRole: SplitDeckRole?
        get() = stateLock.withLock { internalSplitDeckRole }

    override val panControlRange: PanControlRange
        get() = stateLock.withLock { internalPanControlRange }

    override val isMicrophoneCaptureRunning: Boolean
        get() = stateLock.withLock { isMicCaptureRunning }

    override fun startEngine(): Result<Unit> = stateLock.withLock {
        running = true
        Result.success(Unit)
    }

    override fun stopEngine() {
        stateLock.withLock {
            if (!running) {
                return
            }
            mediaPlayer?.runCatching { pause() }
            lastKnownCurrentTimeSeconds = resolvedCurrentTimeLocked()
            running = false
            internalPlaybackState = if (loadedSourceUri == null) {
                AudioPlaybackState.IDLE
            } else {
                AudioPlaybackState.PAUSED
            }
        }
    }

    override fun loadFile(sourceUri: String): Result<Unit> = stateLock.withLock {
        if (sourceUri.isBlank()) {
            return failure(AudioEngineError.FileLoadFailed("Blank source URI"))
        }

        val playerResult = prepareMediaPlayerLocked(sourceUri)
        if (playerResult.isFailure) {
            return failure(
                AudioEngineError.FileLoadFailed(
                    playerResult.exceptionOrNull()?.message ?: "Unable to load media source.",
                ),
            )
        }

        loadedSourceUri = sourceUri
        playerResult.getOrNull()?.runCatching {
            if (isPlaying) {
                pause()
            }
            seekTo(0)
        }
        val resolvedDuration = playerResult.getOrNull()
            ?.duration
            ?.takeIf { it > 0 }
            ?.let { it / 1000.0 }
            ?: defaultDurationSeconds
        internalTotalDurationSeconds = max(1.0, resolvedDuration)
        playbackStartOffsetSeconds = 0.0
        lastKnownCurrentTimeSeconds = 0.0
        isScratchModeActive = false
        scratchWasPlayingBeforeGesture = false
        internalPlaybackState = AudioPlaybackState.PAUSED
        Result.success(Unit)
    }

    override fun play(): Result<Unit> = stateLock.withLock {
        if (loadedSourceUri == null) {
            return failure(AudioEngineError.NoFileLoaded)
        }

        running = true
        val player = mediaPlayer
        if (player != null) {
            if (internalTotalDurationSeconds > 0.0 && resolvedCurrentTimeLocked() >= internalTotalDurationSeconds - 0.01) {
                runCatching { player.seekTo(0) }
            }
            val rateResult = runCatching {
                val playbackParams = player.playbackParams
                player.playbackParams = playbackParams.setSpeed(internalPlaybackRate)
            }
            if (rateResult.isFailure) {
                return failure(AudioEngineError.StartFailed("Unable to set playback speed"))
            }
            val startResult = runCatching { player.start() }
            if (startResult.isFailure) {
                return failure(AudioEngineError.StartFailed("Unable to start playback"))
            }
            internalPlaybackState = AudioPlaybackState.PLAYING
            return Result.success(Unit)
        }

        val current = resolvedCurrentTimeLocked()
        if (internalTotalDurationSeconds > 0.0 && current >= internalTotalDurationSeconds - 0.01) {
            lastKnownCurrentTimeSeconds = 0.0
            playbackStartOffsetSeconds = 0.0
        }
        startPlaybackClockLocked(lastKnownCurrentTimeSeconds)
        internalPlaybackState = AudioPlaybackState.PLAYING
        Result.success(Unit)
    }

    override fun pause() {
        stateLock.withLock {
            if (loadedSourceUri == null) {
                internalPlaybackState = AudioPlaybackState.IDLE
                return
            }

            mediaPlayer?.runCatching {
                if (isPlaying) {
                    pause()
                }
            }
            lastKnownCurrentTimeSeconds = resolvedCurrentTimeLocked()
            playbackStartOffsetSeconds = lastKnownCurrentTimeSeconds
            internalPlaybackState = AudioPlaybackState.PAUSED
        }
    }

    override fun seekTo(timeSeconds: Double): Result<Unit> = stateLock.withLock {
        if (loadedSourceUri == null) {
            return failure(AudioEngineError.NoFileLoaded)
        }

        val clamped = clampTime(timeSeconds)
        lastKnownCurrentTimeSeconds = clamped
        mediaPlayer?.runCatching { seekTo((clamped * 1000.0).toInt()) }
        if (internalPlaybackState == AudioPlaybackState.PLAYING) {
            startPlaybackClockLocked(clamped)
        } else {
            playbackStartOffsetSeconds = clamped
        }
        Result.success(Unit)
    }

    override fun beginScratch(): Result<Unit> = stateLock.withLock {
        if (loadedSourceUri == null) {
            return failure(AudioEngineError.NoFileLoaded)
        }

        running = true
        if (!isScratchModeActive) {
            scratchWasPlayingBeforeGesture = internalPlaybackState == AudioPlaybackState.PLAYING
        }
        lastKnownCurrentTimeSeconds = resolvedCurrentTimeLocked()
        playbackStartOffsetSeconds = lastKnownCurrentTimeSeconds
        isScratchModeActive = true
        internalPlaybackState = AudioPlaybackState.PAUSED
        Result.success(Unit)
    }

    override fun scratchTo(
        timeSeconds: Double,
        angularVelocity: Double,
    ): Result<Unit> = stateLock.withLock {
        if (loadedSourceUri == null) {
            return failure(AudioEngineError.NoFileLoaded)
        }
        if (!isScratchModeActive) {
            return failure(AudioEngineError.ScratchNotActive)
        }

        val clamped = clampTime(timeSeconds)
        lastKnownCurrentTimeSeconds = clamped
        playbackStartOffsetSeconds = clamped
        mediaPlayer?.runCatching { seekTo((clamped * 1000.0).toInt()) }
        internalPlaybackState = if (angularVelocity == 0.0) {
            mediaPlayer?.runCatching { pause() }
            AudioPlaybackState.PAUSED
        } else {
            mediaPlayer?.runCatching { start() }
            startPlaybackClockLocked(clamped)
            AudioPlaybackState.PLAYING
        }
        Result.success(Unit)
    }

    override fun endScratch(resumePlayback: Boolean): Result<Unit> = stateLock.withLock {
        if (loadedSourceUri == null) {
            return failure(AudioEngineError.NoFileLoaded)
        }

        val finalTime = if (isScratchModeActive) {
            lastKnownCurrentTimeSeconds
        } else {
            resolvedCurrentTimeLocked()
        }
        isScratchModeActive = false
        val shouldResume = resumePlayback && scratchWasPlayingBeforeGesture
        scratchWasPlayingBeforeGesture = false
        if (shouldResume) {
            mediaPlayer?.runCatching { start() }
            startPlaybackClockLocked(finalTime)
            internalPlaybackState = AudioPlaybackState.PLAYING
        } else {
            mediaPlayer?.runCatching { pause() }
            lastKnownCurrentTimeSeconds = finalTime
            playbackStartOffsetSeconds = finalTime
            internalPlaybackState = AudioPlaybackState.PAUSED
        }
        Result.success(Unit)
    }

    override fun setVolume(value: Float) {
        stateLock.withLock {
            internalVolume = clampVolume(value)
            applyVolumeAndPanLocked()
        }
    }

    override fun setPan(value: Float) {
        stateLock.withLock {
            internalPan = clampPanForRange(value, internalPanControlRange)
            applyVolumeAndPanLocked()
        }
    }

    fun setRoutingPolicy(
        role: SplitDeckRole?,
        panRange: PanControlRange,
    ) {
        stateLock.withLock {
            internalSplitDeckRole = role
            internalPanControlRange = panRange
            internalPan = clampPanForRange(internalPan, panRange)
            applyVolumeAndPanLocked()
        }
    }

    override fun setPlaybackRate(value: Float) {
        stateLock.withLock {
            val current = resolvedCurrentTimeLocked()
            internalPlaybackRate = clampPlaybackRate(value)
            mediaPlayer?.let { player ->
                runCatching {
                    val playbackParams = player.playbackParams
                    player.playbackParams = playbackParams.setSpeed(internalPlaybackRate)
                }
            }
            if (internalPlaybackState == AudioPlaybackState.PLAYING) {
                startPlaybackClockLocked(current)
            } else {
                lastKnownCurrentTimeSeconds = current
                playbackStartOffsetSeconds = current
            }
        }
    }

    override fun setEqualizer(
        low: Float,
        mid: Float,
        high: Float,
    ) {
        // Stored as clamped values to keep state parity with iOS; no DSP hookup yet.
        stateLock.withLock {
            clampNormalizedEq(low)
            clampNormalizedEq(mid)
            clampNormalizedEq(high)
        }
    }

    override fun startMicrophoneCapture(
        onBuffer: (MicrophoneCaptureFrame) -> Unit,
    ): Result<Unit> = stateLock.withLock {
        running = true
        isMicCaptureRunning = true
        micCallback = onBuffer
        micFramesProcessed = 0L
        micCallbacksOverBudget = 0L
        micCallbackTotalNanos = 0L
        micCallbackMaxNanos = 0L
        micCallbackLastNanos = 0L
        startMicSimulationLocked()
        Result.success(Unit)
    }

    override fun stopMicrophoneCapture() {
        val threadToJoin = stateLock.withLock {
            isMicCaptureRunning = false
            micCallback = null
            val existing = micSimulationThread
            micSimulationThread = null
            existing
        }
        threadToJoin?.join(250L)
    }

    private fun startMicSimulationLocked() {
        if (micSimulationThread?.isAlive == true) return

        micSimulationThread = thread(
            start = true,
            isDaemon = true,
            name = "mixer-mic-sim",
        ) {
            val sampleRate = 44_100.0
            val frameSize = 1_024
            val frameDurationMs = ((frameSize / sampleRate) * 1_000.0).toLong().coerceAtLeast(5L)
            val frameBudgetNanos = (frameDurationMs * 1_000_000L).coerceAtLeast(1_000_000L)
            var sampleCursor = 0L
            val frame = FloatArray(frameSize)
            val reusableCaptureFrame = MicrophoneCaptureFrame(
                samples = frame,
                sampleRate = sampleRate,
                channelCount = 1,
                isInterleaved = false,
            )

            while (true) {
                val callback = stateLock.withLock {
                    if (!isMicCaptureRunning) return@thread
                    micCallback
                }

                if (callback == null) {
                    Thread.sleep(frameDurationMs)
                    continue
                }

                val bpm = 124.0
                val beatIntervalSamples = (sampleRate * 60.0 / bpm).toInt().coerceAtLeast(1)
                for (index in frame.indices) {
                    val absoluteSample = sampleCursor + index
                    val beatPhase = (absoluteSample % beatIntervalSamples).toInt()
                    val clickEnvelope = when {
                        beatPhase < 40 -> (1.0 - (beatPhase / 40.0))
                        else -> 0.0
                    }
                    val carrier = sin((absoluteSample / sampleRate) * Math.PI * 2.0 * 880.0)
                    frame[index] = ((carrier * clickEnvelope) * 0.7).toFloat()
                }
                sampleCursor += frameSize

                val callbackStart = clock.nowMonotonicNanos()
                callback(reusableCaptureFrame)
                val callbackNanos = (clock.nowMonotonicNanos() - callbackStart).coerceAtLeast(0L)
                stateLock.withLock {
                    micFramesProcessed += 1L
                    micCallbackLastNanos = callbackNanos
                    micCallbackTotalNanos += callbackNanos
                    if (callbackNanos > micCallbackMaxNanos) micCallbackMaxNanos = callbackNanos
                    if (callbackNanos > frameBudgetNanos) micCallbacksOverBudget += 1L
                }

                Thread.sleep(frameDurationMs)
            }
        }
    }

    override fun snapshotPerformanceMetrics(): AudioPerformanceMetrics = stateLock.withLock {
        val processed = micFramesProcessed
        val averageMillis = if (processed > 0L) {
            (micCallbackTotalNanos.toDouble() / processed.toDouble()) / 1_000_000.0
        } else {
            0.0
        }
        AudioPerformanceMetrics(
            micFramesProcessed = processed,
            micCallbacksOverBudget = micCallbacksOverBudget,
            micCallbackAverageMillis = averageMillis,
            micCallbackMaxMillis = micCallbackMaxNanos.toDouble() / 1_000_000.0,
            micCallbackLastMillis = micCallbackLastNanos.toDouble() / 1_000_000.0,
        )
    }

    private fun resolvedCurrentTimeLocked(): Double {
        val player = mediaPlayer
        if (player != null) {
            val position = runCatching { player.currentPosition / 1000.0 }
                .getOrDefault(lastKnownCurrentTimeSeconds)
            val duration = internalTotalDurationSeconds
            val clamped = if (duration > 0.0) position.coerceIn(0.0, duration) else max(0.0, position)
            if (duration > 0.0 && clamped >= duration - 0.01) {
                internalPlaybackState = AudioPlaybackState.PAUSED
            }
            lastKnownCurrentTimeSeconds = clamped
            return clamped
        }

        val duration = internalTotalDurationSeconds
        if (internalPlaybackState != AudioPlaybackState.PLAYING || duration <= 0.0) {
            return clampTime(lastKnownCurrentTimeSeconds)
        }

        val elapsedNanos = max(0L, clock.nowMonotonicNanos() - playbackStartMonotonicNanos)
        val elapsedSeconds = (elapsedNanos.toDouble() / 1_000_000_000.0) * internalPlaybackRate
        val resolved = clampTime(playbackStartOffsetSeconds + elapsedSeconds)
        if (resolved >= duration) {
            lastKnownCurrentTimeSeconds = duration
            playbackStartOffsetSeconds = duration
            internalPlaybackState = AudioPlaybackState.PAUSED
            return duration
        }
        lastKnownCurrentTimeSeconds = resolved
        return resolved
    }

    private fun startPlaybackClockLocked(fromSeconds: Double) {
        val clamped = clampTime(fromSeconds)
        lastKnownCurrentTimeSeconds = clamped
        playbackStartOffsetSeconds = clamped
        playbackStartMonotonicNanos = clock.nowMonotonicNanos()
    }

    private fun clampTime(value: Double): Double {
        val duration = internalTotalDurationSeconds
        return if (duration <= 0.0) {
            max(0.0, value)
        } else {
            min(max(0.0, value), duration)
        }
    }

    private fun failure(error: AudioEngineError): Result<Unit> = Result.failure(error.asThrowable())

    private fun clampVolume(value: Float): Float = min(max(value, 0.0f), 1.0f)

    private fun clampPan(value: Float): Float = min(max(value, -1.0f), 1.0f)

    private fun clampPanForRange(
        value: Float,
        range: PanControlRange,
    ): Float = min(max(value, range.lowerBound.toFloat()), range.upperBound.toFloat())

    private fun clampPlaybackRate(value: Float): Float = min(max(value, 0.5f), 2.0f)

    private fun clampNormalizedEq(value: Float): Float = min(max(value, 0.0f), 1.0f)

    private fun prepareMediaPlayerLocked(sourceUri: String): Result<MediaPlayer?> {
        val context = appContext ?: return Result.success(null)
        val createdPlayer = runCatching {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            player.setDataSource(context, Uri.parse(sourceUri))
            player.isLooping = false
            player.prepare()
            player.setOnCompletionListener {
                stateLock.withLock {
                    internalPlaybackState = AudioPlaybackState.PAUSED
                    lastKnownCurrentTimeSeconds = internalTotalDurationSeconds
                    playbackStartOffsetSeconds = internalTotalDurationSeconds
                }
            }
            player
        }
        if (createdPlayer.isFailure) {
            return Result.failure(createdPlayer.exceptionOrNull() ?: IllegalStateException("Media load failed"))
        }

        mediaPlayer?.runCatching { release() }
        mediaPlayer = createdPlayer.getOrNull()
        applyVolumeAndPanLocked()
        return Result.success(mediaPlayer)
    }

    private fun applyVolumeAndPanLocked() {
        val player = mediaPlayer ?: return
        val pan = internalPan.coerceIn(-1.0f, 1.0f)
        val leftGain = internalVolume * if (pan > 0f) (1f - pan) else 1f
        val rightGain = internalVolume * if (pan < 0f) (1f + pan) else 1f
        runCatching { player.setVolume(leftGain.coerceIn(0f, 1f), rightGain.coerceIn(0f, 1f)) }
    }
}

interface MonotonicClock {
    fun nowMonotonicNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMonotonicNanos(): Long = System.nanoTime()
}
