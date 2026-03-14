import AudioEngine
import AVFoundation
import DSP
import Foundation
import QuartzCore
import Waveform

@MainActor
public final class DeckViewModel: ObservableObject {
    @Published public private(set) var volume: Double
    @Published public private(set) var pan: Double
    @Published public private(set) var externalBPMText: String
    @Published public private(set) var externalBPMStatusText: String
    @Published public private(set) var isExternalBPMLoading: Bool
    @Published public private(set) var isMicrophoneBPMDetectionActive: Bool
    @Published public private(set) var isPitchLockedToExternalBPM: Bool

    public let leftTurntableDeckViewModel: TurntableDeckViewModel
    public let rightTurntableDeckViewModel: TurntableDeckViewModel

    private let audioEngine: AudioEngineControlling
    private let microphoneBPMPipeline = MicrophoneBPMPipeline()
    private var latestExternalBPM: Double?

    public init(
        volume: Double = 0.8,
        pan: Double = 0.0,
        audioEngine: AudioEngineControlling = AudioEngineManager(),
        waveformAnalyzer: WaveformAnalyzing = WaveformAnalyzer()
    ) {
        self.audioEngine = audioEngine
        self.audioEngine.setVolume(Float(min(max(volume, 0), 1)))
        self.audioEngine.setPan(Float(min(max(pan, -1), 1)))
        self.volume = Double(self.audioEngine.volume)
        self.pan = Double(self.audioEngine.pan)
        self.externalBPMText = "-- BPM"
        self.externalBPMStatusText = "Mic BPM stopped"
        self.isExternalBPMLoading = false
        self.isMicrophoneBPMDetectionActive = false
        self.isPitchLockedToExternalBPM = false
        self.leftTurntableDeckViewModel = TurntableDeckViewModel(
            audioEngine: audioEngine,
            waveformAnalyzer: waveformAnalyzer
        )

        self.rightTurntableDeckViewModel = TurntableDeckViewModel(
            audioEngine: AudioEngineManager(),
            waveformAnalyzer: WaveformAnalyzer()
        )

        microphoneBPMPipeline.setResultHandler { [weak self] result in
            self?.handleMicrophoneBPMResult(result)
        }
    }

    deinit {
        audioEngine.stopMicrophoneCapture()
    }

    public var panRoutingText: String {
        if pan < -0.1 {
            return "L"
        }
        if pan > 0.1 {
            return "R"
        }
        return "C"
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

    public var canLockPitchToExternalBPM: Bool {
        isMicrophoneBPMDetectionActive
    }

    public func togglePitchLockToExternalBPM() {
        if isPitchLockedToExternalBPM {
            setPitchLockEnabled(false)
            return
        }

        guard canLockPitchToExternalBPM else {
            return
        }

        guard let externalBPM = latestExternalBPM else {
            externalBPMStatusText = "Listening... lock will apply once BPM is detected."
            return
        }

        setPitchLockEnabled(true, externalBPM: externalBPM)
        stopMicrophoneBPMDetection()
    }

    public func setPitchLockEnabled(_ isEnabled: Bool) {
        if isEnabled {
            guard let externalBPM = latestExternalBPM else {
                return
            }
            setPitchLockEnabled(true, externalBPM: externalBPM)
            return
        }

        isPitchLockedToExternalBPM = false
        leftTurntableDeckViewModel.unlockPitch()
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

    private func setPitchLockEnabled(_ isEnabled: Bool, externalBPM: Double) {
        if !isEnabled {
            isPitchLockedToExternalBPM = false
            leftTurntableDeckViewModel.unlockPitch()
            return
        }

        isPitchLockedToExternalBPM = true
        leftTurntableDeckViewModel.lockPitch(to: externalBPM)
    }

    private func handleMicrophoneBPMResult(_ result: BPMResult) {
        switch result {
        case let .detected(bpm, confidence):
            latestExternalBPM = bpm
            externalBPMText = String(format: "%.1f BPM", bpm)
            externalBPMStatusText = String(
                format: "Mic BPM (acc. %.2f)",
                confidence
            )
            isExternalBPMLoading = false
            if isPitchLockedToExternalBPM {
                leftTurntableDeckViewModel.lockPitch(to: bpm)
            }
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
        externalBPMStatusText = "Listening to MIC..."
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
