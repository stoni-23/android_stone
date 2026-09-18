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
import androidx.compose.runtime.mutableStateListOf
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
import kotlin.math.abs
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
    var life: Int = 55,
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

private fun shortest(a: Float, b: Float, size: Float): Float {
    var d = a - b
    if (d > size * 0.5f) d -= size
    if (d < -size * 0.5f) d += size
    return d
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
            drawPath(path, Color(0xFF8BC34A))
            drawPath(path, Color(0xFFE8F5E9), style = Stroke(width = 3f))
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
            drawPath(path, Color(0xFFB2DFDB), style = Stroke(width = 3f))
        }
        EnemyKind.RUND -> {
            drawCircle(Color(0xFF00897B), radius = w * 0.48f, center = Offset(cx, cy))
            drawCircle(Color(0xFF004D40), radius = w * 0.22f, center = Offset(cx, cy))
            drawCircle(Color(0xFF80CBC4), radius = w * 0.48f, center = Offset(cx, cy), style = Stroke(width = 4f))
        }
        EnemyKind.BIG -> {
            drawCircle(Color(0xFF558B2F), radius = w * 0.48f, center = Offset(cx, cy))
            drawCircle(Color(0xFF33691E), radius = w * 0.28f, center = Offset(cx, cy))
            drawCircle(Color(0xFFDCEDC8), radius = w * 0.48f, center = Offset(cx, cy), style = Stroke(width = 4f))
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
    drawPath(path, Color(0xFFE8E4DC), style = Stroke(width = 3f))
    drawCircle(Color(0xFFE8E4DC), radius = size * 0.08f, center = Offset(cx, cy - size * 0.06f))
    if (triple) {
        drawCircle(Color(0xFF7EC8C4), radius = size * 0.06f, center = Offset(cx - size * 0.22f, cy + size * 0.08f))
        drawCircle(Color(0xFF7EC8C4), radius = size * 0.06f, center = Offset(cx + size * 0.22f, cy + size * 0.08f))
    }
}

private fun DrawScope.drawWrapped(
    x: Float,
    y: Float,
    sw: Float,
    sh: Float,
    pad: Float,
    block: (Float, Float) -> Unit,
) {
    for (ox in floatArrayOf(-sw, 0f, sw)) {
        for (oy in floatArrayOf(-sh, 0f, sh)) {
            val dx = x + ox
            val dy = y + oy
            if (dx > -pad && dx < sw + pad && dy > -pad && dy < sh + pad) {
                block(dx, dy)
            }
        }
    }
}

