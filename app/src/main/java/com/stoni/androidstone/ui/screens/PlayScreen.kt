package com.stoni.androidstone.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlin.math.*
import kotlin.random.Random

/** Large fixed portrait map (no wrap — ship clamps at edges). */
private const val WORLD_W = 8000f
private const val WORLD_H = 12000f

private enum class EnemyType(
    val baseRadius: Float,
    val speed: Float,
    val maxHp: Int,
    val color: Color,
    val scoreValue: Int
) {
    SWARMER(14f, 4.5f, 1, Color(0xFF00FF9D), 10),
    SCOUT(22f, 3.0f, 3, Color(0xFFFF5252), 25),
    TANK(38f, 1.4f, 10, Color(0xFFFF9100), 75),
    BOSS(65f, 0.9f, 60, Color(0xFFE040FB), 500)
}

private data class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val angle: Float,
    val fromPlayer: Boolean,
    val triple: Boolean = false
)

private data class Enemy(
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    var fireCd: Int = 60,
    val type: EnemyType,
    var angle: Float = 0f
)

private data class PowerUp(var x: Float, var y: Float, val type: Int)
private data class Fx(var x: Float, var y: Float, var life: Int, val kind: Int)

private class GameSfx(context: Context) {
    private val pool: SoundPool
    private val shootId: Int
    private val hitId: Int
    private val pickupId: Int

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
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

    fun release() { pool.release() }
}

