import os

public protocol AudioEngineBuilding {
    func makeAudioEngine() -> any AudioEngineControlling
}

public final class DefaultAudioEngineFactory: AudioEngineBuilding {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "AudioEngineFactory"
    )

    private let modeStore: any AudioEngineModeStoring

    public init(modeStore: any AudioEngineModeStoring = UserDefaultsAudioEngineModeStore()) {
        self.modeStore = modeStore
    }

    public func makeAudioEngine() -> any AudioEngineControlling {
        let selectedMode = modeStore.selectedMode
        Self.log.info("Creating audio engine for mode: \(selectedMode.rawValue, privacy: .public)")
        switch selectedMode {
        case .standard:
            return AudioEngineManager()
        case .split:
            Self.log.info("Split mode selected; using split engine.")
            return SplitAudioEngineManager()
        }
    }
}
