package com.freemytv.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.buttons.ButtonTarget
import com.freemytv.home.buttons.ShortcutButton
import com.freemytv.home.inputs.InputTile
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.ui.theme.Tokens

/** What the Botões do controle section shows. */
class ButtonsPanelState(
    val assignments: Map<ShortcutButton, ButtonTarget>,
    val targetOf: (ShortcutButton) -> ButtonTarget,
    val apps: List<InstalledApp>,
    val inputs: List<InputTile>,
)

/** Each Shortcut Button and what it opens; selecting one picks a new target. Null restores the factory behaviour. */
@Composable
fun ButtonsPanel(state: ButtonsPanelState, onAssign: (ShortcutButton, ButtonTarget?) -> Unit) {
    var picking by remember { mutableStateOf<ShortcutButton?>(null) }
    ShortcutButton.entries.forEach { button ->
        val changed = button in state.assignments
        Surface(
            onClick = { picking = button },
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
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Keycap(buttonLabel(button))
                Box(Modifier.weight(1f))
                if (changed) Text(stringResource(R.string.changed), fontSize = 9.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
                Text(
                    targetLabel(state.targetOf(button), state),
                    fontSize = 12.sp,
                    fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
                    color = if (changed) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.6f),
                )
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(12.dp), tint = LocalContentColor.current.copy(alpha = 0.6f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
    }
    CompositionLocalProvider(LocalContentColor provides Tokens.TextPrimary) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Keycap(stringResource(R.string.home_held))
            Box(Modifier.weight(1f))
            Text(stringResource(R.string.notifications), fontSize = 12.sp, color = Tokens.TextSecondary)
        }
    }
    Text(stringResource(R.string.buttons_note), color = Tokens.TextSecondary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(12.dp))

    picking?.let { button ->
        TargetPicker(
            button = button,
            state = state,
            onPick = { target -> onAssign(button, target); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun Keycap(label: String) {
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(BorderStroke(1.dp, LocalContentColor.current.copy(alpha = 0.35f)), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/** Choices for one button: its factory behaviour, the App Switcher, the Input Tray, each Input and each app. */
@Composable
private fun TargetPicker(button: ShortcutButton, state: ButtonsPanelState, onPick: (ButtonTarget?) -> Unit, onDismiss: () -> Unit) {
    val factory = button.factoryApp?.let { ButtonTarget.App(it) } ?: ButtonTarget.InputTray
    val choices: List<Pair<String, ButtonTarget?>> = buildList {
        add(stringResource(R.string.factory_default, targetLabel(factory, state)) to null)
        add(stringResource(R.string.app_switcher) to ButtonTarget.AppSwitcher)
        add(stringResource(R.string.input_tray) to ButtonTarget.InputTray)
        state.inputs.forEach { add(stringResource(R.string.input_named, it.displayName()) to ButtonTarget.TvInput(it.id)) }
        state.apps.forEach { add(it.label to ButtonTarget.App(it.packageName)) }
    }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(300.dp).background(Tokens.Menu, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.button_opens, buttonLabel(button)), color = Tokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(choices) { (label, target) ->
                    Surface(
                        onClick = { onPick(target) },
                        modifier = Modifier.fillMaxWidth().then(if (target == null) Modifier.focusRequester(first) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = Tokens.TextPrimary,
                            focusedContainerColor = Tokens.TextPrimary,
                            focusedContentColor = Color.Black,
                        ),
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun buttonLabel(button: ShortcutButton): String = when (button) {
    ShortcutButton.Netflix -> "Netflix"
    ShortcutButton.Globoplay -> "Globoplay"
    ShortcutButton.PrimeVideo -> "Prime Video"
    ShortcutButton.DisneyPlus -> "Disney+"
    ShortcutButton.YouTube -> "YouTube"
    ShortcutButton.Input -> stringResource(R.string.input_button)
}

@Composable
private fun targetLabel(target: ButtonTarget, state: ButtonsPanelState): String = when (target) {
    // A factory app that isn't installed still reads as the service printed on the button.
    is ButtonTarget.App -> state.apps.firstOrNull { it.packageName == target.packageName }?.label
        ?: ShortcutButton.entries.firstOrNull { it.factoryApp == target.packageName }?.let { buttonLabel(it) }
        ?: target.packageName
    is ButtonTarget.TvInput -> state.inputs.firstOrNull { it.id == target.inputId }?.displayName() ?: stringResource(R.string.inputs)
    ButtonTarget.AppSwitcher -> stringResource(R.string.app_switcher)
    ButtonTarget.InputTray -> stringResource(R.string.input_tray)
}
