package com.stoni.androidstone.ui

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import com.stoni.androidstone.R
import com.stoni.androidstone.game.BuildingType

/**
 * Looki Luke drawable ids (P1 buildings/icons + P2 FX/units/path).
 * Returns null via [rememberDrawableOrNull] when a name is missing
 * so callers can keep the colored/text fallback UI.
 */
object PixelAssets {
    @DrawableRes
    fun building(type: BuildingType, level: Int): Int {
        val stage2 = level >= 2
        return when (type) {
            BuildingType.THINGHALLE ->
                if (stage2) R.drawable.thinghalle_2 else R.drawable.thinghalle_1
            BuildingType.LANGHAUS ->
                if (stage2) R.drawable.langhaus_2 else R.drawable.langhaus_1
            BuildingType.HOLZFAELLER ->
                if (stage2) R.drawable.holzfaeller_2 else R.drawable.holzfaeller_1
            BuildingType.ACKER ->
                if (stage2) R.drawable.acker_2 else R.drawable.acker_1
            BuildingType.SCHMIEDE ->
                if (stage2) R.drawable.schmiede_2 else R.drawable.schmiede_1
        }
    }

    /** Productive buildings that can show an idle worker sprite. */
    fun isProductive(type: BuildingType): Boolean = when (type) {
        BuildingType.HOLZFAELLER, BuildingType.ACKER, BuildingType.SCHMIEDE -> true
        else -> false
    }

    @DrawableRes val grass: Int = R.drawable.tile_grass
    @DrawableRes val path: Int = R.drawable.tile_path
    @DrawableRes val iconHolz: Int = R.drawable.icon_holz
    @DrawableRes val iconGetreide: Int = R.drawable.icon_getreide
    @DrawableRes val iconEisen: Int = R.drawable.icon_eisen
    @DrawableRes val iconRuhm: Int = R.drawable.icon_ruhm
    @DrawableRes val wachturm: Int = R.drawable.wachturm

    @DrawableRes val fxSmoke1: Int = R.drawable.fx_smoke_1
    @DrawableRes val fxSmoke2: Int = R.drawable.fx_smoke_2
    @DrawableRes val fxFire1: Int = R.drawable.fx_fire_1
    @DrawableRes val fxFire2: Int = R.drawable.fx_fire_2
    @DrawableRes val fxHit: Int = R.drawable.fx_hit

    @DrawableRes val workerA: Int = R.drawable.worker_a
    @DrawableRes val workerB: Int = R.drawable.worker_b
    @DrawableRes val warriorA: Int = R.drawable.warrior_a
    @DrawableRes val warriorB: Int = R.drawable.warrior_b
}

/** Runtime check so a missing id does not crash the UI. */
@Composable
fun rememberDrawableOrNull(@DrawableRes id: Int): Int? {
    val resources = LocalContext.current.resources
    return remember(id) {
        if (id == 0) return@remember null
        try {
            resources.getResourceName(id)
            id
        } catch (_: Resources.NotFoundException) {
            null
        }
    }
}

/**
 * Nearest-neighbor painter for pixel art (FilterQuality.None).
 * Same role as painterResource, but keeps 128px sprites crisp in the grid.
 */
@Composable
fun pixelPainterResource(@DrawableRes id: Int): Painter {
    val image = ImageBitmap.imageResource(id)
    return remember(id, image) {
        BitmapPainter(image = image, filterQuality = FilterQuality.None)
    }
}

@Composable
fun PixelImage(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Image(
        painter = pixelPainterResource(id),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
