package dev.manelix.mixer.feature.deck

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.manelix.mixer.core.audio.AudioEngineController
import dev.manelix.mixer.core.audio.AudioEngineException
import dev.manelix.mixer.core.audio.AudioEngineRoutingProvider
import dev.manelix.mixer.core.audio.SkeletonAudioEngineController
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

class DeckViewModel : ViewModel() {
    companion object {
        private const val WAVEFORM_SAMPLE_COUNT = 512
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
        private val ALLOWED_PITCH_SENSITIVITY_PERCENTS = listOf(2, 4, 8, 16)
    }

    private val leftEngine: AudioEngineController = SkeletonAudioEngineController()
    private val rightEngine: AudioEngineController = SkeletonAudioEngineController()
    private val waveformAnalyzer: WaveformAnalyzer = ProceduralWaveformAnalyzer()
    private val tempoDetector: TempoDetector = FallbackTempoDetector()
    private val microphoneBpmPipeline = MicrophoneBpmPipeline(detector = tempoDetector)
    private var leftWaveformJob: Job? = null
    private var rightWaveformJob: Job? = null
    private var leftOfflineBpmJob: Job? = null
    private var rightOfflineBpmJob: Job? = null
    private var latestExternalBpm: Double? = null
    private val leftRuntime = DeckRuntime()
    private val rightRuntime = DeckRuntime()

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
    ) {
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

    fun beginLeftDeckWaveformScratch() = beginDeckScratch(isLeft = true)

    fun beginRightDeckWaveformScratch() = beginDeckScratch(isLeft = false)

    fun updateLeftDeckWaveformScratch(deltaX: Double) = updateWaveformScratch(isLeft = true, deltaX = deltaX)

    fun updateRightDeckWaveformScratch(deltaX: Double) = updateWaveformScratch(isLeft = false, deltaX = deltaX)

    fun endLeftDeckWaveformScratch() = endDeckScratch(isLeft = true)

    fun endRightDeckWaveformScratch() = endDeckScratch(isLeft = false)

    fun beginLeftDeckPlatterScratch() = beginDeckScratch(isLeft = true)

    fun beginRightDeckPlatterScratch() = beginDeckScratch(isLeft = false)

    fun updateLeftDeckPlatterScratch(deltaX: Double, deltaY: Double) = updatePlatterScratch(isLeft = true, deltaX = deltaX, deltaY = deltaY)

    fun updateRightDeckPlatterScratch(deltaX: Double, deltaY: Double) = updatePlatterScratch(isLeft = false, deltaX = deltaX, deltaY = deltaY)

    fun endLeftDeckPlatterScratch() = endDeckScratch(isLeft = true)

    fun endRightDeckPlatterScratch() = endDeckScratch(isLeft = false)

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
        val engine = engineForDeck(isLeft)
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

        val loadResult = engine.loadFile(uri)
        if (loadResult.isSuccess) {
            syncDeckFromEngine(isLeft)
            updateDeckState(isLeft) { deck ->
                deck.copy(playbackStatusText = "")
            }
            loadWaveformForDeck(isLeft = isLeft, uri = uri)
        } else {
            val statusText = statusForError(loadResult.exceptionOrNull(), fallback = "Failed to load selected track")
            updateDeckState(isLeft) { deck ->
                deck.copy(
                    playbackStatusText = statusText,
                    playbackState = AudioPlaybackState.IDLE,
                    playbackTimeText = "00:00 / 00:00",
                    playbackProgress = 0.0,
                    isWaveformLoading = false,
                )
            }
        }
    }

    private fun togglePlayPauseDeck(isLeft: Boolean) {
        val engine = engineForDeck(isLeft)
        val deckState = currentDeckState(isLeft)
        if (!deckState.hasSelectedTrack) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = "Select a track first") }
            return
        }

        val result = if (deckState.isPlaybackActive) {
            engine.pause()
            Result.success(Unit)
        } else {
            engine.play()
        }

        if (result.isFailure) {
            updateDeckState(isLeft) { deck ->
                deck.copy(playbackStatusText = statusForError(result.exceptionOrNull(), "Unable to start playback"))
            }
            return
        }

        syncDeckFromEngine(isLeft)
        updateDeckState(isLeft) { deck -> deck.copy(playbackStatusText = "") }
    }

    private fun stopDeck(isLeft: Boolean) {
        val engine = engineForDeck(isLeft)
        val deckState = currentDeckState(isLeft)
        if (!deckState.hasSelectedTrack) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = "Select a track first") }
            return
        }

        engine.pause()
        val seekResult = engine.seekTo(0.0)
        if (seekResult.isFailure) {
            updateDeckState(isLeft) { deck ->
                deck.copy(playbackStatusText = statusForError(seekResult.exceptionOrNull(), "Unable to stop playback"))
            }
            return
        }

        syncDeckFromEngine(isLeft)
        updateDeckState(isLeft) { deck -> deck.copy(playbackStatusText = "Stopped") }
    }

    private fun syncDeckFromEngine(isLeft: Boolean) {
        val engine = engineForDeck(isLeft)
        val runtime = runtimeForDeck(isLeft)
        stepTurntablePhysics(isLeft)
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
            )
        }
    }

    private fun resolvePlaybackStatus(
        currentStatus: String,
        playbackState: AudioPlaybackState,
    ): String {
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
                bpmText = String.format("BPM %.1f | %.3fx", target, target / original),
            )
        }
        engineForDeck(isLeft).setPlaybackRate((target / original).toFloat())
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
        val original = if (deck.originalBpm > 0.0) deck.originalBpm else 120.0
        val playbackRate = (deck.targetBpm / original).coerceIn(0.5, 2.0)
        engineForDeck(isLeft).setPlaybackRate(playbackRate.toFloat())
        updateDeckState(isLeft) {
            it.copy(
                bpmText = String.format("BPM %.1f | %.3fx", it.targetBpm, it.targetBpm / original),
            )
        }
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
        if (engine is SkeletonAudioEngineController) {
            engine.setRoutingPolicy(role = role, panRange = panRange)
        }
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

    private fun beginDeckScratch(isLeft: Boolean) {
        val engine = engineForDeck(isLeft)
        val runtime = runtimeForDeck(isLeft)
        val deck = currentDeckState(isLeft)
        if (!deck.hasSelectedTrack || runtime.isScrubbing) return

        val beginResult = engine.beginScratch()
        if (beginResult.isFailure) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = statusForError(beginResult.exceptionOrNull(), "Scratch unavailable")) }
            return
        }

        runtime.isScrubbing = true
        runtime.wasPlayingBeforeScrub = deck.isPlaybackActive
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
        updateDeckScratch(isLeft = isLeft, angleDelta = angleDelta)
    }

    private fun updatePlatterScratch(
        isLeft: Boolean,
        deltaX: Double,
        deltaY: Double,
    ) {
        val projectedDelta = deltaX - deltaY
        val angleDelta = -(projectedDelta / PLATTER_POINTS_PER_REVOLUTION) * (Math.PI * 2.0)
        updateDeckScratch(isLeft = isLeft, angleDelta = angleDelta)
    }

    private fun updateDeckScratch(
        isLeft: Boolean,
        angleDelta: Double,
    ) {
        val runtime = runtimeForDeck(isLeft)
        val engine = engineForDeck(isLeft)
        if (!runtime.isScrubbing) {
            beginDeckScratch(isLeft)
            if (!runtimeForDeck(isLeft).isScrubbing) return
        }

        val nowNanos = System.nanoTime()
        val deltaNanos = if (runtime.lastScratchUpdateNanos > 0L) {
            nowNanos - runtime.lastScratchUpdateNanos
        } else {
            16_666_666L
        }
        runtime.lastScratchUpdateNanos = nowNanos
        val deltaSeconds = max(deltaNanos.toDouble() / 1_000_000_000.0, 0.001)

        runtime.latestScratchAngularVelocity = angleDelta / deltaSeconds
        if (kotlin.math.abs(angleDelta) >= SCRATCH_DIRECTION_ANGLE_THRESHOLD) {
            runtime.latestScratchDirection = if (angleDelta >= 0.0) 1.0 else -1.0
        }
        runtime.smoothedScratchAngularVelocity +=
            (runtime.latestScratchAngularVelocity - runtime.smoothedScratchAngularVelocity) * SCRATCH_VELOCITY_SMOOTHING

        val isScratchMode = kotlin.math.abs(runtime.smoothedScratchAngularVelocity) >= SCRATCH_ANGULAR_VELOCITY_THRESHOLD
        runtime.scratchMode = if (isScratchMode) ScratchMode.SCRATCH else ScratchMode.SCRUB
        val isJitter = kotlin.math.abs(angleDelta) < SCRATCH_JITTER_ANGLE_THRESHOLD &&
            kotlin.math.abs(runtime.smoothedScratchAngularVelocity) < SCRATCH_JITTER_VELOCITY_THRESHOLD
        if (isJitter) return

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
        val shouldResumePlayback = runtime.wasPlayingBeforeScrub && !isAtTrackEnd
        val endResult = engine.endScratch(resumePlayback = shouldResumePlayback)
        if (endResult.isFailure) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = statusForError(endResult.exceptionOrNull(), "Scratch release failed")) }
        }

        runtime.isScrubbing = false
        runtime.wasPlayingBeforeScrub = false
        runtime.lastScratchUpdateNanos = 0L
        runtime.smoothedScratchAngularVelocity = 0.0
        runtime.latestScratchAngularVelocity = 0.0
        runtime.latestScratchDirection = 1.0
        runtime.scratchMode = ScratchMode.SCRUB

        if (!shouldResumePlayback && !isAtTrackEnd) {
            engine.pause()
        }

        updateDeckState(isLeft) {
            it.copy(
                scratchInteractionState = ScratchInteractionState.IDLE,
                playbackStatusText = if (isAtTrackEnd) "Stopped" else "",
            )
        }
        syncDeckFromEngine(isLeft)
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

        val signedVelocity = max(kotlin.math.abs(runtime.latestScratchAngularVelocity), 0.001) * runtime.latestScratchDirection
        val result = engine.scratchTo(runtime.scratchCurrentTime, signedVelocity)
        if (result.isSuccess) {
            runtime.lastScratchCommitNanos = nowNanos
            runtime.lastCommittedScratchTime = runtime.scratchCurrentTime
        } else if (!force) {
            updateDeckState(isLeft) { it.copy(playbackStatusText = statusForError(result.exceptionOrNull(), "Scrub unavailable")) }
        }
    }

    private fun stepTurntablePhysics(isLeft: Boolean) {
        val runtime = runtimeForDeck(isLeft)
        if (runtime.isScrubbing) {
            publishTurntableRotation(isLeft, runtime.physics.platterPosition)
            return
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
        publishTurntableRotation(isLeft, runtime.physics.platterPosition)
    }

    private fun publishTurntableRotation(
        isLeft: Boolean,
        wrappedPositionRadians: Double,
    ) {
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
        updateDeckState(isLeft) { it.copy(platterRotationDegrees = runtime.unwrappedPlatterDegrees) }
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
                detectOfflineBpmForDeck(isLeft = isLeft, waveform = finalWaveform)
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
            }
        }
        setWaveformJobForDeck(isLeft, job)
    }

    private fun detectOfflineBpmForDeck(
        isLeft: Boolean,
        waveform: FloatArray,
    ) {
        if (waveform.isEmpty()) {
            updateDeckState(isLeft) { deck ->
                deck.copy(
                    isBpmLoading = false,
                    bpmDetectionStatusText = "BPM detection unavailable (empty waveform).",
                )
            }
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
                        bpm = clampedBpm,
                        confidence = result.confidence,
                    )
                }
                is BpmResult.Unavailable -> {
                    updateDeckState(isLeft) { deck ->
                        val original = deck.originalBpm
                        val target = if (original > 0.0) original else 120.0
                        deck.copy(
                            isBpmLoading = false,
                            bpmDetectionStatusText = "BPM detection unavailable (${result.reason}). Manual control active.",
                            targetBpm = target,
                            bpmText = formatDeckBpmText(target = target, original = target),
                        )
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
        bpm: Double,
        confidence: Double,
    ) {
        updateDeckState(isLeft) { deck ->
            val target = if (deck.isPitchLockedToExternalBpm) deck.targetBpm else bpm
            val original = bpm
            deck.copy(
                originalBpm = original,
                targetBpm = target,
                bpmText = formatDeckBpmText(target = target, original = original),
                isBpmLoading = false,
                bpmDetectionStatusText = String.format("Detected %.1f BPM (acc. %.2f)", bpm, confidence),
            )
        }

        val deck = currentDeckState(isLeft)
        val playbackRate = if (deck.originalBpm > 0.0) {
            (deck.targetBpm / deck.originalBpm).coerceIn(0.5, 2.0)
        } else {
            1.0
        }
        engineForDeck(isLeft).setPlaybackRate(playbackRate.toFloat())
    }

    private fun startMicrophoneBpmDetection() {
        if (_screenState.value.root.isMicrophoneBpmDetectionActive) return

        microphoneBpmPipeline.reset()
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
                val bpm = result.bpm.coerceIn(MIN_BPM, MAX_BPM)
                latestExternalBpm = bpm
                _screenState.update { state ->
                    state.copy(
                        root = state.root.copy(
                            externalBpmText = String.format("%.1f BPM", bpm),
                            externalBpmStatusText = String.format("Mic BPM (acc. %.2f)", result.confidence),
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
                    val keepLoading = state.root.externalBpmText == "-- BPM"
                    state.copy(
                        root = state.root.copy(
                            externalBpmStatusText = "Listening... no stable tempo yet",
                            isExternalBpmLoading = keepLoading,
                        ),
                    )
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

        val deck = currentDeckState(true)
        val original = if (deck.originalBpm > 0.0) deck.originalBpm else clamped
        val playbackRate = (clamped / original).coerceIn(0.5, 2.0)
        leftEngine.setPlaybackRate(playbackRate.toFloat())
    }

    private fun extractTrackName(uri: String): String {
        val parsed = Uri.parse(uri)
        val rawName = parsed.lastPathSegment?.substringAfterLast('/')
        return if (rawName.isNullOrBlank()) {
            "Selected track"
        } else {
            rawName
        }
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

    private data class DeckRuntime(
        var physics: TurntablePhysicsState = TurntablePhysicsState(),
        var isScrubbing: Boolean = false,
        var wasPlayingBeforeScrub: Boolean = false,
        var scratchCurrentTime: Double = 0.0,
        var lastScratchUpdateNanos: Long = 0L,
        var lastScratchCommitNanos: Long = 0L,
        var lastCommittedScratchTime: Double = 0.0,
        var smoothedScratchAngularVelocity: Double = 0.0,
        var latestScratchAngularVelocity: Double = 0.0,
        var latestScratchDirection: Double = 1.0,
        var scratchMode: ScratchMode = ScratchMode.SCRUB,
        var lastPhysicsStepNanos: Long = 0L,
        var lastWrappedPlatterDegrees: Double? = null,
        var unwrappedPlatterDegrees: Double = 0.0,
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
