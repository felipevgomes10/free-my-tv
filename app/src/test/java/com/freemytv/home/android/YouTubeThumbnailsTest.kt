package com.freemytv.home.android

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeThumbnailsTest {

    @Test
    fun `YouTube's small thumbnails are asked for at 1280 by 720 first, then as given`() {
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
                "https://i.ytimg.com/vi/abc123/hq720.jpg",
                "https://i.ytimg.com/vi/abc123/hqdefault.jpg?sqp=xyz",
            ),
            largestFirst("https://i.ytimg.com/vi/abc123/hqdefault.jpg?sqp=xyz"),
        )
    }

    @Test
    fun `other pictures, and YouTube's large ones, are asked for as they are`() {
        assertEquals(listOf("https://example.com/poster/default.jpg"), largestFirst("https://example.com/poster/default.jpg"))
        assertEquals(listOf("https://i.ytimg.com/vi/abc123/maxresdefault.jpg"), largestFirst("https://i.ytimg.com/vi/abc123/maxresdefault.jpg"))
    }
}
