package com.stoni.androidstone.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pursuit / intercept styles for hunter vs drift archetypes.
 * Spawn formations stay in [WaveSpawn]; this module only steers after spawn.
 */
enum class PursuitStyle {
    /** Full lead intercept — SCOUT / JAEGER / SCHNELL. */
    INTERCEPT,
    /** Soft lead + light approach — SWARMER / DROHNE. */
    INTERCEPT_LIGHT,
    /** Slower pursuit, short lead — TANK / PANZER. */
    PURSUIT_SLOW,
    /** Mild intercept — LANG / RUND / BOSS. */
    CHASE,
    /** Lateral / drop path — BOMBER (no aggressive intercept). */
    CROSS_DROP,
    /** Fixed drift — ASTEROID / FELS / KOMET / MINE (no retarget). */
    DRIFT,
}

fun EnemyType.pursuitStyle(): PursuitStyle = when (this) {
    EnemyType.SCOUT, EnemyType.JAEGER, EnemyType.SCHNELL -> PursuitStyle.INTERCEPT
    EnemyType.SWARMER, EnemyType.DROHNE -> PursuitStyle.INTERCEPT_LIGHT
    EnemyType.TANK, EnemyType.PANZER -> PursuitStyle.PURSUIT_SLOW
    EnemyType.LANG, EnemyType.RUND, EnemyType.BOSS -> PursuitStyle.CHASE
    EnemyType.BOMBER -> PursuitStyle.CROSS_DROP
    EnemyType.ASTEROID, EnemyType.FELS, EnemyType.KOMET, EnemyType.KOMET_BIG, EnemyType.MINE ->
        PursuitStyle.DRIFT
}

data class SteerResult(
    /** World-space position delta this tick (already toroidal-safe direction). */
    val dx: Float,
    val dy: Float,
    /** Aim vector for facing / shooting (toroidal toward aim point). */
    val aimDx: Float,
    val aimDy: Float,
)

/**
 * Toroidal delta from (fromX,fromY) to an intercept aim of a moving target.
 * Uses shortest wrap path — never aims through map center via naive Euclidean.
 *
 * Lead time ≈ dist / (eSpeed + relative closing), scaled by [leadFactor], capped.
 */
fun interceptAimDelta(
    fromX: Float,
    fromY: Float,
    targetX: Float,
    targetY: Float,
    targetVx: Float,
    targetVy: Float,
    worldW: Float,
    worldH: Float,
    eSpeed: Float,
    leadFactor: Float,
): Pair<Float, Float> {
    val curDx = wrapDelta(targetX - fromX, worldW)
    val curDy = wrapDelta(targetY - fromY, worldH)
    val dist = hypot(curDx, curDy)
    if (dist < 1e-3f || leadFactor <= 0f) return curDx to curDy

    val closing = (eSpeed + hypot(targetVx, targetVy)).coerceAtLeast(0.5f)
    val leadTicks = ((dist / closing) * leadFactor).coerceIn(0f, 48f)
    val predX = targetX + targetVx * leadTicks
    val predY = targetY + targetVy * leadTicks
    return wrapDelta(predX - fromX, worldW) to wrapDelta(predY - fromY, worldH)
}

/**
 * One-tick steering for an enemy. Drift types keep [driftVx]/[driftVy]
 * (set at spawn from edge facing); hunters recompute toward intercept each tick.
 */
