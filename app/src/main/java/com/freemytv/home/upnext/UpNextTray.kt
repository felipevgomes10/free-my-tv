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
    /** The app's own link to the title, from Watch Next. */
    val link: String? = null,
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
) {
    /** The apps on the Tray, package and name, the most recently watched first: what it filters by. */
    val apps: List<Pair<String, String>> get() = entries.distinctBy { it.packageName }.map { it.packageName to it.appLabel }

    /** One app's entries, or all of them for null. */
    fun entriesOf(packageName: String?): List<UpNextEntry> = if (packageName == null) entries else entries.filter { it.packageName == packageName }
}

/**
 * The Up Next Tray (ADR 0012). Apps that list what the owner is watching in Android TV's Watch
 * Next are read from there, with their own episode, progress, picture and link, even while
 * closed. Apps that don't (YouTube, Prime Video) come from what the Home sees playing: one entry
 * per title, or one per app for apps that don't name what is playing. Entries leave once
 * finished, after [EXPIRES_AFTER] unwatched, with their app, or when the owner removes them.
 */
class UpNextTray(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(UpNextState())
    val state: StateFlow<UpNextState> = _state.asStateFlow()

    /** What the Home saw play, for apps that don't list in Watch Next; saved across restarts. */
    private var history: List<UpNextEntry> = emptyList()

    /** What apps list in Watch Next, as last read. */
    private var listed: List<UpNextEntry> = emptyList()

    /** The apps that list in Watch Next at all: for them, Watch Next is the whole story. */
    private var publishers: Set<String> = emptySet()

    /** Watch Next entries the owner removed, with when they were last watched then. */
    private var dismissed: Map<String, Instant> = emptyMap()

    suspend fun load() {
        history = platform.loadUpNext().sortedByDescending { it.lastWatched }
        dismissed = platform.loadDismissedUpNext()
        refresh()
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

    /** Reads Watch Next again and drops what went unwatched for too long; run whenever the Home shows. */
    suspend fun refresh() {
        val programs = platform.watchNext().orEmpty()
        publishers = programs.map { it.packageName }.toSet()
        listed = programs
            .filter { it.kind == WatchNextKind.Continue || it.kind == WatchNextKind.Next }
            .map { entryOf(it) }
        // A removal lasts until the app lists the title as watched since, or stops listing it.
        val stillDismissed = dismissed.filter { (key, at) -> listed.any { it.key == key && !it.lastWatched.isAfter(at) } }
        if (stillDismissed != dismissed) {
            dismissed = stillDismissed
            platform.saveDismissedUpNext(dismissed)
        }
        change { entries -> entries.filterNot { it.packageName in publishers } }
        publish()
    }

    /** "Remover da lista". An app's Watch Next can't be changed from here, so the Home remembers it. */
    suspend fun remove(entry: UpNextEntry) {
        if (listed.any { it.key == entry.key }) {
            dismissed = dismissed + (entry.key to entry.lastWatched)
            platform.saveDismissedUpNext(dismissed)
            publish()
        } else {
            change { entries -> entries.filterNot { it.key == entry.key } }
        }
    }

    /**
     * Back to the entry where the owner stopped, as close as the app allows: a Watch Next entry
     * through the app's own link; a YouTube video with an id reopens at its position; an app whose
     * session is still on the title resumes it; a YouTube video otherwise is searched for (YouTube's
     * on-TV app names no id, and the video comes up first, at the owner's position); anything else
     * opens its app.
     */
    fun select(entry: UpNextEntry) {
        if (entry.link != null && platform.openLink(entry.packageName, entry.link)) return
        val videoId = entry.mediaId?.takeIf { entry.packageName == YOUTUBE && YOUTUBE_ID.matches(it) }
        when {
            videoId != null -> platform.openYouTubeVideo(videoId, startSeconds = ((entry.positionMillis ?: 0) / 1000).toInt())
            platform.resumeSession(entry.packageName, entry.title) -> Unit
            entry.packageName == YOUTUBE && entry.title != null -> platform.openYouTubeSearch(listOfNotNull(entry.title, entry.subtitle).joinToString(" "))
            else -> platform.launch(entry.packageName)
        }
    }

    private suspend fun entryOf(program: WatchNextProgram) = UpNextEntry(
        key = listOf("watchnext", program.packageName, program.title, program.season, program.episode).joinToString("|"),
        packageName = program.packageName,
        appLabel = platform.appLabel(program.packageName),
        title = program.title,
        subtitle = episodeLine(program),
        positionMillis = program.positionMillis?.takeIf { it > 0 },
        durationMillis = program.durationMillis?.takeIf { it > 0 },
        artwork = program.artwork,
        lastWatched = program.lastEngagement,
        link = program.link,
    )

    /** "T1:E42 Capítulo 42", as Google TV writes an episode; nothing for a movie. */
    private fun episodeLine(program: WatchNextProgram): String? {
        val number = when {
            program.season != null && program.episode != null -> "T${program.season}:E${program.episode}"
            program.episode != null -> "E${program.episode}"
            else -> null
        }
        val name = program.episodeTitle?.trim()?.takeUnless { it.isEmpty() || it.sameName(program.title) }
        return listOfNotNull(number, name).joinToString(" ").ifEmpty { null }
    }

    /** The Tray: Watch Next and what the Home saw play, together, within the Tray's limits. */
    private fun publish() {
        val oldest = platform.now().toInstant().minus(EXPIRES_AFTER)
        val shown = (listed.filter { entry -> dismissed[entry.key]?.let { entry.lastWatched.isAfter(it) } ?: true } + history)
            .filter { it.lastWatched.isAfter(oldest) }
            .withinLimits()
        _state.value = UpNextState(shown)
    }

    private suspend fun record(media: PlayingMedia) {
        // Watch Next already tells this app's story, better than its session can.
        if (media.packageName in publishers) return
        val label = platform.appLabel(media.packageName)
        val title = media.title?.trim()?.takeUnless { it.isEmpty() || it.sameName(label) || it.sameName(media.packageName) }
        val key = if (title == null) media.packageName else "${media.packageName}|$title"
        val existing = history.firstOrNull { it.key == key }
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

    /**
     * Applies [edit] to what the Home saw play, then the Tray's limits, saves it and shows the
     * Tray again; artwork no entry uses any more is deleted.
     */
    private suspend fun change(edit: (List<UpNextEntry>) -> List<UpNextEntry>) {
        val before = history
        val oldest = platform.now().toInstant().minus(EXPIRES_AFTER)
        val after = edit(before).filter { it.lastWatched.isAfter(oldest) }.withinLimits()
        if (after != before) {
            history = after
            platform.saveUpNext(after)
            val kept = after.mapNotNull { it.artwork }.toSet()
            before.mapNotNull { it.artwork }.filter { it !in kept }.distinct().forEach { platform.deleteArtwork(it) }
        }
        publish()
    }

    /** Most recent first; one app doesn't crowd out the others: a binge keeps its latest few. */
    private fun List<UpNextEntry>.withinLimits() = sortedByDescending { it.lastWatched }
        .groupBy { it.packageName }.values.flatMap { it.take(MAX_PER_APP) }
        .sortedByDescending { it.lastWatched }
        .take(MAX_ENTRIES)

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
