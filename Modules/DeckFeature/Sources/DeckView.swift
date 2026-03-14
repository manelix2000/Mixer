import SwiftUI

@MainActor
public struct DeckView: View {
    @StateObject private var viewModel: DeckViewModel
    @State private var areControlsVisible = true

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

                if areControlsVisible {
                    controlsColumn
                        .frame(maxWidth: 180)
                        .transition(.move(edge: .leading).combined(with: .opacity))
                }

                TurntableDeckView(
                    viewModel: viewModel.turntableDeckViewModel,
                    isPitchLockedToExternalBPM: Binding(
                        get: { viewModel.isPitchLockedToExternalBPM },
                        set: { viewModel.setPitchLockEnabled($0) }
                    )
                )
            }
            .padding(12)
            .animation(.easeInOut(duration: 0.22), value: areControlsVisible)
            .background(Color(uiColor: .systemBackground))
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
}

#Preview("Landscape View", traits: .landscapeLeft) {
    DeckView()
}
