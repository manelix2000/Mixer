import AVFoundation
import Foundation

public protocol WaveformAnalyzing: Sendable {
    func generateWaveform(url: URL, sampleCount: Int) throws -> [Float]
}

public enum WaveformAnalyzerError: Error {
    case invalidSampleCount
    case unsupportedAudioBuffer
}

public struct WaveformAnalyzer: WaveformAnalyzing, Sendable {
    public init() {}

    public func generateWaveform(url: URL, sampleCount: Int = 512) throws -> [Float] {
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

        var currentBucketMax: Float = 0
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
                currentBucketMax = max(currentBucketMax, averagedAmplitude)
                framesInBucket += 1

                if framesInBucket >= framesPerBucket {
                    buckets.append(currentBucketMax)
                    currentBucketMax = 0
                    framesInBucket = 0

                    if buckets.count >= sampleCount {
                        shouldStop = true
                        break
                    }
                }
            }
        }

        if framesInBucket > 0 && buckets.count < sampleCount {
            buckets.append(currentBucketMax)
        }

        if buckets.count > sampleCount {
            buckets = Array(buckets.prefix(sampleCount))
        } else if buckets.count < sampleCount {
            buckets.append(contentsOf: repeatElement(0, count: sampleCount - buckets.count))
        }

        if let maxAmplitude = buckets.max(), maxAmplitude > 0 {
            buckets = buckets.map { $0 / maxAmplitude }
        }

        return buckets
    }
}
