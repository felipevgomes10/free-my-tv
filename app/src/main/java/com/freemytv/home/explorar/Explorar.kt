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

data class ExplorarState(
    val window: TrendWindow = TrendWindow.Today,
    val filter: KindFilter = KindFilter.All,
    val titles: List<TitleSummary> = emptyList(),
    /** The Title the hero shows: the focused card, once focus settles. */
    val hero: TitleSummary? = null,
    val loading: Boolean = false,
    /** Why the highlights may be missing or old; the titles shown, if any, are the last ones fetched. */
    val problem: ExplorarProblem? = null,
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
        _state.value = ExplorarState(window, filter, titles, hero = titles.firstOrNull(), loading = false, problem = problem)
    }
}
