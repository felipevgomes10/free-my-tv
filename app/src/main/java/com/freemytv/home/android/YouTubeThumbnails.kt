package com.freemytv.home.android

/**
 * The addresses to try for a picture, best first: YouTube's apps hand over small thumbnails
 * (320 × 180, or 480 × 360 with black bars), while the same video has 1280 × 720 ones, which
 * don't exist for every video. Any other picture is asked for as it is.
 */
internal fun largestFirst(address: String): List<String> {
    if ("ytimg.com" !in address || !YOUTUBE_THUMBNAIL.containsMatchIn(address)) return listOf(address)
    // Some come signed for their own size: the larger ones are asked for without the signature.
    val plain = address.substringBefore('?')
    return listOf(plain.replace(YOUTUBE_THUMBNAIL, "/maxresdefault."), plain.replace(YOUTUBE_THUMBNAIL, "/hq720."), address).distinct()
}

private val YOUTUBE_THUMBNAIL = Regex("""/(hq|mq|sd|)default\.""")
