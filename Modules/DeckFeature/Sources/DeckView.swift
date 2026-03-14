import SwiftUI

@MainActor
public struct DeckView: View {
    @StateObject private var viewModel: DeckViewModel
    @State private var areControlsVisible = false

    public init() {
        _viewModel = StateObject(wrappedValue: DeckViewModel())
    }

    public init(viewModel: DeckViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    public var body: some View {
        GeometryReader { _ in
            HStack(alignment: .top, spacing: 12) {
                controlsVisibilityButton

                VStack(alignment: .leading, spacing: 12) {
                    if areControlsVisible {
                        controlsColumn
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    HStack {
                        TurntableDeckView(
                            viewModel: viewModel.turntableDeckViewModel,
                            isPitchLockedToExternalBPM: Binding(
                                get: { viewModel.isPitchLockedToExternalBPM },
                                set: { viewModel.setPitchLockEnabled($0) }
                            ),
                            areControlsVisible: $areControlsVisible
                        )
                    }
                }
            }
            .padding(12)
            .animation(.easeInOut(duration: 0.22), value: areControlsVisible)
            .background(Color(uiColor: .systemBackground))
        }
    }

    private var controlsVisibilityButton: some View {
        VStack {
            Button {
                areControlsVisible.toggle()
            } label: {
                Image(systemName: areControlsVisible ? "xmark" : "line.3.horizontal")
                    .font(.caption.weight(.bold))
                    .frame(maxWidth: 14)
                    .frame(minHeight: 20)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityLabel(areControlsVisible ? "Hide controls" : "Show controls")
            
            if areControlsVisible {
                VStack {
                    Button {
                        if viewModel.isMicrophoneBPMDetectionActive {
                            viewModel.stopMicrophoneBPMDetection()
                        } else {
                            viewModel.startMicrophoneBPMDetection()
                        }
                    } label: {
                        Image(systemName: viewModel.isMicrophoneBPMDetectionActive ? "mic.slash.fill" : "mic.fill")
                            .frame(maxWidth: 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityLabel(
                        viewModel.isMicrophoneBPMDetectionActive
                        ? "Stop microphone BPM detection"
                        : "Start microphone BPM detection"
                    )
                    
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
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .frame(width: 44)
        .accessibilityLabel(areControlsVisible ? "Hide controls" : "Show controls")
        .accessibilityHint("Toggles the controls column visibility")
    }

    private var controlsColumn: some View {
        HStack(alignment: .top, spacing: 12) {
            externalBPMControls
            volumeControls
            panControls
        }
    }

    private var volumeControls: some View {
        HStack(spacing: 8) {
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
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var panControls: some View {
        HStack(spacing: 8) {
            Image(systemName: "arrow.left.and.right")
                .foregroundStyle(.secondary)
            Slider(
                value: Binding(
                    get: { viewModel.pan },
                    set: { viewModel.setPan($0) }
                ),
                in: -1...1
            )

            Text("\(String(format: "%.2f", viewModel.pan)) \(viewModel.panRoutingText)")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(minWidth: 60, alignment: .trailing)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var externalBPMControls: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text("Mic BPM ")
                    .font(.subheadline.weight(.semibold))
                if viewModel.isExternalBPMLoading {
                    ProgressView()
                        .controlSize(.small)
                }
                
                Spacer()
                
                Text(viewModel.externalBPMText)
                    .font(.footnote.monospacedDigit().weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
            }
            
            Text(viewModel.externalBPMStatusText)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

#Preview("Landscape View", traits: .landscapeLeft) {
    DeckView()
}
