package dev.manelix.mixer.feature.deck

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.manelix.mixer.core.audio.AudioEngineController
import dev.manelix.mixer.core.audio.AudioEngineException
import dev.manelix.mixer.core.audio.AudioEngineRoutingProvider
import dev.manelix.mixer.core.audio.MediaPlayerAudioEngineController
import dev.manelix.mixer.core.audio.model.MicrophoneCaptureFrame
import dev.manelix.mixer.core.common.model.AudioEngineMode
import dev.manelix.mixer.core.common.model.AudioPlaybackState
import dev.manelix.mixer.core.common.model.CueMixMode
import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckRole
import dev.manelix.mixer.core.common.model.SplitDeckLayout
import dev.manelix.mixer.core.dsp.FallbackTempoDetector
import dev.manelix.mixer.core.dsp.TempoDetector
import dev.manelix.mixer.core.dsp.model.BpmResult
import dev.manelix.mixer.core.dsp.model.TempoInputBuffer
import dev.manelix.mixer.core.waveform.ProceduralWaveformAnalyzer
import dev.manelix.mixer.core.waveform.WaveformAnalyzer
import dev.manelix.mixer.feature.deck.model.DeckScreenState
import dev.manelix.mixer.feature.deck.model.ScratchInteractionState
import dev.manelix.mixer.feature.deck.model.TurntableDeckUiState
import dev.manelix.mixer.feature.deck.model.TurntablePhysicsState
import dev.manelix.mixer.feature.deck.model.hasSelectedTrack
import dev.manelix.mixer.feature.deck.model.isPlaybackActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class DeckViewModel : ViewModel() {
    companion object {
        private const val TAG = "MixerDeckVM"
        private const val WAVEFORM_SAMPLE_COUNT = 4096 * 4
        private const val WAVEFORM_MIN_ZOOM = 0.2
        private const val WAVEFORM_MAX_ZOOM = 8.0
        private const val WAVEFORM_BASE_SAMPLE_SPACING = 2.0
        private const val SCRUB_SECONDS_PER_REVOLUTION = 1.8
        private const val SCRUB_SECONDS_PER_RADIAN = SCRUB_SECONDS_PER_REVOLUTION / (Math.PI * 2.0)
        private const val SCRATCH_SECONDS_PER_RADIAN = 0.20
        private const val MAX_SCRUB_MODE_STEP = 0.10
        private const val MAX_SCRATCH_MODE_STEP = 0.20
        private const val SCRATCH_ANGULAR_VELOCITY_THRESHOLD = 3.0
        private const val SCRATCH_VELOCITY_SMOOTHING = 0.35
        private const val SCRATCH_DIRECTION_ANGLE_THRESHOLD = 0.0025
        private const val SCRATCH_JITTER_ANGLE_THRESHOLD = 0.002
        private const val SCRATCH_JITTER_VELOCITY_THRESHOLD = 0.6
        private const val MIN_SCRUB_COMMIT_INTERVAL_NANOS = 4_166_666L
        private const val MIN_SCRATCH_COMMIT_INTERVAL_NANOS = 5_555_555L
        private const val MIN_SCRUB_COMMIT_DELTA = 0.001
        private const val MIN_SCRATCH_COMMIT_DELTA = 0.003
        private const val BASE_PLATTER_ANGULAR_VELOCITY = (33.33 / 60.0) * (Math.PI * 2.0)
        private const val TRACK_END_TOLERANCE = 0.01
        private const val WAVEFORM_POINTS_PER_REVOLUTION = 60.0
        private const val MIN_WAVEFORM_POINTS_PER_REVOLUTION = 20.0
        private const val PLATTER_POINTS_PER_REVOLUTION = 120.0
        private const val MIN_BPM = 60.0
        private const val MAX_BPM = 200.0
        private const val OFFLINE_BPM_ANALYSIS_SECONDS = 12.0
        private const val LIVE_MIC_BPM_SMOOTHING = 0.22
        private const val MAX_TRACK_ANALYSIS_CACHE_ENTRIES = 24
        private const val MAX_PRESSURE_SLOWDOWN_FRACTION = 0.9
        private const val MIN_PRESSURE_SLOWDOWN_MULTIPLIER = 0.08
        private const val MAX_PRESSURE_ACCELERATION_FRACTION = 0.9
        private const val MAX_PRESSURE_ACCELERATION_MULTIPLIER = 1.92
        private const val PRESSURE_CURVE_EXPONENT = 1.6
        private val ALLOWED_PITCH_SENSITIVITY_PERCENTS = listOf(2, 4, 8, 16)
    }

    private var leftEngine: AudioEngineController = MediaPlayerAudioEngineController()
    private var rightEngine: AudioEngineController = MediaPlayerAudioEngineController()
    private val waveformAnalyzer: WaveformAnalyzer = ProceduralWaveformAnalyzer()
    private val tempoDetector: TempoDetector = FallbackTempoDetector()
    private val microphoneBpmPipeline = MicrophoneBpmPipeline(detector = tempoDetector)
    private var leftWaveformJob: Job? = null
    private var rightWaveformJob: Job? = null
    private var leftOfflineBpmJob: Job? = null
    private var rightOfflineBpmJob: Job? = null
    private var latestExternalBpm: Double? = null
    private val analysisCacheLock = ReentrantLock()
    private val trackAnalysisCache =
        object : LinkedHashMap<String, TrackAnalysisCacheEntry>(MAX_TRACK_ANALYSIS_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackAnalysisCacheEntry>?): Boolean {
                return size > MAX_TRACK_ANALYSIS_CACHE_ENTRIES
            }
        }
    private val leftRuntime = DeckRuntime()
    private val rightRuntime = DeckRuntime()
    private var hasBoundContextBackedEngines = false
    private var appContextForAnalysis: Context? = null

    private val _screenState = MutableStateFlow(DeckScreenState())
    val screenState: StateFlow<DeckScreenState> = _screenState

    init {
        leftEngine.startEngine()
        rightEngine.startEngine()
        microphoneBpmPipeline.setResultHandler(::handleMicrophoneBpmResult)
        startPlaybackTicker()
    }

    fun initializeForDevice(
        isTablet: Boolean,
        restoredMode: AudioEngineMode? = null,
        restoredLayout: SplitDeckLayout? = null,
        context: Context? = null,
    ) {
        bindContextBackedEnginesIfNeeded(context)
        _screenState.update { current ->
            if (current.isInitializedForDevice) {
                current
            } else {
                current.copy(
                    isInitializedForDevice = true,
                    areControlsVisible = isTablet,
                    isRightDeckVisible = isTablet,
                    root = current.root.copy(
                        selectedAudioEngineMode = restoredMode ?: current.root.selectedAudioEngineMode,
                        selectedSplitDeckLayout = restoredLayout ?: current.root.selectedSplitDeckLayout,
                    ),
                )
            }
        }
        refreshRoutingAndMix(resetPanToDefault = true)
    }

    fun toggleControls() {
        _screenState.update { state ->
            val nextVisibility = !state.areControlsVisible
            state.copy(
                areControlsVisible = nextVisibility,
                isSettingsVisible = if (nextVisibility) state.isSettingsVisible else false,
            )
        }
    }

    private fun bindContextBackedEnginesIfNeeded(context: Context?) {
        val appContext = context?.applicationContext ?: return
        appContextForAnalysis = appContext
        if (hasBoundContextBackedEngines) return
        hasBoundContextBackedEngines = true

        leftEngine.stopMicrophoneCapture()
        rightEngine.stopMicrophoneCapture()
        leftEngine.stopEngine()
        rightEngine.stopEngine()

        leftEngine = MediaPlayerAudioEngineController(appContext = appContext)
        rightEngine = MediaPlayerAudioEngineController(appContext = appContext)
        leftEngine.startEngine()
        rightEngine.startEngine()
    }

    fun toggleSettings() {
        _screenState.update { state -> state.copy(isSettingsVisible = !state.isSettingsVisible) }
    }

    fun toggleRightDeck() {
        _screenState.update { state -> state.copy(isRightDeckVisible = !state.isRightDeckVisible) }
    }

    fun toggleEqualizer() {
        _screenState.update { state -> state.copy(isEqualizerVisible = !state.isEqualizerVisible) }
    }

    fun toggleMic() {
        if (_screenState.value.root.isMicrophoneBpmDetectionActive) {
            stopMicrophoneBpmDetection()
        } else {
            startMicrophoneBpmDetection()
        }
    }

    fun togglePitchLock() {
        val state = _screenState.value
        if (state.root.isPitchLockedToExternalBpm) {
            setPitchLockEnabled(false, externalBpm = null)
            return
        }

        if (!state.root.isMicrophoneBpmDetectionActive) {
            return
        }

        val externalBpm = latestExternalBpm
        if (externalBpm == null) {
            _screenState.update { current ->
                current.copy(
                    root = current.root.copy(
                        externalBpmStatusText = "Listening... lock will apply once BPM is detected.",
                    ),
                )
            }
            return
        }

        setPitchLockEnabled(true, externalBpm = externalBpm)
        stopMicrophoneBpmDetection()
    }

    fun setAudioEngineMode(mode: AudioEngineMode) {
        _screenState.update { state ->
            val updatedRoot = if (mode == AudioEngineMode.SPLIT) {
                state.root.copy(
                    selectedAudioEngineMode = mode,
                    isLeftDeckCueEnabled = true,
                    isRightDeckCueEnabled = true,
                    cueMixMode = CueMixMode.BLEND,
                )
            } else {
                state.root.copy(
                    selectedAudioEngineMode = mode,
                    isLeftDeckCueEnabled = false,
                    isRightDeckCueEnabled = false,
                    cueMixMode = CueMixMode.MASTER,
                )
            }
            state.copy(root = updatedRoot)
        }
        refreshRoutingAndMix(resetPanToDefault = mode == AudioEngineMode.SPLIT)
    }

    fun setSplitDeckLayout(layout: SplitDeckLayout) {
        _screenState.update { state ->
            state.copy(root = state.root.copy(selectedSplitDeckLayout = layout))
        }
        refreshRoutingAndMix(resetPanToDefault = true)
    }

    fun selectTrackForLeftDeck(uri: String) = selectTrack(isLeft = true, uri = uri)

    fun selectTrackForRightDeck(uri: String) = selectTrack(isLeft = false, uri = uri)

    fun togglePlayPauseLeftDeck() = togglePlayPauseDeck(isLeft = true)

    fun togglePlayPauseRightDeck() = togglePlayPauseDeck(isLeft = false)

    fun stopLeftDeck() = stopDeck(isLeft = true)

    fun stopRightDeck() = stopDeck(isLeft = false)

    fun seekLeftDeckFromWaveformTap(xOffset: Double) = seekDeckFromWaveformTap(isLeft = true, xOffset = xOffset)

    fun seekRightDeckFromWaveformTap(xOffset: Double) = seekDeckFromWaveformTap(isLeft = false, xOffset = xOffset)

    fun setLeftDeckWaveformZoom(value: Double) = setDeckWaveformZoom(isLeft = true, value = value)

    fun setRightDeckWaveformZoom(value: Double) = setDeckWaveformZoom(isLeft = false, value = value)

    fun beginLeftDeckWaveformScratch(wasPlayingAtGestureStart: Boolean) =
        beginDeckScratch(
            isLeft = true,
            wasPlayingAtGestureStart = wasPlayingAtGestureStart,
            allowEngineScratch = false,
        )

    fun beginRightDeckWaveformScratch(wasPlayingAtGestureStart: Boolean) =
        beginDeckScratch(
            isLeft = false,
            wasPlayingAtGestureStart = wasPlayingAtGestureStart,
            allowEngineScratch = false,
        )

    fun updateLeftDeckWaveformScratch(deltaX: Double) = updateWaveformScratch(isLeft = true, deltaX = deltaX)

    fun updateRightDeckWaveformScratch(deltaX: Double) = updateWaveformScratch(isLeft = false, deltaX = deltaX)

    fun endLeftDeckWaveformScratch() = endDeckScratch(isLeft = true)

    fun endRightDeckWaveformScratch() = endDeckScratch(isLeft = false)

    fun beginLeftDeckPlatterScratch(wasPlayingAtGestureStart: Boolean) =
        beginDeckScratch(
            isLeft = true,
            wasPlayingAtGestureStart = wasPlayingAtGestureStart,
            allowEngineScratch = true,
        )

    fun beginRightDeckPlatterScratch(wasPlayingAtGestureStart: Boolean) =
        beginDeckScratch(
            isLeft = false,
            wasPlayingAtGestureStart = wasPlayingAtGestureStart,
            allowEngineScratch = true,
        )

    fun updateLeftDeckPlatterScratch(angleDelta: Double) = updatePlatterScratch(isLeft = true, angleDelta = angleDelta)

    fun updateRightDeckPlatterScratch(angleDelta: Double) = updatePlatterScratch(isLeft = false, angleDelta = angleDelta)

    fun endLeftDeckPlatterScratch() = endDeckScratch(isLeft = true)

    fun endRightDeckPlatterScratch() = endDeckScratch(isLeft = false)

    fun updateLeftDeckPressureTouch(pressure: Double, direction: Double) =
        updateDeckPressureTouch(isLeft = true, pressure = pressure, direction = direction)

    fun updateRightDeckPressureTouch(pressure: Double, direction: Double) =
        updateDeckPressureTouch(isLeft = false, pressure = pressure, direction = direction)

    fun endLeftDeckPressureTouch() = endDeckPressureTouch(isLeft = true)

    fun endRightDeckPressureTouch() = endDeckPressureTouch(isLeft = false)

    fun increaseBpmForLeftDeck() = adjustDeckBpm(isLeft = true, delta = 1.0)

    fun decreaseBpmForLeftDeck() = adjustDeckBpm(isLeft = true, delta = -1.0)

    fun increaseBpmForRightDeck() = adjustDeckBpm(isLeft = false, delta = 1.0)

    fun decreaseBpmForRightDeck() = adjustDeckBpm(isLeft = false, delta = -1.0)

    fun setLeftDeckVolume(value: Double) = setDeckVolume(isLeft = true, value = value)

    fun setRightDeckVolume(value: Double) = setDeckVolume(isLeft = false, value = value)

    fun setLeftDeckPitchOffset(offset: Double) = setDeckPitchOffset(isLeft = true, offset = offset)

    fun setRightDeckPitchOffset(offset: Double) = setDeckPitchOffset(isLeft = false, offset = offset)

    fun increaseLeftDeckPitchSensitivity() = adjustDeckPitchSensitivity(isLeft = true, increase = true)

    fun decreaseLeftDeckPitchSensitivity() = adjustDeckPitchSensitivity(isLeft = true, increase = false)

    fun increaseRightDeckPitchSensitivity() = adjustDeckPitchSensitivity(isLeft = false, increase = true)

    fun decreaseRightDeckPitchSensitivity() = adjustDeckPitchSensitivity(isLeft = false, increase = false)

    fun setLeftDeckPan(value: Double) = setDeckPan(isLeft = true, value = value)

    fun setRightDeckPan(value: Double) = setDeckPan(isLeft = false, value = value)

    fun toggleLeftDeckCue() {
        _screenState.update { state ->
            state.copy(root = state.root.copy(isLeftDeckCueEnabled = !state.root.isLeftDeckCueEnabled))
        }
        applyCueRoutingMix()
    }

    fun toggleRightDeckCue() {
        _screenState.update { state ->
            state.copy(root = state.root.copy(isRightDeckCueEnabled = !state.root.isRightDeckCueEnabled))
        }
        applyCueRoutingMix()
    }

    fun setCueMixFromFader(value: Double) {
        val mode = when {
            value <= -0.33 -> CueMixMode.CUE
            value >= 0.33 -> CueMixMode.MASTER
            else -> CueMixMode.BLEND
        }
        _screenState.update { state ->
            state.copy(root = state.root.copy(cueMixMode = mode))
        }
        applyCueRoutingMix()
    }

    fun setCueLevelPercent(value: Int) {
        _screenState.update { state ->
            state.copy(root = state.root.copy(cueLevelPercent = value.coerceIn(0, 100)))
        }
        applyCueRoutingMix()
    }

    fun setLeftDeckEqualizerLow(value: Double) = setDeckEqualizerBand(isLeft = true, band = EqBand.LOW, value = value)

    fun setLeftDeckEqualizerMid(value: Double) = setDeckEqualizerBand(isLeft = true, band = EqBand.MID, value = value)

    fun setLeftDeckEqualizerHigh(value: Double) = setDeckEqualizerBand(isLeft = true, band = EqBand.HIGH, value = value)

    fun setRightDeckEqualizerLow(value: Double) = setDeckEqualizerBand(isLeft = false, band = EqBand.LOW, value = value)

    fun setRightDeckEqualizerMid(value: Double) = setDeckEqualizerBand(isLeft = false, band = EqBand.MID, value = value)

    fun setRightDeckEqualizerHigh(value: Double) = setDeckEqualizerBand(isLeft = false, band = EqBand.HIGH, value = value)

    override fun onCleared() {
        leftWaveformJob?.cancel()
        rightWaveformJob?.cancel()
        leftOfflineBpmJob?.cancel()
        rightOfflineBpmJob?.cancel()
        microphoneBpmPipeline.reset()
        leftEngine.stopMicrophoneCapture()
        rightEngine.stopMicrophoneCapture()
        leftEngine.stopEngine()
        rightEngine.stopEngine()
        super.onCleared()
    }

    private fun startPlaybackTicker() {
        viewModelScope.launch {
            while (isActive) {
                syncDeckFromEngine(isLeft = true)
                syncDeckFromEngine(isLeft = false)
                delay(16L)
            }
        }
    }

    private fun selectTrack(
        isLeft: Boolean,
        uri: String,
    ) {
        val deckLabel = if (isLeft) "L" else "R"
        val engine = engineForDeck(isLeft)
        val cachedAnalysis = getTrackAnalysis(uri)
        cancelScratchState(isLeft)
        runtimeForDeck(isLeft).userWantsPlayback = false
        runtimeForDeck(isLeft).blockAutoplayUntilManualStart = true
        runtimeForDeck(isLeft).forceStayPausedAfterManualPause = true
        val trackName = extractTrackName(uri)
        updateDeckState(isLeft) { deck ->
            deck.copy(
                selectedTrackUri = uri,
                selectedTrackName = trackName,
                playbackStatusText = "Importing...",
                playbackTimeText = "00:00 / 00:00",
                playbackProgress = 0.0,
                playbackState = AudioPlaybackState.IDLE,
                waveformText = "Waveform Placeholder",
                waveformData = floatArrayOf(),
                isWaveformLoading = true,
                waveformZoom = 1.0,
                isBpmLoading = true,
                bpmDetectionStatusText = "Detecting BPM...",
                originalBpm = 0.0,
                targetBpm = 120.0,
                bpmText = "-- BPM",
            )
        }

        // Run analysis in parallel with engine load for lower perceived import latency.
        if (cachedAnalysis?.waveform?.isNotEmpty() == true) {
            applyCachedTrackAnalysis(isLeft = isLeft, uri = uri, cached = cachedAnalysis)
        } else {
            loadWaveformForDeck(isLeft = isLeft, uri = uri)
        }

        val loadResult = engine.loadFile(uri)
        Log.d(TAG, "selectTrack[$deckLabel] loadResult=${loadResult.isSuccess}")
        if (loadResult.isSuccess) {
            // Loading a track must never auto-start playback.
            engine.pause()
            engine.seekTo(0.0)
            syncDeckFromEngine(isLeft)
            updateDeckState(isLeft) { deck ->
                deck.copy(playbackStatusText = "")
            }
        } else {
            waveformJobForDeck(isLeft)?.cancel()
            if (isLeft) leftOfflineBpmJob?.cancel() else rightOfflineBpmJob?.cancel()
            val statusText = statusForError(loadResult.exceptionOrNull(), fallback = "Failed to load selected track")
            updateDeckState(isLeft) { deck ->
                deck.copy(
                    playbackStatusText = statusText,
                    playbackState = AudioPlaybackState.IDLE,
                    playbackTimeText = "00:00 / 00:00",
                    playbackProgress = 0.0,
                    isWaveformLoading = false,
                    isBpmLoading = false,
                )
            }
        }
    }

    private fun togglePlayPauseDeck(isLeft: Boolean) {
        val deckLabel = if (isLeft) "L" else "R"
        val engine = engineForDeck(isLeft)
        val runtime = runtimeForDeck(isLeft)
        val deckState = currentDeckState(isLeft)
        if (!deckState.hasSelectedTrack) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = "Select a track first") }
            return
        }
        if (deckState.isWaveformLoading) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = "Loading track...") }
            return
        }

        val result = if (deckState.isPlaybackActive) {
            runtime.userWantsPlayback = false
            runtime.forceStayPausedAfterManualPause = true
            engine.pause()
            Result.success(Unit)
        } else {
            runtime.userWantsPlayback = true
            runtime.blockAutoplayUntilManualStart = false
            runtime.forceStayPausedAfterManualPause = false
            engine.play()
        }
        Log.d(
            TAG,
            "togglePlayPause[$deckLabel] wasPlaying=${deckState.isPlaybackActive} wants=${runtime.userWantsPlayback} result=${result.isSuccess}",
        )

        if (result.isFailure) {
            if (!deckState.isPlaybackActive) {
                runtime.userWantsPlayback = false
            }
            updateDeckState(isLeft) { deck ->
                deck.copy(playbackStatusText = statusForError(result.exceptionOrNull(), "Unable to start playback"))
            }
            return
        }

        syncDeckFromEngine(isLeft)
        updateDeckState(isLeft) { deck -> deck.copy(playbackStatusText = "") }
    }

    private fun stopDeck(isLeft: Boolean) {
        val deckLabel = if (isLeft) "L" else "R"
        val engine = engineForDeck(isLeft)
        val runtime = runtimeForDeck(isLeft)
        val deckState = currentDeckState(isLeft)
        if (!deckState.hasSelectedTrack) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = "Select a track first") }
            return
        }
        if (deckState.isWaveformLoading) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = "Loading track...") }
            return
        }

        cancelScratchState(isLeft)
        engine.pause()
        val seekResult = engine.seekTo(0.0)
        if (seekResult.isFailure) {
            updateDeckState(isLeft) { deck ->
                deck.copy(playbackStatusText = statusForError(seekResult.exceptionOrNull(), "Unable to stop playback"))
            }
            return
        }
        engine.pause()
        runtime.blockAutoplayUntilManualStart = false
        runtime.userWantsPlayback = false
        runtime.forceStayPausedAfterManualPause = true
        Log.d(TAG, "stopDeck[$deckLabel] paused+seek0 done")

        syncDeckFromEngine(isLeft)
        updateDeckState(isLeft) { deck -> deck.copy(playbackStatusText = "Stopped") }
    }

    private fun cancelScratchState(isLeft: Boolean) {
        val runtime = runtimeForDeck(isLeft)
        if (runtime.isScrubbing && runtime.isEngineScratchActive) {
            engineForDeck(isLeft).endScratch(resumePlayback = false)
        }
        runtime.isScrubbing = false
        runtime.wasPlayingBeforeScrub = false
        runtime.isEngineScratchActive = false
        runtime.lastScratchUpdateNanos = 0L
        runtime.lastScratchCommitNanos = 0L
        runtime.smoothedScratchAngularVelocity = 0.0
        runtime.latestScratchAngularVelocity = 0.0
        runtime.latestScratchDirection = 1.0
        runtime.scratchMode = ScratchMode.SCRUB
        updateDeckState(isLeft) { deck ->
            deck.copy(scratchInteractionState = ScratchInteractionState.IDLE)
        }
    }

    private fun syncDeckFromEngine(isLeft: Boolean) {
        val engine = engineForDeck(isLeft)
        val runtime = runtimeForDeck(isLeft)
        val deckSnapshot = currentDeckState(isLeft)
        if (deckSnapshot.isWaveformLoading) {
            runtime.userWantsPlayback = false
            if (engine.playbackState == AudioPlaybackState.PLAYING) {
                engine.pause()
            }
            if (engine.currentTimeSeconds > 0.01) {
                engine.seekTo(0.0)
            }
        }
        if (
            runtime.blockAutoplayUntilManualStart &&
            !runtime.isScrubbing
        ) {
            cancelScratchState(isLeft)
            if (engine.playbackState == AudioPlaybackState.PLAYING) {
                engine.pause()
            }
            if (engine.currentTimeSeconds > 0.01) {
                engine.seekTo(0.0)
            }
        }
        if (!runtime.isScrubbing && !runtime.userWantsPlayback) {
            if (engine.playbackState == AudioPlaybackState.PLAYING) {
                engine.pause()
            }
        }
        if (runtime.isScrubbing) {
            val nowNanos = System.nanoTime()
            val idleNanos = if (runtime.lastScratchUpdateNanos > 0L) nowNanos - runtime.lastScratchUpdateNanos else 0L
            if (idleNanos > 45_000_000L) {
                runtime.latestScratchAngularVelocity = 0.0
                runtime.smoothedScratchAngularVelocity = 0.0
                commitScratchAudio(isLeft = isLeft, force = true)
            }
        }
        val platterRotationDegrees = stepTurntablePhysics(isLeft)
        val currentTime = engine.currentTimeSeconds
        val totalDuration = engine.totalDurationSeconds
        val resolvedCurrentTime = if (runtime.isScrubbing) runtime.scratchCurrentTime else currentTime
        val progress = if (totalDuration > 0.0) {
            (resolvedCurrentTime / totalDuration).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val playbackState = engine.playbackState

        updateDeckState(isLeft) { deck ->
            val routingProvider = engine as? AudioEngineRoutingProvider
            val panRange = routingProvider?.panControlRange ?: deck.panControlRange
            deck.copy(
                playbackState = playbackState,
                playbackTimeText = formatPlaybackTime(resolvedCurrentTime, totalDuration),
                playbackProgress = progress,
                volume = deck.volume,
                pan = panRange.clamp(engine.pan.toDouble()),
                panControlRange = panRange,
                splitDeckRole = routingProvider?.splitDeckRole,
                playbackStatusText = resolvePlaybackStatus(deck.playbackStatusText, playbackState),
                platterRotationDegrees = platterRotationDegrees,
            )
        }
    }

    private fun resolvePlaybackStatus(
        currentStatus: String,
        playbackState: AudioPlaybackState,
    ): String {
        if (currentStatus == "Scratching") {
            return currentStatus
        }
        if (playbackState == AudioPlaybackState.PLAYING) {
            return ""
        }
        if (currentStatus == "Importing..." && playbackState == AudioPlaybackState.FILE_LOADED) {
            return ""
        }
        return currentStatus
    }

    private fun adjustDeckBpm(isLeft: Boolean, delta: Double) {
        _screenState.update { state ->
            if (isLeft) {
                state.copy(leftDeck = state.leftDeck.bumpTargetBpm(delta))
            } else {
                state.copy(rightDeck = state.rightDeck.bumpTargetBpm(delta))
            }
        }
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
    }

    private fun setDeckVolume(
        isLeft: Boolean,
        value: Double,
    ) {
        val clamped = value.coerceIn(0.0, 1.0)
        updateDeckState(isLeft) { it.copy(volume = clamped) }
        applyCueRoutingMix()
    }

    private fun setDeckPitchOffset(
        isLeft: Boolean,
        offset: Double,
    ) {
        val deck = currentDeckState(isLeft)
        if (deck.isPitchLockedToExternalBpm) return
        val sensitivity = (deck.pitchSensitivityPercent.coerceIn(2, 16) / 100.0)
        val clampedOffset = offset.coerceIn(-sensitivity, sensitivity)
        val original = if (deck.originalBpm > 0.0) deck.originalBpm else 120.0
        val target = (original * (1.0 + clampedOffset)).coerceIn(40.0, 240.0)
        updateDeckState(isLeft) {
            it.copy(
                targetBpm = target,
            )
        }
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
    }

    private fun adjustDeckPitchSensitivity(
        isLeft: Boolean,
        increase: Boolean,
    ) {
        updateDeckState(isLeft) { deck ->
            val currentIndex = ALLOWED_PITCH_SENSITIVITY_PERCENTS.indexOf(deck.pitchSensitivityPercent).let { index ->
                if (index >= 0) index else ALLOWED_PITCH_SENSITIVITY_PERCENTS.indexOf(8).coerceAtLeast(0)
            }
            val nextIndex = if (increase) {
                (currentIndex + 1).coerceAtMost(ALLOWED_PITCH_SENSITIVITY_PERCENTS.lastIndex)
            } else {
                (currentIndex - 1).coerceAtLeast(0)
            }
            val nextSensitivity = ALLOWED_PITCH_SENSITIVITY_PERCENTS[nextIndex]
            val nextPitchLimit = nextSensitivity / 100.0
            val clampedTarget = if (deck.originalBpm > 0.0) {
                val currentOffset = ((deck.targetBpm / deck.originalBpm) - 1.0).coerceIn(-nextPitchLimit, nextPitchLimit)
                deck.originalBpm * (1.0 + currentOffset)
            } else {
                deck.targetBpm
            }
            deck.copy(
                pitchSensitivityPercent = nextSensitivity,
                targetBpm = clampedTarget,
            )
        }

        val deck = currentDeckState(isLeft)
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
    }

    private fun setDeckPan(
        isLeft: Boolean,
        value: Double,
    ) {
        val deck = currentDeckState(isLeft)
        val clamped = deck.panControlRange.clamp(value)
        engineForDeck(isLeft).setPan(clamped.toFloat())
        updateDeckState(isLeft) { it.copy(pan = clamped) }
    }

    private fun refreshRoutingAndMix(resetPanToDefault: Boolean) {
        val state = _screenState.value
        val isSplitMode = state.root.selectedAudioEngineMode == AudioEngineMode.SPLIT
        val layout = state.root.selectedSplitDeckLayout

        val leftRole = if (!isSplitMode) {
            null
        } else if (layout == SplitDeckLayout.LEFT_MASTER_RIGHT_CUE) {
            SplitDeckRole.MASTER
        } else {
            SplitDeckRole.CUE
        }
        val rightRole = if (!isSplitMode) {
            null
        } else if (layout == SplitDeckLayout.LEFT_MASTER_RIGHT_CUE) {
            SplitDeckRole.CUE
        } else {
            SplitDeckRole.MASTER
        }

        applyRoutingToDeck(isLeft = true, role = leftRole, resetPanToDefault = resetPanToDefault)
        applyRoutingToDeck(isLeft = false, role = rightRole, resetPanToDefault = resetPanToDefault)
        applyCueRoutingMix()
    }

    private fun applyRoutingToDeck(
        isLeft: Boolean,
        role: SplitDeckRole?,
        resetPanToDefault: Boolean,
    ) {
        val panRange = when (role) {
            SplitDeckRole.MASTER -> PanControlRange.MasterSplit
            SplitDeckRole.CUE -> PanControlRange.CueSplit
            null -> PanControlRange.Standard
        }
        val targetPan = if (resetPanToDefault && role != null) {
            if (role == SplitDeckRole.MASTER) -1.0 else 1.0
        } else {
            panRange.clamp(currentDeckState(isLeft).pan)
        }

        val engine = engineForDeck(isLeft)
        (engine as? AudioEngineRoutingProvider)?.setRoutingPolicy(role = role, panRange = panRange)
        engine.setPan(targetPan.toFloat())
        updateDeckState(isLeft) {
            it.copy(
                splitDeckRole = role,
                panControlRange = panRange,
                pan = targetPan,
            )
        }
    }

    private fun applyCueRoutingMix() {
        val state = _screenState.value
        val isSplitMode = state.root.selectedAudioEngineMode == AudioEngineMode.SPLIT
        if (!isSplitMode) {
            leftEngine.setVolume(state.leftDeck.volume.toFloat())
            rightEngine.setVolume(state.rightDeck.volume.toFloat())
            return
        }

        val cueLevel = state.root.cueLevelPercent.coerceIn(0, 100) / 100.0
        val (masterFactor, cueFactor) = when (state.root.cueMixMode) {
            CueMixMode.CUE -> 0.0 to cueLevel
            CueMixMode.BLEND -> 1.0 to cueLevel
            CueMixMode.MASTER -> 1.0 to 0.0
        }

        val leftRole = state.leftDeck.splitDeckRole ?: SplitDeckRole.MASTER
        val rightRole = state.rightDeck.splitDeckRole ?: SplitDeckRole.CUE

        val leftRoleFactor = when (leftRole) {
            SplitDeckRole.MASTER -> if (state.root.isLeftDeckCueEnabled) masterFactor else 0.0
            SplitDeckRole.CUE -> if (state.root.isLeftDeckCueEnabled) cueFactor else 0.0
        }
        val rightRoleFactor = when (rightRole) {
            SplitDeckRole.MASTER -> if (state.root.isRightDeckCueEnabled) masterFactor else 0.0
            SplitDeckRole.CUE -> if (state.root.isRightDeckCueEnabled) cueFactor else 0.0
        }

        leftEngine.setVolume((state.leftDeck.volume * leftRoleFactor).toFloat())
        rightEngine.setVolume((state.rightDeck.volume * rightRoleFactor).toFloat())
    }

    private fun setDeckEqualizerBand(
        isLeft: Boolean,
        band: EqBand,
        value: Double,
    ) {
        val clamped = value.coerceIn(0.0, 1.0)
        updateDeckState(isLeft) { deck ->
            when (band) {
                EqBand.LOW -> deck.copy(equalizerLow = clamped)
                EqBand.MID -> deck.copy(equalizerMid = clamped)
                EqBand.HIGH -> deck.copy(equalizerHigh = clamped)
            }
        }
        val updated = currentDeckState(isLeft)
        engineForDeck(isLeft).setEqualizer(
            low = updated.equalizerLow.toFloat(),
            mid = updated.equalizerMid.toFloat(),
            high = updated.equalizerHigh.toFloat(),
        )
    }

    private fun setDeckWaveformZoom(
        isLeft: Boolean,
        value: Double,
    ) {
        updateDeckState(isLeft) { deck ->
            deck.copy(waveformZoom = value.coerceIn(WAVEFORM_MIN_ZOOM, WAVEFORM_MAX_ZOOM))
        }
    }

    private fun seekDeckFromWaveformTap(
        isLeft: Boolean,
        xOffset: Double,
    ) {
        val engine = engineForDeck(isLeft)
        val deck = currentDeckState(isLeft)
        if (!deck.hasSelectedTrack || deck.waveformData.isEmpty()) return

        val sampleSpacing = max(WAVEFORM_BASE_SAMPLE_SPACING * deck.waveformZoom, 0.001)
        val deltaSamples = xOffset / sampleSpacing
        val denominator = max(deck.waveformData.size - 1, 1).toDouble()
        val targetProgress = (deck.playbackProgress + (deltaSamples / denominator)).coerceIn(0.0, 1.0)
        val targetTime = targetProgress * engine.totalDurationSeconds
        val result = engine.seekTo(targetTime)
        if (result.isSuccess) {
            syncDeckFromEngine(isLeft)
            updateDeckState(isLeft) { it.copy(playbackStatusText = "") }
        } else {
            updateDeckState(isLeft) { current ->
                current.copy(playbackStatusText = statusForError(result.exceptionOrNull(), "Seek unavailable"))
            }
        }
    }

    private enum class EqBand {
        LOW,
        MID,
        HIGH,
    }

    private fun beginDeckScratch(
        isLeft: Boolean,
        wasPlayingAtGestureStart: Boolean? = null,
        allowEngineScratch: Boolean = true,
    ) {
        val engine = engineForDeck(isLeft)
        val runtime = runtimeForDeck(isLeft)
        syncDeckFromEngine(isLeft)
        val deck = currentDeckState(isLeft)
        if (!deck.hasSelectedTrack || runtime.isScrubbing) return

        if (runtime.pressureStartTargetBpm != null) {
            endDeckPressureTouch(isLeft = isLeft)
        }
        runtime.isScrubbing = true
        val wasPlayingFromUi = wasPlayingAtGestureStart ?: deck.isPlaybackActive
        if (!wasPlayingFromUi) {
            runtime.userWantsPlayback = false
            engine.pause()
        }
        runtime.wasPlayingBeforeScrub =
            wasPlayingFromUi &&
                runtime.userWantsPlayback &&
                !runtime.forceStayPausedAfterManualPause
        if (runtime.wasPlayingBeforeScrub && allowEngineScratch) {
            val beginResult = engine.beginScratch()
            if (beginResult.isFailure) {
                runtime.isScrubbing = false
                runtime.wasPlayingBeforeScrub = false
                updateDeckState(isLeft) {
                    it.copy(playbackStatusText = statusForError(beginResult.exceptionOrNull(), "Scratch unavailable"))
                }
                return
            }
            runtime.isEngineScratchActive = true
        } else {
            runtime.isEngineScratchActive = false
            engine.pause()
        }
        runtime.scratchCurrentTime = engine.currentTimeSeconds
        runtime.lastCommittedScratchTime = runtime.scratchCurrentTime
        runtime.lastScratchUpdateNanos = 0L
        runtime.lastScratchCommitNanos = 0L
        runtime.smoothedScratchAngularVelocity = 0.0
        runtime.latestScratchAngularVelocity = 0.0
        runtime.latestScratchDirection = 1.0
        runtime.scratchMode = ScratchMode.SCRUB

        updateDeckState(isLeft) {
            it.copy(
                scratchInteractionState = ScratchInteractionState.TOUCH_DOWN,
                playbackStatusText = "Scratching",
            )
        }
    }

    private fun updateWaveformScratch(
        isLeft: Boolean,
        deltaX: Double,
    ) {
        val zoom = currentDeckState(isLeft).waveformZoom
        val pointsPerRevolution = max(MIN_WAVEFORM_POINTS_PER_REVOLUTION, WAVEFORM_POINTS_PER_REVOLUTION * zoom)
        val angleDelta = -(deltaX / pointsPerRevolution) * (Math.PI * 2.0)
        updateDeckScratch(
            isLeft = isLeft,
            angleDelta = angleDelta,
            allowEngineScratchIfBeginNeeded = false,
        )
    }

    private fun updatePlatterScratch(
        isLeft: Boolean,
        angleDelta: Double,
    ) = updateDeckScratch(
        isLeft = isLeft,
        angleDelta = angleDelta,
        allowEngineScratchIfBeginNeeded = true,
    )

    private fun updateDeckScratch(
        isLeft: Boolean,
        angleDelta: Double,
        allowEngineScratchIfBeginNeeded: Boolean,
    ) {
        val runtime = runtimeForDeck(isLeft)
        val engine = engineForDeck(isLeft)
        if (!runtime.isScrubbing) {
            beginDeckScratch(
                isLeft = isLeft,
                wasPlayingAtGestureStart = currentDeckState(isLeft).isPlaybackActive,
                allowEngineScratch = allowEngineScratchIfBeginNeeded,
            )
            if (!runtimeForDeck(isLeft).isScrubbing) return
        }

        val nowNanos = System.nanoTime()
        val deltaNanos = if (runtime.lastScratchUpdateNanos > 0L) {
            nowNanos - runtime.lastScratchUpdateNanos
        } else {
            16_666_666L
        }
        val deltaSeconds = max(deltaNanos.toDouble() / 1_000_000_000.0, 0.001)

        val isJitter = kotlin.math.abs(angleDelta) < SCRATCH_JITTER_ANGLE_THRESHOLD &&
            kotlin.math.abs(runtime.smoothedScratchAngularVelocity) < SCRATCH_JITTER_VELOCITY_THRESHOLD
        if (isJitter) {
            // Keep held finger locked in place: do not treat micro-noise as movement updates.
            runtime.latestScratchAngularVelocity = 0.0
            runtime.smoothedScratchAngularVelocity = 0.0
            val nanosSinceCommit = nowNanos - runtime.lastScratchCommitNanos
            if (runtime.lastScratchCommitNanos <= 0L || nanosSinceCommit >= MIN_SCRUB_COMMIT_INTERVAL_NANOS) {
                commitScratchAudio(isLeft = isLeft, force = true)
            }
            return
        }

        // Update movement timestamp only for meaningful deltas.
        runtime.lastScratchUpdateNanos = nowNanos
        runtime.latestScratchAngularVelocity = angleDelta / deltaSeconds
        if (kotlin.math.abs(angleDelta) >= SCRATCH_DIRECTION_ANGLE_THRESHOLD) {
            runtime.latestScratchDirection = if (angleDelta >= 0.0) 1.0 else -1.0
        }
        runtime.smoothedScratchAngularVelocity +=
            (runtime.latestScratchAngularVelocity - runtime.smoothedScratchAngularVelocity) * SCRATCH_VELOCITY_SMOOTHING

        val isScratchMode = kotlin.math.abs(runtime.smoothedScratchAngularVelocity) >= SCRATCH_ANGULAR_VELOCITY_THRESHOLD
        runtime.scratchMode = if (isScratchMode) ScratchMode.SCRATCH else ScratchMode.SCRUB

        val secondsPerRadian = if (isScratchMode) SCRATCH_SECONDS_PER_RADIAN else SCRUB_SECONDS_PER_RADIAN
        val rawTimeDelta = angleDelta * secondsPerRadian
        val maxStep = if (isScratchMode) MAX_SCRATCH_MODE_STEP else MAX_SCRUB_MODE_STEP
        val clampedDelta = rawTimeDelta.coerceIn(-maxStep, maxStep)
        val duration = engine.totalDurationSeconds
        runtime.scratchCurrentTime = (runtime.scratchCurrentTime + clampedDelta).coerceIn(0.0, duration)
        runtime.physics = runtime.physics.applyAngularDrag(deltaAngle = angleDelta, deltaTime = deltaSeconds)

        updateDeckState(isLeft) { deck ->
            deck.copy(
                scratchInteractionState = ScratchInteractionState.DRAGGING,
                platterRotationDegrees = runtime.unwrappedPlatterDegrees,
                playbackTimeText = formatPlaybackTime(runtime.scratchCurrentTime, duration),
                playbackProgress = if (duration > 0.0) (runtime.scratchCurrentTime / duration).coerceIn(0.0, 1.0) else 0.0,
            )
        }
        commitScratchAudio(isLeft = isLeft, force = false)
    }

    private fun endDeckScratch(isLeft: Boolean) {
        val runtime = runtimeForDeck(isLeft)
        val engine = engineForDeck(isLeft)
        if (!runtime.isScrubbing) return

        updateDeckState(isLeft) { it.copy(scratchInteractionState = ScratchInteractionState.RELEASE) }
        commitScratchAudio(isLeft = isLeft, force = true)
        val totalDuration = engine.totalDurationSeconds
        val isAtTrackEnd = runtime.scratchCurrentTime >= (totalDuration - TRACK_END_TOLERANCE)
        val shouldResumePlayback =
            runtime.wasPlayingBeforeScrub &&
                !runtime.forceStayPausedAfterManualPause &&
                !isAtTrackEnd
        if (runtime.isEngineScratchActive) {
            val endResult = engine.endScratch(resumePlayback = false)
            if (endResult.isFailure) {
                updateDeckState(isLeft) { it.copy(playbackStatusText = statusForError(endResult.exceptionOrNull(), "Scratch release failed")) }
            }
        } else {
            engine.seekTo(runtime.scratchCurrentTime)
            engine.pause()
        }

        runtime.isScrubbing = false
        runtime.wasPlayingBeforeScrub = false
        runtime.isEngineScratchActive = false
        runtime.lastScratchUpdateNanos = 0L
        runtime.smoothedScratchAngularVelocity = 0.0
        runtime.latestScratchAngularVelocity = 0.0
        runtime.latestScratchDirection = 1.0
        runtime.scratchMode = ScratchMode.SCRUB

        if (shouldResumePlayback) {
            runtime.userWantsPlayback = true
            engine.play()
        } else if (!isAtTrackEnd) {
            runtime.userWantsPlayback = false
            engine.pause()
            engine.seekTo(runtime.scratchCurrentTime)
            engine.pause()
        } else {
            runtime.userWantsPlayback = false
        }

        updateDeckState(isLeft) {
            it.copy(
                scratchInteractionState = ScratchInteractionState.IDLE,
                playbackStatusText = if (isAtTrackEnd) "Stopped" else "",
            )
        }
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
        syncDeckFromEngine(isLeft)
    }

    private fun updateDeckPressureTouch(
        isLeft: Boolean,
        pressure: Double,
        direction: Double,
    ) {
        val runtime = runtimeForDeck(isLeft)
        val deck = currentDeckState(isLeft)
        if (!deck.hasSelectedTrack || runtime.isScrubbing) return
        if (runtime.pressureStartTargetBpm == null) {
            runtime.pressureStartTargetBpm = deck.targetBpm
        }
        runtime.pressureIntensity = pressure.coerceIn(0.0, 1.0)
        runtime.pressureDirection = if (direction >= 0.0) 1.0 else -1.0
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
    }

    private fun endDeckPressureTouch(isLeft: Boolean) {
        val runtime = runtimeForDeck(isLeft)
        val startBpm = runtime.pressureStartTargetBpm ?: return
        runtime.pressureStartTargetBpm = null
        runtime.pressureIntensity = 0.0
        runtime.pressureDirection = -1.0
        updateDeckState(isLeft) { deck ->
            deck.copy(targetBpm = startBpm.coerceIn(MIN_BPM, MAX_BPM))
        }
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
    }

    private fun commitScratchAudio(
        isLeft: Boolean,
        force: Boolean,
    ) {
        val runtime = runtimeForDeck(isLeft)
        val engine = engineForDeck(isLeft)
        val nowNanos = System.nanoTime()
        val moved = kotlin.math.abs(runtime.scratchCurrentTime - runtime.lastCommittedScratchTime)
        val isScratchMode = runtime.scratchMode == ScratchMode.SCRATCH
        val minIntervalNanos = if (isScratchMode) MIN_SCRATCH_COMMIT_INTERVAL_NANOS else MIN_SCRUB_COMMIT_INTERVAL_NANOS
        val minDelta = if (isScratchMode) MIN_SCRATCH_COMMIT_DELTA else MIN_SCRUB_COMMIT_DELTA
        if (!force) {
            val elapsed = nowNanos - runtime.lastScratchCommitNanos
            if (runtime.lastScratchCommitNanos > 0L && elapsed < minIntervalNanos && moved < minDelta) return
        }

        if (!runtime.isEngineScratchActive) {
            val seekResult = engine.seekTo(runtime.scratchCurrentTime)
            if (seekResult.isSuccess) {
                engine.pause()
                runtime.lastScratchCommitNanos = nowNanos
                runtime.lastCommittedScratchTime = runtime.scratchCurrentTime
            } else if (!force) {
                updateDeckState(isLeft) { it.copy(playbackStatusText = statusForError(seekResult.exceptionOrNull(), "Scrub unavailable")) }
            }
            return
        }

        val signedVelocity = if (runtime.wasPlayingBeforeScrub && !runtime.forceStayPausedAfterManualPause) {
            val absoluteVelocity = kotlin.math.abs(runtime.latestScratchAngularVelocity)
            if (absoluteVelocity < 0.0005) {
                0.0
            } else {
                absoluteVelocity * runtime.latestScratchDirection
            }
        } else {
            0.0
        }
        val result = engine.scratchTo(runtime.scratchCurrentTime, signedVelocity)
        if (result.isSuccess) {
            runtime.lastScratchCommitNanos = nowNanos
            runtime.lastCommittedScratchTime = runtime.scratchCurrentTime
        } else if (!force) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = statusForError(result.exceptionOrNull(), "Scrub unavailable")) }
        }
    }

    private fun stepTurntablePhysics(isLeft: Boolean): Double {
        val runtime = runtimeForDeck(isLeft)
        if (runtime.isScrubbing) {
            return publishTurntableRotation(isLeft, runtime.physics.platterPosition)
        }
        val engine = engineForDeck(isLeft)
        val nowNanos = System.nanoTime()
        val deltaSeconds = if (runtime.lastPhysicsStepNanos > 0L) {
            ((nowNanos - runtime.lastPhysicsStepNanos).toDouble() / 1_000_000_000.0).coerceIn(0.0, 0.1)
        } else {
            1.0 / 60.0
        }
        runtime.lastPhysicsStepNanos = nowNanos
        val shouldDrive = engine.playbackState == AudioPlaybackState.PLAYING
        val driveAngularVelocity = if (shouldDrive) BASE_PLATTER_ANGULAR_VELOCITY * engine.playbackRate else null
        runtime.physics = runtime.physics.step(deltaTime = deltaSeconds, driveAngularVelocity = driveAngularVelocity)
        return publishTurntableRotation(isLeft, runtime.physics.platterPosition)
    }

    private fun publishTurntableRotation(
        isLeft: Boolean,
        wrappedPositionRadians: Double,
    ): Double {
        val runtime = runtimeForDeck(isLeft)
        val wrappedDegrees = wrappedPositionRadians * 180.0 / Math.PI
        val lastWrapped = runtime.lastWrappedPlatterDegrees
        if (lastWrapped == null) {
            runtime.lastWrappedPlatterDegrees = wrappedDegrees
            runtime.unwrappedPlatterDegrees = wrappedDegrees
        } else {
            var delta = wrappedDegrees - lastWrapped
            if (delta > 180.0) delta -= 360.0
            if (delta < -180.0) delta += 360.0
            runtime.lastWrappedPlatterDegrees = wrappedDegrees
            runtime.unwrappedPlatterDegrees += delta
        }
        return runtime.unwrappedPlatterDegrees
    }

    private fun updateDeckState(
        isLeft: Boolean,
        transform: (TurntableDeckUiState) -> TurntableDeckUiState,
    ) {
        _screenState.update { state ->
            if (isLeft) {
                state.copy(leftDeck = transform(state.leftDeck))
            } else {
                state.copy(rightDeck = transform(state.rightDeck))
            }
        }
    }

    private fun currentDeckState(isLeft: Boolean): TurntableDeckUiState =
        if (isLeft) _screenState.value.leftDeck else _screenState.value.rightDeck

    private fun engineForDeck(isLeft: Boolean): AudioEngineController =
        if (isLeft) leftEngine else rightEngine

    private fun runtimeForDeck(isLeft: Boolean): DeckRuntime = if (isLeft) leftRuntime else rightRuntime

    private fun waveformJobForDeck(isLeft: Boolean): Job? = if (isLeft) leftWaveformJob else rightWaveformJob

    private fun setWaveformJobForDeck(
        isLeft: Boolean,
        job: Job?,
    ) {
        if (isLeft) {
            leftWaveformJob = job
        } else {
            rightWaveformJob = job
        }
    }

    private fun loadWaveformForDeck(
        isLeft: Boolean,
        uri: String,
    ) {
        waveformJobForDeck(isLeft)?.cancel()
        val job = viewModelScope.launch {
            try {
                val finalWaveform = withContext(Dispatchers.Default) {
                    waveformAnalyzer.generateWaveform(
                        sourceUri = uri,
                        sampleCount = WAVEFORM_SAMPLE_COUNT,
                        appContext = appContextForAnalysis,
                    ) { progress ->
                        updateDeckState(isLeft) { deck ->
                            deck.copy(
                                waveformData = progress.samples.copyOf(),
                                isWaveformLoading = progress.completedBuckets < progress.totalBuckets,
                                waveformText = if (progress.completedBuckets < progress.totalBuckets) {
                                    "Waveform ${((progress.fraction * 100.0).toInt())}%"
                                } else {
                                    "Waveform Ready"
                                },
                            )
                        }
                    }
                }
                updateDeckState(isLeft) { deck ->
                    deck.copy(
                        waveformData = finalWaveform.copyOf(),
                        isWaveformLoading = false,
                        waveformText = if (finalWaveform.isNotEmpty()) "Waveform Ready" else "Waveform unavailable",
                    )
                }
                if (finalWaveform.isNotEmpty()) {
                    updateTrackWaveformCache(uri = uri, waveform = finalWaveform)
                }
                detectOfflineBpmForDeck(isLeft = isLeft, uri = uri, waveform = finalWaveform)
            } catch (_: Throwable) {
                updateDeckState(isLeft) { deck ->
                    deck.copy(
                        isWaveformLoading = false,
                        waveformData = floatArrayOf(),
                        waveformText = "Waveform unavailable",
                        isBpmLoading = false,
                        bpmDetectionStatusText = "BPM detection unavailable (waveform unavailable).",
                    )
                }
                updateTrackBpmCache(
                    uri = uri,
                    bpm = null,
                    confidence = null,
                    statusText = "BPM detection unavailable (waveform unavailable).",
                )
            }
        }
        setWaveformJobForDeck(isLeft, job)
    }

    private fun detectOfflineBpmForDeck(
        isLeft: Boolean,
        uri: String,
        waveform: FloatArray,
    ) {
        if (waveform.isEmpty()) {
            val status = "BPM detection unavailable (empty waveform)."
            updateDeckState(isLeft) { deck ->
                deck.copy(
                    isBpmLoading = false,
                    bpmDetectionStatusText = status,
                )
            }
            updateTrackBpmCache(uri = uri, bpm = null, confidence = null, statusText = status)
            return
        }

        if (isLeft) {
            leftOfflineBpmJob?.cancel()
        } else {
            rightOfflineBpmJob?.cancel()
        }

        val job = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val input = synthesizeTempoInputFromWaveform(waveform = waveform)
                tempoDetector.detectTempo(input)
            }

            when (result) {
                is BpmResult.Detected -> {
                    val clampedBpm = result.bpm.coerceIn(MIN_BPM, MAX_BPM)
                    applyDetectedDeckBpm(
                        isLeft = isLeft,
                        uri = uri,
                        bpm = clampedBpm,
                        confidence = result.confidence,
                    )
                }
                is BpmResult.Unavailable -> {
                    val estimated = estimateBpmFromWaveform(waveform)
                    if (estimated != null) {
                        applyDetectedDeckBpm(
                            isLeft = isLeft,
                            uri = uri,
                            bpm = estimated,
                            confidence = 0.35,
                        )
                        val status = String.format("Estimated %.1f BPM (fallback)", estimated)
                        updateDeckState(isLeft) { deck -> deck.copy(bpmDetectionStatusText = status) }
                        updateTrackBpmCache(uri = uri, bpm = estimated, confidence = 0.35, statusText = status)
                    } else {
                        val status = "BPM detection unavailable (${result.reason}). Manual control active."
                        updateDeckState(isLeft) { deck ->
                            val original = deck.originalBpm
                            val target = if (original > 0.0) original else 120.0
                            deck.copy(
                                isBpmLoading = false,
                                bpmDetectionStatusText = status,
                                targetBpm = target,
                                bpmText = formatDeckBpmText(target = target, original = target),
                            )
                        }
                        updateTrackBpmCache(uri = uri, bpm = null, confidence = null, statusText = status)
                    }
                }
            }
        }

        if (isLeft) {
            leftOfflineBpmJob = job
        } else {
            rightOfflineBpmJob = job
        }
    }

    private fun synthesizeTempoInputFromWaveform(waveform: FloatArray): TempoInputBuffer {
        val sampleRate = 44_100.0
        val seconds = OFFLINE_BPM_ANALYSIS_SECONDS
        val outputSize = (sampleRate * seconds).toInt().coerceAtLeast(waveform.size)
        val output = FloatArray(outputSize)
        val step = (waveform.size - 1).toDouble() / max(outputSize - 1, 1).toDouble()
        var cursor = 0.0
        for (index in 0 until outputSize) {
            val leftIndex = cursor.toInt().coerceIn(0, waveform.lastIndex)
            val rightIndex = min(leftIndex + 1, waveform.lastIndex)
            val frac = cursor - leftIndex
            val amplitude = waveform[leftIndex] * (1.0 - frac) + waveform[rightIndex] * frac
            output[index] = ((amplitude * 2.0) - 1.0).toFloat()
            cursor += step
        }
        return TempoInputBuffer(
            samples = output,
            sampleRate = sampleRate,
            channelCount = 1,
            isInterleaved = false,
        )
    }

    private fun applyDetectedDeckBpm(
        isLeft: Boolean,
        uri: String,
        bpm: Double,
        confidence: Double,
    ) {
        val status = String.format("Detected %.1f BPM (acc. %.2f)", bpm, confidence)
        updateDeckState(isLeft) { deck ->
            val target = if (deck.isPitchLockedToExternalBpm) deck.targetBpm else bpm
            val original = bpm
            deck.copy(
                originalBpm = original,
                targetBpm = target,
                bpmText = formatDeckBpmText(target = target, original = original),
                isBpmLoading = false,
                bpmDetectionStatusText = status,
            )
        }

        updateTrackBpmCache(uri = uri, bpm = bpm, confidence = confidence, statusText = status)
        applyDeckPlaybackRateAndBpmText(isLeft = isLeft)
    }

    private fun applyCachedTrackAnalysis(
        isLeft: Boolean,
        uri: String,
        cached: TrackAnalysisCacheEntry,
    ) {
        val waveform = cached.waveform
        if (waveform.isEmpty()) {
            loadWaveformForDeck(isLeft = isLeft, uri = uri)
            return
        }

        updateDeckState(isLeft) { deck ->
            deck.copy(
                waveformData = waveform.copyOf(),
                isWaveformLoading = false,
                waveformText = "Waveform Ready",
                isBpmLoading = cached.bpm == null,
                bpmDetectionStatusText = cached.statusText ?: deck.bpmDetectionStatusText,
            )
        }

        if (cached.bpm != null) {
            applyDetectedDeckBpm(
                isLeft = isLeft,
                uri = uri,
                bpm = cached.bpm,
                confidence = cached.confidence ?: 0.35,
            )
            if (!cached.statusText.isNullOrBlank()) {
                updateDeckState(isLeft) { deck -> deck.copy(bpmDetectionStatusText = cached.statusText) }
            }
        } else {
            detectOfflineBpmForDeck(isLeft = isLeft, uri = uri, waveform = waveform)
        }
    }

    private fun getTrackAnalysis(uri: String): TrackAnalysisCacheEntry? =
        analysisCacheLock.withLock {
            trackAnalysisCache[uri]?.copy(waveform = trackAnalysisCache[uri]?.waveform?.copyOf() ?: floatArrayOf())
        }

    private fun updateTrackWaveformCache(
        uri: String,
        waveform: FloatArray,
    ) {
        analysisCacheLock.withLock {
            val existing = trackAnalysisCache[uri]
            trackAnalysisCache[uri] =
                TrackAnalysisCacheEntry(
                    waveform = waveform.copyOf(),
                    bpm = existing?.bpm,
                    confidence = existing?.confidence,
                    statusText = existing?.statusText,
                )
        }
    }

    private fun updateTrackBpmCache(
        uri: String,
        bpm: Double?,
        confidence: Double?,
        statusText: String?,
    ) {
        analysisCacheLock.withLock {
            val existing = trackAnalysisCache[uri]
            trackAnalysisCache[uri] =
                TrackAnalysisCacheEntry(
                    waveform = existing?.waveform?.copyOf() ?: floatArrayOf(),
                    bpm = bpm,
                    confidence = confidence,
                    statusText = statusText,
                )
        }
    }

    private fun startMicrophoneBpmDetection() {
        if (_screenState.value.root.isMicrophoneBpmDetectionActive) return

        microphoneBpmPipeline.reset()
        latestExternalBpm = null
        _screenState.update { state ->
            state.copy(
                root = state.root.copy(
                    isMicrophoneBpmDetectionActive = true,
                    isExternalBpmLoading = true,
                    externalBpmStatusText = "Listening to MIC...",
                ),
            )
        }

        val result = leftEngine.startMicrophoneCapture { frame ->
            microphoneBpmPipeline.ingest(frame)
        }
        if (result.isFailure) {
            _screenState.update { state ->
                state.copy(
                    root = state.root.copy(
                        isMicrophoneBpmDetectionActive = false,
                        isExternalBpmLoading = false,
                        externalBpmStatusText = "Mic BPM unavailable: capture startup failed.",
                    ),
                )
            }
        }
    }

    private fun stopMicrophoneBpmDetection() {
        leftEngine.stopMicrophoneCapture()
        microphoneBpmPipeline.reset()
        latestExternalBpm = null
        _screenState.update { state ->
            state.copy(
                root = state.root.copy(
                    isMicrophoneBpmDetectionActive = false,
                    isExternalBpmLoading = false,
                    externalBpmStatusText = "Mic BPM stopped",
                ),
            )
        }
    }

    private fun handleMicrophoneBpmResult(result: BpmResult) {
        when (result) {
            is BpmResult.Detected -> {
                val detected = result.bpm.coerceIn(MIN_BPM, MAX_BPM)
                val bpm = latestExternalBpm?.let { previous ->
                    previous + ((detected - previous) * LIVE_MIC_BPM_SMOOTHING)
                } ?: detected
                latestExternalBpm = bpm
                _screenState.update { state ->
                    state.copy(
                        root = state.root.copy(
                            externalBpmText = String.format("%.1f BPM", bpm),
                            externalBpmStatusText = String.format(
                                "Detected %.1f BPM (acc. %.2f)",
                                bpm,
                                result.confidence,
                            ),
                            isExternalBpmLoading = false,
                        ),
                    )
                }
                if (_screenState.value.root.isPitchLockedToExternalBpm) {
                    applyPitchLockToLeftDeck(bpm)
                }
            }
            is BpmResult.Unavailable -> {
                _screenState.update { state ->
                    val hasDetectedBpm = state.root.externalBpmText != "-- BPM"
                    if (hasDetectedBpm) {
                        state.copy(
                            root = state.root.copy(
                                isExternalBpmLoading = false,
                            ),
                        )
                    } else {
                        state.copy(
                            root = state.root.copy(
                                externalBpmStatusText = "Listening... no stable tempo yet",
                                isExternalBpmLoading = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun setPitchLockEnabled(
        isEnabled: Boolean,
        externalBpm: Double?,
    ) {
        if (!isEnabled) {
            _screenState.update { state ->
                state.copy(
                    root = state.root.copy(isPitchLockedToExternalBpm = false),
                    leftDeck = state.leftDeck.copy(isPitchLockedToExternalBpm = false),
                )
            }
            return
        }

        val bpm = externalBpm ?: return
        _screenState.update { state ->
            state.copy(
                root = state.root.copy(isPitchLockedToExternalBpm = true),
                leftDeck = state.leftDeck.copy(isPitchLockedToExternalBpm = true),
            )
        }
        applyPitchLockToLeftDeck(bpm)
    }

    private fun applyPitchLockToLeftDeck(externalBpm: Double) {
        val clamped = externalBpm.coerceIn(MIN_BPM, MAX_BPM)
        updateDeckState(isLeft = true) { deck ->
            val original = if (deck.originalBpm > 0.0) deck.originalBpm else clamped
            deck.copy(
                isPitchLockedToExternalBpm = true,
                targetBpm = clamped,
                bpmText = formatDeckBpmText(target = clamped, original = original),
            )
        }

        applyDeckPlaybackRateAndBpmText(isLeft = true)
    }

    private fun extractTrackName(uri: String): String {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "android.resource") {
            return "Sample.mp3"
        }

        val contentName = appContextForAnalysis
            ?.contentResolver
            ?.let { resolver ->
                runCatching {
                    resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        }
                }.getOrNull()
            }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (!contentName.isNullOrBlank()) return contentName

        val rawName = parsed.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return rawName ?: "Selected track"
    }

    private fun statusForError(
        throwable: Throwable?,
        fallback: String,
    ): String {
        val error = (throwable as? AudioEngineException)?.error
        return when {
            error != null -> "$fallback (${error.javaClass.simpleName})"
            throwable?.message?.isNotBlank() == true -> "$fallback (${throwable.message})"
            else -> fallback
        }
    }

    private fun TurntableDeckUiState.bumpTargetBpm(delta: Double): TurntableDeckUiState {
        val current = if (targetBpm > 0.0) targetBpm else 120.0
        val next = (current + delta).coerceIn(40.0, 240.0)
        return copy(targetBpm = next, bpmText = String.format("%.1f BPM", next))
    }

    private fun formatDeckBpmText(
        target: Double,
        original: Double,
    ): String {
        val safeOriginal = if (original > 0.0) original else target.coerceAtLeast(1.0)
        return String.format("BPM %.1f | %.3fx", target, (target / safeOriginal))
    }

    private fun applyDeckPlaybackRateAndBpmText(isLeft: Boolean) {
        val deck = currentDeckState(isLeft)
        val runtime = runtimeForDeck(isLeft)
        val original = if (deck.originalBpm > 0.0) deck.originalBpm else 120.0
        val effectiveTarget = effectiveTargetBpmForCurrentState(deck = deck, runtime = runtime)
        val playbackRate = (effectiveTarget / original).coerceIn(0.5, 2.0)
        engineForDeck(isLeft).setPlaybackRate(playbackRate.toFloat())
        updateDeckState(isLeft) {
            it.copy(
                bpmText = String.format("BPM %.1f | %.3fx", it.targetBpm, playbackRate),
                displayedTargetBpm = effectiveTarget,
                isPressureTouchActive = runtime.pressureStartTargetBpm != null,
            )
        }
    }

    private fun effectiveTargetBpmForCurrentState(
        deck: TurntableDeckUiState,
        runtime: DeckRuntime,
    ): Double {
        val pressureStartBpm = runtime.pressureStartTargetBpm ?: return deck.targetBpm
        val normalizedPressure = runtime.pressureIntensity.coerceIn(0.0, 1.0)
        val pressureCurve = normalizedPressure.pow(PRESSURE_CURVE_EXPONENT)
        return if (runtime.pressureDirection < 0.0) {
            val slowdown = pressureCurve * MAX_PRESSURE_SLOWDOWN_FRACTION
            val multiplier = (1.0 - slowdown).coerceAtLeast(MIN_PRESSURE_SLOWDOWN_MULTIPLIER)
            pressureStartBpm * multiplier
        } else {
            val acceleration = pressureCurve * MAX_PRESSURE_ACCELERATION_FRACTION
            val multiplier = (1.0 + acceleration).coerceAtMost(MAX_PRESSURE_ACCELERATION_MULTIPLIER)
            pressureStartBpm * multiplier
        }
    }

    private fun formatPlaybackTime(
        currentSeconds: Double,
        totalSeconds: Double,
    ): String {
        val safeCurrent = max(0.0, currentSeconds)
        val safeTotal = max(0.0, totalSeconds)
        return "${formatClockTime(safeCurrent)} / ${formatClockTime(safeTotal)}"
    }

    private fun formatClockTime(seconds: Double): String {
        val total = min(seconds.toInt(), 99 * 60 + 59)
        val minutes = total / 60
        val remainingSeconds = total % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }

    private fun estimateBpmFromWaveform(waveform: FloatArray): Double? {
        if (waveform.size < 32) return null
        var peakCount = 0
        var index = 1
        while (index < waveform.lastIndex) {
            val previous = waveform[index - 1]
            val current = waveform[index]
            val next = waveform[index + 1]
            if (current > 0.72f && current > previous && current >= next) {
                peakCount += 1
            }
            index += 1
        }
        if (peakCount <= 0) return null
        val ratio = peakCount.toDouble() / waveform.size.toDouble()
        val estimated = (60.0 + (ratio * 7800.0)).coerceIn(MIN_BPM, MAX_BPM)
        return estimated
    }

    private data class DeckRuntime(
        var physics: TurntablePhysicsState = TurntablePhysicsState(),
        var isScrubbing: Boolean = false,
        var wasPlayingBeforeScrub: Boolean = false,
        var isEngineScratchActive: Boolean = false,
        var userWantsPlayback: Boolean = false,
        var blockAutoplayUntilManualStart: Boolean = false,
        var forceStayPausedAfterManualPause: Boolean = false,
        var scratchCurrentTime: Double = 0.0,
        var lastScratchUpdateNanos: Long = 0L,
        var lastScratchCommitNanos: Long = 0L,
        var lastCommittedScratchTime: Double = 0.0,
        var smoothedScratchAngularVelocity: Double = 0.0,
        var latestScratchAngularVelocity: Double = 0.0,
        var latestScratchDirection: Double = 1.0,
        var scratchMode: ScratchMode = ScratchMode.SCRUB,
        var pressureStartTargetBpm: Double? = null,
        var pressureIntensity: Double = 0.0,
        var pressureDirection: Double = -1.0,
        var lastPhysicsStepNanos: Long = 0L,
        var lastWrappedPlatterDegrees: Double? = null,
        var unwrappedPlatterDegrees: Double = 0.0,
    )

    private data class TrackAnalysisCacheEntry(
        val waveform: FloatArray,
        val bpm: Double?,
        val confidence: Double?,
        val statusText: String?,
    )

    private enum class ScratchMode {
        SCRUB,
        SCRATCH,
    }

    private fun TurntablePhysicsState.applyAngularDrag(
        deltaAngle: Double,
        deltaTime: Double,
    ): TurntablePhysicsState {
        val safeDelta = max(deltaTime, 0.001)
        val nextPosition = normalizedAngle(platterPosition + deltaAngle)
        return copy(
            platterPosition = nextPosition,
            angularVelocity = deltaAngle / safeDelta,
        )
    }

    private fun TurntablePhysicsState.step(
        deltaTime: Double,
        driveAngularVelocity: Double?,
    ): TurntablePhysicsState {
        val safeDelta = deltaTime.coerceIn(0.0, 0.1)
        if (safeDelta <= 0.0) return this
        val safeInertia = max(inertia, 0.0001)
        val safeDamping = max(damping, 0.0)

        val nextVelocity = if (driveAngularVelocity != null) {
            val blend = min(1.0, safeDelta / safeInertia)
            angularVelocity + ((driveAngularVelocity - angularVelocity) * blend)
        } else {
            angularVelocity * kotlin.math.exp(-safeDamping * safeDelta)
        }
        val nextPosition = normalizedAngle(platterPosition + (nextVelocity * safeDelta))
        return copy(
            platterPosition = nextPosition,
            angularVelocity = nextVelocity,
        )
    }

    private fun normalizedAngle(value: Double): Double {
        val twoPi = Math.PI * 2.0
        var normalized = value % twoPi
        if (normalized < 0.0) normalized += twoPi
        return normalized
    }
}

private class MicrophoneBpmPipeline(
    private val detector: TempoDetector,
) {
    private val lock = ReentrantLock()
    private var onResult: ((BpmResult) -> Unit)? = null

    private var sampleRate: Double? = null
    private val samples = FloatRingBuffer(capacity = MAX_RING_CAPACITY_SAMPLES)
    private var isAnalysisInFlight = false
    private var lastAnalysisNanos: Long = 0L

    fun setResultHandler(handler: (BpmResult) -> Unit) {
        lock.withLock {
            onResult = handler
        }
    }

    fun reset() {
        lock.withLock {
            sampleRate = null
            samples.clear()
            isAnalysisInFlight = false
            lastAnalysisNanos = 0L
        }
    }

    fun ingest(frame: MicrophoneCaptureFrame) {
        var shouldAnalyze = false
        var analysisSamples = floatArrayOf()
        var analysisSampleRate = 0.0
        lock.withLock {
            if (frame.samples.isEmpty() || frame.sampleRate <= 0.0) return

            val frameMono = toMono(frame)
            if (sampleRate == null || kotlin.math.abs((sampleRate ?: 0.0) - frame.sampleRate) > 0.5) {
                sampleRate = frame.sampleRate
                samples.clear()
                isAnalysisInFlight = false
                lastAnalysisNanos = 0L
            }

            samples.append(frameMono)
            val activeSampleRate = sampleRate ?: frame.sampleRate
            val maxStoredSamples = (activeSampleRate * MAX_STORED_SECONDS).toInt()
            val availableSamples = min(samples.size, maxStoredSamples)

            val now = System.nanoTime()
            if (isAnalysisInFlight) return
            if (lastAnalysisNanos > 0L && now - lastAnalysisNanos < ANALYSIS_INTERVAL_NANOS) return

            val minRequired = (activeSampleRate * MIN_ANALYSIS_WINDOW_SECONDS).toInt()
            if (availableSamples < minRequired) return

            val analysisWindow = min((activeSampleRate * MAX_ANALYSIS_WINDOW_SECONDS).toInt(), maxStoredSamples)
            analysisSamples = samples.toRecentFloatArray(analysisWindow)
            analysisSampleRate = activeSampleRate
            isAnalysisInFlight = true
            lastAnalysisNanos = now
            shouldAnalyze = true
        }

        if (!shouldAnalyze) return

        Thread {
            val result = runCatching {
                detector.detectTempo(
                    TempoInputBuffer(
                        samples = analysisSamples,
                        sampleRate = analysisSampleRate,
                        channelCount = 1,
                        isInterleaved = false,
                    ),
                )
            }.getOrElse {
                BpmResult.Unavailable(reason = "Mic detection failed")
            }

            val callback = lock.withLock {
                isAnalysisInFlight = false
                onResult
            }
            callback?.invoke(result)
        }.start()
    }

    private fun toMono(frame: MicrophoneCaptureFrame): FloatArray {
        if (frame.channelCount <= 1) return frame.samples
        val frameCount = frame.samples.size / frame.channelCount
        if (frameCount <= 0) return FloatArray(0)
        val mono = FloatArray(frameCount)
        if (frame.isInterleaved) {
            for (index in 0 until frameCount) {
                var sum = 0f
                val base = index * frame.channelCount
                for (ch in 0 until frame.channelCount) {
                    sum += frame.samples[base + ch]
                }
                mono[index] = sum / frame.channelCount.toFloat()
            }
        } else {
            for (index in 0 until frameCount) {
                var sum = 0f
                for (ch in 0 until frame.channelCount) {
                    sum += frame.samples[ch * frameCount + index]
                }
                mono[index] = sum / frame.channelCount.toFloat()
            }
        }
        return mono
    }

    private companion object {
        private const val MAX_RING_CAPACITY_SAMPLES = 44_100 * 16
        private const val MIN_ANALYSIS_WINDOW_SECONDS = 4.0
        private const val MAX_ANALYSIS_WINDOW_SECONDS = 8.0
        private const val MAX_STORED_SECONDS = 12.0
        private const val ANALYSIS_INTERVAL_NANOS = 1_000_000_000L
    }
}

private class FloatRingBuffer(
    capacity: Int,
) {
    private val buffer = FloatArray(capacity.coerceAtLeast(1))
    private var head = 0
    private var count = 0

    val size: Int
        get() = count

    fun clear() {
        head = 0
        count = 0
    }

    fun append(values: FloatArray) {
        for (value in values) {
            buffer[head] = value
            head = (head + 1) % buffer.size
            if (count < buffer.size) count += 1
        }
    }

    fun toRecentFloatArray(maxCount: Int): FloatArray {
        if (count == 0 || maxCount <= 0) return FloatArray(0)
        val actualCount = min(maxCount, count)
        val start = (head - actualCount + buffer.size) % buffer.size
        val output = FloatArray(actualCount)
        var cursor = start
        for (index in 0 until actualCount) {
            output[index] = buffer[cursor]
            cursor = (cursor + 1) % buffer.size
        }
        return output
    }
}
