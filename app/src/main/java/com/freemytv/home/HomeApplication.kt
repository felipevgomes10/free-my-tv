package com.freemytv.home

import android.app.Application
import com.freemytv.home.android.AndroidTvPlatform
import com.freemytv.home.buttons.ShortcutButtons
import com.freemytv.home.setup.SetupWatchdog
import com.freemytv.home.upnext.UpNextTray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the Home's screens and its key filter share for as long as the process lives. */
class HomeApplication : Application() {
    /** A native dream can cover the Home without pausing its activity. */
    val screensaverActive = kotlinx.coroutines.flow.MutableStateFlow(false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val platform by lazy { AndroidTvPlatform(this) }

    val shortcutButtons by lazy { ShortcutButtons(platform) }

    val setupWatchdog by lazy { SetupWatchdog(platform) }

    val upNext by lazy { UpNextTray(platform) }

    override fun onCreate() {
        super.onCreate()
        scope.launch { shortcutButtons.load() }
        // Checked from boot (the Home starts with the TV) and every 15 minutes, whichever app is in front.
        scope.launch { setupWatchdog.keepWatching() }
        // What plays is recorded whichever app is in front, for as long as the Home's process lives.
        scope.launch {
            upNext.load()
            upNext.follow()
        }
    }
}
