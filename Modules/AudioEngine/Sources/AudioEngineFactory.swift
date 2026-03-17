import Foundation
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
    private let splitRoleLock = NSLock()
    private var splitEngineCreationCount = 0

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
            let role = nextSplitRole()
            Self.log.info("Split mode selected; using split engine role=\(role.rawValue, privacy: .public).")
            return SplitAudioEngineManager(role: role, modeStore: modeStore)
        }
    }

    private func nextSplitRole() -> SplitDeckRole {
        splitRoleLock.lock()
        defer { splitRoleLock.unlock() }
        let role: SplitDeckRole = (splitEngineCreationCount % 2 == 0) ? .master : .cue
        splitEngineCreationCount += 1
        return role
    }
}
