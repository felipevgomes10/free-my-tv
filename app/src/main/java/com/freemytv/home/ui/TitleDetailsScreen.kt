package com.freemytv.home.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.explorar.OfferKind
import com.freemytv.home.explorar.WatchOption
import com.freemytv.home.R
import com.freemytv.home.android.TmdbImages
import com.freemytv.home.explorar.AgeRating
import com.freemytv.home.explorar.DetailsState
import com.freemytv.home.explorar.ExplorarProblem
import com.freemytv.home.explorar.TitleKind
import com.freemytv.home.explorar.ageRating
import com.freemytv.home.explorar.facts
import com.freemytv.home.explorar.leadCast
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens
import java.util.Locale

/** A Title's details, as in docs/design/explorar-movie-details.png and explorar-series-details.png. */
@Composable
fun TitleDetailsScreen(
    state: DetailsState,
    clock: StatusClock,
    images: TmdbImages,
    onBack: () -> Unit,
    onWatch: (WatchOption) -> Unit,
    onTrailer: () -> Unit,
    statusWidgets: @Composable () -> Unit,
) {
    val back = remember { FocusRequester() }
    val trailer = remember { FocusRequester() }
    val firstOffer = remember { FocusRequester() }
    val details = state.details
    val hasTrailer = state.trailer != null
    val hasOffers = state.whereToWatch.isNotEmpty()
    // The trailer is the screen's main action when there is one; otherwise the way back.
    LaunchedEffect(hasTrailer) { runCatching { (if (hasTrailer) trailer else back).requestFocus() } }
    val summary = state.summary
    Box(Modifier.fillMaxSize().background(Tokens.Background)) {
        (details?.backdrop ?: summary.backdrop)?.let { path ->
            val picture by produceState<Bitmap?>(null, path) {
                value = images.file(path, "w1280")?.let { bakeBackdrop(it, 1280, 720, fadeFrom = 0.5f) }
            }
            picture?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.fillMaxSize().padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop)) {
            StatusBar(clock, trailing = statusWidgets)
            PillButton(
                stringResource(R.string.section_explorar),
                R.drawable.ic_chevron_left,
                onBack,
                Modifier
                    .padding(top = NAVIGATION_TOP)
                    .focusRequester(back)
                    .focusProperties { down = if (hasTrailer) trailer else if (hasOffers) firstOffer else FocusRequester.Default },
            )
            Text(
                details?.name ?: summary.name,
                color = Tokens.TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.75).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 20.dp),
            )
            if (details != null) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ageRating(details.certification)?.let { RatingBadge(it) }
                    val kind = stringResource(if (details.kind == TitleKind.Movie) R.string.kind_movie else R.string.kind_series)
                    details.facts(kind).forEach { Text(it, color = Tokens.TextPrimary, fontSize = 12.sp) }
                }
                details.rating?.let { rating ->
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("☆ " + String.format(PT_BR, "%.1f / 10", rating), color = Tokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.audience_rating), color = Tokens.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                (details?.overview ?: summary.overview)?.let {
                    Text(it, color = Color(0xD9FFFFFF), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(470.dp).padding(top = 12.dp))
                }
                Box(Modifier.weight(1f))
                if (hasTrailer) {
                    PillButton(
                        stringResource(R.string.watch_trailer),
                        R.drawable.ic_play,
                        onTrailer,
                        Modifier.focusRequester(trailer).focusProperties {
                            up = back
                            left = back
                            down = if (hasOffers) firstOffer else FocusRequester.Default
                        },
                    )
                }
            }
            when {
                state.problem == ExplorarProblem.MissingToken -> Problem(stringResource(R.string.explorar_missing_token))
                state.problem == ExplorarProblem.Offline -> Problem(stringResource(R.string.details_offline))
                state.loading -> Text(stringResource(R.string.loading), color = Tokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            }
            if (state.whereToWatch.isNotEmpty()) {
                Text(stringResource(R.string.where_to_watch), color = Tokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
                SlidingHighlightGroup(rememberSlidingHighlight(), RoundedCornerShape(12.dp)) { group ->
                    LazyRow(
                        modifier = group,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(state.whereToWatch, key = { _, option -> option.name }) { index, option ->
                            val toTop = Modifier.focusProperties { up = if (hasTrailer) trailer else back }
                            WatchCard(option, if (index == 0) toTop.focusRequester(firstOffer) else toTop) { onWatch(option) }
                        }
                    }
                }
                Text(stringResource(R.string.where_to_watch_note), color = Tokens.TextSecondary, fontSize = 10.sp)
            }
            val cast = details?.leadCast().orEmpty()
            if (cast.isNotEmpty()) {
                Box(Modifier.weight(1f))
                Text(stringResource(R.string.cast), color = Tokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 30.dp)) {
                    cast.forEach { member ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(member.name, color = Tokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            member.character?.let { Text(it, color = Tokens.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/** A service offering the Title: ▷ opens its app, ↗ its Play Store page; one without a TV app can't be selected. */
@Composable
private fun WatchCard(option: WatchOption, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = option.packageName != null,
        modifier = Modifier.slidingHighlightItem().then(modifier).width(190.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = highlightScale(1.03f),
        colors = highlightColors(
            container = Color(0x2EFFFFFF),
            disabledContainer = Color(0x14FFFFFF),
            disabledContent = Tokens.TextSecondary,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (option.packageName != null) {
                Icon(painterResource(if (option.installed) R.drawable.ic_play else R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(option.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (option.kind == OfferKind.Subscription) R.string.offer_subscription else R.string.offer_rent_or_buy),
                    fontSize = 10.sp,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Brazil's age rating in ClassInd's colors. */
@Composable
private fun RatingBadge(rating: AgeRating) {
    val (fill, text) = when (rating) {
        AgeRating.Free -> Color(0xFF0C9447) to Color.White
        AgeRating.Ten -> Color(0xFF0F7DC2) to Color.White
        AgeRating.Twelve -> Color(0xFFF8C411) to Color.Black
        AgeRating.Fourteen -> Color(0xFFE67824) to Color.Black
        AgeRating.Sixteen -> Color(0xFFDB2827) to Color.White
        AgeRating.Eighteen -> Color(0xFF1D1815) to Color.White
    }
    Box(
        Modifier.size(22.dp).background(fill, RoundedCornerShape(4.dp)).border(if (rating == AgeRating.Eighteen) 1.dp else 0.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(rating.label, color = text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Problem(text: String) {
    Text(text, color = Tokens.Attention, fontSize = 12.sp, modifier = Modifier.width(520.dp).padding(top = 16.dp))
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
