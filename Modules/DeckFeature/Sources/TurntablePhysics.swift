import Foundation

struct TurntablePhysics {
    var platterPosition: Double
    var angularVelocity: Double
    let inertia: Double
    let damping: Double

    init(
        platterPosition: Double = 0,
        angularVelocity: Double = 0,
        inertia: Double = 0.12,
        damping: Double = 3.5
    ) {
        self.platterPosition = platterPosition
        self.angularVelocity = angularVelocity
        self.inertia = max(inertia, 0.0001)
        self.damping = max(damping, 0)
    }

    mutating func reset(position: Double = 0) {
        platterPosition = normalizedAngle(position)
        angularVelocity = 0
    }

    mutating func applyAngularDrag(deltaAngle: Double, deltaTime: TimeInterval) {
        let safeDeltaTime = max(deltaTime, 0.001)
        platterPosition = normalizedAngle(platterPosition + deltaAngle)
        angularVelocity = deltaAngle / safeDeltaTime
    }

    mutating func step(deltaTime: TimeInterval, driveAngularVelocity: Double?) {
        let safeDeltaTime = min(max(deltaTime, 0), 0.1)
        guard safeDeltaTime > 0 else {
            return
        }

        if let targetVelocity = driveAngularVelocity {
            let blend = min(1, safeDeltaTime / inertia)
            angularVelocity += (targetVelocity - angularVelocity) * blend
        } else {
            angularVelocity *= exp(-damping * safeDeltaTime)
        }

        platterPosition = normalizedAngle(platterPosition + (angularVelocity * safeDeltaTime))
    }

    private func normalizedAngle(_ angle: Double) -> Double {
        let twoPi = 2.0 * Double.pi
        var normalized = angle.truncatingRemainder(dividingBy: twoPi)
        if normalized < 0 {
            normalized += twoPi
        }
        return normalized
    }
}
