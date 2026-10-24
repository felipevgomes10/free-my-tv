package com.freemytv.home.explorar

/** A video TMDB lists for a Title. */
data class TitleVideo(
    /** The video's id on its site. */
    val key: String,
    val site: String,
    /** TMDB's type: "Trailer", "Teaser", "Featurette"... */
    val type: String,
    /** ISO 639-1, e.g. "pt" or "en". */
    val language: String?,
    val official: Boolean,
)

/**
 * The Trailer to play: a YouTube trailer in Portuguese (official first), then in English, then a
 * teaser in either; null when there is none, which hides "Assistir trailer".
 */
fun chooseTrailer(videos: List<TitleVideo>): TitleVideo? {
    val youtube = videos.filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
    fun pick(type: String, language: String) = youtube
        .filter { it.type.equals(type, ignoreCase = true) && it.language.equals(language, ignoreCase = true) }
        .sortedByDescending { it.official }
        .firstOrNull()
    return pick("Trailer", "pt") ?: pick("Trailer", "en") ?: pick("Teaser", "pt") ?: pick("Teaser", "en")
}
