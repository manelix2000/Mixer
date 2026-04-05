package dev.manelix.mixer.feature.deck

import android.Manifest
import android.os.Build
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import dev.manelix.mixer.core.common.model.AudioEngineMode
import dev.manelix.mixer.core.common.model.AudioPlaybackState
import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckLayout
import dev.manelix.mixer.core.ui.WaveformView
import dev.manelix.mixer.feature.deck.model.DeckUiState
import dev.manelix.mixer.feature.deck.model.isPlaybackActive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DeckRoute(
    isTablet: Boolean,
    viewModel: DeckViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsPrefs = remember(context) {
        context.getSharedPreferences("mixer_deck_prefs", Context.MODE_PRIVATE)
    }
    val restoredAudioMode = remember(settingsPrefs) {
        runCatching {
            settingsPrefs.getString("audio_engine_mode", AudioEngineMode.STANDARD.name)
                ?.let(AudioEngineMode::valueOf)
                ?: AudioEngineMode.STANDARD
        }.getOrDefault(AudioEngineMode.STANDARD)
    }
    val restoredSplitLayout = remember(settingsPrefs) {
        runCatching {
            settingsPrefs.getString("split_deck_layout", SplitDeckLayout.LEFT_MASTER_RIGHT_CUE.name)
                ?.let(SplitDeckLayout::valueOf)
                ?: SplitDeckLayout.LEFT_MASTER_RIGHT_CUE
        }.getOrDefault(SplitDeckLayout.LEFT_MASTER_RIGHT_CUE)
    }
    val isEmulator = remember {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.lowercase().contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MODEL.contains("sdk_gphone") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
    }
    val leftDeckPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.selectTrackForLeftDeck(it.toString()) }
    }
    val rightDeckPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.selectTrackForRightDeck(it.toString()) }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.toggleMic()
        }
    }
    val audioMimeTypes = remember { arrayOf("audio/*") }
    LaunchedEffect(isTablet) {
        viewModel.initializeForDevice(
            isTablet = isTablet,
            restoredMode = restoredAudioMode,
            restoredLayout = restoredSplitLayout,
            context = context,
        )
    }
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val rootState = screenState.root
    LaunchedEffect(rootState.selectedAudioEngineMode, rootState.selectedSplitDeckLayout) {
        settingsPrefs.edit()
            .putString("audio_engine_mode", rootState.selectedAudioEngineMode.name)
            .putString("split_deck_layout", rootState.selectedSplitDeckLayout.name)
            .apply()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ControlRail(
            isTablet = isTablet,
            areControlsVisible = screenState.areControlsVisible,
            isSettingsVisible = screenState.isSettingsVisible,
            isRightDeckVisible = screenState.isRightDeckVisible,
            isEqualizerVisible = screenState.isEqualizerVisible,
            isMicrophoneBpmActive = rootState.isMicrophoneBpmDetectionActive,
            isPitchLocked = rootState.isPitchLockedToExternalBpm,
            onToggleControls = viewModel::toggleControls,
            onToggleSettings = viewModel::toggleSettings,
            onToggleRightDeck = viewModel::toggleRightDeck,
            onToggleEqualizer = viewModel::toggleEqualizer,
            onToggleMic = {
                if (rootState.isMicrophoneBpmDetectionActive) {
                    viewModel.toggleMic()
                } else if (hasRecordAudioPermission(context)) {
                    viewModel.toggleMic()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onTogglePitchLock = viewModel::togglePitchLock,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(animationSpec = tween(durationMillis = 220)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(
                visible = isTablet || screenState.areControlsVisible,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(durationMillis = 220),
                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 220),
                ) + fadeOut(animationSpec = tween(durationMillis = 220)),
            ) {
                if (!screenState.isSettingsVisible) {
                    if (rootState.selectedAudioEngineMode == AudioEngineMode.SPLIT) {
                        SplitCueControlsCard(
                            showsRightDeck = isTablet || screenState.isRightDeckVisible,
                            leftTrackUri = screenState.leftDeck.selectedTrackUri,
                            rightTrackUri = screenState.rightDeck.selectedTrackUri,
                            leftRole = screenState.leftDeck.splitDeckRole,
                            rightRole = screenState.rightDeck.splitDeckRole,
                            isLeftDeckCueEnabled = rootState.isLeftDeckCueEnabled,
                            isRightDeckCueEnabled = rootState.isRightDeckCueEnabled,
                            cueMixValue = cueMixModeToValue(rootState.cueMixMode),
                            cueMixCode = rootState.cueMixMode.shortCode,
                            cueLevelPercent = rootState.cueLevelPercent,
                            onToggleLeftCue = viewModel::toggleLeftDeckCue,
                            onToggleRightCue = viewModel::toggleRightDeckCue,
                            onCueMixChange = viewModel::setCueMixFromFader,
                            onCueLevelChange = viewModel::setCueLevelPercent,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StandardPanControlsCard(
                                pan = screenState.leftDeck.pan,
                                range = screenState.leftDeck.panControlRange,
                                roleBadge = null,
                                artworkTrackUri = screenState.leftDeck.selectedTrackUri,
                                onPanChange = viewModel::setLeftDeckPan,
                                modifier = Modifier.weight(1f),
                            )
                            if (isTablet || screenState.isRightDeckVisible) {
                                StandardPanControlsCard(
                                    pan = screenState.rightDeck.pan,
                                    range = screenState.rightDeck.panControlRange,
                                    roleBadge = null,
                                    artworkTrackUri = screenState.rightDeck.selectedTrackUri,
                                    onPanChange = viewModel::setRightDeckPan,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = screenState.isSettingsVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                ) {
                    SettingsCard(
                        selectedAudioEngineMode = rootState.selectedAudioEngineMode,
                        selectedSplitDeckLayout = rootState.selectedSplitDeckLayout,
                        onModeChange = viewModel::setAudioEngineMode,
                        onSplitLayoutChange = viewModel::setSplitDeckLayout,
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !screenState.isSettingsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        DeckSurface(
                            title = "Deck A",
                            trackName = screenState.leftDeck.selectedTrackName,
                            selectedTrackUri = screenState.leftDeck.selectedTrackUri,
                            playbackTimeText = screenState.leftDeck.playbackTimeText,
                            waveformText = screenState.leftDeck.waveformText,
                            waveformData = screenState.leftDeck.waveformData,
                            waveformProgress = screenState.leftDeck.playbackProgress,
                            waveformZoom = screenState.leftDeck.waveformZoom,
                            isWaveformLoading = screenState.leftDeck.isWaveformLoading,
                            playbackStatusText = screenState.leftDeck.playbackStatusText,
                            isPlaying = screenState.leftDeck.isPlaybackActive,
                            bpmText = screenState.leftDeck.bpmText,
                            bpmStatusText = screenState.leftDeck.bpmDetectionStatusText,
                            targetBpm = screenState.leftDeck.targetBpm,
                            displayedTargetBpm = screenState.leftDeck.displayedTargetBpm,
                            isPressureTouchActive = screenState.leftDeck.isPressureTouchActive,
                            showExternalBpmBadge = true,
                            externalBpmBadgeText = micBadgeText(rootState),
                            showEqualizerOverlay = screenState.isEqualizerVisible,
                            modifier = Modifier.weight(1f),
                            onImportTrack = { leftDeckPicker.launch(audioMimeTypes) },
                            onImportSampleTrack = {
                                val sampleUri = "android.resource://${context.packageName}/${R.raw.sample}"
                                viewModel.selectTrackForLeftDeck(sampleUri)
                            },
                            showSampleImport = isEmulator,
                            onTapSeek = viewModel::seekLeftDeckFromWaveformTap,
                            onSetWaveformZoom = viewModel::setLeftDeckWaveformZoom,
                            onBeginWaveformScratch = { viewModel.beginLeftDeckWaveformScratch(screenState.leftDeck.isPlaybackActive) },
                            onWaveformScratchDelta = viewModel::updateLeftDeckWaveformScratch,
                            onEndWaveformScratch = viewModel::endLeftDeckWaveformScratch,
                            platterRotationDegrees = screenState.leftDeck.platterRotationDegrees.toFloat(),
                            onBeginPlatterScratch = { viewModel.beginLeftDeckPlatterScratch(screenState.leftDeck.isPlaybackActive) },
                            onPlatterScratchDelta = viewModel::updateLeftDeckPlatterScratch,
                            onEndPlatterScratch = viewModel::endLeftDeckPlatterScratch,
                            onPlatterPressureUpdate = viewModel::updateLeftDeckPressureTouch,
                            onPlatterPressureEnd = viewModel::endLeftDeckPressureTouch,
                            onStartPause = viewModel::togglePlayPauseLeftDeck,
                            onStop = viewModel::stopLeftDeck,
                            isTransportReady = screenState.leftDeck.playbackState != AudioPlaybackState.IDLE,
                            volume = screenState.leftDeck.volume,
                            onVolumeChange = viewModel::setLeftDeckVolume,
                            pitchOffset = pitchOffset(
                                if (screenState.leftDeck.displayedTargetBpm > 0.0) {
                                    screenState.leftDeck.displayedTargetBpm
                                } else {
                                    screenState.leftDeck.targetBpm
                                },
                                screenState.leftDeck.originalBpm,
                            ),
                            pitchSensitivityPercent = screenState.leftDeck.pitchSensitivityPercent,
                            onPitchOffsetChange = viewModel::setLeftDeckPitchOffset,
                            onIncreasePitchSensitivity = viewModel::increaseLeftDeckPitchSensitivity,
                            onDecreasePitchSensitivity = viewModel::decreaseLeftDeckPitchSensitivity,
                            eqLow = screenState.leftDeck.equalizerLow,
                            eqMid = screenState.leftDeck.equalizerMid,
                            eqHigh = screenState.leftDeck.equalizerHigh,
                            onEqLowChange = viewModel::setLeftDeckEqualizerLow,
                            onEqMidChange = viewModel::setLeftDeckEqualizerMid,
                            onEqHighChange = viewModel::setLeftDeckEqualizerHigh,
                        )

                        if (isTablet || screenState.isRightDeckVisible) {
                            DeckSurface(
                                title = "Deck B",
                                trackName = screenState.rightDeck.selectedTrackName,
                                selectedTrackUri = screenState.rightDeck.selectedTrackUri,
                                playbackTimeText = screenState.rightDeck.playbackTimeText,
                                waveformText = screenState.rightDeck.waveformText,
                                waveformData = screenState.rightDeck.waveformData,
                                waveformProgress = screenState.rightDeck.playbackProgress,
                                waveformZoom = screenState.rightDeck.waveformZoom,
                                isWaveformLoading = screenState.rightDeck.isWaveformLoading,
                                playbackStatusText = screenState.rightDeck.playbackStatusText,
                                isPlaying = screenState.rightDeck.isPlaybackActive,
                                bpmText = screenState.rightDeck.bpmText,
                                bpmStatusText = screenState.rightDeck.bpmDetectionStatusText,
                                targetBpm = screenState.rightDeck.targetBpm,
                                displayedTargetBpm = screenState.rightDeck.displayedTargetBpm,
                                isPressureTouchActive = screenState.rightDeck.isPressureTouchActive,
                                showExternalBpmBadge = false,
                                externalBpmBadgeText = null,
                                showEqualizerOverlay = screenState.isEqualizerVisible,
                                modifier = Modifier.weight(1f),
                                onImportTrack = { rightDeckPicker.launch(audioMimeTypes) },
                                onImportSampleTrack = {
                                    val sampleUri = "android.resource://${context.packageName}/${R.raw.sample}"
                                    viewModel.selectTrackForRightDeck(sampleUri)
                                },
                                showSampleImport = isEmulator,
                                onTapSeek = viewModel::seekRightDeckFromWaveformTap,
                                onSetWaveformZoom = viewModel::setRightDeckWaveformZoom,
                                onBeginWaveformScratch = { viewModel.beginRightDeckWaveformScratch(screenState.rightDeck.isPlaybackActive) },
                                onWaveformScratchDelta = viewModel::updateRightDeckWaveformScratch,
                                onEndWaveformScratch = viewModel::endRightDeckWaveformScratch,
                                platterRotationDegrees = screenState.rightDeck.platterRotationDegrees.toFloat(),
                                onBeginPlatterScratch = { viewModel.beginRightDeckPlatterScratch(screenState.rightDeck.isPlaybackActive) },
                                onPlatterScratchDelta = viewModel::updateRightDeckPlatterScratch,
                                onEndPlatterScratch = viewModel::endRightDeckPlatterScratch,
                                onPlatterPressureUpdate = viewModel::updateRightDeckPressureTouch,
                                onPlatterPressureEnd = viewModel::endRightDeckPressureTouch,
                                onStartPause = viewModel::togglePlayPauseRightDeck,
                                onStop = viewModel::stopRightDeck,
                                isTransportReady = screenState.rightDeck.playbackState != AudioPlaybackState.IDLE,
                                volume = screenState.rightDeck.volume,
                                onVolumeChange = viewModel::setRightDeckVolume,
                                pitchOffset = pitchOffset(
                                    if (screenState.rightDeck.displayedTargetBpm > 0.0) {
                                        screenState.rightDeck.displayedTargetBpm
                                    } else {
                                        screenState.rightDeck.targetBpm
                                    },
                                    screenState.rightDeck.originalBpm,
                                ),
                                pitchSensitivityPercent = screenState.rightDeck.pitchSensitivityPercent,
                                onPitchOffsetChange = viewModel::setRightDeckPitchOffset,
                                onIncreasePitchSensitivity = viewModel::increaseRightDeckPitchSensitivity,
                                onDecreasePitchSensitivity = viewModel::decreaseRightDeckPitchSensitivity,
                                eqLow = screenState.rightDeck.equalizerLow,
                                eqMid = screenState.rightDeck.equalizerMid,
                                eqHigh = screenState.rightDeck.equalizerHigh,
                                onEqLowChange = viewModel::setRightDeckEqualizerLow,
                                onEqMidChange = viewModel::setRightDeckEqualizerMid,
                                onEqHighChange = viewModel::setRightDeckEqualizerHigh,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private val ControlRailButtonSize = 30.dp
private val ControlRailIconSize = 15.dp

@Composable
private fun ControlRail(
    isTablet: Boolean,
    areControlsVisible: Boolean,
    isSettingsVisible: Boolean,
    isRightDeckVisible: Boolean,
    isEqualizerVisible: Boolean,
    isMicrophoneBpmActive: Boolean,
    isPitchLocked: Boolean,
    onToggleControls: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleRightDeck: () -> Unit,
    onToggleEqualizer: () -> Unit,
    onToggleMic: () -> Unit,
    onTogglePitchLock: () -> Unit,
) {
    Card(
        modifier = Modifier.width(ControlRailButtonSize),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isTablet) {
                RailButton(
                    icon = if (areControlsVisible) Icons.Filled.Close else Icons.Filled.Menu,
                    text = null,
                    onClick = onToggleControls,
                )
            }

            AnimatedVisibility(
                visible = isTablet || areControlsVisible,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetY = { -it / 2 },
                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetY = { -it / 2 },
                ) + fadeOut(animationSpec = tween(durationMillis = 220)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RailButton(
                        icon = if (isSettingsVisible) Icons.Filled.Settings else Icons.Outlined.Settings,
                        text = null,
                        onClick = onToggleSettings,
                    )
                    if (!isTablet) {
                        RailButton(
                            icon = null,
                            text = null,
                            onClick = onToggleRightDeck,
                            customContent = {
                                TurntableToggleIcon(isActive = isRightDeckVisible)
                            },
                        )
                    }
                    RailButton(
                        icon = if (isMicrophoneBpmActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                        text = null,
                        onClick = onToggleMic,
                    )
                    RailButton(
                        icon = if (isPitchLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        text = null,
                        onClick = onTogglePitchLock,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            RailButton(
                icon = null,
                text = null,
                onClick = onToggleEqualizer,
                customContent = { EqualizerControlIcon(isDisabledState = isEqualizerVisible) },
            )
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    text: String?,
    onClick: () -> Unit,
    customContent: @Composable (() -> Unit)? = null,
    containerColor: Color = Color(0xFF0A84FF),
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(ControlRailButtonSize),
        shape = RoundedCornerShape(7.5.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (customContent != null) {
            customContent()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ControlRailIconSize),
                tint = contentColor,
            )
        } else if (!text.isNullOrBlank()) {
            Text(text = text, fontSize = 13.sp, maxLines = 1, color = contentColor)
        }
    }
}

@Composable
private fun TurntableToggleIcon(isActive: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(1.2.dp, Color.White, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(2.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(7.dp),
            shape = CircleShape,
            color = Color.White,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isActive) "−" else "+",
                    color = Color(0xFF0A84FF),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EqualizerControlIcon(isDisabledState: Boolean) {
    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            EqBar(height = 6.dp)
            EqBar(height = 10.dp)
            EqBar(height = 7.dp)
            EqBar(height = 12.dp)
        }
        if (isDisabledState) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.Red)
                    .graphicsLayer { rotationZ = -35f },
            )
        }
    }
}

@Composable
private fun EqBar(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(height)
            .clip(RoundedCornerShape(1.dp))
            .background(Color.White),
    )
}

@Composable
private fun StandardPanControlsCard(
    pan: Double,
    range: PanControlRange,
    roleBadge: String?,
    artworkTrackUri: String?,
    onPanChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        PanCard(
            pan = pan,
            range = range,
            roleBadge = roleBadge,
            artworkTrackUri = artworkTrackUri,
            onPanChange = onPanChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PanCard(
    pan: Double,
    range: PanControlRange,
    roleBadge: String?,
    artworkTrackUri: String?,
    onPanChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBadge(roleBadge = roleBadge, artworkTrackUri = artworkTrackUri)
            HorizontalFader(
                value = pan.toFloat(),
                valueRange = range.lowerBound.toFloat()..range.upperBound.toFloat(),
                thumbText = panRoutingText(pan),
                onValueChange = { onPanChange(it.toDouble()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SplitCueControlsCard(
    showsRightDeck: Boolean,
    leftTrackUri: String?,
    rightTrackUri: String?,
    leftRole: dev.manelix.mixer.core.common.model.SplitDeckRole?,
    rightRole: dev.manelix.mixer.core.common.model.SplitDeckRole?,
    isLeftDeckCueEnabled: Boolean,
    isRightDeckCueEnabled: Boolean,
    cueMixValue: Float,
    cueMixCode: String,
    cueLevelPercent: Int,
    onToggleLeftCue: () -> Unit,
    onToggleRightCue: () -> Unit,
    onCueMixChange: (Double) -> Unit,
    onCueLevelChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CueDeckButton(
                title = splitCueDeckTitle(role = leftRole, fallback = "Master"),
                isEnabled = isLeftDeckCueEnabled,
                trackUri = leftTrackUri,
                onClick = onToggleLeftCue,
                modifier = Modifier.weight(1f),
            )
            if (showsRightDeck) {
                CueDeckButton(
                    title = splitCueDeckTitle(role = rightRole, fallback = "Cue Deck"),
                    isEnabled = isRightDeckCueEnabled,
                    trackUri = rightTrackUri,
                    onClick = onToggleRightCue,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Mix",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black.copy(alpha = 0.55f),
                )
                HorizontalFader(
                    value = cueMixValue,
                    valueRange = -1f..1f,
                    thumbText = cueMixCode,
                    onValueChange = { onCueMixChange(it.toDouble()) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Cue",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black.copy(alpha = 0.55f),
                )
                HorizontalFader(
                    value = cueLevelPercent.toFloat(),
                    valueRange = 0f..100f,
                    thumbText = "$cueLevelPercent%",
                    onValueChange = { onCueLevelChange(it.toInt().coerceIn(0, 100)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CueDeckButton(
    title: String,
    isEnabled: Boolean,
    trackUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) Color(0x240A84FF) else Color.Black.copy(alpha = 0.08f),
            contentColor = Color.Black,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ArtworkBadge(roleBadge = null, artworkTrackUri = trackUri)
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Surface(
                color = if (isEnabled) Color(0xFF0A84FF) else Color.Gray.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = "CUE",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun splitCueDeckTitle(
    role: dev.manelix.mixer.core.common.model.SplitDeckRole?,
    fallback: String,
): String = when (role) {
    dev.manelix.mixer.core.common.model.SplitDeckRole.MASTER -> "Master"
    dev.manelix.mixer.core.common.model.SplitDeckRole.CUE -> "Cue Deck"
    null -> fallback
}

@Composable
private fun CueBadge(
    deckName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(text = deckName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = "CUE",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HorizontalFader(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    thumbText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalized = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    val baselineNormalized = when {
        valueRange.start <= 0f && valueRange.endInclusive >= 0f ->
            ((0f - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
        valueRange.start >= 0f -> 0f
        else -> 1f
    }
    var widthPx by remember { mutableStateOf(1f) }
    var isDraggingThumb by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(normalized) }
    val thumbWidth = 34.dp
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val usableWidthPx = (widthPx - thumbWidthPx).coerceAtLeast(1f)
    val displayProgress = if (isDraggingThumb) dragProgress else normalized
    LaunchedEffect(normalized, isDraggingThumb) {
        if (!isDraggingThumb) {
            dragProgress = normalized
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF1EFF4),
        tonalElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { widthPx = it.width.toFloat() },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFDADADA))
                    .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(999.dp)),
            )
            val selectedFraction = kotlin.math.abs(displayProgress - baselineNormalized).coerceAtLeast(0.002f)
            val selectedCenter = (displayProgress + baselineNormalized) * 0.5f
            val selectedWidthDp = with(density) { (widthPx * selectedFraction).toDp() }
            val selectedOffsetDp = with(density) { ((selectedCenter - 0.5f) * widthPx).toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = selectedOffsetDp)
                    .width(selectedWidthDp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                // no-op
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { (displayProgress * usableWidthPx).toDp() })
                    .width(thumbWidth)
                    .height(26.dp)
                    .pointerInput(valueRange) {
                        detectDragGestures(
                            onDragStart = {
                                isDraggingThumb = true
                            },
                            onDragEnd = { isDraggingThumb = false },
                            onDragCancel = { isDraggingThumb = false },
                        ) { _, dragAmount ->
                            val next = (dragProgress + (dragAmount.x / usableWidthPx)).coerceIn(0f, 1f)
                            dragProgress = next
                            onValueChange(valueRange.start + (next * rangeSpan))
                        }
                    },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF4F4F4),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = thumbText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    selectedAudioEngineMode: AudioEngineMode,
    selectedSplitDeckLayout: SplitDeckLayout,
    onModeChange: (AudioEngineMode) -> Unit,
    onSplitLayoutChange: (SplitDeckLayout) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Settings", style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split Audio Engine Mode", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(
                    checked = selectedAudioEngineMode == AudioEngineMode.SPLIT,
                    onCheckedChange = { enabled ->
                        onModeChange(if (enabled) AudioEngineMode.SPLIT else AudioEngineMode.STANDARD)
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SegButton(
                    text = "L:Master / R:Cue",
                    selected = selectedSplitDeckLayout == SplitDeckLayout.LEFT_MASTER_RIGHT_CUE,
                    onClick = { onSplitLayoutChange(SplitDeckLayout.LEFT_MASTER_RIGHT_CUE) },
                )
                SegButton(
                    text = "L:Cue / R:Master",
                    selected = selectedSplitDeckLayout == SplitDeckLayout.LEFT_CUE_RIGHT_MASTER,
                    onClick = { onSplitLayoutChange(SplitDeckLayout.LEFT_CUE_RIGHT_MASTER) },
                )
            }
            HorizontalDivider()
            Text("Current mode: ${selectedAudioEngineMode.name}")
            Text("Split layout: ${selectedSplitDeckLayout.name}")
        }
    }
}

@Composable
private fun RowScope.SegButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(text, fontSize = 11.sp) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(text, fontSize = 11.sp) }
    }
}

@Composable
private fun ArtworkBadge(roleBadge: String?) {
    ArtworkBadge(roleBadge = roleBadge, artworkTrackUri = null)
}

@Composable
private fun ArtworkBadge(
    roleBadge: String?,
    artworkTrackUri: String?,
) {
    val artworkBitmap = rememberTrackArtworkBitmap(artworkTrackUri)
    Box {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (artworkBitmap != null) {
                Image(
                    bitmap = artworkBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!roleBadge.isNullOrBlank()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    roleBadge,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun VerticalPitchFader(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    thumbText: String,
    onValueChange: (Float) -> Unit,
    isInverted: Boolean = false,
    showPopoverOnLeft: Boolean = false,
    enabled: Boolean = true,
    disabledAlpha: Float = 1f,
    onInteractionChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalized = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    val baselineNormalized = when {
        valueRange.start <= 0f && valueRange.endInclusive >= 0f ->
            ((0f - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
        valueRange.start >= 0f -> 0f
        else -> 1f
    }
    var isDragging by remember { mutableStateOf(false) }
    var isThumbPressed by remember { mutableStateOf(false) }
    var dragValueProgress by remember { mutableFloatStateOf(normalized) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    val thumbHeight = 26.dp
    val thumbWidth = 34.dp
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val usableHeightPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val animatedValueProgress by animateFloatAsState(
        targetValue = if (isDragging) dragValueProgress else normalized,
        animationSpec = tween(durationMillis = 110, easing = LinearEasing),
        label = "verticalPitchFaderProgress",
    )
    val valueProgress = if (isDragging) dragValueProgress else animatedValueProgress
    val displayProgress = if (isInverted) 1f - valueProgress else valueProgress
    val baselineDisplayProgress = if (isInverted) 1f - baselineNormalized else baselineNormalized
    val selectedHeightFraction = abs(displayProgress - baselineDisplayProgress).coerceAtLeast(0.01f)
    val selectedCenter = (displayProgress + baselineDisplayProgress) * 0.5f
    val thumbCenterY = (1f - displayProgress) * usableHeightPx + (thumbHeightPx * 0.5f)
    val latestThumbCenterY by rememberUpdatedState(thumbCenterY)
    val latestTrackWidthPx by rememberUpdatedState(trackWidthPx)
    val latestThumbWidthPx by rememberUpdatedState(thumbWidthPx)
    val latestThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val latestUsableHeightPx by rememberUpdatedState(usableHeightPx)
    val isInteracting = isDragging || isThumbPressed

    LaunchedEffect(normalized, isDragging) {
        if (!isDragging) dragValueProgress = normalized
    }
    LaunchedEffect(isInteracting) {
        onInteractionChanged?.invoke(isInteracting)
    }

    Box(
        modifier = modifier
            .width(52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else disabledAlpha }
            .onSizeChanged {
                trackWidthPx = it.width.toFloat()
                trackHeightPx = it.height.toFloat()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val thumbLeft = ((latestTrackWidthPx - latestThumbWidthPx) * 0.5f).coerceAtLeast(0f)
                    val thumbRight = (thumbLeft + latestThumbWidthPx).coerceAtMost(latestTrackWidthPx)
                    val thumbTop = latestThumbCenterY - (latestThumbHeightPx * 0.5f)
                    val thumbBottom = latestThumbCenterY + (latestThumbHeightPx * 0.5f)
                    val pressedThumb = down.position.x in thumbLeft..thumbRight &&
                        down.position.y in thumbTop..thumbBottom
                    if (!pressedThumb) return@awaitEachGesture

                    isThumbPressed = true
                    try {
                        var anyPressed = true
                        while (anyPressed) {
                            val event = awaitPointerEvent()
                            anyPressed = event.changes.any { it.pressed }
                        }
                    } finally {
                        isThumbPressed = false
                    }
                }
            }
            .pointerInput(valueRange, isInverted, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val thumbLeft = ((latestTrackWidthPx - latestThumbWidthPx) * 0.5f).coerceAtLeast(0f)
                    val thumbRight = (thumbLeft + latestThumbWidthPx).coerceAtMost(latestTrackWidthPx)
                    val thumbTop = latestThumbCenterY - (latestThumbHeightPx * 0.5f)
                    val thumbBottom = latestThumbCenterY + (latestThumbHeightPx * 0.5f)
                    val pressedThumb = down.position.x in thumbLeft..thumbRight &&
                        down.position.y in thumbTop..thumbBottom
                    if (!pressedThumb) return@awaitEachGesture

                    val safeUsableHeight = latestUsableHeightPx.coerceAtLeast(1f)
                    var activePointerId = down.id
                    var lastPointerY = down.position.y
                    val startValueProgress = dragValueProgress

                    isDragging = true
                    dragValueProgress = startValueProgress
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChange = event.changes.firstOrNull { it.id == activePointerId }
                            if (activeChange != null) {
                                val deltaY = activeChange.position.y - lastPointerY
                                lastPointerY = activeChange.position.y
                                val displayDelta = deltaY / safeUsableHeight
                                val currentDisplayProgress = if (isInverted) 1f - dragValueProgress else dragValueProgress
                                val updatedDisplayProgress = (currentDisplayProgress - displayDelta).coerceIn(0f, 1f)
                                val updatedValueProgress = if (isInverted) 1f - updatedDisplayProgress else updatedDisplayProgress
                                dragValueProgress = updatedValueProgress
                                onValueChange(valueRange.start + (updatedValueProgress * rangeSpan))
                                if (!activeChange.pressed) break
                                continue
                            }
                            val replacementPointer = event.changes.firstOrNull { it.pressed } ?: break
                            activePointerId = replacementPointer.id
                            lastPointerY = replacementPointer.position.y
                        }
                    } finally {
                        isDragging = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(12.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFE1E3E7))
                .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(999.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(12.dp)
                .height(with(density) { (trackHeightPx * selectedHeightFraction).toDp() })
                .offset(y = with(density) { ((0.5f - selectedCenter) * trackHeightPx).toDp() })
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF8FB1DA)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(20.dp)
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (thumbCenterY - (thumbHeightPx * 0.5f)).toDp() })
                .width(thumbWidth)
                .height(thumbHeight),
            shape = RoundedCornerShape(7.dp),
            color = Color(0xFFF4F4F4),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = thumbText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black.copy(alpha = 0.86f),
                )
            }
        }
        if (showPopoverOnLeft && isInteracting) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = (-90).dp, y = with(density) { (thumbCenterY - 23f).toDp() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.requiredWidth(124.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF9F9F9),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        text = thumbText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                    )
                }
                Canvas(modifier = Modifier.size(width = 8.dp, height = 12.dp)) {
                    drawPath(
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        },
                        color = Color(0xFFF9F9F9),
                    )
                }
            }
        }
    }
}

@Composable
private fun TechnicsButtonLabel(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isStartButton: Boolean = false,
    isPlaying: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "startGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "startGlowPulse",
    )
    val isBlinkingReadyState = isStartButton && enabled && !isPlaying
    val isSteadyPlayingState = isStartButton && enabled && isPlaying
    val glowAlpha = when {
        !enabled -> 0f
        isSteadyPlayingState -> 0.82f
        isBlinkingReadyState -> pulse
        else -> 0f
    }
    Box(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.14f
                        scaleY = 1.24f
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFE26D).copy(alpha = glowAlpha * 0.62f))
                    .blur(7.dp),
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 0.dp,
                    shape = RoundedCornerShape(2.dp),
                ),
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF3F3F3),
                contentColor = Color.Black.copy(alpha = 0.92f),
                disabledContainerColor = Color(0xFFE3E3E3),
                disabledContentColor = Color.Black.copy(alpha = 0.4f),
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.9f)),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                color = Color.Black.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun DeckSurface(
    title: String,
    trackName: String?,
    selectedTrackUri: String?,
    playbackTimeText: String,
    waveformText: String,
    waveformData: FloatArray,
    waveformProgress: Double,
    waveformZoom: Double,
    isWaveformLoading: Boolean,
    playbackStatusText: String,
    isPlaying: Boolean,
    bpmText: String,
    bpmStatusText: String?,
    targetBpm: Double,
    displayedTargetBpm: Double,
    isPressureTouchActive: Boolean,
    showExternalBpmBadge: Boolean,
    externalBpmBadgeText: String?,
    showEqualizerOverlay: Boolean,
    onImportTrack: () -> Unit,
    onImportSampleTrack: () -> Unit,
    showSampleImport: Boolean,
    onTapSeek: (Double) -> Unit,
    onSetWaveformZoom: (Double) -> Unit,
    onBeginWaveformScratch: () -> Unit,
    onWaveformScratchDelta: (Double) -> Unit,
    onEndWaveformScratch: () -> Unit,
    platterRotationDegrees: Float,
    onBeginPlatterScratch: () -> Unit,
    onPlatterScratchDelta: (Double) -> Unit,
    onEndPlatterScratch: () -> Unit,
    onPlatterPressureUpdate: (Double, Double) -> Unit,
    onPlatterPressureEnd: () -> Unit,
    onStartPause: () -> Unit,
    onStop: () -> Unit,
    isTransportReady: Boolean,
    volume: Double,
    onVolumeChange: (Double) -> Unit,
    pitchOffset: Double,
    pitchSensitivityPercent: Int,
    onPitchOffsetChange: (Double) -> Unit,
    onIncreasePitchSensitivity: () -> Unit,
    onDecreasePitchSensitivity: () -> Unit,
    eqLow: Double,
    eqMid: Double,
    eqHigh: Double,
    onEqLowChange: (Double) -> Unit,
    onEqMidChange: (Double) -> Unit,
    onEqHighChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPitchAdjusting by remember { mutableStateOf(false) }
    var platterLastAngle by remember { mutableStateOf<Double?>(null) }
    var platterTouchStartPoint by remember { mutableStateOf<Offset?>(null) }
    var isPlatterScratching by remember { mutableStateOf(false) }
    var isPlatterPressureActive by remember { mutableStateOf(false) }
    var latchedDeckBpmStatus by remember(selectedTrackUri) { mutableStateOf<String?>(null) }
    val currentDeckBpmStatus = bpmStatusText?.trim().orEmpty()
    val isMicListeningLikeDeckStatus = currentDeckBpmStatus.startsWith("Listening", ignoreCase = true)
    if (currentDeckBpmStatus.isNotBlank() && !isMicListeningLikeDeckStatus) {
        latchedDeckBpmStatus = currentDeckBpmStatus
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFD2D7DE), Color(0xFFB8BEC6)),
                        ),
                    )
                    .border(1.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = trackName ?: "No song loaded",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(playbackTimeText, fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = onImportTrack,
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0A84FF),
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .size(12.dp)
                                            .border(1.dp, Color(0xFF0A84FF), CircleShape),
                                        shape = CircleShape,
                                        color = Color.White,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                tint = Color(0xFF0A84FF),
                                                modifier = Modifier.size(8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (showSampleImport) {
                                Button(
                                    onClick = onImportSampleTrack,
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFC2C7CF),
                                        contentColor = Color(0xFF0A84FF),
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .pointerInput(waveformZoom) {
                                    detectTransformGestures { _, _, zoomChange, _ ->
                                        onSetWaveformZoom(waveformZoom * zoomChange.toDouble())
                                    }
                                }
                                .pointerInput(waveformData, waveformZoom) {
                                    detectTapGestures { offset ->
                                        val leftBoundary = 50.dp.toPx()
                                        val rightBoundary = size.width - 50.dp.toPx()
                                        if (offset.x in leftBoundary..rightBoundary) {
                                            val xOffset = offset.x - (size.width * 0.5f)
                                            onTapSeek(xOffset.toDouble())
                                        }
                                    }
                                }
                                .pointerInput(waveformData, waveformZoom) {
                                    detectDragGestures(
                                        onDragStart = { onBeginWaveformScratch() },
                                        onDragEnd = { onEndWaveformScratch() },
                                        onDragCancel = { onEndWaveformScratch() },
                                    ) { _, dragAmount ->
                                        onWaveformScratchDelta(dragAmount.x.toDouble())
                                    }
                                },
                        ) {
                            WaveformView(
                                samples = waveformData,
                                progress = waveformProgress,
                                zoom = waveformZoom,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isPitchAdjusting) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(3f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xAA616161)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = String.format(
                                            "%.1f BPM",
                                            if (displayedTargetBpm > 0.0) displayedTargetBpm
                                            else if (targetBpm > 0.0) targetBpm
                                            else 120.0,
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                    )
                                }
                            }
                            if (isWaveformLoading) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .zIndex(3f),
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.25f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (!isPitchAdjusting) {
                                Button(
                                    onClick = { onSetWaveformZoom(waveformZoom + 0.25) },
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 8.dp)
                                        .zIndex(2f)
                                        .size(width = 46.dp, height = 40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xCC191B20),
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = null, modifier = Modifier.size(23.dp))
                                }
                                Button(
                                    onClick = { onSetWaveformZoom(waveformZoom - 0.25) },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 8.dp)
                                        .zIndex(2f)
                                        .size(width = 46.dp, height = 40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xCC191B20),
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(Icons.Filled.ZoomOut, contentDescription = null, modifier = Modifier.size(23.dp))
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bpmText.ifBlank { "-- BPM" },
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val rightStatusText = latchedDeckBpmStatus.orEmpty()
                        val shouldShowRightStatus = rightStatusText.isNotBlank()
                        if (shouldShowRightStatus) {
                            Text(
                                text = rightStatusText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (
                        playbackStatusText.isNotBlank() &&
                        !playbackStatusText.equals("Stopped", ignoreCase = true) &&
                        !playbackStatusText.equals("Scratching", ignoreCase = true)
                    ) {
                        Text(
                            text = playbackStatusText,
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFD2D7DE), Color(0xFFB8BEC6), Color(0xFFA7ADB5)),
                        ),
                    )
                    .border(1.dp, Color.Black.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val platterVerticalSize = (maxHeight - 8.dp).coerceAtLeast(120.dp)
                    val platterWidthLimit = (maxWidth - 176.dp).coerceAtLeast(120.dp)
                    val clampedPlatterSize = minOf(platterVerticalSize, platterWidthLimit)

                    TurntablePlatter(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(clampedPlatterSize)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var activePointerId = down.id
                                    val touchWidth = size.width.toFloat()
                                    val touchHeight = size.height.toFloat()
                                    val touchSide = min(touchWidth, touchHeight)
                                    val visualInsetPx = TURNTABLE_VISUAL_OUTER_INSET_DP.dp.toPx()
                                    val startNanos = System.nanoTime()
                                    var lastPosition = down.position
                                    var latestRawPressure = runCatching { down.pressure }.getOrDefault(0f)
                                    var observedPressureMin = latestRawPressure.coerceIn(0f, 1f)
                                    var observedPressureMax = observedPressureMin
                                    var observedPressureSamples = 0
                                    Log.i(
                                        "MixerPressure",
                                        "gesture start pointer=${down.id.value} pos=(${down.position.x},${down.position.y}) pressure=$latestRawPressure size=(${size.width},${size.height})",
                                    )

                                    val initialNormalized = normalizedPlatterPoint(
                                        point = lastPosition,
                                        widthPx = touchWidth,
                                        heightPx = touchHeight,
                                    )
                                    platterTouchStartPoint = initialNormalized
                                    platterLastAngle = initialNormalized?.let {
                                        platterAngleForPoint(
                                            point = it,
                                            sidePx = touchSide,
                                            visualInsetPx = visualInsetPx,
                                        )
                                    }
                                    isPlatterScratching = false
                                    isPlatterPressureActive = false
                                    var hasDetectedPressureGesture = false

                                    fun processTouchPoint(currentPosition: Offset, rawPressure: Float) {
                                        if (touchSide <= 0f) return
                                        val normalized = normalizedPlatterPoint(
                                            point = currentPosition,
                                            widthPx = touchWidth,
                                            heightPx = touchHeight,
                                        )
                                        val angle = normalized?.let {
                                            platterAngleForPoint(
                                                point = it,
                                                sidePx = touchSide,
                                                visualInsetPx = visualInsetPx,
                                            )
                                        }
                                        if (normalized == null) {
                                            Log.i(
                                                "MixerPressure",
                                                "skip update: normalized point is null pos=(${currentPosition.x},${currentPosition.y})",
                                            )
                                            platterLastAngle = null
                                            if (isPlatterPressureActive) {
                                                Log.i("MixerPressure", "end pressure: pointer outside platter bounds")
                                                onPlatterPressureEnd()
                                                isPlatterPressureActive = false
                                            }
                                            return
                                        }

                                        val elapsedSeconds = ((System.nanoTime() - startNanos).coerceAtLeast(0L)) / 1_000_000_000.0
                                        val fallbackPressure = (elapsedSeconds / PRESSURE_FALLBACK_RAMP_SECONDS).toFloat().coerceIn(0f, 1f)
                                        val rawNormalized = rawPressure.coerceIn(0f, 1f)
                                        observedPressureSamples += 1
                                        observedPressureMin = min(observedPressureMin, rawNormalized)
                                        observedPressureMax = kotlin.math.max(observedPressureMax, rawNormalized)
                                        val observedPressureRange = observedPressureMax - observedPressureMin
                                        val looksLikeStuckLowPressureSensor =
                                            observedPressureSamples >= PRESSURE_STABLE_MIN_SAMPLES &&
                                                observedPressureRange <= PRESSURE_STABLE_EPSILON &&
                                                rawNormalized <= PRESSURE_STABLE_LOW_MAX
                                        val isConstantPressureSensor =
                                            isLikelyUnsupportedPressureSensorValue(rawNormalized) || looksLikeStuckLowPressureSensor
                                        val currentPressure = resolvePressureInput(
                                            rawPressure = rawPressure,
                                            fallbackPressure = fallbackPressure,
                                            forceFallback = isConstantPressureSensor,
                                        )
                                        val effectivePressureForStart = currentPressure
                                        val pressureValueForUpdate = currentPressure
                                        val movement = platterTouchStartPoint?.let { start ->
                                            hypot(
                                                (normalized.x - start.x).toDouble(),
                                                (normalized.y - start.y).toDouble(),
                                            ).toFloat()
                                        } ?: 0f
                                        val movedEnoughForScratch =
                                            movement >= (touchSide * PLATTER_SCRATCH_START_MOVEMENT_THRESHOLD_RATIO)
                                        Log.i(
                                            "MixerPressure",
                                            "update raw=$rawNormalized effective=$effectivePressureForStart fallback=$fallbackPressure pressureRange=$observedPressureRange samples=$observedPressureSamples stuckLow=$looksLikeStuckLowPressureSensor movement=$movement movedEnough=$movedEnoughForScratch angleNull=${angle == null} scratching=$isPlatterScratching pressureActive=$isPlatterPressureActive",
                                        )

                                        if (isPlatterPressureActive) {
                                            if (isAbovePressureBottomThreshold(normalized, touchSide, visualInsetPx)) {
                                                Log.i(
                                                    "MixerPressure",
                                                    "pressure update dir=${pressureDirection(normalized, touchSide)} value=$currentPressure",
                                                )
                                                onPlatterPressureUpdate(
                                                    pressureValueForUpdate,
                                                    pressureDirection(normalized, touchSide),
                                                )
                                            } else {
                                                Log.i(
                                                    "MixerPressure",
                                                    "end pressure: entered blocked bottom zone y=${normalized.y}",
                                                )
                                                onPlatterPressureEnd()
                                                isPlatterPressureActive = false
                                            }
                                            platterLastAngle = angle
                                            return
                                        }

                                        if (!isPlatterScratching &&
                                            isAbovePressureBottomThreshold(normalized, touchSide, visualInsetPx) &&
                                            !movedEnoughForScratch &&
                                            effectivePressureForStart >= PRESSURE_START_MIN_VALUE
                                        ) {
                                            Log.i(
                                                "MixerPressure",
                                                "start pressure raw=$rawNormalized effective=$effectivePressureForStart fallback=$fallbackPressure angle=${angle != null}",
                                            )
                                            onPlatterPressureUpdate(
                                                pressureValueForUpdate,
                                                pressureDirection(normalized, touchSide),
                                            )
                                            isPlatterPressureActive = true
                                            hasDetectedPressureGesture = true
                                            platterLastAngle = angle
                                            return
                                        }

                                        if (hasDetectedPressureGesture) {
                                            platterLastAngle = angle
                                            return
                                        }

                                        val previousAngle = platterLastAngle
                                        if (previousAngle != null && angle != null) {
                                            val delta = normalizedAngleDelta(previousAngle, angle)
                                            Log.i(
                                                "MixerPressure",
                                                "scratch candidate delta=$delta threshold=$PLATTER_SCRATCH_ACTIVATION_ANGLE_THRESHOLD movedEnough=$movedEnoughForScratch",
                                            )
                                            if (!isPlatterScratching &&
                                                (abs(delta) >= PLATTER_SCRATCH_ACTIVATION_ANGLE_THRESHOLD || movedEnoughForScratch)
                                            ) {
                                                if (isPlatterPressureActive) {
                                                    onPlatterPressureEnd()
                                                    isPlatterPressureActive = false
                                                }
                                                onBeginPlatterScratch()
                                                isPlatterScratching = true
                                            }
                                            if (isPlatterScratching) {
                                                onPlatterScratchDelta(delta)
                                            }
                                        }
                                        platterLastAngle = angle
                                    }

                                    while (true) {
                                        val event = withTimeoutOrNull(16L) { awaitPointerEvent() }
                                        if (event == null) {
                                            Log.i(
                                                "MixerPressure",
                                                "event timeout: replaying last point pos=(${lastPosition.x},${lastPosition.y}) pressure=$latestRawPressure",
                                            )
                                            processTouchPoint(lastPosition, latestRawPressure)
                                            continue
                                        }
                                        val anyPressed = event.changes.any { it.pressed }
                                        Log.i(
                                            "MixerPressure",
                                            "event changes=${event.changes.size} anyPressed=$anyPressed activePointer=${activePointerId.value}",
                                        )
                                        if (!anyPressed) {
                                            Log.i("MixerPressure", "loop break: no pressed pointers")
                                            break
                                        }
                                        val changeForActive = event.changes.firstOrNull { it.id == activePointerId }
                                        val replacementPressed = event.changes.firstOrNull { it.pressed }
                                        val change = when {
                                            changeForActive != null -> changeForActive
                                            replacementPressed != null -> {
                                                Log.i(
                                                    "MixerPressure",
                                                    "active pointer switched old=${activePointerId.value} new=${replacementPressed.id.value}",
                                                )
                                                activePointerId = replacementPressed.id
                                                replacementPressed
                                            }
                                            else -> event.changes.firstOrNull() ?: break
                                        }
                                        lastPosition = change.position
                                        latestRawPressure = runCatching { change.pressure }.getOrDefault(latestRawPressure)
                                        Log.i(
                                            "MixerPressure",
                                            "processing pointer=${change.id.value} pressed=${change.pressed} up=${change.changedToUpIgnoreConsumed()} pos=(${change.position.x},${change.position.y}) pressure=$latestRawPressure",
                                        )
                                        processTouchPoint(lastPosition, latestRawPressure)
                                        if (change.changedToUpIgnoreConsumed() && !anyPressed) {
                                            Log.i("MixerPressure", "end pressure: pointer up")
                                            break
                                        }
                                    }

                                    platterLastAngle = null
                                    platterTouchStartPoint = null
                                    if (isPlatterPressureActive) {
                                        Log.i("MixerPressure", "end pressure: gesture finished")
                                        onPlatterPressureEnd()
                                    }
                                    if (isPlatterScratching) {
                                        Log.i("MixerPressure", "end scratch: gesture finished")
                                        onEndPlatterScratch()
                                    }
                                    isPlatterPressureActive = false
                                    isPlatterScratching = false
                                    Log.i("MixerPressure", "gesture end")
                                }
                            },
                        platterRotationDegrees = platterRotationDegrees,
                        isPlaying = isPlaying,
                        artworkTrackUri = selectedTrackUri,
                    )

                    DecorativeTonearm(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(clampedPlatterSize),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            VerticalPitchFader(
                                value = volume.toFloat(),
                                valueRange = 0f..1f,
                                thumbText = "${(volume * 100.0).toInt()}%",
                                onValueChange = { onVolumeChange(it.toDouble()) },
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .padding(top = 4.dp, bottom = 4.dp),
                            )
                            TechnicsButtonLabel(
                                text = if (isPlaying) "PAUSE" else "START",
                                onClick = onStartPause,
                                enabled = isTransportReady,
                                isStartButton = true,
                                isPlaying = isPlaying,
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            VerticalPitchFader(
                                value = pitchOffset.toFloat(),
                                valueRange = (-(pitchSensitivityPercent / 100f))..(pitchSensitivityPercent / 100f),
                                thumbText = String.format("%+.1f%%", pitchOffset * 100.0),
                                onValueChange = { onPitchOffsetChange(it.toDouble()) },
                                isInverted = true,
                                showPopoverOnLeft = true,
                                enabled = !isPressureTouchActive,
                                disabledAlpha = 0.45f,
                                onInteractionChanged = { isPitchAdjusting = it },
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .padding(top = 4.dp, bottom = 4.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TechnicsPitchSensitivityButton(text = "+", onClick = onIncreasePitchSensitivity)
                                TechnicsPitchSensitivityButton(text = "-", onClick = onDecreasePitchSensitivity)
                            }
                            Text(
                                text = "±${pitchSensitivityPercent}%",
                                fontSize = 11.sp,
                                color = Color.Black,
                            )
                            TechnicsButtonLabel(
                                text = "STOP",
                                onClick = onStop,
                                enabled = isTransportReady,
                            )
                        }
                    }

                    if (showExternalBpmBadge && !externalBpmBadgeText.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                        ) {
                            Text(
                                text = externalBpmBadgeText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = Color.Black,
                            )
                        }
                    }
                    if (playbackStatusText.equals("Scratching", ignoreCase = true)) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                        ) {
                            Text(
                                text = "Scratching",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = Color.Black,
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showEqualizerOverlay,
                        modifier = Modifier.matchParentSize(),
                        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 220)),
                    ) {
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                EqFader(
                                    label = "LOW",
                                    value = eqLow,
                                    onValueChange = onEqLowChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                                EqFader(
                                    label = "MID",
                                    value = eqMid,
                                    onValueChange = onEqMidChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                                EqFader(
                                    label = "HIGH",
                                    value = eqHigh,
                                    onValueChange = onEqHighChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun TechnicsPitchSensitivityButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 28.dp, height = 22.dp),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF3F3F3),
            contentColor = Color.Black,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.9f)),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
    }
}

@Composable
private fun TurntablePlatter(
    platterRotationDegrees: Float,
    isPlaying: Boolean,
    artworkTrackUri: String?,
    modifier: Modifier = Modifier,
) {
    val techniksFontFamily = FontFamily(Font(R.font.microgramma_d_extended_bold))
    val artworkBitmap = rememberTrackArtworkBitmap(artworkTrackUri)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
    ) {
        val platterDp = minOf(maxWidth, maxHeight)
        val techniksFontSize = (platterDp.value * 0.11f).coerceIn(16f, 27f).sp
        val techniksLineHeight = techniksFontSize

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = platterRotationDegrees }
                .clip(CircleShape)
                .background(Color(0xFF050505))
                .border(2.dp, Color(0xFF3D3D3D), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val playingGlowAlpha = if (isPlaying) 0.16f else 0.08f

                drawCircle(color = Color(0xFF090909), radius = radius)
                drawCircle(color = Color(0xFF4E4E4E), radius = radius * 0.96f, style = Stroke(width = radius * 0.03f))
                drawCircle(color = Color(0xFF101010), radius = radius * 0.91f, style = Stroke(width = radius * 0.06f))

                val dottedStrokeOuter = Stroke(
                    width = radius * 0.040f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, radius * 0.06f), 0f),
                )
                val dottedStrokeMid = Stroke(
                    width = radius * 0.028f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, radius * 0.05f), 0f),
                )
                drawCircle(color = Color(0xFFB5B5B5), radius = radius * 0.87f, style = dottedStrokeOuter)
                drawCircle(color = Color(0xFF868686), radius = radius * 0.80f, style = dottedStrokeMid)

                for (index in 1..20) {
                    val grooveRadius = radius * (0.12f + (index / 22f) * 0.62f)
                    drawCircle(
                        color = if (index % 2 == 0) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
                        radius = grooveRadius,
                        style = Stroke(width = 1f),
                    )
                }

                drawCircle(color = Color(0xFF141C27), radius = radius * 0.27f)
                drawCircle(color = Color.White.copy(alpha = 0.14f), radius = radius * 0.21f, style = Stroke(width = 1.2f))
                drawCircle(color = Color(0xFFBFC3C8), radius = radius * 0.05f)

                drawArc(
                    color = Color.White.copy(alpha = playingGlowAlpha),
                    startAngle = -30f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(radius * 0.18f, radius * 0.18f),
                    size = Size(radius * 1.64f, radius * 1.64f),
                    style = Stroke(width = radius * 0.12f),
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Techniks",
                    fontSize = techniksFontSize,
                    lineHeight = techniksLineHeight,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = techniksFontFamily,
                    letterSpacing = (-0.7).sp,
                    color = Color(0xFF7E8FA5).copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Techniks",
                    fontSize = techniksFontSize,
                    lineHeight = techniksLineHeight,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = techniksFontFamily,
                    letterSpacing = (-0.7).sp,
                    color = Color(0xFF7E8FA5).copy(alpha = 0.60f),
                    modifier = Modifier.graphicsLayer { rotationZ = 180f },
                    textAlign = TextAlign.Center,
                )
            }

            if (artworkBitmap != null) {
                Surface(
                    modifier = Modifier
                        .size(platterDp * 0.78f)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    Image(
                        bitmap = artworkBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTrackArtworkBitmap(trackUri: String?): ImageBitmap? {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, trackUri) {
        value = null
        if (trackUri.isNullOrBlank()) return@produceState
        val parsedUri = runCatching { Uri.parse(trackUri) }.getOrNull() ?: return@produceState
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, parsedUri)
            val embeddedBytes = retriever.embeddedPicture
            if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(embeddedBytes, 0, embeddedBytes.size)
                value = bitmap?.asImageBitmap()
            }
        } catch (_: Throwable) {
            value = null
        } finally {
            runCatching { retriever.release() }
        }
    }
    return imageBitmap
}

