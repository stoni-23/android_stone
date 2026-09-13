package com.stoni.androidstone.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Lädt freigestellte PNGs aus assets/stargame/
 * und stellt sicher, dass der Alpha-Kanal (Transparenz) aktiv bleibt.
 */
fun loadStargameAsset(context: Context, name: String): ImageBitmap {
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
        inPremultiplied = true // Garantiert korrekte Transparenz-Berechnung
    }

    val stream = context.assets.open("stargame/$name")
    val bmp = stream.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: error("Asset stargame/$name konnte nicht dekodiert werden")

    // Sicherstellen, dass es ein bearbeitbares ARGB_8888 Bitmap mit Transparenz ist
    val transparentBitmap = if (bmp.config != Bitmap.Config.ARGB_8888) {
        bmp.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bmp) bmp.recycle() }
    } else {
        bmp
    }

    // Transparenz für den Canvas-Renderer explizit aktivieren
    transparentBitmap.setHasAlpha(true)

    return transparentBitmap.asImageBitmap()
}
