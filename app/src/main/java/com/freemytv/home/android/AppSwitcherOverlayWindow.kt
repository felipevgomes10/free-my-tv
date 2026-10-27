package com.freemytv.home.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.freemytv.home.HomeApplication
import com.freemytv.home.multitasking.OverlayResult
import com.freemytv.home.multitasking.SwitcherOverlay
import com.freemytv.home.ui.AppSwitcherScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** A focusable accessibility window: no Activity, screenshot, audio focus or second key transport. */
internal class AppSwitcherOverlayWindow(
    private val service: KeyFilterService,
    private val busy: () -> Boolean,
) {
    private val home = service.application as HomeApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows = service.getSystemService(WindowManager::class.java)
    private val model = SwitcherOverlay(home.platform, home.multitasking)
    private val opens = Mutex()
    private var root: FrameLayout? = null
    private var owner: WindowOwner? = null
    private var watch: Job? = null
    private var focusedPackage: String? = null
    private var stopped = false
    private var generation = 0
    @Volatile var isOpen = false
        private set

    private val sleep = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { hide() }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(sleep, filter, Context.RECEIVER_NOT_EXPORTED)
        else service.registerReceiver(sleep, filter)
    }

    suspend fun show(): OverlayResult = withTimeoutOrNull(4_000) {
        opens.withLock {
            withContext(Dispatchers.Main.immediate) {
                if (stopped) return@withContext OverlayResult.Unavailable
                if (home.screensaverActive.value) return@withContext OverlayResult.Unavailable
                if (busy()) return@withContext OverlayResult.Busy
                if (isOpen) return@withContext OverlayResult.Shown
                val opening = generation
                try {
                    val underneath = home.platform.foregroundPackage()
                    model.open(underneath)
                    // Service/sleep/navigation can change while the snapshot is being read.
                    if (stopped || opening != generation || home.screensaverActive.value) {
                        model.dismiss()
                        return@withContext OverlayResult.Unavailable
                    }
                    if (busy()) { model.dismiss(); return@withContext OverlayResult.Busy }
                    attach()
                    isOpen = true
                    awaitFrameAndFocus()
                    watch = scope.launch {
                        launch { home.screensaverActive.collect { if (it) hide() } }
                        while (isActive) {
                            delay(3_000)
                            if (busy()) continue
                            val current = home.platform.foregroundPackage()
                            if (current != null && underneath != null && current != underneath) { hide(); break }
                            model.refresh()
                        }
                    }
                    OverlayResult.Shown
                } catch (cancelled: CancellationException) {
                    hide()
                    throw cancelled
                } catch (_: Exception) {
                    hide()
                    OverlayResult.Unavailable
                }
            }
        }
    } ?: OverlayResult.Unavailable

    /** Dispatch cleanup before an Activity/Input launch, even when called from the key thread. */
    fun dismiss() { scope.launch { hide() } }

    fun isUnderneath(packageName: String) = model.state.value?.underneath == packageName

    private fun attach() {
        check(Looper.myLooper() == Looper.getMainLooper())
        val lifecycle = WindowOwner()
        owner = lifecycle
        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    // Remove on release so the app beneath never receives the second half of Back.
                    if (event.action == KeyEvent.ACTION_UP) hide()
                    return true
                }
                val handled = super.dispatchKeyEvent(event)
                return handled || event.keyCode in navigationKeys
            }
        }
        frame.isFocusableInTouchMode = true
        frame.setViewTreeLifecycleOwner(lifecycle)
        frame.setViewTreeViewModelStoreOwner(lifecycle)
        frame.setViewTreeSavedStateRegistryOwner(lifecycle)
        frame.addView(ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by model.state.collectAsState()
                state?.let { current ->
                    AppSwitcherScreen(
                        state = current.switcher.copy(initialFocus = current.switcher.apps.orEmpty().indexOfFirst { it.packageName == focusedPackage }
                            .takeIf { it >= 0 } ?: current.switcher.initialFocus),
                        artwork = home.artwork, now = home.platform.now().toInstant(),
                        overlay = true, underneath = current.underneath,
                        onSwitch = { app -> scope.launch { model.select(app.packageName); if (model.state.value == null) hide() } },
                        onClose = { app -> scope.launch { model.close(app.packageName) } },
                        onFocused = { focusedPackage = it },
                    )
                }
            }
        }, FrameLayout.LayoutParams(-1, -1))
        root = frame
        windows.addView(frame, WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.FILL; title = "FMTV App Switcher"; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN })
        lifecycle.resume()
        frame.requestFocus()
    }

    private suspend fun awaitFrameAndFocus() = suspendCancellableCoroutine<Unit> { waiting ->
        val view = root
        if (view == null) { waiting.cancel(); return@suspendCancellableCoroutine }
        var drawn = false
        fun complete() { if (drawn && view.hasWindowFocus() && waiting.isActive) waiting.resume(Unit) }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { complete() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
                if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                    view.viewTreeObserver.registerFrameCommitCallback { drawn = true; complete() }
                } else { view.post { drawn = true; complete() } }
            }
        })
        waiting.invokeOnCancellation { view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus) } }
        view.invalidate()
    }

    /** Called only from ForceStopper's worker, once its cover is committed, never from the key thread. */
    fun covered() = runBlocking { withContext(Dispatchers.Main.immediate) { detach() } }

    /** Restore the live overlay before ForceStopper uncovers the original app. */
    fun uncover() = runBlocking {
        withTimeoutOrNull(2_000) {
            withContext(Dispatchers.Main.immediate) {
                if (isOpen && !stopped) {
                    try { attach(); awaitFrameAndFocus() } catch (_: Exception) { hide() }
                }
            }
        } ?: withContext(Dispatchers.Main.immediate) { hide() }
    }

    private fun detach() {
        root?.let { runCatching { windows.removeViewImmediate(it) } }
        root = null
        owner?.destroy()
        owner = null
    }

    private fun hide() {
        generation++
        isOpen = false
        watch?.cancel()
        watch = null
        detach()
        model.dismiss()
        focusedPackage = null
    }

    fun quit() {
        scope.launch {
            stopped = true
            hide()
            service.unregisterReceiver(sleep)
            scope.cancel()
        }
    }

    private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle get() = registry
        override val savedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init { saved.performAttach(); saved.performRestore(null); registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) }
        fun resume() { registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun destroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); viewModelStore.clear() }
    }

    private companion object {
        val navigationKeys = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
    }
}
