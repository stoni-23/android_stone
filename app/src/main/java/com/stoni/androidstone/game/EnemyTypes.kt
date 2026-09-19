package com.stoni.androidstone.game

import androidx.compose.ui.graphics.Color

/**
 * Enemy archetypes: legacy Stoerer mix + Artiflux pack (SCHNELL/PANZER/DROHNE/BOMBER).
 * Stats only — draw stays in PlayScreen.
 */
enum class EnemyType(
    val baseRadius: Float,
    val speed: Float,
    val maxHp: Int,
    val color: Color,
    val scoreValue: Int
) {
    SWARMER(14f, 4.5f, 1, Color(0xFF00FF9D), 10),
    SCOUT(22f, 3.0f, 3, Color(0xFFFF5252), 25),
    TANK(38f, 1.4f, 10, Color(0xFFFF9100), 75),
    BOSS(65f, 0.9f, 60, Color(0xFFE040FB), 500),
    LANG(20f, 2.8f, 4, Color(0xFF82B1FF), 35),
    RUND(28f, 2.0f, 6, Color(0xFFFF80AB), 45),
    KOMET(22f, 3.2f, 2, Color(0xFFFFAB40), 20),
    KOMET_BIG(65f, 0.75f, 18, Color(0xFFFF6D00), 400),
    ASTEROID(24f, 2.2f, 3, Color(0xFFBCAAA4), 15),
    FELS(40f, 1.1f, 12, Color(0xFF8D6E63), 80),
    JAEGER(22f, 3.4f, 3, Color(0xFF40C4FF), 30),
    MINE(18f, 1.6f, 1, Color(0xFFFF1744), 35),
    /** Fast zig — narrow needle. */
    SCHNELL(16f, 5.4f, 1, Color(0xFF18FFFF), 22),
    /** Slow tanky brick. */
    PANZER(42f, 0.95f, 16, Color(0xFFBF360C), 95),
    /** Swarm drone — packs of 3–5. */
    DROHNE(15f, 3.6f, 1, Color(0xFF69F0AE), 14),
    /** Drop bomber — lateral flight, bombs fall down (vy > 0). */
    BOMBER(28f, 1.7f, 5, Color(0xFFFF6E40), 55)
}

fun EnemyType.isBossLike(): Boolean =
    this == EnemyType.BOSS || this == EnemyType.KOMET_BIG

fun EnemyType.canShoot(): Boolean = when (this) {
    EnemyType.SCOUT, EnemyType.TANK, EnemyType.BOSS, EnemyType.JAEGER,
    EnemyType.LANG, EnemyType.RUND, EnemyType.SCHNELL, EnemyType.PANZER,
    EnemyType.BOMBER -> true
    else -> false
}
