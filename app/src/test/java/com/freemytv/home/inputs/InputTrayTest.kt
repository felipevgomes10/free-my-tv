package com.freemytv.home.inputs

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mirrors what the owner's TCL publishes (dumpsys tv_input, 2026-10-23). */
class InputTrayTest {

    private val tcl = "com.tcl.tvinput"
    private fun hdmi(n: Int, signal: Signal = Signal.Connected) =
        TvInputSource("$tcl/.TvPassThroughService/HW${14 + n}", tcl, InputKind.Hdmi, true, null, "HDMI $n", signal)

    private val hdmi1 = hdmi(1)
    private val hdmi2 = hdmi(2)
    private val hdmi3 = hdmi(3, Signal.Disconnected)
    private val antenna = TvInputSource("$tcl/.TunerInputService/HW2", tcl, InputKind.Tuner, false, null, "TV", Signal.Connected)
    private val ps5 = TvInputSource("$tcl/.TvPassThroughService/HDMI300004", tcl, InputKind.Hdmi, true, hdmi1.id, "PS5", Signal.Connected)
    private val channelPlus = TvInputSource("com.tcl.channelplus/com.tcl.iptv.service.InputService", "com.tcl.channelplus", InputKind.Tuner, false, null, "Channel+", Signal.Connected)
    private val globoplay = TvInputSource("com.globo.globotv/.androidtv.tif.service.LiveTvInputService", "com.globo.globotv", InputKind.Tuner, false, null, "Globoplay", Signal.Connected)

    private val ownersTv = listOf(globoplay, antenna, hdmi3, ps5, hdmi2, channelPlus, hdmi1)

    private suspend fun TestScope.tray(platform: FakeTvPlatform) = InputTray(platform, backgroundScope).also {
        backgroundScope.launch { it.followInputChanges() }
        it.load()
    }

    private fun InputTray.ids() = state.value.inputs.map { it.id }
    private fun InputTray.tile(source: TvInputSource) = state.value.inputs.first { it.id == source.id }

    @Test
    fun `only HDMI ports and the antenna are Inputs, never live channels from apps`() = runTest(UnconfinedTestDispatcher()) {
        val tray = tray(FakeTvPlatform(inputs = ownersTv))

        assertEquals(listOf(hdmi1, hdmi2, hdmi3, antenna).map { it.id }, tray.ids())
    }

    @Test
    fun `an HDMI port shows the name of the HDMI-CEC device plugged into it`() = runTest(UnconfinedTestDispatcher()) {
        val tray = tray(FakeTvPlatform(inputs = ownersTv))

        assertEquals("PS5", tray.tile(hdmi1).name)
        assertEquals("HDMI 1", tray.tile(hdmi1).portLabel)
        assertNull(tray.tile(hdmi2).name)
    }

    @Test
    fun `an HDMI port with nothing connected has no signal, the antenna always does`() = runTest(UnconfinedTestDispatcher()) {
        val tray = tray(FakeTvPlatform(inputs = ownersTv))

        assertEquals(listOf(true, true, false, true), tray.state.value.inputs.map { it.hasSignal })
        assertEquals(true, tray.tile(antenna).isAntenna)
    }

    @Test
    fun `selecting an Input switches to it, through its HDMI-CEC device when there is one`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(inputs = ownersTv)
        val tray = tray(platform)

        tray.select(tray.tile(hdmi1))
        tray.select(tray.tile(antenna))

        assertEquals(listOf(ps5.id, antenna.id), platform.switchedTo)
    }

    @Test
    fun `renaming an Input keeps the owner's name over the device name, and a blank name restores it`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(inputs = ownersTv)
        val tray = tray(platform)

        tray.startEdit(tray.tile(hdmi1))
        tray.renameLifted("  Videogame  ")
        assertEquals("Videogame", tray.tile(hdmi1).name)
        assertEquals("Videogame", tray(platform).tile(hdmi1).name)

        tray.startEdit(tray.tile(hdmi1))
        tray.renameLifted(" ")
        assertEquals("PS5", tray.tile(hdmi1).name)
    }

    @Test
    fun `hiding an Input takes it off the tray until the owner shows hidden Inputs again`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(inputs = ownersTv)
        val tray = tray(platform)

        tray.startEdit(tray.tile(hdmi3))
        tray.hideLifted()
        assertEquals(listOf(hdmi1, hdmi2, antenna).map { it.id }, tray.ids())
        assertEquals(1, tray.state.value.hiddenCount)
        assertEquals(listOf(hdmi1, hdmi2, antenna).map { it.id }, tray(platform).ids())

        tray.showHidden()
        assertEquals(listOf(hdmi1, hdmi2, hdmi3, antenna).map { it.id }, tray.ids())
        assertEquals(0, tray.state.value.hiddenCount)
    }

    @Test
    fun `moving an Input in Edit Mode keeps the new order`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(inputs = ownersTv)
        val tray = tray(platform)

        tray.startEdit(tray.tile(antenna))
        tray.move(-3)
        tray.drop()

        val expected = listOf(antenna, hdmi1, hdmi2, hdmi3).map { it.id }
        assertEquals(expected, tray.ids())
        assertEquals(expected, tray(platform).ids())
    }

    @Test
    fun `cancelling an Input move restores the order`() = runTest(UnconfinedTestDispatcher()) {
        val tray = tray(FakeTvPlatform(inputs = ownersTv))

        tray.startEdit(tray.tile(hdmi1))
        tray.move(+2)
        tray.cancelEdit()

        assertEquals(listOf(hdmi1, hdmi2, hdmi3, antenna).map { it.id }, tray.ids())
        assertNull(tray.state.value.lifted)
    }

    @Test
    fun `plugging a device in updates the tray`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(inputs = ownersTv)
        val tray = tray(platform)

        platform.changeInputs(ownersTv - hdmi3 + hdmi(3, Signal.Connected))

        assertEquals(true, tray.tile(hdmi3).hasSignal)
    }
}
