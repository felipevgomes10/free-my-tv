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
import androidx.tv.material3.Text
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
    statusWidgets: @Composable () -> Unit,
) {
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
    val details = state.details
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
            PillButton(stringResource(R.string.section_explorar), R.drawable.ic_chevron_left, onBack, Modifier.padding(top = NAVIGATION_TOP).focusRequester(back))
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
            (details?.overview ?: summary.overview)?.let {
                Text(it, color = Color(0xD9FFFFFF), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(470.dp).padding(top = 12.dp))
            }
            when {
                state.problem == ExplorarProblem.MissingToken -> Problem(stringResource(R.string.explorar_missing_token))
                state.problem == ExplorarProblem.Offline -> Problem(stringResource(R.string.details_offline))
                state.loading -> Text(stringResource(R.string.loading), color = Tokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
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
