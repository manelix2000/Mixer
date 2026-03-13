import Foundation

/// Tempo detection output exposed to app modules.
/// This type intentionally hides any implementation details from third-party DSP engines.
public enum BPMResult: Equatable, Sendable {
    /// A BPM value was detected.
    /// - Parameters:
    ///   - bpm: Beats per minute.
    ///   - confidence: Normalized confidence in `[0, 1]`.
    case detected(bpm: Double, confidence: Double)

    /// A detector could not produce a BPM.
    /// Use this case for non-fatal situations (for example, silent input or stub implementation).
    case unavailable(reason: String)
}
