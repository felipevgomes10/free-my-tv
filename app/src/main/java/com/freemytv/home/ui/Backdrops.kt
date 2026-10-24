package com.freemytv.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A picture cropped to [width] × [height] and baked with what keeps the UI readable on top of
 * it: an overall dim, shade behind the Status Bar and the text on the left, and a fade to black
 * from [fadeFrom] of the height down. Baked once, so a frame draws a single image (ADR 0004).
 */
internal suspend fun bakeBackdrop(path: String, width: Int, height: Int, fadeFrom: Float = 0.45f): Bitmap? = withContext(Dispatchers.Default) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val source = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
        val baked = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(baked)
        // Crop to the backdrop's shape around the middle of the picture.
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val cropWidth = (width / scale).toInt()
        val cropHeight = (height / scale).toInt()
        val left = (source.width - cropWidth) / 2
        val top = (source.height - cropHeight) / 2
        canvas.drawBitmap(source, Rect(left, top, left + cropWidth, top + cropHeight), Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(0x4D000000)
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w * 0.65f, 0f, 0xD9000000.toInt(), 0, Shader.TileMode.CLAMP) })
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.35f, 0xB3000000.toInt(), 0, Shader.TileMode.CLAMP) })
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, h * fadeFrom, 0f, h, 0, 0xFF000000.toInt(), Shader.TileMode.CLAMP) })
        baked
    }.getOrNull()
}

/** A picture decoded near [width] pixels wide, for cards (ADR 0004). */
internal suspend fun decodeNear(path: String, width: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
