package com.stoni.androidstone.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Result of one spawn roll: type + how many entities to place. */
data class SpawnPick(
    val type: EnemyType,
    val packSize: Int
)

/**
 * Power-up kinds for drop rolls (Artiflux Items v1).
 * P1: ENERGY (heal) + LIFE (extra life); SPREAD highest among weapons.
 * P2 rarer: WEAPON_LASER, WEAPON_MISSILE, BOMB, OVERDRIVE.
 * Optional low: RAPID_FIRE, SCORE_MAGNET. SHIELD/SPEED_BOOST moderate.
 */
enum class PowerUpKind {
    ENERGY,
    LIFE,
    RAPID_FIRE,
    SPREAD,
    SHIELD,
    SCORE_MAGNET,
    SPEED_BOOST,
    WEAPON_LASER,
    WEAPON_MISSILE,
    BOMB,
    OVERDRIVE,
}

/** Pack geometry for edge spawns — clear, deterministic offsets (no random clutter). */
enum class Formation {
    /** Horizontal line, spaced along local X. */
    LINE,
    /** V / Keil — leader at tip, wings trailing. */
    WEDGE,
    /** Compact swarm nest slots. */
    SWARM,
}

data class SpawnOffset(val dx: Float, val dy: Float)

data class WorldPos(val x: Float, val y: Float)

private fun <T> weightedPick(weights: List<Pair<T, Int>>, rng: Random): T {
    val total = weights.sumOf { it.second }.coerceAtLeast(1)
    var roll = rng.nextInt(total)
    for ((item, w) in weights) {
        if (roll < w) return item
        roll -= w
    }
    return weights.last().first
}

/**
 * Floor-based toroidal wrap on one axis.
 * Never clamps to world-center; maps into [0, size).
 */
fun wrapCoord(v: Float, size: Float): Float {
    if (size <= 0f) return 0f
    var x = v - size * floor(v / size)
    if (x >= size) x -= size
    if (x < 0f) x += size
    return x
}

/** Shortest signed delta on a toroidal axis (result in (-size/2, size/2]). */
fun wrapDelta(d: Float, size: Float): Float {
    if (size <= 0f) return 0f
    var v = d - size * floor(d / size)
    if (v >= size) v -= size
    if (v < 0f) v += size
    if (v > size * 0.5f) v -= size
    return v
}

fun wrapWorld(x: Float, y: Float, worldW: Float, worldH: Float): WorldPos =
    WorldPos(wrapCoord(x, worldW), wrapCoord(y, worldH))

/**
 * Spawn just outside the camera viewport on a world/viewport edge.
 * Never places the pack origin in map/viewport center.
 */
fun pickEdgeSpawnOrigin(
    camX: Float,
    camY: Float,
    viewW: Float,
    viewH: Float,
    worldW: Float,
    worldH: Float,
    margin: Float = 140f,
    rng: Random = Random,
): WorldPos {
    val halfW = viewW.coerceAtLeast(1f) * 0.5f
    val halfH = viewH.coerceAtLeast(1f) * 0.5f
    val alongX = (rng.nextFloat() - 0.5f) * viewW * 0.85f
    val alongY = (rng.nextFloat() - 0.5f) * viewH * 0.85f
    val (lx, ly) = when (rng.nextInt(4)) {
        0 -> -(halfW + margin) to alongY
        1 -> (halfW + margin) to alongY
        2 -> alongX to -(halfH + margin)
        else -> alongX to (halfH + margin)
    }
    return wrapWorld(camX + lx, camY + ly, worldW, worldH)
}

/**
 * Deterministic formation offsets in local space (forward = +Y after rotate).
 * Spacing is fixed so packs read as Linie / Keil / Schwarm.
 */
