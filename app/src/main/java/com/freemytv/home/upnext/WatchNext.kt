package com.freemytv.home.upnext

import java.time.Instant

/** Why an app lists a program in Watch Next. */
enum class WatchNextKind {
    /** The owner stopped partway through it. */
    Continue,

    /** The next episode of something the owner is watching. */
    Next,

    /** A new episode or season of something the owner watched. */
    New,

    /** Something the owner saved for later. */
    Watchlist,
}

/**
 * A program a streaming app lists in Android TV's Watch Next (ADR 0012): what the owner is in
 * the middle of, as the app itself keeps it, even while the app is closed.
 */
data class WatchNextProgram(
    val packageName: String,
    val kind: WatchNextKind,
    /** The movie's or the series' name. */
    val title: String,
    val season: String? = null,
    val episode: String? = null,
    val episodeTitle: String? = null,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    /** The program's picture, stored on the TV. */
    val artwork: String? = null,
    /** The app's own link to the program, an intent URI. */
    val link: String? = null,
    val lastEngagement: Instant,
)
