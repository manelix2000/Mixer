import AudioEngine
import AVFoundation
import DSP
import Foundation
import QuartzCore
import Waveform

@MainActor
public final class DeckViewModel: ObservableObject {
    public enum ScratchInteractionState: Equatable {
        case idle
        case touchDown
        case dragging
        case release
    }

    @Published public private(set) var bpmText: String
    @Published public var waveformText: String
    @Published public var platterText: String
    @Published public private(set) var volume: Double
    @Published public private(set) var pan: Double
    @Published public private(set) var selectedTrackURL: URL?
    @Published public private(set) var selectedTrackName: String?
    @Published public private(set) var playbackStatusText: String
    @Published public private(set) var playbackState: AudioPlaybackState
    @Published public private(set) var playbackTimeText: String
    @Published public private(set) var playbackProgress: Double
    @Published public private(set) var waveformZoom: Double
    @Published public private(set) var waveformData: [Float]
    @Published public private(set) var isWaveformLoading: Bool
    @Published public private(set) var isBPMLoading: Bool
    @Published public private(set) var bpmDetectionStatusText: String?
    @Published public private(set) var originalBPM: Double
    @Published public private(set) var targetBPM: Double
    @Published public private(set) var pitchSensitivityPercent: Int
    @Published public private(set) var externalBPMText: String
    @Published public private(set) var externalBPMStatusText: String
    @Published public private(set) var isExternalBPMLoading: Bool
    @Published public private(set) var isMicrophoneBPMDetectionActive: Bool
    @Published public private(set) var isPitchLockedToExternalBPM: Bool
    @Published public private(set) var platterRotationDegrees: Double
    @Published public private(set) var scratchInteractionState: ScratchInteractionState

    private let audioEngine: AudioEngineControlling
    private let waveformAnalyzer: WaveformAnalyzing
    private let microphoneBPMPipeline = MicrophoneBPMPipeline()
    private var playbackTimer: Timer?
    private var turntableTimer: Timer?
    private var waveformLoadTask: Task<Void, Never>?
    private var bpmLoadTask: Task<Void, Never>?
    private var isPlatterScrubbing = false
    private var wasPlayingBeforePlatterScrub = false
    private var scratchCurrentTime: TimeInterval = 0
    private var lastScratchCommitTimestamp: TimeInterval = 0
    private var lastCommittedScratchTime: TimeInterval = 0
    private var lastTurntableUpdateTimestamp: TimeInterval = 0
    private var lastScrubAngleTimestamp: TimeInterval = 0
    private var turntablePhysics = TurntablePhysics()

    public init(
        bpmText: String = "-- BPM",
        waveformText: String = "Waveform Placeholder",
        platterText: String = "Platter Placeholder",
        volume: Double = 0.8,
        pan: Double = 0.0,
        originalBPM: Double = 0,
        audioEngine: AudioEngineControlling = AudioEngineManager(),
        waveformAnalyzer: WaveformAnalyzing = WaveformAnalyzer()
    ) {
        let clampedOriginalBPM = min(max(originalBPM, Self.minBPM), Self.maxBPM)
        self.originalBPM = clampedOriginalBPM
        self.targetBPM = clampedOriginalBPM
        self.pitchSensitivityPercent = 16
        self.bpmText = bpmText
        self.waveformText = waveformText
        self.platterText = platterText
        self.audioEngine = audioEngine
        self.waveformAnalyzer = waveformAnalyzer
        self.audioEngine.setVolume(Float(volume))
        self.audioEngine.setPan(Float(pan))
        self.volume = Double(self.audioEngine.volume)
        self.pan = Double(self.audioEngine.pan)
        self.playbackState = .idle
        self.playbackStatusText = ""
        self.playbackTimeText = "00:00 / 00:00"
        self.playbackProgress = 0
        self.waveformZoom = 1.0
        self.waveformData = []
        self.isWaveformLoading = false
        self.isBPMLoading = false
        self.bpmDetectionStatusText = nil
        self.externalBPMText = "-- BPM"
        self.externalBPMStatusText = "Mic BPM stopped"
        self.isExternalBPMLoading = false
        self.isMicrophoneBPMDetectionActive = false
        self.isPitchLockedToExternalBPM = false
        self.platterRotationDegrees = 0
        self.scratchInteractionState = .idle
        self.microphoneBPMPipeline.setResultHandler { [weak self] result in
            self?.handleMicrophoneBPMResult(result)
        }
        applyTargetBPM()
        refreshBPMText()
        startTurntableTimer()
    }

