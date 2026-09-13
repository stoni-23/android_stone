package com.stoni.androidstone.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Load from assets/stargame/ — bypasses aapt drawable crunch completely. */
fun loadStargameAsset(context: Context, name: String): ImageBitmap {
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
    check(argb.hasAlpha()) { "no alpha on $name — wrong/old asset" }
    return argb.asImageBitmap()
}
