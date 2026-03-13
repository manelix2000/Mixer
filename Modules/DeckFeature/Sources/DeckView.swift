import SwiftUI
import UIComponents
import UniformTypeIdentifiers

@MainActor
public struct DeckView: View {
    @StateObject private var viewModel: DeckViewModel
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
                ScrollView {
                    controlsColumn
                        .frame(width: max(240, geometry.size.width * 0.28))
                }

                deckArea
            }
            .padding(12)
            .background(Color(uiColor: .systemBackground))
        }
        .fileImporter(
            isPresented: $isImportingTrack,
            allowedContentTypes: Self.supportedAudioTypes,
            allowsMultipleSelection: false
        ) { result in
            guard case let .success(urls) = result, let selectedURL = urls.first else {
                return
            }
            viewModel.selectTrack(url: selectedURL)
        }
    }

    private var controlsColumn: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Controls")
                .font(.headline)

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
                let size = min(geometry.size.width, geometry.size.height)

                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color(uiColor: .tertiarySystemBackground))

                        TurntableView(
                            isPlaying: viewModel.isPlaybackActive,
                            platterAngleDegrees: viewModel.platterRotationDegrees
                        )
                        .frame(width: size, height: size)
                        .contentShape(Circle())
                        .gesture(platterDragGesture(platterSize: size))

                        VStack {
                            HStack {
                                if viewModel.isPitchLockedToExternalBPM {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Locked Pitch")
                                            .font(.caption2.weight(.semibold))
                                        Text(
                                            String(
                                                format: "%.1f BPM | %+.1f%%",
                                                viewModel.targetBPM,
                                                ((viewModel.targetBPM / max(viewModel.originalBPM, 0.001)) - 1.0) * 100.0
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
                    .frame(maxWidth: .infinity)
                    

                    bpmPitchCard
                        .frame(maxWidth: 70)
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
                        .frame(minWidth: 24)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel("Load track")

                Button {
                    loadSampleTrack()
                } label: {
                    Image(systemName: "music.note")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("Load sample track")

                Button {
                    if viewModel.isPlaybackActive {
                        viewModel.pause()
                    } else {
                        viewModel.play()
                    }
                } label: {
                    Image(systemName: viewModel.isPlaybackActive ? "pause.fill" : "play.fill")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.hasSelectedTrack)
                .accessibilityLabel(viewModel.isPlaybackActive ? "Pause" : "Play")

                Button {
                    viewModel.stop()
                } label: {
                    Image(systemName: "stop.fill")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.hasSelectedTrack)
                .accessibilityLabel("Stop")

                Spacer()
            }

            HStack(spacing: 8) {

                ZStack(alignment: Alignment(horizontal: .leading, vertical: .center)) {
                    WaveformView(
                        samples: viewModel.waveformData,
                        progress: viewModel.playbackProgress,
                        isLoading: viewModel.isWaveformLoading,
                        zoom: viewModel.waveformZoom
                    )
                    if viewModel.isWaveformLoading {
                        ProgressView()
                            .controlSize(.small)
                            .tint(.white)
                            .padding(.leading, 8)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 35)
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
                .simultaneousGesture(waveformScratchGesture())

                Button {
                    viewModel.zoomInWaveform()
                } label: {
                    Image(systemName: "plus.magnifyingglass")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.canZoomInWaveform)

                Button {
                    viewModel.zoomOutWaveform()
                } label: {
                    Image(systemName: "minus.magnifyingglass")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.canZoomOutWaveform)
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
                        .fixedSize(horizontal: true, vertical: false)
                    Spacer()
                    Text(viewModel.bpmDetectionStatusText ?? "")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: true, vertical: false)
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
            Text("Volume")
                .font(.subheadline.weight(.semibold))
            HStack(spacing: 10) {
                Image(systemName: "speaker.fill")
                    .foregroundStyle(.secondary)

                Slider(
                    value: Binding(
                        get: { viewModel.volume },
                        set: { viewModel.setVolume($0) }
                    ),
                    in: 0...1
                )
                .accessibilityLabel("Volume")

                Text(String(format: "%.2f", viewModel.volume))
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(minWidth: 42, alignment: .trailing)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var bpmPitchCard: some View {
        GeometryReader { geometry in
            VStack {
                HStack(alignment: .center, spacing: 8) {
                    VStack {
                        Button {
                            viewModel.incrementBPM()
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .frame(minWidth: 24)
                        }
                        .buttonStyle(.bordered)
                        .disabled(!viewModel.canIncrementBPM)
                        .accessibilityLabel("Increase BPM")
                        
                        Button {
                            viewModel.decrementBPM()
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .frame(minWidth: 24)
                        }
                        .buttonStyle(.bordered)
                        .disabled(!viewModel.canDecrementBPM)
                        .accessibilityLabel("Decrease BPM")
                    }
                }
                
                Text(String(format: "%+.1f%%", ((viewModel.targetBPM / max(viewModel.originalBPM, 0.001)) - 1.0) * 100.0))
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .padding(.top, 10)
            }
            .padding(10)
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
                Text("External BPM (Mic)")
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
                        .frame(minWidth: 24)
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isMicrophoneBPMDetectionActive)
                .accessibilityLabel("Start microphone BPM detection")

                Button {
                    viewModel.stopMicrophoneBPMDetection()
                } label: {
                    Image(systemName: "stop.fill")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.isMicrophoneBPMDetectionActive)
                .accessibilityLabel("Stop microphone BPM detection")

                Button {
                    viewModel.togglePitchLockToExternalBPM()
                } label: {
                    Image(systemName: viewModel.isPitchLockedToExternalBPM ? "lock.fill" : "lock.open.fill")
                        .frame(minWidth: 24)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.isPitchLockedToExternalBPM && !viewModel.canLockPitchToExternalBPM)
                .accessibilityLabel(viewModel.isPitchLockedToExternalBPM ? "Unlock pitch from external BPM" : "Lock pitch to external BPM")
            }

            Text(viewModel.externalBPMText)
                .font(.footnote.monospacedDigit().weight(.semibold))

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

    private func platterDragGesture(platterSize: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                // Enter scratch mode as soon as finger touches the platter.
                if !viewModel.isTurntableScrubbing {
                    viewModel.beginTurntableScrub()
                }

                guard let angle = platterAngle(for: value.location, size: platterSize) else {
                    platterLastAngle = nil
                    return
                }

                if let previousAngle = platterLastAngle {
                    let delta = normalizedAngleDelta(from: previousAngle, to: angle)
                    viewModel.updateTurntableScrub(angleDelta: delta)
                }

                platterLastAngle = angle
            }
            .onEnded { _ in
                platterLastAngle = nil
                if viewModel.isTurntableScrubbing {
                    viewModel.endTurntableScrub()
                }
            }
    }

    private func waveformScratchGesture() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !viewModel.isTurntableScrubbing {
                    viewModel.beginTurntableScrub()
                }

                if let previousX = waveformLastDragX {
                    let deltaX = value.location.x - previousX
                    let zoomScaledPointsPerRevolution = max(
                        Self.minWaveformPointsPerRevolution,
                        Self.waveformPointsPerRevolution * viewModel.waveformZoom
                    )
                    let angleDelta = -(Double(deltaX) / zoomScaledPointsPerRevolution) * (2.0 * .pi)
                    viewModel.updateTurntableScrub(angleDelta: angleDelta)
                }

                waveformLastDragX = value.location.x
            }
            .onEnded { _ in
                waveformLastDragX = nil
                if viewModel.isTurntableScrubbing {
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

    private static let waveformPointsPerRevolution: Double = 180.0
    private static let minWaveformPointsPerRevolution: Double = 60.0
}

#Preview {
    DeckView()
}