    deinit {
        audioEngine.stopMicrophoneCapture()
        playbackTimer?.invalidate()
        turntableTimer?.invalidate()
        waveformLoadTask?.cancel()
        bpmLoadTask?.cancel()
    }

    public func selectTrack(url: URL) {
        let resolvedURL: URL
        do {
            resolvedURL = try importedTrackURL(from: url)
        } catch {
            playbackState = .idle
            playbackStatusText = "Failed to import selected track"
            return
        }

        stopPlaybackTimer()
        waveformLoadTask?.cancel()
        waveformLoadTask = nil
        bpmLoadTask?.cancel()
        bpmLoadTask = nil
        isWaveformLoading = false
        isBPMLoading = false
        bpmDetectionStatusText = nil
        isPlatterScrubbing = false
        wasPlayingBeforePlatterScrub = false
        scratchCurrentTime = 0
        lastScratchCommitTimestamp = 0
        lastCommittedScratchTime = 0
        lastTurntableUpdateTimestamp = 0
        lastScrubAngleTimestamp = 0
        scratchInteractionState = .idle
        turntablePhysics.reset()
        publishTurntableState()
        selectedTrackURL = resolvedURL
        selectedTrackName = url.lastPathComponent
        isPitchLockedToExternalBPM = false

        do {
            try audioEngine.loadFile(url: resolvedURL)
            playbackState = audioEngine.playbackState
            playbackStatusText = ""
            refreshPlaybackTimeText()
            loadWaveform(url: resolvedURL)
            detectBPM(url: resolvedURL)
        } catch {
            playbackState = .idle
            playbackStatusText = "Failed to load selected track"
            playbackTimeText = "00:00 / 00:00"
            playbackProgress = 0
            waveformData = []
            waveformText = "Waveform unavailable"
            isWaveformLoading = false
            isBPMLoading = false
            bpmDetectionStatusText = "BPM detection skipped: track failed to load."
        }
    }

    public func play() {
        guard selectedTrackURL != nil else {
            playbackStatusText = "Select a track first"
            return
        }

        do {
            try audioEngine.play()
            playbackState = audioEngine.playbackState
            playbackStatusText = "Playing"
            startPlaybackTimer()
        } catch {
            playbackStatusText = "Unable to start playback"
        }
    }

    public func pause() {
        guard selectedTrackURL != nil else {
            playbackStatusText = "Select a track first"
            return
        }

        audioEngine.pause()
        playbackState = audioEngine.playbackState
        playbackStatusText = "Paused"
        stopPlaybackTimer()
        refreshPlaybackTimeText()
    }

    public func stop() {
        guard selectedTrackURL != nil else {
            playbackStatusText = "Select a track first"
            return
        }

        audioEngine.pause()
        do {
            try audioEngine.seek(to: 0)
            playbackState = audioEngine.playbackState
            playbackStatusText = "Stopped"
            stopPlaybackTimer()
            refreshPlaybackTimeText()
        } catch {
            playbackStatusText = "Unable to stop playback"
        }
    }

    public var hasSelectedTrack: Bool {
        selectedTrackURL != nil
    }

    public var isPlaybackActive: Bool {
        playbackState == .playing
    }

    public var playbackRate: Double {
        Double(audioEngine.playbackRate)
    }

    public var panRoutingText: String {
        if pan < -0.1 {
            return "Left"
        }
        if pan > 0.1 {
            return "Right"
        }
        return "Center"
    }

    public func volumeUp() {
        let newValue = min(Float(volume) + 0.05, 1.0)
        audioEngine.setVolume(newValue)
        volume = Double(audioEngine.volume)
    }

