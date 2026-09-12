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
 * Looki Luke game-size ids: buildings/tiles *_256, icons/units/FX *_128.
 */
object PixelAssets {
    @DrawableRes
    fun building(type: BuildingType, level: Int): Int {
        val stage2 = level >= 2
        return when (type) {
            BuildingType.THINGHALLE ->
                if (stage2) R.drawable.thinghalle_2_256 else R.drawable.thinghalle_1_256
            BuildingType.LANGHAUS ->
                if (stage2) R.drawable.langhaus_2_256 else R.drawable.langhaus_1_256
            BuildingType.HOLZFAELLER ->
                if (stage2) R.drawable.holzfaeller_2_256 else R.drawable.holzfaeller_1_256
            BuildingType.ACKER ->
                if (stage2) R.drawable.acker_2_256 else R.drawable.acker_1_256
            BuildingType.SCHMIEDE ->
                if (stage2) R.drawable.schmiede_2_256 else R.drawable.schmiede_1_256
        }
    }

    fun isProductive(type: BuildingType): Boolean = when (type) {
        BuildingType.HOLZFAELLER, BuildingType.ACKER, BuildingType.SCHMIEDE -> true
        else -> false
    }

    @DrawableRes val grass: Int = R.drawable.tile_grass_256
    @DrawableRes val path: Int = R.drawable.tile_path_256
    @DrawableRes val buildSlot: Int = R.drawable.tile_build_slot_256

    @DrawableRes val iconHolz: Int = R.drawable.icon_holz_128
    @DrawableRes val iconGetreide: Int = R.drawable.icon_getreide_128
    @DrawableRes val iconEisen: Int = R.drawable.icon_eisen_128
    @DrawableRes val iconRuhm: Int = R.drawable.icon_ruhm_128
    @DrawableRes val wachturm: Int = R.drawable.wachturm_256

    @DrawableRes val fxSmoke1: Int = R.drawable.fx_smoke_1_128
    @DrawableRes val fxSmoke2: Int = R.drawable.fx_smoke_2_128
    @DrawableRes val fxFire1: Int = R.drawable.fx_fire_1_128
    @DrawableRes val fxFire2: Int = R.drawable.fx_fire_2_128
    @DrawableRes val fxHit: Int = R.drawable.fx_hit_128

    @DrawableRes val workerA: Int = R.drawable.worker_a_128
    @DrawableRes val workerB: Int = R.drawable.worker_b_128
    @DrawableRes val warriorA: Int = R.drawable.warrior_a_128
    @DrawableRes val warriorB: Int = R.drawable.warrior_b_128
}

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
