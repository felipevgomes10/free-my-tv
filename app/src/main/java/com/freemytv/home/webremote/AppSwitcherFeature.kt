package com.freemytv.home.webremote

import com.freemytv.home.multitasking.OverlayResult
import com.freemytv.home.platform.TvPlatform
import org.json.JSONObject

/** A dedicated show-only action; never emulate a double Home press. */
fun CompanionFeatures.addAppSwitcher(platform: TvPlatform) {
    answer("open-app-switcher") {
        when (platform.showAppSwitcherOverlay()) {
            OverlayResult.Shown -> JSONObject().put("type", "done")
            OverlayResult.Unavailable -> throw Refused("overlay-unavailable")
            OverlayResult.Busy -> throw Refused("busy")
            OverlayResult.Error -> throw Refused("error")
        }
    }
}
