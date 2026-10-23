package com.freemytv.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.home.AppTrayItem
import com.freemytv.home.home.TileArt
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.ui.ButtonsPanelState
import com.freemytv.home.home.HomeStateHolder
import com.freemytv.home.inputs.InputTray
import com.freemytv.home.ui.AllAppsScreen
import com.freemytv.home.ui.HomeActions
import com.freemytv.home.monitor.SystemMonitor
import com.freemytv.home.settings.SettingsStateHolder
import com.freemytv.home.multitasking.HomePresses
import com.freemytv.home.multitasking.Multitasking
import com.freemytv.home.notifications.NotificationCenter
import com.freemytv.home.ui.AppSwitcherScreen
import com.freemytv.home.ui.InputActions
import com.freemytv.home.ui.NotificationBanner
import com.freemytv.home.ui.NotificationPanel
import com.freemytv.home.ui.NotificationsBell
import com.freemytv.home.ui.SettingsActions
import com.freemytv.home.ui.SettingsScreen
import com.freemytv.home.ui.StatusIconButton
import com.freemytv.home.ui.UpdatesScreen
import com.freemytv.home.ui.UpdatesStatusButton
import com.freemytv.home.updates.UpdatesStateHolder
import com.freemytv.home.ui.MonitorPill
import com.freemytv.home.ui.MonitorScreen
import com.freemytv.home.ui.WeatherPill
import com.freemytv.home.ui.WeatherScreen
import com.freemytv.home.weather.WeatherWidget
import com.freemytv.home.widgets.WidgetSettings
import com.freemytv.home.ui.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private enum class Screen { Home, AllApps, Weather, Monitor, Settings, Updates, Switcher }

    private var screen by mutableStateOf(Screen.Home)
    private var notificationsOpen by mutableStateOf(false)
    private var inputTrayFocusRequest by mutableIntStateOf(0)
    private val homePresses = HomePresses()
    private lateinit var multitasking: Multitasking
    private lateinit var platform: AndroidTvPlatform
    private lateinit var home: HomeStateHolder
    private lateinit var inputs: InputTray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as HomeApplication
        platform = app.platform
        val library = AppLibrary(platform)
        home = HomeStateHolder(platform, library, lifecycleScope)
        val allApps = AllAppsStateHolder(platform, library, lifecycleScope)
        inputs = InputTray(platform, lifecycleScope)
        val widgetSettings = WidgetSettings(platform)
        val weather = WeatherWidget(platform, widgetSettings)
        val monitor = SystemMonitor(platform, widgetSettings)
        val settings = SettingsStateHolder(platform, widgetSettings, lifecycleScope)
        val updates = UpdatesStateHolder(platform)
        val notifications = NotificationCenter(platform)
        multitasking = Multitasking(platform)
        val artwork = AppArtworkLoader(applicationContext)

        // Back leaves Edit Mode, then All Apps; on the Home itself it has nowhere to go.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    notificationsOpen -> notificationsOpen = false
                    screen == Screen.Switcher -> {
                        multitasking.closeSwitcher()
                        screen = Screen.Home
                    }
                    home.state.value.lifted != null -> home.cancelEdit()
                    inputs.state.value.lifted != null -> inputs.cancelEdit()
                    else -> screen = Screen.Home
                }
            }
        })

        lifecycleScope.launch { library.followPackageEvents() }
        lifecycleScope.launch { inputs.followInputChanges() }
        lifecycleScope.launch { notifications.follow() }
        lifecycleScope.launch {
            multitasking.load()
            // The Open App Limit holds while the Home is alive, whichever app is in front.
            multitasking.keepLimit()
        }
        lifecycleScope.launch {
            widgetSettings.load()
            // Widgets only work while the Home is visible; a TV app in front never pays for them.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { weather.follow() }
                launch { monitor.follow() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.shortcutButtons.ensureKeyFilter()
                app.setupWatchdog.check()
                home.refresh()
                inputs.load()
                settings.load()
                updates.load()
                while (true) {
                    home.tick()
                    delay(CLOCK_TICK_MILLIS)
                }
            }
        }

        intent?.getStringExtra(EXTRA_DESTINATION)?.let { name -> runCatching { HomeDestination.valueOf(name) }.getOrNull()?.let(::open) }

        setContent {
            val scope = rememberCoroutineScope()
            val homeState by home.state.collectAsStateWithLifecycle()
            val inputState by inputs.state.collectAsStateWithLifecycle()
            val weatherState by weather.state.collectAsStateWithLifecycle()
            val monitorState by monitor.state.collectAsStateWithLifecycle()
            val updatesState by updates.state.collectAsStateWithLifecycle()
            val notificationsState by notifications.state.collectAsStateWithLifecycle()
            val switcherState by multitasking.switcher.collectAsStateWithLifecycle()
            val openAppLimit by multitasking.limit.collectAsStateWithLifecycle()
            val statusWidgets: @Composable () -> Unit = {
                NotificationsBell(notificationsState) { notificationsOpen = true }
                WeatherPill(weatherState) { screen = Screen.Weather }
                MonitorPill(monitorState) { screen = Screen.Monitor }
                UpdatesStatusButton(updatesState.patchOutdated, getString(R.string.updates)) { screen = Screen.Updates }
                StatusIconButton(R.drawable.ic_settings, getString(R.string.settings)) { screen = Screen.Settings }
            }
            Box {
            when (screen) {
                Screen.Home -> HomeScreen(
                    state = homeState,
                    inputs = inputState,
                    inputTrayFocusRequest = inputTrayFocusRequest,
                    artwork = artwork,
                    actions = HomeActions(
                        onSelect = { item -> if (item == AppTrayItem.AllApps) screen = Screen.AllApps else home.select(item) },
                        onStartEdit = home::startEdit,
                        onMove = home::move,
                        onDrop = { scope.launch { home.drop() } },
                        onUnpin = { scope.launch { home.unpinLifted() } },
                        onUninstall = home::uninstallLifted,
                    ),
                    inputActions = InputActions(
                        onSelect = inputs::select,
                        onStartEdit = inputs::startEdit,
                        onMove = inputs::move,
                        onDrop = { scope.launch { inputs.drop() } },
                        onRename = { name -> scope.launch { inputs.renameLifted(name) } },
                        onHide = { scope.launch { inputs.hideLifted() } },
                        onShowHidden = { scope.launch { inputs.showHidden() } },
                        onCancel = inputs::cancelEdit,
                    ),
                    statusWidgets = statusWidgets,
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
                        statusWidgets = statusWidgets,
                    )
                }
                Screen.Weather -> WeatherScreen(weatherState.report, homeState.statusClock, statusWidgets)
                Screen.Settings -> {
                    val settingsState by settings.state.collectAsStateWithLifecycle()
                    val assignments by app.shortcutButtons.assignments.collectAsStateWithLifecycle()
                    val setupState by app.setupWatchdog.state.collectAsStateWithLifecycle()
                    val allAppsState by allApps.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = settingsState,
                        openAppLimit = openAppLimit,
                        buttons = ButtonsPanelState(
                            assignments = assignments,
                            targetOf = app.shortcutButtons::targetOf,
                            apps = allAppsState.apps.map { InstalledApp(it.packageName, it.label, it.art == TileArt.Banner) },
                            inputs = inputState.inputs,
                        ),
                        setup = setupState,
                        clock = homeState.statusClock,
                        statusWidgets = statusWidgets,
                        actions = SettingsActions(
                            onWeatherVisible = { scope.launch { settings.setWeatherVisible(it) } },
                            onMonitorVisible = { scope.launch { settings.setMonitorVisible(it) } },
                            onWeatherCity = { scope.launch { settings.setWeatherCity(it) } },
                            onOpenAndroidSettings = settings::openAndroidSettings,
                            onOpenAppLimit = { scope.launch { multitasking.setLimit(it) } },
                            onAssignButton = { button, target ->
                                scope.launch {
                                    if (target == null) app.shortcutButtons.restoreFactory(button) else app.shortcutButtons.assign(button, target)
                                }
                            },
                        ),
                    )
                }
                Screen.Switcher -> AppSwitcherScreen(
                    state = switcherState,
                    artwork = artwork,
                    now = platform.now().toInstant(),
                    onSwitch = { app ->
                        screen = Screen.Home
                        multitasking.switchTo(app)
                    },
                    onClose = multitasking::close,
                )
                Screen.Updates -> UpdatesScreen(
                    state = updatesState,
                    clock = homeState.statusClock,
                    statusWidgets = statusWidgets,
                    onCheckSystemUpdate = updates::checkSystemUpdate,
                    onOpenPlayStoreApps = updates::openPlayStoreApps,
                )
                Screen.Monitor -> MonitorScreen(
                    state = monitorState,
                    clock = homeState.statusClock,
                    statusWidgets = statusWidgets,
                    onOpen = monitor::openDetail,
                    onClose = monitor::closeDetail,
                    onUninstall = { library.requestUninstall(it.packageName) },
                    onCloseApp = { scope.launch { monitor.close(it) } },
                )
            }
            val now = platform.now().toInstant()
            if (notificationsOpen) {
                NotificationPanel(
                    state = notificationsState,
                    artwork = artwork,
                    now = now,
                    onOpen = { notificationsOpen = false; notifications.open(it) },
                    onClearAll = notifications::clearAll,
                )
            } else {
                notificationsState.banner?.let { NotificationBanner(it, artwork, now) }
            }
            }
        }
    }

    /** Pressing Home while the Home is already in front always returns to the Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        home.cancelEdit()
        inputs.cancelEdit()
        notificationsOpen = false
        val destination = intent.getStringExtra(EXTRA_DESTINATION)?.let { runCatching { HomeDestination.valueOf(it) }.getOrNull() }
        when {
            destination != null -> open(destination)
            intent.hasCategory(Intent.CATEGORY_HOME) && homePresses.press(platform.now().toInstant()) -> open(HomeDestination.AppSwitcher)
            else -> {
                multitasking.closeSwitcher()
                screen = Screen.Home
            }
        }
    }

    /** Where a Shortcut Button, long-press Home or a double Home press sends the owner. */
    private fun open(destination: HomeDestination) {
        when (destination) {
            HomeDestination.NotificationPanel -> notificationsOpen = true
            HomeDestination.InputTray -> {
                multitasking.closeSwitcher()
                screen = Screen.Home
                inputTrayFocusRequest++
            }
            HomeDestination.AppSwitcher -> lifecycleScope.launch {
                multitasking.openSwitcher()
                screen = Screen.Switcher
            }
        }
    }

    companion object {
        /** A [HomeDestination] name; the Home opens there. */
        const val EXTRA_DESTINATION = "com.freemytv.home.DESTINATION"

        private const val CLOCK_TICK_MILLIS = 1_000L
    }
}
