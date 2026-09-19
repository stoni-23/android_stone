package com.stoni.androidstone.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.stoni.androidstone.R
import com.stoni.androidstone.game.loadStargameAsset
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private fun wrap(v: Float, span: Float): Float {
    if (span <= 0f) return 0f
    var x = v % span
    if (x < 0f) x += span
    return x
}

/** Shortest-path angle lerp in degrees (Compose rotate). */
private fun lerpAngleDeg(from: Float, to: Float, t: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return from + d * t
}

private data class Bullet(var x: Float, var y: Float, val dy: Float, val fromPlayer: Boolean, val triple: Boolean = false, val dx: Float = 0f)
/** Wave archetypes — sprites are existing placeholders until Looki packs land. */
private enum class EnemyKind {
    STORLING,      // zigzag
    PANZERDROHNE,  // slow formation hold
    DIVEBOMBER,    // dive
    HEULER,        // fan shots
    COMET          // hazard placeholder
}

private data class Enemy(
    var x: Float,
    var y: Float,
    var hp: Int = 1,
    var dx: Float = 0f,
    var dy: Float = 1.2f,
    var fireCd: Int = 40,
    val big: Boolean = false,
    val kind: EnemyKind = EnemyKind.DIVEBOMBER,
    var phase: Int = 0,
    var holdY: Float = 120f,
    val comet: Boolean = false
)
private data class PowerUp(var x: Float, var y: Float, val type: Int)
private data class Fx(var x: Float, var y: Float, var life: Int, val kind: Int)

