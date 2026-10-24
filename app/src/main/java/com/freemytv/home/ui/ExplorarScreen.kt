package com.freemytv.home.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.android.TmdbImages
import com.freemytv.home.explorar.ExplorarProblem
import com.freemytv.home.explorar.ExplorarState
import com.freemytv.home.explorar.KindFilter
import com.freemytv.home.explorar.TitleKind
import com.freemytv.home.explorar.TitleSummary
import com.freemytv.home.explorar.TrendWindow
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens
import java.util.Locale

class ExplorarActions(
    val onWindow: (TrendWindow) -> Unit,
    val onFilter: (KindFilter) -> Unit,
    val onFocus: (TitleSummary) -> Unit,
    val onDetails: (TitleSummary) -> Unit,
)

/** Explorar: TMDB's trending movies and series, as in docs/design/explorar-today.png. */
@Composable
fun ExplorarScreen(
    state: ExplorarState,
    clock: StatusClock,
    images: TmdbImages,
    actions: ExplorarActions,
    onSection: (HomeSection) -> Unit,
    statusWidgets: @Composable () -> Unit,
    /** Coming back from a Title's details: focus returns to its card instead of the navigation. */
    returnToCard: Boolean = false,
) {
    val navigation = remember { FocusRequester() }
    val cards = remember { HashMap<String, FocusRequester>() }
    val row = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = state.titles.indexOf(state.hero)
        if (returnToCard && index >= 0) {
            row.scrollToItem(index)
            runCatching { cards.getOrPut(state.hero!!.key()) { FocusRequester() }.requestFocus() }
        } else {
            runCatching { navigation.requestFocus() }
        }
    }
    Box(Modifier.fillMaxSize().background(Tokens.Background)) {
        state.hero?.backdrop?.let { HeroBackdrop(it, images) }
        Column(Modifier.fillMaxSize()) {
            StatusBar(clock, Modifier.padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop), statusWidgets)
            HomeNavigation(HomeSection.Explorar, onSection, Modifier.padding(top = NAVIGATION_TOP), currentFocus = navigation)
            Row(
                Modifier.fillMaxWidth().padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Segmented(
                    options = listOf(TrendWindow.Today to stringResource(R.string.today), TrendWindow.Week to stringResource(R.string.week)),
                    selected = state.window,
                    onSelect = actions.onWindow,
                )
                SlidingHighlightGroup(rememberSlidingHighlight(), CircleShape) { group ->
                    Row(group, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(KindFilter.All to R.string.filter_all, KindFilter.Movies to R.string.filter_movies, KindFilter.Series to R.string.filter_series).forEach { (filter, label) ->
                            FilterPill(stringResource(label), filter == state.filter) { actions.onFilter(filter) }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(HERO_HEIGHT).padding(start = Tokens.SafeAreaHorizontal)) {
                state.hero?.let { Hero(it, Modifier.align(Alignment.BottomStart)) { actions.onDetails(it) } }
                    ?: ProblemText(state, Modifier.align(Alignment.CenterStart))
            }
            Spacer(Modifier.weight(1f))
            if (state.titles.isNotEmpty()) {
                state.problem?.let { ProblemText(state, Modifier.padding(start = Tokens.SafeAreaHorizontal, bottom = 6.dp)) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Tokens.SafeAreaHorizontal),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        stringResource(if (state.window == TrendWindow.Today) R.string.highlights_today else R.string.highlights_week),
                        color = Tokens.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(if (state.window == TrendWindow.Today) R.string.highlights_today_note else R.string.highlights_week_note),
                        color = Tokens.TextSecondary,
                        fontSize = 10.sp,
                    )
                }
                LazyRow(
                    state = row,
                    contentPadding = PaddingValues(horizontal = Tokens.SafeAreaHorizontal, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(state.titles, key = { _, title -> title.key() }) { _, title ->
                        TitleCard(
                            title,
                            images,
                            onFocus = { actions.onFocus(title) },
                            onClick = { actions.onDetails(title) },
                            modifier = Modifier.focusRequester(cards.getOrPut(title.key()) { FocusRequester() }),
                        )
                    }
                }
                Text(
                    stringResource(R.string.explorar_hint),
                    color = Tokens.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = Tokens.SafeAreaHorizontal, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroBackdrop(path: String, images: TmdbImages) {
    val picture by produceState<Bitmap?>(null, path) {
        value = images.file(path, "w1280")?.let { bakeBackdrop(it, 1280, 720, fadeFrom = 0.55f) }
    }
    picture?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
}

@Composable
private fun Hero(title: TitleSummary, modifier: Modifier, onDetails: () -> Unit) {
    Column(modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.name, color = Tokens.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.75).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(meta(title, ratingOutOfTen = true), color = Color(0xD9FFFFFF), fontSize = 12.sp)
        title.overview?.let { Text(it, color = Color(0xD9FFFFFF), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        PillButton(stringResource(R.string.see_details), R.drawable.ic_info, onDetails, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TitleCard(title: TitleSummary, images: TmdbImages, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val picture by produceState<Bitmap?>(null, title.backdrop) {
        value = title.backdrop?.let { images.file(it, "w780") }?.let { decodeNear(it, CARD_DECODE_WIDTH) }
    }
    Column(Modifier.width(CARD_WIDTH), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TvTile(onClick = onClick, width = CARD_WIDTH, height = CARD_HEIGHT, focusedScale = 1.05f, modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() }) {
            picture?.let { Image(it.asImageBitmap(), contentDescription = title.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(title.name, color = Tokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(meta(title, ratingOutOfTen = false), color = Tokens.TextSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ProblemText(state: ExplorarState, modifier: Modifier) {
    val text = when (state.problem) {
        ExplorarProblem.MissingToken -> stringResource(R.string.explorar_missing_token)
        ExplorarProblem.Offline -> stringResource(if (state.titles.isEmpty()) R.string.explorar_offline else R.string.explorar_offline_stale)
        null -> if (state.loading) stringResource(R.string.loading) else return
    }
    Text(text, color = if (state.problem != null) Tokens.Attention else Tokens.TextSecondary, fontSize = 12.sp, modifier = modifier.width(520.dp))
}

/** "Série • Suspense • 8,6 / 10": what is known of the kind, the main genre and the rating. */
@Composable
private fun meta(title: TitleSummary, ratingOutOfTen: Boolean): String {
    val kind = stringResource(if (title.kind == TitleKind.Movie) R.string.kind_movie else R.string.kind_series)
    val rating = title.rating?.let { String.format(PT_BR, "%.1f", it) + if (ratingOutOfTen) " / 10" else "" }
    return listOfNotNull(kind, title.genres.firstOrNull(), rating).joinToString("  •  ")
}

/** Two or three choices in a glass capsule, the chosen one on a white pill that slides when the choice changes. */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val highlight = rememberSlidingHighlight(selected = selected)
    SlidingHighlightGroup(highlight, CircleShape) { group ->
        Row(Modifier.background(Tokens.Glass, CircleShape).padding(4.dp).then(group), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                Surface(
                    onClick = { onSelect(value) },
                    modifier = Modifier.slidingHighlightItem(key = value),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    // The pill marks the choice; focus is a lighter glass of its own.
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Tokens.TextPrimary,
                        focusedContainerColor = Color(0x59FFFFFF),
                        focusedContentColor = Tokens.TextPrimary,
                    ),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.slidingHighlightItem(),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = highlightScale(1.1f),
        colors = highlightColors(
            content = if (selected) Tokens.TextPrimary else Tokens.TextSecondary,
            container = if (selected) Color(0x33FFFFFF) else Color.Transparent,
        ),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}

private fun TitleSummary.key() = "$kind-$id"

/** Where the Home Navigation sits under the Status Bar, on every section. */
internal val NAVIGATION_TOP = 16.dp

private val HERO_HEIGHT = 150.dp
private val CARD_WIDTH = 204.dp
private val CARD_HEIGHT = 105.dp
private const val CARD_DECODE_WIDTH = 420
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
