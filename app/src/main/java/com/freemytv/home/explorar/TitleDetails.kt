package com.freemytv.home.explorar

/** Someone in a Title's cast and who they play. */
data class CastMember(val name: String, val character: String?)

/** A Title's details, as TMDB has them for Brazil; anything it doesn't have is null or empty. */
data class TitleDetails(
    val id: Int,
    val kind: TitleKind,
    val name: String,
    val year: Int?,
    val genres: List<String>,
    val overview: String?,
    val rating: Double?,
    /** Brazil's rating as TMDB writes it ("L", "14"...), for movies from their release dates. */
    val certification: String?,
    /** A movie's length. */
    val runtimeMinutes: Int?,
    /** A series' typical episode length. */
    val episodeMinutes: Int?,
    val seasons: Int?,
    val cast: List<CastMember>,
    val backdrop: String?,
    /** Brazil's watch offers, best first, as TMDB lists them. */
    val offers: List<WatchOffer> = emptyList(),
    /** Videos in Portuguese or English. */
    val videos: List<TitleVideo> = emptyList(),
)

sealed interface DetailsLookup {
    data class Found(val details: TitleDetails) : DetailsLookup
    data object Offline : DetailsLookup
    data object MissingToken : DetailsLookup
}

/** Brazil's age ratings (ClassInd). */
enum class AgeRating(val label: String) { Free("L"), Ten("10"), Twelve("12"), Fourteen("14"), Sixteen("16"), Eighteen("18") }

/** Brazil's rating from what TMDB wrote, or null when it is something else or missing. */
fun ageRating(certification: String?): AgeRating? = when (certification?.trim()?.uppercase()) {
    "L", "LIVRE", "AL" -> AgeRating.Free
    "10", "A10" -> AgeRating.Ten
    "12", "A12" -> AgeRating.Twelve
    "14", "A14" -> AgeRating.Fourteen
    "16", "A16" -> AgeRating.Sixteen
    "18", "A18" -> AgeRating.Eighteen
    else -> null
}

/** "2 h 08 min", "48 min" or null. */
fun movieLength(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
}

/** "48 min/episódio" or null. */
fun episodeLength(minutes: Int?): String? = minutes?.takeIf { it > 0 }?.let { "$it min/episódio" }

/** "1 temporada", "2 temporadas" or null. */
fun seasonCount(seasons: Int?): String? = seasons?.takeIf { it > 0 }?.let { if (it == 1) "1 temporada" else "$it temporadas" }

/** The facts line after the rating badge: kind, year, main genre and length, whatever is known. */
fun TitleDetails.facts(kindLabel: String): List<String> = listOfNotNull(
    kindLabel,
    year?.toString(),
    genres.firstOrNull(),
    if (kind == TitleKind.Movie) movieLength(runtimeMinutes) else episodeLength(episodeMinutes),
    if (kind == TitleKind.Series) seasonCount(seasons) else null,
)

/** The first of the cast, as many as the details screen has room for. */
fun TitleDetails.leadCast(): List<CastMember> = cast.take(LEAD_CAST)

private const val LEAD_CAST = 4