private class GameSfx(context: Context) {
    private val pool: SoundPool
    private val shootId: Int
    private val hitId: Int
    private val pickupId: Int
    private var loaded = 0

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loaded++
        }
        shootId = pool.load(context, R.raw.shoot, 1)
        hitId = pool.load(context, R.raw.hit, 1)
        pickupId = pool.load(context, R.raw.pickup, 1)
    }

    fun shoot() = play(shootId, 0.55f)
    fun hit() = play(hitId, 0.7f)
    fun pickup() = play(pickupId, 0.65f)

    private fun play(id: Int, vol: Float) {
        if (id != 0) pool.play(id, vol, vol, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}

@Composable
fun PlayScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shipPxSize = with(density) { 120.dp.toPx() }
    val enemyPxSize = with(density) { 72.dp.toPx() }
    val bigPxSize = with(density) { 110.dp.toPx() }
    val bulletW = with(density) { 18.dp.toPx() }
    val bulletH = with(density) { 36.dp.toPx() }
    val prefs = remember { context.getSharedPreferences("stargame", Context.MODE_PRIVATE) }
    var high by remember { mutableIntStateOf(prefs.getInt("highscore", 0)) }

    val sfx = remember { GameSfx(context) }
    DisposableEffect(Unit) {
        onDispose { sfx.release() }
    }

    // Seitenprofil Bell — tip up / mouth down in asset; rotate with shipAngle
    val shipHull = remember { loadStargameAsset(context, "player_glocke_side_alt_128.png") }
    // Separate Looki thrust loop (prefer _64); NOT baked player_glocke_thrust_on_*
    val thrustFrames = remember {
        listOf(
            loadStargameAsset(context, "fx_thrust_1_64.png"),
            loadStargameAsset(context, "fx_thrust_2_64.png"),
            loadStargameAsset(context, "fx_thrust_3_64.png"),
            loadStargameAsset(context, "fx_thrust_4_64.png"),
        )
    }
    val enemyImg = remember { loadStargameAsset(context, "enemy_stoerer_64.png") }
    val enemyImgB = remember { loadStargameAsset(context, "enemy_stoerer_b_64.png") }
    val enemyBig = remember { loadStargameAsset(context, "enemy_stoerer_big_128.png") }
    val bulletImg = remember { loadStargameAsset(context, "bullet_player.png") }
    val bulletTriple = remember { loadStargameAsset(context, "bullet_player_triple.png") }
    val bulletEnemy = remember { loadStargameAsset(context, "bullet_enemy.png") }
    val starFar = remember { loadStargameAsset(context, "bg_stars_far.png") }
    val starMid = remember { loadStargameAsset(context, "bg_stars_mid.png") }
    val starNear = remember { loadStargameAsset(context, "bg_stars_near.png") }
    val heartImg = remember { loadStargameAsset(context, "ui_heart.png") }
    val puWeapon = remember { loadStargameAsset(context, "icon_mode_weapon_64.png") }
    val puHeal = remember { loadStargameAsset(context, "icon_mode_heal_64.png") }
    val puSpeed = remember { loadStargameAsset(context, "powerup_speed_64.png") }
    val boom1 = remember { loadStargameAsset(context, "fx_explosion_1.png") }
    val boom2 = remember { loadStargameAsset(context, "fx_explosion_2.png") }
    val boom3 = remember { loadStargameAsset(context, "fx_explosion_3.png") }
    val muzzle1 = remember { loadStargameAsset(context, "fx_muzzle_1.png") }
    val muzzle2 = remember { loadStargameAsset(context, "fx_muzzle_2.png") }
    val death1 = remember { loadStargameAsset(context, "fx_player_death_1.png") }
    val death2 = remember { loadStargameAsset(context, "fx_player_death_2.png") }
    val death3 = remember { loadStargameAsset(context, "fx_player_death_3.png") }
    val death4 = remember { loadStargameAsset(context, "fx_player_death_4.png") }
    val spark1 = remember { loadStargameAsset(context, "fx_spark_cyan_1.png") }
    val spark2 = remember { loadStargameAsset(context, "fx_spark_cyan_2.png") }

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var shipX by remember { mutableFloatStateOf(0.5f) }
    var shipY by remember { mutableFloatStateOf(0.82f) }
    // Compose degrees: 0 = tip up (asset default); tip follows movement via atan2+90
    var shipAngle by remember { mutableFloatStateOf(0f) }
    var shipSpeed by remember { mutableFloatStateOf(0f) }
    var prevShipPx by remember { mutableFloatStateOf(Float.NaN) }
    var prevShipPy by remember { mutableFloatStateOf(Float.NaN) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("Störfeuer. Bleib im Klang.") }
    var multishot by remember { mutableIntStateOf(0) }
    var shield by remember { mutableIntStateOf(0) }
    var speedBoost by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var starY1 by remember { mutableFloatStateOf(0f) }
    var starY2 by remember { mutableFloatStateOf(0f) }
    var starY3 by remember { mutableFloatStateOf(0f) }
    var muzzleFlash by remember { mutableIntStateOf(0) }
    var iFrames by remember { mutableIntStateOf(0) }
    var deathFrame by remember { mutableIntStateOf(0) }

    val bullets = remember { mutableListOf<Bullet>() }
    val enemies = remember { mutableListOf<Enemy>() }
    val powerups = remember { mutableListOf<PowerUp>() }
    val fx = remember { mutableListOf<Fx>() }
    var fireCd by remember { mutableIntStateOf(0) }
    var spawnCd by remember { mutableIntStateOf(0) }
    var wave by remember { mutableIntStateOf(1) }
    var spawned by remember { mutableIntStateOf(0) }

    fun hurtPlayer() {
        if (iFrames > 0) return
        if (shield > 0) {
            shield = 0
            iFrames = 45
            return
        }
        lives--
        iFrames = 60
        if (lives <= 0) {
            gameOver = true
            banner = "Überstimmt. Nochmal?"
            if (score > high) {
                high = score
                prefs.edit { putInt("highscore", score) }
            }
        }
    }

    LaunchedEffect(paused, gameOver, won) {
        while (!paused && !gameOver && !won) {
            delay(20)
            tick++
            val sw = w.coerceAtLeast(1f)
            val sh = h.coerceAtLeast(1f)
            val shipPx = shipX * sw
            val shipPy = shipY * sh

            starY1 = wrap(starY1 + 0.6f, sh)
            starY2 = wrap(starY2 + 1.2f, sh)
            starY3 = wrap(starY3 + 2.0f, sh)
            if (iFrames > 0) iFrames--
            // Endless X wrap; soft Y band so touch-fly never freezes far forward
            shipX = ((shipX % 1f) + 1f) % 1f
            shipY = shipY.coerceIn(0.50f, 0.92f)
            if (muzzleFlash > 0) muzzleFlash--

            // Velocity → facing (keep last angle when nearly still)
            if (!prevShipPx.isNaN()) {
                var vx = shipPx - prevShipPx
                val vy = shipPy - prevShipPy
                if (vx > sw * 0.5f) vx -= sw
                if (vx < -sw * 0.5f) vx += sw
                val spd = hypot(vx, vy)
                shipSpeed = spd
                if (spd > 1.2f) {
                    val target = Math.toDegrees(atan2(vy.toDouble(), vx.toDouble())).toFloat() + 90f
                    shipAngle = lerpAngleDeg(shipAngle, target, 0.28f)
                }
            }
            prevShipPx = shipPx
            prevShipPy = shipPy

            val angRad = shipAngle * (PI.toFloat() / 180f)
            val fdx = sin(angRad)   // tip forward (Compose rotate: 0=up)
            val fdy = -cos(angRad)

            // Auto-fire when enemy in forward sight cone along ship facing
            fun enemyInSight(e: Enemy): Boolean {
                var edx = e.x - shipPx
                if (edx > sw * 0.5f) edx -= sw
                if (edx < -sw * 0.5f) edx += sw
                val edy = e.y - shipPy
                val dist = hypot(edx, edy)
                if (dist < 12f || dist > sh * 0.55f) return false
                val dot = (edx * fdx + edy * fdy) / dist
                return dot > 0.50f
            }
            val hasSight = enemies.any { enemyInSight(it) }
            if (fireCd > 0) fireCd-- else if (hasSight) {
                fireCd = if (multishot > 0) 10 else 16
                muzzleFlash = 4
                sfx.shoot()
                val muzzle = if (speedBoost > 0) 11f else 9f
                val mouth = shipPxSize * 0.38f
                fun spawnShot(spreadDeg: Float) {
                    val r = (shipAngle + spreadDeg) * (PI.toFloat() / 180f)
                    val sx = sin(r)
                    val sy = -cos(r)
                    bullets += Bullet(
                        shipPx + sx * mouth,
                        shipPy + sy * mouth,
                        sy * muzzle,
                        true,
                        multishot > 0,
                        dx = sx * muzzle
                    )
                }
                if (multishot > 0) {
                    spawnShot(-14f)
                    spawnShot(0f)
                    spawnShot(14f)
                } else {
                    spawnShot(0f)
                }
            }
            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--

            // Waves: swarm (Divebomber/Störling) → mixed (+Panzer/Heuler) → elite+comet
            val maxSpawn = when (wave) { 1 -> 10; 2 -> 14; else -> 12 }
            if (spawnCd > 0) spawnCd-- else if (spawned < maxSpawn) {
                spawnCd = when (wave) { 1 -> 38; 2 -> 44; else -> 52 }
                val slot = spawned
                val kind = when (wave) {
                    1 -> if (slot % 3 == 0) EnemyKind.STORLING else EnemyKind.DIVEBOMBER
                    2 -> when (slot % 4) {
                        0 -> EnemyKind.DIVEBOMBER
                        1 -> EnemyKind.STORLING
                        2 -> EnemyKind.PANZERDROHNE
                        else -> EnemyKind.HEULER
                    }
                    else -> when {
                        slot == 0 || slot == 5 -> EnemyKind.COMET
                        slot == 3 || slot == 8 -> EnemyKind.PANZERDROHNE
                        slot % 3 == 0 -> EnemyKind.HEULER
                        slot % 2 == 0 -> EnemyKind.STORLING
                        else -> EnemyKind.DIVEBOMBER
                    }
                }
                val isComet = kind == EnemyKind.COMET
                val isPanzer = kind == EnemyKind.PANZERDROHNE
                val big = isPanzer && wave >= 3
                val formX = when (kind) {
                    EnemyKind.DIVEBOMBER -> {
                        val col = slot % 5
                        sw * (0.15f + col * 0.175f) + Random.nextFloat() * 12f - 6f
                    }
                    EnemyKind.STORLING -> sw * (0.2f + Random.nextFloat() * 0.6f)
                    EnemyKind.PANZERDROHNE -> sw * (0.2f + (slot % 4) * 0.2f)
                    EnemyKind.HEULER -> if (Random.nextBoolean()) 50f else sw - 50f
                    EnemyKind.COMET -> if (Random.nextBoolean()) -40f else sw + 40f
                }
                val formY = when (kind) {
                    EnemyKind.PANZERDROHNE -> -40f
                    EnemyKind.COMET -> Random.nextFloat() * (sh * 0.35f) + 40f
                    else -> -50f - (slot % 3) * 28f
                }
                enemies += Enemy(
                    x = formX,
                    y = formY,
                    hp = when {
                        isComet -> 2
                        isPanzer -> if (wave >= 3) 5 else 3
                        kind == EnemyKind.HEULER -> 2
                        wave >= 2 -> 2
                        else -> 1
                    },
                    dx = when (kind) {
                        EnemyKind.STORLING -> if (Random.nextBoolean()) 2.4f else -2.4f
                        EnemyKind.HEULER -> if (formX < sw * 0.5f) 1.6f else -1.6f
                        EnemyKind.COMET -> if (formX < 0f) 4.5f else -4.5f
                        EnemyKind.PANZERDROHNE -> if (Random.nextBoolean()) 0.7f else -0.7f
                        else -> Random.nextFloat() * 0.6f - 0.3f
                    },
                    dy = when (kind) {
                        EnemyKind.COMET -> 1.6f + Random.nextFloat()
                        EnemyKind.PANZERDROHNE -> 0.7f
                        EnemyKind.HEULER -> 0.85f
                        EnemyKind.STORLING -> 1.1f
                        else -> 1.45f + wave * 0.15f
                    },
                    fireCd = when {
                        isComet -> 9999
                        kind == EnemyKind.HEULER -> 35 + Random.nextInt(20)
                        else -> 50 + Random.nextInt(40)
                    },
                    big = big || (isPanzer && wave >= 2),
                    kind = kind,
                    phase = when (kind) {
                        EnemyKind.PANZERDROHNE -> 70 + slot * 6
                        EnemyKind.HEULER -> 55
                        EnemyKind.STORLING -> 0
                        else -> 0
                    },
                    holdY = sh * (0.18f + (slot % 3) * 0.08f),
                    comet = isComet
                )
                spawned++
            } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                if (wave < 3) {
                    wave++
                    spawned = 0
                    spawnCd = 55
                    banner = when (wave) {
                        2 -> "Gemischt: Störling & Heuler"
                        else -> "Panzerdrohne & Kometen"
                    }
                } else {
                    won = true
                    banner = "Orbit ruhig."
                    if (score > high) { high = score; prefs.edit { putInt("highscore", score) } }
                }
            }

            bullets.forEach {
                it.x += it.dx
                it.y += it.dy
            }
            // Archetype motion — leave downward / off-side; never screen-wrap as a gag
            enemies.forEach { e ->
                when (e.kind) {
                    EnemyKind.DIVEBOMBER -> {
                        e.y += e.dy + wave * 0.2f
                        e.x += e.dx
                    }
                    EnemyKind.STORLING -> {
                        // zigzag
                        e.y += e.dy + wave * 0.15f
                        e.x += e.dx
                        if (e.x < 36f || e.x > sw - 36f) e.dx = -e.dx
                        if (tick % 28 == 0) e.dx = -e.dx
                    }
                    EnemyKind.PANZERDROHNE -> {
                        if (e.y < e.holdY && e.phase > 0) {
                            e.y += e.dy
                        } else if (e.phase > 0) {
                            e.y = e.holdY
                            e.phase--
                            e.x += e.dx
                            if (e.x < 50f || e.x > sw - 50f) e.dx = -e.dx
                        } else {
                            e.y += e.dy + 1.4f + wave * 0.2f
                        }
                    }
                    EnemyKind.HEULER -> {
                        if (e.phase > 0) {
                            e.x += e.dx
                            e.y += e.dy * 0.4f
                            e.phase--
                            if (e.x < 30f) { e.x = 30f; e.dx = abs(e.dx); e.phase = 0 }
                            if (e.x > sw - 30f) { e.x = sw - 30f; e.dx = -abs(e.dx); e.phase = 0 }
                        } else {
                            e.y += e.dy + 1.1f + wave * 0.2f
                            e.x += e.dx * 0.3f
                        }
                    }
                    EnemyKind.COMET -> {
                        e.x += e.dx
                        e.y += e.dy
                    }
                }
                if (e.comet) return@forEach
                if (e.fireCd > 0) {
                    e.fireCd--
                } else {
                    when (e.kind) {
                        EnemyKind.HEULER -> {
                            // fan shots
                            e.fireCd = 55 - wave * 5
                            val base = 5.2f + wave * 0.35f
                            bullets += Bullet(e.x, e.y + 28f, base, false, dx = -2.2f)
                            bullets += Bullet(e.x, e.y + 28f, base + 0.4f, false, dx = 0f)
                            bullets += Bullet(e.x, e.y + 28f, base, false, dx = 2.2f)
                        }
                        else -> {
                            e.fireCd = 70 - wave * 8
                            bullets += Bullet(e.x, e.y + 30f, 5.5f + wave * 0.4f, false)
                        }
                    }
                }
            }
            powerups.forEach { it.y += 1.6f }
            fx.forEach { it.life-- }
            fx.removeAll { it.life <= 0 }
            bullets.removeAll { it.x < -60f || it.x > sw + 60f || it.y < -60f || it.y > sh + 60f }
            enemies.removeAll { e ->
                when {
                    e.comet && (e.x < -80f || e.x > sw + 80f || e.y > sh + 60f) -> true
                    e.y > sh + 50 -> { hurtPlayer(); true }
                    e.x < -100f || e.x > sw + 100f -> true // left the arena, no wrap re-entry
                    else -> false
                }
            }
            powerups.removeAll { it.y > sh + 20 }

            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    val r = if (e.big) bigPxSize * 0.4f else enemyPxSize * 0.4f
                    if (abs(b.x - e.x) < r && abs(b.y - e.y) < r) {
                        hitBullets += b
                        e.hp--
                        fx += Fx(e.x, e.y, 8, 0)
                        if (e.hp <= 0) {
                            hitEnemies += e
                            fx += Fx(e.x, e.y, 14, 1)
                            fx += Fx(e.x, e.y, 18, 2)
                            score += when { e.comet -> 30 * wave; e.big -> 40 * wave; e.kind == EnemyKind.HEULER -> 18 * wave; else -> 10 * wave }
                            sfx.hit()
                            if (Random.nextFloat() < 0.22f) powerups += PowerUp(e.x, e.y, Random.nextInt(3))
                        }
                    }
                }
            }
            bullets.removeAll { it in hitBullets }
            enemies.removeAll { it in hitEnemies }

            val enemyHits = mutableSetOf<Bullet>()
            for (b in bullets.filter { !it.fromPlayer }) {
                if (abs(b.x - shipPx) < shipPxSize * 0.28f && abs(b.y - shipPy) < shipPxSize * 0.28f) {
                    enemyHits += b
                    fx += Fx(shipPx, shipPy, 10, 0)
                    hurtPlayer()
                }
            }
            bullets.removeAll { it in enemyHits }

            enemies.toList().forEach { e ->
                val r = if (e.big) bigPxSize * 0.4f else enemyPxSize * 0.4f
                if (abs(e.x - shipPx) < r && abs(e.y - shipPy) < r) {
                    fx += Fx(e.x, e.y, 14, 1)
                    enemies.remove(e)
                    hurtPlayer()
                }
            }

            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (abs(p.x - shipPx) < 48 && abs(p.y - shipPy) < 48) {
                    got += p
                    sfx.pickup()
                    when (p.type) {
                        0 -> { multishot = 360; banner = "Mehrschuss" }
                        1 -> { shield = 360; banner = "Schild" }
                        2 -> { speedBoost = 360; banner = "Speed" }
                    }
                }
            }
            powerups.removeAll { it in got }
        }
    }

    LaunchedEffect(gameOver) {
        if (gameOver) {
            deathFrame = 0
            while (deathFrame < 4) {
                delay(120)
                deathFrame++
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(paused, gameOver, won) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (paused || gameOver || won) return@awaitEachGesture
                        val lerp = 0.55f
                        fun follow(pos: Offset) {
                            val tw = size.width.toFloat().coerceAtLeast(1f)
                            val th = size.height.toFloat().coerceAtLeast(1f)
                            var tx = pos.x / tw
                            // endless X: normalize into 0..1 (wrap), no hard wall
                            tx = ((tx % 1f) + 1f) % 1f
                            val ty = (pos.y / th).coerceIn(0.50f, 0.92f)
                            // short-path lerp on wrapped X
                            var dx = tx - shipX
                            if (dx > 0.5f) dx -= 1f
                            if (dx < -0.5f) dx += 1f
                            val dy = ty - shipY
                            val pxDx = dx * tw
                            val pxDy = dy * th
                            val move = hypot(pxDx, pxDy)
                            if (move > 2.5f) {
                                val target = Math.toDegrees(atan2(pxDy.toDouble(), pxDx.toDouble())).toFloat() + 90f
                                shipAngle = lerpAngleDeg(shipAngle, target, 0.42f)
                                shipSpeed = move
                            }
                            shipX = ((shipX + dx * lerp) % 1f + 1f) % 1f
                            shipY = shipY + dy * lerp
                        }
                        follow(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (!paused && !gameOver && !won) follow(change.position)
                            change.consume()
                        }
                    }
                }
        ) {
            w = size.width
            h = size.height
            // Deep space base — parallax stars only (no beam / zone art)
            drawRect(Color(0xFF050510))
            drawImg(starFar, 0f, starY1 - h, w, h)
            drawImg(starFar, 0f, starY1, w, h)
            drawImg(starMid, 0f, starY2 - h, w, h)
            drawImg(starMid, 0f, starY2, w, h)
            drawImg(starNear, 0f, starY3 - h, w, h)
            drawImg(starNear, 0f, starY3, w, h)

            val shipPx = shipX * w
            val shipPy = shipY * h
            val half = shipPxSize / 2f
            if (gameOver) {
                val d = when (deathFrame.coerceIn(0, 3)) {
                    0 -> death1
                    1 -> death2
                    2 -> death3
                    else -> death4
                }
                val ds = shipPxSize * 1.35f
                drawImg(d, shipPx - ds / 2f, shipPy - ds / 2f, ds, ds)
            } else if (iFrames == 0 || (tick / 3) % 2 == 0) {
                rotate(degrees = shipAngle, pivot = Offset(shipPx, shipPy)) {
                    // Thrust behind hull: mouth = bottom-center; nozzle ~size/10 from top of fx
                    if (shipSpeed > 1.5f) {
                        // ~12.5 fps at 50Hz tick (tick/4); loop 1→2→3→4
                        val flame = thrustFrames[(tick / 4) % 4]
                        val fw = shipPxSize * 0.42f
                        val fh = shipPxSize * 0.55f
                        val mouthY = shipPy + half  // bottom-center of ship bbox
                        val nozzleFromTop = fh / 10f
                        val thrustTop = mouthY - nozzleFromTop
                        drawImg(flame, shipPx - fw / 2f, thrustTop, fw, fh)
                    }
                    drawImg(shipHull, shipPx - half, shipPy - half, shipPxSize, shipPxSize)
                    if (muzzleFlash > 0) {
                        val m = if (muzzleFlash > 2) muzzle1 else muzzle2
                        val flash = 56f
                        // Tip / forward of side-profile (top of asset)
                        drawImg(m, shipPx - flash / 2f, shipPy - half - flash * 0.35f, flash, flash)
                    }
                }
            }
            if (shield > 0) {
                drawCircle(Color(0x554FC3F7), shipPxSize * 0.42f, Offset(shipPx, shipPy))
            }

            enemies.forEachIndexed { i, e ->
                when {
                    e.comet -> {
                        // Hazard placeholder — cyan streak until comet sprite arrives
                        val sp = if ((tick / 4) % 2 == 0) spark1 else spark2
                        val cw = enemyPxSize * 1.6f
                        val ch = enemyPxSize * 0.7f
                        drawCircle(Color(0x6600E5FF), cw * 0.55f, Offset(e.x, e.y))
                        drawImg(sp, e.x - cw / 2f, e.y - ch / 2f, cw, ch)
                    }
                    e.big || e.kind == EnemyKind.PANZERDROHNE -> {
                        val sz = if (e.big) bigPxSize else enemyPxSize * 1.15f
                        drawImg(enemyBig, e.x - sz / 2f, e.y - sz / 2f, sz, sz)
                    }
                    else -> {
                        val img = if ((tick / 12 + i) % 2 == 0) enemyImg else enemyImgB
                        drawImg(img, e.x - enemyPxSize / 2f, e.y - enemyPxSize / 2f, enemyPxSize, enemyPxSize)
                    }
                }
            }
            bullets.forEach { b ->
                if (b.fromPlayer) {
                    val img = if (b.triple) bulletTriple else bulletImg
                    drawImg(img, b.x - bulletW / 2f, b.y - bulletH / 2f, bulletW, bulletH)
                } else {
                    drawImg(bulletEnemy, b.x - bulletW / 2f, b.y - bulletH / 2f, bulletW, bulletH * 0.85f)
                }
            }
            powerups.forEach { p ->
                val ring = when (p.type) {
                    0 -> Color(0xFFFF5252) // multishot — red-ish
                    1 -> Color(0xFF69F0AE) // shield/heal — green
                    else -> Color(0xFF00E5FF) // speed — cyan
                }
                // Soft outer glow + crisp mode-badge rim (not identity-only soft circles)
                drawCircle(ring.copy(alpha = 0.22f), 50f, Offset(p.x, p.y))
                drawCircle(
                    color = ring.copy(alpha = 0.85f),
                    radius = 42f,
                    center = Offset(p.x, p.y),
                    style = Stroke(width = 5f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = 38f,
                    center = Offset(p.x, p.y),
                    style = Stroke(width = 1.5f)
                )
                val img = when (p.type) { 0 -> puWeapon; 1 -> puHeal; else -> puSpeed }
                // Mode icons are 64px; speed keeps soft powerup art
                val icon = if (p.type == 2) 72f else 56f
                drawImg(img, p.x - icon / 2f, p.y - icon / 2f, icon, icon)
            }
            fx.forEach { f ->
                val img = when (f.kind) { 0 -> boom1; 1 -> boom2; else -> boom3 }
                val s = 96f + (20 - f.life) * 6f
                drawImg(img, f.x - s / 2, f.y - s / 2, s, s)
            }
            repeat(lives.coerceAtLeast(0)) { i ->
                drawImg(heartImg, 12f + i * 36f, 12f, 32f, 32f)
            }
        }

        Text(
            "Score $score  Hi $high  W$wave",
            color = Color(0xFFF2E6D0),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        )
        Text(
            banner,
            color = Color(0xFFC9A66B),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        )
        TextButton(onClick = { paused = !paused }, modifier = Modifier.align(Alignment.TopStart).padding(top = 44.dp)) {
            Text(if (paused) "Weiter" else "Pause", color = Color.White)
        }
        if (paused) {
            Text("Pause", color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (gameOver || won) {
            TextButton(onClick = onExit, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                Text(if (won) "Menü" else "Nochmal / Menü", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun DrawScope.drawImg(img: ImageBitmap, x: Float, y: Float, dw: Float, dh: Float) {
    drawImage(
        image = img,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.None,
        blendMode = BlendMode.SrcOver
    )
}
