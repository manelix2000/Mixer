import SwiftUI
import UIKit
import CoreText
import os

public struct TurntableView: View {
    private static let log = Logger(
        subsystem: "dev.manelix.Mixer",
        category: "TurntableView.Font"
    )

    public let isPlaying: Bool
    public let platterAngleDegrees: Double
    public let tonearmAngleDegrees: Double

    public init(
        isPlaying: Bool,
        platterAngleDegrees: Double = 0,
        tonearmAngleDegrees: Double = 48
    ) {
        self.isPlaying = isPlaying
        self.platterAngleDegrees = platterAngleDegrees
        self.tonearmAngleDegrees = tonearmAngleDegrees
    }

    public var body: some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)
            ZStack {
                dottedStrobeRing(size: size, rotationDegrees: platterAngleDegrees)
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

                technicsRingLogos(size: size, rotationDegrees: platterAngleDegrees)

                labelDisc(size: size)
                    .rotationEffect(.degrees(platterAngleDegrees * 0.95))

                rotatingLightShadow(size: size)
                    .rotationEffect(.degrees(platterAngleDegrees))

                spindle(size: size)

                TurntableTonearmView(
                    anchor: CGPoint(x: 0.99, y: 0.16),
                    relativeScale: 0.46,
                    relativeOffset: .zero,
                    armRotationDegrees: tonearmAngleDegrees
                )
                .transaction { transaction in
                    transaction.animation = nil
                }
            }
            .padding(10)
            .frame(width: size, height: size)
        }
    }

    private func dottedStrobeRing(size: CGFloat, rotationDegrees: Double) -> some View {
        ZStack {
            Circle()
                .fill(Color.black.opacity(0.96))

            Circle()
                .stroke(Color(white: 0.34), lineWidth: size * 0.014)
                .padding(size * 0.006)

            Circle()
                .stroke(Color.black.opacity(0.78), lineWidth: size * 0.007)
                .padding(size * 0.02)

            ZStack {
                dottedArc(size: size, inset: size * 0.03, dotSize: size * 0.009, spacing: size * 0.021, color: Color(white: 0.62))
                dottedArc(size: size, inset: size * 0.045, dotSize: size * 0.017, spacing: size * 0.027, color: Color(white: 0.78))
                dottedArc(size: size, inset: size * 0.063, dotSize: size * 0.009, spacing: size * 0.021, color: Color(white: 0.6))
            }
            .rotationEffect(.degrees(rotationDegrees))
        }
    }

    private func dottedArc(
        size: CGFloat,
        inset: CGFloat,
        dotSize: CGFloat,
        spacing: CGFloat,
        color: Color
    ) -> some View {
        Circle()
            .stroke(
                color,
                style: StrokeStyle(
                    lineWidth: dotSize,
                    lineCap: .round,
                    dash: [0.001, spacing]
                )
            )
            .padding(inset)
            .overlay(
                Circle()
                    .stroke(
                        Color.black.opacity(0.35),
                        style: StrokeStyle(
                            lineWidth: max(dotSize * 1.1, size * 0.005),
                            lineCap: .round,
                            dash: [0.001, spacing]
                        )
                    )
                    .padding(inset)
            )
            .blendMode(.normal)
            .shadow(color: Color.black.opacity(0.25), radius: size * 0.002, x: 0, y: 0)
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

    private func technicsRingLogos(size: CGFloat, rotationDegrees: Double) -> some View {
        let logoWidth = size * 0.72
        let logoYOffset = size * 0.08
        let logoFontSize = size * 0.110

        return ZStack {
            VStack(spacing: size * 0.25) {
                Text("Technics")
                    .font(technicsLogoFont(size: logoFontSize))
                    .tracking(-0.5)
                    .foregroundStyle(Color(red: 0.50, green: 0.58, blue: 0.68).opacity(0.78))
                    .shadow(color: Color.black.opacity(0.30), radius: size * 0.003, x: 0, y: size * 0.0015)
                    .frame(width: logoWidth)
                Text("\nTechnics")
                    .font(technicsLogoFont(size: logoFontSize))
                    .tracking(-0.5)
                    .foregroundStyle(Color(red: 0.50, green: 0.58, blue: 0.68).opacity(0.60))
                    .shadow(color: Color.black.opacity(0.30), radius: size * 0.003, x: 0, y: size * 0.0015)
                    .rotationEffect(.degrees(180))
                    .frame(width: logoWidth)
            }
            .offset(y: logoYOffset)
        }
        .rotationEffect(.degrees(rotationDegrees))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func technicsLogoFont(size: CGFloat) -> Font {
        Self.ensureTechnicsFontRegistered()
        Self.logTechnicsFontDebugIfNeeded()
        for name in Self.technicsLogoFontCandidates {
            if UIFont(name: name, size: size) != nil {
                return .custom(name, size: size)
            }
        }
        Self.log.error("Technics font fallback used. No candidate matched.")
        return .system(size: size, weight: .regular, design: .rounded).italic()
    }

    private static let technicsLogoFontCandidates: [String] = [
        "MicrogrammaD-BoldExte",
        "Microgramma D Bold Extended",
        "Microgramma D ExtendedBold",
        "MicrogrammaD-BoldExtended",
        "Microgramma D Extended",
        "Microgramma D"
    ]

    private static var hasRegisteredTechnicsFont = false
    private static var hasLoggedTechnicsFontDebug = false

    private static func ensureTechnicsFontRegistered() {
        guard !hasRegisteredTechnicsFont else {
            return
        }
        hasRegisteredTechnicsFont = true

        guard let fontURL = Bundle.main.url(forResource: "MicrogrammaDExtendedBold", withExtension: "otf") else {
            log.error("Font file not found in Bundle.main: MicrogrammaDExtendedBold.otf")
            return
        }

        CTFontManagerRegisterFontsForURL(fontURL as CFURL, .process, nil)
        log.info("Requested CoreText registration for: \(fontURL.lastPathComponent, privacy: .public)")
    }

    private static func logTechnicsFontDebugIfNeeded() {
        guard !hasLoggedTechnicsFontDebug else {
            return
        }
        hasLoggedTechnicsFontDebug = true

        let microgrammaCandidates = UIFont.familyNames
            .flatMap { UIFont.fontNames(forFamilyName: $0) }
            .filter { $0.localizedCaseInsensitiveContains("microgramma") }

        log.info("Technics candidates: \(technicsLogoFontCandidates.joined(separator: ", "), privacy: .public)")
        if microgrammaCandidates.isEmpty {
            log.error("No runtime fonts containing 'microgramma' found.")
        } else {
            log.info("Runtime Microgramma fonts: \(microgrammaCandidates.joined(separator: ", "), privacy: .public)")
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
}
