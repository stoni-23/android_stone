package com.stoni.androidstone.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Loads a PNG from assets. Tries `stargame/$name` first, then `$name` at the
 * assets root (LANG/RUND ships live there). Returns null instead of crashing
 * so the renderer can draw a geometry fallback.
 */
fun loadStargameAssetOrNull(context: Context, name: String): ImageBitmap? {
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
        inPremultiplied = true
    }
    val candidates = listOf("stargame/$name", name)
    for (path in candidates) {
        try {
            context.assets.open(path).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream, null, opts) ?: return@use
                val argb = if (bmp.config != Bitmap.Config.ARGB_8888) {
                    bmp.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bmp) bmp.recycle() }
                } else {
                    bmp
                }
                argb.setHasAlpha(true)
                return argb.asImageBitmap()
            }
        } catch (_: Exception) {
            // try next path
        }
    }
    return null
}

fun loadStargameAsset(context: Context, name: String): ImageBitmap {
    return loadStargameAssetOrNull(context, name)
        ?: error("Asset $name konnte nicht geladen werden (stargame/ und assets-root)")
}
