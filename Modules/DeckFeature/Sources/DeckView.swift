import SwiftUI
import UIComponents
import UniformTypeIdentifiers
import UIKit
import os
import QuartzCore

@MainActor
public struct DeckView: View {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "DeckView"
    )

    @StateObject private var viewModel: DeckViewModel
    @State private var areControlsVisible = true
    @State private var isImportingTrack = false
    @State private var pinchStartZoom: Double?
    @State private var platterLastAngle: Double?
    @State private var waveformLastDragX: CGFloat?

    public init() {
        _viewModel = StateObject(wrappedValue: DeckViewModel())
    }

    public init(viewModel: DeckViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    public var body: some View {
        GeometryReader { geometry in
            HStack(alignment: .top, spacing: 12) {
                controlsVisibilityButton

                if areControlsVisible {
                        controlsColumn
                            .frame(maxWidth: 180)
                            .transition(.move(edge: .leading).combined(with: .opacity))
                }

                
                deckArea
            }
            .padding(12)
            .animation(.easeInOut(duration: 0.22), value: areControlsVisible)
            .background(Color(uiColor: .systemBackground))
        }
        .sheet(isPresented: $isImportingTrack) {
            TrackDocumentPicker(contentTypes: Self.supportedAudioTypes) { selectedURL in
                viewModel.selectTrack(url: selectedURL)
            }
        }
    }

    private var controlsVisibilityButton: some View {
        Button {
            areControlsVisible.toggle()
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "line.3.horizontal")
                    .font(.subheadline.weight(.semibold))
                Image(systemName: areControlsVisible ? "chevron.left" : "chevron.right")
                    .font(.caption.weight(.bold))
            }
            .foregroundStyle(Color.primary)
            .padding(.horizontal, 10)
            .frame(height: 32)
            .background(.ultraThinMaterial)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .shadow(color: .black.opacity(0.12), radius: 4, x: 0, y: 2)
        .frame(width: 44)
        .accessibilityLabel(areControlsVisible ? "Hide controls" : "Show controls")
        .accessibilityHint("Toggles the controls column visibility")
    }

    private var controlsColumn: some View {
        VStack(alignment: .leading, spacing: 12) {
            externalBPMControls
            volumeControls
            panControls

            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var deckArea: some View {
        VStack(alignment: .leading, spacing: 12) {
            waveformCard

            GeometryReader { geometry in
                let pitchSize = 70.0
                let turntableSize = geometry.size.width - pitchSize - 12
                let size = min(turntableSize, geometry.size.height)
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color(uiColor: .tertiarySystemBackground))

                        TurntableView(
                            isPlaying: viewModel.isPlaybackActive,
                            platterAngleDegrees: viewModel.platterRotationDegrees
                        )
                        .frame(width: size, height: size)
                        .overlay {
                            TurntableTouchSurface(
                                onTouchBegan: { location, pressure in
                                    handlePlatterTouchBegan(at: location, pressure: pressure, platterSize: size)
                                },
                                onTouchMoved: { location, pressure in
                                    handlePlatterTouchMoved(at: location, pressure: pressure, platterSize: size)
                                },
                                onTouchEnded: {
                                    handlePlatterTouchEnded()
                                }
                            )
                            .clipShape(Circle())
                        }

                        VStack {
                            HStack {
                                if viewModel.isPitchLockedToExternalBPM {
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
                        .padding(10)
                    }
                    .frame(width: turntableSize)
                    
                    bpmPitchCard
                        .frame(width: pitchSize)
                }
                
            }
        }
        .padding(12)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
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
                    .foregroundStyle(.secondary)
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

                Button {
                    if viewModel.isPlaybackActive {
                        viewModel.pause()
                    } else {
                        viewModel.play()
                    }
                } label: {
                    Image(systemName: viewModel.isPlaybackActive ? "pause.fill" : "play.fill")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.hasSelectedTrack)
                .accessibilityLabel(viewModel.isPlaybackActive ? "Pause" : "Play")

                Button {
                    viewModel.stop()
                } label: {
                    Image(systemName: "stop.fill")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.hasSelectedTrack)
                .accessibilityLabel("Stop")

                Spacer()
            }

            HStack(spacing: 8) {

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
                HStack{
                    Text(viewModel.bpmText)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer()
                    Text(viewModel.bpmDetectionStatusText ?? "")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var volumeControls: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "speaker.fill")
                    .foregroundStyle(.secondary)
                Spacer()
                Text(String(format: "%.2f", viewModel.volume))
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(minWidth: 42, alignment: .trailing)
            }
            Slider(
                value: Binding(
                    get: { viewModel.volume },
                    set: { viewModel.setVolume($0) }
                ),
                in: 0...1
            )
            .accessibilityLabel("Volume")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var bpmPitchCard: some View {
        GeometryReader { geometry in
            let safeOriginalBPM = max(viewModel.originalBPM, DeckViewModel.minBPM)
            let sensitivityFraction = viewModel.pitchSensitivityFraction

            VStack(spacing: 5) {
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
                        viewModel.isPitchLockedToExternalBPM ||
                        viewModel.isTurntableScrubbing ||
                        viewModel.isPressureTouchActive
                    )
                    .opacity(
                        (viewModel.isPitchLockedToExternalBPM ||
                         viewModel.isTurntableScrubbing ||
                         viewModel.isPressureTouchActive) ? 0.45 : 1
                    )
                    .accessibilityLabel("Pitch fader")
                    
                    Text(String(format: "%+.1f%%", ((viewModel.displayedTargetBPM / max(viewModel.originalBPM, 0.001)) - 1.0) * 100.0))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .padding(.top, 2)
                }
                
                
                VStack(spacing: 0) {
                    HStack(spacing: 5) {
                        Button {
                            viewModel.increasePitchSensitivity()
                        } label: {
                            Image(systemName: "plus.circle.fill")
                        }
                        .buttonStyle(.plain)
                        .disabled(!viewModel.canIncreasePitchSensitivity)
                        .accessibilityLabel("Increase pitch sensitivity")
                        .frame(maxWidth: 40)
                        
                        Button {
                            viewModel.decreasePitchSensitivity()
                        } label: {
                            Image(systemName: "minus.circle.fill")
                        }
                        .buttonStyle(.plain)
                        .disabled(!viewModel.canDecreasePitchSensitivity)
                        .accessibilityLabel("Decrease pitch sensitivity")
                        .frame(maxWidth: 40)
                    }
                    
                    Text("±\(viewModel.pitchSensitivityPercent)%")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
            .padding(5)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(uiColor: .tertiarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    private var panControls: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("Pan")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text("\(viewModel.panRoutingText) (\(String(format: "%.2f", viewModel.pan)))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Slider(
                value: Binding(
                    get: { viewModel.pan },
                    set: { viewModel.setPan($0) }
                ),
                in: -1...1
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var externalBPMControls: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("Mic BPM")
                    .font(.subheadline.weight(.semibold))
                if viewModel.isExternalBPMLoading {
                    ProgressView()
                        .controlSize(.small)
                }
            }

            HStack(spacing: 8) {
                Button {
                    viewModel.startMicrophoneBPMDetection()
                } label: {
                    Image(systemName: "play.fill")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isMicrophoneBPMDetectionActive)
                .accessibilityLabel("Start microphone BPM detection")

                Button {
                    viewModel.stopMicrophoneBPMDetection()
                } label: {
                    Image(systemName: "stop.fill")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.isMicrophoneBPMDetectionActive)
                .accessibilityLabel("Stop microphone BPM detection")

                Button {
                    viewModel.togglePitchLockToExternalBPM()
                } label: {
                    Image(systemName: viewModel.isPitchLockedToExternalBPM ? "lock.fill" : "lock.open.fill")
                        .frame(maxWidth: 14)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.isPitchLockedToExternalBPM && !viewModel.canLockPitchToExternalBPM)
                .accessibilityLabel(viewModel.isPitchLockedToExternalBPM ? "Unlock pitch from external BPM" : "Lock pitch to external BPM")
            }

            Text(viewModel.externalBPMText)
                .font(.footnote.monospacedDigit().weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)

            Text(viewModel.externalBPMStatusText)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
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
        viewModel.selectTrack(url: sampleURL)
    }

    private func handlePlatterTouchBegan(at location: CGPoint, pressure: CGFloat, platterSize: CGFloat) {
        if !viewModel.isTurntableScrubbing {
            viewModel.beginTurntablePressureTouch(
                pressure: normalizedPressure(pressure),
                direction: pressureDirection(for: location, platterSize: platterSize)
            )
        }

        guard let angle = platterAngle(for: location, size: platterSize) else {
            platterLastAngle = nil
            return
        }

        platterLastAngle = angle
    }

    private func handlePlatterTouchMoved(at location: CGPoint, pressure: CGFloat, platterSize: CGFloat) {
        if !viewModel.isTurntableScrubbing {
            viewModel.updateTurntablePressureTouch(
                pressure: normalizedPressure(pressure),
                direction: pressureDirection(for: location, platterSize: platterSize)
            )
        }

        guard let angle = platterAngle(for: location, size: platterSize) else {
            platterLastAngle = nil
            return
        }

        if let previousAngle = platterLastAngle {
            let delta = normalizedAngleDelta(from: previousAngle, to: angle)
            if !viewModel.isTurntableScrubbing, abs(delta) >= Self.platterScratchActivationAngleThreshold {
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
        let minRadius = size * 0.12
        let maxRadius = size * 0.5

        guard radius >= minRadius, radius <= maxRadius else {
            return nil
        }

        return atan2(dy, dx)
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
    let onTouchBegan: (CGPoint, CGFloat) -> Void
    let onTouchMoved: (CGPoint, CGFloat) -> Void
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
        let onTouchBegan: (CGPoint, CGFloat) -> Void
        let onTouchMoved: (CGPoint, CGFloat) -> Void
        let onTouchEnded: () -> Void

        init(
            onTouchBegan: @escaping (CGPoint, CGFloat) -> Void,
            onTouchMoved: @escaping (CGPoint, CGFloat) -> Void,
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
            coordinator?.onTouchBegan(activeTouchLocation ?? .zero, pressure)
        } else {
            fallbackPressureStartTime = CACurrentMediaTime()
            coordinator?.onTouchBegan(activeTouchLocation ?? .zero, 0)
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
            coordinator?.onTouchMoved(activeTouchLocation ?? .zero, pressure)
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
        coordinator?.onTouchMoved(location, normalized)
    }

    private static let fallbackPressureRampDuration: TimeInterval = 1.2
}

private struct VerticalPitchFader: View {
    @Binding var value: Double
    let range: ClosedRange<Double>

    var body: some View {
        GeometryReader { geometry in
            let height = max(geometry.size.height, 1)
            let progress = normalizedProgress(for: value)
            let thumbSize: CGFloat = 26
            let usableHeight = max(height - thumbSize, 1)
            let thumbY = (1.0 - progress) * usableHeight

            ZStack(alignment: .center) {
                Capsule()
                    .fill(Color(uiColor: .systemGray5))
                    .frame(width: 10)
                    .frame(maxHeight: .infinity)

                Capsule()
                    .fill(Color.accentColor.opacity(0.25))
                    .frame(width: 10, height: max(abs(progress - 0.5) * usableHeight, 2))
                    .offset(y: (0.5 - progress) * usableHeight * 0.5)

                Rectangle()
                    .fill(Color.primary.opacity(0.35))
                    .frame(width: 20, height: 1)

                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(Color(uiColor: .systemBackground))
                    .overlay(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .stroke(Color.primary.opacity(0.15), lineWidth: 1)
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
                        // Quick reset to center (0% pitch).
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
}

#Preview("Landscape View", traits: .landscapeLeft) {
    DeckView()
}
