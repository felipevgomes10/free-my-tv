package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.home.StatusClock
import com.freemytv.home.monitor.AppUsage
import com.freemytv.home.monitor.MonitorState
import com.freemytv.home.ui.theme.Tokens
import java.util.Locale
import kotlin.math.roundToInt

/** The System Monitor Widget's compact form: storage used, RAM in use, the Home's fps and the display's Hz. */
@Composable
fun MonitorPill(state: MonitorState, onClick: () -> Unit) {
    if (!state.visible) return
    WidgetPill(onClick) {
        val color = LocalContentColor.current
        val dim = color.copy(alpha = 0.55f)
        Reading(R.drawable.ic_storage, state.storage?.let { "${(it.usedBytes * 100 / it.totalBytes)}%" } ?: "—", stringResource(R.string.used), dim)
        Reading(R.drawable.ic_memory, state.memory?.let { formatBytes(it.usedBytes) } ?: "—", state.memory?.let { stringResource(R.string.of_total, formatBytes(it.totalBytes)) }.orEmpty(), dim)
        Reading(R.drawable.ic_speed, state.homeFps?.let { stringResource(R.string.fps, it) } ?: "—", state.refreshRate?.let { stringResource(R.string.hz, it) }.orEmpty(), dim)
    }
}

@Composable
private fun Reading(icon: Int, value: String, unit: String, dim: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = dim, modifier = Modifier.size(11.dp))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        if (unit.isNotEmpty()) Text(unit, fontSize = 9.sp, color = dim)
    }
}

/** The System Monitor Widget's detail: storage and memory with the apps behind them, and the last minute of frame rates. */
@Composable
fun MonitorScreen(
    state: MonitorState,
    clock: StatusClock,
    statusWidgets: @Composable () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onUninstall: (AppUsage) -> Unit,
    onCloseApp: (AppUsage) -> Unit,
) {
    DisposableEffect(Unit) {
        onOpen()
        onDispose(onClose)
    }
    val detail = state.detail
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .padding(horizontal = Tokens.SafeAreaHorizontal)
            .padding(top = Tokens.SafeAreaTop),
    ) {
        StatusBar(clock, trailing = statusWidgets)
        Text(
            stringResource(R.string.system_monitor),
            color = Tokens.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(top = 24.dp, bottom = 20.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            val storage = state.storage
            Column(Modifier.weight(1f)) {
                Headline(
                    big = storage?.let { formatBytes(it.usedBytes) } ?: "—",
                    unit = storage?.let { stringResource(R.string.of_total, formatBytes(it.totalBytes)) }.orEmpty(),
                    sub = storage?.let { stringResource(R.string.free_space, formatBytes(it.freeBytes)) }.orEmpty(),
                    ratio = storage?.let { it.usedBytes.toFloat() / it.totalBytes } ?: 0f,
                )
                AppList(detail?.topStorage, stringResource(R.string.uninstall), Tokens.DestructiveOnLight, { it.canUninstall }, onUninstall, takesFirstFocus = true)
            }
            val memory = state.memory
            Column(Modifier.weight(1f)) {
                Headline(
                    big = memory?.let { formatBytes(it.usedBytes) } ?: "—",
                    unit = memory?.let { stringResource(R.string.of_total, formatBytes(it.totalBytes)) }.orEmpty(),
                    sub = memory?.let { stringResource(R.string.memory_left, formatBytes(it.availableBytes)) }.orEmpty(),
                    ratio = memory?.let { it.usedBytes.toFloat() / it.totalBytes } ?: 0f,
                )
                AppList(detail?.topMemory, stringResource(R.string.close_app), Color.Black, { it.canClose }, onCloseApp, takesFirstFocus = false)
            }
        }
        FrameRate(state.homeFps, state.refreshRate ?: 60, detail?.fpsHistory.orEmpty())
    }
}

@Composable
private fun Headline(big: String, unit: String, sub: String, ratio: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            Text(big, color = Tokens.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, lineHeight = 36.sp)
            Text(unit, color = Tokens.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(sub, color = Tokens.TextSecondary, fontSize = 11.sp)
        Meter(ratio)
    }
}

@Composable
private fun Meter(ratio: Float) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(3.dp))) {
        Box(
            Modifier
                .width(maxWidth * ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(if (ratio > ATTENTION_ABOVE) Tokens.Attention else Tokens.TextPrimary, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun AppList(
    apps: List<AppUsage>?,
    actionLabel: String,
    actionColor: Color,
    allowed: (AppUsage) -> Boolean,
    onAction: (AppUsage) -> Unit,
    takesFirstFocus: Boolean,
) {
    val first = remember { FocusRequester() }
    SlidingHighlightGroup(rememberSlidingHighlight(), RoundedCornerShape(8.dp)) { group ->
        Column(Modifier.padding(top = 6.dp).then(group)) {
            if (apps == null) {
                Text(stringResource(R.string.needs_setup), color = Tokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                return@Column
            }
            apps.forEachIndexed { index, app ->
                var focused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { if (allowed(app)) onAction(app) },
                    modifier = Modifier
                        .slidingHighlightItem()
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = highlightColors(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(app.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(formatBytes(app.bytes), fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
                        if (focused && allowed(app)) {
                            Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = actionColor, modifier = Modifier.keepColorsUnderHighlight())
                        }
                    }
                }
            }
        }
    }
    if (takesFirstFocus && !apps.isNullOrEmpty()) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun FrameRate(fps: Int?, refreshRate: Int, history: List<Int>) {
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Text(fps?.let { stringResource(R.string.fps, it) } ?: "—", color = Tokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.frame_rate_window, refreshRate), color = Tokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            val bars = List(MonitorWindow - history.size) { null } + history
            bars.forEach { value ->
                val ratio = ((value ?: 0).toFloat() / refreshRate).coerceIn(0.05f, 1f)
                val slow = value != null && value < refreshRate * SLOW_BELOW
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(if (value == null) 0.05f else ratio)
                        .background(if (slow) Tokens.Attention else Color(0x59FFFFFF), RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/** Decimal units, Brazilian style: "1,4 GB", "580 MB". */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(PT_BR, "%.1f GB", bytes / 1e9)
    else -> "${(bytes / 1e6).roundToInt()} MB"
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private const val MonitorWindow = 60
private const val ATTENTION_ABOVE = 0.8f
private const val SLOW_BELOW = 0.85f