@Composable
private fun DecorativeTonearm(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)
        val baseCenter = Offset(w * 0.80f, h * 0.16f)
        val armStart = Offset(baseCenter.x + (unit * 0.03f), baseCenter.y + (unit * 0.02f))
        val armEnd = Offset(w * 0.78f, h * 0.72f)

        drawCircle(
            color = Color(0xFFB7C0C9),
            radius = unit * 0.11f,
            center = baseCenter,
        )
        drawCircle(
            color = Color(0xFF5C646D),
            radius = unit * 0.082f,
            center = baseCenter,
            style = Stroke(width = unit * 0.018f),
        )
        drawCircle(
            color = Color(0xFF1E2328),
            radius = unit * 0.022f,
            center = baseCenter,
        )

        drawLine(
            color = Color(0xFFC7CED5),
            start = armStart,
            end = armEnd,
            strokeWidth = unit * 0.020f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.35f),
            start = Offset(armStart.x + unit * 0.004f, armStart.y + unit * 0.004f),
            end = Offset(armEnd.x + unit * 0.004f, armEnd.y + unit * 0.004f),
            strokeWidth = unit * 0.009f,
            cap = StrokeCap.Round,
        )
        drawRect(
            color = Color(0xFFE8D84B),
            topLeft = Offset(armEnd.x - (unit * 0.015f), armEnd.y - (unit * 0.015f)),
            size = Size(unit * 0.026f, unit * 0.04f),
        )
    }
}

