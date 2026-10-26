package com.freemytv.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A Top Shelf picture made ready for the screen: the [card], exactly as many pixels as the card
 * covers, so it is drawn one to one and never stretched while drawing; and the [glow], the same
 * picture as a small, blurred, darkened field of its colours that lights the top of the Home.
 */
internal class ShelfPicture(val card: Bitmap, val glow: Bitmap)

/**
 * The picture at [path] made ready for a card of [cardWidth] × [cardHeight] pixels, or null if
 * it can't be read. The apps' pictures are often small or not 16:9 (YouTube's are 4:3 with
 * black bars, some apps give portrait posters), so: bars are cut; a picture close to 16:9 fills
 * the card, any other sits whole on a blur of itself; an enlarged picture is sharpened. Done
 * once per picture on a background thread, and the last few are kept.
 */
internal suspend fun shelfPicture(path: String, cardWidth: Int, cardHeight: Int): ShelfPicture? {
    val key = "$path|$cardWidth|$cardHeight"
    prepared.get(key)?.let { return it }
    return withContext(Dispatchers.Default) {
        runCatching { prepare(path, cardWidth, cardHeight) }.getOrNull()
    }?.also { prepared.put(key, it) }
}

private fun prepare(path: String, cardWidth: Int, cardHeight: Int): ShelfPicture? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= cardWidth && bounds.outHeight / (sample * 2) >= cardHeight) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val pixels = IntArray(decoded.width * decoded.height).also { decoded.getPixels(it, 0, decoded.width, 0, 0, decoded.width, decoded.height) }
    val kept = withoutBars(pixels, decoded.width, decoded.height)
    val picture = Bitmap.createBitmap(decoded, kept.left, kept.top, kept.width, kept.height)
    if (picture !== decoded) decoded.recycle()
    val glow = glow(picture)
    val card = card(picture, cardWidth, cardHeight)
    picture.recycle()
    return ShelfPicture(card, glow)
}

private fun card(picture: Bitmap, width: Int, height: Int): Bitmap {
    val card = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(card)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val cardAspect = width.toFloat() / height
    val aspect = picture.width.toFloat() / picture.height
    val scale: Float
    if (abs(aspect / cardAspect - 1) <= FILL_TOLERANCE) {
        // Close enough to the card's shape: fill it, cropping the little that sticks out.
        scale = max(width.toFloat() / picture.width, height.toFloat() / picture.height)
        val cropWidth = width / scale
        val cropHeight = height / scale
        val left = (picture.width - cropWidth) / 2
        val top = (picture.height - cropHeight) / 2
        canvas.drawBitmap(picture, Rect(left.toInt(), top.toInt(), (left + cropWidth).toInt(), (top + cropHeight).toInt()), Rect(0, 0, width, height), paint)
    } else {
        // A poster or a square: whole, on a blur of its own colours, rather than cut to a sliver.
        canvas.drawBitmap(blurred(picture, BACKING_WIDTH, (BACKING_WIDTH / cardAspect).toInt()), null, Rect(0, 0, width, height), paint)
        scale = min(width.toFloat() / picture.width, height.toFloat() / picture.height)
        val shownWidth = picture.width * scale
        val shownHeight = picture.height * scale
        canvas.drawBitmap(picture, null, RectF((width - shownWidth) / 2, (height - shownHeight) / 2, (width + shownWidth) / 2, (height + shownHeight) / 2), paint)
    }
    if (scale <= SHARPEN_FROM) return card
    val pixels = IntArray(width * height).also { card.getPixels(it, 0, width, 0, 0, width, height) }
    // The more it was enlarged, the more it needs, up to a point where sharpening shows itself.
    val amount = min(MAX_SHARPEN, (scale - 1) * SHARPEN_PER_SCALE)
    card.setPixels(sharpen(pixels, width, height, amount), 0, width, 0, 0, width, height)
    return card
}

/** The picture shrunk to [width] × [height], cropped to that shape, and blurred. */
private fun blurred(picture: Bitmap, width: Int, height: Int): Bitmap {
    val scale = max(width.toFloat() / picture.width, height.toFloat() / picture.height)
    val cropWidth = width / scale
    val cropHeight = height / scale
    val left = ((picture.width - cropWidth) / 2).toInt()
    val top = ((picture.height - cropHeight) / 2).toInt()
    // Halved step by step, so every pixel of the picture counts in the colours, not one in a hundred.
    var small = Bitmap.createBitmap(picture, left, top, cropWidth.toInt().coerceAtLeast(1), cropHeight.toInt().coerceAtLeast(1))
    while (small.width / 2 >= width * 2) {
        val half = Bitmap.createScaledBitmap(small, small.width / 2, small.height / 2, true)
        if (small !== picture) small.recycle()
        small = half
    }
    val sized = Bitmap.createScaledBitmap(small, width, height, true)
    if (small !== picture && small !== sized) small.recycle()
    val pixels = IntArray(width * height).also { sized.getPixels(it, 0, width, 0, 0, width, height) }
    sized.setPixels(blur(pixels, width, height, max(1, width / BLUR_DIVISOR)), 0, width, 0, 0, width, height)
    return sized
}

/**
 * The glow: the picture's colours across the top of the Home, dimmed where the Status Bar and
 * the words sit and fading to black before the App Tray. Small on purpose: drawn stretched, its
 * blur has no detail to lose, and it costs a frame as little as any picture.
 */
private fun glow(picture: Bitmap): Bitmap {
    val glow = blurred(picture, GLOW_PIXELS_WIDE, GLOW_PIXELS_HIGH)
    val canvas = Canvas(glow)
    val w = GLOW_PIXELS_WIDE.toFloat()
    val h = GLOW_PIXELS_HIGH.toFloat()
    canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w * 0.6f, 0f, 0x80000000.toInt(), 0, Shader.TileMode.CLAMP) })
    canvas.drawRect(
        0f, 0f, w, h,
        Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x66000000, 0x14000000, 0x99000000.toInt(), 0xFF000000.toInt()),
                floatArrayOf(0f, 0.3f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        },
    )
    return glow
}

private val prepared = LruCache<String, ShelfPicture>(KEPT)

internal fun clearShelfPictures() = prepared.evictAll()

// Each kept picture is about 0.6 MB, mostly its card (ADR 0004).
private const val KEPT = 3
private const val FILL_TOLERANCE = 0.2f
private const val SHARPEN_FROM = 1.05f
private const val SHARPEN_PER_SCALE = 1.2f
private const val MAX_SHARPEN = 0.7f
private const val BACKING_WIDTH = 96
private const val GLOW_PIXELS_WIDE = 120
private const val GLOW_PIXELS_HIGH = 40
private const val BLUR_DIVISOR = 24
