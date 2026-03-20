import AudioEngine
import SwiftUI
import UIKit

@MainActor
public struct DeckView: View {
    @StateObject private var viewModel: DeckViewModel
    @State private var areControlsVisible: Bool
    @State private var isRightDeckVisible: Bool
    @State private var isSettingsVisible: Bool
    @State private var isEqualizerVisible: Bool
    @State private var selectedAudioEngineMode: AudioEngineMode
    @State private var selectedSplitDeckLayout: SplitDeckLayout
    private let isIPad: Bool
    private let audioEngineModeStore: UserDefaultsAudioEngineModeStore
    private let splitDeckLayoutStore: UserDefaultsSplitDeckLayoutStore

    public init() {
        let isIPad = UIDevice.current.userInterfaceIdiom == .pad
        let modeStore = UserDefaultsAudioEngineModeStore()
        let layoutStore = UserDefaultsSplitDeckLayoutStore()
        self.isIPad = isIPad
        self.audioEngineModeStore = modeStore
        self.splitDeckLayoutStore = layoutStore
        _areControlsVisible = State(initialValue: isIPad)
        _isRightDeckVisible = State(initialValue: isIPad)
        _isSettingsVisible = State(initialValue: false)
        _isEqualizerVisible = State(initialValue: false)
        _selectedAudioEngineMode = State(initialValue: modeStore.selectedMode)
        _selectedSplitDeckLayout = State(initialValue: layoutStore.selectedLayout)
        _viewModel = StateObject(wrappedValue: DeckViewModel())
    }