@Composable
private fun EqFader(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            VerticalPitchFader(
                value = value.toFloat().coerceIn(0f, 1f),
                valueRange = 0f..1f,
                thumbText = "${(value.coerceIn(0.0, 1.0) * 100.0).toInt()}%",
                onValueChange = { onValueChange(it.toDouble()) },
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 4.dp, bottom = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp)
    }
}

private fun panRoutingText(pan: Double): String = when {
    pan <= -0.1 -> "L"
    pan >= 0.1 -> "R"
    else -> "C"
}

private fun cueMixCodeToValue(code: String): Float = when (code.uppercase()) {
    "C" -> -1f
    "M" -> 1f
    else -> 0f
}

private fun cueMixModeToValue(mode: dev.manelix.mixer.core.common.model.CueMixMode): Float = when (mode) {
    dev.manelix.mixer.core.common.model.CueMixMode.CUE -> -1f
    dev.manelix.mixer.core.common.model.CueMixMode.BLEND -> 0f
    dev.manelix.mixer.core.common.model.CueMixMode.MASTER -> 1f
}

private fun pitchOffset(
    targetBpm: Double,
    originalBpm: Double,
): Double {
    if (targetBpm <= 0.0) return 0.0
    val safeOriginal = if (originalBpm > 0.0) originalBpm else 120.0
    return ((targetBpm / safeOriginal) - 1.0).coerceIn(-0.16, 0.16)
}

