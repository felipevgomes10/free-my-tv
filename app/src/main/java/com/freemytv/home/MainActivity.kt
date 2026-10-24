package com.freemytv.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.freemytv.home.android.TmdbImages
import com.freemytv.home.apkdrop.ApkDrop
import com.freemytv.home.apps.AllAppsStateHolder
import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.explorar.Explorar
import com.freemytv.home.home.AppTrayItem
import com.freemytv.home.home.HomeStateHolder
import com.freemytv.home.home.TileArt
import com.freemytv.home.inputs.InputTray
import com.freemytv.home.monitor.SystemMonitor
import com.freemytv.home.multitasking.HomePresses
import com.freemytv.home.multitasking.Multitasking
import com.freemytv.home.notifications.NotificationCenter
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.settings.SettingsStateHolder
import com.freemytv.home.topshelf.ShelfApp
import com.freemytv.home.topshelf.TopShelf
import com.freemytv.home.ui.AllAppsScreen
import com.freemytv.home.ui.ApkDropScreen
import com.freemytv.home.ui.AppSwitcherScreen
import com.freemytv.home.ui.ButtonsPanelState
import com.freemytv.home.ui.ExplorarActions
import com.freemytv.home.ui.ExplorarScreen
import com.freemytv.home.ui.HomeActions
import com.freemytv.home.ui.HomeScreen
import com.freemytv.home.ui.HomeSection
import com.freemytv.home.ui.InputActions
import com.freemytv.home.ui.LocalScreenSettled
import com.freemytv.home.ui.MonitorPill
import com.freemytv.home.ui.MonitorScreen
import com.freemytv.home.ui.NotificationBanner
import com.freemytv.home.ui.NotificationPanel
import com.freemytv.home.ui.NotificationsBell
import com.freemytv.home.ui.SettingsActions
import com.freemytv.home.ui.SettingsScreen
import com.freemytv.home.ui.StatusIconButton
import com.freemytv.home.ui.TitleDetailsScreen
import com.freemytv.home.ui.TrailerScreen
import com.freemytv.home.ui.UpdatesScreen
import com.freemytv.home.ui.UpdatesStatusButton
import com.freemytv.home.ui.WeatherPill
import com.freemytv.home.ui.WeatherScreen
import com.freemytv.home.updates.UpdatesStateHolder
import com.freemytv.home.weather.WeatherWidget
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation

class MainActivity : ComponentActivity() {

    private enum class Screen { Home, Explorar, TitleDetails, Trailer, AllApps, ApkDrop, Weather, Monitor, Settings, Updates, Switcher }

