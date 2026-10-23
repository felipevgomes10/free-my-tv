package com.freemytv.home.inputs

/** A TV input as Android publishes it: a physical port, a tuner, an HDMI-CEC device, or a channel source from an app. */
data class TvInputSource(
    val id: String,
    /** The package that publishes the input. */
    val ownerPackage: String,
    val kind: InputKind,
    /** True for inputs that pass a physical signal through (HDMI ports and the devices on them). */
    val isPassthrough: Boolean,
    /** For an HDMI-CEC device, the input of the port it is plugged into. */
    val parentId: String?,
    val label: String,
    val signal: Signal,
)

enum class InputKind { Hdmi, Tuner, Other }

enum class Signal { Connected, Standby, Disconnected }

/** The owner's arrangement of the Input Tray. */
data class InputPreferences(
    val order: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
)
