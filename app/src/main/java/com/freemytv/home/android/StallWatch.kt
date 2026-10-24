package com.freemytv.home.android

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Notes in the log what the main thread was doing whenever it stalled long enough to drop
 * frames: how long, and the code on its stack midway. It asks the main thread for a tiny task
 * every [PING_MILLIS] and looks at its stack only when that task is late, so it costs next to
 * nothing. Read from the computer with `adb logcat -s Stall`.
 */
internal object StallWatch {

    fun start() {
        val main = Handler(Looper.getMainLooper())
        val mainThread = Looper.getMainLooper().thread
        val answered = AtomicLong()
        thread(name = "StallWatch", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            while (true) {
                val asked = SystemClock.uptimeMillis()
                main.post { answered.set(SystemClock.uptimeMillis()) }
                Thread.sleep(STALL_MILLIS)
                if (answered.get() >= asked) {
                    Thread.sleep(PING_MILLIS)
                    continue
                }
                // Stalled: what is it doing now? Then wait for the end to know how long.
                val stack = mainThread.stackTrace
                while (answered.get() < asked) Thread.sleep(POLL_MILLIS)
                val lasted = answered.get() - asked
                // The top of the stack says what kind of work; the Home's own frames say whose.
                val own = stack.drop(TOP_FRAMES).filter { it.className.startsWith(OWN_CODE) }.take(OWN_FRAMES)
                Log.w(TAG, "Main thread stalled ${lasted}ms:\n" + (stack.take(TOP_FRAMES) + own).joinToString("\n") { "  at $it" })
            }
        }
    }

    private const val TAG = "Stall"
    private const val PING_MILLIS = 50L
    private const val STALL_MILLIS = 80L
    private const val POLL_MILLIS = 10L
    private const val TOP_FRAMES = 12
    private const val OWN_FRAMES = 15
    private const val OWN_CODE = "com.freemytv"
}
