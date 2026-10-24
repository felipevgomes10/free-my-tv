package com.freemytv.home.upnext

/**
 * A program a streaming app lists in Android TV's Watch Next: what the owner is in the middle
 * of, as the app itself keeps it. Only what the probe needs so far: whose it is, and whether it
 * carries a link to open it, how far the owner got, and a picture.
 */
data class WatchNextRow(
    val packageName: String,
    val hasLink: Boolean,
    val hasProgress: Boolean,
    val hasPicture: Boolean,
)