private fun micBadgeText(rootState: DeckUiState): String? {
    if (rootState.isMicrophoneBpmDetectionActive) {
        val status = rootState.externalBpmStatusText.trim()
        if (status.isNotBlank()) return status
        val bpm = rootState.externalBpmText.trim()
        if (bpm.isNotBlank() && bpm != "-- BPM") return "Detected $bpm"
        return "Listening to MIC..."
    }
    if (rootState.isExternalBpmLoading) {
        val status = rootState.externalBpmStatusText.trim()
        if (status.isNotBlank()) return status
        return "Listening to MIC..."
    }
    return null
}

private fun normalizedPlatterPoint(point: Offset, widthPx: Float, heightPx: Float): Offset? {
    val side = min(widthPx, heightPx)
    if (side <= 0f) return null
    val xOffset = (widthPx - side) * 0.5f
    val yOffset = (heightPx - side) * 0.5f
    val normalized = Offset(point.x - xOffset, point.y - yOffset)
    return if (
        normalized.x >= 0f &&
        normalized.y >= 0f &&
        normalized.x <= side &&
        normalized.y <= side
    ) {
        normalized
    } else {
        null
    }
}

private fun platterAngleForPoint(
    point: Offset,
    sidePx: Float,
    visualInsetPx: Float,
): Double? {
    val center = Offset(sidePx * 0.5f, sidePx * 0.5f)
    val dx = point.x - center.x
    val dy = point.y - center.y
    val radius = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    val visiblePlatterDiameter = (sidePx - (visualInsetPx * 2f)).coerceAtLeast(0f)
    val minRadius = visiblePlatterDiameter * PLATTER_MIN_TOUCH_RADIUS_RATIO
    val maxRadius = visiblePlatterDiameter * PLATTER_MAX_TOUCH_RADIUS_RATIO
    if (radius < minRadius || radius > maxRadius) return null
    return atan2(dy.toDouble(), dx.toDouble())
}