    public func volumeDown() {
        let newValue = max(Float(volume) - 0.05, 0.0)
        audioEngine.setVolume(newValue)
        volume = Double(audioEngine.volume)
    }

    public func setVolume(_ value: Double) {
        let clamped = min(max(value, 0.0), 1.0)
        audioEngine.setVolume(Float(clamped))
        volume = Double(audioEngine.volume)
    }

    public func setPan(_ value: Double) {
        audioEngine.setPan(Float(value))
        pan = Double(audioEngine.pan)
    }

    public func incrementBPM() {
        guard !isPitchLockedToExternalBPM else {
            return
        }
        targetBPM = min(targetBPM + Self.bpmStep, Self.maxBPM)
        applyTargetBPM()
        refreshBPMText()
    }

    public func decrementBPM() {
        guard !isPitchLockedToExternalBPM else {
            return
        }
        targetBPM = max(targetBPM - Self.bpmStep, Self.minBPM)
        applyTargetBPM()
        refreshBPMText()
    }

    public func setTargetBPM(_ value: Double) {
        guard !isPitchLockedToExternalBPM else {
            return
        }
        targetBPM = min(max(value, Self.minBPM), Self.maxBPM)
        applyTargetBPM()
        refreshBPMText()
    }

    public func setPitchOffset(_ offset: Double) {
        guard !isPitchLockedToExternalBPM else {
            return
        }
        let maxOffset = pitchSensitivityFraction
        let clampedOffset = min(max(offset, -maxOffset), maxOffset)
        let safeOriginal = max(originalBPM, 1.0)
        targetBPM = safeOriginal * (1.0 + clampedOffset)
        applyTargetBPM()
        refreshBPMText()
    }

    public var pitchSensitivityFraction: Double {
        Double(pitchSensitivityPercent) / 100.0
    }

    public var canIncreasePitchSensitivity: Bool {
        guard let index = Self.allowedPitchSensitivityPercents.firstIndex(of: pitchSensitivityPercent) else {
            return false
        }
        return index < (Self.allowedPitchSensitivityPercents.count - 1)
    }

    public var canDecreasePitchSensitivity: Bool {
        guard let index = Self.allowedPitchSensitivityPercents.firstIndex(of: pitchSensitivityPercent) else {
            return false
        }
        return index > 0
    }

    public func increasePitchSensitivity() {
        guard let index = Self.allowedPitchSensitivityPercents.firstIndex(of: pitchSensitivityPercent),
              index < (Self.allowedPitchSensitivityPercents.count - 1) else {
            return
        }
        pitchSensitivityPercent = Self.allowedPitchSensitivityPercents[index + 1]
        clampPitchOffsetToSensitivityIfNeeded()
    }

    public func decreasePitchSensitivity() {
        guard let index = Self.allowedPitchSensitivityPercents.firstIndex(of: pitchSensitivityPercent),
              index > 0 else {
            return
        }
        pitchSensitivityPercent = Self.allowedPitchSensitivityPercents[index - 1]
        clampPitchOffsetToSensitivityIfNeeded()
    }

    public var canIncrementBPM: Bool {
        !isPitchLockedToExternalBPM && targetBPM < Self.maxBPM
    }

    public var canDecrementBPM: Bool {
        !isPitchLockedToExternalBPM && targetBPM > Self.minBPM
    }

    public var canLockPitchToExternalBPM: Bool {
        isMicrophoneBPMDetectionActive
    }

    public func togglePitchLockToExternalBPM() {
        if isPitchLockedToExternalBPM {
            isPitchLockedToExternalBPM = false
            return
        }

        guard canLockPitchToExternalBPM else {
            return
        }

        guard let externalBPM = latestExternalBPM else {
            externalBPMStatusText = "Listening... lock will apply once BPM is detected."
            return
        }

        isPitchLockedToExternalBPM = true
        let clampedBPM = min(max(externalBPM, Self.minBPM), Self.maxBPM)
        targetBPM = clampedBPM
        applyTargetBPM()
        refreshBPMText()
        stopMicrophoneBPMDetection()
    }

    public func zoomInWaveform() {
        setWaveformZoom(waveformZoom + 0.25)
    }

