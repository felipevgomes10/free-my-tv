package com.freemytv.home.android

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import com.freemytv.home.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Force stops apps by pressing Settings' own buttons (ADR 0010). Android keeps force stop to
 * itself, so the key filter, which already holds accessibility access, opens the app's page in
 * Settings, presses Force stop, confirms with OK and leaves Settings again. One app at a time,
 * on a thread of its own so remote keys never wait for it. A still of the screen covers Settings
 * meanwhile, so the owner never sees it, and [busy] tells the key filter to hold keys back.
 */
internal class ForceStopper(private val service: AccessibilityService) {

    private val thread = HandlerThread("ForceStop").apply { start() }
    private val handler = Handler(thread.looper)
    private val cover = Cover()
    private val pending = AtomicInteger()

    /** True from a close's request until Settings is gone again. */
    val busy get() = pending.get() > 0

    fun forceStop(packageName: String) {
        pending.incrementAndGet()
        handler.post {
            try {
                run(packageName)
            } finally {
                pending.decrementAndGet()
            }
        }
    }

    fun quit() {
        thread.quitSafely()
        cover.quit()
    }

    private fun run(packageName: String) {
        // Settings' own words for the button, in the TV's language ("Forçar parada").
        val forceStop = runCatching {
            val resources = service.packageManager.getResourcesForApplication(SETTINGS)
            resources.getString(resources.getIdentifier("device_apps_app_management_force_stop", "string", SETTINGS))
        }.getOrNull() ?: return
        // Lets the Home take the closed app off its screen first, so the still already shows it gone.
        SystemClock.sleep(SETTLE_MILLIS)
        cover.show(packageName)
        try {
            pressForceStop(packageName, forceStop)
            leaveSettings()
            SystemClock.sleep(SETTLE_MILLIS)
        } finally {
            cover.hide()
        }
    }

    private fun pressForceStop(packageName: String, forceStop: String) {
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

    /**
     * A window over everything, Settings included, showing the screen as it was; plain black with
     * "Fechando …" when Android won't take the still. It lives on a thread of its own: the close
     * itself sleeps while it polls, and the cover must keep drawing.
     */
    private inner class Cover {
        private val thread = HandlerThread("ForceStopCover").apply { start() }
        private val handler = Handler(thread.looper)
        private val windows = service.getSystemService(WindowManager::class.java)
        private var view: View? = null
        private var still: HardwareBuffer? = null

        fun show(packageName: String) {
            val shot = screenshot()
            val drawn = CountDownLatch(1)
            handler.post {
                val view = if (shot != null) {
                    ImageView(service).apply {
                        setImageBitmap(shot.first)
                        scaleType = ImageView.ScaleType.FIT_XY
                    }
                } else {
                    TextView(service).apply {
                        val label = runCatching { service.packageManager.run { getApplicationLabel(getApplicationInfo(packageName, 0)) } }.getOrDefault(packageName)
                        text = service.getString(R.string.closing_app, label)
                        gravity = Gravity.CENTER
                        textSize = 24f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.BLACK)
                    }
                }
                view.viewTreeObserver.addOnDrawListener(ViewTreeObserver.OnDrawListener { drawn.countDown() })
                runCatching { windows.addView(view, layout()) }.onFailure { drawn.countDown() }.onSuccess {
                    this.view = view
                    still = shot?.second
                }
            }
            drawn.await(COVER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            // Drawn is not yet on screen: give it a couple of frames before Settings opens.
            SystemClock.sleep(FRAMES_MILLIS)
        }

        fun hide() {
            handler.post {
                view?.let { runCatching { windows.removeView(it) } }
                view = null
                still?.close()
                still = null
            }
        }

        fun quit() {
            hide()
            thread.quitSafely()
        }

        private fun layout() = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        )

        /** The screen as a bitmap and the buffer behind it, to close once it's off screen. */
        private fun screenshot(): Pair<Bitmap, HardwareBuffer>? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val result = AtomicReference<AccessibilityService.ScreenshotResult?>()
            val done = CountDownLatch(1)
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                { it.run() },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        result.set(screenshot)
                        done.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        done.countDown()
                    }
                },
            )
            done.await(COVER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            val shot = result.get() ?: return null
            val bitmap = Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace) ?: return null.also { shot.hardwareBuffer.close() }
            return bitmap to shot.hardwareBuffer
        }
    }

    private companion object {
        const val SETTINGS = "com.android.tv.settings"
        const val GUIDANCE_TITLE = "$SETTINGS:id/guidance_title"
        const val ACTION_TITLE = "$SETTINGS:id/guidedactions_item_title"
        const val TIMEOUT_MILLIS = 4_000L
        const val POLL_MILLIS = 100L
        const val MAX_BACKS = 3
        const val SETTLE_MILLIS = 150L
        const val FRAMES_MILLIS = 50L
        const val COVER_TIMEOUT_MILLIS = 500L
    }
}
