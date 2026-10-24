package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens

/** Explorar: TMDB's trending movies and series. */
@Composable
fun ExplorarScreen(
    clock: StatusClock,
    onSection: (HomeSection) -> Unit,
    statusWidgets: @Composable () -> Unit,
) {
    val navigation = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { navigation.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Tokens.Background)) {
        StatusBar(clock, Modifier.padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop), statusWidgets)
        HomeNavigation(HomeSection.Explorar, onSection, Modifier.padding(top = NAVIGATION_TOP), currentFocus = navigation)
    }
}

/** Where the Home Navigation sits under the Status Bar, on every section. */
internal val NAVIGATION_TOP = 16.dp
