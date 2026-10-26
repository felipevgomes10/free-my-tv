package com.freemytv.home.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * An Up Next entry's still for the FMTV Controller, from the artwork file the Home keeps for it:
 * decoded near 480 px wide (a 1080p still would cost ~8 MB, free-my-tv ADR 0004) and sent as JPEG.
 */
suspend fun upNextPictureJpeg(path: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }.getOrNull()
}

private const val WIDTH = 480
