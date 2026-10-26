package com.freemytv.home.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.freemytv.home.apps.tileArt
import com.freemytv.home.platform.InstalledApp
import java.io.ByteArrayOutputStream

/**
 * An app's tile for the FMTV Controller, as a 320×180 PNG: its App Banner, or its icon centred on
 * its colour, as the Home draws it (AppArt).
 */
suspend fun AppArtworkLoader.picturePng(app: InstalledApp): ByteArray? {
    val bitmap = when (val art = load(app.packageName, app.tileArt()) ?: return null) {
        is AppArtwork.Banner -> art.bitmap
        is AppArtwork.IconOnColor -> Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { tile ->
            val canvas = Canvas(tile)
            canvas.drawColor(art.color)
            val size = HEIGHT / 2
            val left = (WIDTH - size) / 2f
            val top = (HEIGHT - size) / 2f
            canvas.drawBitmap(Bitmap.createScaledBitmap(art.icon, size, size, true), left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}

private const val WIDTH = 320
private const val HEIGHT = 180
