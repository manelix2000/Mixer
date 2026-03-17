import DSP
import Foundation
import os

/// Placeholder split engine. This keeps a dedicated type for split mode while
/// delegating behavior to the standard engine until split routing is implemented.
public final class SplitAudioEngineManager: AudioEngineControlling {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "SplitAudioEngineManager"
    )

    private let standardEngine: any AudioEngineControlling

    public init(standardEngine: any AudioEngineControlling = AudioEngineManager()) {
        self.standardEngine = standardEngine
        Self.log.info("Split engine initialized in compatibility mode (standard routing).")
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
        standardEngine.setPan(value)
    }

    public func setPlaybackRate(_ value: Float) {
        standardEngine.setPlaybackRate(value)
    }

    public func startMicrophoneCapture(
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws {
        try standardEngine.startMicrophoneCapture(onBuffer: onBuffer)
    }

    public func stopMicrophoneCapture() {
        standardEngine.stopMicrophoneCapture()
    }
}