fun enemySteer(
    type: EnemyType,
    ex: Float,
    ey: Float,
    shipX: Float,
    shipY: Float,
    shipVx: Float,
    shipVy: Float,
    worldW: Float,
    worldH: Float,
    eSpeed: Float,
    tick: Int,
    driftVx: Float,
    driftVy: Float,
): SteerResult {
    val style = type.pursuitStyle()
    val curDx = wrapDelta(shipX - ex, worldW)
    val curDy = wrapDelta(shipY - ey, worldH)
    val dist = hypot(curDx, curDy)

    when (style) {
        PursuitStyle.DRIFT -> {
            val spd = hypot(driftVx, driftVy).coerceAtLeast(0.01f)
            val scale = eSpeed / spd
            return SteerResult(driftVx * scale, driftVy * scale, driftVx, driftVy)
        }

        PursuitStyle.CROSS_DROP -> {
            // Sideways/cross or slow frontal — not aggressive chase (unchanged intent)
            if (dist <= 12f) return SteerResult(0f, 0f, curDx, curDy)
            val nx = curDx / dist
            val ny = curDy / dist
            val crossBias = 0.55f + 0.35f * sin(tick * 0.04f + ex * 0.01f)
            val approach = eSpeed * 0.22f
            val lateral = eSpeed * 1.05f
            val dx = nx * approach * (1f - crossBias) + (-ny) * lateral * crossBias
            val dy = ny * approach * (1f - crossBias) + nx * lateral * crossBias
            return SteerResult(dx, dy, curDx, curDy)
        }

        PursuitStyle.INTERCEPT,
        PursuitStyle.INTERCEPT_LIGHT,
        PursuitStyle.PURSUIT_SLOW,
        PursuitStyle.CHASE -> {
            val lead = when (style) {
                PursuitStyle.INTERCEPT -> 1.0f
                PursuitStyle.INTERCEPT_LIGHT -> 0.45f
                PursuitStyle.PURSUIT_SLOW -> 0.35f
                else -> 0.65f
            }
            val speedMul = when (style) {
                PursuitStyle.PURSUIT_SLOW -> 0.72f
                PursuitStyle.INTERCEPT_LIGHT -> 0.88f
                else -> 1f
            }
            val (aimDx, aimDy) = interceptAimDelta(
                ex, ey, shipX, shipY, shipVx, shipVy,
                worldW, worldH, eSpeed * speedMul, lead,
            )
            val aimDist = hypot(aimDx, aimDy)
            if (aimDist <= 12f) return SteerResult(0f, 0f, aimDx, aimDy)

            val nx = aimDx / aimDist
            val ny = aimDy / aimDist
            val spd = eSpeed * speedMul

            return when (type) {
                EnemyType.SCHNELL -> {
                    val zig = sin(tick * 0.28f + ex * 0.02f) * spd * 0.45f
                    SteerResult(
                        nx * spd + (-ny) * zig,
                        ny * spd + nx * zig,
                        aimDx, aimDy,
                    )
                }
                EnemyType.DROHNE -> {
                    val prefer = 170f
                    val radial = when {
                        dist > prefer + 50f -> spd * 0.9f
                        dist < prefer - 50f -> -spd * 0.55f
                        else -> spd * 0.15f
                    }
                    val tang = spd * 1.1f
                    SteerResult(
                        nx * radial + (-ny) * tang,
                        ny * radial + nx * tang,
                        aimDx, aimDy,
                    )
                }
                EnemyType.SWARMER -> {
                    val wobble = sin(tick * 0.19f + ey * 0.015f) * spd * 0.55f
                    SteerResult(
                        nx * spd + (-ny) * wobble,
                        ny * spd + nx * wobble,
                        aimDx, aimDy,
                    )
                }
                else -> SteerResult(nx * spd, ny * spd, aimDx, aimDy)
            }
        }
    }
}

/** Initial drift / entry velocity from spawn facing (unit direction × speed). */
fun spawnDriftVelocity(facingRad: Float, speed: Float): Pair<Float, Float> {
    val s = speed.coerceAtLeast(0.1f)
    return (cos(facingRad) * s) to (sin(facingRad) * s)
}

/** Facing angle in PlayScreen degrees (0 = tip toward -Y). */
fun aimFacingDeg(aimDx: Float, aimDy: Float): Float =
    (atan2(aimDy, aimDx) * 180.0 / Math.PI).toFloat() + 90f

