package com.freemytv.home.explorar

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerTest {

    private fun video(key: String, type: String, language: String, official: Boolean = true, site: String = "YouTube") =
        TitleVideo(key, site, type, language, official)

    @Test
    fun `the official Portuguese trailer comes first`() {
        val chosen = chooseTrailer(
            listOf(
                video("en-trailer", "Trailer", "en"),
                video("pt-fan", "Trailer", "pt", official = false),
                video("pt-official", "Trailer", "pt"),
            ),
        )

        assertEquals("pt-official", chosen?.key)
    }

    @Test
    fun `then a Portuguese trailer, then English, then a teaser`() {
        assertEquals("pt-fan", chooseTrailer(listOf(video("en", "Trailer", "en"), video("pt-fan", "Trailer", "pt", official = false)))?.key)
        assertEquals("en", chooseTrailer(listOf(video("teaser", "Teaser", "pt"), video("en", "Trailer", "en")))?.key)
        assertEquals("teaser", chooseTrailer(listOf(video("feat", "Featurette", "en"), video("teaser", "Teaser", "en")))?.key)
    }

    @Test
    fun `no YouTube trailer or teaser means no trailer`() {
        assertNull(chooseTrailer(listOf(video("feat", "Featurette", "en"), video("vimeo", "Trailer", "pt", site = "Vimeo"))))
        assertNull(chooseTrailer(emptyList()))
    }

    @Test
    fun `a trailer the embedded player can't play goes to the YouTube app`() = runTest {
        val platform = FakeTvPlatform()

        Explorar(platform).trailerFailed(video("abc123", "Trailer", "pt"))

        assertEquals(listOf("abc123"), platform.youTubeVideos)
    }
}
