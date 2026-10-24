package com.freemytv.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.notifications.NotificationsState
import com.freemytv.home.notifications.TvNotification
import com.freemytv.home.ui.theme.Tokens
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The bell on the Status Bar: only while there are notifications, with how many. */
@Composable
fun NotificationsBell(state: NotificationsState, onClick: () -> Unit) {
    if (state.notifications.isEmpty()) return
    Surface(
        onClick = onClick,
        modifier = Modifier.slidingHighlightItem(),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = highlightScale(1.1f),
        colors = highlightColors(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = stringResource(R.string.notifications), modifier = Modifier.size(14.dp))
            Text("${state.notifications.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The Notification Panel, over whatever screen is open, on the right. */
@Composable
fun NotificationPanel(
    state: NotificationsState,
    artwork: AppArtworkLoader,
    now: Instant,
    onOpen: (TvNotification) -> Unit,
    onClearAll: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(state.notifications.isEmpty()) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xA6000000))) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 65.dp, end = Tokens.SafeAreaHorizontal)
                .width(320.dp)
                .shadow(40.dp, RoundedCornerShape(16.dp))
                .background(Tokens.Menu, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications), color = Tokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (state.notifications.any { it.clearable }) {
                    PanelButton(stringResource(R.string.clear_all), onClearAll)
                }
            }
            when {
                !state.hasAccess -> PanelNote(stringResource(R.string.notifications_need_setup), Modifier.focusRequester(first))
                state.notifications.isEmpty() -> PanelNote(stringResource(R.string.no_notifications), Modifier.focusRequester(first))
                else -> SlidingHighlightGroup(rememberSlidingHighlight(), RoundedCornerShape(10.dp)) { group ->
                    LazyColumn(group, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.notifications, key = { it.key }) { notification ->
                            NotificationCard(
                                notification,
                                artwork,
                                now,
                                onClick = { onOpen(notification) },
                                modifier = if (notification == state.notifications.first()) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The Notification Banner, top right, for a few seconds after a notification arrives. */
@Composable
fun NotificationBanner(notification: TvNotification, artwork: AppArtworkLoader, now: Instant) {
    Box(Modifier.fillMaxSize().padding(top = 65.dp, end = Tokens.SafeAreaHorizontal), contentAlignment = Alignment.TopEnd) {
        Box(Modifier.width(300.dp).shadow(30.dp, RoundedCornerShape(10.dp)).background(Tokens.Menu, RoundedCornerShape(10.dp))) {
            NotificationContent(notification, artwork, now, Tokens.TextPrimary)
        }
    }
}

@Composable
private fun NotificationCard(notification: TvNotification, artwork: AppArtworkLoader, now: Instant, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = Modifier.slidingHighlightItem().then(modifier).fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = highlightColors(container = Color(0x0FFFFFFF)),
    ) {
        NotificationContent(notification, artwork, now, LocalContentColor.current)
    }
}

@Composable
private fun NotificationContent(notification: TvNotification, artwork: AppArtworkLoader, now: Instant, color: Color) {
    val icon by produceState(artwork.cachedIcon(notification.packageName), notification.packageName) { value = artwork.icon(notification.packageName) }
    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(24.dp).background(Color(0x14FFFFFF), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(18.dp).keepColorsUnderHighlight()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(notification.appLabel, color = color.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                Text(ago(notification.postedAt, now), color = color.copy(alpha = 0.6f), fontSize = 9.sp)
            }
            if (notification.title.isNotBlank()) Text(notification.title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (notification.text.isNotBlank()) Text(notification.text, color = color.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Tokens.TextSecondary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

/** A focusable line of text, so Back still has something holding focus in the panel. */
@Composable
private fun PanelNote(text: String, modifier: Modifier) {
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Tokens.TextSecondary, focusedContainerColor = Color.Transparent, focusedContentColor = Tokens.TextSecondary),
    ) {
        Text(text, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(10.dp))
    }
}

/** How long ago, Brazilian style: "agora", "há 5 min", "há 2 h", "ontem", "21 out". */
@Composable
fun ago(posted: Instant, now: Instant): String {
    val ago = Duration.between(posted, now)
    val zone = ZoneId.systemDefault()
    return when {
        ago.toMinutes() < 1 -> stringResource(R.string.just_now)
        ago.toMinutes() < 60 -> stringResource(R.string.minutes_ago, ago.toMinutes().toInt())
        posted.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() -> stringResource(R.string.hours_ago, ago.toHours().toInt())
        posted.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate().minusDays(1) -> stringResource(R.string.yesterday)
        else -> posted.atZone(zone).format(SHORT_DATE)
    }
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pt-BR"))
