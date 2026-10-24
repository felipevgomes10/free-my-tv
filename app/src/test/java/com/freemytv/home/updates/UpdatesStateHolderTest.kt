package com.freemytv.home.updates

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdatesStateHolderTest {

    // The fake clock is 2026-10-23.
    private fun platformWithPatch(patch: LocalDate?, packages: List<InstalledPackage> = emptyList()) =
        FakeTvPlatform(packages = packages).apply { device = device.copy(securityPatch = patch) }

    private suspend fun updates(platform: FakeTvPlatform) = UpdatesStateHolder(platform).also { it.load() }

    @Test
    fun `the owner's TV patch, from December 2025, is ten months old and outdated`() = runTest {
        val state = updates(platformWithPatch(LocalDate.of(2025, 12, 5))).state.value

        assertEquals(10L, state.patchAgeMonths)
        assertTrue(state.patchOutdated)
    }

    @Test
    fun `a patch exactly six months old is not yet outdated, a day more is`() = runTest {
        assertFalse(updates(platformWithPatch(LocalDate.of(2026, 4, 23))).state.value.patchOutdated)
        assertTrue(updates(platformWithPatch(LocalDate.of(2026, 4, 22))).state.value.patchOutdated)
    }

    @Test
    fun `a TV that reports no patch shows no age and no warning`() = runTest {
        val state = updates(platformWithPatch(null)).state.value

        assertNull(state.patchAgeMonths)
        assertFalse(state.patchOutdated)
    }

    @Test
    fun `apps not installed by the Play Store are listed with their version, system apps left out`() = runTest {
        val packages = listOf(
            InstalledPackage("com.teamsmart.videomanager.tv", "SmartTube", "25.4", installer = null, isSystem = false),
            InstalledPackage("com.netflix.ninja", "Netflix", "11.2", installer = "com.android.vending", isSystem = false),
            InstalledPackage("org.xbmc.kodi", "Kodi", "21.1", installer = "com.google.android.packageinstaller", isSystem = false),
            InstalledPackage("com.tcl.tv", "TV", "3.0", installer = null, isSystem = true),
            InstalledPackage("com.freemytv.home", "Free My TV", "0.1.0", installer = null, isSystem = false),
        )

        val sideloaded = updates(platformWithPatch(LocalDate.of(2025, 12, 5), packages)).state.value.sideloaded

        assertEquals(
            listOf("com.freemytv.home", "org.xbmc.kodi", "com.teamsmart.videomanager.tv"),
            sideloaded.map { it.packageName },
        )
    }

    @Test
    fun `the buttons open the system updater and the Play Store's app list`() = runTest {
        val platform = platformWithPatch(LocalDate.of(2025, 12, 5))
        val updates = updates(platform)

        updates.checkSystemUpdate()
        updates.openPlayStoreApps()

        assertEquals(1, platform.systemUpdaterOpened)
        assertEquals(1, platform.playStoreAppsOpened)
    }
}
