package com.stoni.androidstone.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

enum class EnemyType(
    val baseRadius: Float,
    val speed: Float,
    val maxHp: Float,
    val color: Color,
    val scoreValue: Int
) {
    SWARMER(14f, 4.5f, 1f, Color(0xFF00FF9D), 10),
    SCOUT(22f, 3.0f, 3f, Color(0xFFFF5252), 25),
    TANK(38f, 1.4f, 10f, Color(0xFFFF9100), 75),
    BOSS(65f, 0.9f, 60f, Color(0xFFE040FB), 500)
}

data class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var lifeTime: Float = 120f
)

data class Enemy(
    val id: Long = Random.nextLong(),
    var x: Float,
    var y: Float,
    var hp: Float,
    val maxHp: Float,
    val type: EnemyType
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    val color: Color,
    var size: Float
)

class ShooterEngine {
    var screenWidth = 1080f
    var screenHeight = 1920f

    var playerX by mutableFloatStateOf(540f)
    var playerY by mutableFloatStateOf(960f)
    var playerAngle by mutableFloatStateOf(-90f)
    var playerHp by mutableFloatStateOf(100f)
    val playerMaxHp = 100f

    /** Finger position in screen space (updated by input). */
    var touchScreenX by mutableFloatStateOf(540f)
    var touchScreenY by mutableFloatStateOf(500f)
    var isTouching by mutableStateOf(false)
    var shootCooldown = 0

    /** Camera top-left in world space; player stays screen-centered. */
    val camX: Float get() = playerX - screenWidth / 2f
    val camY: Float get() = playerY - screenHeight / 2f

    val bullets = mutableStateListOf<Bullet>()
    val enemies = mutableStateListOf<Enemy>()
    val particles = mutableStateListOf<Particle>()

    var score by mutableIntStateOf(0)
    var wave by mutableIntStateOf(1)
    var isGameOver by mutableStateOf(false)
    var isBossActive by mutableStateOf(false)
    var bossHpCurrent by mutableFloatStateOf(0f)
    var bossHpMax by mutableFloatStateOf(1f)

    private var spawnTimer = 0
    private var enemiesSpawnedInWave = 0
    private val enemiesPerWave get() = 10 + (wave * 5)

    /** Off-camera despawn radius (world units from player). */
    private val despawnDist: Float
        get() = max(screenWidth, screenHeight) * 0.85f + 280f

    fun reset() {
        playerX = screenWidth / 2f
        playerY = screenHeight / 2f
        playerHp = playerMaxHp
        playerAngle = -90f
        touchScreenX = screenWidth / 2f
        touchScreenY = screenHeight / 2f - 200f
        bullets.clear()
        enemies.clear()
        particles.clear()
        score = 0
        wave = 1
        isGameOver = false
        isBossActive = false
        spawnTimer = 0
        enemiesSpawnedInWave = 0
        shootCooldown = 0
    }

    fun update() {
        if (isGameOver) return

        // World-space aim/fly target from screen touch + current camera
        val targetWorldX = touchScreenX + camX
        val targetWorldY = touchScreenY + camY
        val dx = targetWorldX - playerX
        val dy = targetWorldY - playerY
        val aimDist = hypot(dx, dy)

        if (aimDist > 1f) {
            playerAngle = (atan2(dy, dx) * 180f / PI).toFloat()
        }

        // Touch-fly: lerp player toward finger (world)
        if (isTouching && aimDist > 2f) {
            val lerp = 0.16f
            playerX += dx * lerp
            playerY += dy * lerp
        }

        if (isTouching) {
            shootCooldown--
            if (shootCooldown <= 0) {
                shootCooldown = 7
                val rad = playerAngle * PI / 180.0
                val speed = 20f
                val vx = (cos(rad) * speed).toFloat()
                val vy = (sin(rad) * speed).toFloat()
                val muzzleX = playerX + (cos(rad) * 35f).toFloat()
                val muzzleY = playerY + (sin(rad) * 35f).toFloat()

                bullets.add(Bullet(muzzleX, muzzleY, vx, vy))
                particles.add(
                    Particle(
                        muzzleX, muzzleY,
                        vx * 0.1f + (Random.nextFloat() - 0.5f) * 2f,
                        vy * 0.1f + (Random.nextFloat() - 0.5f) * 2f,
                        1f, Color(0xFF00E5FF), 5f
                    )
                )
            }
        }

        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val b = bulletIterator.next()
            b.x += b.vx
            b.y += b.vy
            b.lifeTime--

            val far = hypot(b.x - playerX, b.y - playerY) > despawnDist
            if (b.lifeTime <= 0 || far) {
                bulletIterator.remove()
            }
        }

        spawnTimer++
        val spawnRate = max(18, 60 - (wave * 4))

        if (enemiesSpawnedInWave < enemiesPerWave) {
            if (spawnTimer >= spawnRate) {
                spawnTimer = 0
                spawnRandomEnemy()
                enemiesSpawnedInWave++
            }
        } else if (enemies.isEmpty() && !isBossActive) {
            if (wave % 3 == 0) {
                spawnBoss()
            } else {
                wave++
                enemiesSpawnedInWave = 0
            }
        }

