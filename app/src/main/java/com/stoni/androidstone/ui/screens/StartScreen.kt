package com.stoni.androidstone.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/** Interim Start menu — Pussy concept refs until Artiflux finals. */
private const val START_CARD_TEXT = "„Start mit voller Leistung!“"
private const val PHASE_MENU = 0
private const val PHASE_START_CARD = 1

private val SpaceDeep = Color(0xFF0A0E1A)
private val NebulaViolet = Color(0xFF2A1A4A)
private val AccentGold = Color(0xFFE8C547)
private val AccentCyan = Color(0xFF4DE8F0)
private val UiText = Color(0xFFE8ECF4)
private val UiMuted = Color(0xFF8A93A8)
private val PanelEdge = Color(0xFF3A4560)
private val GlassFill = Color(0xAD0C101C) // ~0.68 alpha

private data class MenuStar(val x: Float, val y: Float, val r: Float, val a: Float, val tw: Float)
@Composable
fun StartScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(PHASE_MENU) }
    var tick by remember { mutableIntStateOf(0) }
    var shipT by remember { mutableFloatStateOf(0f) }
    var sheetFrame by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    val menuBg = remember {
        loadStargameAssetOrNull(context, "menu_bg_portrait.png")
            ?: loadStargameAssetOrNull(context, "menu_bg_1.png")
    }
    val trailSheet = remember { loadStargameAssetOrNull(context, "menu_glocke_trail_sheet.png") }
    val ship = remember {
        loadStargameAssetOrNull(context, "player_glocke_single_128.png")
            ?: loadStargameAsset(context, "player_glocke_128.png")
    }
    val thrustFrames = remember {
        listOfNotNull(
            loadStargameAssetOrNull(context, "fx_thrust_1_48.png"),
            loadStargameAssetOrNull(context, "fx_thrust_2_48.png"),
            loadStargameAssetOrNull(context, "fx_thrust_3_48.png"),
            loadStargameAssetOrNull(context, "fx_thrust_4_48.png"),
        )
    }

    val stars = remember {
        val rng = Random(20260920)
        List(48) {
            MenuStar(
                x = rng.nextFloat(),
                y = rng.nextFloat() * 0.72f,
                r = 1.0f + rng.nextFloat() * 2.4f,
                a = 0.28f + rng.nextFloat() * 0.55f,
                tw = rng.nextFloat() * PI.toFloat() * 2f,
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
                shipT += 0.0036f
                if (shipT > 1.28f) shipT = -0.12f
                if (tick % 9 == 0) {
                    sheetFrame = (sheetFrame + 1) % 6
                }
            }
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
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

    Box(Modifier.fillMaxSize().background(SpaceDeep)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Full-bleed navy/violet menu BG (Pussy portrait concept)
            if (menuBg != null) {
                drawCover(menuBg, w, h)
            } else {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(SpaceDeep, NebulaViolet.copy(alpha = 0.85f), SpaceDeep)
                    )
                )
            }

            // Soft nebula accents if BG missing detail
            drawCircle(
                color = NebulaViolet.copy(alpha = 0.16f),
                radius = w * 0.55f,
                center = Offset(w * 0.22f, h * 0.28f),
            )
            drawCircle(
                color = AccentCyan.copy(alpha = 0.05f),
                radius = w * 0.4f,
                center = Offset(w * 0.82f, h * 0.55f),
            )

            stars.forEachIndexed { i, s ->
                val pulse = 0.55f + 0.45f * sin(tick * 0.035f + s.tw)
                val sx = ((s.x * w) + tick * (0.06f + (i % 4) * 0.015f)) % w
                val sy = s.y * h + sin(tick * 0.012f + i) * 3f
                drawCircle(
                    color = Color.White.copy(alpha = (s.a * pulse).coerceIn(0.12f, 0.95f)),
                    radius = s.r * (0.85f + 0.2f * pulse),
                    center = Offset(sx, sy),
                )
            }

            // Loop-sheet hero (6 frames, 2×3) as soft mid-upper vignette
            trailSheet?.let { sheet ->
                val cols = 2
                val rows = 3
                val fw = sheet.width / cols
                val fh = sheet.height / rows
                val col = sheetFrame % cols
                val row = sheetFrame / cols
                val heroW = w * 0.78f
                val heroH = heroW * (fh.toFloat() / fw.toFloat())
                val hx = (w - heroW) / 2f
                val hy = h * 0.14f
                drawImage(
                    image = sheet,
                    srcOffset = IntOffset(col * fw, row * fh),
                    srcSize = IntSize(fw, fh),
                    dstOffset = IntOffset(hx.toInt(), hy.toInt()),
                    dstSize = IntSize(heroW.toInt().coerceAtLeast(1), heroH.toInt().coerceAtLeast(1)),
                    alpha = 0.42f,
                    filterQuality = FilterQuality.Low,
                )
            }

            // Drifting Glocke + gold/cyan trail (diagonal bottom-left → top-right)
            val pathX = -w * 0.18f + shipT * (w * 1.36f)
            val pathY = h * 0.58f - shipT * (h * 0.38f) + sin(shipT * PI.toFloat() * 2f) * (h * 0.035f)
            val heading = -28f + cos(shipT * PI.toFloat() * 2f).toFloat() * 6f
            val shipSize = w * 0.20f

            // Trail sparks behind ship
            for (k in 0 until 14) {
                val t = k / 14f
                val back = shipSize * (0.35f + t * 1.1f)
                val ang = Math.toRadians((heading + 180.0))
                val tx = pathX + cos(ang).toFloat() * back + sin(shipT * 8f + k) * 4f
                val ty = pathY + sin(ang).toFloat() * back + cos(shipT * 7f + k) * 3f
                val gold = k % 2 == 0
                val col = if (gold) AccentGold else AccentCyan
                drawCircle(
                    color = col.copy(alpha = (0.55f - t * 0.45f).coerceAtLeast(0.05f)),
                    radius = shipSize * (0.07f - t * 0.04f),
                    center = Offset(tx, ty),
                )
            }

            rotate(degrees = heading, pivot = Offset(pathX, pathY)) {
                if (thrustFrames.isNotEmpty()) {
                    val tf = thrustFrames[(tick / 3) % thrustFrames.size]
                    val tw = shipSize * 0.42f
                    val th = shipSize * 0.48f
                    drawMenuImg(tf, pathX - tw / 2f, pathY + shipSize * 0.22f, tw, th)
                }
                drawMenuImg(ship, pathX - shipSize / 2f, pathY - shipSize / 2f, shipSize, shipSize)
            }

            // Thumb-zone darken (lower third)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.72f to SpaceDeep.copy(alpha = 0.35f),
                    1f to SpaceDeep.copy(alpha = 0.78f),
                )
            )
        }

        when (phase) {
            PHASE_MENU -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        "STARGAME",
                        color = UiText,
                        fontSize = 34.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .width(72.dp)
                            .height(2.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(AccentCyan.copy(alpha = 0.2f), AccentGold, AccentCyan.copy(alpha = 0.2f))
                                )
                            )
                    )
                    Text(
                        "REICHSZEITGLOCKE",
                        color = UiMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    Spacer(Modifier.weight(1f))

                    // Thumb-zone button stack
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GlassMenuButton(
                            label = "Start",
                            primary = true,
                            onClick = { phase = PHASE_START_CARD },
                        )
                        GlassMenuButton(
                            label = "Einstellungen",
                            primary = false,
                            onClick = { toast = "Einstellungen — bald verfügbar" },
                        )
                        GlassMenuButton(
                            label = "Credits",
                            primary = false,
                            onClick = { toast = "Credits — bald verfügbar" },
                        )
                    }
                }

                toast?.let { msg ->
                    Text(
                        msg,
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp),
                    )
                }
            }

            PHASE_START_CARD -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f)
                        .clickable {
                            startCardPlayer?.pause()
                            onStart()
                        }
                        .background(Color(0xF00A0E1A), RoundedCornerShape(16.dp))
                        .padding(horizontal = 22.dp, vertical = 28.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            START_CARD_TEXT,
                            color = UiText,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "► TIPPEN ZUM START",
                            color = AccentGold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassMenuButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (primary) AccentCyan.copy(alpha = 0.75f) else PanelEdge.copy(alpha = 0.9f)
    val glow = if (primary) AccentCyan.copy(alpha = 0.22f) else AccentGold.copy(alpha = 0.08f)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .height(56.dp),
        shape = shape,
        color = GlassFill,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = if (primary) 8.dp else 4.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            glow,
                            Color.Black.copy(alpha = 0.25f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = UiText,
                fontSize = 16.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

private fun DrawScope.drawCover(img: ImageBitmap, w: Float, h: Float) {
    val iw = img.width.toFloat().coerceAtLeast(1f)
    val ih = img.height.toFloat().coerceAtLeast(1f)
    val scale = maxOf(w / iw, h / ih)
    val dw = iw * scale
    val dh = ih * scale
    val ox = (w - dw) / 2f
    val oy = (h - dh) / 2f
    drawImage(
        image = img,
        dstOffset = IntOffset(ox.toInt(), oy.toInt()),
        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Low,
    )
}

private fun DrawScope.drawMenuImg(
    img: ImageBitmap,
    x: Float,
    y: Float,
    dw: Float,
    dh: Float,
    alpha: Float = 1f,
) {
    drawImage(
        image = img,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}
