package com.freemytv.home.explorar

/** A movie or series TMDB found by name. */
data class SearchResult(
    val id: Int,
    val kind: TitleKind,
    val name: String,
    /** The release or first air year; null when TMDB doesn't know it. */
    val year: Int?,
    /** TMDB's image paths, or null. */
    val backdrop: String?,
    val poster: String?,
)

sealed interface SearchLookup {
    data class Found(val results: List<SearchResult>) : SearchLookup
    data object Offline : SearchLookup
    data object MissingToken : SearchLookup
}
