import SwiftUI
import UIComponents
import UniformTypeIdentifiers
import UIKit
import os
import QuartzCore

@MainActor
public struct TurntableDeckView: View {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "TurntableDeckView"
    )

    @StateObject private var viewModel: TurntableDeckViewModel
    @Binding private var isPitchLockedToExternalBPM: Bool
    @Binding private var areControlsVisible: Bool
    @State private var isImportingTrack = false
    @State private var pinchStartZoom: Double?
    @State private var platterLastAngle: Double?
    @State private var platterTouchStartPoint: CGPoint?
    @State private var platterTouchStartTimestamp: TimeInterval?
    @State private var waveformLastDragX: CGFloat?
    @State private var armVisible = true
    @State private var armVisibilityTask: Task<Void, Never>?

    public init(
        viewModel: TurntableDeckViewModel,
        isPitchLockedToExternalBPM: Binding<Bool>,
        areControlsVisible: Binding<Bool>
    ) {
        _viewModel = StateObject(wrappedValue: viewModel)
        _isPitchLockedToExternalBPM = isPitchLockedToExternalBPM
        _areControlsVisible = areControlsVisible
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            waveformCard

            GeometryReader { geometry in
                let pitchSize = 60.0
                let turntableSize = geometry.size.width
                let size = min(turntableSize, geometry.size.height)
                HStack(spacing: 12) {
                    ZStack {
                        turntableMetalPlateBackground

                        TurntableView(
                            isPlaying: viewModel.isPlaybackActive,
                            platterAngleDegrees: viewModel.platterRotationDegrees,
                            tonearmAngleDegrees: viewModel.tonearmRotationDegrees - TurntableDeckViewModel.tonearmStartRotationDegrees,
                            showDecorativeArm: armVisible
                        )
                        .padding(10)
                        .frame(width: size, height: size)
                        .overlay {
                            TurntableTouchSurface(
                                onTouchBegan: { location, pressure, boundsSize in
                                    handlePlatterTouchBegan(at: location, pressure: pressure, touchBoundsSize: boundsSize)
                                },
                                onTouchMoved: { location, pressure, boundsSize in
                                    handlePlatterTouchMoved(at: location, pressure: pressure, touchBoundsSize: boundsSize)
                                },
                                onTouchEnded: {
                                    handlePlatterTouchEnded()
                                }
                            )
                            .clipShape(Circle())
                        }

                        VStack {
                            HStack {
                                if isPitchLockedToExternalBPM {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Locked Pitch")
                                            .font(.caption2.weight(.semibold))
                                        Text(
                                            String(
                                                format: "%.1f BPM | %+.1f%%",
                                                viewModel.displayedTargetBPM,
                                                ((viewModel.displayedTargetBPM / max(viewModel.originalBPM, 0.001)) - 1.0) * 100.0
                                            )
                                        )
                                        .font(.caption2.monospacedDigit())
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(.ultraThinMaterial)
                                    .clipShape(Capsule())
                                }

                                Spacer()

                                if !viewModel.playbackStatusText.isEmpty {
                                    Text(viewModel.playbackStatusText)
                                        .font(.caption2.weight(.semibold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(.ultraThinMaterial)
                                        .clipShape(Capsule())
                                }
                            }
                            Spacer()
                        }
                        .zIndex(5)
                        .padding(10)
                        .allowsHitTesting(false)

                        VStack {
                            Spacer()
                            let controlsPadding = max(size * 0.035, 8)
                            HStack(alignment: .bottom) {
                                VStack(alignment: .leading, spacing: 15) {
                                    deckVolumeFader(
                                        containerWidth: turntableSize,
                                        availableHeight: max(size - (controlsPadding * 2.0), 0)
                                    )
                                    technicsStartPauseButton(containerWidth: turntableSize)
                                }
                                Spacer()
                                VStack(alignment: .trailing) {
                                    bpmPitchCard
                                        .frame(width: pitchSize)
                                        .offset(x: 3)
                                    technicsStopButton(containerWidth: turntableSize)
                                }
                            }
                            .padding(controlsPadding)
                        }
                    }
                    .frame(width: turntableSize)

                    
                }
            }
        }
        .padding(12)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .sheet(isPresented: $isImportingTrack) {
            TrackDocumentPicker(contentTypes: Self.supportedAudioTypes) { selectedURL in
                if isPitchLockedToExternalBPM {
                    isPitchLockedToExternalBPM = false
                }
                viewModel.selectTrack(url: selectedURL)
            }
        }
        .onChange(of: areControlsVisible) { _ in
            scheduleArmVisibilityAfterControlsResize()
        }
        .onDisappear {
            armVisibilityTask?.cancel()
            armVisibilityTask = nil
        }
    }

    private func scheduleArmVisibilityAfterControlsResize() {
        armVisibilityTask?.cancel()
        armVisible = false
        armVisibilityTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.controlsResizeAnimationDurationNanoseconds)
            withAnimation(.easeInOut(duration: Self.armFadeInAnimationDuration)) {
                armVisible = true
            }
        }
    }

    private var turntableMetalPlateBackground: some View {
        metalPlateBackground(cornerRadius: 12)
    }

    private func metalPlateBackground(cornerRadius: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(
                LinearGradient(
                    colors: [
                        Color(red: 0.83, green: 0.85, blue: 0.87),
                        Color(red: 0.73, green: 0.76, blue: 0.79),
                        Color(red: 0.66, green: 0.69, blue: 0.72)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.35),
                                .clear,
                                Color.black.opacity(0.08)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .blendMode(.softLight)
            }
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.65),
                                Color.black.opacity(0.25)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.0
                    )
            }
            .overlay {
                RoundedRectangle(cornerRadius: max(cornerRadius - 1, 0), style: .continuous)
                    .stroke(Color.black.opacity(0.15), lineWidth: 0.6)
                    .padding(1)
            }
            .shadow(color: Color.black.opacity(0.12), radius: 2, x: 0, y: 1)
    }

    private var waveformCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(viewModel.selectedTrackName ?? "No song loaded")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .truncationMode(.middle)
                Spacer()
                Text(viewModel.playbackTimeText)
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(.black)
            }

            HStack(spacing: 8) {
                Button {
                    isImportingTrack = true
                } label: {
                    Image(systemName: "folder.badge.plus")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel("Load track")

                #if targetEnvironment(simulator)
                Button {
                    loadSampleTrack()
                } label: {
                    Image(systemName: "music.note")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("Load sample track")
                #endif

                GeometryReader { waveformGeometry in
                        ZStack(alignment: Alignment(horizontal: .center, vertical: .center)) {
                            WaveformView(
                                samples: viewModel.waveformData,
                                progress: viewModel.playbackProgress,
                                isLoading: viewModel.isWaveformLoading,
                                zoom: viewModel.waveformZoom
                            )
                            HStack {
                                Button {
                                    Self.log.debug("zoom in button tapped")
                                    viewModel.zoomInWaveform()
                                } label: {
                                    Image(systemName: "plus.magnifyingglass")
                                        .frame(maxWidth: 10)
                                }
                                .buttonStyle(.bordered)
                                .foregroundColor(.white)
                                .disabled(!viewModel.canZoomInWaveform)
                                
                                Spacer()
                                
                                Button {
                                    Self.log.debug("zoom out button tapped")
                                    viewModel.zoomOutWaveform()
                                } label: {
                                    Image(systemName: "minus.magnifyingglass")
                                        .frame(maxWidth: 10)
                                }
                                .buttonStyle(.bordered)
                                .foregroundColor(.white)
                                .disabled(!viewModel.canZoomOutWaveform)
                            }
                            if viewModel.isWaveformLoading {
                                ProgressView()
                                    .controlSize(.small)
                                    .tint(.white)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { location in
                            let leftBoundary = Self.waveformTapSeekHorizontalMargin
                            let rightBoundary = waveformGeometry.size.width - Self.waveformTapSeekHorizontalMargin
                            Self.log.debug(
                                "waveform tap | x=\(location.x, format: .fixed(precision: 2)) width=\(waveformGeometry.size.width, format: .fixed(precision: 2)) left=\(leftBoundary, format: .fixed(precision: 2)) right=\(rightBoundary, format: .fixed(precision: 2))"
                            )
                            guard location.x >= leftBoundary, location.x <= rightBoundary else {
                                Self.log.debug("waveform tap ignored by margin")
                                return
                            }
                            let xOffset = location.x - (waveformGeometry.size.width * 0.5)
                            Self.log.debug("waveform seek triggered | xOffset=\(xOffset, format: .fixed(precision: 2))")
                            viewModel.seekFromWaveformTap(
                                xOffset: Double(xOffset),
                                baseSampleSpacing: Self.waveformBaseSampleSpacing
                            )
                        }
                        .gesture(
                            MagnificationGesture()
                                .onChanged { scale in
                                    if pinchStartZoom == nil {
                                        pinchStartZoom = viewModel.waveformZoom
                                    }
                                    let startZoom = pinchStartZoom ?? viewModel.waveformZoom
                                    viewModel.setWaveformZoom(startZoom * scale)
                                }
                                .onEnded { _ in
                                    pinchStartZoom = nil
                                }
                        )
                        .simultaneousGesture(waveformScratchGesture(width: waveformGeometry.size.width))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 35)
            }

            if viewModel.isBPMLoading {
                HStack {
                    ProgressView()
                        .controlSize(.small)
                    Spacer()
                }
            } else {
                HStack {
                    Text(viewModel.bpmText)
                        .font(.system(size: 11, weight: .regular))
                        .foregroundStyle(.black)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer()
                    Text(viewModel.bpmDetectionStatusText ?? "")
                        .font(.system(size: 11, weight: .regular))
                        .foregroundStyle(.black)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(metalPlateBackground(cornerRadius: 10))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func technicsStartPauseButton(containerWidth: CGFloat) -> some View {
        Button {
            if viewModel.isPlaybackActive {
                viewModel.pause()
            } else {
                viewModel.play()
            }
        } label: {
            TimelineView(.animation) { context in
                let isLoaded = viewModel.hasSelectedTrack
                let isPlaying = viewModel.isPlaybackActive
                let glowOpacity = !isLoaded
                    ? 0.0
                    : (isPlaying ? 0.90 : flashingGlowOpacity(at: context.date))

                technicsRectangleLabel(text: isPlaying ? "PAUSE" : "START", containerWidth: containerWidth)
                    .shadow(
                        color: Color.yellow.opacity(glowOpacity),
                        radius: max(containerWidth * 0.022, 5.0),
                        x: 0,
                        y: 0
                    )
            }
        }
        .buttonStyle(.plain)
        .disabled(!viewModel.hasSelectedTrack)
        .opacity(viewModel.hasSelectedTrack ? 1 : 0.55)
        .accessibilityLabel(viewModel.isPlaybackActive ? "Pause" : "Start")
    }

    private func deckVolumeFader(containerWidth: CGFloat, availableHeight: CGFloat) -> some View {
        let buttonBaseRatio = max(min(containerWidth / 340.0, 1.0), 0.5)
        let startButtonHeight = CGFloat(40.0) * buttonBaseRatio
        let safeAvailableHeight = availableHeight.isFinite ? max(availableHeight, 0) : 0
        let textHeight: CGFloat = 24
        let stackSpacing: CGFloat = 2
        let faderHeight = max(safeAvailableHeight - startButtonHeight - textHeight - stackSpacing, 44)

        return VStack(spacing: stackSpacing) {
            VerticalPitchFader(
                value: Binding(
                    get: { viewModel.volume },
                    set: { viewModel.setVolume($0) }
                ),
                range: 0...1
            )
            .frame(width: 40, height: faderHeight)
            .accessibilityLabel("Deck volume")

            Text(String(format: "%.0f%%", viewModel.volume * 100.0))
                .font(.system(size: 9, weight: .semibold, design: .monospaced))
                .foregroundStyle(.black)
        }
    }

    private func technicsStopButton(containerWidth: CGFloat) -> some View {
        Button {
            viewModel.stop()
        } label: {
            technicsRectangleLabel(text: "STOP", containerWidth: containerWidth)
        }
        .buttonStyle(.plain)
        .disabled(!viewModel.hasSelectedTrack)
        .opacity(viewModel.hasSelectedTrack ? 1 : 0.55)
        .accessibilityLabel("Stop")
    }

    private func technicsRectangleLabel(text: String, containerWidth: CGFloat) -> some View {
        let baseRatio = max(min(containerWidth / 340.0, 1.0), 0.5)
        let fontSize = CGFloat(8.2) * baseRatio
        let width = CGFloat(72.0) * baseRatio
        let height = CGFloat(27.0) * baseRatio
        let outerLine = max(CGFloat(1.0) * baseRatio, 0.9)
        let innerLine = max(CGFloat(0.6) * baseRatio, 0.55)
        let corner = max(CGFloat(2.0) * baseRatio, 1.4)

        return Text(text)
            .font(.system(size: fontSize, weight: .semibold, design: .default))
            .tracking(0.6)
            .foregroundStyle(.black.opacity(0.92))
            .frame(minWidth: width, minHeight: height)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(white: 0.98),
                                Color(white: 0.90)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .stroke(Color.black.opacity(0.92), lineWidth: outerLine)
            )
            .overlay(
                RoundedRectangle(cornerRadius: max(corner - (0.8 * baseRatio), 1), style: .continuous)
                    .stroke(Color.black.opacity(0.28), lineWidth: innerLine)
                    .padding(max(CGFloat(2.0) * baseRatio, 1.2))
            )
    }

    private func technicsPitchSensitivityButtonLabel(text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .foregroundStyle(.black.opacity(0.92))
            .frame(width: 25, height: 20)
            .background(
                RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(white: 0.98),
                                Color(white: 0.90)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                    .stroke(Color.black.opacity(0.92), lineWidth: 0.9)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 1, style: .continuous)
                    .stroke(Color.black.opacity(0.28), lineWidth: 0.55)
                    .padding(1.2)
            )
    }

    private func flashingGlowOpacity(at date: Date) -> Double {
        let time = date.timeIntervalSinceReferenceDate
        let normalized = (sin(time * (2.0 * .pi * 1.35)) + 1.0) * 0.5
        return 0.22 + (normalized * 0.78)
    }

    private var bpmPitchCard: some View {
        GeometryReader { _ in
            let safeOriginalBPM = max(viewModel.originalBPM, TurntableDeckViewModel.minBPM)
            let sensitivityFraction = viewModel.pitchSensitivityFraction

            VStack(alignment: .trailing, spacing: 5) {
                VStack(alignment: .center, spacing: 0) {
                    VerticalPitchFader(
                        value: Binding(
                            get: {
                                ((viewModel.displayedTargetBPM / safeOriginalBPM) - 1.0)
                            },
                            set: { newPitch in
                                viewModel.setPitchOffset(newPitch)
                            }
                        ),
                        range: -sensitivityFraction...sensitivityFraction
                    )
                    .frame(maxHeight: .infinity)
                    .disabled(
                        isPitchLockedToExternalBPM ||
                        viewModel.isTurntableScrubbing ||
                        viewModel.isPressureTouchActive
                    )
                    .opacity(
                        (isPitchLockedToExternalBPM ||
                         viewModel.isTurntableScrubbing ||
                         viewModel.isPressureTouchActive) ? 0.45 : 1
                    )
                    .accessibilityLabel("Pitch fader")

                    Text(String(format: "%+.1f%%", ((viewModel.displayedTargetBPM / max(viewModel.originalBPM, 0.001)) - 1.0) * 100.0))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.black)
                        .padding(.top, 2)
                }

                VStack(spacing: 2) {
                    HStack(spacing: 0) {
                        Button {
                            viewModel.increasePitchSensitivity()
                        } label: {
                            technicsPitchSensitivityButtonLabel(text: "+")
                        }
                        .buttonStyle(.plain)
                        .disabled(!viewModel.canIncreasePitchSensitivity)
                        .accessibilityLabel("Increase pitch sensitivity")
                        .frame(maxWidth: 40)

                        Button {
                            viewModel.decreasePitchSensitivity()
                        } label: {
                            technicsPitchSensitivityButtonLabel(text: "-")
                        }
                        .buttonStyle(.plain)
                        .disabled(!viewModel.canDecreasePitchSensitivity)
                        .accessibilityLabel("Decrease pitch sensitivity")
                        .frame(maxWidth: 40)
                    }

                    Text("±\(viewModel.pitchSensitivityPercent)%")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.black)
                }
            }
            .padding(0)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(.clear)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    private static let supportedAudioTypes: [UTType] = [
        "mp3",
        "wav",
        "aiff",
        "m4a"
    ].compactMap { UTType(filenameExtension: $0, conformingTo: .audio) }

    private func loadSampleTrack() {
        guard let sampleURL = Bundle.main.url(forResource: "Sample", withExtension: "mp3") else {
            return
        }
        if isPitchLockedToExternalBPM {
            isPitchLockedToExternalBPM = false
        }
        viewModel.selectTrack(url: sampleURL)
    }

    private func handlePlatterTouchBegan(at location: CGPoint, pressure: CGFloat, touchBoundsSize: CGSize) {
        guard let normalized = normalizedPlatterPoint(from: location, in: touchBoundsSize) else {
            Self.log.debug(
                "pressure begin ignored by touch square bounds | x=\(location.x, format: .fixed(precision: 2)) y=\(location.y, format: .fixed(precision: 2)) w=\(touchBoundsSize.width, format: .fixed(precision: 2)) h=\(touchBoundsSize.height, format: .fixed(precision: 2))"
            )
            platterLastAngle = nil
            return
        }

        let platterSize = min(touchBoundsSize.width, touchBoundsSize.height)
        guard let angle = platterAngle(for: normalized, size: platterSize) else {
            Self.log.debug(
                "pressure begin ignored by platter bounds | x=\(normalized.x, format: .fixed(precision: 2)) y=\(normalized.y, format: .fixed(precision: 2)) size=\(platterSize, format: .fixed(precision: 2))"
            )
            platterLastAngle = nil
            return
        }

        platterTouchStartPoint = normalized
        platterTouchStartTimestamp = CACurrentMediaTime()
        platterLastAngle = angle
    }

    private func handlePlatterTouchMoved(at location: CGPoint, pressure: CGFloat, touchBoundsSize: CGSize) {
        guard let normalized = normalizedPlatterPoint(from: location, in: touchBoundsSize) else {
            Self.log.debug(
                "pressure move ignored by touch square bounds | x=\(location.x, format: .fixed(precision: 2)) y=\(location.y, format: .fixed(precision: 2)) w=\(touchBoundsSize.width, format: .fixed(precision: 2)) h=\(touchBoundsSize.height, format: .fixed(precision: 2))"
            )
            platterLastAngle = nil
            return
        }

        let platterSize = min(touchBoundsSize.width, touchBoundsSize.height)
        guard let angle = platterAngle(for: normalized, size: platterSize) else {
            Self.log.debug(
                "pressure move ignored by platter bounds | x=\(normalized.x, format: .fixed(precision: 2)) y=\(normalized.y, format: .fixed(precision: 2))"
            )
            platterLastAngle = nil
            return
        }

        let currentPressure = normalizedPressure(pressure)
        let movement = if let touchStart = platterTouchStartPoint {
            hypot(normalized.x - touchStart.x, normalized.y - touchStart.y)
        } else {
            0.0
        }
        let movedEnoughForScratch = movement >= (platterSize * Self.scratchStartMovementThresholdRatio)

        if viewModel.isPressureTouchActive {
            if isAbovePressureBottomThreshold(normalized, size: platterSize) {
                viewModel.updateTurntablePressureTouch(
                    pressure: currentPressure,
                    direction: pressureDirection(for: normalized, platterSize: platterSize)
                )
            } else {
                viewModel.endTurntablePressureTouch()
            }
            platterLastAngle = angle
            return
        }

        if !viewModel.isTurntableScrubbing,
           isAbovePressureBottomThreshold(normalized, size: platterSize),
           !movedEnoughForScratch,
           let touchStartTimestamp = platterTouchStartTimestamp {
            let holdElapsed = CACurrentMediaTime() - touchStartTimestamp
            if holdElapsed >= Self.pressureStartHoldDelay,
               currentPressure >= Self.pressureStartMinPressure {
                viewModel.beginTurntablePressureTouch(
                    pressure: currentPressure,
                    direction: pressureDirection(for: normalized, platterSize: platterSize)
                )
                platterLastAngle = angle
                return
            }
        }

        if let previousAngle = platterLastAngle {
            let delta = normalizedAngleDelta(from: previousAngle, to: angle)
            if !viewModel.isTurntableScrubbing,
               (abs(delta) >= Self.platterScratchActivationAngleThreshold || movedEnoughForScratch) {
                viewModel.beginTurntableScrub()
            }
            if viewModel.isTurntableScrubbing {
                viewModel.updateTurntableScrub(angleDelta: delta)
            }
        }

        platterLastAngle = angle
    }

    private func handlePlatterTouchEnded() {
        platterLastAngle = nil
        platterTouchStartPoint = nil
        platterTouchStartTimestamp = nil
        viewModel.endTurntablePressureTouch()
        if viewModel.isTurntableScrubbing {
            viewModel.endTurntableScrub()
        }
    }

    private func normalizedPressure(_ rawPressure: CGFloat) -> Double {
        min(max(Double(rawPressure), 0), 1)
    }

    private func pressureDirection(for location: CGPoint, platterSize: CGFloat) -> Double {
        location.x < (platterSize * 0.5) ? -1 : 1
    }

    private func normalizedPlatterPoint(from point: CGPoint, in boundsSize: CGSize) -> CGPoint? {
        let side = min(boundsSize.width, boundsSize.height)
        guard side > 0 else {
            return nil
        }

        let xOffset = (boundsSize.width - side) * 0.5
        let yOffset = (boundsSize.height - side) * 0.5
        let normalized = CGPoint(x: point.x - xOffset, y: point.y - yOffset)

        guard normalized.x >= 0, normalized.y >= 0, normalized.x <= side, normalized.y <= side else {
            return nil
        }
        return normalized
    }

    private func waveformScratchGesture(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let leftBoundary = Self.waveformTapSeekHorizontalMargin
                let rightBoundary = width - Self.waveformTapSeekHorizontalMargin
                guard value.location.x >= leftBoundary, value.location.x <= rightBoundary else {
                    Self.log.debug(
                        "waveform scratch ignored by margin | x=\(value.location.x, format: .fixed(precision: 2)) left=\(leftBoundary, format: .fixed(precision: 2)) right=\(rightBoundary, format: .fixed(precision: 2))"
                    )
                    waveformLastDragX = nil
                    return
                }

                if !viewModel.isTurntableScrubbing {
                    Self.log.debug("waveform scratch begin")
                    viewModel.beginTurntableScrub()
                }

                if let previousX = waveformLastDragX {
                    let deltaX = value.location.x - previousX
                    let zoomScaledPointsPerRevolution = max(
                        Self.minWaveformPointsPerRevolution,
                        Self.waveformPointsPerRevolution * viewModel.waveformZoom
                    )
                    let angleDelta = -(Double(deltaX) / zoomScaledPointsPerRevolution) * (2.0 * .pi)
                    Self.log.debug(
                        "waveform scratch update | deltaX=\(deltaX, format: .fixed(precision: 2)) angleDelta=\(angleDelta, format: .fixed(precision: 4))"
                    )
                    viewModel.updateTurntableScrub(angleDelta: angleDelta)
                }

                waveformLastDragX = value.location.x
            }
            .onEnded { _ in
                waveformLastDragX = nil
                if viewModel.isTurntableScrubbing {
                    Self.log.debug("waveform scratch end")
                    viewModel.endTurntableScrub()
                }
            }
    }

    private func platterAngle(for point: CGPoint, size: CGFloat) -> Double? {
        let center = CGPoint(x: size * 0.5, y: size * 0.5)
        let dx = point.x - center.x
        let dy = point.y - center.y
        let radius = sqrt((dx * dx) + (dy * dy))
        // Match interactive area with visible platter circle in TurntableView, which has fixed outer padding.
        let visiblePlatterDiameter = max(size - (Self.turntableVisualOuterInset * 2.0), 0)
        let minRadius = visiblePlatterDiameter * 0.12
        let maxRadius = visiblePlatterDiameter * 0.5

        guard radius >= minRadius, radius <= maxRadius else {
            return nil
        }

        return atan2(dy, dx)
    }

    private func isAbovePressureBottomThreshold(_ point: CGPoint, size: CGFloat) -> Bool {
        let visiblePlatterBottomY = size - Self.turntableVisualOuterInset
        let threshold = visiblePlatterBottomY - (size * Self.pressureBottomBlockedZoneRatio)
        return point.y <= threshold
    }

    private func normalizedAngleDelta(from previous: Double, to current: Double) -> Double {
        var delta = current - previous
        if delta > .pi {
            delta -= (.pi * 2.0)
        } else if delta < -.pi {
            delta += (.pi * 2.0)
        }
        return delta
    }

    private static let waveformPointsPerRevolution: Double = 60.0
    private static let minWaveformPointsPerRevolution: Double = 20.0
    private static let waveformBaseSampleSpacing: Double = 2.0
    private static let waveformTapSeekHorizontalMargin: CGFloat = 40.0
    private static let platterScratchActivationAngleThreshold: Double = 0.002
    private static let turntableVisualOuterInset: CGFloat = 10.0
    private static let pressureBottomBlockedZoneRatio: CGFloat = 0.18
    private static let scratchStartMovementThresholdRatio: CGFloat = 0.035
    private static let pressureStartMinPressure: Double = 0.10
    private static let pressureStartHoldDelay: TimeInterval = 0.0
    private static let armFadeInAnimationDuration: TimeInterval = 0.18
    private static let controlsResizeAnimationDurationNanoseconds: UInt64 = 220_000_000
}

private struct TrackDocumentPicker: UIViewControllerRepresentable {
    let contentTypes: [UTType]
    let onPick: (URL) -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: contentTypes,
            asCopy: true
        )
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: (URL) -> Void

        init(onPick: @escaping (URL) -> Void) {
            self.onPick = onPick
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let first = urls.first else { return }
            onPick(first)
        }
    }
}

