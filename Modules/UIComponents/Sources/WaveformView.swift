import SwiftUI

public struct WaveformView: View {
    public let samples: [Float]
    public let progress: Double
    public let isLoading: Bool
    public let zoom: Double

    private let baseSampleSpacing: CGFloat = 2.0
    private let maxBarHeightRatio: CGFloat = 0.42

    public init(
        samples: [Float],
        progress: Double,
        isLoading: Bool,
        zoom: Double = 1.0
    ) {
        self.samples = samples
        self.progress = min(max(progress, 0), 1)
        self.isLoading = isLoading
        self.zoom = min(max(zoom, 0.5), 4.0)
    }

    public var body: some View {
        GeometryReader { geometry in
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(uiColor: .tertiarySystemBackground))

                Canvas(opaque: true, colorMode: .linear, rendersAsynchronously: true) { context, size in
                    let midY = size.height / 2

                    var baseline = Path()
                    baseline.move(to: CGPoint(x: 0, y: midY))
                    baseline.addLine(to: CGPoint(x: size.width, y: midY))
                    context.stroke(
                        baseline,
                        with: .color(.secondary.opacity(0.2)),
                        lineWidth: 1
                    )

                    drawWaveform(context: context, size: size)

                    var playhead = Path()
                    playhead.move(to: CGPoint(x: size.width / 2, y: 0))
                    playhead.addLine(to: CGPoint(x: size.width / 2, y: size.height))
                    context.stroke(
                        playhead,
                        with: .color(.red.opacity(0.85)),
                        lineWidth: 2
                    )
                }

                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    private func drawWaveform(context: GraphicsContext, size: CGSize) {
        guard !samples.isEmpty else {
            return
        }

        let centerX = size.width / 2
        let sampleSpacing = baseSampleSpacing * CGFloat(zoom)
        let middleSample = progress * Double(max(samples.count - 1, 0))
        let maxHeight = (size.height * maxBarHeightRatio)
        let barWidth: CGFloat = 1.2

        var waveform = Path()
        for index in samples.indices {
            let x = centerX + (CGFloat(index) - CGFloat(middleSample)) * sampleSpacing
            if x < -sampleSpacing || x > size.width + sampleSpacing {
                continue
            }

            let normalized = CGFloat(min(max(samples[index], 0), 1))
            let halfBarHeight = max(1, normalized * maxHeight)
            let alignedX = x.rounded(.toNearestOrAwayFromZero)
            let barRect = CGRect(
                x: alignedX - (barWidth / 2),
                y: (size.height / 2) - halfBarHeight,
                width: barWidth,
                height: halfBarHeight * 2
            )
            waveform.addRect(barRect)
        }

        context.fill(waveform, with: .color(.blue.opacity(0.85)))
    }
}

#Preview {
    let demoSamples: [Float] = (0..<512).map { i in
        let phase = Double(i) / 512.0
        return Float(abs(sin(phase * .pi * 10)))
    }

    WaveformView(samples: demoSamples, progress: 0.3, isLoading: false, zoom: 1.25)
        .frame(height: 110)
        .padding()
}
