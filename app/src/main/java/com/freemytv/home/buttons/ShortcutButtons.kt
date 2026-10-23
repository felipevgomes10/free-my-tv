package com.freemytv.home.buttons

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The remote's Shortcut Buttons, recognised by the scan codes measured on the owner's
 * TCL remote (getevent, 2026-10-23). Their key codes are TCL-specific, so scan codes it is.
 */
enum class ShortcutButton(val scanCode: Int, val factoryApp: String?, val keyCode: Int? = null) {
    Netflix(472, "com.netflix.ninja"),
    Globoplay(586, "com.globo.globotv"),
    PrimeVideo(473, "com.amazon.amazonvideo.livingroom"),
    DisneyPlus(762, "com.disney.disneyplus"),
    YouTube(569, "com.google.android.youtube.tv"),

    /** Input also has a standard Android key code, KEYCODE_TV_INPUT. */
    Input(471, null, keyCode = 178),
}

/** What a Shortcut Button does. */
sealed interface ButtonTarget {
    data class App(val packageName: String) : ButtonTarget
    data class TvInput(val inputId: String) : ButtonTarget
    data object AppSwitcher : ButtonTarget
    data object InputTray : ButtonTarget
}

/** Where the Home should open when a button asks for it. */
enum class HomeDestination { InputTray, AppSwitcher, NotificationPanel }

/** What the key filter does with a key event. */
sealed interface KeyDecision {
    /** Let the system and apps handle it as usual. */
    data object Pass : KeyDecision

    /** Swallow it; when [action] is set, act on it. */
    data class Consume(val action: ButtonTarget?) : KeyDecision

    /** Let it through and also open the Notification Panel (long-press Home). */
    data object PassAndOpenNotifications : KeyDecision
}

class ShortcutButtons(private val platform: TvPlatform) {

    private val _assignments = MutableStateFlow<Map<ShortcutButton, ButtonTarget>>(emptyMap())

    /** The owner's changes from the factory behaviour; buttons not in the map do what they always did. */
    val assignments: StateFlow<Map<ShortcutButton, ButtonTarget>> = _assignments.asStateFlow()

    suspend fun load() {
        _assignments.value = platform.loadButtonAssignments()
    }

    suspend fun assign(button: ShortcutButton, target: ButtonTarget) = change { it + (button to target) }

    suspend fun restoreFactory(button: ShortcutButton) = change { it - button }

    fun targetOf(button: ShortcutButton): ButtonTarget =
        _assignments.value[button] ?: button.factoryApp?.let { ButtonTarget.App(it) } ?: ButtonTarget.InputTray

    private var homeHoldHandled = false

    /**
     * Decides what happens to a key. Shortcut Buttons are swallowed on both press and release
     * and act on the press; a target that can't work (an app not installed) lets the key through
     * untouched. Home is never swallowed: holding it also opens the Notification Panel, once per
     * hold, noticed either by the key repeating or, for remotes that don't repeat (the owner's
     * doesn't), by being released after [LONG_PRESS_MILLIS].
     */
    fun onKey(scanCode: Int, keyCode: Int, down: Boolean, repeatCount: Int, heldMillis: Long = 0): KeyDecision {
        if (keyCode == KEYCODE_HOME) {
            val held = if (down) repeatCount == 1 else heldMillis >= LONG_PRESS_MILLIS
            if (down && repeatCount == 0) homeHoldHandled = false
            if (!held || homeHoldHandled) return KeyDecision.Pass
            homeHoldHandled = true
            return KeyDecision.PassAndOpenNotifications
        }
        val button = ShortcutButton.entries.firstOrNull { it.scanCode == scanCode || (it.keyCode != null && it.keyCode == keyCode) } ?: return KeyDecision.Pass
        val target = targetOf(button)
        if (target is ButtonTarget.App && !platform.isLaunchable(target.packageName)) return KeyDecision.Pass
        return KeyDecision.Consume(target.takeIf { down && repeatCount == 0 })
    }

    /** Carries out a button's target. */
    fun perform(target: ButtonTarget) = when (target) {
        is ButtonTarget.App -> platform.launch(target.packageName)
        is ButtonTarget.TvInput -> platform.switchToInputById(target.inputId)
        ButtonTarget.AppSwitcher -> platform.openHome(HomeDestination.AppSwitcher)
        ButtonTarget.InputTray -> platform.openHome(HomeDestination.InputTray)
    }

    /** Turns the key filter back on if the system turned it off; false if the Home lacks the permission Setup grants. */
    fun ensureKeyFilter(): Boolean = platform.isKeyFilterEnabled() || platform.enableKeyFilter()

    private suspend fun change(edit: (Map<ShortcutButton, ButtonTarget>) -> Map<ShortcutButton, ButtonTarget>) {
        val changed = edit(_assignments.value)
        platform.saveButtonAssignments(changed)
        _assignments.value = changed
    }

    private companion object {
        const val KEYCODE_HOME = 3
        const val LONG_PRESS_MILLIS = 500L
    }
}
