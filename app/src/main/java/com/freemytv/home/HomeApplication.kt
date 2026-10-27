package com.freemytv.home

import android.app.Application
import com.freemytv.home.android.AndroidTvPlatform
import com.freemytv.home.android.StallWatch
import com.freemytv.home.buttons.ShortcutButtons
import com.freemytv.home.setup.SetupWatchdog
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.apps.AllAppsStateHolder
import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.explorar.Explorar
import com.freemytv.home.inputs.InputTray
import com.freemytv.home.multitasking.Multitasking
import com.freemytv.home.upnext.UpNextTray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.freemytv.home.android.picturePng
import com.freemytv.home.android.upNextPictureJpeg
import com.freemytv.home.webremote.addApps
import com.freemytv.home.webremote.addAppSwitcher
import com.freemytv.home.webremote.addExplorar
import com.freemytv.home.webremote.addInputs
import com.freemytv.home.webremote.addOpenApps
import com.freemytv.home.webremote.addShortcuts
import com.freemytv.home.webremote.addUpNext
import com.freemytv.home.webremote.watchOpenApps
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    val webRemoteBridge by lazy { com.freemytv.home.webremote.WebRemoteBridge(platform, pairedPhones) }

    val webRemotePictures by lazy { com.freemytv.home.webremote.WebRemotePictures(pairedPhones) }

    val upNext by lazy { UpNextTray(platform) }

    // The apps, the Inputs, the Open Apps and Explorar live as long as the process, not only while the
    // Home's screen is open, so the FMTV Controller's bridge reaches them with another app in front.
    val library by lazy { AppLibrary(platform) }

    val allApps by lazy { AllAppsStateHolder(platform, library, scope) }

    val inputs by lazy { InputTray(platform, scope) }

    val multitasking by lazy { Multitasking(platform) }

    private val cachedArtwork = lazy { AppArtworkLoader(this) }
    val artwork get() = cachedArtwork.value

    val explorar by lazy { Explorar(platform) }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) clearPictures()
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        clearPictures()
    }

    private fun clearPictures() {
        if (cachedArtwork.isInitialized()) artwork.clear()
        webRemotePictures.clear()
        com.freemytv.home.ui.clearShelfPictures()
    }

    override fun onCreate() {
        super.onCreate()
        StallWatch.start()
        scope.launch(Dispatchers.IO) { platform.prepareTemporaryDownloads() }
        scope.launch { shortcutButtons.load() }
        // Checked from boot (the Home starts with the TV) and every 15 minutes, whichever app is in front.
        scope.launch { setupWatchdog.keepWatching() }
        // The FMTV Controller's bridge serves its page, Pairing and keys for as long as the Home lives.
        // The bridge writes to phones' sockets: never on the main thread.
        scope.launch(Dispatchers.IO) { pairedPhones.revoked.collect { webRemoteBridge.revoked(it) } }
        scope.launch(Dispatchers.IO) { webRemoteLink.state.collect { if (it != com.freemytv.home.webremote.LinkState.Checking && it != com.freemytv.home.webremote.LinkState.Linking) webRemoteBridge.linkChanged() } }
        // The companion features the FMTV Controller's tabs ask for (its ADR 0006).
        webRemoteBridge.companion.addShortcuts(shortcutButtons, platform)
        scope.launch(Dispatchers.IO) { shortcutButtons.assignments.collect { webRemoteBridge.push("shortcuts") } }
        webRemoteBridge.companion.addApps(library, platform)
        webRemoteBridge.companion.addAppSwitcher(platform)
        webRemoteBridge.companion.addInputs(inputs)
        webRemoteBridge.companion.addOpenApps(multitasking)
        webRemoteBridge.companion.addUpNext(upNext)
        webRemoteBridge.companion.addExplorar(explorar, platform)
        webRemotePictures.source("upnext") { key -> upNext.state.value.entries.firstOrNull { it.key == key }?.artwork?.let { upNextPictureJpeg(it) } }
        scope.launch(Dispatchers.IO) { upNext.state.map { it.entries }.distinctUntilChanged().collect { webRemoteBridge.push("up-next") } }
        scope.launch(Dispatchers.IO) { webRemoteBridge.watchOpenApps(multitasking) }
        webRemotePictures.source("app") { pkg ->
            platform.installedApps().firstOrNull { it.packageName == pkg }?.let { artwork.picturePng(it) }
        }
        scope.launch(Dispatchers.IO) { library.library.collect { webRemoteBridge.push("apps") } }
        scope.launch(Dispatchers.IO) { inputs.state.map { it.inputs }.distinctUntilChanged().collect { webRemoteBridge.push("inputs") } }
        scope.launch {
            pairedPhones.load()
            com.freemytv.home.android.WebRemoteServer(
                assets = assets,
                pair = { body -> runBlocking { webRemotePairing.answer(body) } },
                remote = { peer ->
                    val session = webRemoteBridge.connect(object : com.freemytv.home.webremote.RemotePeer {
                        override fun send(text: String) = peer.send(text)
                        override fun close() = peer.close()
                    })
                    // Each connection reads on its own thread: its keys wait for one another, never for another phone's reading.
                    object : com.freemytv.home.android.WebSocketListener {
                        override fun onText(text: String) = runBlocking { session.onText(text) }
                        override fun onClosed() = session.onClosed()
                    }
                },
                picture = { id, authorization -> runBlocking { webRemotePictures.serve(id, authorization) } },
            ).start(com.freemytv.home.webremote.WEB_REMOTE_PORT)
        }
        scope.launch { library.followPackageEvents() }
        scope.launch { inputs.followInputChanges() }
        scope.launch {
            multitasking.load()
            // The Open App Limit holds while the Home is alive, whichever app is in front.
            multitasking.keepLimit()
        }
        // What plays is recorded whichever app is in front, for as long as the Home's process lives.
        scope.launch {
            upNext.load()
            upNext.follow()
        }
    }
}
