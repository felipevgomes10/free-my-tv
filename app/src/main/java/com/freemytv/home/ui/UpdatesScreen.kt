package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.updates.SideloadedApp
import com.freemytv.home.updates.UpdateProblem
import com.freemytv.home.updates.UpdatesState
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UpdatesScreen(
    state: UpdatesState,
    clock: StatusClock,
    statusWidgets: @Composable () -> Unit,
    onCheckSystemUpdate: () -> Unit,
    onOpenPlayStoreApps: () -> Unit,
    onSetSource: (packageName: String, input: String) -> Unit,
    onUpdate: (packageName: String) -> Unit,
) {
    var editingSource by remember { mutableStateOf<SideloadedApp?>(null) }
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
        Text(
            stringResource(R.string.updates),
            color = Tokens.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(top = 20.dp, bottom = 14.dp),
        )
        Section(stringResource(R.string.system)) {
            val device = state.device
            Text(
                device?.securityPatch?.let { stringResource(R.string.patch_of, it.format(LONG_DATE)) } ?: stringResource(R.string.no_patch),
                color = Tokens.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (device != null) {
                Text(stringResource(R.string.firmware_line, device.androidVersion, device.build), color = Tokens.TextSecondary, fontSize = 10.sp)
            }
            if (state.patchOutdated && state.patchAgeMonths != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = Tokens.Attention, modifier = Modifier.size(12.dp))
                    Text(
                        pluralStringResource(R.plurals.patch_outdated, state.patchAgeMonths.toInt(), state.patchAgeMonths.toInt()),
                        color = Tokens.Attention,
                        fontSize = 11.sp,
                    )
                }
            }
            PillAction(stringResource(R.string.check_system_update), R.drawable.ic_refresh, onCheckSystemUpdate, Modifier.focusRequester(first))
        }
        Section(stringResource(R.string.play_store_apps)) {
            Text(stringResource(R.string.play_store_note), color = Tokens.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            PillAction(stringResource(R.string.open_play_store_apps), R.drawable.ic_open_in_new, onOpenPlayStoreApps)
        }
        Section(stringResource(R.string.apk_apps)) {
            if (state.sideloaded.isEmpty()) {
                Text(stringResource(R.string.no_apk_apps), color = Tokens.TextSecondary, fontSize = 11.sp)
            }
            state.sideloaded.forEach { app ->
                SideloadedRow(app, onEditSource = { editingSource = app }, onUpdate = { onUpdate(app.packageName) })
            }
        }
    }

    editingSource?.let { app ->
        TextInputDialog(
            title = stringResource(R.string.update_source_title, app.label),
            hint = stringResource(R.string.update_source_hint),
            current = app.source.orEmpty(),
            onDone = { onSetSource(app.packageName, it); editingSource = null },
            onDismiss = { editingSource = null },
        )
    }
}

/** An app installed from an APK: its version, its Update Source, and what can be done about updates. */
@Composable
private fun SideloadedRow(app: SideloadedApp, onEditSource: () -> Unit, onUpdate: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${app.label}  ${app.version}", color = Tokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(app.source?.let { "github.com/$it" } ?: stringResource(R.string.no_update_source), color = Tokens.TextSecondary, fontSize = 10.sp)
                app.problem?.let { Text(problemText(it), color = Tokens.Attention, fontSize = 10.sp) }
            }
            val update = app.update
            when {
                app.updating -> Text(stringResource(R.string.updating), color = Tokens.TextSecondary, fontSize = 11.sp)
                update != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!app.fixedSource) TextAction(stringResource(R.string.change_source), onEditSource)
                    PillAction(stringResource(R.string.update_to, update.version), R.drawable.ic_download, onUpdate)
                }
                // The Home's own source is this project's repository, always.
                app.fixedSource -> Unit
                else -> TextAction(stringResource(if (app.source == null) R.string.set_source else R.string.change_source), onEditSource)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
    }
}

@Composable
private fun problemText(problem: UpdateProblem) = stringResource(
    when (problem) {
        UpdateProblem.NotFound -> R.string.source_not_found
        UpdateProblem.NoApk -> R.string.source_no_apk
        UpdateProblem.Offline -> R.string.source_offline
        UpdateProblem.DownloadFailed -> R.string.update_download_failed
        UpdateProblem.InstallFailed -> R.string.update_install_failed
    },
)

/** A quiet action, as in the design's "Associar origem": plain text that turns into a pill when focused. */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Tokens.TextSecondary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

/** A titled block of Updates, separated from the one above by a hairline. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Text(title, color = Tokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
        }
    }
}

@Composable
private fun PillAction(label: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(top = 4.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Tokens.Glass,
            contentColor = Tokens.TextPrimary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(12.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The Updates icon on the Status Bar, with an attention dot when something needs the owner. */
@Composable
fun UpdatesStatusButton(needsAttention: Boolean, contentDescription: String, onClick: () -> Unit) {
    Box {
        StatusIconButton(R.drawable.ic_download, contentDescription, onClick)
        if (needsAttention) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(6.dp).background(Tokens.Attention, CircleShape))
        }
    }
}

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
