package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.freemytv.home.buttons.ButtonTarget
import com.freemytv.home.buttons.ShortcutButton
import com.freemytv.home.multitasking.OpenAppLimit
import com.freemytv.home.android.SetupNotifications
import com.freemytv.home.settings.CastReceiver
import com.freemytv.home.settings.PackageState
import com.freemytv.home.settings.SettingsState
import com.freemytv.home.setup.SetupCheck
import com.freemytv.home.setup.SetupState
import com.freemytv.home.ui.theme.Tokens
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sections of Settings, in the order the owner sees them. */
enum class SettingsSection(val title: Int, val icon: Int) {
    Widgets(R.string.section_widgets, R.drawable.ic_widgets),
    OpenApps(R.string.section_open_apps, R.drawable.ic_apps),
    Buttons(R.string.section_buttons, R.drawable.ic_settings_remote),
    Cast(R.string.section_cast, R.drawable.ic_cast),
    Setup(R.string.section_setup, R.drawable.ic_build),
    About(R.string.section_about, R.drawable.ic_info),
    AndroidSettings(R.string.section_android_settings, R.drawable.ic_open_in_new),
}

class SettingsActions(
    val onWeatherVisible: (Boolean) -> Unit,
    val onMonitorVisible: (Boolean) -> Unit,
    val onWeatherCity: (String) -> Unit,
    val onOpenAndroidSettings: () -> Unit,
    val onOpenAppLimit: (Int) -> Unit,
    val onAssignButton: (ShortcutButton, ButtonTarget?) -> Unit,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    openAppLimit: Int,
    buttons: ButtonsPanelState,
    setup: SetupState,
    clock: StatusClock,
    statusWidgets: @Composable () -> Unit,
    actions: SettingsActions,
) {
    var section by remember { mutableStateOf(SettingsSection.Widgets) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .padding(horizontal = Tokens.SafeAreaHorizontal)
            .padding(top = Tokens.SafeAreaTop),
    ) {
        StatusBar(clock, trailing = statusWidgets)
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings), color = Tokens.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Box(Modifier.height(12.dp))
                SettingsSection.entries.forEachIndexed { index, entry ->
                    SectionItem(
                        entry,
                        selected = entry == section,
                        onFocus = { section = entry },
                        onClick = { if (entry == SettingsSection.AndroidSettings) actions.onOpenAndroidSettings() },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(top = 48.dp)) {
                when (section) {
                    SettingsSection.Widgets -> WidgetsPanel(state, actions)
                    SettingsSection.OpenApps -> {
                        ValueRow(stringResource(R.string.open_app_limit), stringResource(R.string.open_app_limit_detail), stringResource(R.string.open_app_limit_value, openAppLimit)) {
                            actions.onOpenAppLimit(if (openAppLimit >= OpenAppLimit.RANGE.last) OpenAppLimit.RANGE.first else openAppLimit + 1)
                        }
                        Note(stringResource(R.string.open_app_limit_note))
                    }
                    SettingsSection.Buttons -> ButtonsPanel(buttons, actions.onAssignButton)
                    SettingsSection.Cast -> CastPanel(state)
                    SettingsSection.Setup -> SetupPanel(setup)
                    SettingsSection.About -> AboutPanel(state)
                    SettingsSection.AndroidSettings -> Note(stringResource(R.string.android_settings_note))
                }
            }
        }
    }
}

