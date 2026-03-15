import SwiftUI

public struct WaveformView: View {
    public static let minZoom: Double = 0.20
    public static let maxZoom: Double = 8.0

    public let samples: [Float]
    public let progress: Double
    public let isLoading: Bool
    public let zoom: Double

    private let baseSampleSpacing: CGFloat = 1.8
    private let maxWaveHeightRatio: CGFloat = 0.36

    public init(
        samples: [Float],
        progress: Double,
        isLoading: Bool,
        zoom: Double = 1.0
    ) {
        self.samples = samples
        self.progress = min(max(progress, 0), 1)
        self.isLoading = isLoading
        self.zoom = min(max(zoom, Self.minZoom), Self.maxZoom)
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
                        with: .color(.secondary.opacity(0.10)),
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
        let sampleSpacing = baseSampleSpacing * CGFloat(pow(zoom, 1.35))
        let middleSample = progress * Double(max(samples.count - 1, 0))
        let maxHalfHeight = size.height * maxWaveHeightRatio
        let columns = max(Int(size.width.rounded(.up)), 2)

        var points: [CGPoint] = []
        points.reserveCapacity(columns)
        var smoothedAmplitude: CGFloat = 0

        for column in 0..<columns {
            let x = CGFloat(column)
            let samplePosition = middleSample + Double((x - centerX) / sampleSpacing)
            let amplitude = interpolatedSample(at: samplePosition)
            let clampedAmplitude = CGFloat(min(max(amplitude, 0), 1))
            smoothedAmplitude += (clampedAmplitude - smoothedAmplitude) * 0.22
            let halfHeight = max(0.15, smoothedAmplitude * maxHalfHeight)
            points.append(CGPoint(x: x, y: halfHeight))
        }

        guard points.count > 4 else {
            return
        }

        let midY = size.height / 2
        var body = Path()
        body.move(to: CGPoint(x: points[0].x, y: midY - points[0].y))
        addSmoothedUpperContour(to: &body, points: points, midY: midY)
        addSmoothedLowerContour(to: &body, points: points, midY: midY)
        body.closeSubpath()

        let innerPoints = points.map { CGPoint(x: $0.x, y: $0.y * 0.58) }
        var innerBody = Path()
        innerBody.move(to: CGPoint(x: innerPoints[0].x, y: midY - innerPoints[0].y))
        addSmoothedUpperContour(to: &innerBody, points: innerPoints, midY: midY)
        addSmoothedLowerContour(to: &innerBody, points: innerPoints, midY: midY)
        innerBody.closeSubpath()

        // Outer layer (base body).
        context.fill(body, with: .color(Color(uiColor: .systemGray2).opacity(0.62)))

        var playedContext = context
        playedContext.clip(to: Path(CGRect(x: 0, y: 0, width: centerX, height: size.height)))
        playedContext.fill(body, with: .color(Color.orange.opacity(0.85)))

        // Inner core layer (gives the two-layer DJ style body).
        context.fill(innerBody, with: .color(Color.white.opacity(0.14)))
        var playedInnerContext = context
        playedInnerContext.clip(to: Path(CGRect(x: 0, y: 0, width: centerX, height: size.height)))
        playedInnerContext.fill(innerBody, with: .color(Color.orange.opacity(0.35)))

        var upperEdge = Path()
        upperEdge.move(to: CGPoint(x: points[0].x, y: midY - points[0].y))
        addSmoothedUpperContour(to: &upperEdge, points: points, midY: midY)
        context.stroke(upperEdge, with: .color(.white.opacity(0.20)), lineWidth: 1)
    }

    private func interpolatedSample(at position: Double) -> Double {
        guard !samples.isEmpty else { return 0 }
        if position < 0 || position > Double(samples.count - 1) { return 0 }

        let lowerIndex = Int(position.rounded(.down))
        let upperIndex = min(lowerIndex + 1, samples.count - 1)
        let fraction = position - Double(lowerIndex)
        let lower = Double(samples[lowerIndex])
        let upper = Double(samples[upperIndex])
        return lower + ((upper - lower) * fraction)
    }

    private func addSmoothedUpperContour(to path: inout Path, points: [CGPoint], midY: CGFloat) {
        guard points.count > 1 else { return }
        for index in 1..<points.count {
            let previous = points[index - 1]
            let current = points[index]
            let control = CGPoint(
                x: (previous.x + current.x) * 0.5,
                y: midY - ((previous.y + current.y) * 0.5)
            )
            path.addQuadCurve(
                to: CGPoint(x: current.x, y: midY - current.y),
                control: control
            )
        }
    }

    private func addSmoothedLowerContour(to path: inout Path, points: [CGPoint], midY: CGFloat) {
        guard points.count > 1 else { return }
        for index in stride(from: points.count - 1, through: 0, by: -1) {
            let current = points[index]
            if index == points.count - 1 {
                path.addLine(to: CGPoint(x: current.x, y: midY + current.y))
                continue
            }
            let next = points[index + 1]
            let control = CGPoint(
                x: (current.x + next.x) * 0.5,
                y: midY + ((current.y + next.y) * 0.5)
            )
            path.addQuadCurve(
                to: CGPoint(x: current.x, y: midY + current.y),
                control: control
            )
        }
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
