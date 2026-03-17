import Foundation

public enum AudioEngineMode: String, CaseIterable, Codable {
    case standard
    case split
}

public enum SplitDeckLayout: String, CaseIterable, Codable {
    case leftMasterRightCue
    case leftCueRightMaster
}

public protocol AudioEngineModeStoring {
    var selectedMode: AudioEngineMode { get set }
}

public protocol SplitDeckLayoutStoring {
    var selectedLayout: SplitDeckLayout { get set }
}

public final class UserDefaultsAudioEngineModeStore: AudioEngineModeStoring {
    private enum Keys {
        static let selectedMode = "dev.manelix.Mixer.audioEngine.selectedMode"
    }
    private enum RuntimeOverrides {
        static let modeArgument = "-MixerAudioEngineMode"
        static let modeEnvironment = "MIXER_AUDIO_ENGINE_MODE"
    }

    private let userDefaults: UserDefaults

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        applyRuntimeOverrideIfPresent()
    }

    public var selectedMode: AudioEngineMode {
        get {
            guard let storedRawValue = userDefaults.string(forKey: Keys.selectedMode),
                  let mode = AudioEngineMode(rawValue: storedRawValue) else {
                return .standard
            }
            return mode
        }
        set {
            userDefaults.set(newValue.rawValue, forKey: Keys.selectedMode)
        }
    }

    private func applyRuntimeOverrideIfPresent() {
        let processInfo = ProcessInfo.processInfo

        if let index = processInfo.arguments.firstIndex(of: RuntimeOverrides.modeArgument),
           processInfo.arguments.indices.contains(index + 1) {
            let rawValue = processInfo.arguments[index + 1].lowercased()
            if let mode = AudioEngineMode(rawValue: rawValue) {
                selectedMode = mode
            }
            return
        }

        if let rawValue = processInfo.environment[RuntimeOverrides.modeEnvironment]?.lowercased(),
           let mode = AudioEngineMode(rawValue: rawValue) {
            selectedMode = mode
        }
    }
}

public final class UserDefaultsSplitDeckLayoutStore: SplitDeckLayoutStoring {
    private enum Keys {
        static let selectedLayout = "dev.manelix.Mixer.audioEngine.splitDeckLayout"
    }
    private enum RuntimeOverrides {
        static let layoutArgument = "-MixerSplitDeckLayout"
        static let layoutEnvironment = "MIXER_SPLIT_DECK_LAYOUT"
    }

    private let userDefaults: UserDefaults

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        applyRuntimeOverrideIfPresent()
    }

    public var selectedLayout: SplitDeckLayout {
        get {
            guard let storedRawValue = userDefaults.string(forKey: Keys.selectedLayout),
                  let layout = SplitDeckLayout(rawValue: storedRawValue) else {
                return .leftMasterRightCue
            }
            return layout
        }
        set {
            userDefaults.set(newValue.rawValue, forKey: Keys.selectedLayout)
        }
    }

    private func applyRuntimeOverrideIfPresent() {
        let processInfo = ProcessInfo.processInfo

        if let index = processInfo.arguments.firstIndex(of: RuntimeOverrides.layoutArgument),
           processInfo.arguments.indices.contains(index + 1) {
            let rawValue = processInfo.arguments[index + 1]
            if let layout = SplitDeckLayout(rawValue: rawValue) {
                selectedLayout = layout
            }
            return
        }

        if let rawValue = processInfo.environment[RuntimeOverrides.layoutEnvironment],
           let layout = SplitDeckLayout(rawValue: rawValue) {
            selectedLayout = layout
        }
    }
}