private fun normalizedAngleDelta(previous: Double, current: Double): Double {
    var delta = current - previous
    if (delta > Math.PI) {
        delta -= (Math.PI * 2.0)
    } else if (delta < -Math.PI) {
        delta += (Math.PI * 2.0)
    }
    return delta
}

private fun pressureDirection(point: Offset, sidePx: Float): Double =
    if (point.x < (sidePx * 0.5f)) -1.0 else 1.0

private fun isAbovePressureBottomThreshold(point: Offset, sidePx: Float, visualInsetPx: Float): Boolean {
    val visiblePlatterBottomY = sidePx - visualInsetPx
    val threshold = visiblePlatterBottomY - (sidePx * PRESSURE_BOTTOM_BLOCKED_ZONE_RATIO)
    return point.y <= threshold
}

private fun resolvePressureInput(rawPressure: Float, fallbackPressure: Float, forceFallback: Boolean): Double {
    val normalizedRaw = rawPressure.coerceIn(0f, 1f)
    val looksLikeUnsupportedConstantPressure = forceFallback || isLikelyUnsupportedPressureSensorValue(normalizedRaw)
    val effective = if (looksLikeUnsupportedConstantPressure) fallbackPressure else normalizedRaw
    return effective.coerceIn(0f, 1f).toDouble()
}

