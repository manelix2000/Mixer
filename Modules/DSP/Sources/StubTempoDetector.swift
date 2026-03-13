import Foundation

/// Compile-safe placeholder until aubio-backed tempo detection is integrated.
/// This detector intentionally does not synthesize BPM values.
public struct StubTempoDetector: TempoDetecting {
    public init() {}

    public func detectTempo(in input: TempoInputBuffer) throws -> BPMResult {
        guard !input.samples.isEmpty else {
            throw TempoDetectionError.emptyInput
        }
        guard input.sampleRate > 0 else {
            throw TempoDetectionError.invalidSampleRate
        }
        guard input.channelCount > 0 else {
            throw TempoDetectionError.invalidChannelCount
        }

        return .unavailable(reason: "Tempo detection unavailable: aubio backend is not linked in this build.")
    }
}
