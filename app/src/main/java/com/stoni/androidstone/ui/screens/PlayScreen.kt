package com.stoni.androidstone.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.stoni.androidstone.R
import com.stoni.androidstone.game.EnemyType
import com.stoni.androidstone.game.PowerUpKind
import com.stoni.androidstone.game.bomberDropVelocity
import com.stoni.androidstone.game.bomberFireCooldown
import com.stoni.androidstone.game.canShoot
import com.stoni.androidstone.game.isBossLike
import com.stoni.androidstone.game.loadStargameAsset
import com.stoni.androidstone.game.loadStargameAssetOrNull
import com.stoni.androidstone.game.pickEnemySpawn
import com.stoni.androidstone.game.rollPowerUpDrop
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/** Large portrait arena with toroidal wrap (no walls). Size divisible by 2×500 star tiles. */
private const val WORLD_W = 10000f
private const val WORLD_H = 14000f

private fun wrapCoord(v: Float, size: Float): Float = ((v % size) + size) % size

/** Shortest signed delta on a toroidal axis. */
private fun wrapDelta(d: Float, size: Float): Float {
    var v = ((d % size) + size) % size
    if (v > size * 0.5f) v -= size
    return v
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

private data class PowerUp(var x: Float, var y: Float, val type: PowerUpKind)
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
    // Artiflux: komet core (ball) + separate flame trail frames
    val kometCore64 = remember { loadStargameAssetOrNull(context, "enemy_komet_core_64.png") }
    val kometCore128 = remember { loadStargameAssetOrNull(context, "enemy_komet_core_128.png") }
    val kometFlame48 = remember {
        listOfNotNull(
            loadStargameAssetOrNull(context, "enemy_komet_flame_1_48.png"),
            loadStargameAssetOrNull(context, "enemy_komet_flame_2_48.png"),
            loadStargameAssetOrNull(context, "enemy_komet_flame_3_48.png"),
            loadStargameAssetOrNull(context, "enemy_komet_flame_4_48.png"),
        )
    }
    val kometFlame64 = remember {
        listOfNotNull(
            loadStargameAssetOrNull(context, "enemy_komet_flame_1_64.png"),
            loadStargameAssetOrNull(context, "enemy_komet_flame_2_64.png"),
            loadStargameAssetOrNull(context, "enemy_komet_flame_3_64.png"),
            loadStargameAssetOrNull(context, "enemy_komet_flame_4_64.png"),
        )
    }
    // Fallback baked komet PNGs if core missing
    val kometImg = remember { loadStargameAssetOrNull(context, "enemy_komet_64.png") }
    val kometImg128 = remember { loadStargameAssetOrNull(context, "enemy_komet_128.png") }
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
    val schnellImg = remember { loadStargameAssetOrNull(context, "enemy_schnell_64.png") }
    val schnellLicht = remember { loadStargameAssetOrNull(context, "enemy_schnell_licht_64.png") }
    val schnellImg80 = remember { loadStargameAssetOrNull(context, "enemy_schnell_80.png") }
    val panzerImg = remember { loadStargameAssetOrNull(context, "enemy_panzer_64.png") }
    val panzerLicht = remember { loadStargameAssetOrNull(context, "enemy_panzer_licht_64.png") }
    val panzerImg80 = remember { loadStargameAssetOrNull(context, "enemy_panzer_80.png") }
    val panzerImg128 = remember { loadStargameAssetOrNull(context, "enemy_panzer_128.png") }
    val drohneImg = remember { loadStargameAssetOrNull(context, "enemy_drohne_64.png") }
    val drohneLicht = remember { loadStargameAssetOrNull(context, "enemy_drohne_licht_64.png") }
    val drohneImg80 = remember { loadStargameAssetOrNull(context, "enemy_drohne_80.png") }
    val bomberImg = remember { loadStargameAssetOrNull(context, "enemy_bomber_64.png") }
    val bomberLicht = remember { loadStargameAssetOrNull(context, "enemy_bomber_licht_64.png") }
    val bomberImg80 = remember { loadStargameAssetOrNull(context, "enemy_bomber_80.png") }
    val heartImg = remember { loadStargameAsset(context, "ui_heart.png") }
    // Artiflux Items v1 — world pickups (64). P1 + P2 wired (collect stubs; Kacki finals Draw/HUD).
    val puEnergy = remember { loadStargameAsset(context, "powerup_energy_64.png") }
    val puRapid = remember { loadStargameAsset(context, "powerup_rapid_64.png") }
    val puMagnet = remember { loadStargameAsset(context, "powerup_magnet_64.png") }
    val puSpread = remember { loadStargameAsset(context, "powerup_spread_64.png") }
    val puShield = remember { loadStargameAsset(context, "powerup_shield_64.png") }
    val puSpeed = remember { loadStargameAsset(context, "powerup_speed_64.png") }
    // P2 Artiflux Items v1 (Kacki finals Draw later; loads for compile + minimal icons)
    val puLaser = remember { loadStargameAsset(context, "powerup_weapon_laser_64.png") }
    val puMissile = remember { loadStargameAsset(context, "powerup_weapon_missile_64.png") }
    val puBomb = remember { loadStargameAsset(context, "powerup_bomb_64.png") }
    val puOverdrive = remember { loadStargameAsset(context, "powerup_overdrive_64.png") }
    // HUD chips: icon_hud_32 where available; shield/speed keep 64
    val hudEnergy = remember { loadStargameAsset(context, "powerup_energy_icon_hud_32.png") }
    val hudRapid = remember { loadStargameAsset(context, "powerup_rapid_icon_hud_32.png") }
    val hudMagnet = remember { loadStargameAsset(context, "powerup_magnet_icon_hud_32.png") }
    val hudSpread = remember { loadStargameAsset(context, "powerup_spread_icon_hud_32.png") }
    val boom1 = remember { loadStargameAsset(context, "fx_explosion_1.png") }
    val boom2 = remember { loadStargameAsset(context, "fx_explosion_2.png") }
    val boom3 = remember { loadStargameAsset(context, "fx_explosion_3.png") }
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
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    val stickMaxRadius = 100f
    // Fixed bottom-left stick (fraction of screen); never floats to finger
    fun stickOx(sw: Float) = sw * 0.18f
    fun stickOy(sh: Float) = sh * 0.82f
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
    var rapidFire by remember { mutableIntStateOf(0) }
    var scoreMagnet by remember { mutableIntStateOf(0) }
    var energyFlash by remember { mutableIntStateOf(0) }
    // Artiflux P2 stubs: laser=pierce mode, missile=homing mode (flags; draw/fire polish = Kacki)
    var laserMode by remember { mutableIntStateOf(0) }
    var missileMode by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    var bgOffsetX by remember { mutableFloatStateOf(0f) }
    var bgOffsetY by remember { mutableFloatStateOf(0f) }

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
            energy = (energy - 10f).coerceAtLeast(15f)
            iFrames = 50
            return
        }
        lives--
        energy = (energy - 18f).coerceAtLeast(0f)
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
        EnemyType.SWARMER, EnemyType.DROHNE -> swarmerPxSize * 0.45f
        EnemyType.SCOUT, EnemyType.JAEGER, EnemyType.KOMET, EnemyType.ASTEROID, EnemyType.MINE,
        EnemyType.SCHNELL ->
            enemyPxSize * 0.45f
        EnemyType.LANG -> max(enemyLangW, enemyLangH) * 0.42f
        EnemyType.RUND, EnemyType.BOMBER -> enemyRundSize * 0.45f
        EnemyType.TANK, EnemyType.FELS, EnemyType.PANZER -> tankPxSize * 0.45f
        EnemyType.BOSS, EnemyType.KOMET_BIG -> bossPxSize * 0.45f
    }

    fun wrapWorld(x: Float, y: Float): Pair<Float, Float> =
        wrapCoord(x, WORLD_W) to wrapCoord(y, WORLD_H)

    fun wrapDx(ax: Float, bx: Float): Float = wrapDelta(ax - bx, WORLD_W)
    fun wrapDy(ay: Float, by: Float): Float = wrapDelta(ay - by, WORLD_H)
    fun wrapDist(ax: Float, ay: Float, bx: Float, by: Float): Float =
        hypot(wrapDx(ax, bx), wrapDy(ay, by))

    LaunchedEffect(paused, gameOver) {
        while (!paused && !gameOver) {
            delay(20)
            tick++
            val sw = w.coerceAtLeast(1f)
            val sh = h.coerceAtLeast(1f)

            if (iFrames > 0) iFrames--
            // Soft energy: regen manageable; firing never drains energy
            if (energy < 100f) energy = (energy + 0.50f).coerceAtMost(100f)

            // Fixed bottom-left stick: vector from fixed origin→finger (clamped). Never chase finger/ship.
            if (isTouching) {
                val ox = stickOx(sw)
                val oy = stickOy(sh)
                val dx = fingerX - ox
                val dy = fingerY - oy
                val dist = hypot(dx, dy)
                if (dist > 8f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val strength = (dist.coerceAtMost(stickMaxRadius) / stickMaxRadius)
                    val targetAngle = (atan2(ny, nx) * 180.0 / PI).toFloat() + 90f
                    var diff = (targetAngle - shipAngle) % 360f
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    // Nose leads motion — turn fast enough that thrust stays nose-first
                    shipAngle += diff * 0.50f

                    // Thrust along facing (tip-up hull: shipAngle 0 = tip toward -Y)
                    val thrust = (if (speedBoost > 0) 1.35f else 1.0f) * strength
                    val rad = (shipAngle - 90f) * (PI / 180.0)
                    shipVx += (cos(rad) * thrust).toFloat()
                    shipVy += (sin(rad) * thrust).toFloat()
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
                    fireCd = when {
                        rapidFire > 0 -> 6
                        multishot > 0 -> 8
                        else -> 13
                    }
                    // No muzzle flash on hull — modes live only in HUD chips
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
            if (rapidFire > 0) rapidFire--
            if (scoreMagnet > 0) scoreMagnet--
            if (energyFlash > 0) energyFlash--
            if (laserMode > 0) laserMode--
            if (missileMode > 0) missileMode--

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
                    spawnCd = max(16, 58 - wave * 3)
                    val spawnAngle = Random.nextFloat() * 2f * PI.toFloat()
                    val spawnDist = max(sw, sh) * 0.75f + 80f
                    val (ex, ey) = wrapWorld(
                        shipPx + cos(spawnAngle) * spawnDist,
                        shipPy + sin(spawnAngle) * spawnDist,
                    )

                    val pick = pickEnemySpawn(wave)
                    val type = pick.type
                    val pack = pick.packSize
                    repeat(pack) { j ->
                        if (spawned >= enemiesPerWave) return@repeat
                        val ox = if (j == 0) 0f else (Random.nextFloat() - 0.5f) * 90f
                        val oy = if (j == 0) 0f else (Random.nextFloat() - 0.5f) * 90f
                        val (px, py) = wrapWorld(ex + ox, ey + oy)
                        enemies += Enemy(
                            x = px, y = py,
                            hp = type.maxHp + (wave - 1) / 2,
                            maxHp = type.maxHp + (wave - 1) / 2,
                            fireCd = 45 + Random.nextInt(35),
                            type = type
                        )
                        spawned++
                    }
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
                    EnemyType.MINE, EnemyType.DROHNE -> e.angle += 1.0f
                    else -> e.angle = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f
                }

                val eSpeed = e.type.speed + wave * 0.08f
                if (dist > 12f) {
                    val nx = edx / dist
                    val ny = edy / dist
                    when (e.type) {
                        EnemyType.SCHNELL -> {
                            val zig = sin(tick * 0.28f + e.x * 0.02f) * eSpeed * 1.35f
                            e.x += nx * eSpeed + (-ny) * zig
                            e.y += ny * eSpeed + nx * zig
                        }
                        EnemyType.DROHNE -> {
                            val prefer = 170f
                            val radial = when {
                                dist > prefer + 50f -> eSpeed * 0.9f
                                dist < prefer - 50f -> -eSpeed * 0.55f
                                else -> eSpeed * 0.15f
                            }
                            val tang = eSpeed * 1.1f
                            e.x += nx * radial + (-ny) * tang
                            e.y += ny * radial + nx * tang
                        }
                        EnemyType.BOMBER -> {
                            // Sideways/cross or slow frontal — not aggressive chase
                            val crossBias = 0.55f + 0.35f * sin(tick * 0.04f + e.x * 0.01f)
                            val approach = eSpeed * 0.22f
                            val lateral = eSpeed * 1.05f
                            e.x += nx * approach * (1f - crossBias) + (-ny) * lateral * crossBias
                            e.y += ny * approach * (1f - crossBias) + nx * lateral * crossBias
                        }
                        else -> {
                            e.x += nx * eSpeed
                            e.y += ny * eSpeed
                        }
                    }
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
                            EnemyType.SCOUT, EnemyType.JAEGER, EnemyType.LANG, EnemyType.SCHNELL -> 90 - wave * 3
                            EnemyType.RUND -> 80 - wave * 2
                            EnemyType.TANK, EnemyType.PANZER -> 70 - wave * 2
                            EnemyType.BOMBER -> bomberFireCooldown(wave)
                            EnemyType.BOSS -> 35
                            else -> 100
                        }.coerceAtLeast(20)

                        if (dist > 15f) {
                            val ebSpeed = when (e.type) {
                                EnemyType.BOSS -> 14f + wave * 0.4f
                                EnemyType.TANK, EnemyType.PANZER -> 10f + wave * 0.3f
                                else -> 11.5f + wave * 0.4f
                            }
                            val faceAng = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f
                            val ebvx = (edx / dist) * ebSpeed
                            val ebvy = (edy / dist) * ebSpeed

                            when (e.type) {
                                EnemyType.BOMBER -> {
                                    val (dropVx, dropVy) = bomberDropVelocity(wave)
                                    bullets += Bullet(e.x, e.y, dropVx, dropVy, 180f, false)
                                }
                                EnemyType.BOSS, EnemyType.TANK, EnemyType.PANZER, EnemyType.RUND -> {
                                    val sp1 = (faceAng - 90f - 18f) * PI / 180.0
                                    val sp2 = (faceAng - 90f + 18f) * PI / 180.0
                                    bullets += Bullet(e.x, e.y, ebvx, ebvy, faceAng, false)
                                    bullets += Bullet(e.x, e.y, (cos(sp1) * ebSpeed).toFloat(), (sin(sp1) * ebSpeed).toFloat(), faceAng - 18f, false)
                                    bullets += Bullet(e.x, e.y, (cos(sp2) * ebSpeed).toFloat(), (sin(sp2) * ebSpeed).toFloat(), faceAng + 18f, false)
                                }
                                else -> {
                                    bullets += Bullet(e.x, e.y, ebvx, ebvy, faceAng, false)
                                }
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
                            } else {
                                rollPowerUpDrop(wave)?.let { kind ->
                                    powerups += PowerUp(e.x, e.y, kind)
                                }
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

            if (scoreMagnet > 0) {
                for (p in powerups) {
                    val mdx = wrapDx(shipPx, p.x)
                    val mdy = wrapDy(shipPy, p.y)
                    val md = hypot(mdx, mdy)
                    if (md > 1f && md < 420f) {
                        val pull = 4.5f
                        val (nxp, nyp) = wrapWorld(p.x + (mdx / md) * pull, p.y + (mdy / md) * pull)
                        p.x = nxp
                        p.y = nyp
                    }
                }
            }

            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (wrapDist(p.x, p.y, shipPx, shipPy) < 48f) {
                    got += p
                    sfx.pickup()
                    when (p.type) {
                        PowerUpKind.ENERGY -> {
                            energy = (energy + 42f).coerceAtMost(100f)
                            energyFlash = 90
                            banner = "Energie +!"
                        }
                        PowerUpKind.RAPID_FIRE -> {
                            rapidFire = 480
                            banner = "Schnellfeuer!"
                        }
                        PowerUpKind.SPREAD -> {
                            multishot = 420
                            banner = "360° Mehrschuss!"
                        }
                        PowerUpKind.SHIELD -> {
                            shield = 420
                            banner = "Glocken-Schutzschild!"
                        }
                        PowerUpKind.SCORE_MAGNET -> {
                            scoreMagnet = 480
                            banner = "Score-Magnet!"
                        }
                        PowerUpKind.SPEED_BOOST -> {
                            speedBoost = 420
                            banner = "Hyper-Schub!"
                        }
                        PowerUpKind.WEAPON_LASER -> {
                            // Stub: Pierce/Laser-Mode Flag; reuse spread-ähnlich bis echtes Beam-Feuer
                            laserMode = 480
                            multishot = max(multishot, 360)
                            banner = "Laser-Mode!"
                        }
                        PowerUpKind.WEAPON_MISSILE -> {
                            // Stub: Homing-Mode Flag (Feuer-Logik folgt)
                            missileMode = 480
                            banner = "Raketen-Mode!"
                        }
                        PowerUpKind.BOMB -> {
                            // Einmalig: nahe Gegner clearen (kein Boss)
                            val radius = 420f
                            val doomed = enemies.filter {
                                !it.type.isBossLike() && wrapDist(it.x, it.y, shipPx, shipPy) < radius
                            }
                            for (e in doomed) {
                                fx += Fx(e.x, e.y, 14, 1)
                                fx += Fx(e.x, e.y, 18, 2)
                                score += e.type.scoreValue * wave
                                enemies.remove(e)
                            }
                            sfx.hit()
                            banner = if (doomed.isEmpty()) "Bombe (leer)!" else "Bombe! ×${doomed.size}"
                        }
                        PowerUpKind.OVERDRIVE -> {
                            // Temporärer Fire-Rate Boost wie RAPID_FIRE
                            rapidFire = max(rapidFire, 520)
                            banner = "Overdrive!"
                        }
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
                        // Stick stays fixed bottom-left; finger position drives relative vector
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

            // Deep-space arena: sparse large stars + soft nebula, mirror→normal tiling (seamless edges).
            drawSeamlessTiledMirrored(starFar, bgOffsetX * 0.22f, bgOffsetY * 0.22f, w, h)
            bgNebula?.let {
                drawSeamlessTiledMirrored(it, bgOffsetX * 0.10f, bgOffsetY * 0.10f, w, h, alpha = 0.32f, tileScale = 1.6f)
            }
            drawSeamlessTiledMirrored(starMid, bgOffsetX * 0.45f, bgOffsetY * 0.45f, w, h)
            drawSeamlessTiledMirrored(starNear, bgOffsetX * 0.85f, bgOffsetY * 0.85f, w, h)

            val half = shipPxSize / 2f

            enemies.forEachIndexed { i, e ->
                val sx = toSx(e.x)
                val sy = toSy(e.y)
                when (e.type) {
                    EnemyType.KOMET -> {
                        // Core ball + flame child @ Bottom-Center; flame DOWN opposite travel
                        val edx = wrapDx(shipPx, e.x)
                        val edy = wrapDy(shipPy, e.y)
                        val heading = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f
                        val coreSize = enemyPxSize * 0.72f
                        val flames = if (kometFlame48.isNotEmpty()) kometFlame48 else emptyList()
                        rotate(degrees = heading, pivot = Offset(sx, sy)) {
                            if (flames.isNotEmpty()) {
                                val ff = flames[(tick / 5 + i) % flames.size]
                                val fw = coreSize * 0.85f
                                val fh = coreSize * 1.05f
                                val mouthY = fh / 12f // Top-Center pivot of flame
                                drawImg(ff, sx - fw / 2f, sy + coreSize / 2f - mouthY, fw, fh)
                            }
                            val body = kometCore64 ?: kometImg
                            drawSpriteOrOval(body, sx, sy, coreSize, e.type.color)
                        }
                    }
                    EnemyType.KOMET_BIG -> {
                        val edx = wrapDx(shipPx, e.x)
                        val edy = wrapDy(shipPy, e.y)
                        val heading = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f
                        val coreSize = bossPxSize * 0.78f
                        val flames = when {
                            kometFlame64.isNotEmpty() -> kometFlame64
                            kometFlame48.isNotEmpty() -> kometFlame48
                            else -> emptyList()
                        }
                        rotate(degrees = heading, pivot = Offset(sx, sy)) {
                            if (flames.isNotEmpty()) {
                                val ff = flames[(tick / 5 + i) % flames.size]
                                val fw = coreSize * 0.95f
                                val fh = coreSize * 1.2f
                                val mouthY = fh / 12f
                                drawImg(ff, sx - fw / 2f, sy + coreSize / 2f - mouthY, fw, fh)
                            }
                            val body = kometCore128 ?: kometBigImg ?: kometImg128
                            drawSpriteOrOval(body, sx, sy, coreSize, e.type.color)
                            drawCircle(
                                e.type.color.copy(alpha = 0.45f),
                                bossPxSize * 0.42f,
                                Offset(sx, sy),
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                    else -> rotate(degrees = e.angle, pivot = Offset(sx, sy)) {
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
                            EnemyType.SCHNELL -> {
                                val blink = (tick / 12 + i) % 2 == 0
                                val img = when {
                                    blink -> schnellImg80 ?: schnellImg
                                    else -> schnellLicht ?: schnellImg
                                }
                                drawSpriteOrOval(img, sx, sy, enemyPxSize * 0.95f, e.type.color)
                            }
                            EnemyType.PANZER -> {
                                val blink = (tick / 12 + i) % 2 == 0
                                val img = when {
                                    blink -> panzerImg128 ?: panzerImg80 ?: panzerImg
                                    else -> panzerLicht ?: panzerImg
                                }
                                drawSpriteOrOval(img, sx, sy, tankPxSize * 0.92f, e.type.color)
                                drawCircle(
                                    e.type.color.copy(alpha = 0.35f),
                                    tankPxSize * 0.42f,
                                    Offset(sx, sy),
                                    style = Stroke(width = 3f)
                                )
                            }
                            EnemyType.DROHNE -> {
                                val blink = (tick / 12 + i) % 2 == 0
                                val img = when {
                                    blink -> drohneImg80 ?: drohneImg
                                    else -> drohneLicht ?: drohneImg
                                }
                                drawSpriteOrOval(img, sx, sy, swarmerPxSize * 1.05f, e.type.color)
                            }
                            EnemyType.BOMBER -> {
                                val blink = (tick / 12 + i) % 2 == 0
                                val img = when {
                                    blink -> bomberImg80 ?: bomberImg
                                    else -> bomberLicht ?: bomberImg
                                }
                                drawSpriteOrOval(img, sx, sy, enemyRundSize * 0.95f, e.type.color)
                            }
                            EnemyType.KOMET, EnemyType.KOMET_BIG -> Unit
                        }
                    }
                }

                if (e.hp < e.maxHp) {
                    val barW = when (e.type) {
                        EnemyType.BOSS, EnemyType.KOMET_BIG -> bossPxSize
                        EnemyType.TANK, EnemyType.FELS, EnemyType.PANZER -> tankPxSize
                        EnemyType.LANG -> enemyLangH
                        EnemyType.RUND, EnemyType.BOMBER -> enemyRundSize
                        EnemyType.SWARMER, EnemyType.DROHNE -> swarmerPxSize
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
                // Artiflux Items v1 remap (P1 energy/spread + optional rapid/magnet; shield/speed kept)
                val ring = when (p.type) {
                    PowerUpKind.ENERGY -> Color(0xFFB2EBF2)
                    PowerUpKind.RAPID_FIRE -> Color(0xFFFF9100)
                    PowerUpKind.SPREAD -> Color(0xFFFF5252)
                    PowerUpKind.SHIELD -> Color(0xFF69F0AE)
                    PowerUpKind.SCORE_MAGNET -> Color(0xFFFFD740)
                    PowerUpKind.SPEED_BOOST -> Color(0xFF00E5FF)
                    PowerUpKind.WEAPON_LASER -> Color(0xFFE040FB)
                    PowerUpKind.WEAPON_MISSILE -> Color(0xFFFF6E40)
                    PowerUpKind.BOMB -> Color(0xFFFFEE58)
                    PowerUpKind.OVERDRIVE -> Color(0xFFFFD740)
                }
                drawCircle(ring.copy(alpha = 0.22f), 38f, Offset(px, py))
                drawCircle(color = ring.copy(alpha = 0.85f), radius = 32f, center = Offset(px, py), style = Stroke(width = 4f))
                val img = when (p.type) {
                    PowerUpKind.ENERGY -> puEnergy
                    PowerUpKind.RAPID_FIRE -> puRapid
                    PowerUpKind.SPREAD -> puSpread
                    PowerUpKind.SHIELD -> puShield
                    PowerUpKind.SCORE_MAGNET -> puMagnet
                    PowerUpKind.SPEED_BOOST -> puSpeed
                    PowerUpKind.WEAPON_LASER -> puLaser
                    PowerUpKind.WEAPON_MISSILE -> puMissile
                    PowerUpKind.BOMB -> puBomb
                    PowerUpKind.OVERDRIVE -> puOverdrive
                }
                val icon = 48f
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
                        val tf = thrustFrames[((tick / 5) % thrustFrames.size).coerceAtLeast(0)]
                        // Artiflux flame-only v2: flush top, 0 cyan — attach Top-Center at bell mouth
                        val tw = shipPxSize * 0.78f
                        val th = shipPxSize * 0.55f
                        // Tip-up hull: mouth ~0.44 below center (cyan mode-icon stripped from asset)
                        val mouthY = shipSy + half * 0.44f
                        // Slight overlap so plume roots under rim with ZERO gap
                        val ty = mouthY - th * 0.06f
                        drawImg(tf, shipSx - tw / 2f, ty, tw, th)
                    }
                    // Always draw hull AFTER thrust so flame sits under mouth
                    drawImg(shipSingle, shipSx - half, shipSy - half, shipPxSize, shipPxSize)
                }
            }

            if (shield > 0) {
                drawCircle(Color(0x554FC3F7), shipPxSize * 0.6f, Offset(shipSx, shipSy))
            }

            // Fixed bottom-left stick — always faint; stronger while active. Finger stays in corner.
            if (!gameOver) {
                val ox = stickOx(size.width)
                val oy = stickOy(size.height)
                val rawDx = if (isTouching) fingerX - ox else 0f
                val rawDy = if (isTouching) fingerY - oy else 0f
                val rawDist = hypot(rawDx, rawDy)
                val knobDx: Float
                val knobDy: Float
                if (isTouching && rawDist > stickMaxRadius && rawDist > 0.001f) {
                    knobDx = rawDx / rawDist * stickMaxRadius
                    knobDy = rawDy / rawDist * stickMaxRadius
                } else if (isTouching) {
                    knobDx = rawDx
                    knobDy = rawDy
                } else {
                    knobDx = 0f
                    knobDy = 0f
                }
                val baseA = if (isTouching) 0.22f else 0.12f
                val fillA = if (isTouching) 0.12f else 0.06f
                val knobA = if (isTouching) 0.40f else 0.18f
                drawCircle(
                    color = Color.White.copy(alpha = baseA),
                    radius = stickMaxRadius,
                    center = Offset(ox, oy),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = fillA),
                    radius = stickMaxRadius,
                    center = Offset(ox, oy)
                )
                drawCircle(
                    color = Color.White.copy(alpha = knobA),
                    radius = 22f,
                    center = Offset(ox + knobDx, oy + knobDy)
                )
                drawCircle(
                    color = Color.White.copy(alpha = if (isTouching) 0.55f else 0.28f),
                    radius = 22f,
                    center = Offset(ox + knobDx, oy + knobDy),
                    style = Stroke(width = 2f)
                )
            }
            // HUD (hearts/bars/score/pause/banner) is Compose overlay with window insets
        }

        // Safe HUD: below status bar / notch / cutout (edge-to-edge)
        val hudInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .union(WindowInsets.statusBars)
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(hudInsets)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Top-left: hearts + thick HP / energy / shield bars + pause
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(lives.coerceAtLeast(0)) {
                        Image(
                            bitmap = heartImg,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                HudStatBar(
                    fraction = (lives.toFloat() / maxLives.toFloat()).coerceIn(0f, 1f),
                    track = Color(0xFF2A1A1A),
                    fill = Color(0xFFE53935),
                    heightDp = 14.dp,
                    widthDp = 168.dp
                )
                HudStatBar(
                    fraction = (energy / 100f).coerceIn(0f, 1f),
                    track = Color(0xFF12202A),
                    fill = Color(0xFF00E5FF),
                    heightDp = 12.dp,
                    widthDp = 168.dp
                )
                if (shield > 0) {
                    HudStatBar(
                        fraction = (shield / 420f).coerceIn(0f, 1f),
                        track = Color(0xFF1A2A3A),
                        fill = Color(0xFF69F0AE),
                        heightDp = 10.dp,
                        widthDp = 168.dp
                    )
                }
                TextButton(
                    onClick = { paused = !paused },
                    modifier = Modifier.padding(start = 0.dp)
                ) {
                    Text(
                        if (paused) "Weiter" else "Pause",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 6f)
                        )
                    )
                }
            }

            // Top-right: score / wave progress + mode chips (modes only in UI)
            val enemiesPerWaveHud = 16 + wave * 6
            val waveAlive = enemies.count { !it.type.isBossLike() }
            val waveRemaining = if (isBossActive || awaitingBoss) {
                0
            } else {
                (enemiesPerWaveHud - spawned).coerceAtLeast(0) + waveAlive
            }
            val waveDone = (enemiesPerWaveHud - waveRemaining).coerceIn(0, enemiesPerWaveHud)
            val wavePct = if (enemiesPerWaveHud > 0) (waveDone * 100) / enemiesPerWaveHud else 0
            val waveLine = when {
                isBossActive -> "Welle $wave · Boss"
                awaitingBoss -> "Welle $wave · Boss…"
                else -> "Welle $wave · $wavePct%  ($waveDone/$enemiesPerWaveHud)"
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC0A0A18), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Score $score  ·  Hi $high\n$waveLine",
                        color = Color.White,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 8f)
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (speedBoost > 0) {
                        HudModeChip(puSpeed, Color(0xFF00E5FF), "Tempo", speedBoost, 420)
                    }
                    if (shield > 0) {
                        HudModeChip(puShield, Color(0xFF69F0AE), "Schild", shield, 420)
                    }
                    if (multishot > 0) {
                        HudModeChip(hudSpread, Color(0xFFFF5252), "Waffe", multishot, 420)
                    }
                    if (rapidFire > 0) {
                        HudModeChip(hudRapid, Color(0xFFFF9100), "Schnell", rapidFire, 480)
                    }
                    if (scoreMagnet > 0) {
                        HudModeChip(hudMagnet, Color(0xFFFFD740), "Magnet", scoreMagnet, 480)
                    }
                    if (energyFlash > 0) {
                        HudModeChip(hudEnergy, Color(0xFFB2EBF2), "Energie", energyFlash, 90)
                    }
                }
            }

            // Banner under top HUD
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .background(Color(0xAA120C08), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    banner,
                    color = Color(0xFFFFE0A0),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        shadow = Shadow(Color.Black, Offset(1f, 1f), 6f)
                    )
                )
            }

            if (isBossActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 96.dp)
                        .fillMaxWidth(0.78f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "SECTOR BOSS",
                        color = Color(0xFFE040FB),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(Color.Black, Offset(1f, 1f), 4f)
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    HudStatBar(
                        fraction = (bossHpCurrent / bossHpMax.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        track = Color(0xFF35123D),
                        fill = Color(0xFFE040FB),
                        heightDp = 14.dp,
                        widthDp = 280.dp
                    )
                }
            }

            if (paused) {
                Text(
                    "Pause",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color.Black, Offset(2f, 2f), 10f)
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Arena minimap — bottom-right, live player + nearby enemies
            if (!gameOver) {
                ArenaMinimap(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 8.dp),
                    shipX = shipPx,
                    shipY = shipPy,
                    enemies = enemies,
                    worldW = WORLD_W,
                    worldH = WORLD_H
                )
            }

            if (gameOver) {
                TextButton(
                    onClick = onExit,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color(0xCC1A1020), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        "Nochmal / Menü",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ArenaMinimap(
    modifier: Modifier = Modifier,
    shipX: Float,
    shipY: Float,
    enemies: List<Enemy>,
    worldW: Float,
    worldH: Float
) {
    val mapDp = 112.dp
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            "Arena",
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
        )
        Box(
            modifier = Modifier
                .size(mapDp)
                .background(Color(0xCC050510), RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = 3f
                val aw = size.width - pad * 2f
                val ah = size.height - pad * 2f
                // Dim arena rect (portrait aspect)
                val aspect = worldW / worldH
                val rw: Float
                val rh: Float
                if (aw / ah > aspect) {
                    rh = ah
                    rw = ah * aspect
                } else {
                    rw = aw
                    rh = aw / aspect
                }
                val left = (size.width - rw) / 2f
                val top = (size.height - rh) / 2f
                drawRoundRect(
                    color = Color(0xFF12122A),
                    topLeft = Offset(left, top),
                    size = Size(rw, rh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = Color(0x334FC3F7),
                    topLeft = Offset(left, top),
                    size = Size(rw, rh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                    style = Stroke(width = 1.5f)
                )
                // Wrap-edge hint (soft border dashes feel)
                drawRect(
                    color = Color(0x22FFFFFF),
                    topLeft = Offset(left + 1f, top + 1f),
                    size = Size(rw - 2f, rh - 2f),
                    style = Stroke(width = 1f)
                )
                fun toMx(wx: Float) = left + (wx / worldW) * rw
                fun toMy(wy: Float) = top + (wy / worldH) * rh
                // Nearby enemies as dots (all alive — arena overview)
                enemies.forEach { e ->
                    val ex = toMx(e.x)
                    val ey = toMy(e.y)
                    val col = when {
                        e.type.isBossLike() -> Color(0xFFE040FB)
                        else -> Color(0xFFFF8A65)
                    }
                    drawCircle(col.copy(alpha = 0.85f), radius = if (e.type.isBossLike()) 3.2f else 2.0f, center = Offset(ex, ey))
                }
                // Player blip
                val px = toMx(shipX)
                val py = toMy(shipY)
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.35f), radius = 6f, center = Offset(px, py))
                drawCircle(Color(0xFF00E5FF), radius = 3.2f, center = Offset(px, py))
                drawCircle(Color.White, radius = 1.4f, center = Offset(px, py))
            }
        }
    }
}

@Composable
private fun HudStatBar(
    fraction: Float,
    track: Color,
    fill: Color,
    heightDp: Dp,
    widthDp: Dp
) {
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .shadow(2.dp, RoundedCornerShape(6.dp))
            .background(track, RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(fill, RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun HudModeChip(
    img: ImageBitmap,
    ring: Color,
    label: String,
    remaining: Int,
    maxTicks: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(ring.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = img,
                contentDescription = label,
                modifier = Modifier.size(42.dp),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                shadow = Shadow(Color.Black, Offset(1f, 1f), 4f)
            )
        )
        HudStatBar(
            fraction = (remaining.toFloat() / maxTicks.toFloat()).coerceIn(0f, 1f),
            track = Color(0xFF1A1A28),
            fill = ring,
            heightDp = 5.dp,
            widthDp = 56.dp
        )
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

/**
 * Seamless mirror tiling: tile (i,j) uses scale(±1,±1) from i%2 / j%2
 * (mirror → normal → mirror → …). Shared edges always match. Native bitmap
 * size (× tileScale) — never stretch one tile to the full screen (avoids jumps).
 */
private fun DrawScope.drawSeamlessTiledMirrored(
    img: ImageBitmap,
    offX: Float,
    offY: Float,
    sw: Float,
    sh: Float,
    alpha: Float = 1f,
    tileScale: Float = 1f
) {
    val tw = img.width.toFloat() * tileScale
    val th = img.height.toFloat() * tileScale
    if (tw < 1f || th < 1f) return
    val pad = 1f
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
                drawImg(img, posX - pad, posY - pad, tw + pad * 2f, th + pad * 2f, alpha)
            }
        }
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

