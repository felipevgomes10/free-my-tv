package com.freemytv.home.android

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.freemytv.home.HomeApplication
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.buttons.KeyDecision
import java.util.concurrent.Executor

/**
 * Sees remote keys before the system does (ADR 0005): keeps every Shortcut Button working
 * after Setup, applies the owner's reassignments, and turns long-press Home into the
 * Notification Panel. Every decision is ShortcutButtons'; this only relays. Its accessibility
 * access also lets the Home force stop apps through Settings ([ForceStopper]); it listens to
 * Settings alone, since once it has read a window Android would otherwise send it every change
 * on screen from every app, onto the thread that answers keys.
 *
 * Every key on the TV waits for this filter's answer, so it answers on a thread of its own:
 * on the Home's main thread a slow frame delayed the answer for a key's release past the
 * system's 400 ms repeat timeout, and one press of an arrow moved the focus twice.
 */
class KeyFilterService : AccessibilityService() {

    private val keys = HandlerThread("KeyFilter", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val keysExecutor = Handler(keys.looper).let { handler -> Executor { handler.post(it) } }

    private val home get() = application as HomeApplication

    private lateinit var forceStopper: ForceStopper
    private lateinit var switcher: AppSwitcherOverlayWindow

    // AccessibilityService delivers its callbacks on the "main" looper (Android 12) or executor (later).
    override fun getMainLooper(): Looper = keys.looper

    override fun getMainExecutor(): Executor = keysExecutor

    /** Keys pressed while Settings works under the cover, held back down and up so none lands in Settings. */
    private val heldBack = mutableSetOf<Int>()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::forceStopper.isInitialized && forceStopper.busy) {
            heldBack += event.keyCode
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && heldBack.remove(event.keyCode)) return true
        if (event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_DOWN && ::switcher.isInitialized) switcher.dismiss()
        val decision = home.shortcutButtons.onKey(
            scanCode = event.scanCode,
            keyCode = event.keyCode,
            down = event.action == KeyEvent.ACTION_DOWN,
            repeatCount = event.repeatCount,
            heldMillis = event.eventTime - event.downTime,
        )
        return when (decision) {
            KeyDecision.Pass -> false
            KeyDecision.PassAndOpenNotifications -> {
                if (::switcher.isInitialized) switcher.dismiss()
                home.platform.openHome(HomeDestination.NotificationPanel)
                false
            }
            is KeyDecision.Consume -> {
                if (decision.action != null && ::switcher.isInitialized) switcher.dismiss()
                decision.action?.let(home.shortcutButtons::perform)
                true
            }
        }
    }

    override fun onServiceConnected() {
        forceStopper = ForceStopper(this) { home.platform.awaitRemoteKeys() }
        switcher = AppSwitcherOverlayWindow(this) { forceStopper.busy }
        running = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() { if (::switcher.isInitialized) switcher.dismiss() }

    override fun onDestroy() {
        if (running === this) running = null
        if (::forceStopper.isInitialized) forceStopper.quit()
        if (::switcher.isInitialized) switcher.quit()
        super.onDestroy()
        keys.quitSafely()
    }

    companion object {
        @Volatile
        private var running: KeyFilterService? = null

        suspend fun showAppSwitcher(): com.freemytv.home.multitasking.OverlayResult =
            running?.switcher?.show() ?: com.freemytv.home.multitasking.OverlayResult.Unavailable

        fun dismissAppSwitcher() { running?.switcher?.dismiss() }

        fun isClosingApp(): Boolean = running?.forceStopper?.busy == true

        /** Force stops an app through Settings; false when the key filter isn't running to do it. */
        fun forceStop(packageName: String): Boolean {
            val service = running ?: return false
            // Abertos can separately close the underlying app. That is competing navigation,
            // not a close from this overlay: drop the overlay and use the normal return path.
            if (service.switcher.isUnderneath(packageName)) service.switcher.dismiss()
            val overlay = service.switcher.takeIf { it.isOpen && !it.isUnderneath(packageName) }
            service.forceStopper.forceStop(packageName, overlay?.let { { it.covered() } }, overlay?.let { { it.uncover() } })
            return true
        }
    }
}
