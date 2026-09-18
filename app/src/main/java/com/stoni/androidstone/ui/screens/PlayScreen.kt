package com.stoni.androidstone.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.stoni.androidstone.R
import com.stoni.androidstone.game.loadStargameAssetOrNull
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private enum class EnemyKind { BASIC, LANG, RUND, BIG }

private data class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val angle: Float,
    val fromPlayer: Boolean,
    val triple: Boolean = false,
)

private data class Enemy(
    var x: Float,
    var y: Float,
    var hp: Int = 1,
    var fireCd: Int = 60,
    val kind: EnemyKind = EnemyKind.BASIC,
    var angle: Float = 0f,
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

    fun release() {
        pool.release()
    }
}

private fun wrapMod(value: Float, size: Float): Float {
    if (size <= 0f) return 0f
    val r = value % size
    return if (r < 0f) r + size else r
}

private fun wrapWorld(value: Float, world: Float): Float {
    if (world <= 0f) return value
    var v = value
    while (v < 0f) v += world
    while (v >= world) v -= world
    return v
}

private fun angleDiff(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/** 0° = up, positive = clockwise. */
private fun headingRad(angleDeg: Float): Pair<Float, Float> {
    val rad = angleDeg * PI / 180.0
    return sin(rad).toFloat() to (-cos(rad)).toFloat()
}

private fun DrawScope.drawImg(img: ImageBitmap?, x: Float, y: Float, w: Float, h: Float) {
    if (img == null) return
    drawImage(
        image = img,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.None,
    )
}

private fun DrawScope.drawMirroredTiled(
    img: ImageBitmap?,
    offX: Float,
    offY: Float,
    viewW: Float,
    viewH: Float,
) {
    if (img == null) return
    val iw = img.width.toFloat()
    val ih = img.height.toFloat()
    if (iw <= 1f || ih <= 1f) return
    val startX = wrapMod(offX, iw) - iw
    val startY = wrapMod(offY, ih) - ih
    var curY = startY
    while (curY < viewH + ih) {
        var curX = startX
        while (curX < viewW + iw) {
            drawImage(
                image = img,
                dstOffset = IntOffset(curX.toInt(), curY.toInt()),
                dstSize = IntSize(iw.toInt(), ih.toInt()),
                filterQuality = FilterQuality.None,
            )
            curX += iw
        }
        curY += ih
    }
}

private fun DrawScope.drawEnemyFallback(kind: EnemyKind, cx: Float, cy: Float, w: Float, h: Float) {
    when (kind) {
        EnemyKind.BASIC -> {
            val path = Path().apply {
                moveTo(cx, cy - h * 0.5f)
                lineTo(cx + w * 0.42f, cy + h * 0.38f)
                lineTo(cx, cy + h * 0.18f)
                lineTo(cx - w * 0.42f, cy + h * 0.38f)
                close()
            }
            drawPath(path, Color(0xFF7CB342))
            drawPath(path, Color(0xFFC5E1A5), style = Stroke(width = 2f))
        }
        EnemyKind.LANG -> {
            val path = Path().apply {
                moveTo(cx, cy - h * 0.52f)
                lineTo(cx + w * 0.28f, cy + h * 0.48f)
                lineTo(cx, cy + h * 0.28f)
                lineTo(cx - w * 0.28f, cy + h * 0.48f)
                close()
            }
            drawPath(path, Color(0xFF26A69A))
            drawCircle(Color(0xFF80CBC4), radius = w * 0.16f, center = Offset(cx, cy - h * 0.08f))
        }
        EnemyKind.RUND -> {
            drawCircle(Color(0xFF00897B), radius = w * 0.48f, center = Offset(cx, cy))
            drawCircle(Color(0xFF004D40), radius = w * 0.22f, center = Offset(cx, cy))
            drawCircle(
                Color(0xFF80CBC4),
                radius = w * 0.48f,
                center = Offset(cx, cy),
                style = Stroke(width = 3f),
            )
        }
        EnemyKind.BIG -> {
            drawCircle(Color(0xFF558B2F), radius = w * 0.48f, center = Offset(cx, cy))
            drawCircle(Color(0xFF33691E), radius = w * 0.28f, center = Offset(cx, cy))
            drawCircle(Color(0xFFC5E1A5), radius = w * 0.1f, center = Offset(cx, cy - w * 0.12f))
        }
    }
}

private fun DrawScope.drawSpriteOrFallback(
    img: ImageBitmap?,
    kind: EnemyKind,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
) {
    if (img != null) {
        drawImg(img, cx - w / 2f, cy - h / 2f, w, h)
    } else {
        drawEnemyFallback(kind, cx, cy, w, h)
    }
}

private fun DrawScope.drawPlayerFallback(cx: Float, cy: Float, size: Float, triple: Boolean) {
    val path = Path().apply {
        moveTo(cx, cy - size * 0.48f)
        lineTo(cx + size * 0.32f, cy + size * 0.38f)
        lineTo(cx, cy + size * 0.18f)
        lineTo(cx - size * 0.32f, cy + size * 0.38f)
        close()
    }
    drawPath(path, Color(0xFFC9A66B))
    drawCircle(Color(0xFFE8E4DC), radius = size * 0.08f, center = Offset(cx, cy - size * 0.06f))
    if (triple) {
        drawCircle(Color(0xFF7EC8C4), radius = size * 0.06f, center = Offset(cx - size * 0.22f, cy + size * 0.08f))
        drawCircle(Color(0xFF7EC8C4), radius = size * 0.06f, center = Offset(cx + size * 0.22f, cy + size * 0.08f))
    }
}

@Composable
fun PlayScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val shipPxSize = with(density) { 68.dp.toPx() }
    val enemyPxSize = with(density) { 48.dp.toPx() }
    val enemyLangW = with(density) { 46.dp.toPx() }
    val enemyLangH = with(density) { 72.dp.toPx() }
    val enemyRundSize = with(density) { 64.dp.toPx() }
    val bigPxSize = with(density) { 82.dp.toPx() }
    val bulletW = with(density) { 14.dp.toPx() }
    val bulletH = with(density) { 26.dp.toPx() }
    val prefs = remember { context.getSharedPreferences("stargame", Context.MODE_PRIVATE) }
    var high by remember { mutableIntStateOf(prefs.getInt("highscore", 0)) }

    val sfx = remember { GameSfx(context) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    val shipSingle = remember { loadStargameAssetOrNull(context, "player_glocke_single_128.png") }
    val shipTriple = remember { loadStargameAssetOrNull(context, "player_glocke_triple_128.png") }
    val enemyImg = remember { loadStargameAssetOrNull(context, "enemy_stoerer_64.png") }
    val enemyImgB = remember { loadStargameAssetOrNull(context, "enemy_stoerer_b_64.png") }
    val enemyBig = remember { loadStargameAssetOrNull(context, "enemy_stoerer_big_128.png") }

    val schiffLang = remember { loadStargameAssetOrNull(context, "schiff_lang.png") }
    val schiffLangLicht = remember { loadStargameAssetOrNull(context, "schiff_lang_licht.png") }
    val schiffRund = remember { loadStargameAssetOrNull(context, "schiff_rund.png") }
    val schiffRundLicht = remember { loadStargameAssetOrNull(context, "schiff_rund_licht.png") }

    val bulletImg = remember { loadStargameAssetOrNull(context, "bullet_player.png") }
    val bulletTriple = remember { loadStargameAssetOrNull(context, "bullet_player_triple.png") }
    val bulletEnemy = remember { loadStargameAssetOrNull(context, "bullet_enemy.png") }
    val starFar = remember { loadStargameAssetOrNull(context, "bg_stars_far.png") }
    val starMid = remember { loadStargameAssetOrNull(context, "bg_stars_mid.png") }
    val starNear = remember { loadStargameAssetOrNull(context, "bg_stars_near.png") }
    val heartImg = remember { loadStargameAssetOrNull(context, "ui_heart.png") }
    val puWeapon = remember { loadStargameAssetOrNull(context, "icon_mode_weapon_64.png") }
    val puHeal = remember { loadStargameAssetOrNull(context, "icon_mode_heal_64.png") }
    val puSpeed = remember { loadStargameAssetOrNull(context, "powerup_speed_64.png") }
    val boom1 = remember { loadStargameAssetOrNull(context, "fx_explosion_1.png") }
    val boom2 = remember { loadStargameAssetOrNull(context, "fx_explosion_2.png") }
    val boom3 = remember { loadStargameAssetOrNull(context, "fx_explosion_3.png") }
    val muzzle1 = remember { loadStargameAssetOrNull(context, "fx_muzzle_1.png") }
    val muzzle2 = remember { loadStargameAssetOrNull(context, "fx_muzzle_2.png") }
    val death1 = remember { loadStargameAssetOrNull(context, "fx_player_death_1.png") }
    val death2 = remember { loadStargameAssetOrNull(context, "fx_player_death_2.png") }
    val death3 = remember { loadStargameAssetOrNull(context, "fx_player_death_3.png") }
    val death4 = remember { loadStargameAssetOrNull(context, "fx_player_death_4.png") }

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    val world = 4200f

    var shipPx by remember { mutableFloatStateOf(world / 2f) }
    var shipPy by remember { mutableFloatStateOf(world / 2f) }
    var shipVx by remember { mutableFloatStateOf(0f) }
    var shipVy by remember { mutableFloatStateOf(0f) }
    var shipAngle by remember { mutableFloatStateOf(0f) }

    var stickActive by remember { mutableStateOf(false) }
    var stickOx by remember { mutableFloatStateOf(0f) }
    var stickOy by remember { mutableFloatStateOf(0f) }
    var stickX by remember { mutableFloatStateOf(0f) }
    var stickY by remember { mutableFloatStateOf(0f) }
    var fireHeld by remember { mutableStateOf(false) }

    var camX by remember { mutableFloatStateOf(world / 2f) }
    var camY by remember { mutableFloatStateOf(world / 2f) }

    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(4) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("Feindflotte gesichtet! Abfangen!") }
    var multishot by remember { mutableIntStateOf(0) }
    var shield by remember { mutableIntStateOf(0) }
    var speedBoost by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    var muzzleFlash by remember { mutableIntStateOf(0) }
    var iFrames by remember { mutableIntStateOf(0) }
    var deathFrame by remember { mutableIntStateOf(0) }

    val bullets = remember { mutableListOf<Bullet>() }
    val enemies = remember { mutableListOf<Enemy>() }
    val powerups = remember { mutableListOf<PowerUp>() }
    val fx = remember { mutableListOf<Fx>() }
    var fireCd by remember { mutableIntStateOf(0) }
    var spawnCd by remember { mutableIntStateOf(20) }
    var wave by remember { mutableIntStateOf(1) }
    var spawned by remember { mutableIntStateOf(0) }

    fun saveHigh() {
        if (score > high) {
            high = score
            prefs.edit { putInt("highscore", score) }
        }
    }

    fun restartGame() {
        bullets.clear()
        enemies.clear()
        powerups.clear()
        fx.clear()
        score = 0
        lives = 4
        wave = 1
        spawned = 0
        spawnCd = 40
        fireCd = 0
        gameOver = false
        won = false
        paused = false
        shipPx = world / 2f
        shipPy = world / 2f
        shipVx = 0f
        shipVy = 0f
        shipAngle = 0f
        camX = shipPx
        camY = shipPy
        iFrames = 0
        multishot = 0
        shield = 0
        speedBoost = 0
        muzzleFlash = 0
        deathFrame = 0
        tick = 0
        fireHeld = false
        stickActive = false
        stickX = 0f
        stickY = 0f
        banner = "Feindflotte gesichtet! Abfangen!"
    }

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
            banner = "Glocke zerstört."
            saveHigh()
        }
    }

    LaunchedEffect(paused, gameOver, won) {
        while (!paused && !gameOver && !won) {
            delay(16)
            tick++
            val sw = w.coerceAtLeast(1f)
            val sh = h.coerceAtLeast(1f)

            if (iFrames > 0) iFrames--
            if (muzzleFlash > 0) muzzleFlash--

            val turn = (-stickX).coerceIn(-1f, 1f)
            val turnRate = if (speedBoost > 0) 6.4f else 5.2f
            shipAngle = wrapMod(shipAngle + turn * turnRate, 360f)

            val (fxFwd, fyFwd) = headingRad(shipAngle)
            val thrustAmt = (-stickY).coerceIn(-1f, 1f)
            val thrustPower = if (speedBoost > 0) 0.55f else 0.38f
            if (stickActive && kotlin.math.abs(thrustAmt) > 0.12f) {
                shipVx += fxFwd * thrustAmt * thrustPower
                shipVy += fyFwd * thrustAmt * thrustPower
            }

            val friction = 0.985f
            shipVx *= friction
            shipVy *= friction
            val maxSpd = if (speedBoost > 0) 16f else 11.5f
            val spd = hypot(shipVx, shipVy)
            if (spd > maxSpd) {
                shipVx = (shipVx / spd) * maxSpd
                shipVy = (shipVy / spd) * maxSpd
            }

            shipPx = wrapWorld(shipPx + shipVx, world)
            shipPy = wrapWorld(shipPy + shipVy, world)

            val look = 90f
            val targetCamX = shipPx + fxFwd * look
            val targetCamY = shipPy + fyFwd * look
            camX += (targetCamX - camX) * 0.14f
            camY += (targetCamY - camY) * 0.14f

            if (fireCd > 0) fireCd--
            if (fireHeld && fireCd <= 0 && iFrames < 70) {
                fireCd = if (multishot > 0) 8 else 13
                muzzleFlash = 3
                sfx.shoot()
                val bSpeed = if (speedBoost > 0) 22f else 18f
                val nose = shipPxSize * 0.44f
                val mx = shipPx + fxFwd * nose
                val my = shipPy + fyFwd * nose
                if (multishot > 0) {
                    val (lx, ly) = headingRad(shipAngle - 12f)
                    val (rx, ry) = headingRad(shipAngle + 12f)
                    bullets += Bullet(mx, my, fxFwd * bSpeed, fyFwd * bSpeed, shipAngle, true, true)
                    bullets += Bullet(mx, my, lx * bSpeed, ly * bSpeed, shipAngle - 12f, true, true)
                    bullets += Bullet(mx, my, rx * bSpeed, ry * bSpeed, shipAngle + 12f, true, true)
                } else {
                    bullets += Bullet(mx, my, fxFwd * bSpeed, fyFwd * bSpeed, shipAngle, true)
                }
            }

            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--

            val maxSpawn = when (wave) {
                1 -> 7
                2 -> 11
                else -> 16
            }
            if (spawnCd > 0) spawnCd-- else if (spawned < maxSpawn) {
                spawnCd = (52 - wave * 4).coerceAtLeast(22)
                val spawnAngle = Random.nextFloat() * (2.0 * PI)
                val spawnDist = maxOf(sw, sh) * 0.72f + 140f
                val ex = wrapWorld(shipPx + (cos(spawnAngle) * spawnDist).toFloat(), world)
                val ey = wrapWorld(shipPy + (sin(spawnAngle) * spawnDist).toFloat(), world)

                val roll = Random.nextFloat()
                val kind = when {
                    wave >= 3 && roll < 0.20f -> EnemyKind.BIG
                    wave >= 2 && roll < 0.50f -> EnemyKind.RUND
                    roll < 0.80f -> EnemyKind.LANG
                    else -> EnemyKind.BASIC
                }
                val hp = when (kind) {
                    EnemyKind.BIG -> 4
                    EnemyKind.RUND -> 3
                    EnemyKind.LANG -> 2
                    EnemyKind.BASIC -> 1
                }
                enemies += Enemy(
                    x = ex,
                    y = ey,
                    hp = hp,
                    fireCd = 40 + Random.nextInt(40),
                    kind = kind,
                    angle = Random.nextFloat() * 360f,
                )
                spawned++
            } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                if (wave < 3) {
                    wave++
                    spawned = 0
                    spawnCd = 55
                    banner = "Welle $wave"
                } else {
                    won = true
                    banner = "Orbit gesichert. Sieg!"
                    saveHigh()
                }
            }

            enemies.forEach { e ->
                var dx = shipPx - e.x
                var dy = shipPy - e.y
                if (dx > world * 0.5f) dx -= world
                if (dx < -world * 0.5f) dx += world
                if (dy > world * 0.5f) dy -= world
                if (dy < -world * 0.5f) dy += world
                val dist = hypot(dx, dy)
                val want = (atan2(dx, -dy) * 180.0 / PI).toFloat()
                val turnSpeed = when (e.kind) {
                    EnemyKind.LANG -> 4.6f
                    EnemyKind.RUND -> 2.4f
                    EnemyKind.BIG -> 1.6f
                    EnemyKind.BASIC -> 3.4f
                }
                val diff = angleDiff(e.angle, want)
                e.angle = wrapMod(e.angle + diff.coerceIn(-turnSpeed, turnSpeed), 360f)

                val eSpeed = when (e.kind) {
                    EnemyKind.LANG -> 2.4f + wave * 0.16f
                    EnemyKind.RUND -> 1.15f + wave * 0.08f
                    EnemyKind.BIG -> 0.85f
                    EnemyKind.BASIC -> 1.7f + wave * 0.14f
                }
                val (exF, eyF) = headingRad(e.angle)
                e.x = wrapWorld(e.x + exF * eSpeed, world)
                e.y = wrapWorld(e.y + eyF * eSpeed, world)

                if (e.fireCd > 0) e.fireCd-- else {
                    e.fireCd = when (e.kind) {
                        EnemyKind.LANG -> (62 - wave * 5).coerceAtLeast(28)
                        EnemyKind.RUND -> (72 - wave * 5).coerceAtLeast(34)
                        else -> (78 - wave * 6).coerceAtLeast(36)
                    }
                    if (dist > 40f && kotlin.math.abs(diff) < 28f) {
                        val ebSpeed = 5.0f + wave * 0.28f
                        if (e.kind == EnemyKind.RUND) {
                            val (a1x, a1y) = headingRad(e.angle - 16f)
                            val (a2x, a2y) = headingRad(e.angle + 16f)
                            bullets += Bullet(e.x, e.y, a1x * ebSpeed, a1y * ebSpeed, e.angle - 16f, false)
                            bullets += Bullet(e.x, e.y, a2x * ebSpeed, a2y * ebSpeed, e.angle + 16f, false)
                        } else {
                            bullets += Bullet(e.x, e.y, exF * ebSpeed, eyF * ebSpeed, e.angle, false)
                        }
                    }
                }
            }

            bullets.forEach { b ->
                b.x = wrapWorld(b.x + b.vx, world)
                b.y = wrapWorld(b.y + b.vy, world)
            }
            powerups.forEach { it.y += 0.35f }
            fx.forEach { it.life-- }
            fx.removeAll { it.life <= 0 }
            bullets.removeAll { hypot(shortest(it.x, shipPx, world), shortest(it.y, shipPy, world)) > maxOf(sw, sh) * 1.45f }
            powerups.removeAll { it.y > world }

            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    val r = when (e.kind) {
                        EnemyKind.BIG -> bigPxSize * 0.45f
                        EnemyKind.RUND -> enemyRundSize * 0.45f
                        EnemyKind.LANG -> enemyLangH * 0.40f
                        EnemyKind.BASIC -> enemyPxSize * 0.45f
                    }
                    if (hypot(shortest(b.x, e.x, world), shortest(b.y, e.y, world)) < r) {
                        hitBullets += b
                        e.hp--
                        fx += Fx(e.x, e.y, 8, 0)
                        if (e.hp <= 0) {
                            hitEnemies += e
                            fx += Fx(e.x, e.y, 14, 1)
                            fx += Fx(e.x, e.y, 18, 2)
                            score += when (e.kind) {
                                EnemyKind.BIG -> 60 * wave
                                EnemyKind.RUND -> 40 * wave
                                EnemyKind.LANG -> 25 * wave
                                EnemyKind.BASIC -> 15 * wave
                            }
                            sfx.hit()
                            if (Random.nextFloat() < 0.30f) {
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
                if (hypot(shortest(b.x, shipPx, world), shortest(b.y, shipPy, world)) < shipPxSize * 0.32f) {
                    enemyHits += b
                    fx += Fx(shipPx, shipPy, 10, 0)
                    hurtPlayer()
                }
            }
            bullets.removeAll { it in enemyHits }

            enemies.toList().forEach { e ->
                val r = when (e.kind) {
                    EnemyKind.BIG -> bigPxSize * 0.45f
                    EnemyKind.RUND -> enemyRundSize * 0.45f
                    EnemyKind.LANG -> enemyLangH * 0.40f
                    EnemyKind.BASIC -> enemyPxSize * 0.45f
                }
                if (hypot(shortest(e.x, shipPx, world), shortest(e.y, shipPy, world)) < (r + shipPxSize * 0.30f)) {
                    fx += Fx(e.x, e.y, 14, 1)
                    enemies.remove(e)
                    hurtPlayer()
                }
            }

            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (hypot(shortest(p.x, shipPx, world), shortest(p.y, shipPy, world)) < 48f) {
                    got += p
                    sfx.pickup()
                    when (p.type) {
                        0 -> {
                            multishot = 420
                            banner = "Dreifach-Schuss!"
                        }
                        1 -> {
                            shield = 420
                            banner = "Glocken-Schild!"
                        }
                        2 -> {
                            speedBoost = 420
                            banner = "Hyper-Schub!"
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
            .background(Color(0xFF050510)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gameOver, won, paused) {
                    awaitPointerEventScope {
                        var stickId: PointerId? = null
                        var fireId: PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (gameOver || won || paused) {
                                stickId = null
                                fireId = null
                                stickActive = false
                                fireHeld = false
                                stickX = 0f
                                stickY = 0f
                                continue
                            }
                            val half = size.width * 0.5f
                            event.changes.forEach { change ->
                                val p = change.position
                                if (change.pressed) {
                                    change.consume()
                                    if (p.x < half) {
                                        if (stickId == null || stickId == change.id) {
                                            if (stickId == null) {
                                                stickOx = p.x
                                                stickOy = p.y
                                                stickId = change.id
                                            }
                                            stickActive = true
                                            val maxR = 90f
                                            val dx = (p.x - stickOx).coerceIn(-maxR, maxR)
                                            val dy = (p.y - stickOy).coerceIn(-maxR, maxR)
                                            stickX = dx / maxR
                                            stickY = dy / maxR
                                        }
                                    } else {
                                        fireId = change.id
                                        fireHeld = true
                                    }
                                } else {
                                    if (change.id == stickId) {
                                        stickId = null
                                        stickActive = false
                                        stickX = 0f
                                        stickY = 0f
                                    }
                                    if (change.id == fireId) {
                                        fireId = null
                                        fireHeld = false
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            w = size.width
            h = size.height

            drawRect(Color(0xFF050510))

            val viewCamX = camX - size.width / 2f
            val viewCamY = camY - size.height / 2f
            drawMirroredTiled(starFar, -viewCamX * 0.22f, -viewCamY * 0.22f, size.width, size.height)
            drawMirroredTiled(starMid, -viewCamX * 0.48f, -viewCamY * 0.48f, size.width, size.height)
            drawMirroredTiled(starNear, -viewCamX * 0.88f, -viewCamY * 0.88f, size.width, size.height)

            translate(left = -viewCamX, top = -viewCamY) {
                enemies.forEachIndexed { i, e ->
                    val isLichtFrame = ((tick / 8 + i) % 2) == 0
                    val (ew, eh, img) = when (e.kind) {
                        EnemyKind.LANG -> Triple(
                            enemyLangW,
                            enemyLangH,
                            if (isLichtFrame) schiffLangLicht else schiffLang,
                        )
                        EnemyKind.RUND -> Triple(
                            enemyRundSize,
                            enemyRundSize,
                            if (isLichtFrame) schiffRundLicht else schiffRund,
                        )
                        EnemyKind.BIG -> Triple(bigPxSize, bigPxSize, enemyBig)
                        EnemyKind.BASIC -> Triple(
                            enemyPxSize,
                            enemyPxSize,
                            if (isLichtFrame) enemyImgB else enemyImg,
                        )
                    }
                    rotate(degrees = e.angle, pivot = Offset(e.x, e.y)) {
                        drawSpriteOrFallback(img, e.kind, e.x, e.y, ew, eh)
                    }
                }

                bullets.forEach { b ->
                    rotate(degrees = b.angle, pivot = Offset(b.x, b.y)) {
                        val img = when {
                            !b.fromPlayer -> bulletEnemy
                            b.triple -> bulletTriple
                            else -> bulletImg
                        }
                        if (img != null) {
                            drawImg(img, b.x - bulletW / 2f, b.y - bulletH / 2f, bulletW, bulletH)
                        } else {
                            val col = if (b.fromPlayer) Color(0xFF7EC8C4) else Color(0xFFE57373)
                            drawCircle(col, radius = 5f, center = Offset(b.x, b.y))
                        }
                    }
                }

                powerups.forEach { p ->
                    val img = when (p.type) {
                        0 -> puWeapon
                        1 -> puHeal
                        else -> puSpeed
                    }
                    if (img != null) {
                        drawImg(img, p.x - 24f, p.y - 24f, 48f, 48f)
                    } else {
                        val col = when (p.type) {
                            0 -> Color(0xFF7EC8C4)
                            1 -> Color(0xFF81C784)
                            else -> Color(0xFF90CAF9)
                        }
                        drawCircle(col, radius = 16f, center = Offset(p.x, p.y))
                    }
                }

                fx.forEach { f ->
                    val img = when (f.kind) {
                        0 -> boom1
                        1 -> boom2
                        else -> boom3
                    }
                    if (img != null) {
                        drawImg(img, f.x - 24f, f.y - 24f, 48f, 48f)
                    } else {
                        drawCircle(Color(0x88FFCC80), radius = 10f + f.life, center = Offset(f.x, f.y))
                    }
                }

                if (!gameOver) {
                    if (iFrames % 4 < 2) {
                        rotate(degrees = shipAngle, pivot = Offset(shipPx, shipPy)) {
                            val curShip = if (multishot > 0) shipTriple else shipSingle
                            if (curShip != null) {
                                drawImg(
                                    curShip,
                                    shipPx - shipPxSize / 2f,
                                    shipPy - shipPxSize / 2f,
                                    shipPxSize,
                                    shipPxSize,
                                )
                            } else {
                                drawPlayerFallback(shipPx, shipPy, shipPxSize, multishot > 0)
                            }
                            if (muzzleFlash > 0) {
                                val mImg = if (muzzleFlash % 2 == 0) muzzle1 else muzzle2
                                drawImg(mImg, shipPx - 16f, shipPy - shipPxSize / 2f - 20f, 32f, 32f)
                            }
                            if (shield > 0) {
                                drawCircle(
                                    Color(0x667EC8C4),
                                    radius = shipPxSize * 0.62f,
                                    center = Offset(shipPx, shipPy),
                                    style = Stroke(width = 3f),
                                )
                            }
                        }
                    }
                } else {
                    val dImg = when (deathFrame) {
                        0 -> death1
                        1 -> death2
                        2 -> death3
                        else -> death4
                    }
                    if (dImg != null) {
                        drawImg(dImg, shipPx - shipPxSize / 2f, shipPy - shipPxSize / 2f, shipPxSize, shipPxSize)
                    } else {
                        drawCircle(Color(0x88FF8A65), radius = 28f + deathFrame * 8f, center = Offset(shipPx, shipPy))
                    }
                }
            }

            for (i in 0 until lives) {
                if (heartImg != null) {
                    drawImg(heartImg, 24f + i * 40f, 40f, 32f, 32f)
                } else {
                    drawCircle(Color(0xFFE57373), radius = 10f, center = Offset(40f + i * 40f, 52f))
                }
            }

            if (stickActive && !gameOver && !won) {
                drawCircle(
                    Color(0x33E8E4DC),
                    radius = 70f,
                    center = Offset(stickOx, stickOy),
                    style = Stroke(width = 2f),
                )
                drawCircle(
                    Color(0x88C9A66B),
                    radius = 22f,
                    center = Offset(stickOx + stickX * 70f, stickOy + stickY * 70f),
                )
            }

            if (fireHeld && !gameOver && !won) {
                drawCircle(
                    Color(0x337EC8C4),
                    radius = 36f,
                    center = Offset(size.width - 70f, size.height - 90f),
                )
            } else if (!gameOver && !won) {
                drawCircle(
                    Color(0x227EC8C4),
                    radius = 34f,
                    center = Offset(size.width - 70f, size.height - 90f),
                    style = Stroke(width = 2f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 78.dp, start = 18.dp, end = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "SCORE  $score",
                        color = Color(0xFFE8E4DC),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "WELLE  $wave   BEST  $high",
                        color = Color(0xFF7EC8C4),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = { paused = !paused }) {
                    Text(if (paused) "WEITER" else "PAUSE", color = Color(0xFFC9A66B), fontSize = 13.sp)
                }
            }
            Text(
                banner,
                color = Color(0xFFB0A99A),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (!gameOver && !won && !paused) {
            Text(
                "LINKS  drehen + schub\nRECHTS halten  feuern",
                color = Color(0x66E8E4DC),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 28.dp),
            )
        }

        if (paused && !gameOver && !won) {
            OverlayCard(
                title = "PAUSE",
                subtitle = "Orbit wartet.",
                primary = "Weiter" to { paused = false },
                secondary = "Hauptmenü" to onExit,
            )
        }

        if (gameOver || won) {
            OverlayCard(
                title = if (won) "SIEG" else "GLOCKE ZERSTÖRT",
                subtitle = "Punkte $score   Welle $wave   Best $high",
                primary = "Erneut spielen" to { restartGame() },
                secondary = "Hauptmenü" to onExit,
            )
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    subtitle: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC050510)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFF12121A), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = Color(0xFFE8E4DC),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                color = Color(0xFF8A8680),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = primary.second,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8E4DC),
                    contentColor = Color(0xFF050510),
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(primary.first, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = secondary.second,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(secondary.first, color = Color(0xFF7EC8C4))
            }
        }
    }
}

private fun shortest(a: Float, b: Float, world: Float): Float {
    var d = a - b
    if (d > world * 0.5f) d -= world
    if (d < -world * 0.5f) d += world
    return d
}
