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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/**
 * FINAL intro captions — Daniel/Stabschef locked (sketch beats 1,3,4,5).
 * Scene art: Looki intro_scene_*_notext (+ Compose overlay). v4b Luke-zu silent
 * panel later; until then 4 notext beats → start card → play.
 */
private val introCaptions = listOf(
    "Major: „Pimpelhuber, ab in die Glocke!“",
    "Pimpelhuber: „Aber Herr Major, wenn das Ding explodiert, spare ich mir vor allem die Rente.“",
    "Major: „Rente gibt es nur für Sieger, Pimpelhuber!“",
    "Major: „Und jetzt rein da, sonst kürze ich Ihre Restlaufzeit auf die nächsten drei Sekunden!“"
)

private const val INTRO_STEP_COUNT = 4
private const val START_CARD_TEXT = "„Start mit voller Leistung!“"

// 0=MENU 1=INTRO 2=START_CARD
private const val PHASE_MENU = 0
private const val PHASE_INTRO = 1
private const val PHASE_START_CARD = 2

@Composable
fun StartScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(PHASE_MENU) }
    var introStep by remember { mutableIntStateOf(0) }
    var menuFrame by remember { mutableIntStateOf(0) }

    val menuBgs = remember {
        listOf(
            loadStargameAsset(context, "menu_bg_1.png"),
            loadStargameAsset(context, "menu_bg_2.png"),
            loadStargameAsset(context, "menu_bg_3.png"),
            loadStargameAsset(context, "menu_bg_4.png")
        )
    }
    val menuLogos = remember {
        listOf(
            loadStargameAsset(context, "menu_logo_1.png"),
            loadStargameAsset(context, "menu_logo_2.png"),
            loadStargameAsset(context, "menu_logo_3.png"),
            loadStargameAsset(context, "menu_logo_4.png")
        )
    }
    // Prefer Looki notext scenes; fall back to stub intro_panel_* if missing
    val introPanels = remember {
        listOf(
            loadStargameAssetOrNull(context, "intro_scene_1_notext.png")
                ?: loadStargameAsset(context, "intro_panel_1.png"),
            loadStargameAssetOrNull(context, "intro_scene_2_notext.png")
                ?: loadStargameAsset(context, "intro_panel_2.png"),
            loadStargameAssetOrNull(context, "intro_scene_3_notext.png")
                ?: loadStargameAsset(context, "intro_panel_3.png"),
            loadStargameAssetOrNull(context, "intro_scene_4_notext.png")
                ?: loadStargameAsset(context, "intro_panel_4.png")
        )
    }
    // Optional silent Luke-zu (v4b); null until Looki ships it
    val lukeZuPanel = remember {
        loadStargameAssetOrNull(context, "intro_scene_luke_zu_notext.png")
            ?: loadStargameAssetOrNull(context, "intro_luke_zu_notext.png")
    }

    val startCardPlayer = remember {
        runCatching {
            MediaPlayer.create(context, R.raw.start_volle_leistung)
        }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose {
            startCardPlayer?.release()
        }
    }

    LaunchedEffect(phase) {
        if (phase == PHASE_MENU) {
            while (true) {
                delay(150)
                menuFrame = (menuFrame + 1) % 4
            }
        }
    }

    // Auto-dismiss start card into play after a short beat (tap also works)
    LaunchedEffect(phase) {
        if (phase == PHASE_START_CARD) {
            runCatching {
                startCardPlayer?.seekTo(0)
                startCardPlayer?.start()
            }
            delay(2200)
            startCardPlayer?.pause()
            onStart()
        }
    }

    val lastIntro = introStep >= INTRO_STEP_COUNT - 1

    fun advanceFromIntro() {
        if (introStep < INTRO_STEP_COUNT - 1) {
            introStep++
        } else {
            // Luke-zu silent image later (v4b); for now go straight to start card
            phase = PHASE_START_CARD
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                when (phase) {
                    PHASE_MENU -> {
                        phase = PHASE_INTRO
                        introStep = 0
                    }
                    PHASE_INTRO -> advanceFromIntro()
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
            drawRect(Color(0xFF050510))
            when (phase) {
                PHASE_MENU -> {
                    val bg = menuBgs[menuFrame % menuBgs.size]
                    drawMenuImg(bg, 0f, 0f, w, h)
                    val logo = menuLogos[menuFrame % menuLogos.size]
                    val lw = w * 0.82f
                    val lh = lw * (logo.height.toFloat() / logo.width.toFloat().coerceAtLeast(1f))
                    val lx = (w - lw) / 2f
                    val ly = h * 0.12f
                    drawMenuImg(logo, lx, ly, lw, lh)
                }
                PHASE_INTRO -> {
                    val panel = introPanels[introStep.coerceIn(0, introPanels.lastIndex)]
                    drawMenuImg(panel, 0f, 0f, w, h)
                }
                PHASE_START_CARD -> {
                    // Hold last intro panel under the start card if present
                    val panel = lukeZuPanel
                        ?: introPanels.getOrNull(introPanels.lastIndex)
                    if (panel != null) drawMenuImg(panel, 0f, 0f, w, h)
                }
            }
        }

        when (phase) {
            PHASE_MENU -> {
                Text(
                    "REICHSZEITGLOCKE",
                    color = Color(0xFFF2E6D0),
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 72.dp)
                )
                Text(
                    "► START GAME",
                    color = Color(0xFFC9A66B),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp)
                )
                Text(
                    "TOUCH-FLY  ·  AUTO-FIRE",
                    color = Color(0xFF8A8680),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                )
            }
            PHASE_INTRO -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC050510))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        introCaptions[introStep.coerceIn(0, introCaptions.lastIndex)],
                        color = Color(0xFFF2E6D0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (lastIntro) "► Tippen …" else "Tippen …",
                        color = Color(0xFF8A8680),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
            PHASE_START_CARD -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f)
                        .background(Color(0xEE0A0A18))
                        .padding(horizontal = 22.dp, vertical = 28.dp)
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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

private fun DrawScope.drawMenuImg(img: ImageBitmap, x: Float, y: Float, dw: Float, dh: Float) {
    drawImage(
        image = img,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.None,
        blendMode = BlendMode.SrcOver
    )
}
