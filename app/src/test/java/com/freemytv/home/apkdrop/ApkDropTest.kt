package com.freemytv.home.apkdrop

import com.freemytv.home.monitor.StorageUsage
import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ApkDropTest {

    private val platform = FakeTvPlatform(storage = StorageUsage(totalBytes = 8_000_000_000, freeBytes = 1_000_000_000)).apply {
        apkInfos = mapOf(
            KODI_FILE to ApkInfo("org.xbmc.kodi", "Kodi", "21.1", 74_000_000),
            UPDATE_FILE to ApkInfo("org.xbmc.kodi", "Kodi", "21.1", 74_000_000, installedVersion = "21.0", sameSigner = true),
            MISMATCH_FILE to ApkInfo("org.xbmc.kodi", "Kodi", "21.1", 74_000_000, installedVersion = "21.0", sameSigner = false),
        )
    }
    private val drop = ApkDrop(platform, Random(7))

    private fun TestScope.opened(): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { drop.open() }

    private suspend fun send(code: String, file: String, size: Long = 74_000_000) = platform.uploads.emit(Triple(code, size, file))

    @Test
    fun `opening shows the address and a four-digit code and starts the page`() = runTest {
        opened()

        val state = drop.state.value
        assertEquals("192.168.100.18:8765", state.address)
        assertTrue(state.code.matches(Regex("""\d{4}""")))
        assertTrue(platform.serving)
    }

    @Test
    fun `leaving the screen stops the page`() = runTest {
        val page = opened()

        page.cancel()

        assertFalse(platform.serving)
        assertEquals(ApkDropState(), drop.state.value)
    }

    @Test
    fun `an APK sent with the right code is shown with its name and version`() = runTest {
        opened()

        send(drop.state.value.code, KODI_FILE)

        val received = drop.state.value.received!!
        assertEquals("Kodi", received.info.label)
        assertEquals("21.1", received.info.versionName)
        assertEquals(ApkKind.New, received.info.kind)
    }

    @Test
    fun `a wrong code is refused, and a few wrong codes change it`() = runTest {
        opened()
        val first = drop.state.value.code
        val wrong = if (first == "0000") "1111" else "0000"

        send(wrong, KODI_FILE)
        assertEquals(listOf(UploadVerdict.WrongCode), platform.verdicts)
        assertNull(drop.state.value.received)
        assertEquals(first, drop.state.value.code)

        send(wrong, KODI_FILE)
        send(wrong, KODI_FILE)

        assertNotEquals(first, drop.state.value.code)
    }

    @Test
    fun `updates and other signers are told apart from new apps`() = runTest {
        opened()

        send(drop.state.value.code, UPDATE_FILE)
        assertEquals(ApkKind.Update, drop.state.value.received!!.info.kind)
        drop.cancel()
        send(drop.state.value.code, MISMATCH_FILE)

        assertEquals(ApkKind.SignerMismatch, drop.state.value.received!!.info.kind)
    }

    @Test
    fun `a file that isn't an APK is deleted and explained`() = runTest {
        opened()

        send(drop.state.value.code, "/cache/photo.jpg")

        assertNull(drop.state.value.received)
        assertEquals(ApkDropMessage.NotAnApk, drop.state.value.message)
        assertEquals(listOf("/cache/photo.jpg"), platform.deletedFiles)
    }

    @Test
    fun `a file too big for the free storage is refused before it is sent`() = runTest {
        opened()

        send(drop.state.value.code, KODI_FILE, size = 460_000_000)

        assertEquals(listOf(UploadVerdict.TooBig), platform.verdicts)
        assertEquals(ApkDropMessage.TooBig, drop.state.value.message)
    }

    @Test
    fun `a second file waits until the first one is installed or cancelled`() = runTest {
        opened()
        send(drop.state.value.code, KODI_FILE)

        send(drop.state.value.code, UPDATE_FILE)

        assertEquals(UploadVerdict.Busy, platform.verdicts.last())
        assertEquals(KODI_FILE, drop.state.value.received!!.path)
    }

    @Test
    fun `installing goes through Android and deletes the file`() = runTest {
        opened()
        send(drop.state.value.code, KODI_FILE)

        drop.install()

        assertEquals(listOf(KODI_FILE to false), platform.installed)
        assertEquals(listOf(KODI_FILE), platform.deletedFiles)
        assertNull(drop.state.value.received)
        assertEquals(ApkDropMessage.Installed("Kodi"), drop.state.value.message)
    }

    @Test
    fun `cancelling at Android's confirmation also deletes the file`() = runTest {
        opened()
        platform.installResult = InstallResult.Cancelled
        send(drop.state.value.code, KODI_FILE)

        drop.install()

        assertEquals(listOf(KODI_FILE), platform.deletedFiles)
        assertEquals(ApkDropMessage.Cancelled, drop.state.value.message)
    }

    @Test
    fun `cancelling on the TV deletes the file without installing`() = runTest {
        opened()
        send(drop.state.value.code, KODI_FILE)

        drop.cancel()

        assertEquals(emptyList<Pair<String, Boolean>>(), platform.installed)
        assertEquals(listOf(KODI_FILE), platform.deletedFiles)
    }

    @Test
    fun `leaving with a file waiting deletes it`() = runTest {
        val page = opened()
        send(drop.state.value.code, KODI_FILE)

        page.cancel()

        assertEquals(listOf(KODI_FILE), platform.deletedFiles)
    }

    private companion object {
        const val KODI_FILE = "/cache/apkdrop/1.apk"
        const val UPDATE_FILE = "/cache/apkdrop/2.apk"
        const val MISMATCH_FILE = "/cache/apkdrop/3.apk"
    }
}
