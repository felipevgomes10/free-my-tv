package com.freemytv.home.webremote

import com.freemytv.home.upnext.UpNextTray
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Continuar assistindo on the FMTV Controller's Assistir tab: the Up Next Tray's entries, as the
 * TV shows them, each resuming as its tile does. The tray is read again on each ask, as when the
 * Home comes to the front, so what an app listed in Watch Next meanwhile is there.
 */
fun CompanionFeatures.addUpNext(tray: UpNextTray) {
    list("up-next") {
        tray.refresh()
        JSONArray().apply {
            for (entry in tray.state.value.entries) {
                put(
                    JSONObject()
                        .put("upNext", entry.key)
                        .put("title", entry.title ?: JSONObject.NULL)
                        .put("episode", entry.subtitle ?: JSONObject.NULL)
                        .put("app", entry.appLabel)
                        .put("minutesLeft", entry.remainingMillis?.let { ((it + 59_999) / 60_000).toInt() } ?: JSONObject.NULL)
                        .put("progress", entry.progress?.let { (it * 100).roundToInt() / 100.0 } ?: JSONObject.NULL)
                        .put("picture", if (entry.artwork != null) "upnext:${entry.key}" else "app:${entry.packageName}"),
                )
            }
        }
    }
    open("upNext") { request ->
        val entry = tray.state.value.entries.firstOrNull { it.key == request.getString("upNext") } ?: throw Refused("not-found")
        tray.select(entry)
    }
}
