package com.freemytv.home.android

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.freemytv.home.HomeApplication
import com.freemytv.home.home.StatusClock
import com.freemytv.home.screensaver.ScreensaverSession
import com.freemytv.home.screensaver.ScreensaverPhase
import com.freemytv.home.ui.WeatherScreen
import com.freemytv.home.weather.WeatherWidget
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A real Android screensaver: never launches Home over another app or polls remote keys. */
class WeatherDreamService : DreamService() {
    private val platform get() = (application as HomeApplication).platform
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null
    private var owner: DreamOwner? = null
    private lateinit var widgets: WidgetSettings
    private lateinit var weather: WeatherWidget
    private var clock by mutableStateOf(StatusClock("", ""))
    private var preparing by mutableStateOf(true)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        current = WeakReference(this)
        isInteractive = false // Android dismisses on the first key/touch and returns to the app beneath.
        isFullscreen = true
        isScreenBright = true
        widgets = WidgetSettings(platform)
        weather = WeatherWidget(platform, widgets)
        val lifecycle = DreamOwner().also { owner = it }
        window.decorView.setViewTreeLifecycleOwner(lifecycle)
        window.decorView.setViewTreeSavedStateRegistryOwner(lifecycle)
        setContentView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by weather.state.collectAsStateWithLifecycle()
                val preferences by widgets.preferences.collectAsStateWithLifecycle()
                WeatherScreen(
                    report = state.report, loading = preparing || state.loading, clock = clock,
                    soundMuted = preferences.weatherSoundMuted, onToggleSound = {}, active = true,
                    statusWidgets = {}, showSoundControl = false,
                )
            }
        })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        (application as HomeApplication).screensaverActive.value = true
        owner?.resume()
        preparing = true
        running = scope.launch {
            val session = ScreensaverSession(platform)
            launch { session.run() }
            session.state.collectLatest { phase ->
                when (phase) {
                    ScreensaverPhase.Checking -> Unit
                    ScreensaverPhase.Finished -> finish()
                    ScreensaverPhase.Showing -> coroutineScope {
                        widgets.load()
                        launch { weather.follow(forScreensaver = true) }
                        preparing = false
                        while (true) {
                            val now = platform.now()
                            clock = StatusClock(now.format(TIME), now.format(DATE).replace("-feira", ""))
                            delay(1_000)
                        }
                    }
                }
            }
        }
    }

    override fun onDreamingStopped() {
        running?.cancel()
        running = null
        owner?.pause()
        if (current?.get() === this) (application as HomeApplication).screensaverActive.value = false
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        running?.cancel()
        owner?.destroy()
        owner = null
        if (current?.get() === this) {
            (application as HomeApplication).screensaverActive.value = false
            current = null
        }
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        scope.cancel()
        owner?.destroy()
        if (current?.get() === this) {
            (application as HomeApplication).screensaverActive.value = false
            current = null
        }
        super.onDestroy()
    }

    private class DreamOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun pause() {
            if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        fun destroy() {
            pause()
            if (registry.currentState != Lifecycle.State.DESTROYED) registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    companion object {
        private var current: WeakReference<WeatherDreamService>? = null
        fun finishCurrent() { current?.get()?.finish() }
        private val PT_BR = Locale.forLanguageTag("pt-BR")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", PT_BR)
        private val DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)
    }
}
