import AVFoundation
import AudioToolbox
import DSP
import Foundation
import os

public enum AudioPlaybackState: Equatable {
    case idle
    case fileLoaded
    case playing
    case paused
}

public protocol AudioEngineControlling: AnyObject {
    var isRunning: Bool { get }
    var playbackState: AudioPlaybackState { get }
    var currentTime: TimeInterval { get }
    var totalDuration: TimeInterval { get }
    var volume: Float { get }
    var pan: Float { get }
    var playbackRate: Float { get }
    var isMicrophoneCaptureRunning: Bool { get }
    func startEngine() throws
    func stopEngine()
    func loadFile(url: URL) throws
    func play() throws
    func pause()
    func seek(to time: TimeInterval) throws
    func beginScratch() throws
    func scratch(to time: TimeInterval, angularVelocity: Double) throws
    func endScratch(resumePlayback: Bool) throws
    func setVolume(_ value: Float)
    func setPan(_ value: Float)
    func setPlaybackRate(_ value: Float)
    func startMicrophoneCapture(
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws
    func stopMicrophoneCapture()
}

public enum AudioEngineManagerError: Error {
    case startFailed(Error)
    case fileLoadFailed(Error)
    case noFileLoaded
    case scratchNotActive
    case microphoneUnavailable
    case microphoneUnsupportedInCurrentEnvironment
    case microphonePermissionDenied
    case sessionConfigurationFailed(Error)
}

extension AudioEngineManagerError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case let .startFailed(error):
            return "Audio engine failed to start (\(error.localizedDescription))."
        case let .fileLoadFailed(error):
            return "Audio file load failed (\(error.localizedDescription))."
        case .noFileLoaded:
            return "No audio file is loaded."
        case .scratchNotActive:
            return "Scratch mode is not active."
        case .microphoneUnavailable:
            return "Microphone input is unavailable on the current audio route."
        case .microphoneUnsupportedInCurrentEnvironment:
            return "Microphone capture is not supported in this runtime environment. Use a physical iPhone for mic BPM."
        case .microphonePermissionDenied:
            return "Microphone permission is denied."
        case let .sessionConfigurationFailed(error):
            return "Audio session configuration failed (\(error.localizedDescription))."
        }
    }
}

