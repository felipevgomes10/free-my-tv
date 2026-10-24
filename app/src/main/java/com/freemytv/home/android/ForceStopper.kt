package com.freemytv.home.android

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Force stops apps by pressing Settings' own buttons (ADR 0010). Android keeps force stop to
 * itself, so the key filter, which already holds accessibility access, opens the app's page in
 * Settings, presses Force stop, confirms with OK and leaves Settings again. One app at a time,
 * on a thread of its own so remote keys never wait for it.
 */
internal class ForceStopper(private val service: AccessibilityService) {

    private val thread = HandlerThread("ForceStop").apply { start() }
    private val handler = Handler(thread.looper)

    fun forceStop(packageName: String) {
        handler.post { run(packageName) }
    }

    fun quit() {
        thread.quitSafely()
    }

    private fun run(packageName: String) {
        // Settings' own words for the button, in the TV's language ("Forçar parada").
        val forceStop = runCatching {
            val resources = service.packageManager.getResourcesForApplication(SETTINGS)
            resources.getString(resources.getIdentifier("device_apps_app_management_force_stop", "string", SETTINGS))
        }.getOrNull() ?: return
        service.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            ),
        )
        // The app's page: the Force stop row, disabled when the app is already stopped.
        val row = waitFor { root -> root.withText(forceStop).firstOrNull { it.viewIdResourceName == "android:id/title" } }
        if (row != null && row.clickableAncestor()?.takeIf { it.isEnabled }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            // The confirmation: Force stop as its title, OK first among its actions.
            val ok = waitFor { root ->
                if (root.byId(GUIDANCE_TITLE).none { it.text?.toString() == forceStop }) return@waitFor null
                val actions = root.byId(ACTION_TITLE)
                actions.firstOrNull { it.text?.toString().equals(Resources.getSystem().getString(android.R.string.ok), ignoreCase = true) } ?: actions.firstOrNull()
            }
            ok?.clickableAncestor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor { root -> root.takeIf { it.byId(GUIDANCE_TITLE).isEmpty() } }
        }
        leaveSettings()
    }

    /** Back out of Settings to whatever the owner was on, never pressing Back anywhere else. */
    private fun leaveSettings() {
        repeat(MAX_BACKS) {
            if (service.rootInActiveWindow?.packageName != SETTINGS) return
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            waitFor { root -> root.takeIf { it.packageName != SETTINGS } } ?: return@repeat
            return
        }
    }

    /** Polls Settings' window until [find] finds something, or gives up after [TIMEOUT_MILLIS]. */
    private fun <T> waitFor(find: (AccessibilityNodeInfo) -> T?): T? {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            service.rootInActiveWindow?.let(find)?.let { return it }
            SystemClock.sleep(POLL_MILLIS)
        }
        return null
    }

    private fun AccessibilityNodeInfo.withText(text: String) =
        findAccessibilityNodeInfosByText(text).filter { it.text?.toString() == text }

    private fun AccessibilityNodeInfo.byId(id: String): List<AccessibilityNodeInfo> = findAccessibilityNodeInfosByViewId(id)

    private fun AccessibilityNodeInfo.clickableAncestor(): AccessibilityNodeInfo? =
        generateSequence(this) { it.parent }.firstOrNull { it.isClickable }

    private companion object {
        const val SETTINGS = "com.android.tv.settings"
        const val GUIDANCE_TITLE = "$SETTINGS:id/guidance_title"
        const val ACTION_TITLE = "$SETTINGS:id/guidedactions_item_title"
        const val TIMEOUT_MILLIS = 4_000L
        const val POLL_MILLIS = 100L
        const val MAX_BACKS = 3
    }
}