@Composable
fun PlayScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val shipPxSize = with(density) { 72.dp.toPx() }
    val enemyPxSize = with(density) { 56.dp.toPx() }
    val enemyLangW = with(density) { 52.dp.toPx() }
    val enemyLangH = with(density) { 86.dp.toPx() }
    val enemyRundSize = with(density) { 72.dp.toPx() }
    val bigPxSize = with(density) { 96.dp.toPx() }
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

    var shipPx by remember { mutableFloatStateOf(0f) }
    var shipPy by remember { mutableFloatStateOf(0f) }
    var shipVx by remember { mutableFloatStateOf(0f) }
    var shipVy by remember { mutableFloatStateOf(0f) }
    var shipAngle by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }

    var stickActive by remember { mutableStateOf(false) }
    var thrusting by remember { mutableStateOf(false) }
    var stickOx by remember { mutableFloatStateOf(0f) }
    var stickOy by remember { mutableFloatStateOf(0f) }
    var stickX by remember { mutableFloatStateOf(0f) }
    var stickY by remember { mutableFloatStateOf(0f) }
    var fireHeld by remember { mutableStateOf(false) }

    var starOffX by remember { mutableFloatStateOf(0f) }
    var starOffY by remember { mutableFloatStateOf(0f) }

    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(4) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("Links schieben = drehen + fliegen") }
    var multishot by remember { mutableIntStateOf(0) }
    var shield by remember { mutableIntStateOf(0) }
    var speedBoost by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    var muzzleFlash by remember { mutableIntStateOf(0) }
    var iFrames by remember { mutableIntStateOf(0) }
    var deathFrame by remember { mutableIntStateOf(0) }

    val bullets = remember { mutableStateListOf<Bullet>() }
    val enemies = remember { mutableStateListOf<Enemy>() }
    val powerups = remember { mutableStateListOf<PowerUp>() }
    val fx = remember { mutableStateListOf<Fx>() }
    var fireCd by remember { mutableIntStateOf(0) }
    var spawnCd by remember { mutableIntStateOf(8) }
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
        spawnCd = 8
        fireCd = 0
        gameOver = false
        won = false
        paused = false
        shipPx = w / 2f
        shipPy = h / 2f
        shipVx = 0f
        shipVy = 0f
        shipAngle = 0f
        placed = w > 10f
        iFrames = 0
        multishot = 0
        shield = 0
        speedBoost = 0
        muzzleFlash = 0
        deathFrame = 0
        tick = 0
        fireHeld = false
        stickActive = false
        thrusting = false
        stickX = 0f
        stickY = 0f
        banner = "Links schieben = drehen + fliegen"
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
            val sw = w
            val sh = h
            if (sw < 80f || sh < 80f) continue

            if (!placed) {
                shipPx = sw / 2f
                shipPy = sh / 2f
                placed = true
            }

            if (iFrames > 0) iFrames--
            if (muzzleFlash > 0) muzzleFlash--

            val mag = hypot(stickX, stickY)
            thrusting = stickActive && mag > 0.12f
            if (thrusting) {
                val want = (atan2(stickX, -stickY) * 180.0 / PI).toFloat()
                val turnRate = if (speedBoost > 0) 8.5f else 6.5f
                val diff = angleDiff(shipAngle, want)
                shipAngle = wrapMod(shipAngle + diff.coerceIn(-turnRate, turnRate), 360f)
                val (fxFwd, fyFwd) = headingRad(shipAngle)
                val thrustPower = if (speedBoost > 0) 0.62f else 0.42f
                shipVx += fxFwd * thrustPower * mag.coerceAtMost(1f)
                shipVy += fyFwd * thrustPower * mag.coerceAtMost(1f)
            }

            val friction = 0.985f
            shipVx *= friction
            shipVy *= friction
            val maxSpd = if (speedBoost > 0) 14f else 9.5f
            val spd = hypot(shipVx, shipVy)
            if (spd > maxSpd) {
                shipVx = (shipVx / spd) * maxSpd
                shipVy = (shipVy / spd) * maxSpd
            }

            shipPx = wrapMod(shipPx + shipVx, sw)
            shipPy = wrapMod(shipPy + shipVy, sh)
            starOffX -= shipVx * 0.55f
            starOffY -= shipVy * 0.55f

            val (noseX, noseY) = headingRad(shipAngle)

            if (fireCd > 0) fireCd--
            if (fireHeld && fireCd <= 0 && iFrames < 70) {
                fireCd = if (multishot > 0) 8 else 12
                muzzleFlash = 3
                sfx.shoot()
                val bSpeed = if (speedBoost > 0) 18f else 14f
                val mx = wrapMod(shipPx + noseX * shipPxSize * 0.48f, sw)
                val my = wrapMod(shipPy + noseY * shipPxSize * 0.48f, sh)
                if (multishot > 0) {
                    val (lx, ly) = headingRad(shipAngle - 14f)
                    val (rx, ry) = headingRad(shipAngle + 14f)
                    bullets += Bullet(mx, my, noseX * bSpeed, noseY * bSpeed, shipAngle, true, true)
                    bullets += Bullet(mx, my, lx * bSpeed, ly * bSpeed, shipAngle - 14f, true, true)
                    bullets += Bullet(mx, my, rx * bSpeed, ry * bSpeed, shipAngle + 14f, true, true)
                } else {
                    bullets += Bullet(mx, my, noseX * bSpeed, noseY * bSpeed, shipAngle, true)
                }
            }

            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--

            val maxSpawn = when (wave) {
                1 -> 5
                2 -> 8
                else -> 12
            }
            if (spawnCd > 0) spawnCd-- else if (spawned < maxSpawn) {
                spawnCd = (40 - wave * 4).coerceAtLeast(18)
                var ex = 0f
                var ey = 0f
                var tries = 0
                do {
                    ex = 40f + Random.nextFloat() * (sw - 80f)
                    ey = 80f + Random.nextFloat() * (sh - 160f)
                    tries++
                } while (hypot(shortest(ex, shipPx, sw), shortest(ey, shipPy, sh)) < 200f && tries < 24)

                val roll = Random.nextFloat()
                val kind = when {
                    wave >= 3 && roll < 0.22f -> EnemyKind.BIG
                    wave >= 2 && roll < 0.52f -> EnemyKind.RUND
                    roll < 0.82f -> EnemyKind.LANG
                    else -> EnemyKind.BASIC
                }
                val hp = when (kind) {
                    EnemyKind.BIG -> 4
                    EnemyKind.RUND -> 3
                    EnemyKind.LANG -> 2
                    EnemyKind.BASIC -> 1
                }
                val face = (atan2(shortest(shipPx, ex, sw), -shortest(shipPy, ey, sh)) * 180.0 / PI).toFloat()
                enemies += Enemy(ex, ey, hp, 30 + Random.nextInt(40), kind, face)
                spawned++
                if (spawned == 1) banner = "Feinde im Orbit — drehen und schieben!"
            } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                if (wave < 3) {
                    wave++
                    spawned = 0
                    spawnCd = 35
                    banner = "Welle $wave"
                } else {
                    won = true
                    banner = "Orbit gesichert. Sieg!"
                    saveHigh()
                }
            }

            enemies.forEach { e ->
                val dx = shortest(shipPx, e.x, sw)
                val dy = shortest(shipPy, e.y, sh)
                val dist = hypot(dx, dy)
                val want = (atan2(dx, -dy) * 180.0 / PI).toFloat()
                val turnSpeed = when (e.kind) {
                    EnemyKind.LANG -> 4.8f
                    EnemyKind.RUND -> 2.6f
                    EnemyKind.BIG -> 1.8f
                    EnemyKind.BASIC -> 3.6f
                }
                val diff = angleDiff(e.angle, want)
                e.angle = wrapMod(e.angle + diff.coerceIn(-turnSpeed, turnSpeed), 360f)

                val eSpeed = when (e.kind) {
                    EnemyKind.LANG -> 2.6f + wave * 0.18f
                    EnemyKind.RUND -> 1.35f + wave * 0.1f
                    EnemyKind.BIG -> 0.95f
                    EnemyKind.BASIC -> 1.9f + wave * 0.16f
                }
                val (exF, eyF) = headingRad(e.angle)
                e.x = wrapMod(e.x + exF * eSpeed, sw)
                e.y = wrapMod(e.y + eyF * eSpeed, sh)

                if (e.fireCd > 0) e.fireCd-- else {
                    e.fireCd = when (e.kind) {
                        EnemyKind.LANG -> (58 - wave * 5).coerceAtLeast(26)
                        EnemyKind.RUND -> (68 - wave * 5).coerceAtLeast(32)
                        else -> (74 - wave * 6).coerceAtLeast(34)
                    }
                    if (dist > 50f && abs(diff) < 32f) {
                        val ebSpeed = 4.6f + wave * 0.28f
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

            val deadBullets = mutableListOf<Bullet>()
            bullets.forEach { b ->
                b.x = wrapMod(b.x + b.vx, sw)
                b.y = wrapMod(b.y + b.vy, sh)
                b.life--
                if (b.life <= 0) deadBullets += b
            }
            bullets.removeAll(deadBullets)

            powerups.forEach { p ->
                p.y = wrapMod(p.y + 0.45f, sh)
            }
            val deadFx = fx.filter { it.life <= 1 }
            fx.forEach { it.life-- }
            fx.removeAll(deadFx)

            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    val r = when (e.kind) {
                        EnemyKind.BIG -> bigPxSize * 0.42f
                        EnemyKind.RUND -> enemyRundSize * 0.42f
                        EnemyKind.LANG -> enemyLangH * 0.36f
                        EnemyKind.BASIC -> enemyPxSize * 0.42f
                    }
                    if (hypot(shortest(b.x, e.x, sw), shortest(b.y, e.y, sh)) < r) {
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
            bullets.removeAll(hitBullets)
            enemies.removeAll(hitEnemies)

            val enemyHits = mutableSetOf<Bullet>()
            for (b in bullets.filter { !it.fromPlayer }) {
                if (hypot(shortest(b.x, shipPx, sw), shortest(b.y, shipPy, sh)) < shipPxSize * 0.32f) {
                    enemyHits += b
                    fx += Fx(shipPx, shipPy, 10, 0)
                    hurtPlayer()
                }
            }
            bullets.removeAll(enemyHits)

            enemies.toList().forEach { e ->
                val r = when (e.kind) {
                    EnemyKind.BIG -> bigPxSize * 0.42f
                    EnemyKind.RUND -> enemyRundSize * 0.42f
                    EnemyKind.LANG -> enemyLangH * 0.36f
                    EnemyKind.BASIC -> enemyPxSize * 0.42f
                }
                if (hypot(shortest(e.x, shipPx, sw), shortest(e.y, shipPy, sh)) < (r + shipPxSize * 0.28f)) {
                    fx += Fx(e.x, e.y, 14, 1)
                    enemies.remove(e)
                    hurtPlayer()
                }
            }

            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (hypot(shortest(p.x, shipPx, sw), shortest(p.y, shipPy, sh)) < 52f) {
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
            powerups.removeAll(got)
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            w = size.width
            h = size.height
            val sw = size.width
            val sh = size.height

            drawRect(Color(0xFF050510))
            drawMirroredTiled(starFar, starOffX * 0.35f, starOffY * 0.35f, sw, sh)
            drawMirroredTiled(starMid, starOffX * 0.7f, starOffY * 0.7f, sw, sh)
            drawMirroredTiled(starNear, starOffX, starOffY, sw, sh)

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
                drawWrapped(e.x, e.y, sw, sh, maxOf(ew, eh)) { dx, dy ->
                    rotate(degrees = e.angle, pivot = Offset(dx, dy)) {
                        drawSpriteOrFallback(img, e.kind, dx, dy, ew, eh)
                    }
                }
            }

            bullets.forEach { b ->
                drawWrapped(b.x, b.y, sw, sh, 32f) { dx, dy ->
                    rotate(degrees = b.angle, pivot = Offset(dx, dy)) {
                        val img = when {
                            !b.fromPlayer -> bulletEnemy
                            b.triple -> bulletTriple
                            else -> bulletImg
                        }
                        if (img != null) {
                            drawImg(img, dx - bulletW / 2f, dy - bulletH / 2f, bulletW, bulletH)
                        } else {
                            val col = if (b.fromPlayer) Color(0xFF7EC8C4) else Color(0xFFE57373)
                            drawCircle(col, radius = 6f, center = Offset(dx, dy))
                        }
                    }
                }
            }

            powerups.forEach { p ->
                drawWrapped(p.x, p.y, sw, sh, 40f) { dx, dy ->
                    val img = when (p.type) {
                        0 -> puWeapon
                        1 -> puHeal
                        else -> puSpeed
                    }
                    if (img != null) {
                        drawImg(img, dx - 24f, dy - 24f, 48f, 48f)
                    } else {
                        val col = when (p.type) {
                            0 -> Color(0xFF7EC8C4)
                            1 -> Color(0xFF81C784)
                            else -> Color(0xFF90CAF9)
                        }
                        drawCircle(col, radius = 16f, center = Offset(dx, dy))
                    }
                }
            }

            fx.forEach { f ->
                val img = when (f.kind) {
                    0 -> boom1
                    1 -> boom2
                    else -> boom3
                }
                if (img != null) {
                    drawImg(img, f.x - 28f, f.y - 28f, 56f, 56f)
                } else {
                    drawCircle(Color(0x88FFCC80), radius = 12f + f.life, center = Offset(f.x, f.y))
                }
            }

            if (!gameOver) {
                if (iFrames % 4 < 2) {
                    drawWrapped(shipPx, shipPy, sw, sh, shipPxSize) { dx, dy ->
                        rotate(degrees = shipAngle, pivot = Offset(dx, dy)) {
                            if (thrusting) {
                                val flame = Path().apply {
                                    moveTo(dx, dy + shipPxSize * 0.22f)
                                    lineTo(dx - 7f, dy + shipPxSize * 0.38f)
                                    lineTo(dx, dy + shipPxSize * 0.62f + (tick % 3) * 4f)
                                    lineTo(dx + 7f, dy + shipPxSize * 0.38f)
                                    close()
                                }
                                drawPath(flame, Color(0xFFFFCC80))
                            }
                            val curShip = if (multishot > 0) shipTriple else shipSingle
                            if (curShip != null) {
                                drawImg(
                                    curShip,
                                    dx - shipPxSize / 2f,
                                    dy - shipPxSize / 2f,
                                    shipPxSize,
                                    shipPxSize,
                                )
                            } else {
                                drawPlayerFallback(dx, dy, shipPxSize, multishot > 0)
                            }
                            if (muzzleFlash > 0) {
                                val mImg = if (muzzleFlash % 2 == 0) muzzle1 else muzzle2
                                drawImg(mImg, dx - 16f, dy - shipPxSize / 2f - 22f, 32f, 32f)
                            }
                            if (shield > 0) {
                                drawCircle(
                                    Color(0x887EC8C4),
                                    radius = shipPxSize * 0.62f,
                                    center = Offset(dx, dy),
                                    style = Stroke(width = 3f),
                                )
                            }
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

            for (i in 0 until lives) {
                if (heartImg != null) {
                    drawImg(heartImg, 24f + i * 40f, 40f, 32f, 32f)
                } else {
                    drawCircle(Color(0xFFE57373), radius = 10f, center = Offset(40f + i * 40f, 52f))
                }
            }

            if (!gameOver && !won && !paused) {
                drawCircle(
                    Color(0x22E8E4DC),
                    radius = 64f,
                    center = Offset(78f, sh - 96f),
                    style = Stroke(width = 2f),
                )
                if (stickActive) {
                    drawCircle(
                        Color(0x33E8E4DC),
                        radius = 72f,
                        center = Offset(stickOx, stickOy),
                        style = Stroke(width = 2f),
                    )
                    drawCircle(
                        Color(0xA0C9A66B),
                        radius = 24f,
                        center = Offset(stickOx + stickX * 70f, stickOy + stickY * 70f),
                    )
                }
                val fireCol = if (fireHeld) Color(0x557EC8C4) else Color(0x227EC8C4)
                drawCircle(fireCol, radius = 42f, center = Offset(sw - 72f, sh - 96f))
                drawCircle(
                    Color(0x667EC8C4),
                    radius = 42f,
                    center = Offset(sw - 72f, sh - 96f),
                    style = Stroke(width = 2f),
                )
            }
        }

        Box(
            Modifier
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
                                thrusting = false
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
                                            val maxR = 88f
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
                                        thrusting = false
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
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 78.dp, start = 18.dp, end = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
