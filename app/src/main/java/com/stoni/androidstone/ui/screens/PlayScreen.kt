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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.stoni.androidstone.game.loadStargameAssetOrNull
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/** Large portrait map with toroidal wrap (no walls). */
private const val WORLD_W = 8000f
private const val WORLD_H = 12000f

private fun wrapCoord(v: Float, size: Float): Float = ((v % size) + size) % size

/** Shortest signed delta on a toroidal axis. */
private fun wrapDelta(d: Float, size: Float): Float {
    var v = ((d % size) + size) % size
    if (v > size * 0.5f) v -= size
    return v
}

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
    BOSS(65f, 0.9f, 60, Color(0xFFE040FB), 500),
    LANG(20f, 2.8f, 4, Color(0xFF82B1FF), 35),
    RUND(28f, 2.0f, 6, Color(0xFFFF80AB), 45),
    KOMET(22f, 3.2f, 2, Color(0xFFFFAB40), 20),
    KOMET_BIG(65f, 0.75f, 18, Color(0xFFFF6D00), 400),
    ASTEROID(24f, 2.2f, 3, Color(0xFFBCAAA4), 15),
    FELS(40f, 1.1f, 12, Color(0xFF8D6E63), 80),
    JAEGER(22f, 3.4f, 3, Color(0xFF40C4FF), 30),
    MINE(18f, 1.6f, 1, Color(0xFFFF1744), 35)
}

private fun EnemyType.isBossLike(): Boolean =
    this == EnemyType.BOSS || this == EnemyType.KOMET_BIG

