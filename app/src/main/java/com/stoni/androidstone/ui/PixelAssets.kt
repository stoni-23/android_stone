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
 * Looki Luke Priority-1 drawable ids. Returns null when a name is missing
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

    @DrawableRes val grass: Int = R.drawable.tile_grass
    @DrawableRes val iconHolz: Int = R.drawable.icon_holz
    @DrawableRes val iconGetreide: Int = R.drawable.icon_getreide
    @DrawableRes val iconEisen: Int = R.drawable.icon_eisen
    @DrawableRes val iconRuhm: Int = R.drawable.icon_ruhm
    @DrawableRes val wachturm: Int = R.drawable.wachturm
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
