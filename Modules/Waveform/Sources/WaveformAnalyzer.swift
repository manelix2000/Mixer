import AVFoundation
import Foundation

public protocol WaveformAnalyzing: Sendable {
    func generateWaveform(url: URL, sampleCount: Int) throws -> [Float]
    func generateWaveform(
        url: URL,
        sampleCount: Int,
        onProgress: @Sendable (WaveformProgress) -> Void
    ) throws -> [Float]
}

public enum WaveformAnalyzerError: Error {
    case invalidSampleCount
    case unsupportedAudioBuffer
}

public struct WaveformProgress: Sendable {
    public let samples: [Float]
    public let completedBuckets: Int
    public let totalBuckets: Int

    public var fraction: Double {
        guard totalBuckets > 0 else { return 0 }
        return min(max(Double(completedBuckets) / Double(totalBuckets), 0), 1)
    }

    public init(samples: [Float], completedBuckets: Int, totalBuckets: Int) {
        self.samples = samples
        self.completedBuckets = completedBuckets
        self.totalBuckets = totalBuckets
    }
}

public struct WaveformAnalyzer: WaveformAnalyzing, Sendable {
    public init() {}

    public func generateWaveform(url: URL, sampleCount: Int = 512) throws -> [Float] {
        try generateWaveform(url: url, sampleCount: sampleCount, onProgress: { _ in })
    }

    public func generateWaveform(
        url: URL,
        sampleCount: Int = 512,
        onProgress: @Sendable (WaveformProgress) -> Void
    ) throws -> [Float] {
        guard sampleCount > 0 else {
            throw WaveformAnalyzerError.invalidSampleCount
        }

        let file = try AVAudioFile(forReading: url)
        let sourceFormat = file.processingFormat
        let readFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: sourceFormat.sampleRate,
            channels: sourceFormat.channelCount,
            interleaved: false
        )!

        let totalFrames = max(Int(file.length), 1)
        let framesPerBucket = max(Int(ceil(Double(totalFrames) / Double(sampleCount))), 1)

        let chunkSize: AVAudioFrameCount = 4096
        guard let buffer = AVAudioPCMBuffer(pcmFormat: readFormat, frameCapacity: chunkSize) else {
            throw WaveformAnalyzerError.unsupportedAudioBuffer
        }

        var buckets: [Float] = []
        buckets.reserveCapacity(sampleCount)
        var runningMax: Float = 0

        var currentBucketPeak: Float = 0
        var currentBucketSquareSum: Float = 0
        var framesInBucket = 0
        var shouldStop = false

        while !shouldStop {
            try file.read(into: buffer, frameCount: chunkSize)
            let frameCount = Int(buffer.frameLength)
            if frameCount == 0 {
                break
            }

            guard let channelData = buffer.floatChannelData else {
                throw WaveformAnalyzerError.unsupportedAudioBuffer
            }

            let channelCount = Int(buffer.format.channelCount)

            for frameIndex in 0..<frameCount {
                var sum: Float = 0
                for channelIndex in 0..<channelCount {
                    sum += abs(channelData[channelIndex][frameIndex])
                }

                let averagedAmplitude = sum / Float(channelCount)
                currentBucketPeak = max(currentBucketPeak, averagedAmplitude)
                currentBucketSquareSum += averagedAmplitude * averagedAmplitude
                framesInBucket += 1

                if framesInBucket >= framesPerBucket {
                    let bucketValue = finalizeBucketValue(
                        peak: currentBucketPeak,
                        squareSum: currentBucketSquareSum,
                        frameCount: framesInBucket
                    )
                    buckets.append(bucketValue)
                    runningMax = max(runningMax, bucketValue)
                    currentBucketPeak = 0
                    currentBucketSquareSum = 0
                    framesInBucket = 0

                    if buckets.count % 64 == 0 || buckets.count == sampleCount {
                        onProgress(
                            WaveformProgress(
                                samples: makeProgressSnapshot(
                                    buckets: buckets,
                                    sampleCount: sampleCount,
                                    runningMax: runningMax
                                ),
                                completedBuckets: buckets.count,
                                totalBuckets: sampleCount
                            )
                        )
                    }

                    if buckets.count >= sampleCount {
                        shouldStop = true
                        break
                    }
                }
            }
        }

        if framesInBucket > 0 && buckets.count < sampleCount {
            let bucketValue = finalizeBucketValue(
                peak: currentBucketPeak,
                squareSum: currentBucketSquareSum,
                frameCount: framesInBucket
            )
            buckets.append(bucketValue)
            runningMax = max(runningMax, bucketValue)
        }

        if buckets.count > sampleCount {
            buckets = Array(buckets.prefix(sampleCount))
        } else if buckets.count < sampleCount {
            buckets.append(contentsOf: repeatElement(0, count: sampleCount - buckets.count))
        }

        let normalization = normalizationWindow(for: buckets)
        if normalization.scale > 0 {
            buckets = buckets.map { sample in
                let normalized = (sample - normalization.floor) / normalization.scale
                return min(max(normalized, 0), 1)
            }
        }

        onProgress(
            WaveformProgress(
                samples: buckets,
                completedBuckets: sampleCount,
                totalBuckets: sampleCount
            )
        )

        return buckets
    }

    private func makeProgressSnapshot(
        buckets: [Float],
        sampleCount: Int,
        runningMax: Float
    ) -> [Float] {
        let normalization = max(runningMax, 0.000001)
        var snapshot = buckets.map { $0 / normalization }
        if snapshot.count < sampleCount {
            snapshot.append(contentsOf: repeatElement(0, count: sampleCount - snapshot.count))
        }
        return snapshot
    }

    private func finalizeBucketValue(peak: Float, squareSum: Float, frameCount: Int) -> Float {
        guard frameCount > 0 else { return 0 }
        let rms = sqrt(squareSum / Float(frameCount))
        // RMS provides body; a smaller peak contribution preserves transients.
        let blended = (rms * 0.82) + (peak * 0.18)
        return pow(max(blended, 0), 0.95)
    }

    private func normalizationWindow(for buckets: [Float]) -> (floor: Float, scale: Float) {
        guard !buckets.isEmpty else { return (0, 0) }
        let sorted = buckets.sorted()
        let floorIndex = Int((Double(sorted.count - 1) * 0.10).rounded(.down))
        let ceilingIndex = Int((Double(sorted.count - 1) * 0.985).rounded(.down))
        let safeFloorIndex = min(max(floorIndex, 0), sorted.count - 1)
        let safeCeilingIndex = min(max(ceilingIndex, safeFloorIndex), sorted.count - 1)
        let floor = sorted[safeFloorIndex]
        let ceiling = sorted[safeCeilingIndex]
        return (floor, max(ceiling - floor, 0.000001))
    }
}