private struct TurntableTouchSurface: UIViewRepresentable {
    let onTouchBegan: (CGPoint, CGFloat, CGSize) -> Void
    let onTouchMoved: (CGPoint, CGFloat, CGSize) -> Void
    let onTouchEnded: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            onTouchBegan: onTouchBegan,
            onTouchMoved: onTouchMoved,
            onTouchEnded: onTouchEnded
        )
    }

    func makeUIView(context: Context) -> TouchSurfaceView {
        let view = TouchSurfaceView()
        view.backgroundColor = .clear
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ uiView: TouchSurfaceView, context: Context) {
        uiView.coordinator = context.coordinator
    }

    final class Coordinator {
        let onTouchBegan: (CGPoint, CGFloat, CGSize) -> Void
        let onTouchMoved: (CGPoint, CGFloat, CGSize) -> Void
        let onTouchEnded: () -> Void

        init(
            onTouchBegan: @escaping (CGPoint, CGFloat, CGSize) -> Void,
            onTouchMoved: @escaping (CGPoint, CGFloat, CGSize) -> Void,
            onTouchEnded: @escaping () -> Void
        ) {
            self.onTouchBegan = onTouchBegan
            self.onTouchMoved = onTouchMoved
            self.onTouchEnded = onTouchEnded
        }
    }
}

