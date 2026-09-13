package com.stoni.androidstone.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.stoni.androidstone.game.loadStargameAsset
import kotlinx.coroutines.delay

private data class IntroBeat(val speaker: String, val line: String)

/** Klugscheißer dialogs as TEXT OVERLAY — Daniel exact on beat 1. */
private val introBeats = listOf(
    IntroBeat("MAJOR", "Holen Sie die Karte, Pimpelhuber — die Karte des Sieges."),
    IntroBeat("PIMPELHUBER", "Zu Befehl, Herr Major!"),
    IntroBeat("MAJOR", "Du fliegst in die Reichszeitglocke. Allein."),
    IntroBeat("PIMPELHUBER", "In… die Glocke, Herr Major?"),
    IntroBeat("MAJOR", "Rein. Luke zu. Und bring den Orbit zur Ruhe."),
    IntroBeat("LUKE", "Luke zu. Start.")
)


@Composable
fun StartScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(0) } // 0=MENU, 1=INTRO
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
    val introPanels = remember {
        listOf(
            loadStargameAsset(context, "intro_panel_1.png"),
            loadStargameAsset(context, "intro_panel_2.png"),
            loadStargameAsset(context, "intro_panel_3.png"),
            loadStargameAsset(context, "intro_panel_4.png"),
            loadStargameAsset(context, "intro_panel_5.png"),
            loadStargameAsset(context, "intro_panel_6.png")
        )
    }

    LaunchedEffect(phase) {
        if (phase == 0) {
            while (true) {
                delay(150)
                menuFrame = (menuFrame + 1) % 4
            }
        }
    }

    val inMenu = phase == 0
    val beat = introBeats[introStep.coerceIn(0, introBeats.lastIndex)]
    val lastIntro = introStep >= introBeats.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (inMenu) {
                    phase = 1
                    introStep = 0
                } else if (introStep < introBeats.lastIndex) {
                    introStep++
                } else {
                    onStart()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF050510))
            if (inMenu) {
                val bg = menuBgs[menuFrame % menuBgs.size]
                drawMenuImg(bg, 0f, 0f, w, h)
                val logo = menuLogos[menuFrame % menuLogos.size]
                // Logo centered upper third, preserve aspect roughly
                val lw = w * 0.82f
                val lh = lw * (logo.height.toFloat() / logo.width.toFloat().coerceAtLeast(1f))
                val lx = (w - lw) / 2f
                val ly = h * 0.12f
                drawMenuImg(logo, lx, ly, lw, lh)
            } else {
                val panel = introPanels[introStep.coerceIn(0, introPanels.lastIndex)]
                drawMenuImg(panel, 0f, 0f, w, h)
            }
        }

        if (inMenu) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    "► TIPPEN ZUM INTRO",
                    color = Color(0xFFC9A66B),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 28.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC12121C), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFC9A66B).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    if (beat.speaker.isNotEmpty()) {
                        Text(
                            beat.speaker,
                            color = Color(0xFF69F0AE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = beat.line,
                        color = Color(0xFFF2E6D0),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (lastIntro) "► TIPPEN ZUM START" else "Tippen …",
                    color = if (lastIntro) Color(0xFFC9A66B) else Color(0xFF8A8680),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (lastIntro) FontWeight.Bold else FontWeight.Normal
                )
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
