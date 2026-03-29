package dev.manelix.mixer.core.audio

import dev.manelix.mixer.core.audio.model.AudioEngineError
import dev.manelix.mixer.core.audio.model.MicrophoneCaptureFrame
import dev.manelix.mixer.core.common.model.AudioPlaybackState
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Phase-4 Android playback skeleton that mirrors the iOS controller contract/state transitions.
 * Real decoding/output routing is added in later phases.
 */
class SkeletonAudioEngineController(
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val defaultDurationSeconds: Double = 180.0,
) : AudioEngineController {
    private val stateLock = ReentrantLock()

    private var running = false
    private var loadedSourceUri: String? = null
    private var internalPlaybackState: AudioPlaybackState = AudioPlaybackState.IDLE
    private var lastKnownCurrentTimeSeconds = 0.0
    private var internalTotalDurationSeconds = 0.0
    private var internalVolume = 1.0f
    private var internalPan = 0.0f
    private var internalPlaybackRate = 1.0f
    private var isScratchModeActive = false
    private var scratchWasPlayingBeforeGesture = false
    private var isMicCaptureRunning = false
    private var micCallback: ((MicrophoneCaptureFrame) -> Unit)? = null
    private var playbackStartOffsetSeconds = 0.0
    private var playbackStartMonotonicNanos = 0L

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

        loadedSourceUri = sourceUri
        internalTotalDurationSeconds = max(1.0, defaultDurationSeconds)
        playbackStartOffsetSeconds = 0.0
        lastKnownCurrentTimeSeconds = 0.0
        isScratchModeActive = false
        scratchWasPlayingBeforeGesture = false
        internalPlaybackState = AudioPlaybackState.FILE_LOADED
        Result.success(Unit)
    }

    override fun play(): Result<Unit> = stateLock.withLock {
        if (loadedSourceUri == null) {
            return failure(AudioEngineError.NoFileLoaded)
        }

        running = true
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

            if (internalPlaybackState != AudioPlaybackState.PLAYING) {
                internalPlaybackState = AudioPlaybackState.PAUSED
                return
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
        internalPlaybackState = if (angularVelocity == 0.0) {
            AudioPlaybackState.PAUSED
        } else {
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
            startPlaybackClockLocked(finalTime)
            internalPlaybackState = AudioPlaybackState.PLAYING
        } else {
            lastKnownCurrentTimeSeconds = finalTime
            playbackStartOffsetSeconds = finalTime
            internalPlaybackState = AudioPlaybackState.PAUSED
        }
        Result.success(Unit)
    }

    override fun setVolume(value: Float) {
        stateLock.withLock {
            internalVolume = clampVolume(value)
        }
    }

    override fun setPan(value: Float) {
        stateLock.withLock {
            internalPan = clampPan(value)
        }
    }

    override fun setPlaybackRate(value: Float) {
        stateLock.withLock {
            val current = resolvedCurrentTimeLocked()
            internalPlaybackRate = clampPlaybackRate(value)
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
        Result.success(Unit)
    }

    override fun stopMicrophoneCapture() {
        stateLock.withLock {
            isMicCaptureRunning = false
            micCallback = null
        }
    }

    private fun resolvedCurrentTimeLocked(): Double {
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

    private fun clampPlaybackRate(value: Float): Float = min(max(value, 0.5f), 2.0f)

    private fun clampNormalizedEq(value: Float): Float = min(max(value, 0.0f), 1.0f)
}

interface MonotonicClock {
    fun nowMonotonicNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMonotonicNanos(): Long = System.nanoTime()
}
