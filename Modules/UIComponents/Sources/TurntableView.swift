import SwiftUI

public struct TurntableView: View {
    public let isPlaying: Bool
    public let platterAngleDegrees: Double

    public init(
        isPlaying: Bool,
        platterAngleDegrees: Double = 0
    ) {
        self.isPlaying = isPlaying
        self.platterAngleDegrees = platterAngleDegrees
    }

    public var body: some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)

            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color(uiColor: .secondarySystemBackground),
                                Color(uiColor: .tertiarySystemBackground)
                            ],
                            center: .center,
                            startRadius: size * 0.06,
                            endRadius: size * 0.52
                        )
                    )

                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color.black.opacity(0.65),
                                Color.black.opacity(0.92)
                            ],
                            center: .center,
                            startRadius: size * 0.02,
                            endRadius: size * 0.5
                        )
                    )
                    .padding(size * 0.07)

                grooves(size: size)
                    .rotationEffect(.degrees(platterAngleDegrees))

                labelDisc(size: size)
                    .rotationEffect(.degrees(platterAngleDegrees * 0.95))

                rotatingLightShadow(size: size)
                    .rotationEffect(.degrees(platterAngleDegrees))

                spindle(size: size)
            }
            .frame(width: size, height: size)
        }
    }

    private func grooves(size: CGFloat) -> some View {
        ZStack {
            ForEach(0..<20, id: \.self) { index in
                let ratio = CGFloat(index + 1) / 21.0
                Circle()
                    .stroke(
                        Color.white.opacity(index.isMultiple(of: 2) ? 0.08 : 0.04),
                        lineWidth: 1
                    )
                    .padding((size*0.5) * (0.12 + ratio * 0.64))
            }
        }
    }

    private func labelDisc(size: CGFloat) -> some View {
        ZStack {
            Circle()
                .fill(Color(red: 0.12, green: 0.16, blue: 0.2))
                .padding(size * 0.34)

            Circle()
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
                .padding(size * 0.36)
        }
    }

    private func spindle(size: CGFloat) -> some View {
        Circle()
            .fill(Color(uiColor: .systemGray3))
            .frame(width: size * 0.06, height: size * 0.06)
            .overlay(
                Circle()
                    .stroke(Color.white.opacity(0.5), lineWidth: 0.5)
            )
    }

    private func rotatingLightShadow(size: CGFloat) -> some View {
        Circle()
            .strokeBorder(
                AngularGradient(
                    colors: [
                        .clear,
                        Color.white.opacity(0.04),
                        Color.white.opacity(0.16),
                        Color.white.opacity(0.06),
                        .clear
                    ],
                    center: .center,
                    startAngle: .degrees(-25),
                    endAngle: .degrees(40)
                ),
                lineWidth: size * 0.11
            )
            .padding(size * 0.12)
            .blur(radius: size * 0.01)
            .blendMode(.screen)
            .opacity(isPlaying ? 1.0 : 0.6)
            .animation(.easeInOut(duration: 0.25), value: isPlaying)
    }
}

#Preview {
    VStack {
        TurntableView(isPlaying: true, platterAngleDegrees: 180)
            .padding(0)
    }
    .background(.red)
}
