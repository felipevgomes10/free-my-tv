package com.freemytv.home.webremote

import com.freemytv.home.buttons.ButtonTarget
import com.freemytv.home.buttons.ShortcutButton
import com.freemytv.home.buttons.ShortcutButtons
import com.freemytv.home.platform.TvPlatform
import org.json.JSONArray
import org.json.JSONObject

/** Each Shortcut Button by its id in the protocol. */
internal val SHORTCUT_IDS = mapOf(
    ShortcutButton.Netflix to "netflix",
    ShortcutButton.Globoplay to "globoplay",
    ShortcutButton.PrimeVideo to "prime-video",
    ShortcutButton.DisneyPlus to "disney-plus",
    ShortcutButton.YouTube to "youtube",
    ShortcutButton.Input to "input",
)

/**
 * The Shortcut Buttons on the FMTV Controller's Controls tab: each one named after what the Owner
 * assigned to it, and doing what the Remote's button does.
 */
fun CompanionFeatures.addShortcuts(buttons: ShortcutButtons, platform: TvPlatform) {
    list("shortcuts") {
        JSONArray().apply {
            for (button in ShortcutButton.entries) {
                put(JSONObject().put("shortcut", SHORTCUT_IDS.getValue(button)).put("label", labelOf(button, buttons.targetOf(button), platform)))
            }
        }
    }
    open("shortcut") { request ->
        val button = SHORTCUT_IDS.entries.firstOrNull { it.value == request.getString("shortcut") }?.key ?: throw Refused("unknown-shortcut")
        val target = buttons.targetOf(button)
        if (target is ButtonTarget.App && !platform.isLaunchable(target.packageName)) throw Refused("not-installed")
        buttons.perform(target)
    }
}

/** The name printed on each button, for an app that isn't installed. */
private val PRINTED = mapOf(
    ShortcutButton.Netflix to "Netflix",
    ShortcutButton.Globoplay to "Globoplay",
    ShortcutButton.PrimeVideo to "Prime Video",
    ShortcutButton.DisneyPlus to "Disney+",
    ShortcutButton.YouTube to "YouTube",
    ShortcutButton.Input to "Entrada",
)

private suspend fun labelOf(button: ShortcutButton, target: ButtonTarget, platform: TvPlatform): String = when (target) {
    is ButtonTarget.App ->
        if (platform.isLaunchable(target.packageName)) platform.appLabel(target.packageName) else PRINTED.getValue(button)
    is ButtonTarget.TvInput ->
        platform.loadInputPreferences().names[target.inputId]
            ?: platform.tvInputs().firstOrNull { it.id == target.inputId }?.label
            ?: "Entrada"
    ButtonTarget.AppSwitcher -> "Apps abertos"
    ButtonTarget.InputTray -> "Entradas"
    ButtonTarget.HomeSettings -> "Configurações"
}