        val enemyIterator = enemies.iterator()
        while (enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()

            val toPlayerX = playerX - enemy.x
            val toPlayerY = playerY - enemy.y
            val dist = hypot(toPlayerX, toPlayerY)

            if (dist > 5f) {
                enemy.x += (toPlayerX / dist) * enemy.type.speed
                enemy.y += (toPlayerY / dist) * enemy.type.speed
            }

            if (dist < (enemy.type.baseRadius + 22f)) {
                val damage = when (enemy.type) {
                    EnemyType.SWARMER -> 8f
                    EnemyType.SCOUT -> 15f
                    EnemyType.TANK -> 30f
                    EnemyType.BOSS -> 50f
                }
                playerHp -= damage
                createExplosion(enemy.x, enemy.y, enemy.type.color, 12)

                if (enemy.type != EnemyType.BOSS) {
                    enemyIterator.remove()
                }

                if (playerHp <= 0f) {
                    playerHp = 0f
                    isGameOver = true
                    createExplosion(playerX, playerY, Color(0xFF00E5FF), 40)
                }
                continue
            }

            for (bullet in bullets) {
                val hitDist = hypot(bullet.x - enemy.x, bullet.y - enemy.y)
                if (hitDist <= (enemy.type.baseRadius + 8f)) {
                    enemy.hp -= 1f
                    bullet.lifeTime = 0f
                    createExplosion(bullet.x, bullet.y, enemy.type.color, 4)

                    if (enemy.type == EnemyType.BOSS) {
                        bossHpCurrent = max(0f, enemy.hp)
                    }

                    if (enemy.hp <= 0f) {
                        score += enemy.type.scoreValue
                        createExplosion(enemy.x, enemy.y, enemy.type.color, 24)

                        if (enemy.type == EnemyType.BOSS) {
                            isBossActive = false
                            wave++
                            enemiesSpawnedInWave = 0
                        }

                        enemyIterator.remove()
                        break
                    }
                }
            }
        }

        val partIterator = particles.iterator()
        while (partIterator.hasNext()) {
            val p = partIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 0.035f
            if (p.alpha <= 0f) {
                partIterator.remove()
            }
        }
    }

    private fun spawnRandomEnemy() {
        val spawnAngle = Random.nextFloat() * 2f * PI.toFloat()
        val spawnDist = max(screenWidth, screenHeight) * 0.72f + 100f
        val spawnX = playerX + cos(spawnAngle) * spawnDist
        val spawnY = playerY + sin(spawnAngle) * spawnDist

        val type = when {
            wave >= 2 && Random.nextFloat() < 0.20f -> EnemyType.TANK
            wave >= 2 && Random.nextFloat() < 0.35f -> EnemyType.SWARMER
            else -> EnemyType.SCOUT
        }

        enemies.add(Enemy(x = spawnX, y = spawnY, hp = type.maxHp, maxHp = type.maxHp, type = type))
    }

    private fun spawnBoss() {
        isBossActive = true
        val bossHp = 45f + (wave * 25f)
        bossHpMax = bossHp
        bossHpCurrent = bossHp

        enemies.add(
            Enemy(
                x = playerX,
                y = playerY - screenHeight * 0.42f - 80f,
                hp = bossHp,
                maxHp = bossHp,
                type = EnemyType.BOSS
            )
        )
    }

    private fun createExplosion(x: Float, y: Float, color: Color, count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * 2f * PI
            val speed = Random.nextFloat() * 7f + 1.5f
            particles.add(
                Particle(
                    x = x, y = y,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    alpha = 1f, color = color,
                    size = Random.nextFloat() * 6f + 3f
                )
            )
        }
    }
}