private fun isLikelyUnsupportedPressureSensorValue(normalizedRaw: Float): Boolean {
    return normalizedRaw >= PRESSURE_UNSUPPORTED_HIGH_MIN || normalizedRaw <= PRESSURE_UNSUPPORTED_LOW_MAX
}

private const val PLATTER_SCRATCH_ACTIVATION_ANGLE_THRESHOLD = 0.002
private const val TURNTABLE_VISUAL_OUTER_INSET_DP = 10f
private const val PLATTER_SCRATCH_START_MOVEMENT_THRESHOLD_RATIO = 0.035f
private const val PLATTER_MIN_TOUCH_RADIUS_RATIO = 0.12f
private const val PLATTER_MAX_TOUCH_RADIUS_RATIO = 0.5f
private const val PRESSURE_BOTTOM_BLOCKED_ZONE_RATIO = 0.18f
private const val PRESSURE_START_MIN_VALUE = 0.10
private const val PRESSURE_FALLBACK_RAMP_SECONDS = 0.9
private const val PRESSURE_UNSUPPORTED_LOW_MAX = 0.02f
private const val PRESSURE_UNSUPPORTED_HIGH_MIN = 0.995f
private const val PRESSURE_STABLE_EPSILON = 0.008f
private const val PRESSURE_STABLE_MIN_SAMPLES = 6
private const val PRESSURE_STABLE_LOW_MAX = 0.08f
