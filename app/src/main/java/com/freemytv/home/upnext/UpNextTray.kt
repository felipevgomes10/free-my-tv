package com.freemytv.home.upnext

import com.freemytv.home.platform.PackageEvent
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch
import java.util.Locale

/** A media session as the TV reports it, with only what the app chose to say. */
data class PlayingMedia(
    val packageName: String,
    val playing: Boolean,
    /** Null when the app doesn't name what is playing (Netflix, Globoplay). */
    val title: String? = null,
    val subtitle: String? = null,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    /** The artwork the platform stored for this title, if the app gave any. */
    val artwork: String? = null,
    /** The app is a music or audio app, by its own declaration. */
    val audioApp: Boolean = false,
    /** The app's own id for what is playing, when it gives one (YouTube: the video's id). */
    val mediaId: String? = null,
)

/** One movie or episode on the Up Next Tray, or one app when the app doesn't say what played. */
data class UpNextEntry(
    val key: String,
    val packageName: String,
    val appLabel: String,
    /** Null for an app's "Continuar no <app>" entry. */
    val title: String?,
    val subtitle: String?,
    val positionMillis: Long?,
    val durationMillis: Long?,
    val artwork: String?,
    val lastWatched: Instant,
    /** The app's own id for the title, which lets YouTube reopen the very video. */
    val mediaId: String? = null,
) {
    /** What is left to watch, when the app tells both its position and its duration. */
    val remainingMillis: Long?
        get() = if (positionMillis != null && durationMillis != null && durationMillis > 0) (durationMillis - positionMillis).coerceAtLeast(0) else null

    /** How far along, from 0 to 1, when known. */
    val progress: Float?
        get() = if (positionMillis != null && durationMillis != null && durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else null
}

data class UpNextState(
    /** Most recently watched first; the Tray is hidden while this is empty. */
    val entries: List<UpNextEntry> = emptyList(),
)

/**
 * The Up Next Tray. Android keeps the apps' own "continue watching" rows from a Home like
 * this one (ADR 0007), so the Tray is built from what the Home sees playing: one entry per
 * title, or one per app for apps that don't name what is playing. Entries leave once
 * finished, after [EXPIRES_AFTER] unwatched, with their app, or when the owner removes them.
 */
class UpNextTray(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(UpNextState())
    val state: StateFlow<UpNextState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = UpNextState(platform.loadUpNext().sortedByDescending { it.lastWatched })
        prune()
    }

    /** Follows what plays on the TV for as long as it is collected, whichever app is in front. */
    suspend fun follow() = coroutineScope {
        launch {
            platform.packageEvents.collect { event ->
                if (event is PackageEvent.Removed) change { entries -> entries.filterNot { it.packageName == event.packageName } }
            }
        }
        platform.nowPlaying.collect { sessions -> sessions.forEach { record(it) } }
    }

    /** Drops what went unwatched for too long; run whenever the Home shows. */
    suspend fun prune() = change { it }

    /** "Remover da lista". */
    suspend fun remove(entry: UpNextEntry) = change { entries -> entries.filterNot { it.key == entry.key } }

    /**
     * Back to the entry where the owner stopped, as close as the app allows: a YouTube video with
     * an id reopens at its position; an app whose session is still on the title resumes it; a
     * YouTube video otherwise is searched for (YouTube's on-TV app names no id, and the video comes
     * up first, at the owner's position); anything else opens its app.
     */
    fun select(entry: UpNextEntry) {
        val videoId = entry.mediaId?.takeIf { entry.packageName == YOUTUBE && YOUTUBE_ID.matches(it) }
        when {
            videoId != null -> platform.openYouTubeVideo(videoId, startSeconds = ((entry.positionMillis ?: 0) / 1000).toInt())
            platform.resumeSession(entry.packageName, entry.title) -> Unit
            entry.packageName == YOUTUBE && entry.title != null -> platform.openYouTubeSearch(listOfNotNull(entry.title, entry.subtitle).joinToString(" "))
            else -> platform.launch(entry.packageName)
        }
    }

    private suspend fun record(media: PlayingMedia) {
        val label = platform.appLabel(media.packageName)
        val title = media.title?.trim()?.takeUnless { it.isEmpty() || it.sameName(label) || it.sameName(media.packageName) }
        val key = if (title == null) media.packageName else "${media.packageName}|$title"
        val entries = _state.value.entries
        val existing = entries.firstOrNull { it.key == key }
        if (media.finished()) {
            if (existing != null) change { all -> all.filterNot { it.key == key } }
            return
        }
        // Something seen only paused, never playing, isn't something the owner started; nor is music or a short clip.
        if (existing == null && (!media.playing || media.audioApp || media.isShort())) return
        val now = platform.now().toInstant()
        val entry = UpNextEntry(
            key = key,
            packageName = media.packageName,
            appLabel = label,
            title = title,
            subtitle = media.subtitle?.trim()?.takeUnless { it.isEmpty() || title == null } ?: existing?.subtitle,
            positionMillis = media.positionMillis?.takeIf { it >= 0 } ?: existing?.positionMillis,
            durationMillis = media.durationMillis?.takeIf { it > 0 } ?: existing?.durationMillis,
            artwork = media.artwork ?: existing?.artwork,
            lastWatched = if (media.playing) now else existing?.lastWatched ?: now,
            mediaId = media.mediaId?.takeIf { it.isNotBlank() } ?: existing?.mediaId,
        )
        if (entry == existing) return
        change { all ->
            all.filterNot { other ->
                other.key == key ||
                    // Apps often start playing before they name it: that nameless moment was this title.
                    (title != null && other.key == media.packageName && Duration.between(other.lastWatched, now) < SAME_PLAYBACK)
            } + entry
        }
    }

    /** At 95%, or with under two minutes left, what remains is credits. */
    private fun PlayingMedia.finished(): Boolean {
        val position = positionMillis ?: return false
        val duration = durationMillis?.takeIf { it > 0 } ?: return false
        return position >= duration * FINISHED_AT || duration - position < CREDITS.toMillis()
    }

    private fun PlayingMedia.isShort() = durationMillis != null && durationMillis in 1 until SHORT.toMillis()

    /** Applies [edit], then the Tray's limits, and saves; artwork no entry uses any more is deleted. */
    private suspend fun change(edit: (List<UpNextEntry>) -> List<UpNextEntry>) {
        val before = _state.value.entries
        val oldest = platform.now().toInstant().minus(EXPIRES_AFTER)
        val after = edit(before)
            .filter { it.lastWatched.isAfter(oldest) }
            .sortedByDescending { it.lastWatched }
            // One app doesn't crowd out the others: a binge keeps its latest few.
            .groupBy { it.packageName }.values.flatMap { it.take(MAX_PER_APP) }
            .sortedByDescending { it.lastWatched }
            .take(MAX_ENTRIES)
        if (after == before) return
        _state.value = UpNextState(after)
        platform.saveUpNext(after)
        val kept = after.mapNotNull { it.artwork }.toSet()
        before.mapNotNull { it.artwork }.filter { it !in kept }.distinct().forEach { platform.deleteArtwork(it) }
    }

    private fun String.sameName(other: String) = normalized() == other.normalized()

    private fun String.normalized() = lowercase(PT_BR).filter { it.isLetterOrDigit() }

    private companion object {
        val SAME_PLAYBACK: Duration = Duration.ofMinutes(2)
        val CREDITS: Duration = Duration.ofMinutes(2)
        val SHORT: Duration = Duration.ofMinutes(5)
        val EXPIRES_AFTER: Duration = Duration.ofDays(30)
        const val FINISHED_AT = 0.95
        const val MAX_ENTRIES = 20
        const val MAX_PER_APP = 5
        const val YOUTUBE = "com.google.android.youtube.tv"
        val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{11}""")
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