public final class AudioEngineManager: AudioEngineControlling {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "AudioEngineManager"
    )
    private static let sharedSessionLock = NSLock()
    private static var hasConfiguredSharedPlaybackSession = false
    private static var isSharedPlaybackSessionActive = false

    private let engine: AVAudioEngine
    private let playerNode: AVAudioPlayerNode
    private let varispeedNode: AVAudioUnitVarispeed
    private let stateLock = NSLock()
    private var isGraphConfigured = false
    private var loadedFile: AVAudioFile?
    private var internalPlaybackState: AudioPlaybackState = .idle
    private var internalVolume: Float
    private var internalPan: Float
    private var internalPlaybackRate: Float
    private var lastKnownCurrentTime: TimeInterval = 0
    private var playbackStartOffset: TimeInterval = 0
    private var isScratchModeActive = false
    private var scratchWasPlayingBeforeGesture = false
    private var scratchCurrentTime: TimeInterval = 0
    private var microphoneCaptureRunning = false
    private var isMicrophoneSessionActive = false
    private var microphoneCaptureEngine: AVAudioEngine?
    private var microphoneCaptureSession: AVCaptureSession?
    private var microphoneCaptureDelegate: MacAudioCaptureDelegate?
    private var microphoneCaptureOutputQueue: DispatchQueue?
    private let microphoneCaptureSessionControlQueue = DispatchQueue(
        label: "dev.manelix.Mixer.AudioEngine.mic-capture-session-control",
        qos: .userInitiated
    )
    private let microphoneDispatchQueue = DispatchQueue(
        label: "dev.manelix.Mixer.AudioEngine.microphone",
        qos: .userInitiated
    )

    public init(
        engine: AVAudioEngine = AVAudioEngine(),
        playerNode: AVAudioPlayerNode = AVAudioPlayerNode(),
        varispeedNode: AVAudioUnitVarispeed = AVAudioUnitVarispeed(),
        initialVolume: Float = 1.0,
        initialPan: Float = 0.0,
        initialPlaybackRate: Float = 1.0
    ) {
        self.engine = engine
        self.playerNode = playerNode
        self.varispeedNode = varispeedNode
        self.internalVolume = Self.clamp(volume: initialVolume)
        self.internalPan = Self.clamp(pan: initialPan)
        self.internalPlaybackRate = Self.clamp(playbackRate: initialPlaybackRate)
        self.playerNode.volume = self.internalVolume
        self.playerNode.pan = self.internalPan
        self.varispeedNode.rate = self.internalPlaybackRate
    }

    public var isRunning: Bool {
        withStateLock {
            engine.isRunning
        }
    }

    public var playbackState: AudioPlaybackState {
        withStateLock {
            internalPlaybackState
        }
    }

    public var currentTime: TimeInterval {
        withStateLock {
            resolvedCurrentTimeLocked()
        }
    }

    public var totalDuration: TimeInterval {
        withStateLock {
            totalDurationLocked()
        }
    }

    public var volume: Float {
        withStateLock {
            internalVolume
        }
    }

    public var pan: Float {
        withStateLock {
            internalPan
        }
    }

    public var playbackRate: Float {
        withStateLock {
            internalPlaybackRate
        }
    }

    public var isMicrophoneCaptureRunning: Bool {
        withStateLock {
            microphoneCaptureRunning
        }
    }

    public func startEngine() throws {
        try withStateLock {
            try startEngineLocked()
        }
    }

    public func stopEngine() {
        withStateLock {
            guard engine.isRunning else {
                return
            }

            playerNode.stop()
            engine.stop()
            internalPlaybackState = loadedFile == nil ? .idle : .paused
        }
    }

    public func loadFile(url: URL) throws {
        try withStateLock {
            if !isGraphConfigured {
                configureGraph()
                isGraphConfigured = true
            }

            let file: AVAudioFile
            do {
                file = try AVAudioFile(forReading: url)
            } catch {
                throw AudioEngineManagerError.fileLoadFailed(error)
            }

            playerNode.stop()
            playerNode.reset()
            playerNode.scheduleFile(file, at: nil, completionHandler: nil)
            loadedFile = file
            playbackStartOffset = 0
            lastKnownCurrentTime = 0
            isScratchModeActive = false
            scratchWasPlayingBeforeGesture = false
            scratchCurrentTime = 0
            internalPlaybackState = .fileLoaded
        }
    }

    public func play() throws {
        try withStateLock {
            guard loadedFile != nil else {
                throw AudioEngineManagerError.noFileLoaded
            }

            try startEngineLocked()

            guard !playerNode.isPlaying else {
                internalPlaybackState = .playing
                return
            }

            if let file = loadedFile {
                let duration = totalDurationLocked()
                let current = resolvedCurrentTimeLocked()
                if duration > 0, current >= (duration - 0.01) {
                    _ = try scheduleFromTimeLocked(file: file, time: 0, shouldPlay: true)
                    return
                }
            }

            playerNode.play()
            internalPlaybackState = .playing
        }
    }

    public func pause() {
        withStateLock {
            guard loadedFile != nil else {
                internalPlaybackState = .idle
                return
            }

            guard playerNode.isPlaying else {
                internalPlaybackState = .paused
                return
            }

            lastKnownCurrentTime = resolvedCurrentTimeLocked()
            playerNode.pause()
            internalPlaybackState = .paused
        }
    }

    public func seek(to time: TimeInterval) throws {
        try withStateLock {
            guard let file = loadedFile else {
                throw AudioEngineManagerError.noFileLoaded
            }

            let duration = totalDurationLocked()
            let clampedTime = min(max(time, 0), duration)
            let wasPlaying = playerNode.isPlaying
            _ = try scheduleFromTimeLocked(
                file: file,
                time: clampedTime,
                shouldPlay: wasPlaying
            )
        }
    }

    public func beginScratch() throws {
        try withStateLock {
            guard loadedFile != nil else {
                throw AudioEngineManagerError.noFileLoaded
            }

            try startEngineLocked()
            if !isScratchModeActive {
                scratchWasPlayingBeforeGesture = playerNode.isPlaying
            }

            scratchCurrentTime = resolvedCurrentTimeLocked()
            playerNode.stop()
            playerNode.reset()
            isScratchModeActive = true
            internalPlaybackState = .paused
        }
    }

    public func scratch(to time: TimeInterval, angularVelocity: Double) throws {
        try withStateLock {
            guard let file = loadedFile else {
                throw AudioEngineManagerError.noFileLoaded
            }
            guard isScratchModeActive else {
                throw AudioEngineManagerError.scratchNotActive
            }

            let duration = totalDurationLocked()
            let clampedTime = min(max(time, 0), duration)
            scratchCurrentTime = clampedTime
            playbackStartOffset = clampedTime
            lastKnownCurrentTime = clampedTime

            // Reverse scratch path: read a small chunk and schedule reversed mono buffer.
            if angularVelocity < 0 {
                let sampleRate = file.processingFormat.sampleRate
                let speed = abs(angularVelocity)
                let dynamicDuration = min(max(0.04 + (speed * 0.008), 0.04), 0.10)
                let previewFrames = AVAudioFramePosition(min(max(sampleRate * dynamicDuration, 1_024), 8_192))
                let centerFrame = AVAudioFramePosition(clampedTime * sampleRate)
                let startFrame = max(0, centerFrame - previewFrames)
                let frameCount = AVAudioFrameCount(max(1, centerFrame - startFrame))

                let mono = try readMonoSamplesLocked(
                    from: file,
                    startFrame: startFrame,
                    frameCount: frameCount
                )
                if !mono.isEmpty {
                    if scheduleMonoScratchBufferLocked(samples: mono, reversed: true) {
                        internalPlaybackState = .playing
                        return
                    }
                }
            }

            // Fallback path: segment scheduling from file (forward only).
            let sampleRate = file.processingFormat.sampleRate
            let startFrame = AVAudioFramePosition(clampedTime * sampleRate)
            let availableFrames = max(file.length - startFrame, 0)
            guard availableFrames > 0 else {
                playerNode.stop()
                playerNode.reset()
                internalPlaybackState = .paused
                return
            }
            let previewFrames = AVAudioFramePosition(min(max(sampleRate * 0.06, 2_048), 8_192))
            let frameCount = AVAudioFrameCount(min(availableFrames, previewFrames))
            playerNode.stop()
            playerNode.reset()
            playerNode.scheduleSegment(file, startingFrame: startFrame, frameCount: frameCount, at: nil, completionHandler: nil)
            playerNode.play()
            internalPlaybackState = .playing
        }
    }

    public func endScratch(resumePlayback: Bool) throws {
        try withStateLock {
            guard let file = loadedFile else {
                throw AudioEngineManagerError.noFileLoaded
            }

            let fallbackCurrentTime = resolvedCurrentTimeLocked()
            let finalTime = isScratchModeActive ? scratchCurrentTime : fallbackCurrentTime
            isScratchModeActive = false

            let shouldResume = resumePlayback && scratchWasPlayingBeforeGesture
            scratchWasPlayingBeforeGesture = false
            _ = try scheduleFromTimeLocked(
                file: file,
                time: finalTime,
                shouldPlay: shouldResume
            )
        }
    }

    public func setVolume(_ value: Float) {
        withStateLock {
            internalVolume = Self.clamp(volume: value)
            playerNode.volume = internalVolume
        }
    }

    public func setPan(_ value: Float) {
        withStateLock {
            internalPan = Self.clamp(pan: value)
            playerNode.pan = internalPan
        }
    }

    public func setPlaybackRate(_ value: Float) {
        withStateLock {
            internalPlaybackRate = Self.clamp(playbackRate: value)
            varispeedNode.rate = internalPlaybackRate
        }
    }

    public func startMicrophoneCapture(
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws {
#if targetEnvironment(simulator) && arch(x86_64)
        Self.log.error("Mic capture unavailable: x86_64 simulator environment.")
        throw AudioEngineManagerError.microphoneUnsupportedInCurrentEnvironment
#else
        try withStateLock {
            if #available(iOS 14.0, *), ProcessInfo.processInfo.isiOSAppOnMac {
                try startMicrophoneCaptureOnMacDesignedForiOSLocked(onBuffer: onBuffer)
                return
            }

            guard !microphoneCaptureRunning else {
                Self.log.info("Mic capture start ignored: already running.")
                return
            }

            let session = AVAudioSession.sharedInstance()
            Self.log.info(
                "Mic capture start requested. permission=\(String(describing: AVAudioApplication.shared.recordPermission), privacy: .public)"
            )
            if AVAudioApplication.shared.recordPermission == .denied {
                Self.log.error("Mic capture denied: record permission is denied.")
                throw AudioEngineManagerError.microphonePermissionDenied
            }

            do {
                try? session.setPreferredSampleRate(48_000)
                try? session.setPreferredInputNumberOfChannels(1)
                try configurePlaybackSessionLocked()
                isMicrophoneSessionActive = true
                Self.log.info(
                    """
                    Mic session active. mode=\(session.mode.rawValue, privacy: .public) \
                    inputAvailable=\(session.isInputAvailable, privacy: .public) \
                    route=\(Self.routeSummary(session.currentRoute), privacy: .public)
                    """
                )
            } catch {
                Self.log.error("Mic session configuration failed: \(error.localizedDescription, privacy: .public)")
                throw AudioEngineManagerError.sessionConfigurationFailed(error)
            }

            do {
                if let inputs = session.availableInputs {
                    Self.log.info("Available inputs before preference: \(Self.portsSummary(inputs), privacy: .public)")
                } else {
                    Self.log.info("Available inputs before preference: none")
                }
                if !session.isInputAvailable {
                    Self.log.warning("Mic input unavailable before recovery. route=\(Self.routeSummary(session.currentRoute), privacy: .public)")
                    _ = attemptMicrophoneRouteRecoveryLocked(session)
                }

                // Some environments may report incomplete route metadata.
                // Rely on input node format validation below as the source of truth.
                try startEngineLocked()
                // Avoid touching playback engine input I/O while track playback is active.
                // This prevents session/engine churn that can interrupt ongoing audio.
                let shouldUseDedicatedCaptureEngine = playerNode.isPlaying || internalPlaybackState == .playing
                if shouldUseDedicatedCaptureEngine {
                    let captureEngine = AVAudioEngine()
                    let selectedFormat = try installMicrophoneTapLocked(
                        on: captureEngine,
                        session: session,
                        onBuffer: onBuffer
                    )
                    if !captureEngine.isRunning {
                        try captureEngine.start()
                    }
                    microphoneCaptureEngine = captureEngine
                    Self.log.info("Mic format selected (dedicated engine; playback active): \(Self.formatSummary(selectedFormat), privacy: .public)")
                } else {
                    do {
                        let selectedFormat = try installMicrophoneTapLocked(
                            on: engine,
                            session: session,
                            onBuffer: onBuffer
                        )
                        microphoneCaptureEngine = nil
                        Self.log.info("Mic format selected (main engine): \(Self.formatSummary(selectedFormat), privacy: .public)")
                    } catch AudioEngineManagerError.microphoneUnavailable {
                        // Some devices/routes can expose an output-only format on the playback engine input node.
                        // Fall back to a dedicated capture engine to keep playback and mic capture decoupled.
                        let captureEngine = AVAudioEngine()
                        let selectedFormat = try installMicrophoneTapLocked(
                            on: captureEngine,
                            session: session,
                            onBuffer: onBuffer
                        )
                        if !captureEngine.isRunning {
                            try captureEngine.start()
                        }
                        microphoneCaptureEngine = captureEngine
                        Self.log.info("Mic format selected (dedicated engine): \(Self.formatSummary(selectedFormat), privacy: .public)")
                    }
                }

                microphoneCaptureRunning = true
                Self.log.info("Mic capture started successfully.")
            } catch {
                // If mic startup fails after switching to playAndRecord, restore playback session.
                Self.log.error(
                    """
                    Mic capture startup failed: \(error.localizedDescription, privacy: .public). \
                    route=\(Self.routeSummary(session.currentRoute), privacy: .public)
                    """
                )
                isMicrophoneSessionActive = false
                do {
                    microphoneCaptureEngine?.inputNode.removeTap(onBus: 0)
                    microphoneCaptureEngine?.stop()
                    microphoneCaptureEngine = nil
                    try configurePlaybackSessionLocked()
                } catch {
                    // Preserve original startup error and keep fallback best-effort.
                }
                throw error
            }
        }
#endif
    }

    public func stopMicrophoneCapture() {
        withStateLock {
            guard microphoneCaptureRunning || isMicrophoneSessionActive else {
                Self.log.info("Mic capture stop ignored: not active.")
                return
            }

            if let captureEngine = microphoneCaptureEngine {
                captureEngine.inputNode.removeTap(onBus: 0)
                captureEngine.stop()
                microphoneCaptureEngine = nil
            } else if let captureSession = microphoneCaptureSession {
                let controlQueue = microphoneCaptureSessionControlQueue
                controlQueue.async {
                    captureSession.stopRunning()
                }
                microphoneCaptureSession = nil
                microphoneCaptureDelegate = nil
                microphoneCaptureOutputQueue = nil
            } else {
                engine.inputNode.removeTap(onBus: 0)
            }
            microphoneCaptureRunning = false
            isMicrophoneSessionActive = false
            // Do not force a session category change here; keep current playback uninterrupted.
            Self.log.info("Mic capture stopped. Playback remains active.")
        }
    }

    private func configureGraph() {
        engine.attach(playerNode)
        engine.attach(varispeedNode)
        engine.connect(playerNode, to: varispeedNode, format: nil)
        engine.connect(varispeedNode, to: engine.mainMixerNode, format: nil)
    }

    private func startEngineLocked() throws {
        if !isGraphConfigured {
            configureGraph()
            isGraphConfigured = true
        }
        try configurePlaybackSessionLocked()

        guard !engine.isRunning else {
            return
        }

        do {
            try engine.start()
        } catch {
            throw AudioEngineManagerError.startFailed(error)
        }
    }

    private func scheduleFromTimeLocked(
        file: AVAudioFile,
        time: TimeInterval,
        shouldPlay: Bool
    ) throws -> Bool {
        let duration = totalDurationLocked()
        let clampedTime = min(max(time, 0), duration)
        let sampleRate = file.processingFormat.sampleRate
        let startFrame = AVAudioFramePosition(clampedTime * sampleRate)
        let remainingFrames = max(file.length - startFrame, 0)

        playerNode.stop()
        playerNode.reset()
        playbackStartOffset = clampedTime
        lastKnownCurrentTime = clampedTime

        if remainingFrames > 0 {
            playerNode.scheduleSegment(
                file,
                startingFrame: startFrame,
                frameCount: AVAudioFrameCount(min(remainingFrames, AVAudioFramePosition(UInt32.max))),
                at: nil,
                completionHandler: nil
            )
        }

        if shouldPlay && remainingFrames > 0 {
            try startEngineLocked()
            playerNode.play()
            internalPlaybackState = .playing
        } else if remainingFrames > 0 {
            internalPlaybackState = .paused
        } else {
            internalPlaybackState = .fileLoaded
        }

        return remainingFrames > 0
    }

    private func configurePlaybackSessionLocked() throws {
        let session = AVAudioSession.sharedInstance()
        do {
            try Self.configureSharedPlaybackSessionIfNeeded(session: session)
        } catch {
            throw AudioEngineManagerError.sessionConfigurationFailed(error)
        }
    }

    private static func configureSharedPlaybackSessionIfNeeded(session: AVAudioSession) throws {
        sharedSessionLock.lock()
        defer { sharedSessionLock.unlock() }

        // AVAudioSession is process-wide. Reapplying category/activation from every deck can
        // stall UI/audio and cause audible state shifts when a second deck starts.
        if !hasConfiguredSharedPlaybackSession {
            try session.setCategory(
                .playAndRecord,
                mode: .default,
                options: [.mixWithOthers, .defaultToSpeaker]
            )
            hasConfiguredSharedPlaybackSession = true
        }

        if !isSharedPlaybackSessionActive {
            try session.setActive(true)
            isSharedPlaybackSessionActive = true
        }
    }

    @discardableResult
    private func attemptMicrophoneRouteRecoveryLocked(_ session: AVAudioSession) -> Bool {
        Self.log.info(
            "Attempting mic route recovery. route(before)=\(Self.routeSummary(session.currentRoute), privacy: .public)"
        )
        // Try to recover from output-only routes by re-preferencing built-in mic
        // and relaxing mode constraints that can make some routes report no input.
        do {
            try session.setMode(.default)
        } catch {
            // Best effort.
        }

        if let inputs = session.availableInputs {
            if let builtInMic = inputs.first(where: { $0.portType == .builtInMic }) {
                try? session.setPreferredInput(builtInMic)
            } else {
                try? session.setPreferredInput(nil)
            }
        }

        try? session.overrideOutputAudioPort(.speaker)
        try? session.setActive(true)
        Self.log.info(
            """
            Mic route recovery result: inputAvailable=\(session.isInputAvailable, privacy: .public) \
            route(after)=\(Self.routeSummary(session.currentRoute), privacy: .public)
            """
        )
        return session.isInputAvailable
    }

    private func withStateLock<T>(_ operation: () throws -> T) rethrows -> T {
        stateLock.lock()
        defer { stateLock.unlock() }
        return try operation()
    }

    private static func clamp(volume: Float) -> Float {
        min(max(volume, 0), 1)
    }

    private static func clamp(pan: Float) -> Float {
        min(max(pan, -1), 1)
    }

    private static func clamp(playbackRate: Float) -> Float {
        min(max(playbackRate, 0.5), 2.0)
    }

    private static func isValidMicrophoneFormat(_ format: AVAudioFormat) -> Bool {
        format.sampleRate > 0 && format.channelCount > 0
    }

    private func resolveMicrophoneTapFormatLocked(
        on audioEngine: AVAudioEngine,
        session: AVAudioSession,
        inputNode: AVAudioInputNode
    ) throws -> AVAudioFormat {
        let firstPreferred = inputNode.outputFormat(forBus: 0)
        let firstFallback = inputNode.inputFormat(forBus: 0)
        Self.log.info(
            """
            Mic formats. preferred=\(Self.formatSummary(firstPreferred), privacy: .public) \
            fallback=\(Self.formatSummary(firstFallback), privacy: .public)
            """
        )
        if Self.isValidMicrophoneFormat(firstPreferred) {
            return firstPreferred
        }
        if Self.isValidMicrophoneFormat(firstFallback) {
            return firstFallback
        }

        // Retry after forcing route recovery and engine I/O refresh.
        _ = attemptMicrophoneRouteRecoveryLocked(session)
        try refreshInputNodeHardwareFormatLocked(on: audioEngine)

        let secondPreferred = inputNode.outputFormat(forBus: 0)
        let secondFallback = inputNode.inputFormat(forBus: 0)
        Self.log.info(
            """
            Mic formats after recovery. preferred=\(Self.formatSummary(secondPreferred), privacy: .public) \
            fallback=\(Self.formatSummary(secondFallback), privacy: .public)
            """
        )
        if Self.isValidMicrophoneFormat(secondPreferred) {
            return secondPreferred
        }
        if Self.isValidMicrophoneFormat(secondFallback) {
            return secondFallback
        }

        Self.log.error(
            """
            Mic format invalid after recovery. inputAvailable=\(session.isInputAvailable, privacy: .public) \
            sessionRate=\(session.sampleRate, privacy: .public) \
            inputChannels=\(session.inputNumberOfChannels, privacy: .public) \
            route=\(Self.routeSummary(session.currentRoute), privacy: .public)
            """
        )
        throw AudioEngineManagerError.microphoneUnavailable
    }

    private func refreshInputNodeHardwareFormatLocked(on audioEngine: AVAudioEngine) throws {
        if audioEngine === engine {
            let wasRunning = engine.isRunning
            let wasPlaying = playerNode.isPlaying
            if wasPlaying {
                lastKnownCurrentTime = resolvedCurrentTimeLocked()
                playerNode.pause()
            }
            if wasRunning {
                Self.log.info("Refreshing main engine I/O to obtain valid input hardware format.")
                engine.stop()
                try engine.start()
                if wasPlaying {
                    playerNode.play()
                }
            }
            return
        }

        let wasRunning = audioEngine.isRunning
        if wasRunning {
            Self.log.info("Refreshing dedicated capture engine I/O format.")
            audioEngine.stop()
        }
        try audioEngine.start()
    }

    private func installMicrophoneTapLocked(
        on audioEngine: AVAudioEngine,
        session: AVAudioSession,
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws -> AVAudioFormat {
        let inputNode = audioEngine.inputNode
        let selectedFormat = try resolveMicrophoneTapFormatLocked(
            on: audioEngine,
            session: session,
            inputNode: inputNode
        )

        inputNode.removeTap(onBus: 0)
        inputNode.installTap(
            onBus: 0,
            bufferSize: 1_024,
            format: selectedFormat
        ) { [weak self] buffer, _ in
            self?.handleMicrophoneBuffer(buffer, format: selectedFormat, onBuffer: onBuffer)
        }
        return selectedFormat
    }

    private static func formatSummary(_ format: AVAudioFormat) -> String {
        "sampleRate=\(format.sampleRate), channels=\(format.channelCount), commonFormat=\(format.commonFormat.rawValue)"
    }

    private static func routeSummary(_ route: AVAudioSessionRouteDescription) -> String {
        let inputs = portsSummary(route.inputs)
        let outputs = portsSummary(route.outputs)
        return "inputs=[\(inputs)] outputs=[\(outputs)]"
    }

    private static func portsSummary(_ ports: [AVAudioSessionPortDescription]) -> String {
        ports.map { port in
            "\(port.portType.rawValue):\(port.portName)"
        }.joined(separator: ", ")
    }

    private func startMicrophoneCaptureOnMacDesignedForiOSLocked(
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws {
        guard !microphoneCaptureRunning else {
            Self.log.info("Mic capture start ignored on My Mac: already running.")
            return
        }

        if AVAudioApplication.shared.recordPermission == .denied {
            Self.log.error("Mic capture denied on My Mac: record permission is denied.")
            throw AudioEngineManagerError.microphonePermissionDenied
        }

        do {
            let captureEngine = AVAudioEngine()
            let selectedFormat = try installMicrophoneTapWithoutSessionLocked(
                on: captureEngine,
                onBuffer: onBuffer
            )
            if !captureEngine.isRunning {
                try captureEngine.start()
            }
            microphoneCaptureEngine = captureEngine
            microphoneCaptureRunning = true
            isMicrophoneSessionActive = true
            Self.log.info(
                "Mic capture started on My Mac. format=\(Self.formatSummary(selectedFormat), privacy: .public)"
            )
        } catch {
            Self.log.error(
                "Mic capture via AVAudioEngine failed on My Mac: \(error.localizedDescription, privacy: .public). Falling back to AVCaptureSession."
            )
            microphoneCaptureEngine?.inputNode.removeTap(onBus: 0)
            microphoneCaptureEngine?.stop()
            microphoneCaptureEngine = nil
            microphoneCaptureRunning = false
            isMicrophoneSessionActive = false
            try startMicrophoneCaptureViaAVCaptureLocked(onBuffer: onBuffer)
        }
    }

    private func resolveMicrophoneTapFormatWithoutSessionLocked(
        on audioEngine: AVAudioEngine
    ) throws -> AVAudioFormat {
        let inputNode = audioEngine.inputNode
        let preferred = inputNode.outputFormat(forBus: 0)
        let fallback = inputNode.inputFormat(forBus: 0)
        Self.log.info(
            """
            My Mac mic formats. preferred=\(Self.formatSummary(preferred), privacy: .public) \
            fallback=\(Self.formatSummary(fallback), privacy: .public)
            """
        )

        if Self.isValidMicrophoneFormat(preferred) {
            return preferred
        }
        if Self.isValidMicrophoneFormat(fallback) {
            return fallback
        }

        try refreshInputNodeHardwareFormatLocked(on: audioEngine)
        let retriedPreferred = inputNode.outputFormat(forBus: 0)
        let retriedFallback = inputNode.inputFormat(forBus: 0)
        Self.log.info(
            """
            My Mac mic formats after refresh. preferred=\(Self.formatSummary(retriedPreferred), privacy: .public) \
            fallback=\(Self.formatSummary(retriedFallback), privacy: .public)
            """
        )
        if Self.isValidMicrophoneFormat(retriedPreferred) {
            return retriedPreferred
        }
        if Self.isValidMicrophoneFormat(retriedFallback) {
            return retriedFallback
        }

        throw AudioEngineManagerError.microphoneUnavailable
    }

    private func installMicrophoneTapWithoutSessionLocked(
        on audioEngine: AVAudioEngine,
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws -> AVAudioFormat {
        let inputNode = audioEngine.inputNode
        let selectedFormat = try resolveMicrophoneTapFormatWithoutSessionLocked(on: audioEngine)
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(
            onBus: 0,
            bufferSize: 1_024,
            format: selectedFormat
        ) { [weak self] buffer, _ in
            self?.handleMicrophoneBuffer(buffer, format: selectedFormat, onBuffer: onBuffer)
        }
        return selectedFormat
    }

    private func startMicrophoneCaptureViaAVCaptureLocked(
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) throws {
        let captureSession = AVCaptureSession()
        captureSession.beginConfiguration()

        guard let audioDevice = AVCaptureDevice.default(for: .audio) else {
            captureSession.commitConfiguration()
            throw AudioEngineManagerError.microphoneUnavailable
        }

        let deviceInput: AVCaptureDeviceInput
        do {
            deviceInput = try AVCaptureDeviceInput(device: audioDevice)
        } catch {
            captureSession.commitConfiguration()
            throw AudioEngineManagerError.microphoneUnavailable
        }

        guard captureSession.canAddInput(deviceInput) else {
            captureSession.commitConfiguration()
            throw AudioEngineManagerError.microphoneUnavailable
        }
        captureSession.addInput(deviceInput)

        let audioOutput = AVCaptureAudioDataOutput()
        let outputQueue = DispatchQueue(
            label: "dev.manelix.Mixer.AudioEngine.mic-capture-output",
            qos: .userInitiated
        )
        let delegate = MacAudioCaptureDelegate(
            processingQueue: microphoneDispatchQueue,
            onBuffer: onBuffer
        )
        audioOutput.setSampleBufferDelegate(delegate, queue: outputQueue)

        guard captureSession.canAddOutput(audioOutput) else {
            captureSession.commitConfiguration()
            throw AudioEngineManagerError.microphoneUnavailable
        }
        captureSession.addOutput(audioOutput)
        captureSession.commitConfiguration()

        let controlQueue = microphoneCaptureSessionControlQueue
        controlQueue.async {
            captureSession.startRunning()
        }
        microphoneCaptureSession = captureSession
        microphoneCaptureDelegate = delegate
        microphoneCaptureOutputQueue = outputQueue
        microphoneCaptureRunning = true
        isMicrophoneSessionActive = true
        Self.log.info("Mic capture started on My Mac via AVCaptureSession.")
    }

    private func totalDurationLocked() -> TimeInterval {
        guard let file = loadedFile else {
            return 0
        }
        return Double(file.length) / file.processingFormat.sampleRate
    }

    private func readMonoSamplesLocked(
        from file: AVAudioFile,
        startFrame: AVAudioFramePosition,
        frameCount: AVAudioFrameCount
    ) throws -> [Float] {
        guard frameCount > 0 else { return [] }
        let format = file.processingFormat
        let channels = Int(format.channelCount)
        guard channels > 0 else { return [] }

        let clampedStartFrame = max(0, min(startFrame, max(file.length - 1, 0)))
        file.framePosition = clampedStartFrame

        guard let buffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: frameCount
        ) else {
            return []
        }
        try file.read(into: buffer, frameCount: frameCount)
        let framesRead = Int(buffer.frameLength)
        guard framesRead > 0 else { return [] }

        var mono = [Float](repeating: 0, count: framesRead)
        if let floatData = buffer.floatChannelData {
            let invChannels = 1.0 / Float(channels)
            for frame in 0..<framesRead {
                var sum: Float = 0
                for channel in 0..<channels {
                    sum += floatData[channel][frame]
                }
                mono[frame] = sum * invChannels
            }
            return mono
        }

        if let int16Data = buffer.int16ChannelData {
            let invChannels = 1.0 / Float(channels)
            let scale = 1.0 / Float(Int16.max)
            for frame in 0..<framesRead {
                var sum: Float = 0
                for channel in 0..<channels {
                    sum += Float(int16Data[channel][frame]) * scale
                }
                mono[frame] = sum * invChannels
            }
            return mono
        }

        if let int32Data = buffer.int32ChannelData {
            let invChannels = 1.0 / Float(channels)
            let scale = 1.0 / Float(Int32.max)
            for frame in 0..<framesRead {
                var sum: Float = 0
                for channel in 0..<channels {
                    sum += Float(int32Data[channel][frame]) * scale
                }
                mono[frame] = sum * invChannels
            }
            return mono
        }

        return []
    }

    private func scheduleMonoScratchBufferLocked(samples: [Float], reversed: Bool) -> Bool {
        guard !samples.isEmpty else { return false }
        let outputFormat = playerNode.outputFormat(forBus: 0)
        let channelCount = max(Int(outputFormat.channelCount), 1)
        let frameCount = samples.count
        guard let buffer = AVAudioPCMBuffer(
            pcmFormat: outputFormat,
            frameCapacity: AVAudioFrameCount(frameCount)
        ) else {
            return false
        }
        buffer.frameLength = AVAudioFrameCount(frameCount)
        guard let channelData = buffer.floatChannelData else {
            return false
        }

        for frame in 0..<frameCount {
            let sourceIndex = reversed ? (frameCount - 1 - frame) : frame
            let value = samples[sourceIndex]
            for channel in 0..<channelCount {
                channelData[channel][frame] = value
            }
        }

        playerNode.stop()
        playerNode.reset()
        playerNode.scheduleBuffer(buffer, at: nil, options: .interrupts, completionHandler: nil)
        playerNode.play()
        return true
    }

    private func resolvedCurrentTimeLocked() -> TimeInterval {
        if let nodeTime = playerNode.lastRenderTime,
           let playerTime = playerNode.playerTime(forNodeTime: nodeTime) {
            let resolved = playbackStartOffset + (Double(playerTime.sampleTime) / playerTime.sampleRate)
            let clamped = min(max(resolved, 0), totalDurationLocked())
            lastKnownCurrentTime = clamped
            return clamped
        }

        return min(max(lastKnownCurrentTime, 0), totalDurationLocked())
    }

    private func handleMicrophoneBuffer(
        _ buffer: AVAudioPCMBuffer,
        format: AVAudioFormat,
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) {
        // Keep audio callback work minimal: copy and downmix quickly, then dispatch processing.
        let frameCount = Int(buffer.frameLength)
        guard frameCount > 0 else {
            return
        }
        guard let channelData = buffer.floatChannelData else {
            return
        }

        let channelCount = Int(format.channelCount)
        guard channelCount > 0 else {
            return
        }

        var monoSamples = [Float](repeating: 0, count: frameCount)
        if channelCount == 1 {
            for frameIndex in 0..<frameCount {
                monoSamples[frameIndex] = channelData[0][frameIndex]
            }
        } else {
            let inverseChannelCount = 1.0 / Float(channelCount)
            for frameIndex in 0..<frameCount {
                var sampleSum: Float = 0
                for channelIndex in 0..<channelCount {
                    sampleSum += channelData[channelIndex][frameIndex]
                }
                monoSamples[frameIndex] = sampleSum * inverseChannelCount
            }
        }

        let input = TempoInputBuffer(
            samples: monoSamples,
            sampleRate: format.sampleRate,
            channelCount: 1,
            isInterleaved: false
        )

        microphoneDispatchQueue.async {
            onBuffer(input)
        }
    }
}

private final class MacAudioCaptureDelegate: NSObject, AVCaptureAudioDataOutputSampleBufferDelegate {
    private let processingQueue: DispatchQueue
    private let onBuffer: @Sendable (TempoInputBuffer) -> Void

    init(
        processingQueue: DispatchQueue,
        onBuffer: @escaping @Sendable (TempoInputBuffer) -> Void
    ) {
        self.processingQueue = processingQueue
        self.onBuffer = onBuffer
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let tempoInputBuffer = Self.makeTempoInputBuffer(from: sampleBuffer) else {
            return
        }

        processingQueue.async {
            self.onBuffer(tempoInputBuffer)
        }
    }

    private static func makeTempoInputBuffer(from sampleBuffer: CMSampleBuffer) -> TempoInputBuffer? {
        guard let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer),
              let asbdPointer = CMAudioFormatDescriptionGetStreamBasicDescription(formatDescription) else {
            return nil
        }

        let asbd = asbdPointer.pointee
        guard asbd.mFormatID == kAudioFormatLinearPCM else {
            return nil
        }

        let channelCount = Int(asbd.mChannelsPerFrame)
        guard channelCount > 0, asbd.mSampleRate > 0 else {
            return nil
        }

        let isFloat = (asbd.mFormatFlags & kAudioFormatFlagIsFloat) != 0
        let isSignedInteger = (asbd.mFormatFlags & kAudioFormatFlagIsSignedInteger) != 0
        let isNonInterleaved = (asbd.mFormatFlags & kAudioFormatFlagIsNonInterleaved) != 0
        guard !isNonInterleaved else {
            return nil
        }

        guard let dataBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) else {
            return nil
        }

        var totalLength = 0
        var dataPointer: UnsafeMutablePointer<Int8>?
        let status = CMBlockBufferGetDataPointer(
            dataBuffer,
            atOffset: 0,
            lengthAtOffsetOut: nil,
            totalLengthOut: &totalLength,
            dataPointerOut: &dataPointer
        )
        guard status == kCMBlockBufferNoErr,
              let dataPointer,
              asbd.mBytesPerFrame > 0 else {
            return nil
        }

        let frameCount = totalLength / Int(asbd.mBytesPerFrame)
        guard frameCount > 0 else {
            return nil
        }

        let sampleCount = frameCount * channelCount
        let samples: [Float]

        if isFloat, asbd.mBitsPerChannel == 32 {
            samples = dataPointer.withMemoryRebound(to: Float.self, capacity: sampleCount) { ptr in
                Array(UnsafeBufferPointer(start: ptr, count: sampleCount))
            }
        } else if isSignedInteger, asbd.mBitsPerChannel == 16 {
            let scale = 1.0 / Float(Int16.max)
            samples = dataPointer.withMemoryRebound(to: Int16.self, capacity: sampleCount) { ptr in
                Array(UnsafeBufferPointer(start: ptr, count: sampleCount)).map { Float($0) * scale }
            }
        } else if isSignedInteger, asbd.mBitsPerChannel == 32 {
            let scale = 1.0 / Float(Int32.max)
            samples = dataPointer.withMemoryRebound(to: Int32.self, capacity: sampleCount) { ptr in
                Array(UnsafeBufferPointer(start: ptr, count: sampleCount)).map { Float($0) * scale }
            }
        } else {
            return nil
        }

        return TempoInputBuffer(
            samples: samples,
            sampleRate: asbd.mSampleRate,
            channelCount: channelCount,
            isInterleaved: true
        )
    }
}
