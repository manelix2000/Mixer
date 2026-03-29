package dev.manelix.mixer.core.audio

import dev.manelix.mixer.core.audio.model.AudioEngineError
import dev.manelix.mixer.core.common.model.AudioPlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkeletonAudioEngineControllerTest {
    @Test
    fun `play returns no file loaded when file was not loaded`() {
        val engine = SkeletonAudioEngineController(clock = FakeClock())

        val result = engine.play()

        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull()
        assertTrue(throwable is AudioEngineException)
        assertEquals(AudioEngineError.NoFileLoaded, throwable.error)
    }

    @Test
    fun `load and play advances current time using playback rate`() {
        val clock = FakeClock()
        val engine = SkeletonAudioEngineController(clock = clock, defaultDurationSeconds = 120.0)

        assertTrue(engine.loadFile("content://track").isSuccess)
        assertTrue(engine.play().isSuccess)
        assertEquals(AudioPlaybackState.PLAYING, engine.playbackState)

        clock.advanceByNanos(2_000_000_000L)
        assertEquals(2.0, engine.currentTimeSeconds, absoluteTolerance = 0.01)

        engine.setPlaybackRate(2.0f)
        clock.advanceByNanos(1_000_000_000L)
        assertEquals(4.0, engine.currentTimeSeconds, absoluteTolerance = 0.01)
    }

    @Test
    fun `pause keeps last known current time`() {
        val clock = FakeClock()
        val engine = SkeletonAudioEngineController(clock = clock, defaultDurationSeconds = 120.0)
        engine.loadFile("content://track")
        engine.play()

        clock.advanceByNanos(1_500_000_000L)
        engine.pause()
        val pausedAt = engine.currentTimeSeconds

        clock.advanceByNanos(3_000_000_000L)
        assertEquals(AudioPlaybackState.PAUSED, engine.playbackState)
        assertEquals(pausedAt, engine.currentTimeSeconds, absoluteTolerance = 0.001)
    }

    @Test
    fun `scratch lifecycle applies and ends at final time`() {
        val clock = FakeClock()
        val engine = SkeletonAudioEngineController(clock = clock, defaultDurationSeconds = 90.0)
        engine.loadFile("content://track")
        engine.play()
        clock.advanceByNanos(1_000_000_000L)

        assertTrue(engine.beginScratch().isSuccess)
        assertTrue(engine.scratchTo(timeSeconds = 12.0, angularVelocity = 3.0).isSuccess)
        assertEquals(12.0, engine.currentTimeSeconds, absoluteTolerance = 0.001)
        assertTrue(engine.endScratch(resumePlayback = false).isSuccess)
        assertEquals(AudioPlaybackState.PAUSED, engine.playbackState)
        assertEquals(12.0, engine.currentTimeSeconds, absoluteTolerance = 0.001)
    }

    @Test
    fun `clamps pan volume and playback rate values`() {
        val engine = SkeletonAudioEngineController(clock = FakeClock())

        engine.setPan(2.5f)
        engine.setVolume(-0.5f)
        engine.setPlaybackRate(0.1f)

        assertEquals(1.0f, engine.pan)
        assertEquals(0.0f, engine.volume)
        assertEquals(0.5f, engine.playbackRate)
    }

    @Test
    fun `microphone capture toggles running flag`() {
        val engine = SkeletonAudioEngineController(clock = FakeClock())
        var callbackFrameCount = 0

        assertTrue(engine.startMicrophoneCapture { callbackFrameCount += 1 }.isSuccess)
        assertTrue(engine.isMicrophoneCaptureRunning)
        assertTrue(engine.isRunning)
        assertEquals(0, callbackFrameCount)

        engine.stopMicrophoneCapture()
        assertFalse(engine.isMicrophoneCaptureRunning)
        assertNotNull(engine.startEngine().getOrNull())
    }
}

private class FakeClock : MonotonicClock {
    private var nowNanos: Long = 0

    override fun nowMonotonicNanos(): Long = nowNanos

    fun advanceByNanos(delta: Long) {
        nowNanos += delta
    }
}
