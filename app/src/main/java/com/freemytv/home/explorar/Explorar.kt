package com.freemytv.home.explorar

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** Trending over the day or over the week. */
enum class TrendWindow { Today, Week }

enum class TitleKind { Movie, Series }

/** Which Titles Explorar shows. */
enum class KindFilter { All, Movies, Series }

/** A Title as the highlights show it. */
data class TitleSummary(
    val id: Int,
    val kind: TitleKind,
    val name: String,
    /** Null when TMDB has no Portuguese synopsis. */
    val overview: String?,
    val genres: List<String>,
    /** Out of 10; null when nobody has rated it yet. */
    val rating: Double?,
    /** TMDB's image path for the backdrop, or null. */
    val backdrop: String?,
)

sealed interface TrendingLookup {
    data class Found(val titles: List<TitleSummary>) : TrendingLookup
    data object Offline : TrendingLookup
    /** Setup hasn't handed the Home a TMDB token, or TMDB refused it. */
    data object MissingToken : TrendingLookup
}

enum class ExplorarProblem { Offline, MissingToken }

/** The details screen of one Title: what the highlights knew at once, then the rest. */
data class DetailsState(
    val summary: TitleSummary,
    val details: TitleDetails? = null,
    val loading: Boolean = true,
    val problem: ExplorarProblem? = null,
    /** "Onde assistir"; empty hides the section. */
    val whereToWatch: List<WatchOption> = emptyList(),
    /** The Trailer to play; null hides "Assistir trailer". */
    val trailer: TitleVideo? = null,
)

data class ExplorarState(
    val window: TrendWindow = TrendWindow.Today,
    val filter: KindFilter = KindFilter.All,
    val titles: List<TitleSummary> = emptyList(),
    /** The Title the hero shows: the focused card, once focus settles. */
    val hero: TitleSummary? = null,
    val loading: Boolean = false,
    /** Why the highlights may be missing or old; the titles shown, if any, are the last ones fetched. */
    val problem: ExplorarProblem? = null,
    /** The Title whose details are open, if any; the hero keeps the card to come back to. */
    val details: DetailsState? = null,
)

/**
 * Explorar: TMDB's trending Titles of the day or the week, all or only movies or series.
 * Highlights are kept for [cacheFor] while the Home runs; the hero follows the focused card
 * once focus rests for [settle], so running along the row loads nothing on the way.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class Explorar(
    private val platform: TvPlatform,
    private val settle: kotlin.time.Duration = 300.milliseconds,
    private val cacheFor: Duration = Duration.ofHours(1),
) {

    private val _state = MutableStateFlow(ExplorarState())
    val state: StateFlow<ExplorarState> = _state.asStateFlow()

    private val choice = MutableStateFlow(TrendWindow.Today to KindFilter.All)
    private val focused = MutableStateFlow<TitleSummary?>(null)
    private val cache = HashMap<Pair<TrendWindow, KindFilter>, Pair<Instant, List<TitleSummary>>>()

    fun choose(window: TrendWindow) {
        choice.update { it.copy(first = window) }
    }

    fun choose(filter: KindFilter) {
        choice.update { it.copy(second = filter) }
    }

    /** The owner moved focus onto a card. */
    fun focus(title: TitleSummary) {
        focused.value = title
    }

    /** Loads and follows the owner's choices for as long as it runs. */
    suspend fun run() = coroutineScope {
        launch {
            focused.debounce(settle).collect { title -> if (title != null) _state.update { it.copy(hero = title) } }
        }
        choice.collectLatest { (window, filter) -> load(window, filter) }
    }

    /** Opens a Title's details: one TMDB request for everything the screen shows. */
    suspend fun openDetails(summary: TitleSummary) {
        _state.update { it.copy(hero = summary, details = DetailsState(summary)) }
        val details = when (val lookup = platform.titleDetails(summary.kind, summary.id)) {
            is DetailsLookup.Found -> DetailsState(
                summary,
                lookup.details,
                loading = false,
                whereToWatch = whereToWatch(lookup.details.offers, platform::isLaunchable).map { it.linkedTo(lookup.details) },
                trailer = chooseTrailer(lookup.details.videos),
            )
            DetailsLookup.Offline -> DetailsState(summary, loading = false, problem = ExplorarProblem.Offline)
            DetailsLookup.MissingToken -> DetailsState(summary, loading = false, problem = ExplorarProblem.MissingToken)
        }
        // The owner may have left the details while they loaded.
        _state.update { if (it.details?.summary == summary) it.copy(details = details) else it }
    }

    /** Opens the service's app, or its Play Store page when the app isn't installed. */
    /**
     * Opens the service's app on the Title itself when its id there is known, else on its search
     * for the Title, else just the app; a service not installed opens its Play Store page.
     */
    fun watch(option: WatchOption) {
        val packageName = option.packageName ?: return
        if (!platform.isLaunchable(packageName)) return platform.openPlayStorePage(packageName)
        if (option.link != null && platform.openLink(packageName, option.link)) return
        if (option.search != null && platform.openLink(packageName, option.search)) return
        platform.launch(packageName)
    }

    private fun WatchOption.linkedTo(details: TitleDetails): WatchOption {
        val service = StreamingService.entries.firstOrNull { it.packageName == packageName } ?: return this
        return copy(link = service.titleLink(details.serviceIds, details.kind, details.name), search = service.searchLink(details.name))
    }

    /**
     * The embedded player couldn't play the Trailer (YouTube refuses embedding for some videos,
     * or the WebView failed): the YouTube app plays it instead (ADR 0009).
     */
    fun trailerFailed(video: TitleVideo) = platform.openYouTubeVideo(video.key)

    fun closeDetails() {
        _state.update { it.copy(details = null) }
    }

    /** Fetches again, ignoring the cache: after Setup, or when the network comes back. */
    suspend fun retry() {
        val (window, filter) = choice.value
        load(window, filter, fresh = true)
    }

    private suspend fun load(window: TrendWindow, filter: KindFilter, fresh: Boolean = false) {
        val key = window to filter
        val now = platform.now().toInstant()
        val cached = cache[key]
        if (!fresh && cached != null && Duration.between(cached.first, now) < cacheFor) {
            show(window, filter, cached.second, problem = null)
            return
        }
        _state.update { it.copy(window = window, filter = filter, loading = true, titles = cached?.second ?: emptyList(), hero = cached?.second?.firstOrNull()) }
        when (val lookup = platform.trending(window, filter)) {
            is TrendingLookup.Found -> {
                cache[key] = now to lookup.titles
                show(window, filter, lookup.titles, problem = null)
            }
            TrendingLookup.Offline -> show(window, filter, cached?.second ?: emptyList(), ExplorarProblem.Offline)
            TrendingLookup.MissingToken -> show(window, filter, emptyList(), ExplorarProblem.MissingToken)
        }
    }

    private fun show(window: TrendWindow, filter: KindFilter, titles: List<TitleSummary>, problem: ExplorarProblem?) {
        focused.value = null
        // Coming back from a Title's details reloads the highlights: the card left keeps the hero.
        val hero = _state.value.hero?.takeIf { it in titles } ?: titles.firstOrNull()
        _state.update { ExplorarState(window, filter, titles, hero = hero, loading = false, problem = problem, details = it.details) }
    }
}
