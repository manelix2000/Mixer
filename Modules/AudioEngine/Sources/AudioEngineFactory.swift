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
    private let splitDeckLayoutStore: any SplitDeckLayoutStoring
    private let splitRoleLock = NSLock()
    private var splitEngineCreationCount = 0

    public init(
        modeStore: any AudioEngineModeStoring = UserDefaultsAudioEngineModeStore(),
        splitDeckLayoutStore: any SplitDeckLayoutStoring = UserDefaultsSplitDeckLayoutStore()
    ) {
        self.modeStore = modeStore
        self.splitDeckLayoutStore = splitDeckLayoutStore
    }

    public func makeAudioEngine() -> any AudioEngineControlling {
        let selectedMode = modeStore.selectedMode
        let splitSlot = nextSplitSlot()
        Self.log.info(
            """
            Creating split-capable audio engine wrapper for mode=\(selectedMode.rawValue, privacy: .public) \
            slot=\(splitSlot, privacy: .public)
            """
        )
        return SplitAudioEngineManager(
            slotIndex: splitSlot,
            modeStore: modeStore,
            layoutStore: splitDeckLayoutStore
        )
    }

    private func nextSplitSlot() -> Int {
        splitRoleLock.lock()
        defer { splitRoleLock.unlock() }
        let slot = splitEngineCreationCount
        splitEngineCreationCount += 1
        return slot
    }
}