fun formationOffsets(formation: Formation, count: Int, spacing: Float = 58f): List<SpawnOffset> {
    val n = count.coerceAtLeast(1)
    return when (formation) {
        Formation.LINE -> (0 until n).map { i ->
            val t = i - (n - 1) * 0.5f
            SpawnOffset(t * spacing, 0f)
        }
        Formation.WEDGE -> (0 until n).map { i ->
            if (i == 0) SpawnOffset(0f, 0f)
            else {
                val row = (i + 1) / 2
                val side = if (i % 2 == 1) -1f else 1f
                SpawnOffset(side * row * spacing, row * spacing * 0.9f)
            }
        }
        Formation.SWARM -> {
            val slots = listOf(
                SpawnOffset(0f, 0f),
                SpawnOffset(-spacing, -spacing * 0.35f),
                SpawnOffset(spacing, -spacing * 0.35f),
                SpawnOffset(-spacing * 0.55f, spacing * 0.75f),
                SpawnOffset(spacing * 0.55f, spacing * 0.75f),
                SpawnOffset(0f, -spacing * 0.95f),
                SpawnOffset(-spacing * 1.15f, spacing * 0.15f),
                SpawnOffset(spacing * 1.15f, spacing * 0.15f),
            )
            if (n <= slots.size) {
                slots.take(n)
            } else {
                slots + (slots.size until n).map { i ->
                    val a = i * 2.399963f
                    val r = spacing * (1.1f + (i - slots.size) * 0.35f)
                    SpawnOffset(cos(a) * r, sin(a) * r)
                }
            }
        }
    }
}

/** Pick formation from enemy archetype + pack size. */
fun pickFormation(type: EnemyType, packSize: Int, rng: Random = Random): Formation = when {
    packSize <= 1 -> Formation.LINE
    type == EnemyType.DROHNE || type == EnemyType.SWARMER -> Formation.SWARM
    type == EnemyType.SCHNELL -> if (rng.nextBoolean()) Formation.WEDGE else Formation.LINE
    packSize >= 3 && rng.nextFloat() < 0.55f -> Formation.WEDGE
    else -> Formation.LINE
}

/** Rotate local formation offsets so local +Y faces [facingRad]. */
fun rotateOffsets(offsets: List<SpawnOffset>, facingRad: Float): List<SpawnOffset> {
    val c = cos(facingRad)
    val s = sin(facingRad)
    return offsets.map { o ->
        SpawnOffset(
            dx = o.dx * c - o.dy * s,
            dy = o.dx * s + o.dy * c,
        )
    }
}

/** Facing from spawn origin toward a target (ship), toroidal. */
fun facingToward(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    worldW: Float,
    worldH: Float,
): Float {
    val dx = wrapDelta(toX - fromX, worldW)
    val dy = wrapDelta(toY - fromY, worldH)
    return atan2(dy, dx)
}

/** Wave size — more packs as waves climb. */
fun enemiesPerWave(wave: Int): Int = 16 + wave * 6 + (wave / 2) * 2

/** Ticks between pack spawns — shorter intervals on higher waves. */
fun spawnCooldownTicks(wave: Int): Int = (58 - wave * 4).coerceAtLeast(10)

/** Pause before first pack of a new wave. */
fun waveStartCooldown(wave: Int): Int = (70 - wave * 2).coerceAtLeast(36)

/**
 * Weighted spawn table per wave band. Legacy types kept; pack types mixed in.
 * DROHNE packs 3–5; SCHNELL 1–2; PANZER/BOMBER rarer (low weight, wave-gated).
 * Higher waves: harder types + slightly larger packs.
 */