private fun EnemyType.canShoot(): Boolean = when (this) {
    EnemyType.SCOUT, EnemyType.TANK, EnemyType.BOSS, EnemyType.JAEGER,
    EnemyType.LANG, EnemyType.RUND -> true
    else -> false
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
    val swarmerPxSize = with(density) { 40.dp.toPx() }
    val enemyLangW = with(density) { 46.dp.toPx() }
    val enemyLangH = with(density) { 72.dp.toPx() }
    val enemyRundSize = with(density) { 64.dp.toPx() }
    val tankPxSize = with(density) { 96.dp.toPx() }
    val bossPxSize = with(density) { 140.dp.toPx() }
    val bulletW = with(density) { 14.dp.toPx() }
    val bulletH = with(density) { 26.dp.toPx() }
    val prefs = remember { context.getSharedPreferences("stargame", Context.MODE_PRIVATE) }
    var high by remember { mutableIntStateOf(prefs.getInt("highscore", 0)) }

    val sfx = remember { GameSfx(context) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    val shipSingle = remember { loadStargameAsset(context, "player_glocke_single_128.png") }
    val enemyImg = remember { loadStargameAsset(context, "enemy_stoerer_64.png") }
    val enemyImgB = remember { loadStargameAsset(context, "enemy_stoerer_b_64.png") }
    val enemySwarmer = remember { loadStargameAsset(context, "enemy_stoerer_48.png") }
    val enemyTank = remember { loadStargameAsset(context, "enemy_stoerer_big_96.png") }
    val enemyBig = remember { loadStargameAsset(context, "enemy_stoerer_big_128.png") }
    val schiffLang = remember { loadStargameAsset(context, "schiff_lang.png") }
    val schiffLangLicht = remember { loadStargameAsset(context, "schiff_lang_licht.png") }
    val schiffRund = remember { loadStargameAsset(context, "schiff_rund.png") }
    val schiffRundLicht = remember { loadStargameAsset(context, "schiff_rund_licht.png") }
    val thrustFrames = remember {
        listOf(
            loadStargameAsset(context, "fx_thrust_1_48.png"),
            loadStargameAsset(context, "fx_thrust_2_48.png"),
            loadStargameAsset(context, "fx_thrust_3_48.png"),
            loadStargameAsset(context, "fx_thrust_4_48.png"),
        )
    }

    val bulletImg = remember { loadStargameAsset(context, "bullet_player.png") }
    val bulletTriple = remember { loadStargameAsset(context, "bullet_player_triple.png") }
    val bulletEnemy = remember { loadStargameAsset(context, "bullet_enemy.png") }
    val starFar = remember { loadStargameAsset(context, "bg_stars_far.png") }
    val starMid = remember { loadStargameAsset(context, "bg_stars_mid.png") }
    val starNear = remember { loadStargameAsset(context, "bg_stars_near.png") }
    val bgNebula = remember { loadStargameAssetOrNull(context, "bg_nebula.png") }
    val bgDebris = remember { loadStargameAssetOrNull(context, "bg_debris.png") }
    val kometImg = remember { loadStargameAssetOrNull(context, "enemy_komet_64.png") }
    val kometLicht = remember { loadStargameAssetOrNull(context, "enemy_komet_licht_64.png") }
    val kometImg128 = remember { loadStargameAssetOrNull(context, "enemy_komet_128.png") }
    val kometLicht128 = remember { loadStargameAssetOrNull(context, "enemy_komet_licht_128.png") }
    val kometBigImg = remember { loadStargameAssetOrNull(context, "enemy_komet_big_128.png") }
    val asteroidImg = remember { loadStargameAssetOrNull(context, "enemy_asteroid_64.png") }
    val asteroidImgB = remember { loadStargameAssetOrNull(context, "enemy_asteroid_b_64.png") }
    val asteroidImg80 = remember { loadStargameAssetOrNull(context, "enemy_asteroid_80.png") }
    val felsImg = remember { loadStargameAssetOrNull(context, "enemy_asteroid_big_128.png") }
    val jaegerImg = remember { loadStargameAssetOrNull(context, "enemy_jaeger_64.png") }
    val jaegerLicht = remember { loadStargameAssetOrNull(context, "enemy_jaeger_licht_64.png") }
    val jaegerImg80 = remember { loadStargameAssetOrNull(context, "enemy_jaeger_80.png") }
    val mineImg = remember { loadStargameAssetOrNull(context, "enemy_mine_64.png") }
    val mineLicht = remember { loadStargameAssetOrNull(context, "enemy_mine_licht_64.png") }
    val mineImg80 = remember { loadStargameAssetOrNull(context, "enemy_mine_80.png") }
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
    val maxLives = 4
    var energy by remember { mutableFloatStateOf(100f) }
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
            energy = (energy - 20f).coerceAtLeast(15f)
            iFrames = 50
            return
        }
        lives--
        energy = (energy - 35f).coerceAtLeast(0f)
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
        EnemyType.SWARMER -> swarmerPxSize * 0.45f
        EnemyType.SCOUT, EnemyType.JAEGER, EnemyType.KOMET, EnemyType.ASTEROID, EnemyType.MINE ->
            enemyPxSize * 0.45f
        EnemyType.LANG -> max(enemyLangW, enemyLangH) * 0.42f
        EnemyType.RUND -> enemyRundSize * 0.45f
        EnemyType.TANK, EnemyType.FELS -> tankPxSize * 0.45f
        EnemyType.BOSS, EnemyType.KOMET_BIG -> bossPxSize * 0.45f
    }

    fun wrapWorld(x: Float, y: Float): Pair<Float, Float> =
        wrapCoord(x, WORLD_W) to wrapCoord(y, WORLD_H)

    fun wrapDx(ax: Float, bx: Float): Float = wrapDelta(ax - bx, WORLD_W)
    fun wrapDy(ay: Float, by: Float): Float = wrapDelta(ay - by, WORLD_H)
    fun wrapDist(ax: Float, ay: Float, bx: Float, by: Float): Float =
        hypot(wrapDx(ax, bx), wrapDy(ay, by))

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
            if (energy < 100f) energy = (energy + 0.22f).coerceAtMost(100f)

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

            // Toroidal wrap — leave left, appear right (no walls / bounce)
            val prevShipX = shipPx
            val prevShipY = shipPy
            shipPx = wrapCoord(shipPx, WORLD_W)
            shipPy = wrapCoord(shipPy, WORLD_H)
            val shipWrapped = shipPx != prevShipX || shipPy != prevShipY

            // Soft camera margins; on wrap snap cam to ship to avoid jump artifact
            if (shipWrapped) {
                camX = shipPx
                camY = shipPy
            } else {
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
                camX = wrapCoord(camX, WORLD_W)
                camY = wrapCoord(camY, WORLD_H)
            }

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

            val enemiesPerWave = 16 + wave * 6
            if (awaitingBoss && enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                awaitingBoss = false
                isBossActive = true
                val bossHp = 60 + wave * 32
                bossHpMax = bossHp.toFloat()
                bossHpCurrent = bossHp.toFloat()
                banner = "⚠ SECTOR BOSS ⚠"
                val spawnAngle = Random.nextFloat() * 2f * PI.toFloat()
                val spawnDist = max(sw, sh) * 0.85f + 120f
                val (bx, by) = wrapWorld(
                    shipPx + cos(spawnAngle) * spawnDist,
                    shipPy + sin(spawnAngle) * spawnDist,
                )
                val bossType = if (wave >= 6 && Random.nextBoolean()) EnemyType.KOMET_BIG else EnemyType.BOSS
                enemies += Enemy(
                    x = bx, y = by,
                    hp = if (bossType == EnemyType.KOMET_BIG) EnemyType.KOMET_BIG.maxHp + wave * 4 + 8 else bossHp,
                    maxHp = if (bossType == EnemyType.KOMET_BIG) EnemyType.KOMET_BIG.maxHp + wave * 4 + 8 else bossHp,
                    fireCd = 40, type = bossType
                )
                if (bossType == EnemyType.KOMET_BIG) {
                    bossHpMax = enemies.last().maxHp.toFloat()
                    bossHpCurrent = enemies.last().hp.toFloat()
                }
            } else if (!isBossActive && !awaitingBoss) {
                if (spawnCd > 0) spawnCd-- else if (spawned < enemiesPerWave) {
                    spawnCd = max(22, 72 - wave * 3)
                    val spawnAngle = Random.nextFloat() * 2f * PI.toFloat()
                    val spawnDist = max(sw, sh) * 0.75f + 80f
                    val (ex, ey) = wrapWorld(
                        shipPx + cos(spawnAngle) * spawnDist,
                        shipPy + sin(spawnAngle) * spawnDist,
                    )

                    // All PNG types; stoerer+lang+rund often; ASTEROID~15% KOMET~8%
                    val r = Random.nextFloat()
                    val type = when {
                        r < 0.15f -> EnemyType.ASTEROID
                        r < 0.23f -> EnemyType.KOMET
                        wave >= 3 && r < 0.31f ->
                            if (Random.nextBoolean()) EnemyType.MINE else EnemyType.FELS
                        wave >= 2 && r < 0.40f -> EnemyType.TANK
                        r < 0.52f -> EnemyType.SWARMER
                        r < 0.64f -> EnemyType.LANG
                        r < 0.74f -> EnemyType.RUND
                        wave >= 2 && r < 0.86f -> EnemyType.JAEGER
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
                    if (wave % 4 == 0) {
                        awaitingBoss = true
                        banner = "Boss-Signatur erkannt…"
                    } else {
                        wave++
                        spawned = 0
                        spawnCd = 70
                        banner = "Welle $wave!"
                    }
                }
            }

            val minesToBoom = mutableListOf<Enemy>()
            enemies.forEach { e ->
                val edx = wrapDx(shipPx, e.x)
                val edy = wrapDy(shipPy, e.y)
                val dist = hypot(edx, edy)

                when (e.type) {
                    EnemyType.KOMET -> e.angle += 1.2f
                    EnemyType.KOMET_BIG -> e.angle += 0.6f
                    EnemyType.ASTEROID -> e.angle += 2.5f
                    EnemyType.FELS -> e.angle += 0.7f
                    EnemyType.MINE -> e.angle += 1.0f
                    else -> e.angle = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f
                }

                val eSpeed = e.type.speed + wave * 0.08f
                if (dist > 12f) {
                    e.x += (edx / dist) * eSpeed
                    e.y += (edy / dist) * eSpeed
                }
                val wrapped = wrapWorld(e.x, e.y)
                e.x = wrapped.first
                e.y = wrapped.second

                if (e.type == EnemyType.MINE && dist < 70f) {
                    minesToBoom += e
                }

                if (e.type.canShoot()) {
                    if (e.fireCd > 0) e.fireCd-- else {
                        e.fireCd = when (e.type) {
                            EnemyType.SCOUT, EnemyType.JAEGER, EnemyType.LANG -> 90 - wave * 3
                            EnemyType.RUND -> 80 - wave * 2
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
                            val faceAng = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f
                            val ebvx = (edx / dist) * ebSpeed
                            val ebvy = (edy / dist) * ebSpeed

                            if (e.type == EnemyType.BOSS || e.type == EnemyType.TANK || e.type == EnemyType.RUND) {
                                val sp1 = (faceAng - 90f - 18f) * PI / 180.0
                                val sp2 = (faceAng - 90f + 18f) * PI / 180.0
                                bullets += Bullet(e.x, e.y, ebvx, ebvy, faceAng, false)
                                bullets += Bullet(e.x, e.y, (cos(sp1) * ebSpeed).toFloat(), (sin(sp1) * ebSpeed).toFloat(), faceAng - 18f, false)
                                bullets += Bullet(e.x, e.y, (cos(sp2) * ebSpeed).toFloat(), (sin(sp2) * ebSpeed).toFloat(), faceAng + 18f, false)
                            } else {
                                // SCOUT / JAEGER / LANG: single shot aimed at player
                                bullets += Bullet(e.x, e.y, ebvx, ebvy, faceAng, false)
                            }
                        }
                    }
                }
            }
            for (m in minesToBoom) {
                fx += Fx(m.x, m.y, 14, 1)
                fx += Fx(m.x, m.y, 18, 2)
                enemies.remove(m)
                hurtPlayer()
                sfx.hit()
            }

            bullets.forEach { it.x += it.vx; it.y += it.vy }
            powerups.forEach {
                it.y += 0.35f
                val wp = wrapWorld(it.x, it.y)
                it.x = wp.first
                it.y = wp.second
            }
            fx.forEach {
                it.life--
                val wf = wrapWorld(it.x, it.y)
                it.x = wf.first
                it.y = wf.second
            }
            fx.removeAll { it.life <= 0 }

            // Bullets: despawn far away (no wrap — avoids rear hits across seam)
            val bulletMaxDist = max(sw, sh) * 1.6f + 200f
            bullets.removeAll {
                hypot(it.x - shipPx, it.y - shipPy) > bulletMaxDist ||
                    it.x < -200f || it.x > WORLD_W + 200f ||
                    it.y < -200f || it.y > WORLD_H + 200f
            }
            powerups.removeAll { wrapDist(it.x, it.y, shipPx, shipPy) > bulletMaxDist * 1.2f }

            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    val r = enemyHitRadius(e.type)
                    if (wrapDist(b.x, b.y, e.x, e.y) < r) {
                        hitBullets += b
                        e.hp--
                        fx += Fx(e.x, e.y, 8, 0)
                        if (e.type.isBossLike()) {
                            bossHpCurrent = e.hp.toFloat().coerceAtLeast(0f)
                        }
                        if (e.hp <= 0) {
                            hitEnemies += e
                            fx += Fx(e.x, e.y, 14, 1)
                            fx += Fx(e.x, e.y, 18, 2)
                            score += e.type.scoreValue * wave
                            sfx.hit()
                            if (e.type.isBossLike()) {
                                isBossActive = false
                                wave++
                                spawned = 0
                                spawnCd = 80
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
                if (wrapDist(b.x, b.y, shipPx, shipPy) < shipPxSize * 0.32f) {
                    enemyHits += b
                    fx += Fx(shipPx, shipPy, 10, 0)
                    hurtPlayer()
                }
            }
            bullets.removeAll { it in enemyHits }

            enemies.toList().forEach { e ->
                val r = enemyHitRadius(e.type)
                if (wrapDist(e.x, e.y, shipPx, shipPy) < (r + shipPxSize * 0.30f)) {
                    fx += Fx(e.x, e.y, 14, 1)
                    if (!e.type.isBossLike()) {
                        enemies.remove(e)
                    }
                    hurtPlayer()
                }
            }

            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (wrapDist(p.x, p.y, shipPx, shipPy) < 48f) {
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
            val toSx = { wx: Float -> wrapDelta(wx - camX, WORLD_W) + size.width / 2f }
            val toSy = { wy: Float -> wrapDelta(wy - camY, WORLD_H) + size.height / 2f }

            drawSeamlessTiled(starFar, bgOffsetX * 0.25f, bgOffsetY * 0.25f, w, h)
            // Nebula: 1–2 large soft cover layers (no small-tile grid seams)
            bgNebula?.let {
                drawSoftCoverLayer(it, bgOffsetX, bgOffsetY, w, h, coverScale = 1.85f, alpha = 0.32f, parallax = 0.12f)
                drawSoftCoverLayer(it, bgOffsetX + w * 0.18f, bgOffsetY - h * 0.12f, w, h, coverScale = 2.35f, alpha = 0.22f, parallax = 0.07f)
            }
            drawSeamlessTiled(starMid, bgOffsetX * 0.50f, bgOffsetY * 0.50f, w, h)
            drawSeamlessTiled(starNear, bgOffsetX * 0.90f, bgOffsetY * 0.90f, w, h)
            // Debris: sparse soft layer, low alpha — avoid dense asteroid wallpaper
            bgDebris?.let {
                drawSoftCoverLayer(it, bgOffsetX, bgOffsetY, w, h, coverScale = 1.55f, alpha = 0.18f, parallax = 0.40f)
            }

            val half = shipPxSize / 2f

            enemies.forEachIndexed { i, e ->
                val sx = toSx(e.x)
                val sy = toSy(e.y)
                rotate(degrees = e.angle, pivot = Offset(sx, sy)) {
                    when (e.type) {
                        EnemyType.SWARMER -> {
                            drawImg(
                                enemySwarmer,
                                sx - swarmerPxSize / 2f,
                                sy - swarmerPxSize / 2f,
                                swarmerPxSize,
                                swarmerPxSize
                            )
                        }
                        EnemyType.SCOUT -> {
                            val img = if ((tick / 12 + i) % 2 == 0) enemyImg else enemyImgB
                            drawImg(img, sx - enemyPxSize / 2f, sy - enemyPxSize / 2f, enemyPxSize, enemyPxSize)
                        }
                        EnemyType.LANG -> {
                            val blink = (tick / 12 + i) % 2 == 0
                            val img = if (blink) schiffLang else schiffLangLicht
                            drawImg(img, sx - enemyLangW / 2f, sy - enemyLangH / 2f, enemyLangW, enemyLangH)
                        }
                        EnemyType.RUND -> {
                            val blink = (tick / 12 + i) % 2 == 0
                            val img = if (blink) schiffRund else schiffRundLicht
                            drawImg(img, sx - enemyRundSize / 2f, sy - enemyRundSize / 2f, enemyRundSize, enemyRundSize)
                        }
                        EnemyType.TANK -> {
                            drawImg(enemyTank, sx - tankPxSize / 2f, sy - tankPxSize / 2f, tankPxSize, tankPxSize)
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
                        EnemyType.KOMET -> {
                            val blink = (tick / 12 + i) % 2 == 0
                            val useBig = (tick / 24 + i) % 3 == 0
                            val img = when {
                                useBig && blink -> kometImg128 ?: kometImg
                                useBig -> kometLicht128 ?: kometLicht ?: kometImg
                                blink -> kometImg
                                else -> kometLicht ?: kometImg
                            }
                            val sz = if (useBig) enemyPxSize * 1.25f else enemyPxSize
                            drawSpriteOrOval(img, sx, sy, sz, e.type.color)
                        }
                        EnemyType.KOMET_BIG -> {
                            drawSpriteOrOval(kometBigImg, sx, sy, bossPxSize, e.type.color)
                            drawCircle(
                                e.type.color.copy(alpha = 0.45f),
                                bossPxSize * 0.48f,
                                Offset(sx, sy),
                                style = Stroke(width = 4f)
                            )
                        }
                        EnemyType.ASTEROID -> {
                            val phase = (tick / 12 + i) % 3
                            val img = when (phase) {
                                0 -> asteroidImg
                                1 -> asteroidImgB ?: asteroidImg
                                else -> asteroidImg80 ?: asteroidImg
                            }
                            val sz = if (phase == 2) enemyPxSize * 1.15f else enemyPxSize
                            drawSpriteOrOval(img, sx, sy, sz, e.type.color)
                        }
                        EnemyType.FELS -> {
                            drawSpriteOrOval(felsImg, sx, sy, tankPxSize, e.type.color)
                            drawCircle(
                                e.type.color.copy(alpha = 0.30f),
                                tankPxSize * 0.42f,
                                Offset(sx, sy),
                                style = Stroke(width = 3f)
                            )
                        }
                        EnemyType.JAEGER -> {
                            val blink = (tick / 12 + i) % 2 == 0
                            val img = when {
                                blink -> jaegerImg80 ?: jaegerImg
                                else -> jaegerLicht ?: jaegerImg
                            }
                            drawSpriteOrOval(img, sx, sy, enemyPxSize * 1.1f, e.type.color)
                        }
                        EnemyType.MINE -> {
                            val blink = (tick / 12 + i) % 2 == 0
                            val img = when {
                                blink -> mineImg80 ?: mineImg
                                else -> mineLicht ?: mineImg
                            }
                            drawSpriteOrOval(img, sx, sy, enemyPxSize * 0.95f, e.type.color)
                        }
                    }
                }
                if (e.hp < e.maxHp) {
                    val barW = when (e.type) {
                        EnemyType.BOSS, EnemyType.KOMET_BIG -> bossPxSize
                        EnemyType.TANK, EnemyType.FELS -> tankPxSize
                        EnemyType.LANG -> enemyLangH
                        EnemyType.RUND -> enemyRundSize
                        EnemyType.SWARMER -> swarmerPxSize
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
                    val thrusting = isTouching || hypot(shipVx, shipVy) > 1.2f
                    if (thrusting) {
                        val tf = thrustFrames[(tick / 3) % thrustFrames.size]
                        // Clear animated thrust at rear/mouth (~0.7–0.9 ship width), under hull
                        val tw = shipPxSize * 0.82f
                        val th = shipPxSize * 0.95f
                        // Tip-up asset: mouth = bottom of sprite → rear after rotate(shipAngle)
                        val ty = shipSy + half - th * 0.12f
                        drawImg(tf, shipSx - tw / 2f, ty, tw, th)
                    }
                    // Always single hull — never swap to triple (blue mode baked in sprite)
                    drawImg(shipSingle, shipSx - half, shipSy - half, shipPxSize, shipPxSize)

                    if (muzzleFlash > 0) {
                        val m = if (muzzleFlash > 2) muzzle1 else muzzle2
                        // Small nose flash only — not a large mode icon on the hull
                        val flash = 26f
                        val mouthY = shipSy - half - flash * 0.50f
                        drawImg(m, shipSx - flash / 2f, mouthY, flash, flash)
                    }
                }
            }

            if (shield > 0) {
                drawCircle(Color(0x554FC3F7), shipPxSize * 0.6f, Offset(shipSx, shipSy))
            }

            // --- Top-left: lives hearts + HP / energy bars ---
            val hudLeft = 12f
            val hudTop = 10f
            repeat(lives.coerceAtLeast(0)) { i ->
                drawImg(heartImg, hudLeft + i * 30f, hudTop, 26f, 26f)
            }
            val barLeft = hudLeft
            val barW = 148f
            val hpY = hudTop + 32f
            val enY = hpY + 16f
            // HP bar (thick)
            drawRect(Color(0xFF2A1A1A), topLeft = Offset(barLeft, hpY), size = Size(barW, 11f))
            drawRect(Color(0xFF4A2020), topLeft = Offset(barLeft, hpY), size = Size(barW, 11f), style = Stroke(width = 1.5f))
            val hpFrac = (lives.toFloat() / maxLives.toFloat()).coerceIn(0f, 1f)
            drawRect(
                Color(0xFFE53935),
                topLeft = Offset(barLeft, hpY),
                size = Size(barW * hpFrac, 11f)
            )
            // Energy / reactor bar
            drawRect(Color(0xFF12202A), topLeft = Offset(barLeft, enY), size = Size(barW, 9f))
            drawRect(Color(0xFF1A3A4A), topLeft = Offset(barLeft, enY), size = Size(barW, 9f), style = Stroke(width = 1.5f))
            val enFrac = (energy / 100f).coerceIn(0f, 1f)
            drawRect(
                Color(0xFF00E5FF),
                topLeft = Offset(barLeft, enY),
                size = Size(barW * enFrac, 9f)
            )
            // Optional shield bar when active
            if (shield > 0) {
                val shY = enY + 14f
                drawRect(Color(0xFF1A2A3A), topLeft = Offset(barLeft, shY), size = Size(barW, 7f))
                val shFrac = (shield / 420f).coerceIn(0f, 1f)
                drawRect(
                    Color(0xFF69F0AE),
                    topLeft = Offset(barLeft, shY),
                    size = Size(barW * shFrac, 7f)
                )
            }

            // Mode status chips in HUD (not on ship) — top-right under score area
            val chipSize = 34f
            var chipX = size.width - 16f - chipSize
            val chipY = 40f
            val modeChips = buildList {
                if (speedBoost > 0) add(puSpeed to Color(0xFF00E5FF))
                if (shield > 0) add(puHeal to Color(0xFF69F0AE))
                if (multishot > 0) add(puWeapon to Color(0xFFFF5252))
            }
            for ((img, ring) in modeChips) {
                val cx = chipX + chipSize / 2f
                val cy = chipY + chipSize / 2f
                drawCircle(ring.copy(alpha = 0.20f), chipSize * 0.62f, Offset(cx, cy))
                drawCircle(ring.copy(alpha = 0.85f), chipSize * 0.55f, Offset(cx, cy), style = Stroke(width = 2f))
                drawImg(img, chipX + 3f, chipY + 3f, chipSize - 6f, chipSize - 6f)
                chipX -= chipSize + 8f
            }

            if (isBossActive) {
                val bossBarW = size.width * 0.7f
                val barX = (size.width - bossBarW) / 2f
                val barY = 102f
                drawRect(Color(0xFF35123D), topLeft = Offset(barX, barY), size = Size(bossBarW, 12f))
                val frac = (bossHpCurrent / bossHpMax.coerceAtLeast(1f)).coerceIn(0f, 1f)
                drawRect(Color(0xFFE040FB), topLeft = Offset(barX, barY), size = Size(bossBarW * frac, 12f))
            }
        }

        Text(
            "Score $score   Hi $high   Welle $wave",
            color = Color(0xFFF2E6D0),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp)
        )
        Text(
            banner,
            color = Color(0xFFC9A66B),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 78.dp)
        )
        TextButton(
            onClick = { paused = !paused },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 70.dp)
        ) {
            Text(if (paused) "Weiter" else "Pause", color = Color.White, fontSize = 14.sp)
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


private fun DrawScope.drawSpriteOrOval(img: ImageBitmap?, cx: Float, cy: Float, size: Float, fallback: Color) {
    if (img != null) {
        drawImg(img, cx - size / 2f, cy - size / 2f, size, size)
    } else {
        drawOval(fallback, topLeft = Offset(cx - size / 2f, cy - size / 2f), size = Size(size, size))
        drawOval(
            Color.White.copy(alpha = 0.25f),
            topLeft = Offset(cx - size / 2f, cy - size / 2f),
            size = Size(size, size),
            style = Stroke(width = 2f)
        )
    }
}

/** One oversized soft layer with slow parallax drift — no tiling grid seams. */
private fun DrawScope.drawSoftCoverLayer(
    img: ImageBitmap,
    offX: Float,
    offY: Float,
    sw: Float,
    sh: Float,
    coverScale: Float = 1.6f,
    alpha: Float = 0.32f,
    parallax: Float = 0.15f
) {
    val dw = sw * coverScale
    val dh = sh * coverScale
    val spanX = dw * 0.28f
    val spanY = dh * 0.28f
    val rawX = offX * parallax
    val rawY = offY * parallax
    val driftX = ((rawX % spanX) + spanX) % spanX - spanX * 0.5f
    val driftY = ((rawY % spanY) + spanY) % spanY - spanY * 0.5f
    val x = (sw - dw) * 0.5f + driftX
    val y = (sh - dh) * 0.5f + driftY
    drawImg(img, x, y, dw, dh, alpha)
}

/** Positive-modulo tile with 2px overlap — no mirror seams. */
private fun DrawScope.drawSeamlessTiled(
    img: ImageBitmap,
    offX: Float,
    offY: Float,
    sw: Float,
    sh: Float,
    alpha: Float = 1f
) {
    val overlap = 2f
    val tw = sw
    val th = sh
    val ox = ((offX % tw) + tw) % tw
    val oy = ((offY % th) + th) % th
    var y = oy - th
    while (y < sh + overlap) {
        var x = ox - tw
        while (x < sw + overlap) {
            drawImg(img, x - overlap * 0.5f, y - overlap * 0.5f, tw + overlap, th + overlap, alpha)
            x += tw
        }
        y += th
    }
}

private fun DrawScope.drawImg(
    img: ImageBitmap,
    x: Float,
    y: Float,
    dw: Float,
    dh: Float,
    alpha: Float = 1f
) {
    drawImage(
        image = img,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Low
    )
}
