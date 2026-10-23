package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.apps.AllAppsItem
import com.freemytv.home.apps.AllAppsState
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens

@Composable
fun AllAppsScreen(
    state: AllAppsState,
    clock: StatusClock,
    artwork: AppArtworkLoader,
    onOpen: (AllAppsItem) -> Unit,
    onTogglePin: (AllAppsItem) -> Unit,
    onUninstall: (AllAppsItem) -> Unit,
    onAppInfo: (AllAppsItem) -> Unit,
    onInstallApp: () -> Unit,
) {
    var actionsFor by remember { mutableStateOf<AllAppsItem?>(null) }
    val firstApp = remember { FocusRequester() }

    LaunchedEffect(state.apps.isNotEmpty()) {
        if (state.apps.isNotEmpty()) runCatching { firstApp.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .padding(top = Tokens.SafeAreaTop),
    ) {
        StatusBar(clock, Modifier.padding(start = Tokens.SafeAreaHorizontal))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Tokens.SafeAreaHorizontal, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.all_apps), color = Tokens.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text(
                    pluralStringResource(R.plurals.apps_installed, state.apps.size, state.apps.size) + ", " +
                        pluralStringResource(R.plurals.apps_pinned, state.pinnedCount, state.pinnedCount),
                    color = Tokens.TextSecondary,
                    fontSize = 11.sp,
                )
            }
            PillButton(stringResource(R.string.install_app), R.drawable.ic_add, onInstallApp)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            contentPadding = PaddingValues(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = 8.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(state.apps, key = { it.packageName }) { app ->
                AllAppsTile(
                    app = app,
                    artwork = artwork,
                    onOpen = { onOpen(app) },
                    showActions = actionsFor == app,
                    onActions = { actionsFor = app },
                    actions = listOf(
                        if (app.isPinned) MenuItem(stringResource(R.string.unpin), R.drawable.ic_close) { onTogglePin(app); actionsFor = null }
                        else MenuItem(stringResource(R.string.pin), R.drawable.ic_pin) { onTogglePin(app); actionsFor = null },
                        MenuItem(stringResource(R.string.uninstall), R.drawable.ic_delete, destructive = true) { onUninstall(app); actionsFor = null },
                        MenuItem(stringResource(R.string.app_info), R.drawable.ic_info) { onAppInfo(app); actionsFor = null },
                    ),
                    onDismissActions = { actionsFor = null },
                    modifier = if (app == state.apps.first()) Modifier.focusRequester(firstApp) else Modifier,
                )
            }
        }
    }

}

@Composable
private fun AllAppsTile(
    app: AllAppsItem,
    artwork: AppArtworkLoader,
    onOpen: () -> Unit,
    showActions: Boolean,
    onActions: () -> Unit,
    actions: List<MenuItem>,
    onDismissActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TvTile(
            onClick = onOpen,
            onLongClick = onActions,
            width = TILE_WIDTH,
            height = TILE_HEIGHT,
            focusedScale = 1.06f,
            modifier = modifier
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    (event.key == Key.Menu && event.type == KeyEventType.KeyUp).also { if (it) onActions() }
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                AppArt(app.packageName, app.label, app.art, artwork)
                if (app.isPinned) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(16.dp)
                            .background(Color(0xA6000000), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_pin), contentDescription = stringResource(R.string.pinned), tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }
            if (showActions) ItemActionsMenu(actions, onDismissActions)
        }
        Text(
            app.label,
            color = if (focused) Tokens.TextPrimary else Tokens.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PillButton(label: String, icon: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Tokens.Glass,
            contentColor = Tokens.TextPrimary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val COLUMNS = 5
private val TILE_WIDTH = 160.dp
private val TILE_HEIGHT = 90.dp
