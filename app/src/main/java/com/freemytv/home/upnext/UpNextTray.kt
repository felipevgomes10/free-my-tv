package com.freemytv.home.upnext

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
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
 * title, or one per app for apps that don't name what is playing.
 */
class UpNextTray(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(UpNextState())
    val state: StateFlow<UpNextState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = UpNextState(platform.loadUpNext().sortedByDescending { it.lastWatched })
    }

    /** Follows what plays on the TV for as long as it is collected, whichever app is in front. */
    suspend fun follow() {
        platform.nowPlaying.collect { sessions -> sessions.forEach { record(it) } }
    }

    /** Back to the entry where the owner stopped when the app allows it, or else into its app. */
    fun select(entry: UpNextEntry) = platform.resume(entry.packageName)

    private suspend fun record(media: PlayingMedia) {
        val label = platform.appLabel(media.packageName)
        val title = media.title?.trim()?.takeUnless { it.isEmpty() || it.sameName(label) || it.sameName(media.packageName) }
        val key = if (title == null) media.packageName else "${media.packageName}|$title"
        val entries = _state.value.entries
        val existing = entries.firstOrNull { it.key == key }
        // Something seen only paused, never playing, isn't something the owner started.
        if (existing == null && !media.playing) return
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
        )
        if (entry == existing) return
        val others = entries.filterNot { other ->
            other.key == key ||
                // Apps often start playing before they name it: that nameless moment was this title.
                (title != null && other.key == media.packageName && Duration.between(other.lastWatched, now) < SAME_PLAYBACK)
        }
        val updated = (others + entry).sortedByDescending { it.lastWatched }
        _state.value = UpNextState(updated)
        platform.saveUpNext(updated)
    }

    private fun String.sameName(other: String) = normalized() == other.normalized()

    private fun String.normalized() = lowercase(PT_BR).filter { it.isLetterOrDigit() }

    private companion object {
        val SAME_PLAYBACK: Duration = Duration.ofMinutes(2)
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