    public func zoomOutWaveform() {
        setWaveformZoom(waveformZoom - 0.25)
    }

    public func setWaveformZoom(_ value: Double) {
        waveformZoom = min(max(value, Self.minWaveformZoom), Self.maxWaveformZoom)
    }

    public var canZoomInWaveform: Bool {
        waveformZoom < Self.maxWaveformZoom
    }

    public var canZoomOutWaveform: Bool {
        waveformZoom > Self.minWaveformZoom
    }

    public var isTurntableScrubbing: Bool {
        isPlatterScrubbing
    }

    public func beginTurntableScrub() {
        guard hasSelectedTrack else {
            return
        }
        guard !isPlatterScrubbing else {
            return
        }

        isPlatterScrubbing = true
        wasPlayingBeforePlatterScrub = playbackState == .playing
        scratchInteractionState = .touchDown
        scratchCurrentTime = audioEngine.currentTime
        lastScratchCommitTimestamp = 0
        lastCommittedScratchTime = scratchCurrentTime
        stopPlaybackTimer()
        playbackStatusText = "Scratching"

        do {
            try audioEngine.beginScratch()
        } catch {
            playbackStatusText = "Scratch unavailable"
        }
        updatePlaybackDisplay(current: scratchCurrentTime, total: audioEngine.totalDuration)
    }

    public func updateTurntableScrub(angleDelta: Double) {
        guard isPlatterScrubbing else {
            beginTurntableScrub()
            return
        }

        scratchInteractionState = .dragging
        let rawTimeDelta = (angleDelta / (2.0 * .pi)) * Self.scrubSecondsPerRevolution
        let clampedDelta = min(max(rawTimeDelta, -Self.maxScrubStep), Self.maxScrubStep)
        let duration = audioEngine.totalDuration
        scratchCurrentTime = min(max(scratchCurrentTime + clampedDelta, 0), duration)

        let now = CACurrentMediaTime()
        let scrubDeltaTime: TimeInterval
        if lastScrubAngleTimestamp > 0 {
            scrubDeltaTime = now - lastScrubAngleTimestamp
        } else {
            scrubDeltaTime = 1.0 / 60.0
        }
        lastScrubAngleTimestamp = now
        turntablePhysics.applyAngularDrag(deltaAngle: angleDelta, deltaTime: scrubDeltaTime)
        publishTurntableState()
        updatePlaybackDisplay(current: scratchCurrentTime, total: duration)
        commitScratchAudio(force: false)
    }

    public func endTurntableScrub() {
        guard isPlatterScrubbing else {
            return
        }

        scratchInteractionState = .release
        commitScratchAudio(force: true)
        do {
            try audioEngine.endScratch(resumePlayback: wasPlayingBeforePlatterScrub)
        } catch {
            playbackStatusText = "Scratch release failed"
        }

        isPlatterScrubbing = false
        lastScrubAngleTimestamp = 0
        scratchInteractionState = .idle

        if wasPlayingBeforePlatterScrub {
            playbackState = audioEngine.playbackState
            playbackStatusText = "Playing"
            startPlaybackTimer()
        } else {
            audioEngine.pause()
            playbackState = audioEngine.playbackState
            playbackStatusText = "Paused"
            stopPlaybackTimer()
        }

        refreshPlaybackTimeText()
    }

    public func startMicrophoneBPMDetection() {
        guard !isMicrophoneBPMDetectionActive else {
            return
        }

        microphoneBPMPipeline.reset()
        isExternalBPMLoading = true
        externalBPMStatusText = "Checking microphone permission..."

        requestMicrophonePermissionAndStart()
    }

    public func stopMicrophoneBPMDetection() {
        guard isMicrophoneBPMDetectionActive || audioEngine.isMicrophoneCaptureRunning else {
            return
        }
        audioEngine.stopMicrophoneCapture()
        microphoneBPMPipeline.reset()
        isMicrophoneBPMDetectionActive = false
        isExternalBPMLoading = false
        externalBPMStatusText = "Mic BPM stopped"
    }

