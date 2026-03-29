package dev.manelix.mixer.feature.deck

import android.os.Build
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.manelix.mixer.core.common.model.AudioEngineMode
import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckLayout
import dev.manelix.mixer.core.ui.WaveformView
import dev.manelix.mixer.feature.deck.model.DeckUiState
import dev.manelix.mixer.feature.deck.model.isPlaybackActive

@Composable
fun DeckRoute(
    isTablet: Boolean,
    viewModel: DeckViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
    val audioMimeTypes = remember { arrayOf("audio/*") }
    LaunchedEffect(isTablet) {
        viewModel.initializeForDevice(isTablet = isTablet)
    }
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val rootState = screenState.root

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            onToggleMic = viewModel::toggleMic,
            onTogglePitchLock = viewModel::togglePitchLock,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(animationSpec = tween(durationMillis = 220)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            cueMixCode = rootState.cueMixMode.shortCode,
                            cueLevelPercent = rootState.cueLevelPercent,
                        )
                    } else {
                        StandardPanControlsCard(
                            showsRightDeck = isTablet || screenState.isRightDeckVisible,
                            leftPan = screenState.leftDeck.pan,
                            rightPan = screenState.rightDeck.pan,
                            leftRange = screenState.leftDeck.panControlRange,
                            rightRange = screenState.rightDeck.panControlRange,
                            leftRoleBadge = null,
                            rightRoleBadge = null,
                            onLeftPanChange = viewModel::setLeftDeckPan,
                            onRightPanChange = viewModel::setRightDeckPan,
                        )
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        DeckSurface(
                            title = "Deck A",
                            trackName = screenState.leftDeck.selectedTrackName,
                            playbackTimeText = screenState.leftDeck.playbackTimeText,
                            waveformText = screenState.leftDeck.waveformText,
                            waveformData = screenState.leftDeck.waveformData,
                            waveformProgress = screenState.leftDeck.playbackProgress,
                            waveformZoom = screenState.leftDeck.waveformZoom,
                            isWaveformLoading = screenState.leftDeck.isWaveformLoading,
                            playbackStatusText = screenState.leftDeck.playbackStatusText,
                            isPlaying = screenState.leftDeck.isPlaybackActive,
                            bpmText = screenState.leftDeck.bpmText,
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
                            onBeginWaveformScratch = viewModel::beginLeftDeckWaveformScratch,
                            onWaveformScratchDelta = viewModel::updateLeftDeckWaveformScratch,
                            onEndWaveformScratch = viewModel::endLeftDeckWaveformScratch,
                            platterRotationDegrees = screenState.leftDeck.platterRotationDegrees.toFloat(),
                            onBeginPlatterScratch = viewModel::beginLeftDeckPlatterScratch,
                            onPlatterScratchDelta = viewModel::updateLeftDeckPlatterScratch,
                            onEndPlatterScratch = viewModel::endLeftDeckPlatterScratch,
                            onStartPause = viewModel::togglePlayPauseLeftDeck,
                            onStop = viewModel::stopLeftDeck,
                            volume = screenState.leftDeck.volume,
                            onVolumeChange = viewModel::setLeftDeckVolume,
                            pitchOffset = pitchOffset(screenState.leftDeck.targetBpm, screenState.leftDeck.originalBpm),
                            pitchSensitivityPercent = screenState.leftDeck.pitchSensitivityPercent,
                            onPitchOffsetChange = viewModel::setLeftDeckPitchOffset,
                        )

                        if (isTablet || screenState.isRightDeckVisible) {
                            DeckSurface(
                                title = "Deck B",
                                trackName = screenState.rightDeck.selectedTrackName,
                                playbackTimeText = screenState.rightDeck.playbackTimeText,
                                waveformText = screenState.rightDeck.waveformText,
                                waveformData = screenState.rightDeck.waveformData,
                                waveformProgress = screenState.rightDeck.playbackProgress,
                                waveformZoom = screenState.rightDeck.waveformZoom,
                                isWaveformLoading = screenState.rightDeck.isWaveformLoading,
                                playbackStatusText = screenState.rightDeck.playbackStatusText,
                                isPlaying = screenState.rightDeck.isPlaybackActive,
                                bpmText = screenState.rightDeck.bpmText,
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
                                onBeginWaveformScratch = viewModel::beginRightDeckWaveformScratch,
                                onWaveformScratchDelta = viewModel::updateRightDeckWaveformScratch,
                                onEndWaveformScratch = viewModel::endRightDeckWaveformScratch,
                                platterRotationDegrees = screenState.rightDeck.platterRotationDegrees.toFloat(),
                                onBeginPlatterScratch = viewModel::beginRightDeckPlatterScratch,
                                onPlatterScratchDelta = viewModel::updateRightDeckPlatterScratch,
                                onEndPlatterScratch = viewModel::endRightDeckPlatterScratch,
                                onStartPause = viewModel::togglePlayPauseRightDeck,
                                onStop = viewModel::stopRightDeck,
                                volume = screenState.rightDeck.volume,
                                onVolumeChange = viewModel::setRightDeckVolume,
                                pitchOffset = pitchOffset(screenState.rightDeck.targetBpm, screenState.rightDeck.originalBpm),
                                pitchSensitivityPercent = screenState.rightDeck.pitchSensitivityPercent,
                                onPitchOffsetChange = viewModel::setRightDeckPitchOffset,
                            )
                        }
                    }
                }
            }
        }
    }
}

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
        modifier = Modifier.width(40.dp),
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
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
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
                modifier = Modifier.size(20.dp),
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
    showsRightDeck: Boolean,
    leftPan: Double,
    rightPan: Double,
    leftRange: PanControlRange,
    rightRange: PanControlRange,
    leftRoleBadge: String?,
    rightRoleBadge: String?,
    onLeftPanChange: (Double) -> Unit,
    onRightPanChange: (Double) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanCard(
                pan = leftPan,
                range = leftRange,
                roleBadge = leftRoleBadge,
                onPanChange = onLeftPanChange,
                modifier = Modifier.weight(1f),
            )
            if (showsRightDeck) {
                PanCard(
                    pan = rightPan,
                    range = rightRange,
                    roleBadge = rightRoleBadge,
                    onPanChange = onRightPanChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PanCard(
    pan: Double,
    range: PanControlRange,
    roleBadge: String?,
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
            ArtworkBadge(roleBadge = roleBadge)
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
    cueMixCode: String,
    cueLevelPercent: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CueBadge(deckName = "Deck A", modifier = Modifier.weight(1f))
                if (showsRightDeck) {
                    CueBadge(deckName = "Deck B", modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalFader(
                    value = cueMixCodeToValue(cueMixCode),
                    valueRange = -1f..1f,
                    thumbText = cueMixCode,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                )
                HorizontalFader(
                    value = cueLevelPercent.toFloat(),
                    valueRange = 0f..100f,
                    thumbText = "$cueLevelPercent%",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
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
    Box {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.padding(4.dp).size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun VerticalFader(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    thumbText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(thumbText, fontSize = 9.sp, maxLines = 1)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { rotationZ = -90f },
        )
    }
}

@Composable
private fun TechnicsButtonLabel(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 72.dp),
        shape = RoundedCornerShape(2.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeckSurface(
    title: String,
    trackName: String?,
    playbackTimeText: String,
    waveformText: String,
    waveformData: FloatArray,
    waveformProgress: Double,
    waveformZoom: Double,
    isWaveformLoading: Boolean,
    playbackStatusText: String,
    isPlaying: Boolean,
    bpmText: String,
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
    onPlatterScratchDelta: (Double, Double) -> Unit,
    onEndPlatterScratch: () -> Unit,
    onStartPause: () -> Unit,
    onStop: () -> Unit,
    volume: Double,
    onVolumeChange: (Double) -> Unit,
    pitchOffset: Double,
    pitchSensitivityPercent: Int,
    onPitchOffsetChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = trackName ?: "No song loaded",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(playbackTimeText, fontSize = 17.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            if (isWaveformLoading && waveformText.isNotBlank()) {
                                Text(
                                    text = waveformText,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bpmText.ifBlank { "-- BPM" },
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showExternalBpmBadge && !externalBpmBadgeText.isNullOrBlank()) {
                            Text(
                                text = externalBpmBadgeText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (playbackStatusText.isNotBlank() && !playbackStatusText.equals("Stopped", ignoreCase = true)) {
                        Text(
                            text = playbackStatusText,
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2D2D2D),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(190.dp)
                            .graphicsLayer { rotationZ = platterRotationDegrees }
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFB8B8B8), CircleShape)
                            .background(Color(0xFF161616))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { onBeginPlatterScratch() },
                                    onDragEnd = { onEndPlatterScratch() },
                                    onDragCancel = { onEndPlatterScratch() },
                                ) { _, dragAmount ->
                                    onPlatterScratchDelta(
                                        dragAmount.x.toDouble(),
                                        dragAmount.y.toDouble(),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("PLATTER", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            VerticalFader(
                                value = volume.toFloat(),
                                valueRange = 0f..1f,
                                onValueChange = { onVolumeChange(it.toDouble()) },
                                thumbText = "${(volume * 100.0).toInt()}%",
                                modifier = Modifier.height(120.dp),
                            )
                            TechnicsButtonLabel(text = if (isPlaying) "PAUSE" else "START", onClick = onStartPause)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            VerticalFader(
                                value = pitchOffset.toFloat(),
                                valueRange = (-(pitchSensitivityPercent / 100f))..(pitchSensitivityPercent / 100f),
                                onValueChange = { onPitchOffsetChange(it.toDouble()) },
                                thumbText = String.format("%+.1f%%", pitchOffset * 100.0),
                                modifier = Modifier.height(120.dp),
                            )
                            TechnicsButtonLabel(text = "STOP", onClick = onStop)
                        }
                    }

                    if (showExternalBpmBadge && !externalBpmBadgeText.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = externalBpmBadgeText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    if (showEqualizerOverlay) {
                        Surface(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                EqBand("LOW")
                                EqBand("MID")
                                EqBand("HIGH")
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun EqBand(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.height(8.dp))
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

private fun pitchOffset(
    targetBpm: Double,
    originalBpm: Double,
): Double {
    val safeOriginal = if (originalBpm > 0.0) originalBpm else 120.0
    return ((targetBpm / safeOriginal) - 1.0).coerceIn(-0.16, 0.16)
}

private fun micBadgeText(rootState: DeckUiState): String? {
    return if (rootState.isMicrophoneBpmDetectionActive || rootState.isExternalBpmLoading) {
        rootState.externalBpmStatusText.ifBlank { rootState.externalBpmText }
    } else {
        null
    }
}
