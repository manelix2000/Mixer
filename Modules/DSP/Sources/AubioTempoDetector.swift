import Foundation

#if canImport(Aubio)
@_implementationOnly import Aubio
#elseif canImport(aubio)
@_implementationOnly import aubio
#endif

#if canImport(Aubio) || canImport(aubio)

/// Narrow Swift wrapper over aubio tempo detection for offline BPM estimation.
/// This type owns all native resources and keeps aubio-specific details inside DSP.
public final class AubioTempoDetector: TempoDetecting, @unchecked Sendable {
    private let configuration: TempoDetectorConfiguration
    private let stateLock = NSLock()
    private var nativeContext: NativeContext?

    public init(configuration: TempoDetectorConfiguration = .init()) throws {
        guard configuration.windowSize > 0, configuration.hopSize > 0 else {
            throw TempoDetectionError.invalidConfiguration
        }
        self.configuration = configuration
    }

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

        let monoSamples = downmixedMonoSamples(from: input)
        guard !monoSamples.isEmpty else {
            return .unavailable(reason: "No samples available after downmix.")
        }

        return try withStateLock {
            let context = try contextForSampleRate(input.sampleRate)
            let result = try context.process(samples: monoSamples)

            if result.bpm.isFinite, result.bpm > 0 {
                return .detected(bpm: result.bpm, confidence: min(max(result.confidence, 0), 1))
            }
            return .unavailable(reason: "Aubio did not produce a valid BPM estimate.")
        }
    }

    private func contextForSampleRate(_ sampleRate: Double) throws -> NativeContext {
        if let nativeContext, nativeContext.sampleRate == sampleRate {
            return nativeContext
        }

        let context = try NativeContext(
            method: configuration.method,
            windowSize: configuration.windowSize,
            hopSize: configuration.hopSize,
            sampleRate: sampleRate
        )
        nativeContext = context
        return context
    }

    private func withStateLock<T>(_ operation: () throws -> T) rethrows -> T {
        stateLock.lock()
        defer { stateLock.unlock() }
        return try operation()
    }

    private func downmixedMonoSamples(from input: TempoInputBuffer) -> [Float] {
        if input.channelCount == 1 {
            return input.samples
        }

        let frameCount = input.samples.count / input.channelCount
        guard frameCount > 0 else {
            return []
        }

        var mono = [Float]()
        mono.reserveCapacity(frameCount)

        if input.isInterleaved {
            for frame in 0..<frameCount {
                var sum: Float = 0
                let base = frame * input.channelCount
                for channel in 0..<input.channelCount {
                    sum += input.samples[base + channel]
                }
                mono.append(sum / Float(input.channelCount))
            }
        } else {
            for frame in 0..<frameCount {
                var sum: Float = 0
                for channel in 0..<input.channelCount {
                    let index = channel * frameCount + frame
                    sum += input.samples[index]
                }
                mono.append(sum / Float(input.channelCount))
            }
        }

        return mono
    }
}

private final class NativeContext {
    let sampleRate: Double
    private let hopSize: Int
    private let tempo: OpaquePointer
    private let inputVector: UnsafeMutablePointer<fvec_t>
    private let outputVector: UnsafeMutablePointer<fvec_t>

    init(
        method: String,
        windowSize: Int,
        hopSize: Int,
        sampleRate: Double
    ) throws {
        guard let cMethod = strdup(method) else {
            throw TempoDetectionError.nativeInitializationFailed
        }
        defer { free(cMethod) }

        let sampleRateUInt = UInt32(sampleRate.rounded())
        guard sampleRateUInt > 0 else {
            throw TempoDetectionError.invalidSampleRate
        }

        guard let tempo = new_aubio_tempo(
            cMethod,
            UInt32(windowSize),
            UInt32(hopSize),
            sampleRateUInt
        ) else {
            throw TempoDetectionError.nativeInitializationFailed
        }

        guard let inputVector = new_fvec(UInt32(hopSize)),
              let outputVector = new_fvec(1) else {
            del_aubio_tempo(tempo)
            throw TempoDetectionError.nativeInitializationFailed
        }

        self.sampleRate = sampleRate
        self.hopSize = hopSize
        self.tempo = tempo
        self.inputVector = inputVector
        self.outputVector = outputVector
    }

    deinit {
        del_fvec(outputVector)
        del_fvec(inputVector)
        del_aubio_tempo(tempo)
    }

    func process(samples: [Float]) throws -> (bpm: Double, confidence: Double) {
        var offset = 0

        while offset < samples.count {
            let remaining = samples.count - offset
            let count = min(remaining, hopSize)

            for index in 0..<hopSize {
                let value: Float = index < count ? samples[offset + index] : 0
                fvec_set_sample(inputVector, value, UInt32(index))
            }

            aubio_tempo_do(tempo, UnsafePointer(inputVector), outputVector)
            offset += count
        }

        let bpm = Double(aubio_tempo_get_bpm(tempo))
        let confidence = Double(aubio_tempo_get_confidence(tempo))

        if bpm.isNaN || bpm.isInfinite || confidence.isNaN || confidence.isInfinite {
            throw TempoDetectionError.nativeProcessingFailed
        }

        return (bpm: bpm, confidence: confidence)
    }
}

#endif
