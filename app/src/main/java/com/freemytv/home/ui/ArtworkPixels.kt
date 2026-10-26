package com.freemytv.home.ui

/*
 * Work on a picture's pixels (ARGB, row by row), done once when a picture is prepared for the
 * screen, never while drawing. Plain arrays, so it runs anywhere and is tested off the TV.
 */

/** The part of a picture to keep, in pixels: [left] and [top] in, [right] and [bottom] out. */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * The picture without the black bars around it: YouTube's 4:3 thumbnails carry a 16:9 frame
 * between bars, and some apps pad posters the same way. A bar is a row or column that is black
 * all along; bars are cut only while at least half of the picture remains.
 */
fun withoutBars(pixels: IntArray, width: Int, height: Int): PixelBounds {
    fun dark(x: Int, y: Int): Boolean {
        val p = pixels[y * width + x]
        return maxOf(p shr 16 and 0xFF, p shr 8 and 0xFF, p and 0xFF) <= BAR_LEVEL
    }
    fun darkRow(y: Int) = (0 until width step SAMPLE_STEP).all { dark(it, y) }
    fun darkColumn(x: Int, top: Int, bottom: Int) = (top until bottom step SAMPLE_STEP).all { dark(x, it) }
    var top = 0
    var bottom = height
    while (top < bottom && darkRow(top)) top++
    while (bottom > top && darkRow(bottom - 1)) bottom--
    if (bottom - top < height / 2) return PixelBounds(0, 0, width, height)
    var left = 0
    var right = width
    while (left < right && darkColumn(left, top, bottom)) left++
    while (right > left && darkColumn(right - 1, top, bottom)) right--
    if (right - left < width / 2) return PixelBounds(0, top, width, bottom)
    return PixelBounds(left, top, right, bottom)
}

/** A soft blur, three box blurs of [radius] pixels, each across then down; close to a Gaussian. */
fun blur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    var out = pixels
    repeat(3) {
        out = boxBlur(out, width, height, radius, across = true)
        out = boxBlur(out, width, height, radius, across = false)
    }
    return out
}

/**
 * Crisper edges for a picture that was enlarged: each pixel pushed away from its surroundings'
 * average by [amount] (an unsharp mask), which brings back some of what enlarging softened.
 */
fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
    val soft = boxBlur(boxBlur(pixels, width, height, 1, across = true), width, height, 1, across = false)
    return IntArray(pixels.size) { i ->
        val p = pixels[i]
        val s = soft[i]
        fun channel(shift: Int): Int {
            val v = p shr shift and 0xFF
            return (v + amount * (v - (s shr shift and 0xFF))).toInt().coerceIn(0, 255) shl shift
        }
        (p and ALPHA) or channel(16) or channel(8) or channel(0)
    }
}

/** A running-average blur along rows or columns; edges repeat the border pixel. */
private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, across: Boolean): IntArray {
    val out = IntArray(pixels.size)
    val lines = if (across) height else width
    val length = if (across) width else height
    val span = radius * 2 + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return pixels[if (across) line * width + c else c * width + line]
        }
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = at(i)
            r += p shr 16 and 0xFF
            g += p shr 8 and 0xFF
            b += p and 0xFF
        }
        for (i in 0 until length) {
            out[if (across) line * width + i else i * width + line] = ALPHA or (r / span shl 16) or (g / span shl 8) or (b / span)
            val gone = at(i - radius)
            val come = at(i + radius + 1)
            r += (come shr 16 and 0xFF) - (gone shr 16 and 0xFF)
            g += (come shr 8 and 0xFF) - (gone shr 8 and 0xFF)
            b += (come and 0xFF) - (gone and 0xFF)
        }
    }
    return out
}

private const val ALPHA = 0xFF shl 24
// JPEG black is rarely exactly 0.
private const val BAR_LEVEL = 24
private const val SAMPLE_STEP = 4
