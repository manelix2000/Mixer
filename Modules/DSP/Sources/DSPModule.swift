import Foundation

public enum DSPModule {
    /// Factory entry point used by app modules.
    /// When aubio is linked into DSP, this returns an aubio-backed detector.
    /// Otherwise it returns a compile-safe stub.
    public static func makeTempoDetector(
        configuration: TempoDetectorConfiguration = .init()
    ) -> any TempoDetecting {
#if canImport(Aubio) || canImport(aubio)
        if let detector = try? AubioTempoDetector(configuration: configuration) {
            return detector
        }
#endif
        return StubTempoDetector()
    }
}