    private func startPlaybackTimer() {
        guard playbackTimer == nil else {
            return
        }

        // Keep text updates lightweight; waveform progress is updated per-frame.
        playbackTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor [weak self] in
                self?.refreshPlaybackTimeText()
            }
        }
        RunLoop.main.add(playbackTimer!, forMode: .common)
    }

    private func stopPlaybackTimer() {
        playbackTimer?.invalidate()
        playbackTimer = nil
    }

    private func startTurntableTimer() {
        guard turntableTimer == nil else {
            return
        }

        turntableTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor [weak self] in
                self?.updateTurntablePhysics()
            }
        }
        RunLoop.main.add(turntableTimer!, forMode: .common)
    }

    private func refreshPlaybackTimeText() {
        let current = audioEngine.currentTime
        let total = audioEngine.totalDuration
        updatePlaybackDisplay(current: current, total: total)
    }

    private func updatePlaybackDisplay(current: TimeInterval, total: TimeInterval) {
        let formattedTime = "\(format(time: current)) / \(format(time: total))"
        if playbackTimeText != formattedTime {
            playbackTimeText = formattedTime
        }
        if total > 0 {
            let normalizedProgress = min(max(current / total, 0), 1)
            if abs(playbackProgress - normalizedProgress) > 0.0005 {
                playbackProgress = normalizedProgress
            }
        } else {
            playbackProgress = 0
        }
    }

    private func commitScratchAudio(force: Bool) {
        guard hasSelectedTrack else {
            return
        }

        let now = CACurrentMediaTime()
        let elapsed = now - lastScratchCommitTimestamp
        let moved = abs(scratchCurrentTime - lastCommittedScratchTime)
        if !force {
            guard elapsed >= Self.minScratchCommitInterval || moved >= Self.minScratchCommitDelta else {
                return
            }
        }

        do {
            try audioEngine.scratch(to: scratchCurrentTime)
            playbackState = audioEngine.playbackState
            lastScratchCommitTimestamp = now
            lastCommittedScratchTime = scratchCurrentTime
        } catch {
            if !force {
                playbackStatusText = "Scrub unavailable"
            }
            return
        }
    }

    private func updateTurntablePhysics() {
        let now = CACurrentMediaTime()
        let deltaTime: TimeInterval
        if lastTurntableUpdateTimestamp > 0 {
            deltaTime = now - lastTurntableUpdateTimestamp
        } else {
            deltaTime = 1.0 / 60.0
        }
        lastTurntableUpdateTimestamp = now

        let shouldDrive = playbackState == .playing && !isPlatterScrubbing
        let targetAngularVelocity = shouldDrive ? Self.basePlatterAngularVelocity * playbackRate : nil
        turntablePhysics.step(deltaTime: deltaTime, driveAngularVelocity: targetAngularVelocity)
        if shouldDrive {
            refreshPlaybackProgressOnly()
        }
        publishTurntableState()
    }

    private func refreshPlaybackProgressOnly() {
        let current = audioEngine.currentTime
        let total = audioEngine.totalDuration
        guard total > 0 else {
            if playbackProgress != 0 {
                playbackProgress = 0
            }
            return
        }

        let normalizedProgress = min(max(current / total, 0), 1)
        if abs(playbackProgress - normalizedProgress) > 0.0001 {
            playbackProgress = normalizedProgress
        }
    }

    private func publishTurntableState() {
        let degrees = turntablePhysics.platterPosition * 180.0 / .pi
        if abs(platterRotationDegrees - degrees) > 0.01 {
            platterRotationDegrees = degrees
        }
    }

    private func loadWaveform(url: URL) {
        waveformLoadTask?.cancel()
        waveformLoadTask = nil
        isWaveformLoading = true
        waveformText = "Loading waveform..."
        waveformData = []
        let sampleCount = 1024

        waveformLoadTask = Task { [waveformAnalyzer] in
            let result = await Task.detached(priority: .userInitiated) {
                try waveformAnalyzer.generateWaveform(url: url, sampleCount: sampleCount)
            }.result

            guard !Task.isCancelled else {
                return
            }

            switch result {
            case let .success(waveform):
                waveformData = waveform
                waveformText = "Loaded!"
                isWaveformLoading = false
            case .failure:
                waveformData = []
                waveformText = "Waveform unavailable"
                isWaveformLoading = false
            }

            waveformLoadTask = nil
        }
    }

    private func importedTrackURL(from sourceURL: URL) throws -> URL {
        // Keep imported tracks inside app sandbox to avoid security-scoped URL lifetime issues.
        let fileManager = FileManager.default
        let importsDirectory = fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        )[0].appendingPathComponent("ImportedTracks", isDirectory: true)
        try fileManager.createDirectory(at: importsDirectory, withIntermediateDirectories: true)

        let destinationURL = importsDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(sourceURL.pathExtension)

        if sourceURL.isFileURL {
            let didAccess = sourceURL.startAccessingSecurityScopedResource()
            defer {
                if didAccess {
                    sourceURL.stopAccessingSecurityScopedResource()
                }
            }
        }

        var coordinationError: NSError?
        var copyError: Error?
        NSFileCoordinator().coordinate(readingItemAt: sourceURL, options: [], error: &coordinationError) { coordinatedURL in
            do {
                try fileManager.copyItem(at: coordinatedURL, to: destinationURL)
            } catch {
                copyError = error
            }
        }

        // Some File Provider picker sessions can disconnect the view service while still
        // leaving us with a readable URL. Prefer successful copy if destination exists.
        if fileManager.fileExists(atPath: destinationURL.path) {
            return destinationURL
        }

        if let copyError {
            // Fallback path: try direct copy from original URL while security scope is active.
            do {
                try fileManager.copyItem(at: sourceURL, to: destinationURL)
                return destinationURL
            } catch {
                throw copyError
            }
        }

        if let coordinationError {
            // Final fallback in case coordinator failed but source URL is still readable.
            do {
                try fileManager.copyItem(at: sourceURL, to: destinationURL)
                return destinationURL
            } catch {
                throw coordinationError
            }
        }
        return destinationURL
    }

    private func detectBPM(url: URL) {
        bpmLoadTask?.cancel()
        bpmLoadTask = nil
        isBPMLoading = true
        bpmDetectionStatusText = "Detecting BPM..."

        let configuration = TempoDetectorConfiguration()
        bpmLoadTask = Task {
            let result = await Task.detached(priority: .utility) { () throws -> BPMResult in
                let input = try Self.makeTempoInputBuffer(url: url)
                let detector = DSPModule.makeTempoDetector(configuration: configuration)
                return try detector.detectTempo(in: input)
            }.result

            guard !Task.isCancelled else {
                return
            }

            switch result {
            case let .success(bpmResult):
                switch bpmResult {
                case let .detected(bpm, confidence):
                    let clampedBPM = min(max(bpm, Self.minBPM), Self.maxBPM)
                    originalBPM = clampedBPM
                    targetBPM = clampedBPM
                    applyTargetBPM()
                    refreshBPMText()
                    bpmDetectionStatusText = String(
                        format: "Detected %.1f BPM (confidence %.2f)",
                        clampedBPM,
                        confidence
                    )
                case let .unavailable(reason):
                    refreshBPMText()
                    bpmDetectionStatusText = "BPM detection unavailable (\(reason)). Manual control active."
                }
            case .failure:
                refreshBPMText()
                bpmDetectionStatusText = "BPM detection failed. Manual control active."
            }

            isBPMLoading = false
            bpmLoadTask = nil
        }
    }

    nonisolated private static func makeTempoInputBuffer(url: URL) throws -> TempoInputBuffer {
        let file = try AVAudioFile(forReading: url)
        let format = file.processingFormat
        let maxFrames = AVAudioFramePosition(Self.maxBPMAnalysisFrames)
        let frameCountToRead = min(file.length, maxFrames)

        guard frameCountToRead > 0 else {
            throw TempoDetectionError.emptyInput
        }

        guard let buffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: AVAudioFrameCount(frameCountToRead)
        ) else {
            throw TempoDetectionError.nativeProcessingFailed
        }

        try file.read(into: buffer, frameCount: AVAudioFrameCount(frameCountToRead))

        let framesRead = Int(buffer.frameLength)
        guard framesRead > 0 else {
            throw TempoDetectionError.emptyInput
        }

        guard let channelData = buffer.floatChannelData else {
            throw TempoDetectionError.nativeProcessingFailed
        }

        let channelCount = Int(format.channelCount)
        guard channelCount > 0 else {
            throw TempoDetectionError.invalidChannelCount
        }

        var samples = [Float]()
        samples.reserveCapacity(framesRead * channelCount)
        for channel in 0..<channelCount {
            let channelSamples = UnsafeBufferPointer(start: channelData[channel], count: framesRead)
            samples.append(contentsOf: channelSamples)
        }

        return TempoInputBuffer(
            samples: samples,
            sampleRate: format.sampleRate,
            channelCount: channelCount,
            isInterleaved: false
        )
    }

    private func format(time: TimeInterval) -> String {
        let totalSeconds = max(0, Int(time.rounded(.down)))
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    private func applyTargetBPM() {
        let safeOriginal = max(originalBPM, Self.minBPM)
        let ratio = targetBPM / safeOriginal
        let clampedRatio = min(max(ratio, Self.minPlaybackRate), Self.maxPlaybackRate)
        audioEngine.setPlaybackRate(Float(clampedRatio))
    }

    private func refreshBPMText() {
        bpmText = String(
            format: "Original %.1f | Target %.1f | %.3fx",
            originalBPM,
            targetBPM,
            playbackRate
        )
    }

    private func handleMicrophoneBPMResult(_ result: BPMResult) {
        switch result {
        case let .detected(bpm, confidence):
            latestExternalBPM = bpm
            externalBPMText = String(format: "%.1f BPM", bpm)
            externalBPMStatusText = String(
                format: "Mic detected (confidence %.2f)",
                confidence
            )
            isExternalBPMLoading = false
        case .unavailable:
            externalBPMStatusText = "Listening... no stable tempo yet"
            if externalBPMText == "-- BPM" {
                isExternalBPMLoading = true
            }
        }
    }

    private func requestMicrophonePermissionAndStart() {
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted:
            startMicrophoneCapturePipeline()
        case .denied:
            isMicrophoneBPMDetectionActive = false
            isExternalBPMLoading = false
            externalBPMStatusText = "Mic BPM unavailable: microphone permission denied."
        case .undetermined:
            externalBPMStatusText = "Requesting microphone permission..."
            session.requestRecordPermission { [weak self] granted in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    guard granted else {
                        self.isMicrophoneBPMDetectionActive = false
                        self.isExternalBPMLoading = false
                        self.externalBPMStatusText = "Mic BPM unavailable: microphone permission denied."
                        return
                    }
                    self.startMicrophoneCapturePipeline()
                }
            }
        @unknown default:
            isMicrophoneBPMDetectionActive = false
            isExternalBPMLoading = false
            externalBPMStatusText = "Mic BPM unavailable: unknown microphone permission state."
        }
    }

    private func startMicrophoneCapturePipeline() {
        externalBPMStatusText = "Listening to microphone..."
        do {
            let pipeline = microphoneBPMPipeline
            try audioEngine.startMicrophoneCapture { input in
                pipeline.ingest(input)
            }
            isMicrophoneBPMDetectionActive = true
        } catch {
            isMicrophoneBPMDetectionActive = false
            isExternalBPMLoading = false
            if let localizedError = error as? LocalizedError,
               let description = localizedError.errorDescription {
                externalBPMStatusText = "Mic BPM unavailable: \(description)"
            } else {
                externalBPMStatusText = "Mic BPM unavailable: \(error.localizedDescription)"
            }
        }
    }

    public static let minBPM: Double = 60
    public static let maxBPM: Double = 200
    public static let bpmStep: Double = 0.5
    public static let minPlaybackRate: Double = 0.5
    public static let maxPlaybackRate: Double = 2.0
    nonisolated public static let maxBPMAnalysisFrames: AVAudioFrameCount = 44_100 * 45
    public static let minWaveformZoom: Double = 0.5
    public static let maxWaveformZoom: Double = 4.0
    public static let allowedPitchSensitivityPercents: [Int] = [2, 4, 8, 16]
    public static let scrubSecondsPerRevolution: Double = 1.8
    public static let maxScrubStep: TimeInterval = 0.25
    public static let minScratchCommitInterval: TimeInterval = 1.0 / 120.0
    public static let minScratchCommitDelta: TimeInterval = 0.004
    public static let basePlatterAngularVelocity: Double = (33.33 / 60.0) * (2.0 * .pi)

    private var latestExternalBPM: Double?

    private func clampPitchOffsetToSensitivityIfNeeded() {
        guard !isPitchLockedToExternalBPM else {
            return
        }
        let safeOriginal = max(originalBPM, 1.0)
        let offset = (targetBPM / safeOriginal) - 1.0
        let maxOffset = pitchSensitivityFraction
        let clampedOffset = min(max(offset, -maxOffset), maxOffset)
        targetBPM = safeOriginal * (1.0 + clampedOffset)
        applyTargetBPM()
        refreshBPMText()
    }
}

