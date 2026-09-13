package com.stoni.androidstone.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/** Load from assets/ — bypasses aapt drawable crunch completely. */
private fun loadAsset(context: Context, name: String): ImageBitmap {
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
        inDensity = 0
        inTargetDensity = 0
    }
    val bmp = context.assets.open("stargame/$name").use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
    } ?: error("missing asset stargame/$name")
    val argb = if (bmp.config != Bitmap.Config.ARGB_8888) {
        bmp.copy(Bitmap.Config.ARGB_8888, false).also { if (it !== bmp) bmp.recycle() }
    } else bmp
    check(argb.hasAlpha()) { "no alpha on $name — wrong/old asset" }
    return argb.asImageBitmap()
}

private data class Bullet(var x: Float, var y: Float, val dy: Float, val fromPlayer: Boolean, val triple: Boolean = false)
private data class Enemy(var x: Float, var y: Float, var hp: Int = 1, var dx: Float = 1.2f, var fireCd: Int = 40, val big: Boolean = false)
private data class PowerUp(var x: Float, var y: Float, val type: Int)
private data class Fx(var x: Float, var y: Float, var life: Int, val kind: Int)

@Composable
fun PlayScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shipDp = with(density) { 192.dp.toPx() }
    val enemyDp = with(density) { 96.dp.toPx() }
    val bigDp = with(density) { 144.dp.toPx() }
    val prefs = remember { context.getSharedPreferences("stargame", Context.MODE_PRIVATE) }
    var high by remember { mutableIntStateOf(prefs.getInt("highscore", 0)) }

    val shipImg = remember { loadAsset(context, "player_glocke_192.png") }
    val enemyImg = remember { loadAsset(context, "enemy_stoerer_64.png") }
    val enemyImgB = remember { loadAsset(context, "enemy_stoerer_b_64.png") }
    val enemyBig = remember { loadAsset(context, "enemy_stoerer_big_128.png") }
    val bulletImg = remember { loadAsset(context, "bullet_player.png") }
    val bulletTriple = remember { loadAsset(context, "bullet_player_triple.png") }
    val bulletEnemy = remember { loadAsset(context, "bullet_enemy.png") }
    val starFar = remember { loadAsset(context, "bg_stars_far.png") }
    val starMid = remember { loadAsset(context, "bg_stars_mid.png") }
    val starNear = remember { loadAsset(context, "bg_stars_near.png") }
    val beamImg = remember { loadAsset(context, "bg_beam.png") }
    val heartImg = remember { loadAsset(context, "ui_heart.png") }
    val puMulti = remember { loadAsset(context, "powerup_multishot_80.png") }
    val puShield = remember { loadAsset(context, "powerup_shield_80.png") }
    val puSpeed = remember { loadAsset(context, "powerup_speed_80.png") }
    val boom1 = remember { loadAsset(context, "fx_explosion_1.png") }
    val boom2 = remember { loadAsset(context, "fx_explosion_2.png") }
    val boom3 = remember { loadAsset(context, "fx_explosion_3.png") }
    val muzzle1 = remember { loadAsset(context, "fx_muzzle_1.png") }
    val muzzle2 = remember { loadAsset(context, "fx_muzzle_2.png") }
    val magentaProof = remember { loadAsset(context, "alpha_proof_magenta.png") }

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var shipX by remember { mutableFloatStateOf(0.5f) }
    var shipY by remember { mutableFloatStateOf(0.82f) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf("Alpha-Check: Magenta hinter Glocke muss durchscheinen.") }
    var multishot by remember { mutableIntStateOf(0) }
    var shield by remember { mutableIntStateOf(0) }
    var speedBoost by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var starY1 by remember { mutableFloatStateOf(0f) }
    var starY2 by remember { mutableFloatStateOf(0f) }
    var starY3 by remember { mutableFloatStateOf(0f) }
    var beamY by remember { mutableFloatStateOf(0f) }
    var muzzleFlash by remember { mutableIntStateOf(0) }

    val bullets = remember { mutableListOf<Bullet>() }
    val enemies = remember { mutableListOf<Enemy>() }
    val powerups = remember { mutableListOf<PowerUp>() }
    val fx = remember { mutableListOf<Fx>() }
    var fireCd by remember { mutableIntStateOf(0) }
    var spawnCd by remember { mutableIntStateOf(0) }
    var wave by remember { mutableIntStateOf(1) }
    var spawned by remember { mutableIntStateOf(0) }

    fun hurtPlayer() {
        if (shield > 0) { shield = 0; return }
        lives--
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

            starY1 = (starY1 + 0.5f) % sh
            starY2 = (starY2 + 1.0f) % sh
            starY3 = (starY3 + 1.8f) % sh
            beamY = (beamY + 0.7f) % (sh + 120f)
            if (muzzleFlash > 0) muzzleFlash--

            if (fireCd > 0) fireCd-- else {
                fireCd = if (multishot > 0) 10 else 16
                muzzleFlash = 4
                val speed = if (speedBoost > 0) -11f else -9f
                if (multishot > 0) {
                    bullets += Bullet(shipPx, shipPy - shipDp * 0.35f, speed, true, true)
                    bullets += Bullet(shipPx - 32f, shipPy - shipDp * 0.25f, speed, true, true)
                    bullets += Bullet(shipPx + 32f, shipPy - shipDp * 0.25f, speed, true, true)
                } else {
                    bullets += Bullet(shipPx, shipPy - shipDp * 0.35f, speed, true)
                }
            }
            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--

            val maxSpawn = when (wave) { 1 -> 8; 2 -> 12; else -> 16 }
            if (spawnCd > 0) spawnCd-- else if (spawned < maxSpawn) {
                spawnCd = 55 - wave * 4
                val big = wave >= 3 && Random.nextFloat() < 0.25f
                enemies += Enemy(
                    x = Random.nextFloat() * (sw - 100f) + 50f,
                    y = -50f,
                    hp = if (big) 3 else if (wave >= 2) 2 else 1,
                    dx = if (Random.nextBoolean()) 1.1f else -1.1f,
                    fireCd = 50 + Random.nextInt(40),
                    big = big
                )
                spawned++
            } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                if (wave < 3) { wave++; spawned = 0; spawnCd = 50 }
                else {
                    won = true
                    banner = "Orbit ruhig."
                    if (score > high) { high = score; prefs.edit { putInt("highscore", score) } }
                }
            }

            bullets.forEach { it.y += it.dy }
            enemies.forEach { e ->
                e.y += 1.1f + wave * 0.25f
                e.x += e.dx
                if (e.x < 40f || e.x > sw - 40f) e.dx = -e.dx
                if (e.fireCd > 0) e.fireCd-- else {
                    e.fireCd = 70 - wave * 8
                    bullets += Bullet(e.x, e.y + 30f, 5.5f + wave * 0.4f, false)
                }
            }
            powerups.forEach { it.y += 1.6f }
            fx.forEach { it.life-- }
            fx.removeAll { it.life <= 0 }
            bullets.removeAll { it.y < -30 || it.y > sh + 30 }
            enemies.removeAll { e ->
                if (e.y > sh + 50) { hurtPlayer(); true } else false
            }
            powerups.removeAll { it.y > sh + 20 }

            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    val r = if (e.big) bigDp * 0.4f else enemyDp * 0.4f
                    if (abs(b.x - e.x) < r && abs(b.y - e.y) < r) {
                        hitBullets += b
                        e.hp--
                        fx += Fx(e.x, e.y, 8, 0)
                        if (e.hp <= 0) {
                            hitEnemies += e
                            fx += Fx(e.x, e.y, 14, 1)
                            fx += Fx(e.x, e.y, 18, 2)
                            score += if (e.big) 40 * wave else 10 * wave
                            if (Random.nextFloat() < 0.22f) powerups += PowerUp(e.x, e.y, Random.nextInt(3))
                        }
                    }
                }
            }
            bullets.removeAll { it in hitBullets }
            enemies.removeAll { it in hitEnemies }

            val enemyHits = mutableSetOf<Bullet>()
            for (b in bullets.filter { !it.fromPlayer }) {
                if (abs(b.x - shipPx) < shipDp * 0.28f && abs(b.y - shipPy) < shipDp * 0.28f) {
                    enemyHits += b
                    fx += Fx(shipPx, shipPy, 10, 0)
                    hurtPlayer()
                }
            }
            bullets.removeAll { it in enemyHits }

            enemies.toList().forEach { e ->
                val r = if (e.big) bigDp * 0.4f else enemyDp * 0.4f
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

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (!paused && !gameOver && !won) {
                            val scale = if (speedBoost > 0) 1.35f else 1f
                            shipX = (shipX + drag.x / w * scale).coerceIn(0.08f, 0.92f)
                            shipY = (shipY + drag.y / h * scale).coerceIn(0.35f, 0.92f)
                        }
                    }
                }
        ) {
            w = size.width
            h = size.height
            drawRect(Color(0xFF050510))
            drawImg(starFar, 0f, starY1 - h, w, h)
            drawImg(starFar, 0f, starY1, w, h)
            drawImg(starMid, 0f, starY2 - h, w, h)
            drawImg(starMid, 0f, starY2, w, h)
            drawImg(beamImg, w * 0.72f - 16f, beamY - 80f, 32f, 160f)
            drawImg(starNear, 0f, starY3 - h, w, h)
            drawImg(starNear, 0f, starY3, w, h)

            val shipPx = shipX * w
            val shipPy = shipY * h
            val half = shipDp / 2f

            // Magenta proof plate UNDER ship — if white shows instead of magenta, wrong/old path
            drawImg(
                magentaProof,
                shipPx - half - 8f,
                shipPy - half - 8f,
                shipDp + 16f,
                shipDp + 16f
            )
            // also solid magenta fallback strip
            drawRect(
                Color(0xFFFF00FF),
                topLeft = Offset(shipPx - half - 4f, shipPy - half - 4f),
                size = Size(shipDp + 8f, shipDp + 8f)
            )
            drawImg(shipImg, shipPx - half, shipPy - half, shipDp, shipDp)

            if (muzzleFlash > 0) {
                val m = if (muzzleFlash > 2) muzzle1 else muzzle2
                drawImg(m, shipPx - 28f, shipPy - half - 36f, 56f, 56f)
            }
            if (shield > 0) {
                drawCircle(Color(0x554FC3F7), shipDp * 0.42f, Offset(shipPx, shipPy))
            }

            enemies.forEachIndexed { i, e ->
                // magenta plate under enemies too
                val sz = if (e.big) bigDp else enemyDp
                drawRect(
                    Color(0xFFFF00FF),
                    topLeft = Offset(e.x - sz / 2f - 2f, e.y - sz / 2f - 2f),
                    size = Size(sz + 4f, sz + 4f)
                )
                if (e.big) {
                    drawImg(enemyBig, e.x - bigDp / 2f, e.y - bigDp / 2f, bigDp, bigDp)
                } else {
                    val img = if ((tick / 12 + i) % 2 == 0) enemyImg else enemyImgB
                    drawImg(img, e.x - enemyDp / 2f, e.y - enemyDp / 2f, enemyDp, enemyDp)
                }
            }
            bullets.forEach { b ->
                if (b.fromPlayer) {
                    val img = if (b.triple) bulletTriple else bulletImg
                    drawImg(img, b.x - 8f, b.y - 16f, 16f, 32f)
                } else {
                    drawImg(bulletEnemy, b.x - 8f, b.y - 12f, 16f, 24f)
                }
            }
            powerups.forEach { p ->
                val img = when (p.type) { 0 -> puMulti; 1 -> puShield; else -> puSpeed }
                drawImg(img, p.x - 40f, p.y - 40f, 80f, 80f)
            }
            fx.forEach { f ->
                val img = when (f.kind) { 0 -> boom1; 1 -> boom2; else -> boom3 }
                val s = 48f + (14 - f.life) * 2f
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
