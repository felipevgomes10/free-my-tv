package com.freemytv.home.webremote

import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.inputs.InputTray
import com.freemytv.home.platform.TvPlatform
import org.json.JSONArray
import org.json.JSONObject

/** Todos os apps on the FMTV Controller's Apps tab: App Tray first, in its order, then the rest by name. */
fun CompanionFeatures.addApps(library: AppLibrary, platform: TvPlatform) {
    list("apps") {
        val current = library.library.value ?: library.load().let { library.library.value }
        val tray = current?.trayApps.orEmpty()
        val rest = current?.installed.orEmpty().filterNot { app -> tray.any { it.packageName == app.packageName } }
        JSONArray().apply {
            for ((apps, inTray) in listOf(tray to true, rest to false)) {
                for (app in apps) {
                    put(JSONObject().put("package", app.packageName).put("label", app.label).put("picture", "app:${app.packageName}").put("inTray", inTray))
                }
            }
        }
    }
    open("package") { request ->
        val pkg = request.getString("package")
        if (!platform.isLaunchable(pkg)) throw Refused("not-installed")
        platform.launch(pkg)
    }
}

/** Entradas on the FMTV Controller's Apps tab: the Input Tray's Inputs, hidden ones left out. */
fun CompanionFeatures.addInputs(inputs: InputTray) {
    list("inputs") {
        if (inputs.state.value.inputs.isEmpty()) inputs.load()
        JSONArray().apply {
            for (tile in inputs.state.value.inputs) {
                put(
                    JSONObject()
                        .put("input", tile.id)
                        .put("name", tile.name ?: if (tile.isAntenna) "Antena" else tile.portLabel)
                        .put("port", if (tile.isAntenna) "TV aberta" else tile.portLabel)
                        .put("kind", if (tile.isAntenna) "antenna" else "hdmi"),
                )
            }
        }
    }
    open("input") { request ->
        val tile = inputs.state.value.inputs.firstOrNull { it.id == request.getString("input") } ?: throw Refused("unknown-input")
        inputs.select(tile)
    }
}
