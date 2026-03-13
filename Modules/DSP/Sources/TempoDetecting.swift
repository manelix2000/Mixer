import Foundation

/// Canonical input buffer contract for tempo detection.
///
/// Expected format for first aubio integration:
/// - `samples`: 32-bit floating point PCM, normalized to roughly `[-1, 1]`.
/// - `sampleRate`: source sample rate in Hz (for example `44_100`).
/// - `channelCount`: number of channels represented in `samples`.
/// - `isInterleaved`: indicates sample layout in `samples`.
///
/// For best deterministic behavior, callers should downmix to mono and provide a stable window size.
public struct TempoInputBuffer: Sendable {
    public let samples: [Float]
    public let sampleRate: Double
    public let channelCount: Int
    public let isInterleaved: Bool

    public init(
        samples: [Float],
        sampleRate: Double,
        channelCount: Int = 1,
        isInterleaved: Bool = false
    ) {
        self.samples = samples
        self.sampleRate = sampleRate
        self.channelCount = channelCount
        self.isInterleaved = isInterleaved
    }
}

public protocol TempoDetecting: Sendable {
    func detectTempo(in input: TempoInputBuffer) throws -> BPMResult
}

/// Configuration for tempo detection engines.
/// Values are intentionally small and explicit for stable offline analysis behavior.
public struct TempoDetectorConfiguration: Sendable, Equatable {
    /// Internal aubio method name. Keep `"default"` unless there is a measured reason to change it.
    public let method: String
    /// Analysis window size in samples.
    public let windowSize: Int
    /// Processing hop size in samples.
    public let hopSize: Int

    public init(
        method: String = "default",
        windowSize: Int = 1024,
        hopSize: Int = 512
    ) {
        self.method = method
        self.windowSize = windowSize
        self.hopSize = hopSize
    }
}

public enum TempoDetectionError: Error, Equatable {
    case emptyInput
    case invalidSampleRate
    case invalidChannelCount
    case invalidConfiguration
    case backendUnavailable
    case nativeInitializationFailed
    case nativeProcessingFailed
}
