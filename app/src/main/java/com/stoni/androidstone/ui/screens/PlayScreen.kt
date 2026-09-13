package com.stoni.androidstone.ui.screens

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.stoni.androidstone.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private data class Bullet(var x: Float, var y: Float, val dy: Float, val fromPlayer: Boolean)
private data class Enemy(var x: Float, var y: Float, var hp: Int = 1)
private data class PowerUp(var x: Float, var y: Float, val type: Int) // 0 multi 1 shield 2 speed

@Composable
fun PlayScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("stargame", Context.MODE_PRIVATE) }
    var high by remember { mutableIntStateOf(prefs.getInt("highscore", 0)) }

    val shipImg = imageResource(R.drawable.player_glocke_96)
    val enemyImg = imageResource(R.drawable.enemy_stoerer_64)
    val bulletImg = imageResource(R.drawable.bullet_player)
    val starFar = imageResource(R.drawable.bg_stars_far)
    val starNear = imageResource(R.drawable.bg_stars_near)
    val heartImg = imageResource(R.drawable.ui_heart)
    val puMulti = imageResource(R.drawable.powerup_multishot_48)
    val puShield = imageResource(R.drawable.powerup_shield_48)
    val puSpeed = imageResource(R.drawable.powerup_speed_48)

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var shipX by remember { mutableFloatStateOf(0.5f) }
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

    val bullets = remember { mutableListOf<Bullet>() }
    val enemies = remember { mutableListOf<Enemy>() }
    val powerups = remember { mutableListOf<PowerUp>() }
    var fireCd by remember { mutableIntStateOf(0) }
    var spawnCd by remember { mutableIntStateOf(0) }
    var wave by remember { mutableIntStateOf(1) }
    var spawned by remember { mutableIntStateOf(0) }

    LaunchedEffect(paused, gameOver, won) {
        while (!paused && !gameOver && !won) {
            delay(16)
            tick++
            val sw = w.coerceAtLeast(1f)
            val sh = h.coerceAtLeast(1f)
            val shipPx = shipX * sw
            val shipPy = sh * 0.88f
            val moveSpeed = if (speedBoost > 0) 0.025f else 0.018f

            starY1 = (starY1 + 1.2f) % sh
            starY2 = (starY2 + 2.4f) % sh

            if (fireCd > 0) fireCd-- else {
                fireCd = if (multishot > 0) 8 else 12
                bullets += Bullet(shipPx, shipPy - 40f, -14f, true)
                if (multishot > 0) {
                    bullets += Bullet(shipPx - 28f, shipPy - 30f, -14f, true)
                    bullets += Bullet(shipPx + 28f, shipPy - 30f, -14f, true)
                }
            }
            if (multishot > 0) multishot--
            if (shield > 0) shield--
            if (speedBoost > 0) speedBoost--

            val maxSpawn = when (wave) {
                1 -> 8
                2 -> 14
                else -> 20
            }
            if (spawnCd > 0) spawnCd-- else if (spawned < maxSpawn) {
                spawnCd = 45 - wave * 5
                enemies += Enemy(Random.nextFloat() * (sw - 80f) + 40f, -40f, if (wave >= 3) 2 else 1)
                spawned++
                if (spawned == 4 && wave == 1) banner = "Störfeuer. Bleib im Klang."
                if (spawned == 8 && wave == 2) banner = "Die Welle wird dichter. Nicht zittern."
                if (wave == 3 && spawned == 1) banner = "Ursprung-Echo. Ein Schlag — dann Stille."
            } else if (enemies.isEmpty() && bullets.none { !it.fromPlayer }) {
                if (wave < 3) {
                    wave++
                    spawned = 0
                    spawnCd = 40
                } else {
                    won = true
                    banner = "Orbit ruhig. Highscore speichern?"
                    if (score > high) {
                        high = score
                        prefs.edit { putInt("highscore", score) }
                    }
                }
            }

            // move
            bullets.forEach { it.y += it.dy }
            enemies.forEach { it.y += 2.2f + wave * 0.4f }
            powerups.forEach { it.y += 2.5f }
            bullets.removeAll { it.y < -20 || it.y > sh + 20 }
            enemies.removeAll { e ->
                if (e.y > sh + 40) {
                    if (shield <= 0) lives-- else shield = 0
                    if (lives <= 0) {
                        gameOver = true
                        banner = "Überstimmt. Nochmal?"
                        if (score > high) {
                            high = score
                            prefs.edit { putInt("highscore", score) }
                        }
                    }
                    true
                } else false
            }
            powerups.removeAll { it.y > sh + 20 }

            // collisions player bullets vs enemies
            val hitEnemies = mutableSetOf<Enemy>()
            val hitBullets = mutableSetOf<Bullet>()
            for (b in bullets.filter { it.fromPlayer }) {
                for (e in enemies) {
                    if (abs(b.x - e.x) < 36 && abs(b.y - e.y) < 36) {
                        hitBullets += b
                        e.hp--
                        if (e.hp <= 0) {
                            hitEnemies += e
                            score += 10 * wave
                            if (Random.nextFloat() < 0.18f) {
                                powerups += PowerUp(e.x, e.y, Random.nextInt(3))
                            }
                        }
                    }
                }
            }
            bullets.removeAll { it in hitBullets }
            enemies.removeAll { it in hitEnemies }

            // powerup pickup
            val got = mutableSetOf<PowerUp>()
            for (p in powerups) {
                if (abs(p.x - shipPx) < 40 && abs(p.y - shipPy) < 40) {
                    got += p
                    when (p.type) {
                        0 -> { multishot = 300; banner = "Mehrschuss — klarerer Ton." }
                        1 -> { shield = 300; banner = "Schutzton aktiv." }
                        2 -> { speedBoost = 300; banner = "Resonanz beschleunigt." }
                    }
                }
            }
            powerups.removeAll { it in got }

            // unused moveSpeed keeps swipe responsive via drag
            @Suppress("UNUSED_VARIABLE")
            val _ms = moveSpeed
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
                            shipX = (shipX + drag.x / w).coerceIn(0.08f, 0.92f)
                        }
                    }
                }
        ) {
            w = size.width
            h = size.height
            // parallax
            drawImg(starFar, 0f, starY1 - h, w, h)
            drawImg(starFar, 0f, starY1, w, h)
            drawImg(starNear, 0f, starY2 - h, w, h)
            drawImg(starNear, 0f, starY2, w, h)

            val shipPx = shipX * w
            val shipPy = h * 0.88f
            drawImg(shipImg, shipPx - 48f, shipPy - 48f, 96f, 96f)
            if (shield > 0) {
                drawCircle(Color(0x664FC3F7), 56f, Offset(shipPx, shipPy))
            }
            enemies.forEach { drawImg(enemyImg, it.x - 32f, it.y - 32f, 64f, 64f) }
            bullets.filter { it.fromPlayer }.forEach {
                drawImg(bulletImg, it.x - 8f, it.y - 16f, 16f, 32f)
            }
            powerups.forEach { p ->
                val img = when (p.type) {
                    0 -> puMulti
                    1 -> puShield
                    else -> puSpeed
                }
                drawImg(img, p.x - 24f, p.y - 24f, 48f, 48f)
            }
            // HUD hearts
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
            Text(
                "Pause — der Klang hält.",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
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
        filterQuality = FilterQuality.None
    )
}
