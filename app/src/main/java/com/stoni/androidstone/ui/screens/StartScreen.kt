package com.stoni.androidstone.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoni.androidstone.R
import com.stoni.androidstone.game.loadStargameAssetOrNull
import kotlinx.coroutines.delay

/** Artiflux Premium Title Pack v1 — 9:16 Start menu. */
private const val START_CARD_TEXT = "„Start mit voller Leistung!“"
private const val PHASE_MENU = 0
private const val PHASE_START_CARD = 1

private val SpaceDeep = Color(0xFF0A0E1A)
private val AccentGold = Color(0xFFE8C547)
private val AccentCyan = Color(0xFF4DE8F0)
private val UiText = Color(0xFFE8ECF4)

@Composable
fun StartScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(PHASE_MENU) }
    var trailFrame by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    val bgPremium = remember {
        loadStargameAssetOrNull(context, "menu_bg_premium_720x1280.png")
            ?: loadStargameAssetOrNull(context, "menu_bg_portrait.png")
            ?: loadStargameAssetOrNull(context, "menu_bg_1.png")
    }
    val bgNebula = remember { loadStargameAssetOrNull(context, "menu_bg_nebula.png") }
    val bgStars = remember { loadStargameAssetOrNull(context, "menu_bg_stars.png") }

    val trailFrames = remember {
        listOfNotNull(
            loadStargameAssetOrNull(context, "menu_glocke_trail_1.png"),
            loadStargameAssetOrNull(context, "menu_glocke_trail_2.png"),
            loadStargameAssetOrNull(context, "menu_glocke_trail_3.png"),
            loadStargameAssetOrNull(context, "menu_glocke_trail_4.png"),
        ).ifEmpty {
            listOfNotNull(loadStargameAssetOrNull(context, "menu_glocke_trail_master.png"))
        }
    }
    val titleBlock = remember {
        loadStargameAssetOrNull(context, "menu_title_block.png")
            ?: loadStargameAssetOrNull(context, "menu_logo_1.png")
    }
    val btnStart = remember {
        loadStargameAssetOrNull(context, "menu_btn_start.png")
            ?: loadStargameAssetOrNull(context, "menu_btn_glass.png")
            ?: loadStargameAssetOrNull(context, "menu_start_btn.png")
    }
    val btnSettings = remember {
        loadStargameAssetOrNull(context, "menu_btn_settings.png")
            ?: loadStargameAssetOrNull(context, "menu_btn_glass.png")
    }
    val btnCredits = remember {
        loadStargameAssetOrNull(context, "menu_btn_credits.png")
            ?: loadStargameAssetOrNull(context, "menu_btn_glass.png")
    }
    val btnPressed = remember { loadStargameAssetOrNull(context, "menu_btn_glass_pressed.png") }

    val startCardPlayer = remember {
        runCatching { MediaPlayer.create(context, R.raw.start_volle_leistung) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { startCardPlayer?.release() } }

    // ~9 FPS trail loop
    LaunchedEffect(phase) {
        if (phase == PHASE_MENU && trailFrames.isNotEmpty()) {
            while (true) {
                delay(111) // ~9 FPS
                trailFrame = (trailFrame + 1) % trailFrames.size
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceDeep)
    ) {
        val w = maxWidth
        val h = maxHeight
        // 1) BG premium (or parallax nebula → stars fallback)
        when {
            bgPremium != null -> {
                AssetImage(
                    bitmap = bgPremium,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            bgNebula != null -> {
                AssetImage(
                    bitmap = bgNebula,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                bgStars?.let {
                    AssetImage(
                        bitmap = it,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        // 2) Hero Glocke trail — ~x42% / y18% (center of sprite)
        if (trailFrames.isNotEmpty()) {
            val heroSize = w * 0.52f
            val heroLeft = w * 0.42f - heroSize / 2f
            val heroTop = h * 0.18f - heroSize / 2f
            AssetImage(
                bitmap = trailFrames[trailFrame % trailFrames.size],
                modifier = Modifier
                    .size(heroSize)
                    .offset(x = heroLeft, y = heroTop),
                contentScale = ContentScale.Fit,
            )
        }

        when (phase) {
            PHASE_MENU -> {
                // 3) Title block — top ~6% H, centered
                if (titleBlock != null) {
                    val titleW = w * 0.82f
                    val aspect = titleBlock.height.toFloat() / titleBlock.width.toFloat().coerceAtLeast(1f)
                    val titleH = titleW * aspect
                    AssetImage(
                        bitmap = titleBlock,
                        modifier = Modifier
                            .width(titleW)
                            .height(titleH)
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = h * 0.06f),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        "STARGAME",
                        color = UiText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 48.dp),
                    )
                }

                // 4) Button stack from ~0.72H, centered
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = h * 0.72f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ArtifluxMenuButton(
                        labeled = btnStart,
                        pressed = btnPressed,
                        fallbackLabel = "Start",
                        width = w * 0.62f,
                        onClick = { phase = PHASE_START_CARD },
                    )
                    ArtifluxMenuButton(
                        labeled = btnSettings,
                        pressed = btnPressed,
                        fallbackLabel = "Einstellungen",
                        width = w * 0.62f,
                        onClick = { toast = "Einstellungen — bald verfügbar" },
                    )
                    ArtifluxMenuButton(
                        labeled = btnCredits,
                        pressed = btnPressed,
                        fallbackLabel = "Credits",
                        width = w * 0.62f,
                        onClick = { toast = "Credits — bald verfügbar" },
                    )
                }

                toast?.let { msg ->
                    Text(
                        msg,
                        color = AccentCyan,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 6.dp),
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
private fun ArtifluxMenuButton(
    labeled: ImageBitmap?,
    pressed: ImageBitmap?,
    fallbackLabel: String,
    width: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val aspect = if (labeled != null) {
        labeled.height.toFloat() / labeled.width.toFloat().coerceAtLeast(1f)
    } else {
        64f / 280f
    }
    val height = width * aspect

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            labeled != null && isPressed && pressed != null -> {
                AssetImage(
                    bitmap = pressed,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                AssetImage(
                    bitmap = labeled,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    alpha = 0.85f,
                )
            }
            labeled != null -> {
                AssetImage(
                    bitmap = labeled,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAD0C101C), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        fallbackLabel,
                        color = UiText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetImage(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}
