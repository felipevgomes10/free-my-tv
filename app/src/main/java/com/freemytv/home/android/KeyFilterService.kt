package com.freemytv.home.android

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.freemytv.home.HomeApplication
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.buttons.KeyDecision

/**
 * Sees remote keys before the system does (ADR 0005): keeps every Shortcut Button working
 * after Setup, applies the owner's reassignments, and turns long-press Home into the
 * Notification Panel. Every decision is ShortcutButtons'; this only relays.
 */
class KeyFilterService : AccessibilityService() {

    private val home get() = application as HomeApplication

    override fun onKeyEvent(event: KeyEvent): Boolean {
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
                home.platform.openHome(HomeDestination.NotificationPanel)
                false
            }
            is KeyDecision.Consume -> {
                decision.action?.let(home.shortcutButtons::perform)
                true
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
