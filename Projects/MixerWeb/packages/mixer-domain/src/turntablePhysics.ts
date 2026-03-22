export type TurntablePhysicsState = {
  platterPositionRadians: number;
  angularVelocity: number;
  inertia: number;
  damping: number;
};

export function createTurntablePhysicsState(
  overrides: Partial<TurntablePhysicsState> = {}
): TurntablePhysicsState {
  return {
    platterPositionRadians: 0,
    angularVelocity: 0,
    inertia: 0.12,
    damping: 3.5,
    ...overrides
  };
}

export function stepTurntablePhysics(
  state: TurntablePhysicsState,
  deltaTime: number,
  driveAngularVelocity?: number
): TurntablePhysicsState {
  const safeDeltaTime = Math.min(Math.max(deltaTime, 0), 0.1);
  if (safeDeltaTime <= 0) {
    return state;
  }

  let angularVelocity = state.angularVelocity;
  if (typeof driveAngularVelocity === "number") {
    const blend = Math.min(1, safeDeltaTime / Math.max(state.inertia, 0.0001));
    angularVelocity += (driveAngularVelocity - angularVelocity) * blend;
  } else {
    angularVelocity *= Math.exp(-Math.max(state.damping, 0) * safeDeltaTime);
  }

  return {
    ...state,
    angularVelocity,
    platterPositionRadians: normalizeAngle(
      state.platterPositionRadians + (angularVelocity * safeDeltaTime)
    )
  };
}

export function applyTurntableDrag(
  state: TurntablePhysicsState,
  deltaAngle: number,
  deltaTime: number
): TurntablePhysicsState {
  const safeDeltaTime = Math.max(deltaTime, 0.001);
  return {
    ...state,
    platterPositionRadians: normalizeAngle(state.platterPositionRadians + deltaAngle),
    angularVelocity: deltaAngle / safeDeltaTime
  };
}

function normalizeAngle(angle: number): number {
  const fullRotation = Math.PI * 2;
  let normalized = angle % fullRotation;
  if (normalized < 0) {
    normalized += fullRotation;
  }
  return normalized;
}