    private var screen by mutableStateOf(Screen.Home)
    private var notificationsOpen by mutableStateOf(false)
    private var explorarReturnsToCard = false
    private lateinit var explorar: Explorar
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
        val topShelf = TopShelf(app.upNext.state)
        val apkDrop = ApkDrop(platform)
        explorar = Explorar(platform)
        val tmdbImages = TmdbImages(applicationContext)

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
                    screen == Screen.ApkDrop -> screen = Screen.AllApps
                    screen == Screen.TitleDetails -> closeDetails()
                    screen == Screen.Trailer -> screen = Screen.TitleDetails
                    else -> screen = Screen.Home
                }
            }
        })

        lifecycleScope.launch { library.followPackageEvents() }
        lifecycleScope.launch { inputs.followInputChanges() }
        lifecycleScope.launch { notifications.follow() }
        lifecycleScope.launch {
            updates.load()
            // Update Sources are checked about once a day while the Home runs, and whenever Updates opens.
            updates.keepChecking()
        }
        lifecycleScope.launch {
            multitasking.load()
            // The Open App Limit holds while the Home is alive, whichever app is in front.
            multitasking.keepLimit()
        }
        lifecycleScope.launch {
            widgetSettings.load()
            // Widgets only work while the Home is visible; a TV app in front never pays for them.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.screensaverActive.collectLatest { dreaming ->
                    if (!dreaming) coroutineScope {
                        launch { topShelf.run() }
                        launch { weather.follow() }
                        launch { monitor.follow() }
                        awaitCancellation()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.shortcutButtons.ensureKeyFilter()
                app.setupWatchdog.check()
                home.refresh()
                app.upNext.prune()
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
            val upNextState by app.upNext.state.collectAsStateWithLifecycle()
            val topShelfState by topShelf.state.collectAsStateWithLifecycle()
            val inputState by inputs.state.collectAsStateWithLifecycle()
            val weatherState by weather.state.collectAsStateWithLifecycle()
            val widgetPreferences by widgetSettings.preferences.collectAsStateWithLifecycle()
            val screensaverActive by app.screensaverActive.collectAsStateWithLifecycle()
            val monitorState by monitor.state.collectAsStateWithLifecycle()
            val updatesState by updates.state.collectAsStateWithLifecycle()
            val notificationsState by notifications.state.collectAsStateWithLifecycle()
            val switcherState by multitasking.switcher.collectAsStateWithLifecycle()
            val openAppLimit by multitasking.limit.collectAsStateWithLifecycle()
            val statusWidgets: @Composable () -> Unit = {
                NotificationsBell(notificationsState) { notificationsOpen = true }
                WeatherPill(weatherState) { screen = Screen.Weather }
                MonitorPill(monitorState) { screen = Screen.Monitor }
                UpdatesStatusButton(updatesState.needsAttention, getString(R.string.updates)) { screen = Screen.Updates }
                StatusIconButton(R.drawable.ic_settings, getString(R.string.settings)) { screen = Screen.Settings }
            }
            Box {
            val screens = updateTransition(screen, label = "screen")
            screens.AnimatedContent(transitionSpec = { screenTransition(initialState, targetState) }) { shown ->
                // An arriving screen settles once the move ends; one uncovered by Back is already in place.
                val settled = screens.currentState == shown || screens.currentState.depth > shown.depth
                CompositionLocalProvider(LocalScreenSettled provides settled) {
                    when (shown) {
                        Screen.Home -> HomeScreen(
                            state = homeState,
                            inputs = inputState,
                            upNext = upNextState,
                            topShelf = topShelfState,
                            onUpNext = app.upNext::select,
                            onRemoveUpNext = { scope.launch { app.upNext.remove(it) } },
                            inputTrayFocusRequest = inputTrayFocusRequest,
                            onSection = ::openSection,
                            artwork = artwork,
                            actions = HomeActions(
                                onSelect = { item -> if (item == AppTrayItem.AllApps) screen = Screen.AllApps else home.select(item) },
                                onFocus = { item -> topShelf.focus((item as? AppTrayItem.App)?.let { ShelfApp(it.packageName, it.label) }) },
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
                                onSendApk = { screen = Screen.ApkDrop },
                                statusWidgets = statusWidgets,
                            )
                        }
                        Screen.Explorar -> {
                            val explorarState by explorar.state.collectAsStateWithLifecycle()
                            // Highlights load while Explorar is on screen; the hour's cache outlives it.
                            LaunchedEffect(Unit) { explorar.run() }
                            ExplorarScreen(
                                state = explorarState,
                                clock = homeState.statusClock,
                                images = tmdbImages,
                                actions = ExplorarActions(
                                    onWindow = explorar::choose,
                                    onFilter = explorar::choose,
                                    onFocus = explorar::focus,
                                    onDetails = { title ->
                                        screen = Screen.TitleDetails
                                        scope.launch { explorar.openDetails(title) }
                                    },
                                ),
                                onSection = ::openSection,
                                statusWidgets = statusWidgets,
                                returnToCard = explorarReturnsToCard.also { explorarReturnsToCard = false },
                            )
                        }
                        Screen.TitleDetails -> {
                            val explorarState by explorar.state.collectAsStateWithLifecycle()
                            explorarState.details?.let { details ->
                                TitleDetailsScreen(
                                    state = details,
                                    clock = homeState.statusClock,
                                    images = tmdbImages,
                                    onBack = ::closeDetails,
                                    onWatch = explorar::watch,
                                    onTrailer = { screen = Screen.Trailer },
                                    statusWidgets = statusWidgets,
                                )
                            }
                        }
                        Screen.Trailer -> {
                            val explorarState by explorar.state.collectAsStateWithLifecycle()
                            val details = explorarState.details
                            val video = details?.trailer
                            if (details != null && video != null) {
                                TrailerScreen(
                                    title = details.details?.name ?: details.summary.name,
                                    video = video,
                                    onBack = { screen = Screen.TitleDetails },
                                    onFailed = {
                                        screen = Screen.TitleDetails
                                        explorar.trailerFailed(video)
                                    },
                                )
                            }
                        }
                        Screen.ApkDrop -> {
                            val apkDropState by apkDrop.state.collectAsStateWithLifecycle()
                            // The page is served only while this screen is on.
                            LaunchedEffect(Unit) { apkDrop.open() }
                            ApkDropScreen(
                                state = apkDropState,
                                clock = homeState.statusClock,
                                onInstall = { scope.launch { apkDrop.install() } },
                                onCancel = { scope.launch { apkDrop.cancel() } },
                                statusWidgets = statusWidgets,
                            )
                        }
                        Screen.Weather -> WeatherScreen(
                            report = weatherState.report,
                            loading = weatherState.loading,
                            clock = homeState.statusClock,
                            soundMuted = widgetPreferences.weatherSoundMuted,
                            onToggleSound = { scope.launch { widgetSettings.setWeatherSoundMuted(!widgetPreferences.weatherSoundMuted) } },
                            active = screen == Screen.Weather && !screensaverActive,
                            statusWidgets = statusWidgets,
                        )
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
                                    onScreensaverEnabled = { scope.launch { settings.setScreensaverEnabled(it) } },
                                    onScreensaverMinutes = { scope.launch { settings.setScreensaverMinutes(it) } },
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
                        Screen.Updates -> {
                            LaunchedEffect(Unit) { updates.checkSources() }
                            UpdatesScreen(
                                state = updatesState,
                                clock = homeState.statusClock,
                                statusWidgets = statusWidgets,
                                onCheckSystemUpdate = updates::checkSystemUpdate,
                                onOpenPlayStoreApps = updates::openPlayStoreApps,
                                onSetSource = { pkg, input -> scope.launch { updates.setSource(pkg, input) } },
                                onUpdate = { pkg -> scope.launch { updates.update(pkg) } },
                            )
                        }
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
                }
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

    /**
     * How one screen gives way to another. Going deeper (a Widget's detail, Settings, a Title, the
     * trailer) the new screen grows into place; coming back, the deeper one shrinks away over the
     * screen beneath; between the Home's sections they cross-fade. The screen left stays solid
     * underneath while the new one fades in, so the Status Bar they share never dims.
     */
    private fun screenTransition(from: Screen, to: Screen): ContentTransform {
        val grow = scaleIn(tween(ENTER_MILLIS, easing = EaseOutCubic), initialScale = DEPTH_SCALE)
        val shrink = scaleOut(tween(EXIT_MILLIS, easing = EaseInCubic), targetScale = DEPTH_SCALE)
        val noResize = SizeTransform(clip = false)
        return when {
            to.depth > from.depth -> ContentTransform(fadeIn(tween(ENTER_MILLIS)) + grow, holdUntilEntered(), sizeTransform = noResize)
            to.depth < from.depth -> ContentTransform(EnterTransition.None, fadeOut(tween(EXIT_MILLIS)) + shrink, targetContentZIndex = -1f, sizeTransform = noResize)
            else -> ContentTransform(fadeIn(tween(ENTER_MILLIS)), holdUntilEntered(), sizeTransform = noResize)
        }
    }

    /** The screen left stays as it is until the new one is fully in, then goes. */
    private fun holdUntilEntered() = fadeOut(tween(durationMillis = 1, delayMillis = ENTER_MILLIS))

    /** How far a screen sits from the Home's sections. */
    private val Screen.depth: Int
        get() = when (this) {
            Screen.Home, Screen.Explorar, Screen.AllApps -> 0
            Screen.Trailer -> 2
            else -> 1
        }

    /** Pressing Home while the Home is already in front always returns to the Home screen. */
    private fun closeDetails() {
        explorar.closeDetails()
        explorarReturnsToCard = true
        screen = Screen.Explorar
    }

    private fun openSection(section: HomeSection) {
        screen = when (section) {
            HomeSection.Inicio -> Screen.Home
            HomeSection.Explorar -> Screen.Explorar
            HomeSection.TodosOsApps -> Screen.AllApps
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Back from closing an app through Settings: the owner stays where they were.
        if (intent.getBooleanExtra(EXTRA_RETURN, false)) return
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

    /** Where a Shortcut Button, the settings button, long-press Home or a double Home press sends the owner. */
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
            HomeDestination.Settings -> {
                multitasking.closeSwitcher()
                screen = Screen.Settings
            }
        }
    }

    companion object {
        /** A [HomeDestination] name; the Home opens there. */
        const val EXTRA_DESTINATION = "com.freemytv.home.DESTINATION"

        /** Brings the Home back in front exactly as it was left. */
        const val EXTRA_RETURN = "com.freemytv.home.RETURN"

        private const val ENTER_MILLIS = 260
        private const val EXIT_MILLIS = 200
        private const val DEPTH_SCALE = 0.96f

        private const val CLOCK_TICK_MILLIS = 1_000L
    }
}