@Composable
fun PlayScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val shipPxSize = with(density) { 68.dp.toPx() }
    val enemyPxSize = with(density) { 48.dp.toPx() }
    val tankPxSize = with(density) { 96.dp.toPx() }
    val bossPxSize = with(density) { 140.dp.toPx() }
    val bulletW = with(density) { 14.dp.toPx() }
    val bulletH = with(density) { 26.dp.toPx() }
    val prefs = remember { context.getSharedPreferences("stargame", Context.MODE_PRIVATE) }
    var high by remember { mutableIntStateOf(prefs.getInt("highscore", 0)) }

    val sfx = remember { GameSfx(context) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    val shipSingle = remember { loadStargameAsset(context, "player_glocke_single_128.png") }
    val shipTriple = remember { loadStargameAsset(context, "player_glocke_triple_128.png") }
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

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }

    var shipPx by remember { mutableFloatStateOf(WORLD_W * 0.5f) }
    var shipPy by remember { mutableFloatStateOf(WORLD_H * 0.5f) }
    var shipVx by remember { mutableFloatStateOf(0f) }
    var shipVy by remember { mutableFloatStateOf(0f) }
    var shipAngle by remember { mutableFloatStateOf(0f) }
    var fingerX by remember { mutableFloatStateOf(540f) }
    var fingerY by remember { mutableFloatStateOf(700f) }
    var isTouching by remember { mutableStateOf(false) }
    var camX by remember { mutableFloatStateOf(WORLD_W * 0.5f) }
    var camY by remember { mutableFloatStateOf(WORLD_H * 0.5f) }

    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(4) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("Feindflotte gesichtet! Abfangen!") }
    var multishot by remember { mutableIntStateOf(0) }
    var shield by remember { mutableIntStateOf(0) }
    var speedBoost by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    var bgOffsetX by remember { mutableFloatStateOf(0f) }
    var bgOffsetY by remember { mutableFloatStateOf(0f) }

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
    var isBossActive by remember { mutableStateOf(false) }
    var bossHpCurrent by remember { mutableFloatStateOf(0f) }
    var bossHpMax by remember { mutableFloatStateOf(1f) }
    var awaitingBoss by remember { mutableStateOf(false) }

    fun hurtPlayer() {
        if (iFrames > 0) return
        if (shield > 0) {
            shield = 0
            iFrames = 50
            return
        }
        lives--
        iFrames = 75
        if (lives <= 0) {
            gameOver = true
            banner = "Glocke zerstört. Nochmal?"
            if (score > high) {
                high = score
                prefs.edit { putInt("highscore", score) }
            }
        }
    }

    fun enemyHitRadius(type: EnemyType): Float = when (type) {
        EnemyType.SWARMER -> type.baseRadius * 1.2f
        EnemyType.SCOUT -> enemyPxSize * 0.45f
        EnemyType.TANK -> tankPxSize * 0.45f
        EnemyType.BOSS -> bossPxSize * 0.45f
    }

    fun clampWorld(x: Float, y: Float, pad: Float = 40f): Pair<Float, Float> =
        x.coerceIn(pad, WORLD_W - pad) to y.coerceIn(pad, WORLD_H - pad)

    LaunchedEffect(w, h) {
        if (w > 10f && h > 10f && fingerX == 540f && fingerY == 700f) {
            fingerX = w / 2f
            fingerY = h / 2f - 100f
        }
    }

    LaunchedEffect(paused, gameOver) {
        while (!paused && !gameOver) {
            delay(20)
            tick++
            val sw = w.coerceAtLeast(1f)
            val sh = h.coerceAtLeast(1f)

            if (iFrames > 0) iFrames--
            if (muzzleFlash > 0) muzzleFlash--

            if (isTouching) {
                val shipSx = shipPx - camX + sw / 2f
                val shipSy = shipPy - camY + sh / 2f
                val dx = fingerX - shipSx
                val dy = fingerY - shipSy
                val dist = hypot(dx, dy)

                if (dist > 15f) {
                    val targetAngle = (atan2(dy, dx) * 180.0 / PI).toFloat() + 90f
                    var diff = (targetAngle - shipAngle) % 360f
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    shipAngle += diff * 0.22f

                    val chaseSpeed = if (speedBoost > 0) 1.3f else 0.95f
                    shipVx += (dx / dist) * chaseSpeed
                    shipVy += (dy / dist) * chaseSpeed
                }
            }

            val friction = 0.92f
            shipVx *= friction
            shipVy *= friction
            val maxSpd = if (speedBoost > 0) 18f else 13f
            val currentSpd = hypot(shipVx, shipVy)
            if (currentSpd > maxSpd) {
                shipVx = (shipVx / currentSpd) * maxSpd
                shipVy = (shipVy / currentSpd) * maxSpd
            }

            shipPx += shipVx
            shipPy += shipVy

            // Soft bounce at fixed world edges (no wrap)
            val edgePad = shipPxSize * 0.4f
            if (shipPx < edgePad) { shipPx = edgePad; shipVx = abs(shipVx) * 0.35f }
            if (shipPx > WORLD_W - edgePad) { shipPx = WORLD_W - edgePad; shipVx = -abs(shipVx) * 0.35f }
            if (shipPy < edgePad) { shipPy = edgePad; shipVy = abs(shipVy) * 0.35f }
            if (shipPy > WORLD_H - edgePad) { shipPy = WORLD_H - edgePad; shipVy = -abs(shipVy) * 0.35f }

            // Soft camera margins (bafbb47 feel); camera stays inside map
            val marginX = sw * 0.22f
            val marginY = sh * 0.22f
            val targetCamX = when {
                shipPx < camX - marginX -> shipPx + marginX
                shipPx > camX + marginX -> shipPx - marginX
                else -> camX
            }
            val targetCamY = when {
                shipPy < camY - marginY -> shipPy + marginY
                shipPy > camY + marginY -> shipPy - marginY
                else -> camY
            }
            camX += (targetCamX - camX) * 0.12f
            camY += (targetCamY - camY) * 0.12f
            // Keep camera from showing past map edges when possible
            camX = camX.coerceIn(sw / 2f, (WORLD_W - sw / 2f).coerceAtLeast(sw / 2f))
            camY = camY.coerceIn(sh / 2f, (WORLD_H - sh / 2f).coerceAtLeast(sh / 2f))

            bgOffsetX = -camX
            bgOffsetY = -camY

            if (isTouching) {
                if (fireCd > 0) {
                    fireCd--
                } else {
                    fireCd = if (multishot > 0) 8 else 13
                    muzzleFlash = 3
                    sfx.shoot()
                    val bSpeed = if (speedBoost > 0) 24f else 20f
                    val shootRad = (shipAngle - 90f) * PI / 180.0
                    val bvx = (cos(shootRad) * bSpeed).toFloat()
                    val bvy = (sin(shootRad) * bSpeed).toFloat()

                    val noseDist = shipPxSize * 0.44f
                    val mx = shipPx + (cos(shootRad) * noseDist).toFloat()
                    val my = shipPy + (sin(shootRad) * noseDist).toFloat()

                    if (multishot > 0) {
                        val sp1 = (shipAngle - 90f - 12f) * PI / 180.0
                        val sp2 = (shipAngle - 90f + 12f) * PI / 180.0
                        bullets += Bullet(mx, my, bvx, bvy, shipAngle, true, true)
                        bullets += Bullet(mx, my, (cos(sp1) * bSpeed).toFloat(), (sin(sp1) * bSpeed).toFloat(), shipAngle - 12f, true, true)
                        bullets += Bullet(mx, my, (cos(sp2) * bSpeed).toFloat(), (sin(sp2) * bSpeed).toFloat(), shipAngle + 12f, true, true)
                    } else {
                        bullets += Bullet(mx, my, bvx, bvy, shipAngle, true)
                    }
                }
            } else {
                if (fireCd > 0) fireCd--
            }

            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--

            val enemiesPerWave = 10 + wave * 5
            if (awaitingBoss && enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                awaitingBoss = false
                isBossActive = true
                val bossHp = 45 + wave * 25
                bossHpMax = bossHp.toFloat()
                bossHpCurrent = bossHp.toFloat()
                banner = "⚠ SECTOR BOSS ⚠"
                val spawnAngle = Random.nextFloat() * 2f * PI.toFloat()
                val spawnDist = max(sw, sh) * 0.85f + 120f
                val (bx, by) = clampWorld(
                    shipPx + cos(spawnAngle) * spawnDist,
                    shipPy + sin(spawnAngle) * spawnDist,
                    80f
                )
                enemies += Enemy(
                    x = bx, y = by,
                    hp = bossHp, maxHp = bossHp,
                    fireCd = 40, type = EnemyType.BOSS
                )
            } else if (!isBossActive && !awaitingBoss) {
                if (spawnCd > 0) spawnCd-- else if (spawned < enemiesPerWave) {
                    spawnCd = max(18, 60 - wave * 4)
                    val spawnAngle = Random.nextFloat() * 2f * PI.toFloat()
                    val spawnDist = max(sw, sh) * 0.75f + 80f
                    val (ex, ey) = clampWorld(
                        shipPx + cos(spawnAngle) * spawnDist,
                        shipPy + sin(spawnAngle) * spawnDist,
                        60f
                    )

                    val type = when {
                        wave >= 2 && Random.nextFloat() < 0.20f -> EnemyType.TANK
                        wave >= 2 && Random.nextFloat() < 0.35f -> EnemyType.SWARMER
                        else -> EnemyType.SCOUT
                    }

                    enemies += Enemy(
                        x = ex, y = ey,
                        hp = type.maxHp + (wave - 1) / 2,
                        maxHp = type.maxHp + (wave - 1) / 2,
                        fireCd = 45 + Random.nextInt(35),
                        type = type
                    )
                    spawned++
                } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                    if (wave % 3 == 0) {
                        awaitingBoss = true
                        banner = "Boss-Signatur erkannt…"
                    } else {
                        wave++
                        spawned = 0
                        spawnCd = 50
                        banner = "Welle $wave!"
                    }
                }
            }

            enemies.forEach { e ->
                val edx = shipPx - e.x
                val edy = shipPy - e.y
                val dist = hypot(edx, edy)

                e.angle = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f

                val eSpeed = e.type.speed + wave * 0.08f
                if (dist > 12f) {
                    e.x += (edx / dist) * eSpeed
                    e.y += (edy / dist) * eSpeed
                }
                val clamped = clampWorld(e.x, e.y, 30f)
                e.x = clamped.first
                e.y = clamped.second

                if (e.type != EnemyType.SWARMER) {
                    if (e.fireCd > 0) e.fireCd-- else {
                        e.fireCd = when (e.type) {
                            EnemyType.SCOUT -> 90 - wave * 3
                            EnemyType.TANK -> 70 - wave * 2
                            EnemyType.BOSS -> 35
                            else -> 100
                        }.coerceAtLeast(20)

                        if (dist > 15f) {
                            val ebSpeed = when (e.type) {
                                EnemyType.BOSS -> 14f + wave * 0.4f
                                EnemyType.TANK -> 10f + wave * 0.3f
                                else -> 11.5f + wave * 0.4f
                            }
                            val ebvx = (edx / dist) * ebSpeed
                            val ebvy = (edy / dist) * ebSpeed

                            if (e.type == EnemyType.BOSS || e.type == EnemyType.TANK) {
                                val sp1 = (e.angle - 90f - 18f) * PI / 180.0
                                val sp2 = (e.angle - 90f + 18f) * PI / 180.0
                                bullets += Bullet(e.x, e.y, ebvx, ebvy, e.angle, false)
                                bullets += Bullet(e.x, e.y, (cos(sp1) * ebSpeed).toFloat(), (sin(sp1) * ebSpeed).toFloat(), e.angle - 18f, false)
                                bullets += Bullet(e.x, e.y, (cos(sp2) * ebSpeed).toFloat(), (sin(sp2) * ebSpeed).toFloat(), e.angle + 18f, false)
                            } else {
                                bullets += Bullet(e.x, e.y, ebvx, ebvy, e.angle, false)
                            }
                        }
                    }
                }
            }

            bullets.forEach { it.x += it.vx; it.y += it.vy }
            powerups.forEach { it.y += 0.35f }
            fx.forEach { it.life-- }
            fx.removeAll { it.life <= 0 }

            // Despawn by distance from ship (world space), not screen sw/sh
            val bulletMaxDist = max(sw, sh) * 1.6f + 200f
            bullets.removeAll { hypot(it.x - shipPx, it.y - shipPy) > bulletMaxDist }
            powerups.removeAll { hypot(it.x - shipPx, it.y - shipPy) > bulletMaxDist * 1.2f }

            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    val r = enemyHitRadius(e.type)
                    if (hypot(b.x - e.x, b.y - e.y) < r) {
                        hitBullets += b
                        e.hp--
                        fx += Fx(e.x, e.y, 8, 0)
                        if (e.type == EnemyType.BOSS) {
                            bossHpCurrent = e.hp.toFloat().coerceAtLeast(0f)
                        }
                        if (e.hp <= 0) {
                            hitEnemies += e
                            fx += Fx(e.x, e.y, 14, 1)
                            fx += Fx(e.x, e.y, 18, 2)
                            score += e.type.scoreValue * wave
                            sfx.hit()
                            if (e.type == EnemyType.BOSS) {
                                isBossActive = false
                                wave++
                                spawned = 0
                                spawnCd = 60
                                banner = "Boss vernichtet! Welle $wave"
                                if (score > high) {
                                    high = score
                                    prefs.edit { putInt("highscore", score) }
                                }
                            } else if (Random.nextFloat() < 0.30f) {
                                powerups += PowerUp(e.x, e.y, Random.nextInt(3))
                            }
                        }
                    }
                }
            }
            bullets.removeAll { it in hitBullets }
            enemies.removeAll { it in hitEnemies }

            val enemyHits = mutableSetOf<Bullet>()
            for (b in bullets.filter { !it.fromPlayer }) {
                if (hypot(b.x - shipPx, b.y - shipPy) < shipPxSize * 0.32f) {
                    enemyHits += b
                    fx += Fx(shipPx, shipPy, 10, 0)
                    hurtPlayer()
                }
            }
            bullets.removeAll { it in enemyHits }

            enemies.toList().forEach { e ->
                val r = enemyHitRadius(e.type)
                if (hypot(e.x - shipPx, e.y - shipPy) < (r + shipPxSize * 0.30f)) {
                    fx += Fx(e.x, e.y, 14, 1)
                    if (e.type != EnemyType.BOSS) {
                        enemies.remove(e)
                    }
                    hurtPlayer()
                }
            }

            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (hypot(p.x - shipPx, p.y - shipPy) < 48f) {
                    got += p
                    sfx.pickup()
                    when (p.type) {
                        0 -> { multishot = 420; banner = "360° Mehrschuss!" }
                        1 -> { shield = 420; banner = "Glocken-Schutzschild!" }
                        2 -> { speedBoost = 420; banner = "Hyper-Schub!" }
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

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        fingerX = offset.x
                        fingerY = offset.y
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        isTouching = true
                        fingerX = change.position.x
                        fingerY = change.position.y
                    },
                    onDragEnd = { isTouching = false },
                    onDragCancel = { isTouching = false }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            w = size.width
            h = size.height

            drawRect(Color(0xFF050510))
            val toSx = { wx: Float -> wx - camX + size.width / 2f }
            val toSy = { wy: Float -> wy - camY + size.height / 2f }

            drawMirroredTiled(starFar, bgOffsetX * 0.25f, bgOffsetY * 0.25f, w, h)
            drawMirroredTiled(starMid, bgOffsetX * 0.50f, bgOffsetY * 0.50f, w, h)
            drawMirroredTiled(starNear, bgOffsetX * 0.90f, bgOffsetY * 0.90f, w, h)

            val half = shipPxSize / 2f

            enemies.forEachIndexed { i, e ->
                val sx = toSx(e.x)
                val sy = toSy(e.y)
                rotate(degrees = e.angle, pivot = Offset(sx, sy)) {
                    when (e.type) {
                        EnemyType.SWARMER -> {
                            val r = e.type.baseRadius * 1.35f
                            val path = Path().apply {
                                moveTo(sx, sy - r)
                                lineTo(sx + r, sy)
                                lineTo(sx, sy + r)
                                lineTo(sx - r, sy)
                                close()
                            }
                            drawPath(path, e.type.color)
                            drawPath(path, Color.White.copy(alpha = 0.35f), style = Stroke(width = 2f))
                        }
                        EnemyType.SCOUT -> {
                            val img = if ((tick / 12 + i) % 2 == 0) enemyImg else enemyImgB
                            drawImg(img, sx - enemyPxSize / 2f, sy - enemyPxSize / 2f, enemyPxSize, enemyPxSize)
                        }
                        EnemyType.TANK -> {
                            drawImg(enemyBig, sx - tankPxSize / 2f, sy - tankPxSize / 2f, tankPxSize, tankPxSize)
                            drawCircle(
                                e.type.color.copy(alpha = 0.35f),
                                tankPxSize * 0.42f,
                                Offset(sx, sy),
                                style = Stroke(width = 3f)
                            )
                        }
                        EnemyType.BOSS -> {
                            drawImg(enemyBig, sx - bossPxSize / 2f, sy - bossPxSize / 2f, bossPxSize, bossPxSize)
                            drawCircle(
                                e.type.color.copy(alpha = 0.55f),
                                bossPxSize * 0.48f,
                                Offset(sx, sy),
                                style = Stroke(width = 4f)
                            )
                            drawCircle(Color.White.copy(alpha = 0.4f), bossPxSize * 0.12f, Offset(sx, sy))
                        }
                    }
                }
                if (e.type != EnemyType.SWARMER && e.hp < e.maxHp) {
                    val barW = when (e.type) {
                        EnemyType.BOSS -> bossPxSize
                        EnemyType.TANK -> tankPxSize
                        else -> enemyPxSize
                    }
                    val top = sy - barW * 0.55f - 10f
                    drawRect(Color(0xFF333333), topLeft = Offset(sx - barW / 2f, top), size = Size(barW, 5f))
                    drawRect(
                        e.type.color,
                        topLeft = Offset(sx - barW / 2f, top),
                        size = Size(barW * (e.hp.toFloat() / e.maxHp.coerceAtLeast(1)), 5f)
                    )
                }
            }

            bullets.forEach { b ->
                val bx = toSx(b.x)
                val by = toSy(b.y)
                rotate(degrees = b.angle, pivot = Offset(bx, by)) {
                    if (b.fromPlayer) {
                        val img = if (b.triple) bulletTriple else bulletImg
                        drawImg(img, bx - bulletW / 2f, by - bulletH / 2f, bulletW, bulletH)
                    } else {
                        drawImg(bulletEnemy, bx - bulletW / 2f, by - bulletH / 2f, bulletW, bulletH * 0.85f)
                    }
                }
            }

            powerups.forEach { p ->
                val px = toSx(p.x)
                val py = toSy(p.y)
                val ring = when (p.type) {
                    0 -> Color(0xFFFF5252)
                    1 -> Color(0xFF69F0AE)
                    else -> Color(0xFF00E5FF)
                }
                drawCircle(ring.copy(alpha = 0.22f), 38f, Offset(px, py))
                drawCircle(color = ring.copy(alpha = 0.85f), radius = 32f, center = Offset(px, py), style = Stroke(width = 4f))
                val img = when (p.type) { 0 -> puWeapon; 1 -> puHeal; else -> puSpeed }
                val icon = if (p.type == 2) 48f else 38f
                drawImg(img, px - icon / 2f, py - icon / 2f, icon, icon)
            }

            fx.forEach { f ->
                val fxX = toSx(f.x)
                val fxY = toSy(f.y)
                val img = when (f.kind) { 0 -> boom1; 1 -> boom2; else -> boom3 }
                val s = 72f + (20 - f.life) * 5f
                drawImg(img, fxX - s / 2, fxY - s / 2, s, s)
            }

            val shipSx = toSx(shipPx)
            val shipSy = toSy(shipPy)

            if (gameOver) {
                val d = when (deathFrame.coerceIn(0, 3)) {
                    0 -> death1
                    1 -> death2
                    2 -> death3
                    else -> death4
                }
                val ds = shipPxSize * 1.35f
                drawImg(d, shipSx - ds / 2f, shipSy - ds / 2f, ds, ds)
            } else if (iFrames == 0 || (tick / 3) % 2 == 0) {
                rotate(degrees = shipAngle, pivot = Offset(shipSx, shipSy)) {
                    val ship = if (multishot > 0) shipTriple else shipSingle
                    drawImg(ship, shipSx - half, shipSy - half, shipPxSize, shipPxSize)

                    if (muzzleFlash > 0) {
                        val m = if (muzzleFlash > 2) muzzle1 else muzzle2
                        val flash = 42f
                        val mouthY = shipSy - half - flash * 0.55f
                        if (multishot > 0) {
                            val offsets = floatArrayOf(-20f, 0f, 20f)
                            for (ox in offsets) {
                                drawImg(m, shipSx + ox - flash / 2f, mouthY, flash, flash)
                            }
                        } else {
                            drawImg(m, shipSx - flash / 2f, mouthY, flash, flash)
                        }
                    }
                }
            }

            if (shield > 0) {
                drawCircle(Color(0x554FC3F7), shipPxSize * 0.6f, Offset(shipSx, shipSy))
            }

            repeat(lives.coerceAtLeast(0)) { i ->
                drawImg(heartImg, 12f + i * 32f, 12f, 28f, 28f)
            }

            if (isBossActive) {
                val barW = size.width * 0.7f
                val barX = (size.width - barW) / 2f
                val barY = 86f
                drawRect(Color(0xFF35123D), topLeft = Offset(barX, barY), size = Size(barW, 12f))
                val frac = (bossHpCurrent / bossHpMax.coerceAtLeast(1f)).coerceIn(0f, 1f)
                drawRect(Color(0xFFE040FB), topLeft = Offset(barX, barY), size = Size(barW * frac, 12f))
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
        if (gameOver) {
            TextButton(onClick = onExit, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                Text("Nochmal / Menü", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun DrawScope.drawMirroredTiled(img: ImageBitmap, offX: Float, offY: Float, sw: Float, sh: Float) {
    val tw = sw
    val th = sh
    val startCol = floor(-offX / tw).toInt() - 1
    val endCol = ceil((sw - offX) / tw).toInt() + 1
    val startRow = floor(-offY / th).toInt() - 1
    val endRow = ceil((sh - offY) / th).toInt() + 1

    for (col in startCol..endCol) {
        for (row in startRow..endRow) {
            val posX = offX + col * tw
            val posY = offY + row * th
            val flipX = if (col % 2 != 0) -1f else 1f
            val flipY = if (row % 2 != 0) -1f else 1f

            scale(scaleX = flipX, scaleY = flipY, pivot = Offset(posX + tw / 2f, posY + th / 2f)) {
                drawImg(img, posX, posY, tw, th)
            }
        }
    }
}

private fun DrawScope.drawImg(img: ImageBitmap, x: Float, y: Float, dw: Float, dh: Float) {
    drawImage(
        image = img,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(dw.toInt(), dh.toInt()),
        filterQuality = FilterQuality.Low
    )
}
