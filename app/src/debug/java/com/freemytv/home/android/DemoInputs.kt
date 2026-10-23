package com.freemytv.home.android

import android.content.Context
import com.freemytv.home.inputs.InputKind
import com.freemytv.home.inputs.Signal
import com.freemytv.home.inputs.TvInputSource
import java.io.File

/**
 * Debug builds only: stands in for the owner's TCL inputs on an emulator, which has none.
 * Turn on with `adb shell run-as com.freemytv.home touch files/demo_inputs`.
 */
object DemoInputs {

    fun sources(context: Context): List<TvInputSource>? =
        if (File(context.filesDir, FLAG).exists()) OWNERS_TV else null

    private const val FLAG = "demo_inputs"
    private const val TCL = "com.tcl.tvinput"

    // Ids as published by the owner's TV (dumpsys tv_input, 2026-10-23).
    private val OWNERS_TV = listOf(
        TvInputSource("$TCL/.TvPassThroughService/HW15", TCL, InputKind.Hdmi, true, null, "HDMI 1", Signal.Connected),
        TvInputSource("$TCL/.TvPassThroughService/HDMI300004", TCL, InputKind.Hdmi, true, "$TCL/.TvPassThroughService/HW15", "PS5", Signal.Connected),
        TvInputSource("$TCL/.TvPassThroughService/HW16", TCL, InputKind.Hdmi, true, null, "HDMI 2", Signal.Connected),
        TvInputSource("$TCL/.TvPassThroughService/HW17", TCL, InputKind.Hdmi, true, null, "HDMI 3", Signal.Disconnected),
        TvInputSource("$TCL/.TunerInputService/HW2", TCL, InputKind.Tuner, false, null, "TV", Signal.Connected),
        TvInputSource("com.tcl.channelplus/com.tcl.iptv.service.InputService", "com.tcl.channelplus", InputKind.Tuner, false, null, "Channel+", Signal.Connected),
    )
}