private final class MicrophoneBPMPipeline {
    private let queue = DispatchQueue(label: "dev.manelix.Mixer.DeckFeature.mic-bpm", qos: .utility)
    private let detector: any TempoDetecting
    private var onResult: (@MainActor (BPMResult) -> Void)?

    private var sampleRate: Double?
    private var samples: [Float] = []
    private var isAnalysisInFlight = false
    private var lastAnalysisTimestamp: TimeInterval = 0

    init() {
        self.detector = DSPModule.makeTempoDetector(configuration: .init())
    }

    func setResultHandler(_ handler: @escaping @MainActor (BPMResult) -> Void) {
        onResult = handler
    }

    func reset() {
        queue.async {
            self.sampleRate = nil
            self.samples.removeAll(keepingCapacity: true)
            self.isAnalysisInFlight = false
            self.lastAnalysisTimestamp = 0
        }
    }

    func ingest(_ input: TempoInputBuffer) {
        queue.async {
            self.process(input)
        }
    }

    private func process(_ input: TempoInputBuffer) {
        guard !input.samples.isEmpty, input.sampleRate > 0 else {
            return
        }

        if sampleRate == nil || abs((sampleRate ?? 0) - input.sampleRate) > 0.5 {
            sampleRate = input.sampleRate
            samples.removeAll(keepingCapacity: true)
            isAnalysisInFlight = false
            lastAnalysisTimestamp = 0
        }

        samples.append(contentsOf: input.samples)

        let rate = sampleRate ?? input.sampleRate
        let maxStoredSamples = Int(rate * Self.maxStoredSeconds)
        if samples.count > maxStoredSamples {
            samples.removeFirst(samples.count - maxStoredSamples)
        }

        let now = CACurrentMediaTime()
        guard !isAnalysisInFlight else {
            return
        }
        guard now - lastAnalysisTimestamp >= Self.analysisInterval else {
            return
        }

        let minimumRequired = Int(rate * Self.minimumAnalysisWindowSeconds)
        guard samples.count >= minimumRequired else {
            return
        }

        let analysisWindow = Int(rate * Self.maximumAnalysisWindowSeconds)
        let windowSamples = Array(samples.suffix(analysisWindow))
        isAnalysisInFlight = true
        lastAnalysisTimestamp = now

        let detector = self.detector
        let onResult = self.onResult
        DispatchQueue.global(qos: .utility).async {
            let result: BPMResult
            do {
                result = try detector.detectTempo(
                    in: TempoInputBuffer(
                        samples: windowSamples,
                        sampleRate: rate,
                        channelCount: 1,
                        isInterleaved: false
                    )
                )
            } catch {
                result = .unavailable(reason: "Mic detection failed")
            }

            self.queue.async {
                self.isAnalysisInFlight = false
            }

            guard let onResult else {
                return
            }

            Task { @MainActor in
                onResult(result)
            }
        }
    }

    private static let minimumAnalysisWindowSeconds: Double = 4
    private static let maximumAnalysisWindowSeconds: Double = 8
    private static let analysisInterval: TimeInterval = 1
    private static let maxStoredSeconds: Double = 12
}