    public init(viewModel: DeckViewModel) {
        let isIPad = UIDevice.current.userInterfaceIdiom == .pad
        let modeStore = UserDefaultsAudioEngineModeStore()
        let layoutStore = UserDefaultsSplitDeckLayoutStore()
        self.isIPad = isIPad
        self.audioEngineModeStore = modeStore
        self.splitDeckLayoutStore = layoutStore
        _areControlsVisible = State(initialValue: isIPad)
        _isRightDeckVisible = State(initialValue: isIPad)
        _isSettingsVisible = State(initialValue: false)
        _isEqualizerVisible = State(initialValue: false)
        _selectedAudioEngineMode = State(initialValue: modeStore.selectedMode)
        _selectedSplitDeckLayout = State(initialValue: layoutStore.selectedLayout)
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    public var body: some View {
        GeometryReader { _ in
            HStack(alignment: .top, spacing: 12) {
                controlsVisibilityButton

                VStack(alignment: .leading, spacing: 12) {
                    if isIPad || areControlsVisible {
                        if !isSettingsVisible {
                            controlsColumn
                                .transition(.move(edge: .top).combined(with: .opacity))
                        }
                    }

                    ZStack(alignment: .topLeading) {
                        decksRow
                            .offset(y: isSettingsVisible ? 36 : 0)
                            .opacity(isSettingsVisible ? 0 : 1)
                            .allowsHitTesting(!isSettingsVisible)

                        if isSettingsVisible {
                            settingsCard
                                .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                    .animation(.easeInOut(duration: 0.22), value: isSettingsVisible)
                }
                .animation(.easeInOut(duration: 0.22), value: isSettingsVisible)
            }
            .padding(12)
            .background(.black)
            .onAppear {
                if isIPad {
                    areControlsVisible = true
                    isRightDeckVisible = true
                }
                selectedAudioEngineMode = audioEngineModeStore.selectedMode
                selectedSplitDeckLayout = splitDeckLayoutStore.selectedLayout
            }
        }
    }

    private var controlsVisibilityButton: some View {
        VStack {
            if !isIPad {
                Button {
                    withAnimation(.easeInOut(duration: 0.22)) {
                        if areControlsVisible {
                            isSettingsVisible = false
                            areControlsVisible = false
                        } else {
                            areControlsVisible = true
                        }
                    }
                } label: {
                    Image(systemName: areControlsVisible ? "xmark" : "line.3.horizontal")
                        .font(.caption.weight(.bold))
                        .frame(maxWidth: 14)
                        .frame(minHeight: 20)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel(areControlsVisible ? "Hide controls" : "Show controls")
            }

            if isIPad || areControlsVisible {
                VStack {
                    Button {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            isSettingsVisible.toggle()
                        }
                    } label: {
                        Image(systemName: isSettingsVisible ? "gearshape.fill" : "gearshape")
                            .frame(maxWidth: 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityLabel(isSettingsVisible ? "Hide settings" : "Show settings")

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

            Spacer(minLength: 8)

            Button {
                withAnimation(.easeInOut(duration: 0.22)) {
                    isEqualizerVisible.toggle()
                }
            } label: {
                EqualizerControlIcon(isDisabledState: isEqualizerVisible)
                    .frame(width: 16, height: 16)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityLabel(isEqualizerVisible ? "Hide equalizer" : "Show equalizer")
        }
        .frame(width: 44)
        .frame(maxHeight: .infinity, alignment: .top)
        .accessibilityLabel((isIPad || areControlsVisible) ? "Hide controls" : "Show controls")
        .accessibilityHint("Toggles the controls column visibility")
    }

    private var controlsColumn: some View {
        Group {
            if selectedAudioEngineMode == .split {
                SplitCueControls(
                    viewModel: viewModel,
                    leftDeckViewModel: viewModel.leftTurntableDeckViewModel,
                    rightDeckViewModel: viewModel.rightTurntableDeckViewModel,
                    showsRightDeck: isIPad || isRightDeckVisible
                )
            } else {
                HStack(alignment: .top, spacing: 12) {
                    DeckPanControls(
                        deckViewModel: viewModel.leftTurntableDeckViewModel
                    )
                    if isIPad || isRightDeckVisible {
                        DeckPanControls(
                            deckViewModel: viewModel.rightTurntableDeckViewModel
                        )
                    }
                }
            }
        }
    }

    private var leftDeckMicBPMBadgeText: String? {
        guard viewModel.isMicrophoneBPMDetectionActive || viewModel.isExternalBPMLoading else {
            return nil
        }

        if viewModel.externalBPMText == "-- BPM" {
            return "\(viewModel.externalBPMStatusText)"
        }
        return "\(viewModel.externalBPMStatusText) \(viewModel.externalBPMText)"
    }

    private var decksRow: some View {
        HStack(alignment: .top, spacing: 12) {
            TurntableDeckView(
                viewModel: viewModel.leftTurntableDeckViewModel,
                isPitchLockedToExternalBPM: Binding(
                    get: { viewModel.isPitchLockedToExternalBPM },
                    set: { viewModel.setPitchLockEnabled($0) }
                ),
                areControlsVisible: $areControlsVisible,
                isEqualizerOverlayVisible: isEqualizerVisible,
                externalBPMBadgeText: leftDeckMicBPMBadgeText,
                isExternalBPMListening: viewModel.isMicrophoneBPMDetectionActive || viewModel.isExternalBPMLoading
            )

            if isIPad || isRightDeckVisible {
                TurntableDeckView(
                    viewModel: viewModel.rightTurntableDeckViewModel,
                    isPitchLockedToExternalBPM: .constant(false),
                    areControlsVisible: $areControlsVisible,
                    isEqualizerOverlayVisible: isEqualizerVisible
                )
            }
        }
        .animation(.easeInOut(duration: 0.22), value: isEqualizerVisible)
    }

    private var settingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Settings")
                .font(.subheadline.weight(.semibold))

            Toggle("Split Audio Engine Mode", isOn: isSplitEngineEnabledBinding)
                .toggleStyle(.switch)

            Picker("Split Deck Layout", selection: splitDeckLayoutBinding) {
                Text("L:Master / R:Cue")
                    .tag(SplitDeckLayout.leftMasterRightCue)
                Text("L:Cue / R:Master")
                    .tag(SplitDeckLayout.leftCueRightMaster)
            }
            .pickerStyle(.segmented)

            Text("Current mode: \(selectedAudioEngineMode.rawValue)")
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)

            Text("Deck routing: \(leftDeckRoleText) | \(rightDeckRoleText)")
                .font(.caption2.monospaced())
                .foregroundStyle(.secondary)

            Text("Mode applies immediately to current deck routing.")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var isSplitEngineEnabledBinding: Binding<Bool> {
        Binding(
            get: { selectedAudioEngineMode == .split },
            set: { isEnabled in
                selectedAudioEngineMode = isEnabled ? .split : .standard
                audioEngineModeStore.selectedMode = selectedAudioEngineMode
                viewModel.handleAudioEngineModeChanged(selectedAudioEngineMode)
            }
        )
    }

    private var splitDeckLayoutBinding: Binding<SplitDeckLayout> {
        Binding(
            get: { selectedSplitDeckLayout },
            set: { layout in
                selectedSplitDeckLayout = layout
                splitDeckLayoutStore.selectedLayout = layout
                viewModel.handleSplitDeckLayoutChanged(isSplitEnabled: selectedAudioEngineMode == .split)
            }
        )
    }

    private var leftDeckRoleText: String {
        "Left=\(splitRoleName(viewModel.leftTurntableDeckViewModel.splitDeckRole))"
    }

    private var rightDeckRoleText: String {
        "Right=\(splitRoleName(viewModel.rightTurntableDeckViewModel.splitDeckRole))"
    }

    private func splitRoleName(_ role: SplitDeckRole?) -> String {
        guard let role else {
            return "Normal"
        }
        switch role {
        case .master:
            return "Master"
        case .cue:
            return "Cue"
        }
    }

}

private struct EqualizerControlIcon: View {
    let isDisabledState: Bool

    var body: some View {
        ZStack {
            HStack(alignment: .bottom, spacing: 2) {
                RoundedRectangle(cornerRadius: 1.1, style: .continuous)
                    .fill(Color.white)
                    .frame(width: 2.8, height: 6)
                RoundedRectangle(cornerRadius: 1.1, style: .continuous)
                    .fill(Color.white)
                    .frame(width: 2.8, height: 10)
                RoundedRectangle(cornerRadius: 1.1, style: .continuous)
                    .fill(Color.white)
                    .frame(width: 2.8, height: 7.5)
                RoundedRectangle(cornerRadius: 1.1, style: .continuous)
                    .fill(Color.white)
                    .frame(width: 2.8, height: 12)
            }

            if isDisabledState {
                Rectangle()
                    .fill(Color.red.opacity(0.9))
                    .frame(width: 16, height: 2)
                    .rotationEffect(.degrees(-35))
            }
        }
    }
}

private struct HorizontalFader: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let thumbText: String
    @State private var isDraggingFromThumb = false

    var body: some View {
        GeometryReader { geometry in
            let width = max(geometry.size.width, 1)
            let height = max(geometry.size.height, 1)
            let thumbWidth: CGFloat = 34
            let thumbHeight: CGFloat = max(height - 2, 22)
            let usableWidth = max(width - thumbWidth, 1)
            let progress = normalizedProgress(for: value)
            let thumbX = progress * usableWidth
            let baselineProgress = baselineProgressForRange()
            let selectedWidth = max(abs(progress - baselineProgress) * width, 2)
            let selectedMidpoint = (progress + baselineProgress) * 0.5

            ZStack {
                ZStack {
                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(white: 0.89),
                                    Color(white: 0.81)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .overlay(
                            Capsule(style: .continuous)
                                .stroke(Color.black.opacity(0.18), lineWidth: 0.8)
                        )
                        .frame(height: max(height * 0.38, 8))

                    Capsule(style: .continuous)
                        .fill(Color.accentColor.opacity(0.25))
                        .frame(width: selectedWidth, height: max(height * 0.38, 8))
                        .offset(x: (selectedMidpoint - 0.5) * width)

                    Rectangle()
                        .fill(Color.primary.opacity(0.35))
                        .frame(width: 1, height: 20)

                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(white: 0.98),
                                    Color(white: 0.91)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .stroke(Color.black.opacity(0.3), lineWidth: 0.9)
                        )
                        .frame(width: thumbWidth, height: thumbHeight)
                        .overlay(
                            Text(thumbText)
                                .font(.caption2.monospacedDigit().weight(.semibold))
                                .foregroundStyle(.black.opacity(0.82))
                        )
                        .offset(x: thumbX - (usableWidth * 0.5))
                        .shadow(color: Color.black.opacity(0.12), radius: 1.2, x: 0, y: 0.6)
                }
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        let x = min(max(gesture.location.x, 0), width)
                        let thumbCenterX = thumbX + (thumbWidth * 0.5)
                        if !isDraggingFromThumb {
                            let startedOnThumb = abs(x - thumbCenterX) <= (thumbWidth * 0.8)
                            guard startedOnThumb else {
                                return
                            }
                            isDraggingFromThumb = true
                        }
                        let mappedProgress = x / width
                        value = mappedValue(forNormalizedProgress: mappedProgress)
                    }
                    .onEnded { _ in
                        isDraggingFromThumb = false
                    }
            )
        }
        .frame(minWidth: 130, maxWidth: .infinity, minHeight: 28, maxHeight: 28)
    }

    private func normalizedProgress(for value: Double) -> CGFloat {
        let clamped = min(max(value, range.lowerBound), range.upperBound)
        let span = range.upperBound - range.lowerBound
        guard span > 0 else { return 0.5 }
        return CGFloat((clamped - range.lowerBound) / span)
    }

    private func mappedValue(forNormalizedProgress progress: CGFloat) -> Double {
        let clamped = min(max(progress, 0), 1)
        let span = range.upperBound - range.lowerBound
        return range.lowerBound + (Double(clamped) * span)
    }

    private func baselineProgressForRange() -> CGFloat {
        if range.lowerBound <= 0, range.upperBound >= 0 {
            return normalizedProgress(for: 0)
        }
        if range.lowerBound >= 0 {
            return 0
        }
        return 1
    }
}

private struct SplitCueControls: View {
    @ObservedObject var viewModel: DeckViewModel
    @ObservedObject var leftDeckViewModel: TurntableDeckViewModel
    @ObservedObject var rightDeckViewModel: TurntableDeckViewModel
    let showsRightDeck: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                cueDeckButton(
                    title: deckCueTitle(deckViewModel: leftDeckViewModel, fallback: "Deck A"),
                    isEnabled: viewModel.isLeftDeckCueEnabled,
                    action: { viewModel.toggleCue(forLeftDeck: true) },
                    artwork: leftDeckViewModel.trackArtwork
                )
                .frame(maxWidth: .infinity)

                if showsRightDeck {
                    cueDeckButton(
                        title: deckCueTitle(deckViewModel: rightDeckViewModel, fallback: "Deck B"),
                        isEnabled: viewModel.isRightDeckCueEnabled,
                        action: { viewModel.toggleCue(forLeftDeck: false) },
                        artwork: rightDeckViewModel.trackArtwork
                    )
                    .frame(maxWidth: .infinity)
                }

                HStack(spacing: 6) {
                    Text("Mix")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                    HorizontalFader(
                        value: cueMixFaderBinding,
                        range: -1...1,
                        thumbText: viewModel.cueMixMode.rawValue
                    )
                    .frame(minWidth: 110, maxWidth: .infinity, minHeight: 28, maxHeight: 28)
                }
                .frame(maxWidth: .infinity)

                HStack(spacing: 6) {
                    Text("Cue")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                    HorizontalFader(
                        value: Binding(
                            get: { Double(viewModel.cueLevelPercent) },
                            set: { viewModel.setCueLevelPercent($0) }
                        ),
                        range: 0...100,
                        thumbText: "\(viewModel.cueLevelPercent)%"
                    )
                    .frame(minWidth: 110, maxWidth: .infinity, minHeight: 28, maxHeight: 28)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private func cueDeckButton(
        title: String,
        isEnabled: Bool,
        action: @escaping () -> Void,
        artwork: UIImage?
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                cueArtworkBadge(artwork: artwork)
                Text(title)
                    .font(.caption2.weight(.semibold))
                    .lineLimit(1)
                Text("CUE")
                    .font(.caption2.bold())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(isEnabled ? Color.accentColor : Color.gray.opacity(0.35))
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }
            .padding(8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(isEnabled ? Color.accentColor.opacity(0.14) : Color.black.opacity(0.08))
            )
        }
        .buttonStyle(.plain)
    }

    private var cueMixFaderBinding: Binding<Double> {
        Binding(
            get: { cueMixValue(for: viewModel.cueMixMode) },
            set: { value in
                viewModel.setCueMixMode(cueMixMode(for: value))
            }
        )
    }

    private func cueMixValue(for mode: DeckViewModel.CueMixMode) -> Double {
        switch mode {
        case .cue:
            return -1.0
        case .blend:
            return 0.0
        case .master:
            return 1.0
        }
    }

    private func cueMixMode(for value: Double) -> DeckViewModel.CueMixMode {
        if value <= -0.33 {
            return .cue
        }
        if value >= 0.33 {
            return .master
        }
        return .blend
    }

    @ViewBuilder
    private func cueArtworkBadge(artwork: UIImage?) -> some View {
        if let artwork {
            Image(uiImage: artwork)
                .resizable()
                .scaledToFill()
                .frame(width: 20, height: 20)
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
        } else {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(Color(uiColor: .secondarySystemBackground))
                .frame(width: 20, height: 20)
                .overlay(
                    Image(systemName: "music.note")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(.secondary)
                )
        }
    }

    private func deckCueTitle(deckViewModel: TurntableDeckViewModel, fallback: String) -> String {
        if let role = deckViewModel.splitDeckRole {
            switch role {
            case .master:
                return "Master"
            case .cue:
                return "Cue Deck"
            }
        }
        return fallback
    }
}

private struct DeckPanControls: View {
    @ObservedObject var deckViewModel: TurntableDeckViewModel

    var body: some View {
        HStack(spacing: 8) {
            artworkBadge

            HorizontalFader(
                value: Binding(
                    get: { deckViewModel.pan },
                    set: { deckViewModel.setPan($0) }
                ),
                range: deckViewModel.panControlRange,
                thumbText: panRoutingText(deckViewModel.pan)
            )
            .frame(minWidth: 130, maxWidth: .infinity, minHeight: 28, maxHeight: 28)
            .accessibilityLabel(accessibilityPanLabel)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private var artworkBadge: some View {
        ZStack(alignment: .bottomTrailing) {
            if let artwork = deckViewModel.trackArtwork {
                Image(uiImage: artwork)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 24, height: 24)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .stroke(Color.black.opacity(0.25), lineWidth: 0.7)
                    )
            } else {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemBackground))
                    .frame(width: 24, height: 24)
                    .overlay(
                        Image(systemName: "music.note")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(.secondary)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .stroke(Color.black.opacity(0.18), lineWidth: 0.7)
                    )
            }

            if let role = deckViewModel.splitDeckRole {
                Text(roleBadgeText(for: role))
                    .font(.system(size: 7, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 3)
                    .padding(.vertical, 1)
                    .background(Color.accentColor)
                    .clipShape(Capsule())
                    .offset(x: 4, y: 4)
            }
        }
    }

    private func panRoutingText(_ pan: Double) -> String {
        if pan < -0.1 {
            return "L"
        }
        if pan > 0.1 {
            return "R"
        }
        return "C"
    }

    private func roleBadgeText(for role: SplitDeckRole) -> String {
        switch role {
        case .master:
            return "M"
        case .cue:
            return "CUE"
        }
    }

    private var accessibilityPanLabel: String {
        guard let role = deckViewModel.splitDeckRole else {
            return "Deck pan control"
        }
        switch role {
        case .master:
            return "Master pan control"
        case .cue:
            return "Cue pan control"
        }
    }
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
