package com.freemytv.home.webremote

import com.freemytv.home.multitasking.Multitasking
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abertos on the FMTV Controller's Apps tab: the Open Apps, most recently used first. Switching
 * to one is opening it (`open` with `package`, from [addApps]), as the App Switcher does; closing
 * one is the App Switcher's ✕, refused for an app playing music.
 */
fun CompanionFeatures.addOpenApps(multitasking: Multitasking) {
    list("open-apps") {
        JSONArray().apply {
            for (app in multitasking.openApps()) {
                put(JSONObject().put("package", app.packageName).put("label", app.label).put("picture", "app:${app.packageName}").put("playing", app.playing))
            }
        }
    }
    answer("close-app") { request ->
        val app = multitasking.openApps().firstOrNull { it.packageName == request.getString("package") } ?: throw Refused("not-open")
        if (app.playing) throw Refused("playing")
        multitasking.close(app)
        JSONObject().put("type", "done")
    }
}

/**
 * Pushes Abertos again whenever the Open Apps change. Nothing tells the Home when an app opens or
 * closes, so it looks every few seconds, and only while a phone is ready to hear it.
 */
suspend fun WebRemoteBridge.watchOpenApps(multitasking: Multitasking, every: Duration = OPEN_APPS_CHECK) {
    var last: List<Pair<String, Boolean>>? = null
    while (true) {
        if (hasReadyPhone) {
            val now = multitasking.openApps().map { it.packageName to it.playing }
            if (last != null && now != last) push("open-apps")
            last = now
        } else {
            last = null
        }
        delay(every)
    }
}

private val OPEN_APPS_CHECK = 5.seconds
