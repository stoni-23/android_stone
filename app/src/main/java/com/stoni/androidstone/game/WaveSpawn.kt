package com.stoni.androidstone.game

import kotlin.random.Random

/** Result of one spawn roll: type + how many entities to place. */
data class SpawnPick(
    val type: EnemyType,
    val packSize: Int
)

/**
 * Power-up kinds for drop rolls.
 * ENERGY + RAPID_FIRE are weighted highest.
 * Draw: PlayScreen wires Artiflux P1 (energy/spread) + optional rapid/magnet;
 * P2 laser/missile/bomb/overdrive assets staged only (no gameplay yet).
 */
enum class PowerUpKind {
    ENERGY,
    RAPID_FIRE,
    SPREAD,
    SHIELD,
    SCORE_MAGNET,
    SPEED_BOOST
}

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
 * Weighted spawn table per wave band. Legacy types kept; pack types mixed in.
 * DROHNE packs 3–5; SCHNELL 1–2; PANZER/BOMBER rarer (low weight, wave-gated).
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
            EnemyType.SWARMER to 10,
            EnemyType.DROHNE to 14,
            EnemyType.SCOUT to 10,
            EnemyType.SCHNELL to 12,
            EnemyType.JAEGER to 10,
            EnemyType.LANG to 6,
            EnemyType.RUND to 6,
            EnemyType.TANK to 7,
            EnemyType.ASTEROID to 4,
            EnemyType.KOMET to 4,
            EnemyType.MINE to 4,
            EnemyType.FELS to 3,
            EnemyType.BOMBER to 6,
            EnemyType.PANZER to 4,
        )
    }

    val type = weightedPick(table, rng)
    val packSize = when (type) {
        EnemyType.SWARMER -> 2 + rng.nextInt(3) // 2–4
        EnemyType.DROHNE -> 3 + rng.nextInt(3) // 3–5
        EnemyType.SCHNELL -> if (rng.nextFloat() < 0.42f) 2 else 1
        else -> 1
    }
    return SpawnPick(type, packSize)
}

/**
 * Roll a power-up drop on enemy death.
 * @return null if nothing drops; otherwise a [PowerUpKind] (Energy/Rapid prioritized).
 */
fun rollPowerUpDrop(wave: Int, rng: Random = Random): PowerUpKind? {
    val chance = (0.28f + wave * 0.012f).coerceAtMost(0.38f)
    if (rng.nextFloat() >= chance) return null
    val weights = listOf(
        PowerUpKind.ENERGY to 28,
        PowerUpKind.RAPID_FIRE to 24,
        PowerUpKind.SPREAD to 14,
        PowerUpKind.SHIELD to 12,
        PowerUpKind.SPEED_BOOST to 12,
        PowerUpKind.SCORE_MAGNET to 10,
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
