package com.stoni.androidstone.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class IntroBeat(val speaker: String, val line: String)

private val introBeats = listOf(
    IntroBeat("MAJOR", "Pimpelhuber! Die Karte des Sieges. Sofort."),
    IntroBeat("PIMPELHUBER", "Zu Befehl, Herr Major!"),
    IntroBeat("MAJOR", "Du fliegst in die Reichszeitglocke. Allein."),
    IntroBeat("PIMPELHUBER", "In… die Glocke, Herr Major?"),
    IntroBeat("MAJOR", "Rein. Luke zu. Und bring den Orbit zur Ruhe."),
    IntroBeat("LUKE", "Luke zu. Start."),
    IntroBeat("", "Tippen zum Start der Mission.")
)

private fun wrapScroll(v: Float, span: Float): Float {
    if (span <= 0f) return 0f
    var x = v % span
    if (x < 0f) x += span
    return x
}

private fun loadMenuAsset(context: Context, name: String): ImageBitmap {
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
    return argb.asImageBitmap()
}

@Composable
fun StartScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var starY1 by remember { mutableFloatStateOf(0f) }
    var starY2 by remember { mutableFloatStateOf(0f) }
    var starY3 by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(1f) }

    val starFar = remember { loadMenuAsset(context, "bg_stars_far.png") }
    val starMid = remember { loadMenuAsset(context, "bg_stars_mid.png") }
    val starNear = remember { loadMenuAsset(context, "bg_stars_near.png") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(32)
            val sh = canvasH.coerceAtLeast(1f)
            starY1 = wrapScroll(starY1 + 0.45f, sh)
            starY2 = wrapScroll(starY2 + 0.9f, sh)
            starY3 = wrapScroll(starY3 + 1.6f, sh)
        }
    }

    val beat = introBeats[step.coerceIn(0, introBeats.lastIndex)]
    val ready = step >= introBeats.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (step < introBeats.lastIndex) step++ else onStart()
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            canvasH = size.height
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF050510))
            drawMenuImg(starFar, 0f, starY1 - h, w, h)
            drawMenuImg(starFar, 0f, starY1, w, h)
            drawMenuImg(starMid, 0f, starY2 - h, w, h)
            drawMenuImg(starMid, 0f, starY2, w, h)
            drawMenuImg(starNear, 0f, starY3 - h, w, h)
            drawMenuImg(starNear, 0f, starY3, w, h)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
        ) {
            Text(
                "STARGAME",
                color = Color(0xFFC9A66B),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
            Text(
                "REICHSZEITGLOCKE V-3",
                color = Color(0xFF8A8680),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(28.dp))
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
            Spacer(Modifier.height(18.dp))
            Text(
                if (ready) "► TIPPEN ZUM START" else "Tippen …",
                color = if (ready) Color(0xFFC9A66B) else Color(0xFF8A8680),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (ready) FontWeight.Bold else FontWeight.Normal
            )
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
