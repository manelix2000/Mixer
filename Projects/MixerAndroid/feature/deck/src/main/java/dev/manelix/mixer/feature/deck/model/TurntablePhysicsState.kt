package dev.manelix.mixer.feature.deck.model

data class TurntablePhysicsState(
    val platterPosition: Double = 0.0,
    val angularVelocity: Double = 0.0,
    val inertia: Double = 0.12,
    val damping: Double = 3.5,
)
