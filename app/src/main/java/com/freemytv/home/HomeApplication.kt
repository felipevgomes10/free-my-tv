package com.freemytv.home

import android.app.Application
import com.freemytv.home.android.AndroidTvPlatform
import com.freemytv.home.android.StallWatch
import com.freemytv.home.buttons.ShortcutButtons
import com.freemytv.home.setup.SetupWatchdog
import com.freemytv.home.upnext.UpNextTray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.freemytv.home.webremote.answer
import kotlinx.coroutines.runBlocking

/** What the Home's screens and its key filter share for as long as the process lives. */
class HomeApplication : Application() {
    /** A native dream can cover the Home without pausing its activity. */
    val screensaverActive = kotlinx.coroutines.flow.MutableStateFlow(false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val platform by lazy { AndroidTvPlatform(this) }

    val shortcutButtons by lazy { ShortcutButtons(platform) }

    val setupWatchdog by lazy { SetupWatchdog(platform) }

    val webRemoteLink by lazy { com.freemytv.home.webremote.WebRemoteLink(platform) }

    val pairedPhones by lazy { com.freemytv.home.webremote.PairedPhones(platform) }

    val webRemotePairing by lazy { com.freemytv.home.webremote.WebRemotePairing(platform, pairedPhones) }

    val upNext by lazy { UpNextTray(platform) }

    override fun onCreate() {
        super.onCreate()
        StallWatch.start()
        scope.launch { shortcutButtons.load() }
        // Checked from boot (the Home starts with the TV) and every 15 minutes, whichever app is in front.
        scope.launch { setupWatchdog.keepWatching() }
        // The FMTV Controller's bridge serves its page, Pairing and keys for as long as the Home lives.
        scope.launch {
            pairedPhones.load()
            com.freemytv.home.android.WebRemoteServer(
                assets = assets,
                pair = { body -> runBlocking { webRemotePairing.answer(body) } },
                // Keys arrive with Web Remote ticket 07; until then a connection is closed at once.
                remote = { peer ->
                    peer.close()
                    object : com.freemytv.home.android.WebSocketListener {
                        override fun onText(text: String) = Unit
                        override fun onClosed() = Unit
                    }
                },
            ).start(com.freemytv.home.webremote.WEB_REMOTE_PORT)
        }
        // What plays is recorded whichever app is in front, for as long as the Home's process lives.
        scope.launch {
            upNext.load()
            upNext.follow()
        }
    }
}
