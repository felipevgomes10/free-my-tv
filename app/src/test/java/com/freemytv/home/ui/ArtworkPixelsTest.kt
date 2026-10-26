package com.freemytv.home.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPixelsTest {

    private val black = 0xFF080808.toInt()
    private val sky = 0xFF3060A0.toInt()

    /** A [width] × [height] picture of [color], with [bars] black rows at the top and bottom. */
    private fun picture(width: Int, height: Int, bars: Int = 0, color: Int = sky) =
        IntArray(width * height) { i -> if (i / width < bars || i / width >= height - bars) black else color }

    @Test
    fun `a YouTube thumbnail loses its black bars and keeps its 16 by 9 frame`() {
        // 480 × 360 with 45 rows of black above and below: a 480 × 270 frame inside.
        val kept = withoutBars(picture(480, 360, bars = 45), 480, 360)

        assertEquals(PixelBounds(0, 45, 480, 315), kept)
    }

    @Test
    fun `a picture without bars, or one that is mostly dark, is kept whole`() {
        assertEquals(PixelBounds(0, 0, 64, 36), withoutBars(picture(64, 36), 64, 36))
        assertEquals(PixelBounds(0, 0, 64, 36), withoutBars(picture(64, 36, color = black), 64, 36))
        // Bars taking more than half of it are more likely a dark scene than padding.
        assertEquals(PixelBounds(0, 0, 64, 36), withoutBars(picture(64, 36, bars = 10), 64, 36))
    }

    @Test
    fun `a dark line inside the picture is not taken for a bar`() {
        val pixels = picture(64, 36)
        for (x in 0 until 64) pixels[18 * 64 + x] = black

        assertEquals(PixelBounds(0, 0, 64, 36), withoutBars(pixels, 64, 36))
    }

    @Test
    fun `blurring leaves a flat colour as it is and softens an edge`() {
        assertTrue(blur(picture(16, 16), 16, 16, 2).all { it == sky })

        val edge = IntArray(16 * 4) { i -> if (i % 16 < 8) black else 0xFFFFFFFF.toInt() }
        val soft = blur(edge, 16, 4, 2)
        val around = (soft[7] and 0xFF) to (soft[8] and 0xFF)

        assertTrue("$around", around.first > 8 && around.second < 255)
    }

    @Test
    fun `sharpening deepens an edge and leaves a flat colour alone`() {
        assertTrue(sharpen(picture(8, 8), 8, 8, 0.7f).all { it == sky })

        val grey = 0xFF808080.toInt()
        val edge = IntArray(8 * 3) { i -> if (i % 8 < 4) grey else 0xFFC0C0C0.toInt() }
        val sharp = sharpen(edge, 8, 3, 0.7f)

        assertTrue((sharp[8 + 3] and 0xFF) < 0x80)
        assertTrue((sharp[8 + 4] and 0xFF) > 0xC0)
    }
}