@Composable
private fun SectionItem(entry: SettingsSection, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Tokens.Glass else Color.Transparent,
            contentColor = Tokens.TextPrimary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(entry.icon), contentDescription = null, modifier = Modifier.size(13.dp))
            Text(stringResource(entry.title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WidgetsPanel(state: SettingsState, actions: SettingsActions) {
    var editingCity by remember { mutableStateOf(false) }
    val widgets = state.widgets
    ToggleRow(stringResource(R.string.weather_widget), stringResource(R.string.weather_widget_detail), widgets.weatherVisible) { actions.onWeatherVisible(!widgets.weatherVisible) }
    ValueRow(stringResource(R.string.weather_city), null, widgets.weatherCity ?: stringResource(R.string.automatic)) { editingCity = true }
    ToggleRow(stringResource(R.string.monitor_widget), stringResource(R.string.monitor_widget_detail), widgets.monitorVisible) { actions.onMonitorVisible(!widgets.monitorVisible) }
    Note(stringResource(R.string.hidden_widget_note))
    if (editingCity) {
        TextInputDialog(
            title = stringResource(R.string.weather_city),
            hint = stringResource(R.string.weather_city_hint),
            current = widgets.weatherCity.orEmpty(),
            onDone = { actions.onWeatherCity(it); editingCity = false },
            onDismiss = { editingCity = false },
        )
    }
}

@Composable
private fun CastPanel(state: SettingsState) {
    InfoRow(stringResource(R.string.tv_name), state.tvName ?: "—")
    state.cast.forEach { status ->
        InfoRow(
            stringResource(
                when (status.receiver) {
                    CastReceiver.Chromecast -> R.string.chromecast
                    CastReceiver.AirPlay -> R.string.airplay
                    CastReceiver.Miracast -> R.string.miracast
                },
            ),
            stringResource(
                when (status.state) {
                    PackageState.Enabled -> R.string.receiver_on
                    PackageState.Disabled -> R.string.receiver_off
                    PackageState.NotInstalled -> R.string.receiver_missing
                },
            ),
        )
    }
    Note(stringResource(R.string.cast_note, state.tvName ?: stringResource(R.string.this_tv)))
}

@Composable
private fun SetupPanel(setup: SetupState) {
    Note(
        stringResource(
            when {
                setup.active -> R.string.setup_active
                !setup.everSetUp -> R.string.setup_never
                else -> R.string.setup_missing
            },
        ),
    )
    SetupCheck.entries.forEach { check ->
        val holds = setup.checks[check] == true
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(SetupNotifications.labelOf(check)), color = Tokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (holds) R.string.check_ok else R.string.check_missing),
                color = if (holds) Color(0xFF30D158) else Tokens.Attention,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Divider()
    }
}

@Composable
private fun AboutPanel(state: SettingsState) {
    val device = state.device ?: return
    InfoRow(stringResource(R.string.app_name), device.homeVersion)
    InfoRow(stringResource(R.string.tv), "${device.manufacturer} ${device.model}")
    InfoRow(stringResource(R.string.android), device.androidVersion)
    InfoRow(stringResource(R.string.security_patch), device.securityPatch?.format(LONG_DATE) ?: "—")
    InfoRow(stringResource(R.string.firmware), device.build)
}

/** A setting the owner turns on and off. */
@Composable
private fun ToggleRow(title: String, detail: String?, on: Boolean, onToggle: () -> Unit) {
    SettingRow(title, detail, onToggle) { Toggle(on) }
}

/** A setting with a value the owner changes elsewhere. */
@Composable
private fun ValueRow(title: String, detail: String?, value: String, onClick: () -> Unit) {
    SettingRow(title, detail, onClick) {
        Text(value, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(12.dp), tint = LocalContentColor.current.copy(alpha = 0.6f))
    }
}

@Composable
private fun SettingRow(title: String, detail: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Tokens.TextPrimary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (detail != null) Text(detail, fontSize = 10.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
            }
            trailing()
        }
    }
    Divider()
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Tokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = Tokens.TextSecondary, fontSize = 12.sp)
    }
    Divider()
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
}

@Composable
private fun Note(text: String) {
    Text(text, color = Tokens.TextSecondary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
}

@Composable
private fun Toggle(on: Boolean) {
    Box(
        Modifier
            .size(34.dp, 20.dp)
            .background(if (on) Color(0xFF30D158) else LocalContentColor.current.copy(alpha = 0.25f), CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = if (on) 16.dp else 2.dp, y = 2.dp)
                .size(16.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** The gear on the Status Bar. */
@Composable
fun StatusIconButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Tokens.TextSecondary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.padding(7.dp).size(14.dp))
    }
}

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
