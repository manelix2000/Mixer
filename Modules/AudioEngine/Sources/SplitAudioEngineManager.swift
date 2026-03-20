import DSP
import Foundation
import os

public enum SplitDeckRole: String {
    case master
    case cue
}

/// Split engine wrapper that enforces per-role stereo routing while reusing
/// the existing playback engine implementation.
public final class SplitAudioEngineManager: AudioEngineControlling, AudioEngineRoutingProviding {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "SplitAudioEngineManager"
    )

    private let standardEngine: any AudioEngineControlling
    private let modeStore: any AudioEngineModeStoring
    private let layoutStore: any SplitDeckLayoutStoring
    private let slotIndex: Int
    public var splitDeckRole: SplitDeckRole? { isSplitModeEnabled ? currentRole : nil }
    public var panControlRange: ClosedRange<Double> {
        guard isSplitModeEnabled else {
            return -1.0...1.0
        }
        switch currentRole {
        case .master:
            return -1.0...0.0
        case .cue:
            return 0.0...1.0
        }
    }

    public init(
        slotIndex: Int,
        modeStore: any AudioEngineModeStoring = UserDefaultsAudioEngineModeStore(),
        layoutStore: any SplitDeckLayoutStoring = UserDefaultsSplitDeckLayoutStore(),
        standardEngine: any AudioEngineControlling = AudioEngineManager()
    ) {
        self.slotIndex = slotIndex
        self.modeStore = modeStore
        self.layoutStore = layoutStore
        self.standardEngine = standardEngine
        if modeStore.selectedMode == .split {
            standardEngine.setPan(Self.defaultPan(for: currentRole))
        }
        Self.log.info(
            "Split engine initialized for slot=\(slotIndex, privacy: .public) role=\(self.currentRole.rawValue, privacy: .public)."
        )
    }

    public var isRunning: Bool { standardEngine.isRunning }
    public var playbackState: AudioPlaybackState { standardEngine.playbackState }
    public var currentTime: TimeInterval { standardEngine.currentTime }
    public var totalDuration: TimeInterval { standardEngine.totalDuration }
    public var volume: Float { standardEngine.volume }
    public var pan: Float { standardEngine.pan }
    public var playbackRate: Float { standardEngine.playbackRate }
    public var isMicrophoneCaptureRunning: Bool { standardEngine.isMicrophoneCaptureRunning }

    public func startEngine() throws {
        try standardEngine.startEngine()
    }

    public func stopEngine() {
        standardEngine.stopEngine()
    }

    public func loadFile(url: URL) throws {
        try standardEngine.loadFile(url: url)
    }

    public func play() throws {
        try standardEngine.play()
    }

    public func pause() {
        standardEngine.pause()
    }

    public func seek(to time: TimeInterval) throws {
        try standardEngine.seek(to: time)
    }

    public func beginScratch() throws {
        try standardEngine.beginScratch()
    }

    public func scratch(to time: TimeInterval, angularVelocity: Double) throws {
        try standardEngine.scratch(to: time, angularVelocity: angularVelocity)
    }

    public func endScratch(resumePlayback: Bool) throws {
        try standardEngine.endScratch(resumePlayback: resumePlayback)
    }

    public func setVolume(_ value: Float) {
        standardEngine.setVolume(value)
    }

    public func setPan(_ value: Float) {
        if isSplitModeEnabled {
            standardEngine.setPan(Self.routeSafePan(value, for: currentRole))
        } else {
            standardEngine.setPan(value)
        }
    }

    public func setPlaybackRate(_ value: Float) {
        standardEngine.setPlaybackRate(value)
    }

    public func setEqualizer(low: Float, mid: Float, high: Float) {
        standardEngine.setEqualizer(low: low, mid: mid, high: high)
    }

    public func startMicrophoneCapture(
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws {
        try standardEngine.startMicrophoneCapture(onBuffer: onBuffer)
    }

    public func stopMicrophoneCapture() {
        standardEngine.stopMicrophoneCapture()
    }

    private static func defaultPan(for role: SplitDeckRole) -> Float {
        switch role {
        case .master:
            return -1.0
        case .cue:
            return 1.0
        }
    }

    private static func routeSafePan(_ value: Float, for role: SplitDeckRole) -> Float {
        let clamped = min(max(value, -1.0), 1.0)
        switch role {
        case .master:
            // Keep master deck anchored to the left side for split monitoring.
            return min(clamped, 0.0)
        case .cue:
            // Keep cue deck anchored to the right side for split monitoring.
            return max(clamped, 0.0)
        }
    }

    private var isSplitModeEnabled: Bool {
        modeStore.selectedMode == .split
    }

    private var currentRole: SplitDeckRole {
        let isEvenSlot = slotIndex % 2 == 0
        switch layoutStore.selectedLayout {
        case .leftMasterRightCue:
            return isEvenSlot ? .master : .cue
        case .leftCueRightMaster:
            return isEvenSlot ? .cue : .master
        }
    }
}
