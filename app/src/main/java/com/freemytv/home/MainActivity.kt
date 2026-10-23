package com.freemytv.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.freemytv.home.android.AndroidTvPlatform
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.apps.AllAppsStateHolder
import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.home.AppTrayItem
import com.freemytv.home.home.HomeStateHolder
import com.freemytv.home.ui.AllAppsScreen
import com.freemytv.home.ui.HomeActions
import com.freemytv.home.ui.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private enum class Screen { Home, AllApps }

    private var screen by mutableStateOf(Screen.Home)
    private lateinit var home: HomeStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = AndroidTvPlatform(applicationContext)
        val library = AppLibrary(platform)
        home = HomeStateHolder(platform, library, lifecycleScope)
        val allApps = AllAppsStateHolder(platform, library, lifecycleScope)
        val artwork = AppArtworkLoader(applicationContext)

        // Back leaves Edit Mode, then All Apps; on the Home itself it has nowhere to go.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (home.state.value.lifted != null) home.cancelEdit() else screen = Screen.Home
            }
        })

        lifecycleScope.launch { library.followPackageEvents() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                home.refresh()
                while (true) {
                    home.tick()
                    delay(CLOCK_TICK_MILLIS)
                }
            }
        }

        setContent {
            val scope = rememberCoroutineScope()
            val homeState by home.state.collectAsStateWithLifecycle()
            when (screen) {
                Screen.Home -> HomeScreen(
                    state = homeState,
                    artwork = artwork,
                    actions = HomeActions(
                        onSelect = { item -> if (item == AppTrayItem.AllApps) screen = Screen.AllApps else home.select(item) },
                        onStartEdit = home::startEdit,
                        onMove = home::move,
                        onDrop = { scope.launch { home.drop() } },
                        onUnpin = { scope.launch { home.unpinLifted() } },
                        onUninstall = home::uninstallLifted,
                    ),
                )
                Screen.AllApps -> {
                    val allAppsState by allApps.state.collectAsStateWithLifecycle()
                    AllAppsScreen(
                        state = allAppsState,
                        clock = homeState.statusClock,
                        artwork = artwork,
                        onOpen = { allApps.open(it.packageName) },
                        onTogglePin = { scope.launch { allApps.togglePin(it.packageName) } },
                        onUninstall = { allApps.uninstall(it.packageName) },
                        onAppInfo = { allApps.openAppInfo(it.packageName) },
                        onInstallApp = allApps::installApp,
                    )
                }
            }
        }
    }

    /** Pressing Home while the Home is already in front always returns to the Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        home.cancelEdit()
        screen = Screen.Home
    }

    private companion object {
        const val CLOCK_TICK_MILLIS = 1_000L
    }
}
