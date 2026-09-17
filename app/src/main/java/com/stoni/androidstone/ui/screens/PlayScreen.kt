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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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

private enum class EnemyKind { BASIC, LANG, RUND, BIG }

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
    var hp: Int = 1,
    var fireCd: Int = 60,
    val kind: EnemyKind = EnemyKind.BASIC,
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

    val shipSingle = remember { loadStargameAsset(context, "player_glocke_single_128.png") }
    val shipTriple = remember { loadStargameAsset(context, "player_glocke_triple_128.png") }
    val enemyImg = remember { loadStargameAsset(context, "enemy_stoerer_64.png") }
    val enemyImgB = remember { loadStargameAsset(context, "enemy_stoerer_b_64.png") }
    val enemyBig = remember { loadStargameAsset(context, "enemy_stoerer_big_128.png") }

    val schiffLang = remember { loadStargameAsset(context, "schiff_lang.png") }
    val schiffLangLicht = remember { loadStargameAsset(context, "schiff_lang_licht.png") }
    val schiffRund = remember { loadStargameAsset(context, "schiff_rund.png") }
    val schiffRundLicht = remember { loadStargameAsset(context, "schiff_rund_licht.png") }

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

    var shipPx by remember { mutableFloatStateOf(540f) }
    var shipPy by remember { mutableFloatStateOf(960f) }
    var shipVx by remember { mutableFloatStateOf(0f) }
    var shipVy by remember { mutableFloatStateOf(0f) }
    var shipAngle by remember { mutableFloatStateOf(0f) }
    var fingerX by remember { mutableFloatStateOf(540f) }
    var fingerY by remember { mutableFloatStateOf(700f) }
    var isTouching by remember { mutableStateOf(false) }
    var camX by remember { mutableFloatStateOf(0f) }
    var camY by remember { mutableFloatStateOf(0f) }

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

    LaunchedEffect(w, h) {
        if (w > 10f && h > 10f && shipPx == 540f && shipPy == 960f) {
            shipPx = w / 2f
            shipPy = h / 2f
            fingerX = shipPx
            fingerY = shipPy - 100f
        }
    }

    LaunchedEffect(paused, gameOver, won) {
        while (!paused && !gameOver && !won) {
            delay(20)
            tick++
            val sw = w.coerceAtLeast(1f)
            val sh = h.coerceAtLeast(1f)

            if (iFrames > 0) iFrames--
            if (muzzleFlash > 0) muzzleFlash--

            if (isTouching) {
                val dx = fingerX - (shipPx - camX)
                val dy = fingerY - (shipPy - camY)
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

            bgOffsetX -= shipVx * 0.25f
            bgOffsetY -= shipVy * 0.25f

            val padX = sw * 0.28f
            shipPx = shipPx.coerceIn(-2000f, 2000f)
            shipPy = shipPy.coerceIn(-2000f, 2000f)
            val padY = sh * 0.28f
            if (shipPx - camX < padX) camX = shipPx - padX
            if (shipPx - camX > sw - padX) camX = shipPx - (sw - padX)
            if (shipPy - camY < padY) camY = shipPy - padY
            if (shipPy - camY > sh - padY) camY = shipPy - (sh - padY)
            bgOffsetX = -camX
            bgOffsetY = -camY

            if (fireCd > 0) fireCd-- else {
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

            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--
            val maxSpawn = when (wave) { 1 -> 7; 2 -> 11; else -> 16 }
            if (spawnCd > 0) spawnCd-- else if (spawned < maxSpawn) {
                spawnCd = 60 - wave * 4
                val spawnAngle = (Random.nextFloat() * 2.0 * PI)
                val spawnDist = max(sw, sh) * 0.75f + 120f
                val ex = (shipPx + cos(spawnAngle) * spawnDist).toFloat()
                val ey = (shipPy + sin(spawnAngle) * spawnDist).toFloat()

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
                    fireCd = 45 + Random.nextInt(35),
                    kind = kind
                )
                spawned++
            } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                if (wave < 3) { wave++; spawned = 0; spawnCd = 50 }
                else {
                    won = true
                    banner = "Orbit gesichert. Sieg!"
                    if (score > high) { high = score; prefs.edit { putInt("highscore", score) } }
                }
            }

            enemies.forEach { e ->
                val edx = shipPx - e.x
                val edy = shipPy - e.y
                val dist = hypot(edx, edy)

                e.angle = (atan2(edy, edx) * 180.0 / PI).toFloat() + 90f

                val eSpeed = when (e.kind) {
                    EnemyKind.LANG -> 2.1f + wave * 0.15f
                    EnemyKind.RUND -> 1.2f + wave * 0.10f
                    EnemyKind.BIG -> 0.9f
                    EnemyKind.BASIC -> 1.6f + wave * 0.15f
                }

                if (dist > 12f) {
                    e.x += (edx / dist) * eSpeed
                    e.y += (edy / dist) * eSpeed
                }

                if (e.fireCd > 0) e.fireCd-- else {
                    e.fireCd = when (e.kind) {
                        EnemyKind.LANG -> 65 - wave * 5
                        EnemyKind.RUND -> 75 - wave * 5
                        else -> 80 - wave * 6
                    }

                    if (dist > 15f) {
                        val ebSpeed = 5.2f + wave * 0.3f
                        val ebvx = (edx / dist) * ebSpeed
                        val ebvy = (edy / dist) * ebSpeed

                        if (e.kind == EnemyKind.RUND) {
                            val sp1 = (e.angle - 90f - 15f) * PI / 180.0
                            val sp2 = (e.angle - 90f + 15f) * PI / 180.0
                            bullets += Bullet(e.x, e.y, (cos(sp1) * ebSpeed).toFloat(), (sin(sp1) * ebSpeed).toFloat(), e.angle - 15f, false)
                            bullets += Bullet(e.x, e.y, (cos(sp2) * ebSpeed).toFloat(), (sin(sp2) * ebSpeed).toFloat(), e.angle + 15f, false)
                        } else {
                            bullets += Bullet(e.x, e.y, ebvx, ebvy, e.angle, false)
                        }
                    }
                }
            }

            bullets.forEach { it.x += it.vx; it.y += it.vy }
            powerups.forEach { it.y += 0.5f }
            fx.forEach { it.life-- }
            fx.removeAll { it.life <= 0 }
            bullets.removeAll { hypot(it.x - shipPx, it.y - shipPy) > max(sw, sh) * 1.5f }
            powerups.removeAll { it.y > sh + 50 }

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
                    if (hypot(b.x - e.x, b.y - e.y) < r) {
                        hitBullets += b
                        e.hp--
                        fx += Fx(e.x, e.y, 8, 0)
                        if (e.hp <= 0) {
                            hitEnemies += e
                            fx += Fx(e.x, e.y, 14, 1)
                            fx += Fx(e.x, e.y, 18, 2)
                            val points = when (e.kind) {
                                EnemyKind.BIG -> 60 * wave
                                EnemyKind.RUND -> 40 * wave
                                EnemyKind.LANG -> 25 * wave
                                EnemyKind.BASIC -> 15 * wave
                            }
                            score += points
                            sfx.hit()
                            if (Random.nextFloat() < 0.30f) powerups += PowerUp(e.x, e.y, Random.nextInt(3))
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
                val r = when (e.kind) {
                    EnemyKind.BIG -> bigPxSize * 0.45f
                    EnemyKind.RUND -> enemyRundSize * 0.45f
                    EnemyKind.LANG -> enemyLangH * 0.40f
                    EnemyKind.BASIC -> enemyPxSize * 0.45f
                }
                if (hypot(e.x - shipPx, e.y - shipPy) < (r + shipPxSize * 0.30f)) {
                    fx += Fx(e.x, e.y, 14, 1)
                    enemies.remove(e)
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

            // Nahtloses gespiegeltes Parallax-Gitter (keine Kanten mehr!)
            drawMirroredTiled(starFar, bgOffsetX * 0.25f, bgOffsetY * 0.25f, w, h)
            drawMirroredTiled(starMid, bgOffsetX * 0.50f, bgOffsetY * 0.50f, w, h)
            drawMirroredTiled(starNear, bgOffsetX * 0.90f, bgOffsetY * 0.90f, w, h)

            translate(left = -camX, top = -camY) {
                val half = shipPxSize / 2f

            enemies.forEachIndexed { i, e ->
                val isLichtFrame = ((tick / 8 + i) % 2) == 0
                rotate(degrees = e.angle, pivot = Offset(e.x, e.y)) {
                    when (e.kind) {
                        EnemyKind.LANG -> {
                            val img = if (isLichtFrame) schiffLangLicht else schiffLang
                            drawImg(img, e.x - enemyLangW / 2f, e.y - enemyLangH / 2f, enemyLangW, enemyLangH)
                        }
                        EnemyKind.RUND -> {
                            val img = if (isLichtFrame) schiffRundLicht else schiffRund
                            drawImg(img, e.x - enemyRundSize / 2f, e.y - enemyRundSize / 2f, enemyRundSize, enemyRundSize)
                        }
                        EnemyKind.BIG -> {
                            drawImg(enemyBig, e.x - bigPxSize / 2f, e.y - bigPxSize / 2f, bigPxSize, bigPxSize)
                        }
                        EnemyKind.BASIC -> {
                            val img = if ((tick / 12 + i) % 2 == 0) enemyImg else enemyImgB
                            drawImg(img, e.x - enemyPxSize / 2f, e.y - enemyPxSize / 2f, enemyPxSize, enemyPxSize)
                        }
                    }
                }
            }

            bullets.forEach { b ->
                val bx = b.x - camX
                val by = b.y - camY
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
                val ring = when (p.type) {
                    0 -> Color(0xFFFF5252)
                    1 -> Color(0xFF69F0AE)
                    else -> Color(0xFF00E5FF)
                }
                drawCircle(ring.copy(alpha = 0.22f), 38f, Offset(p.x, p.y))
                drawCircle(color = ring.copy(alpha = 0.85f), radius = 32f, center = Offset(p.x, p.y), style = Stroke(width = 4f))
                val img = when (p.type) { 0 -> puWeapon; 1 -> puHeal; else -> puSpeed }
                val icon = if (p.type == 2) 48f else 38f
                drawImg(img, p.x - icon / 2f, p.y - icon / 2f, icon, icon)
            }

            fx.forEach { f ->
                val img = when (f.kind) { 0 -> boom1; 1 -> boom2; else -> boom3 }
                val s = 72f + (20 - f.life) * 5f
                drawImg(img, f.x - s / 2, f.y - s / 2, s, s)
            }

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
                    val ship = if (multishot > 0) shipTriple else shipSingle
                    drawImg(ship, shipPx - half, shipPy - half, shipPxSize, shipPxSize)

                    if (muzzleFlash > 0) {
                        val m = if (muzzleFlash > 2) muzzle1 else muzzle2
                        val flash = 42f
                        val mouthY = shipPy - half - flash * 0.55f
                        if (multishot > 0) {
                            val offsets = floatArrayOf(-20f, 0f, 20f)
                            for (ox in offsets) {
                                drawImg(m, shipPx + ox - flash / 2f, mouthY, flash, flash)
                            }
                        } else {
                            drawImg(m, shipPx - flash / 2f, mouthY, flash, flash)
                        }
                    }
                }
            }

            if (shield > 0) {
                drawCircle(Color(0x554FC3F7), shipPxSize * 0.6f, Offset(shipPx, shipPy))
            }

            repeat(lives.coerceAtLeast(0)) { i ->
                drawImg(heartImg, 12f + i * 32f, 12f, 28f, 28f)
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

// Spiegelt abwechselnd horizontal & vertikal für 100% nahtlose Kanten
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
