package com.freemytv.home

import android.app.Application
import com.freemytv.home.android.AndroidTvPlatform
import com.freemytv.home.buttons.ShortcutButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the Home's screens and its key filter share for as long as the process lives. */
class HomeApplication : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val platform by lazy { AndroidTvPlatform(this) }

    val shortcutButtons by lazy { ShortcutButtons(platform) }

    override fun onCreate() {
        super.onCreate()
        scope.launch { shortcutButtons.load() }
    }
}
