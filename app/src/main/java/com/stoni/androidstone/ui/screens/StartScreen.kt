package com.stoni.androidstone.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoni.androidstone.R
import com.stoni.androidstone.game.loadStargameAsset
import com.stoni.androidstone.game.loadStargameAssetOrNull
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val START_CARD_TEXT = "„Start mit voller Leistung!“"
private const val PHASE_MENU = 0
private const val PHASE_START_CARD = 1

private data class MenuStar(val x: Float, val y: Float, val r: Float, val a: Float, val tw: Float)

@Composable
fun StartScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(PHASE_MENU) }
    var tick by remember { mutableIntStateOf(0) }
    var shipT by remember { mutableFloatStateOf(0f) }

    val ship = remember { loadStargameAsset(context, "player_glocke_single_128.png") }
    val thrustFrames = remember {
        listOfNotNull(
            loadStargameAssetOrNull(context, "fx_thrust_1_48.png"),
            loadStargameAssetOrNull(context, "fx_thrust_2_48.png"),
            loadStargameAssetOrNull(context, "fx_thrust_3_48.png"),
        )
    }
    val menuLogo = remember { loadStargameAssetOrNull(context, "menu_logo_1.png") }

    val stars = remember {
        val rng = Random(2026)
        List(42) {
            MenuStar(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                r = 1.2f + rng.nextFloat() * 2.8f,
                a = 0.35f + rng.nextFloat() * 0.55f,
                tw = rng.nextFloat() * PI.toFloat() * 2f
            )
        }
    }

    val startCardPlayer = remember {
        runCatching { MediaPlayer.create(context, R.raw.start_volle_leistung) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { startCardPlayer?.release() } }

    LaunchedEffect(phase) {
        if (phase == PHASE_MENU) {
            while (true) {
                delay(16)
                tick++
                shipT += 0.0042f
                if (shipT > 1.35f) shipT = -0.15f
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == PHASE_START_CARD) {
            runCatching {
                startCardPlayer?.seekTo(0)
                startCardPlayer?.start()
            }
            delay(1800)
            startCardPlayer?.pause()
            onStart()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                when (phase) {
                    PHASE_MENU -> phase = PHASE_START_CARD
                    PHASE_START_CARD -> {
                        startCardPlayer?.pause()
                        onStart()
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF03020C), Color(0xFF0A0618), Color(0xFF050510))
                )
            )
            drawCircle(
                color = Color(0xFF2A1450).copy(alpha = 0.18f),
                radius = w * 0.55f,
                center = Offset(w * 0.2f, h * 0.3f)
            )
            drawCircle(
                color = Color(0xFF0A3050).copy(alpha = 0.14f),
                radius = w * 0.45f,
                center = Offset(w * 0.85f, h * 0.7f)
            )
            stars.forEachIndexed { i, s ->
                val pulse = 0.55f + 0.45f * sin(tick * 0.04f + s.tw)
                val sx = ((s.x * w) + tick * (0.08f + (i % 5) * 0.02f)) % w
                val sy = (s.y * h + sin(tick * 0.01f + i) * 4f)
                drawCircle(
                    color = Color.White.copy(alpha = (s.a * pulse).coerceIn(0.15f, 1f)),
                    radius = s.r * (0.85f + 0.25f * pulse),
                    center = Offset(sx, sy)
                )
            }

            if (phase == PHASE_MENU || phase == PHASE_START_CARD) {
                val pathX = -w * 0.2f + shipT * (w * 1.4f)
                val pathY = h * 0.42f + sin(shipT * PI.toFloat() * 2f) * (h * 0.06f)
                val heading = 12f + cos(shipT * PI.toFloat() * 2f).toFloat() * 8f
                val shipSize = w * 0.22f
                rotate(degrees = heading, pivot = Offset(pathX, pathY)) {
                    if (thrustFrames.isNotEmpty()) {
                        val tf = thrustFrames[(tick / 4) % thrustFrames.size]
                        val tw = shipSize * 0.38f
                        val th = shipSize * 0.42f
                        drawMenuImg(tf, pathX - tw / 2f, pathY + shipSize * 0.28f, tw, th)
                    } else {
                        for (k in 0..5) {
                            val oy = shipSize * (0.28f + k * 0.07f)
                            val rr = shipSize * (0.06f - k * 0.008f)
                            drawCircle(
                                color = Color(0xFFFF9100).copy(alpha = 0.55f - k * 0.08f),
                                radius = rr,
                                center = Offset(pathX, pathY + oy)
                            )
                        }
                    }
                    drawMenuImg(ship, pathX - shipSize / 2f, pathY - shipSize / 2f, shipSize, shipSize)
                }
                menuLogo?.let { logo ->
                    val lw = w * 0.78f
                    val lh = lw * (logo.height.toFloat() / logo.width.toFloat().coerceAtLeast(1f))
                    drawMenuImg(logo, (w - lw) / 2f, h * 0.10f, lw, lh, alpha = 0.92f)
                }
            }
        }

        when (phase) {
            PHASE_MENU -> {
                if (menuLogo == null) {
                    Text(
                        "REICHSZEITGLOCKE",
                        color = Color(0xFFF2E6D0),
                        fontSize = 26.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 96.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                        .widthIn(min = 220.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFC9A66B), Color(0xFFE8D5A3), Color(0xFFB8893E))
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 36.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "▶  START",
                        color = Color(0xFF1A1208),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                Text(
                    "TOUCH-FLY  ·  AUTO-FIRE",
                    color = Color(0xFF8A8680),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp)
                )
            }
            PHASE_START_CARD -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f)
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .background(Color(0xF00A0A18), RoundedCornerShape(16.dp))
                        .padding(horizontal = 22.dp, vertical = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            START_CARD_TEXT,
                            color = Color(0xFFF2E6D0),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "► TIPPEN ZUM START",
                            color = Color(0xFFC9A66B),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMenuImg(
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
