import SwiftUI
import UIKit

@MainActor
public struct DeckView: View {
    @StateObject private var viewModel: DeckViewModel
    @State private var areControlsVisible: Bool
    @State private var isRightDeckVisible: Bool
    private let isIPad: Bool

    public init() {
        let isIPad = UIDevice.current.userInterfaceIdiom == .pad
        self.isIPad = isIPad
        _areControlsVisible = State(initialValue: isIPad)
        _isRightDeckVisible = State(initialValue: isIPad)
        _viewModel = StateObject(wrappedValue: DeckViewModel())
    }

    public init(viewModel: DeckViewModel) {
        let isIPad = UIDevice.current.userInterfaceIdiom == .pad
        self.isIPad = isIPad
        _areControlsVisible = State(initialValue: isIPad)
        _isRightDeckVisible = State(initialValue: isIPad)
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

                    HStack(alignment: .top, spacing: 12) {
                        TurntableDeckView(
                            viewModel: viewModel.leftTurntableDeckViewModel,
                            isPitchLockedToExternalBPM: Binding(
                                get: { viewModel.isPitchLockedToExternalBPM },
                                set: { viewModel.setPitchLockEnabled($0) }
                            ),
                            areControlsVisible: $areControlsVisible
                        )

                        if isIPad || isRightDeckVisible {
                            TurntableDeckView(
                                viewModel: viewModel.rightTurntableDeckViewModel,
                                isPitchLockedToExternalBPM: .constant(false),
                                areControlsVisible: $areControlsVisible
                            )
                        }
                    }
                }
            }
            .padding(12)
            .background(.black)
            .onAppear {
                guard isIPad else {
                    return
                }
                areControlsVisible = true
                isRightDeckVisible = true
            }
        }
    }

    private var controlsVisibilityButton: some View {
        VStack {
            Button {
                withAnimation(.easeInOut(duration: 0.22)) {
                    areControlsVisible.toggle()
                }
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
                    if !isIPad {
                        Button {
                            isRightDeckVisible.toggle()
                        } label: {
                            TurntableToggleIcon(isActive: isRightDeckVisible, foregroundColor: .white)
                                .frame(width: 16, height: 16)
                        }
                        .buttonStyle(.borderedProminent)
                        .foregroundColor(.accentColor)
                        .accessibilityLabel(isRightDeckVisible ? "Hide right deck" : "Show right deck")
                    }

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

private struct TurntableToggleIcon: View {
    let isActive: Bool
    let foregroundColor: Color

    init(isActive: Bool, foregroundColor: Color = .primary) {
        self.isActive = isActive
        self.foregroundColor = foregroundColor
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                Circle()
                    .strokeBorder(foregroundColor, lineWidth: 1.4)

                Circle()
                    .strokeBorder(foregroundColor.opacity(0.65), lineWidth: 1)
                    .padding(2.2)

                Circle()
                    .fill(foregroundColor)
                    .frame(width: 2.2, height: 2.2)
            }

            Circle()
                .fill(Color(uiColor: .systemBackground))
                .overlay(
                    Image(systemName: isActive ? "minus" : "plus")
                        .font(.system(size: 5.8, weight: .bold))
                        .foregroundStyle(Color.accentColor)
                )
                .frame(width: 7.2, height: 7.2)
                .offset(x: 1.8, y: 1.8)
        }
    }
}