@Composable
fun PlayScreen(onBackToMenu: () -> Unit = {}) {
    val engine = remember { ShooterEngine() }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {
                engine.update()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        engine.isTouching = true
                        engine.touchScreenX = offset.x
                        engine.touchScreenY = offset.y
                    },
                    onDrag = { change, _ ->
                        engine.touchScreenX = change.position.x
                        engine.touchScreenY = change.position.y
                    },
                    onDragEnd = { engine.isTouching = false },
                    onDragCancel = { engine.isTouching = false }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        engine.isTouching = true
                        engine.touchScreenX = offset.x
                        engine.touchScreenY = offset.y
                        tryAwaitRelease()
                        engine.isTouching = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (engine.screenWidth != size.width || engine.screenHeight != size.height) {
                val first = engine.screenWidth == 1080f && engine.playerX == 540f
                engine.screenWidth = size.width
                engine.screenHeight = size.height
                if (first) {
                    engine.playerX = size.width / 2f
                    engine.playerY = size.height / 2f
                    engine.touchScreenX = size.width / 2f
                    engine.touchScreenY = size.height / 2f - 200f
                }
            }

            val camX = engine.camX
            val camY = engine.camY

            // World grid (camera-relative)
            val spacing = 80f
            var curX = -((camX % spacing) + spacing) % spacing
            while (curX < size.width) {
                drawLine(
                    Color(0xFF151824),
                    start = Offset(curX, 0f),
                    end = Offset(curX, size.height),
                    strokeWidth = 1.5f
                )
                curX += spacing
            }
            var curY = -((camY % spacing) + spacing) % spacing
            while (curY < size.height) {
                drawLine(
                    Color(0xFF151824),
                    start = Offset(0f, curY),
                    end = Offset(size.width, curY),
                    strokeWidth = 1.5f
                )
                curY += spacing
            }

            // Particles (world → screen)
            for (p in engine.particles) {
                drawCircle(
                    color = p.color.copy(alpha = max(0f, p.alpha)),
                    radius = p.size,
                    center = Offset(p.x - camX, p.y - camY)
                )
            }

            // Lasers
            for (b in engine.bullets) {
                val sx = b.x - camX
                val sy = b.y - camY
                drawCircle(color = Color(0xFF00E5FF), radius = 5f, center = Offset(sx, sy))
                drawLine(
                    color = Color.White,
                    start = Offset(sx, sy),
                    end = Offset(sx - b.vx * 1.5f, sy - b.vy * 1.5f),
                    strokeWidth = 3f
                )
            }

            // Enemies
            for (enemy in engine.enemies) {
                val r = enemy.type.baseRadius
                val center = Offset(enemy.x - camX, enemy.y - camY)
                when (enemy.type) {
                    EnemyType.SWARMER -> {
                        val path = Path().apply {
                            moveTo(center.x, center.y - r)
                            lineTo(center.x + r, center.y)
                            lineTo(center.x, center.y + r)
                            lineTo(center.x - r, center.y)
                            close()
                        }
                        drawPath(path, enemy.type.color)
                    }
                    EnemyType.SCOUT -> {
                        drawCircle(enemy.type.color, radius = r, center = center)
                        drawCircle(Color.Black, radius = r * 0.45f, center = center)
                    }
                    EnemyType.TANK -> {
                        drawCircle(enemy.type.color, radius = r, center = center)
                        drawCircle(Color(0xFF3E1E00), radius = r * 0.65f, center = center)
                    }
                    EnemyType.BOSS -> {
                        drawCircle(enemy.type.color, radius = r, center = center)
                        drawCircle(Color.White, radius = r * 0.35f, center = center)
                    }
                }

                if (enemy.type != EnemyType.SWARMER && enemy.hp < enemy.maxHp) {
                    val barWidth = r * 2f
                    val barX = center.x - r
                    val barY = center.y - r - 8f
                    drawRect(Color(0xFF333333), topLeft = Offset(barX, barY), size = Size(barWidth, 4f))
                    drawRect(
                        Color(0xFF00FF9D),
                        topLeft = Offset(barX, barY),
                        size = Size(barWidth * (enemy.hp / enemy.maxHp), 4f)
                    )
                }
            }

            // Player (screen-centered via camera)
            if (!engine.isGameOver) {
                val px = engine.playerX - camX
                val py = engine.playerY - camY
                rotate(degrees = engine.playerAngle + 90f, pivot = Offset(px, py)) {
                    val shipPath = Path().apply {
                        moveTo(px, py - 28f)
                        lineTo(px + 18f, py + 20f)
                        lineTo(px, py + 12f)
                        lineTo(px - 18f, py + 20f)
                        close()
                    }
                    drawPath(path = shipPath, color = Color(0xFF00E5FF))
                    drawCircle(color = Color.White, radius = 5f, center = Offset(px, py - 4f))
                }

                if (engine.isTouching) {
                    drawCircle(
                        color = Color(0x6600E5FF),
                        radius = 20f,
                        center = Offset(engine.touchScreenX, engine.touchScreenY),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }

        // HUD (screen-fixed)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 42.dp, start = 18.dp, end = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SCORE: ${engine.score}",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "WAVE ${engine.wave}",
                        color = Color(0xFF00E5FF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "HP: ${engine.playerHp.toInt()}%",
                        color = if (engine.playerHp > 30f) Color(0xFF00FF9D) else Color(0xFFFF5252),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { engine.playerHp / engine.playerMaxHp },
                        modifier = Modifier.width(130.dp).height(10.dp),
                        color = if (engine.playerHp > 30f) Color(0xFF00FF9D) else Color(0xFFFF5252),
                        trackColor = Color(0xFF222230),
                    )
                }
            }

            if (engine.isBossActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "⚠ SECTOR BOSS ⚠",
                        color = Color(0xFFE040FB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { engine.bossHpCurrent / engine.bossHpMax },
                        modifier = Modifier.fillMaxWidth(0.85f).height(12.dp),
                        color = Color(0xFFE040FB),
                        trackColor = Color(0xFF35123D),
                    )
                }
            }
        }

        if (engine.isGameOver) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "SYSTEM DESTROYED",
                            color = Color(0xFFFF5252),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Erreichte Welle: ${engine.wave}", color = Color.LightGray, fontSize = 16.sp)
                        Text(
                            "Punkte: ${engine.score}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { engine.reset() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("NEUSTART", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