private final class TouchSurfaceView: UIView {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "TurntableTouchSurface"
    )

    weak var coordinator: TurntableTouchSurface.Coordinator?
    private var didLogNoForceSupport = false
    private var lastMoveLogTime: TimeInterval = 0
    private var lastLoggedPressure: CGFloat = -1
    private var activeTouchLocation: CGPoint?
    private var fallbackPressureStartTime: TimeInterval?
    private var fallbackPressureDisplayLink: CADisplayLink?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        activeTouchLocation = touch.location(in: self)
        let pressure = normalizedPressure(for: touch)
        let rawForce = touch.force
        let maxForce = touch.maximumPossibleForce
        Self.log.info(
            "touchesBegan | rawForce=\(rawForce, format: .fixed(precision: 3)) maxForce=\(maxForce, format: .fixed(precision: 3)) normalized=\(pressure, format: .fixed(precision: 3))"
        )
        if maxForce > 0 {
            stopFallbackPressureUpdates()
            coordinator?.onTouchBegan(activeTouchLocation ?? .zero, pressure, bounds.size)
        } else {
            fallbackPressureStartTime = CACurrentMediaTime()
            coordinator?.onTouchBegan(activeTouchLocation ?? .zero, 0, bounds.size)
            startFallbackPressureUpdates()
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        activeTouchLocation = touch.location(in: self)
        let pressure = normalizedPressure(for: touch)
        let now = CACurrentMediaTime()
        let shouldLog = abs(pressure - lastLoggedPressure) >= 0.05 || (now - lastMoveLogTime) >= 0.35
        if shouldLog {
            lastMoveLogTime = now
            lastLoggedPressure = pressure
            Self.log.info(
                "touchesMoved | rawForce=\(touch.force, format: .fixed(precision: 3)) maxForce=\(touch.maximumPossibleForce, format: .fixed(precision: 3)) normalized=\(pressure, format: .fixed(precision: 3))"
            )
        }
        if touch.maximumPossibleForce > 0 {
            stopFallbackPressureUpdates()
            coordinator?.onTouchMoved(activeTouchLocation ?? .zero, pressure, bounds.size)
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        Self.log.info("touchesEnded")
        stopFallbackPressureUpdates()
        activeTouchLocation = nil
        fallbackPressureStartTime = nil
        coordinator?.onTouchEnded()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        Self.log.info("touchesCancelled")
        stopFallbackPressureUpdates()
        activeTouchLocation = nil
        fallbackPressureStartTime = nil
        coordinator?.onTouchEnded()
    }

    private func normalizedPressure(for touch: UITouch) -> CGFloat {
        let maxForce = touch.maximumPossibleForce
        guard maxForce > 0 else {
            if !didLogNoForceSupport {
                didLogNoForceSupport = true
                Self.log.warning("Force touch unsupported on current input route (maximumPossibleForce == 0)")
            }
            return 0
        }
        return min(max(touch.force / maxForce, 0), 1)
    }

    private func startFallbackPressureUpdates() {
        guard fallbackPressureDisplayLink == nil else { return }
        let displayLink = CADisplayLink(target: self, selector: #selector(handleFallbackPressureTick))
        displayLink.add(to: .main, forMode: .common)
        fallbackPressureDisplayLink = displayLink
        Self.log.info("Fallback pressure enabled (force unsupported): press-hold will ramp pressure")
    }

    private func stopFallbackPressureUpdates() {
        fallbackPressureDisplayLink?.invalidate()
        fallbackPressureDisplayLink = nil
    }

    @objc
    private func handleFallbackPressureTick() {
        guard let startTime = fallbackPressureStartTime,
              let location = activeTouchLocation else {
            return
        }

        let elapsed = CACurrentMediaTime() - startTime
        let normalized = min(max(elapsed / Self.fallbackPressureRampDuration, 0), 1)
        coordinator?.onTouchMoved(location, normalized, bounds.size)
    }

    private static let fallbackPressureRampDuration: TimeInterval = 1.2
}

private struct VerticalPitchFader: View {
    @Binding var value: Double
    var border: Color = .black
    let range: ClosedRange<Double>

    var body: some View {
        GeometryReader { geometry in
            let height = max(geometry.size.height, 1)
            let progress = normalizedProgress(for: value)
            let thumbSize: CGFloat = 26
            let usableHeight = max(height - thumbSize, 1)
            let thumbY = (1.0 - progress) * usableHeight
            let baselineProgress = baselineProgressForRange()
            let selectedHeight = max(abs(progress - baselineProgress) * height, 2)
            let selectedMidpoint = (progress + baselineProgress) * 0.5

            ZStack(alignment: .center) {
                Capsule()
                    .fill(Color(uiColor: .systemGray5))
                    .frame(width: 10)
                    .frame(maxHeight: .infinity)
                    .overlay(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .stroke(border.opacity(0.25), lineWidth: 1)
                    )

                Capsule()
                    .fill(Color.accentColor.opacity(0.25))
                    .frame(width: 10, height: selectedHeight)
                    .offset(y: (0.5 - selectedMidpoint) * height)

                Rectangle()
                    .fill(Color.primary.opacity(0.35))
                    .frame(width: 20, height: 1)

                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(Color(uiColor: .systemBackground))
                    .overlay(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .stroke(border.opacity(0.25), lineWidth: 1)
                    )
                    .frame(width: 34, height: thumbSize)
                    .offset(y: thumbY - (usableHeight * 0.5))
                    .shadow(color: .black.opacity(0.16), radius: 2, x: 0, y: 1)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        let y = min(max(gesture.location.y, 0), height)
                        let mappedProgress = 1.0 - (y / height)
                        value = mappedValue(forNormalizedProgress: mappedProgress)
                    }
            )
            .simultaneousGesture(
                TapGesture(count: 2)
                    .onEnded {
                        value = 0
                    }
            )
            .onTapGesture { location in
                let y = min(max(location.y, 0), height)
                let mappedProgress = 1.0 - (y / height)
                value = mappedValue(forNormalizedProgress: mappedProgress)
            }
        }
    }

    private func normalizedProgress(for rawValue: Double) -> Double {
        let clamped = min(max(rawValue, range.lowerBound), range.upperBound)
        let span = range.upperBound - range.lowerBound
        guard span > 0 else { return 0.5 }
        return (clamped - range.lowerBound) / span
    }

    private func mappedValue(forNormalizedProgress progress: Double) -> Double {
        let clamped = min(max(progress, 0), 1)
        let span = range.upperBound - range.lowerBound
        return range.lowerBound + (clamped * span)
    }

    private func baselineProgressForRange() -> Double {
        if range.lowerBound <= 0, range.upperBound >= 0 {
            return normalizedProgress(for: 0)
        }
        if range.lowerBound >= 0 {
            return 0
        }
        return 1
    }
}

#Preview("Landscape View", traits: .landscapeLeft) {
    TurntableDeckView(viewModel: TurntableDeckViewModel(), isPitchLockedToExternalBPM: .constant(false), areControlsVisible: .constant(false))
}
