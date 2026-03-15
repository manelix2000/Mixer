import SwiftUI

public struct TurntableTonearmView: View {
    public let anchor: CGPoint
    public let relativeScale: CGFloat
    public let relativeOffset: CGSize
    public let armRotationDegrees: Double

    public init(
        anchor: CGPoint = CGPoint(x: 0.898, y: 0.096),
        relativeScale: CGFloat = 1.0,
        relativeOffset: CGSize = .zero,
        armRotationDegrees: Double = 0
    ) {
        self.anchor = anchor
        self.relativeScale = relativeScale
        self.relativeOffset = relativeOffset
        self.armRotationDegrees = armRotationDegrees
    }

    public var body: some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)
            let clampedScale = max(relativeScale, 0.1)
            // 1.0 maps to the historical visual size used before this remapping.
            let armSize = size * Self.referenceScaleFactor * clampedScale
            let anchoredBaseCenter = CGPoint(
                x: geometry.size.width * anchor.x + (relativeOffset.width * size),
                y: geometry.size.height * anchor.y + (relativeOffset.height * size)
            )
            let frameOrigin = CGPoint(
                x: anchoredBaseCenter.x - (armSize * 0.30),
                y: anchoredBaseCenter.y - (armSize * 0.36)
            )

            Canvas { context, canvasSize in
                let frame = CGRect(
                    x: frameOrigin.x,
                    y: frameOrigin.y,
                    width: armSize,
                    height: armSize
                )

                drawTonearm(in: &context, frame: frame)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func drawTonearm(in context: inout GraphicsContext, frame: CGRect) {
        let unit = min(frame.width, frame.height)

        let baseCenter = CGPoint(x: frame.minX + unit * 0.30, y: frame.minY + unit * 0.36)
        let ringRadius = unit * 0.20

        let armStart = CGPoint(x: baseCenter.x + unit * 0.10, y: baseCenter.y + unit * 0.05)
        let armMid = CGPoint(x: frame.minX + unit * 0.88, y: frame.minY + unit * 0.86)
        let armEnd = CGPoint(x: frame.minX + unit * 1.26, y: frame.minY + unit * 1.23)

        let silverDark = Color(red: 0.36, green: 0.39, blue: 0.43)
        let silverMid = Color(red: 0.72, green: 0.75, blue: 0.79)
        let silverLight = Color(red: 0.91, green: 0.93, blue: 0.95)

        let baseOuter = Path(ellipseIn: CGRect(
            x: baseCenter.x - ringRadius,
            y: baseCenter.y - ringRadius,
            width: ringRadius * 2,
            height: ringRadius * 2
        ))
        context.fill(baseOuter, with: .radialGradient(
            Gradient(colors: [silverLight, silverMid, silverDark]),
            center: baseCenter,
            startRadius: unit * 0.02,
            endRadius: ringRadius
        ))

        let baseInnerRadius = unit * 0.12
        let baseInner = Path(ellipseIn: CGRect(
            x: baseCenter.x - baseInnerRadius,
            y: baseCenter.y - baseInnerRadius,
            width: baseInnerRadius * 2,
            height: baseInnerRadius * 2
        ))
        context.stroke(baseInner, with: .color(Color.black.opacity(0.45)), lineWidth: unit * 0.01)

        var armContext = context
        armContext.translateBy(x: baseCenter.x, y: baseCenter.y)
        armContext.rotate(by: .degrees(Self.zeroDownRotationOffsetDegrees + armRotationDegrees))
        armContext.translateBy(x: -baseCenter.x, y: -baseCenter.y)

        let armPath: Path = {
            var p = Path()
            p.move(to: armStart)
            p.addQuadCurve(to: armMid, control: CGPoint(x: frame.minX + unit * 0.58, y: frame.minY + unit * 0.62))
            p.addQuadCurve(to: armEnd, control: CGPoint(x: frame.minX + unit * 1.02, y: frame.minY + unit * 1.03))
            return p
        }()

        armContext.stroke(
            armPath,
            with: .linearGradient(
                Gradient(colors: [silverDark, silverLight, silverMid]),
                startPoint: armStart,
                endPoint: armEnd
            ),
            style: StrokeStyle(lineWidth: unit * 0.06, lineCap: .round, lineJoin: .round)
        )

        armContext.stroke(
            armPath,
            with: .color(Color.black.opacity(0.25)),
            style: StrokeStyle(lineWidth: unit * 0.012, lineCap: .round, lineJoin: .round)
        )

        let counterWeightRect = CGRect(
            x: baseCenter.x - unit * 0.23,
            y: baseCenter.y - unit * 0.07,
            width: unit * 0.16,
            height: unit * 0.14
        )
        armContext.fill(
            Path(roundedRect: counterWeightRect, cornerRadius: unit * 0.03),
            with: .linearGradient(
                Gradient(colors: [silverDark, silverMid, silverLight]),
                startPoint: CGPoint(x: counterWeightRect.minX, y: counterWeightRect.midY),
                endPoint: CGPoint(x: counterWeightRect.maxX, y: counterWeightRect.midY)
            )
        )

        var headshellContext = armContext
        headshellContext.translateBy(x: armEnd.x, y: armEnd.y)
        headshellContext.rotate(by: .degrees(-90))
        headshellContext.translateBy(x: -armEnd.x, y: -armEnd.y)

        let ortofonYellow = Color(red: 0.95, green: 0.82, blue: 0.15)
        let headshellPlate = Path { p in
            p.move(to: CGPoint(x: armEnd.x - unit * 0.12, y: armEnd.y - unit * 0.055))
            p.addLine(to: CGPoint(x: armEnd.x + unit * 0.02, y: armEnd.y - unit * 0.028))
            p.addLine(to: CGPoint(x: armEnd.x + unit * 0.008, y: armEnd.y + unit * 0.016))
            p.addLine(to: CGPoint(x: armEnd.x - unit * 0.132, y: armEnd.y - unit * 0.014))
            p.closeSubpath()
        }
        headshellContext.fill(headshellPlate, with: .linearGradient(
            Gradient(colors: [Color(red: 0.23, green: 0.25, blue: 0.28), Color(red: 0.36, green: 0.39, blue: 0.43)]),
            startPoint: CGPoint(x: armEnd.x - unit * 0.12, y: armEnd.y - unit * 0.06),
            endPoint: CGPoint(x: armEnd.x + unit * 0.02, y: armEnd.y + unit * 0.02)
        ))

        let cartridge = Path(roundedRect: CGRect(
            x: armEnd.x - unit * 0.045,
            y: armEnd.y - unit * 0.028,
            width: unit * 0.135,
            height: unit * 0.072
        ), cornerRadius: unit * 0.016)
        headshellContext.fill(cartridge, with: .linearGradient(
            Gradient(colors: [ortofonYellow.opacity(0.94), ortofonYellow.opacity(0.78)]),
            startPoint: CGPoint(x: armEnd.x - unit * 0.045, y: armEnd.y - unit * 0.028),
            endPoint: CGPoint(x: armEnd.x + unit * 0.09, y: armEnd.y + unit * 0.044)
        ))

        let cartridgeFront = Path(roundedRect: CGRect(
            x: armEnd.x + unit * 0.062,
            y: armEnd.y - unit * 0.01,
            width: unit * 0.04,
            height: unit * 0.036
        ), cornerRadius: unit * 0.01)
        headshellContext.fill(cartridgeFront, with: .color(Color.black.opacity(0.88)))

        let mountScrew1 = Path(ellipseIn: CGRect(
            x: armEnd.x - unit * 0.018,
            y: armEnd.y - unit * 0.008,
            width: unit * 0.012,
            height: unit * 0.012
        ))
        let mountScrew2 = Path(ellipseIn: CGRect(
            x: armEnd.x + unit * 0.008,
            y: armEnd.y - unit * 0.003,
            width: unit * 0.012,
            height: unit * 0.012
        ))
        headshellContext.fill(mountScrew1, with: .color(Color.black.opacity(0.65)))
        headshellContext.fill(mountScrew2, with: .color(Color.black.opacity(0.65)))

        let stylus = Path { p in
            p.move(to: CGPoint(x: armEnd.x + unit * 0.093, y: armEnd.y + unit * 0.015))
            p.addLine(to: CGPoint(x: armEnd.x + unit * 0.128, y: armEnd.y + unit * 0.068))
        }
        headshellContext.stroke(stylus, with: .color(Color.black.opacity(0.95)), lineWidth: unit * 0.008)

        let pivotCap = Path(ellipseIn: CGRect(
            x: baseCenter.x - unit * 0.03,
            y: baseCenter.y - unit * 0.03,
            width: unit * 0.06,
            height: unit * 0.06
        ))
        context.fill(pivotCap, with: .color(Color.black.opacity(0.72)))
    }
}

extension TurntableTonearmView {
    private static let zeroDownRotationOffsetDegrees: Double = 48
    private static let referenceScaleFactor: CGFloat = 0.46
}

#Preview {
    TurntableTonearmView(
        anchor: CGPoint(x: 0.99, y: 0.16),
        relativeScale: 1.0,
        relativeOffset: .zero,
        armRotationDegrees: 0
    )
}
