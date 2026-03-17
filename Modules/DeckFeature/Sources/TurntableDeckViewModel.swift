import AudioEngine
import AVFoundation
import DSP
import Foundation
import QuartzCore
import UIComponents
import UIKit
import Waveform
import os

@MainActor
public final class TurntableDeckViewModel: ObservableObject {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "TurntableDeckViewModel.PressureTouch"
    )
    private static let trackLog = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "TurntableDeckViewModel.TrackImport"
    )

    public enum ScratchInteractionState: Equatable {
        case idle
        case touchDown
        case dragging
        case release
    }

    private enum ScratchMotionMode {
        case scrub
        case scratch
    }

    @Published public private(set) var bpmText: String
    @Published public var waveformText: String
    @Published public var platterText: String
    @Published public private(set) var selectedTrackURL: URL?
    @Published public private(set) var selectedTrackName: String?
    @Published public private(set) var trackArtwork: UIImage?
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
    @Published public private(set) var isPitchLockedToExternalBPM: Bool
    @Published public private(set) var platterRotationDegrees: Double
    @Published public private(set) var scratchInteractionState: ScratchInteractionState
    @Published public private(set) var volume: Double
    @Published public private(set) var pan: Double
    @Published public private(set) var panControlRange: ClosedRange<Double>
    @Published public private(set) var splitDeckRole: SplitDeckRole?

    private let audioEngine: AudioEngineControlling
    private let waveformAnalyzer: WaveformAnalyzing
    private var playbackTimer: Timer?
    private var turntableTimer: Timer?
    private var waveformLoadTask: Task<Void, Never>?
    private var waveformLoadID = UUID()
    private var bpmLoadTask: Task<Void, Never>?
    private var artworkLoadTask: Task<Void, Never>?
    private var stoppedStatusClearTask: Task<Void, Never>?
    private var isPlatterScrubbing = false
    private var wasPlayingBeforePlatterScrub = false
    private var scratchCurrentTime: TimeInterval = 0
    private var lastScratchCommitTimestamp: TimeInterval = 0
    private var lastCommittedScratchTime: TimeInterval = 0
    private var lastTurntableUpdateTimestamp: TimeInterval = 0
    private var lastScrubAngleTimestamp: TimeInterval = 0
    private var smoothedScratchAngularVelocity: Double = 0
    private var latestScratchAngularVelocity: Double = 0
    private var latestScratchDirection: Double = 1
    private var scratchMotionMode: ScratchMotionMode = .scrub
    private var pressureTouchStartTargetBPM: Double?
    private var pressureTouchIntensity: Double = 0
    private var pressureTouchDirection: Double = -1
    private var lastPressureDebugLogTimestamp: TimeInterval = 0
    private var lastLoggedPressureIntensity: Double = -1
    private var turntablePhysics = TurntablePhysics()
    private var lastWrappedPlatterDegrees: Double?
    private var masterVolume: Double = 1.0

    public init(
        bpmText: String = "-- BPM",
        waveformText: String = "Waveform Placeholder",
        platterText: String = "Platter Placeholder",
        originalBPM: Double = 0,
        audioEngine: AudioEngineControlling = AudioEngineManager(),
        waveformAnalyzer: WaveformAnalyzing = WaveformAnalyzer()
    ) {
        let clampedOriginalBPM = min(max(originalBPM, Self.minBPM), Self.maxBPM)
        self.originalBPM = clampedOriginalBPM
        self.targetBPM = clampedOriginalBPM
        self.pitchSensitivityPercent = 8
        self.bpmText = bpmText
        self.waveformText = waveformText
        self.platterText = platterText
        self.audioEngine = audioEngine
        self.waveformAnalyzer = waveformAnalyzer
        self.playbackState = .idle
        self.playbackStatusText = ""
        self.playbackTimeText = "00:00 / 00:00"
        self.playbackProgress = 0
        self.trackArtwork = nil
        self.waveformZoom = 1.0
        self.waveformData = []
        self.isWaveformLoading = false
        self.isBPMLoading = false
        self.bpmDetectionStatusText = nil
        self.isPitchLockedToExternalBPM = false
        self.platterRotationDegrees = 0
        self.scratchInteractionState = .idle
        self.volume = Double(min(max(audioEngine.volume, 0), 1))
        let resolvedPanRange: ClosedRange<Double>
        let resolvedSplitRole: SplitDeckRole?
        if let routing = audioEngine as? AudioEngineRoutingProviding {
            resolvedPanRange = routing.panControlRange
            resolvedSplitRole = routing.splitDeckRole
        } else {
            resolvedPanRange = -1.0...1.0
            resolvedSplitRole = nil
        }
        self.panControlRange = resolvedPanRange
        self.splitDeckRole = resolvedSplitRole
        self.pan = min(max(Double(audioEngine.pan), resolvedPanRange.lowerBound), resolvedPanRange.upperBound)
        self.masterVolume = 1.0

        applyEffectiveOutputVolume()
        applyTargetBPM()
        refreshBPMText()
        startTurntableTimer()
    }

    deinit {
        playbackTimer?.invalidate()
        turntableTimer?.invalidate()
        waveformLoadTask?.cancel()
        bpmLoadTask?.cancel()
        artworkLoadTask?.cancel()
        stoppedStatusClearTask?.cancel()
    }

    public func selectTrack(url: URL) {
        print("[Mixer][Import] selectTrack called with URL: \(url)")
        Self.trackLog.info("selectTrack called with URL: \(url.path(percentEncoded: false), privacy: .public)")
        playbackStatusText = "Importing..."
        selectedTrackName = url.lastPathComponent

        let resolvedURL: URL
        do {
            resolvedURL = try importedTrackURL(from: url)
            print("[Mixer][Import] imported URL: \(resolvedURL)")
            Self.trackLog.info("Resolved imported URL: \(resolvedURL.path(percentEncoded: false), privacy: .public)")
        } catch {
            print("[Mixer][Import] import failed: \(error.localizedDescription)")
            Self.trackLog.error("Track import failed: \(error.localizedDescription, privacy: .public)")
            playbackState = .idle
            playbackStatusText = "Failed to import track (\(error.localizedDescription))"
            return
        }

        stopPlaybackTimer()
        waveformLoadTask?.cancel()
        waveformLoadTask = nil
        bpmLoadTask?.cancel()
        bpmLoadTask = nil
        artworkLoadTask?.cancel()
        artworkLoadTask = nil
        isWaveformLoading = false
        isBPMLoading = false
        trackArtwork = nil
        bpmDetectionStatusText = nil
        isPlatterScrubbing = false
        wasPlayingBeforePlatterScrub = false
        scratchCurrentTime = 0
        lastScratchCommitTimestamp = 0
        lastCommittedScratchTime = 0
        lastTurntableUpdateTimestamp = 0
        lastScrubAngleTimestamp = 0
        pressureTouchStartTargetBPM = nil
        pressureTouchIntensity = 0
        pressureTouchDirection = -1
        lastPressureDebugLogTimestamp = 0
        lastLoggedPressureIntensity = -1
        scratchInteractionState = .idle
        turntablePhysics.reset()
        lastWrappedPlatterDegrees = nil
        publishTurntableState()
        selectedTrackURL = resolvedURL
        selectedTrackName = url.lastPathComponent
        loadTrackArtwork(url: resolvedURL)

        do {
            try audioEngine.loadFile(url: resolvedURL)
            print("[Mixer][Import] audioEngine.loadFile succeeded")
            Self.trackLog.info("audioEngine.loadFile succeeded.")
            playbackState = audioEngine.playbackState
            playbackStatusText = ""
            refreshPlaybackTimeText()
            loadWaveform(url: resolvedURL)
            detectBPM(url: resolvedURL)
        } catch {
            print("[Mixer][Import] audioEngine.loadFile failed: \(error.localizedDescription)")
            Self.trackLog.error("audioEngine.loadFile failed: \(error.localizedDescription, privacy: .public)")
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

    private func loadTrackArtwork(url: URL) {
        artworkLoadTask?.cancel()
        artworkLoadTask = Task { [weak self] in
            let artworkData = await Task.detached(priority: .utility) {
                await Self.extractArtworkData(from: url)
            }.value

            guard let self else { return }
            guard !Task.isCancelled else { return }
            if let artworkData, let image = UIImage(data: artworkData) {
                self.trackArtwork = image
            } else {
                self.trackArtwork = nil
            }
            self.artworkLoadTask = nil
        }
    }

    nonisolated private static func extractArtworkData(from url: URL) async -> Data? {
        let asset = AVURLAsset(url: url)
        guard let metadata = try? await asset.load(.commonMetadata) else {
            return nil
        }

        for item in metadata {
            if item.commonKey == .commonKeyArtwork {
                if let data = item.dataValue {
                    return data
                }
                if let value = item.value as? Data {
                    return value
                }
                if let value = item.value as? NSDictionary,
                   let data = value["data"] as? Data {
                    return data
                }
            }
        }
        return nil
    }

    public func play() {
        guard selectedTrackURL != nil else {
            playbackStatusText = "Select a track first"
            return
        }

        do {
            try audioEngine.play()
            playbackState = audioEngine.playbackState
            playbackStatusText = ""
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
        playbackStatusText = ""
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
            scheduleStoppedStatusAutoClear()
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

    public func setVolume(_ value: Double) {
        let clamped = min(max(value, 0.0), 1.0)
        volume = clamped
        applyEffectiveOutputVolume()
    }

    public func setMasterVolume(_ value: Double) {
        masterVolume = min(max(value, 0.0), 1.0)
        applyEffectiveOutputVolume()
    }

    public func setPan(_ value: Double) {
        let clamped = min(max(value, panControlRange.lowerBound), panControlRange.upperBound)
        audioEngine.setPan(Float(clamped))
        pan = min(max(Double(audioEngine.pan), panControlRange.lowerBound), panControlRange.upperBound)
    }

    public func refreshPanRouting(resetPanToCenter: Bool) {
        let resolvedPanRange: ClosedRange<Double>
        let resolvedSplitRole: SplitDeckRole?
        if let routing = audioEngine as? AudioEngineRoutingProviding {
            resolvedPanRange = routing.panControlRange
            resolvedSplitRole = routing.splitDeckRole
        } else {
            resolvedPanRange = -1.0...1.0
            resolvedSplitRole = nil
        }

        panControlRange = resolvedPanRange
        splitDeckRole = resolvedSplitRole

        if resetPanToCenter {
            let center = min(max(0.0, resolvedPanRange.lowerBound), resolvedPanRange.upperBound)
            setPan(center)
        } else {
            pan = min(max(Double(audioEngine.pan), resolvedPanRange.lowerBound), resolvedPanRange.upperBound)
        }
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

    public func lockPitch(to externalBPM: Double) {
        isPitchLockedToExternalBPM = true
        let clampedBPM = min(max(externalBPM, Self.minBPM), Self.maxBPM)
        targetBPM = clampedBPM
        applyTargetBPM()
        refreshBPMText()
    }

    public func unlockPitch() {
        guard isPitchLockedToExternalBPM else {
            return
        }
        isPitchLockedToExternalBPM = false
        clampPitchOffsetToSensitivityIfNeeded()
    }

    public func setWaveformZoom(_ value: Double) {
        waveformZoom = min(max(value, Self.minWaveformZoom), Self.maxWaveformZoom)
    }

    public func zoomInWaveform() {
        setWaveformZoom(waveformZoom + 0.25)
    }

    public func zoomOutWaveform() {
        setWaveformZoom(waveformZoom - 0.25)
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

    public var isPressureTouchActive: Bool {
        pressureTouchStartTargetBPM != nil
    }

    public var displayedTargetBPM: Double {
        effectiveTargetBPMForCurrentState()
    }

    public var tonearmRotationDegrees: Double {
        let clampedProgress = min(max(playbackProgress, 0), 1)
        return Self.tonearmStartRotationDegrees +
            (Self.tonearmEndRotationDegrees - Self.tonearmStartRotationDegrees) * clampedProgress
    }

    public func seekFromWaveformTap(xOffset: Double, baseSampleSpacing: Double) {
        guard hasSelectedTrack else {
            return
        }
        guard !waveformData.isEmpty else {
            return
        }

        let sampleSpacing = max(baseSampleSpacing * waveformZoom, 0.001)
        let deltaSamples = xOffset / sampleSpacing
        let denominator = Double(max(waveformData.count - 1, 1))
        let targetProgress = min(max(playbackProgress + (deltaSamples / denominator), 0), 1)
        let targetTime = targetProgress * audioEngine.totalDuration

        do {
            try audioEngine.seek(to: targetTime)
            playbackState = audioEngine.playbackState
            if playbackState == .playing {
                clearPlaybackStatusIfTransient()
                startPlaybackTimer()
            } else {
                stopPlaybackTimer()
            }
            refreshPlaybackTimeText()
        } catch {
            playbackStatusText = "Seek unavailable"
        }
    }

    public func beginTurntableScrub() {
        guard hasSelectedTrack else {
            return
        }
        guard !isPlatterScrubbing else {
            return
        }

        // Scratching and pressure-brake are mutually exclusive.
        if pressureTouchStartTargetBPM != nil {
            endTurntablePressureTouch()
        }

        isPlatterScrubbing = true
        wasPlayingBeforePlatterScrub = playbackState == .playing
        scratchInteractionState = .touchDown
        scratchCurrentTime = audioEngine.currentTime
        lastScratchCommitTimestamp = 0
        lastCommittedScratchTime = scratchCurrentTime
        smoothedScratchAngularVelocity = 0
        latestScratchAngularVelocity = 0
        latestScratchDirection = 1
        scratchMotionMode = .scrub
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
        let now = CACurrentMediaTime()
        let scrubDeltaTime: TimeInterval
        if lastScrubAngleTimestamp > 0 {
            scrubDeltaTime = now - lastScrubAngleTimestamp
        } else {
            scrubDeltaTime = 1.0 / 60.0
        }
        lastScrubAngleTimestamp = now

        let safeDeltaTime = max(scrubDeltaTime, 0.001)
        let instantaneousAngularVelocity = angleDelta / safeDeltaTime
        latestScratchAngularVelocity = instantaneousAngularVelocity
        if abs(angleDelta) >= Self.scratchDirectionAngleThreshold {
            latestScratchDirection = angleDelta >= 0 ? 1 : -1
        }
        smoothedScratchAngularVelocity +=
            (instantaneousAngularVelocity - smoothedScratchAngularVelocity) * Self.scratchVelocitySmoothing

        let isScratch = abs(smoothedScratchAngularVelocity) >= Self.scratchAngularVelocityThreshold
        scratchMotionMode = isScratch ? .scratch : .scrub

        let isJitter =
            abs(angleDelta) < Self.scratchJitterAngleThreshold &&
            abs(smoothedScratchAngularVelocity) < Self.scratchJitterVelocityThreshold
        if isJitter {
            return
        }

        let secondsPerRadian = isScratch ? Self.scratchSecondsPerRadian : Self.scrubSecondsPerRadian
        let rawTimeDelta = angleDelta * secondsPerRadian
        let maxStep = isScratch ? Self.maxScratchModeStep : Self.maxScrubModeStep
        let clampedDelta = min(max(rawTimeDelta, -maxStep), maxStep)
        let duration = audioEngine.totalDuration
        scratchCurrentTime = min(max(scratchCurrentTime + clampedDelta, 0), duration)

        turntablePhysics.applyAngularDrag(deltaAngle: angleDelta, deltaTime: safeDeltaTime)
        publishTurntableState()
        updatePlaybackDisplay(current: scratchCurrentTime, total: duration)
        commitScratchAudio(force: false, mode: scratchMotionMode)
    }

    public func endTurntableScrub() {
        guard isPlatterScrubbing else {
            return
        }

        scratchInteractionState = .release
        commitScratchAudio(force: true)
        let totalDuration = audioEngine.totalDuration
        let isAtTrackEnd = hasReachedTrackEnd(current: scratchCurrentTime, total: totalDuration)
        let shouldResumePlaybackAfterScratch = wasPlayingBeforePlatterScrub && !isAtTrackEnd

        do {
            try audioEngine.endScratch(resumePlayback: shouldResumePlaybackAfterScratch)
        } catch {
            playbackStatusText = "Scratch release failed"
        }

        isPlatterScrubbing = false
        lastScrubAngleTimestamp = 0
        smoothedScratchAngularVelocity = 0
        latestScratchAngularVelocity = 0
        latestScratchDirection = 1
        scratchMotionMode = .scrub
        scratchInteractionState = .idle

        if shouldResumePlaybackAfterScratch {
            playbackState = audioEngine.playbackState
            clearPlaybackStatusIfTransient()
            startPlaybackTimer()
        } else {
            if isAtTrackEnd {
                stopPlaybackAtTrackEnd(total: totalDuration)
            } else {
                audioEngine.pause()
                playbackState = audioEngine.playbackState
                clearPlaybackStatusIfTransient()
                stopPlaybackTimer()
            }
        }

        refreshPlaybackTimeText()
    }

    public func beginTurntablePressureTouch(pressure: Double, direction: Double) {
        guard hasSelectedTrack else {
            return
        }
        guard !isPlatterScrubbing else {
            return
        }

        if pressureTouchStartTargetBPM == nil {
            pressureTouchStartTargetBPM = targetBPM
        }

        pressureTouchIntensity = min(max(pressure, 0), 1)
        pressureTouchDirection = direction >= 0 ? 1 : -1
        lastPressureDebugLogTimestamp = CACurrentMediaTime()
        lastLoggedPressureIntensity = pressureTouchIntensity
        applyTargetBPM()
        refreshBPMText()
        Self.log.info(
            "Pressure began | pressure=\(self.pressureTouchIntensity, format: .fixed(precision: 3)) direction=\(self.pressureTouchDirection, format: .fixed(precision: 1)) startBPM=\(self.pressureTouchStartTargetBPM ?? 0, format: .fixed(precision: 2)) rate=\(self.playbackRate, format: .fixed(precision: 3))"
        )
    }

    public func updateTurntablePressureTouch(pressure: Double, direction: Double) {
        guard !isPlatterScrubbing else {
            return
        }
        guard pressureTouchStartTargetBPM != nil else {
            beginTurntablePressureTouch(pressure: pressure, direction: direction)
            return
        }

        pressureTouchIntensity = min(max(pressure, 0), 1)
        pressureTouchDirection = direction >= 0 ? 1 : -1
        applyTargetBPM()
        refreshBPMText()
        let now = CACurrentMediaTime()
        let elapsed = now - lastPressureDebugLogTimestamp
        if abs(pressureTouchIntensity - lastLoggedPressureIntensity) >= 0.05 || elapsed >= 0.4 {
            lastPressureDebugLogTimestamp = now
            lastLoggedPressureIntensity = pressureTouchIntensity
            Self.log.info(
                "Pressure moved | pressure=\(self.pressureTouchIntensity, format: .fixed(precision: 3)) direction=\(self.pressureTouchDirection, format: .fixed(precision: 1)) rate=\(self.playbackRate, format: .fixed(precision: 3))"
            )
        }
    }

    public func endTurntablePressureTouch() {
        guard let startBPM = pressureTouchStartTargetBPM else {
            return
        }

        pressureTouchStartTargetBPM = nil
        pressureTouchIntensity = 0
        pressureTouchDirection = -1
        lastPressureDebugLogTimestamp = 0
        lastLoggedPressureIntensity = -1
        targetBPM = min(max(startBPM, Self.minBPM), Self.maxBPM)
        applyTargetBPM()
        refreshBPMText()
        Self.log.info(
            "Pressure ended | restoredBPM=\(self.targetBPM, format: .fixed(precision: 2)) rate=\(self.playbackRate, format: .fixed(precision: 3))"
        )
    }

    private func startPlaybackTimer() {
        guard playbackTimer == nil else {
            return
        }

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
        if playbackState == .playing, hasReachedTrackEnd(current: current, total: total) {
            stopPlaybackAtTrackEnd(total: total)
            return
        }

        let formattedTime = "\(format(time: current)) / \(format(time: total))"
        if playbackTimeText != formattedTime {
            playbackTimeText = formattedTime
        }
        if total > 0 {
            let normalizedProgress = min(max(current / total, 0), 1)
            let progressEpsilon = isPlatterScrubbing ? Self.scratchProgressEpsilon : Self.normalProgressEpsilon
            if abs(playbackProgress - normalizedProgress) > progressEpsilon {
                playbackProgress = normalizedProgress
            }
        } else {
            playbackProgress = 0
        }
    }

    private func commitScratchAudio(force: Bool, mode: ScratchMotionMode = .scrub) {
        guard hasSelectedTrack else {
            return
        }

        let now = CACurrentMediaTime()
        let elapsed = now - lastScratchCommitTimestamp
        let moved = abs(scratchCurrentTime - lastCommittedScratchTime)
        let minInterval: TimeInterval
        let minDelta: TimeInterval
        switch mode {
        case .scrub:
            minInterval = Self.minScrubCommitInterval
            minDelta = Self.minScrubCommitDelta
        case .scratch:
            minInterval = Self.minScratchCommitInterval
            minDelta = Self.minScratchCommitDelta
        }
        if !force {
            guard elapsed >= minInterval || moved >= minDelta else {
                return
            }
        }

        do {
            let signedVelocity = max(abs(latestScratchAngularVelocity), 0.001) * latestScratchDirection
            try audioEngine.scratch(
                to: scratchCurrentTime,
                angularVelocity: signedVelocity
            )
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
        if playbackState == .playing, hasReachedTrackEnd(current: current, total: total) {
            stopPlaybackAtTrackEnd(total: total)
            return
        }
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
        let wrappedDegrees = turntablePhysics.platterPosition * 180.0 / .pi
        guard let lastWrappedPlatterDegrees else {
            self.lastWrappedPlatterDegrees = wrappedDegrees
            if abs(platterRotationDegrees - wrappedDegrees) > 0.01 {
                platterRotationDegrees = wrappedDegrees
            }
            return
        }

        var delta = wrappedDegrees - lastWrappedPlatterDegrees
        if delta > 180 {
            delta -= 360
        } else if delta < -180 {
            delta += 360
        }

        self.lastWrappedPlatterDegrees = wrappedDegrees
        let unwrappedDegrees = platterRotationDegrees + delta
        if abs(delta) > 0.01 {
            platterRotationDegrees = unwrappedDegrees
        }
    }

    private func hasReachedTrackEnd(current: TimeInterval, total: TimeInterval) -> Bool {
        guard total > 0 else {
            return false
        }
        return current >= (total - Self.trackEndTolerance)
    }

    private func stopPlaybackAtTrackEnd(total: TimeInterval) {
        audioEngine.pause()
        do {
            try audioEngine.seek(to: total)
        } catch {
            // Keep paused state even if precise end seek fails.
        }

        playbackState = audioEngine.playbackState
        playbackStatusText = "Stopped"
        scheduleStoppedStatusAutoClear()
        stopPlaybackTimer()
        playbackProgress = total > 0 ? 1.0 : 0
        playbackTimeText = "\(format(time: total)) / \(format(time: total))"
    }

    private func clearPlaybackStatusIfTransient() {
        guard playbackStatusText != "Stopped" else {
            return
        }
        playbackStatusText = ""
    }

    private func scheduleStoppedStatusAutoClear() {
        stoppedStatusClearTask?.cancel()
        stoppedStatusClearTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard let self else { return }
            guard !Task.isCancelled else { return }
            guard self.playbackStatusText == "Stopped" else { return }
            self.playbackStatusText = ""
            self.stoppedStatusClearTask = nil
        }
    }

    private func loadWaveform(url: URL) {
        waveformLoadTask?.cancel()
        waveformLoadTask = nil
        let loadID = UUID()
        waveformLoadID = loadID
        isWaveformLoading = true
        waveformText = "Loading waveform..."
        let sampleCount = 4096

        waveformLoadTask = Task { [waveformAnalyzer] in
            let result = await Task.detached(priority: .userInitiated) {
                try waveformAnalyzer.generateWaveform(
                    url: url,
                    sampleCount: sampleCount
                ) { progress in
                    Task { @MainActor [progress] in
                        guard self.waveformLoadID == loadID else {
                            return
                        }
                        self.waveformData = progress.samples
                        self.waveformText = "Loading waveform \(Int(progress.fraction * 100))%"
                    }
                }
            }.result

            guard !Task.isCancelled else {
                return
            }
            guard self.waveformLoadID == loadID else {
                return
            }

            switch result {
            case let .success(waveform):
                waveformData = waveform
                waveformText = "Loaded!"
                isWaveformLoading = false
            case .failure:
                waveformText = "Waveform unavailable"
                isWaveformLoading = false
            }

            waveformLoadTask = nil
        }
    }

    private func importedTrackURL(from sourceURL: URL) throws -> URL {
        print("[Mixer][Import] importing source URL: \(sourceURL)")
        Self.trackLog.info("Importing track from source: \(sourceURL.path(percentEncoded: false), privacy: .public)")
        let fileManager = FileManager.default
        let importsDirectory = fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        )[0].appendingPathComponent("ImportedTracks", isDirectory: true)
        try fileManager.createDirectory(at: importsDirectory, withIntermediateDirectories: true)
        Self.trackLog.info("Import destination directory: \(importsDirectory.path(percentEncoded: false), privacy: .public)")

        let destinationURL = importsDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(sourceURL.pathExtension)

        let didAccessSecurityScopedResource = sourceURL.isFileURL && sourceURL.startAccessingSecurityScopedResource()
        print("[Mixer][Import] security scope started: \(didAccessSecurityScopedResource)")
        Self.trackLog.info("Security-scoped access started: \(didAccessSecurityScopedResource, privacy: .public)")
        defer {
            if didAccessSecurityScopedResource {
                sourceURL.stopAccessingSecurityScopedResource()
                print("[Mixer][Import] security scope stopped")
                Self.trackLog.info("Security-scoped access stopped.")
            }
        }

        var coordinationError: NSError?
        var copyError: Error?
        NSFileCoordinator().coordinate(readingItemAt: sourceURL, options: [], error: &coordinationError) { coordinatedURL in
            do {
                try fileManager.copyItem(at: coordinatedURL, to: destinationURL)
                print("[Mixer][Import] coordinated copy succeeded -> \(destinationURL)")
                Self.trackLog.info(
                    "Coordinated copy succeeded: \(destinationURL.path(percentEncoded: false), privacy: .public)"
                )
            } catch {
                print("[Mixer][Import] coordinated copy failed: \(error.localizedDescription)")
                Self.trackLog.error("Coordinated copy failed: \(error.localizedDescription, privacy: .public)")
                copyError = error
            }
        }

        if fileManager.fileExists(atPath: destinationURL.path) {
            print("[Mixer][Import] destination exists after coordinated copy")
            Self.trackLog.info("Imported file exists after coordinated copy.")
            return destinationURL
        }

        if let copyError {
            do {
                try fileManager.copyItem(at: sourceURL, to: destinationURL)
                print("[Mixer][Import] fallback direct copy succeeded after copy error")
                Self.trackLog.info("Fallback direct copy succeeded after coordinated copy error.")
                return destinationURL
            } catch {
                print("[Mixer][Import] fallback direct copy failed: \(error.localizedDescription)")
                Self.trackLog.error("Fallback direct copy failed: \(error.localizedDescription, privacy: .public)")
                throw copyError
            }
        }

        if let coordinationError {
            do {
                try fileManager.copyItem(at: sourceURL, to: destinationURL)
                print("[Mixer][Import] fallback direct copy succeeded after coordination error")
                Self.trackLog.info("Fallback direct copy succeeded after coordination error.")
                return destinationURL
            } catch {
                print("[Mixer][Import] fallback direct copy failed: \(error.localizedDescription)")
                Self.trackLog.error("Fallback direct copy failed: \(error.localizedDescription, privacy: .public)")
                throw coordinationError
            }
        }

        print("[Mixer][Import] import failed with unknown file read error")
        Self.trackLog.error("Import failed with unknown file read error.")
        throw NSError(
            domain: NSCocoaErrorDomain,
            code: NSFileReadUnknownError,
            userInfo: [
                NSURLErrorKey: sourceURL
            ]
        )
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
                        format: "Detected %.1f BPM (acc. %.2f)",
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
        let effectiveTargetBPM = effectiveTargetBPMForCurrentState()
        let ratio = effectiveTargetBPM / safeOriginal
        let clampedRatio = min(max(ratio, Self.minPlaybackRate), Self.maxPlaybackRate)
        audioEngine.setPlaybackRate(Float(clampedRatio))
    }

    private func applyEffectiveOutputVolume() {
        let combined = min(max(volume * masterVolume, 0.0), 1.0)
        audioEngine.setVolume(Float(combined))
    }

    private func effectiveTargetBPMForCurrentState() -> Double {
        guard let pressureStartBPM = pressureTouchStartTargetBPM else {
            return targetBPM
        }

        let normalizedPressure = min(max(pressureTouchIntensity, 0), 1)
        let pressureCurve = pow(normalizedPressure, Self.pressureCurveExponent)

        if pressureTouchDirection < 0 {
            let slowdown = pressureCurve * Self.maxPressureSlowdownFraction
            let multiplier = max(1.0 - slowdown, Self.minPressureSlowdownMultiplier)
            return pressureStartBPM * multiplier
        }

        let acceleration = pressureCurve * Self.maxPressureAccelerationFraction
        let multiplier = min(1.0 + acceleration, Self.maxPressureAccelerationMultiplier)
        return pressureStartBPM * multiplier
    }

    private func refreshBPMText() {
        bpmText = String(
            format: "BPM %.1f | %.3fx",
            targetBPM,
            playbackRate
        )
    }

    public static let minBPM: Double = 60
    public static let maxBPM: Double = 200
    public static let bpmStep: Double = 0.5
    public static let minPlaybackRate: Double = 0.5
    public static let maxPlaybackRate: Double = 2.0
    nonisolated public static let maxBPMAnalysisFrames: AVAudioFrameCount = 44_100 * 45
    public static let minWaveformZoom: Double = WaveformView.minZoom
    public static let maxWaveformZoom: Double = WaveformView.maxZoom
    public static let allowedPitchSensitivityPercents: [Int] = [2, 4, 8, 16]
    public static let scrubSecondsPerRevolution: Double = 1.8
    public static let scrubSecondsPerRadian: Double = scrubSecondsPerRevolution / (2.0 * .pi)
    public static let scratchSecondsPerRadian: Double = 0.20
    public static let maxScrubModeStep: TimeInterval = 0.10
    public static let maxScratchModeStep: TimeInterval = 0.20
    public static let scratchAngularVelocityThreshold: Double = 3.0
    public static let scratchVelocitySmoothing: Double = 0.35
    public static let scratchDirectionAngleThreshold: Double = 0.0025
    public static let scratchJitterAngleThreshold: Double = 0.002
    public static let scratchJitterVelocityThreshold: Double = 0.6
    public static let minScrubCommitInterval: TimeInterval = 1.0 / 240.0
    public static let minScratchCommitInterval: TimeInterval = 1.0 / 180.0
    public static let minScrubCommitDelta: TimeInterval = 0.001
    public static let minScratchCommitDelta: TimeInterval = 0.003
    public static let normalProgressEpsilon: Double = 0.0005
    public static let scratchProgressEpsilon: Double = 0.00002
    public static let basePlatterAngularVelocity: Double = (33.33 / 60.0) * (2.0 * .pi)
    public static let tonearmStartRotationDegrees: Double = 0
    public static let tonearmEndRotationDegrees: Double = 27
    public static let trackEndTolerance: TimeInterval = 0.01
    public static let maxPressureSlowdownFraction: Double = 0.9
    public static let minPressureSlowdownMultiplier: Double = 0.08
    public static let maxPressureAccelerationFraction: Double = 0.9
    public static let maxPressureAccelerationMultiplier: Double = 1.92
    public static let pressureCurveExponent: Double = 1.6

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