fun pickEnemySpawn(wave: Int, rng: Random = Random): SpawnPick {
    val band = when {
        wave <= 2 -> 0
        wave <= 5 -> 1
        else -> 2
    }

    val table: List<Pair<EnemyType, Int>> = when (band) {
        0 -> listOf(
            EnemyType.SWARMER to 22,
            EnemyType.DROHNE to 18,
            EnemyType.SCOUT to 16,
            EnemyType.LANG to 12,
            EnemyType.SCHNELL to 12,
            EnemyType.RUND to 8,
            EnemyType.ASTEROID to 6,
            EnemyType.KOMET to 4,
            EnemyType.JAEGER to 2,
        )
        1 -> listOf(
            EnemyType.SWARMER to 14,
            EnemyType.DROHNE to 16,
            EnemyType.SCOUT to 12,
            EnemyType.SCHNELL to 14,
            EnemyType.LANG to 8,
            EnemyType.RUND to 7,
            EnemyType.JAEGER to 8,
            EnemyType.TANK to 6,
            EnemyType.ASTEROID to 4,
            EnemyType.KOMET to 4,
            EnemyType.MINE to 3,
            EnemyType.FELS to 2,
            EnemyType.BOMBER to 5,
            EnemyType.PANZER to 3,
        )
        else -> listOf(
            EnemyType.SWARMER to 8,
            EnemyType.DROHNE to 12,
            EnemyType.SCOUT to 8,
            EnemyType.SCHNELL to 12,
            EnemyType.JAEGER to 12,
            EnemyType.LANG to 5,
            EnemyType.RUND to 5,
            EnemyType.TANK to 8,
            EnemyType.ASTEROID to 3,
            EnemyType.KOMET to 3,
            EnemyType.MINE to 5,
            EnemyType.FELS to 4,
            EnemyType.BOMBER to 8,
            EnemyType.PANZER to 7,
        )
    }

    val type = weightedPick(table, rng)
    val packBoost = (wave / 4).coerceAtMost(2)
    val packSize = when (type) {
        EnemyType.SWARMER -> (2 + rng.nextInt(3) + packBoost).coerceAtMost(6)
        EnemyType.DROHNE -> (3 + rng.nextInt(3) + packBoost).coerceAtMost(7)
        EnemyType.SCHNELL -> when {
            wave >= 6 && rng.nextFloat() < 0.35f -> 3
            rng.nextFloat() < 0.42f + wave * 0.02f -> 2
            else -> 1
        }
        EnemyType.JAEGER -> if (wave >= 5 && rng.nextFloat() < 0.3f) 2 else 1
        else -> 1
    }
    return SpawnPick(type, packSize)
}

/**
 * Roll a power-up drop on enemy death (Artiflux Items v1 weights).
 * Energy klar häufiger als jede einzelne Waffe; Spread zweithöchste Waffen-Gewichtung.
 * @return null if nothing drops; otherwise a [PowerUpKind].
 */
fun rollPowerUpDrop(wave: Int, rng: Random = Random): PowerUpKind? {
    // Early waves: higher drop chance + more ENERGY/LIFE so the game is less brutal.
    val early = wave <= 3
    val chance = if (early) {
        (0.40f + wave * 0.02f).coerceAtMost(0.50f)
    } else {
        (0.30f + wave * 0.012f).coerceAtMost(0.40f)
    }
    if (rng.nextFloat() >= chance) return null
    val energyW = if (early) 38 else 30
    val lifeW = if (early) 22 else 14
    val weights = listOf(
        PowerUpKind.ENERGY to energyW,
        PowerUpKind.LIFE to lifeW,
        PowerUpKind.SPREAD to 14,
        PowerUpKind.SHIELD to 11,
        PowerUpKind.SPEED_BOOST to 10,
        PowerUpKind.RAPID_FIRE to 7,
        PowerUpKind.SCORE_MAGNET to 5,
        PowerUpKind.WEAPON_LASER to 4,
        PowerUpKind.WEAPON_MISSILE to 4,
        PowerUpKind.OVERDRIVE to 4,
        PowerUpKind.BOMB to 3,
    )
    return weightedPick(weights, rng)
}

/** Bomber bomb drop cooldown (ticks); longer than aim-shooters. */
fun bomberFireCooldown(wave: Int): Int = (78 - wave * 2).coerceAtLeast(42)

/**
 * Bomber bomb velocity: always falls "down" (vy > 0), slight lateral drift, no aim-at-player.
 */
fun bomberDropVelocity(wave: Int, rng: Random = Random): Pair<Float, Float> {
    val vy = (9.2f + wave * 0.28f).coerceAtLeast(0.5f)
    val vx = (rng.nextFloat() - 0.5f) * 3.6f
    return vx to vy
}
