package dev.manelix.mixer.core.audio

import dev.manelix.mixer.core.audio.model.AudioEngineError
import dev.manelix.mixer.core.audio.model.MicrophoneCaptureFrame
import dev.manelix.mixer.core.common.model.AudioPlaybackState

interface AudioEngineController {
    val isRunning: Boolean
    val playbackState: AudioPlaybackState
    val currentTimeSeconds: Double
    val totalDurationSeconds: Double
    val volume: Float
    val pan: Float
    val playbackRate: Float
    val isMicrophoneCaptureRunning: Boolean

    fun startEngine(): Result<Unit>
    fun stopEngine()
    fun loadFile(sourceUri: String): Result<Unit>
    fun play(): Result<Unit>
    fun pause()
    fun seekTo(timeSeconds: Double): Result<Unit>
    fun beginScratch(): Result<Unit>
    fun scratchTo(timeSeconds: Double, angularVelocity: Double): Result<Unit>
    fun endScratch(resumePlayback: Boolean): Result<Unit>
    fun setVolume(value: Float)
    fun setPan(value: Float)
    fun setPlaybackRate(value: Float)
    fun setEqualizer(low: Float, mid: Float, high: Float)
    fun startMicrophoneCapture(onBuffer: (MicrophoneCaptureFrame) -> Unit): Result<Unit>
    fun stopMicrophoneCapture()
}

class AudioEngineException(val error: AudioEngineError) : IllegalStateException(error.toString())

fun AudioEngineError.asThrowable(): Throwable = AudioEngineException(this)
