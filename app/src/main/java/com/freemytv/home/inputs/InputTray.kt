package com.freemytv.home.inputs

import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.trays.TrayEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One Input on the Input Tray. */
data class InputTile(
    /** The port's input id, which the owner's names, order and hidden state refer to. */
    val id: String,
    /** The owner's name for it, else the HDMI-CEC device name, else null (show the port or "Antena"). */
    val name: String?,
    /** The system's name for the port, such as "HDMI 1". */
    val portLabel: String,
    val isAntenna: Boolean,
    /** False for an HDMI port with nothing plugged in. */
    val hasSignal: Boolean,
)

data class InputTrayState(
    val inputs: List<InputTile>,
    val hiddenCount: Int,
    /** The Input lifted in Edit Mode, or null outside Edit Mode. */
    val lifted: InputTile?,
)

/**
 * The Input Tray. Inputs are the TV's physical sources only: HDMI ports and the
 * antenna. Live channels published by apps are never Inputs; the antenna is told
 * apart from them by being published by the same package as the HDMI ports.
 */
class InputTray(private val platform: TvPlatform, scope: CoroutineScope) {

    private class Port(val source: TvInputSource, val cecDevice: TvInputSource?)

    private val ports = MutableStateFlow<List<Port>>(emptyList())
    private val preferences = MutableStateFlow(InputPreferences())
    private val edit = MutableStateFlow<TrayEdit<String>?>(null)

    val state: StateFlow<InputTrayState> = combine(ports, preferences, edit) { ports, prefs, edit ->
        val visible = ports.filterNot { it.source.id in prefs.hidden }.associateBy { it.source.id }
        val order = edit?.order ?: ordered(visible.keys, prefs.order)
        val tiles = order.mapNotNull { visible[it]?.toTile(prefs) }
        InputTrayState(tiles, ports.size - visible.size, tiles.firstOrNull { it.id == edit?.lifted })
    }.stateIn(scope, SharingStarted.Eagerly, InputTrayState(emptyList(), 0, null))

    suspend fun load() {
        preferences.value = platform.loadInputPreferences()
        ports.value = physicalPorts(platform.tvInputs())
    }

    /** Keeps the tray current as devices are plugged in, switched on or removed. */
    suspend fun followInputChanges() {
        platform.inputChanges.collect { ports.value = physicalPorts(platform.tvInputs()) }
    }

    /** Switches the TV to the Input, through its HDMI-CEC device when one is known. */
    fun select(tile: InputTile) {
        val port = ports.value.firstOrNull { it.source.id == tile.id } ?: return
        val target = port.cecDevice ?: port.source
        platform.switchToInput(target.id, target.isPassthrough)
    }

    fun startEdit(tile: InputTile) {
        if (edit.value != null) return
        edit.value = TrayEdit.start(state.value.inputs.map { it.id }, tile.id)
    }

    fun move(steps: Int) = edit.update { it?.move(steps) }

    suspend fun drop() {
        val dropped = edit.value ?: return
        savePreferences { it.copy(order = dropped.order + it.order.filterNot { id -> id in dropped.order }) }
        edit.value = null
    }

    fun cancelEdit() {
        edit.value = null
    }

    /** Names the lifted Input; a blank name goes back to the device or port name. */
    suspend fun renameLifted(name: String) {
        val lifted = edit.value?.lifted ?: return
        val trimmed = name.trim()
        savePreferences { it.copy(names = if (trimmed.isEmpty()) it.names - lifted else it.names + (lifted to trimmed)) }
        edit.value = null
    }

    suspend fun hideLifted() {
        val lifted = edit.value?.lifted ?: return
        edit.value = null
        savePreferences { it.copy(hidden = it.hidden + lifted) }
    }

    /** Brings every hidden Input back onto the tray. */
    suspend fun showHidden() {
        edit.value = null
        savePreferences { it.copy(hidden = emptySet()) }
    }

    private suspend fun savePreferences(change: (InputPreferences) -> InputPreferences) {
        val changed = change(preferences.value)
        platform.saveInputPreferences(changed)
        preferences.value = changed
    }

    private fun Port.toTile(prefs: InputPreferences) = InputTile(
        id = source.id,
        name = prefs.names[source.id] ?: cecDevice?.label,
        portLabel = source.label,
        isAntenna = source.kind == InputKind.Tuner,
        hasSignal = source.kind == InputKind.Tuner || cecDevice != null || source.signal != Signal.Disconnected,
    )

    private companion object {

        fun physicalPorts(sources: List<TvInputSource>): List<Port> {
            val hardwarePackages = sources.filter { it.isPassthrough }.map { it.ownerPackage }.toSet()
            val cecDevices = sources.filter { it.isPassthrough && it.parentId != null }.associateBy { it.parentId }
            val hdmiPorts = sources.filter { it.isPassthrough && it.parentId == null }.sortedBy { it.label }
            val antennas = sources.filter { it.kind == InputKind.Tuner && !it.isPassthrough && it.ownerPackage in hardwarePackages }
            return (hdmiPorts + antennas).map { Port(it, cecDevices[it.id]) }
        }

        /** The owner's order first, then any Input the owner hasn't placed yet, in natural order. */
        fun ordered(ids: Set<String>, ownerOrder: List<String>): List<String> =
            ownerOrder.filter { it in ids } + ids.filterNot { it in ownerOrder }
    }
}
