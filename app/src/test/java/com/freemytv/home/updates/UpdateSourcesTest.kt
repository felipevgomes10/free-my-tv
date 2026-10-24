package com.freemytv.home.updates

import com.freemytv.home.apkdrop.InstallResult
import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSourcesTest {

    private val platform = FakeTvPlatform(
        packages = listOf(InstalledPackage(SMARTTUBE, "SmartTube", "25.4", installer = null, isSystem = false)),
    ).apply {
        releases = mapOf(REPO to ReleaseLookup.Found(release("v25.6", "SmartTube_stable_25.6_armeabi-v7a.apk", "SmartTube_stable_25.6_arm64-v8a.apk")))
    }

    private suspend fun updates() = UpdatesStateHolder(platform).also { it.load() }

    private val UpdatesStateHolder.smartTube get() = state.value.sideloaded.single()

    @Test
    fun `a repository with an APK release becomes the app's Update Source`() = runTest {
        val updates = updates()

        updates.setSource(SMARTTUBE, "https://github.com/yuliskov/SmartTube/releases")

        assertEquals(REPO, updates.smartTube.source)
        assertEquals(mapOf(SMARTTUBE to REPO), platform.updateSources)
    }

    @Test
    fun `a repository without an APK, or that doesn't exist, is refused`() = runTest {
        platform.releases = mapOf("owner/sources-only" to ReleaseLookup.Found(release("1.0", "source.zip")))
        val updates = updates()

        updates.setSource(SMARTTUBE, "owner/sources-only")
        assertEquals(UpdateProblem.NoApk, updates.smartTube.problem)
        updates.setSource(SMARTTUBE, "owner/missing")
        assertEquals(UpdateProblem.NotFound, updates.smartTube.problem)
        updates.setSource(SMARTTUBE, "not a repository")

        assertNull(updates.smartTube.source)
        assertEquals(emptyMap<String, String>(), platform.updateSources)
    }

    @Test
    fun `a newer release offers its version and lights the Updates dot`() = runTest {
        platform.device = platform.device.copy(securityPatch = null)
        val updates = updates()
        assertFalse(updates.state.value.needsAttention)

        updates.setSource(SMARTTUBE, REPO)

        assertEquals("25.6", updates.smartTube.update?.version)
        assertTrue(updates.state.value.needsAttention)
    }

    @Test
    fun `the installed version or an older one offers nothing`() = runTest {
        platform.releases = mapOf(REPO to ReleaseLookup.Found(release("v25.4", "SmartTube_25.4_armeabi-v7a.apk")))
        val updates = updates()

        updates.setSource(SMARTTUBE, REPO)

        assertNull(updates.smartTube.update)
    }

    @Test
    fun `sources are checked again once a day`() = runTest {
        platform.updateSources = mapOf(SMARTTUBE to REPO)
        val updates = updates()
        backgroundScope.launch { updates.keepChecking() }
        runCurrent()
        assertEquals(1, platform.releaseChecks.size)

        advanceTimeBy(24.hours)
        runCurrent()

        assertEquals(2, platform.releaseChecks.size)
    }

    @Test
    fun `updating downloads the APK for this TV and hands it to Android`() = runTest {
        val updates = updates()
        updates.setSource(SMARTTUBE, REPO)

        updates.update(SMARTTUBE)

        assertEquals(listOf("SmartTube_stable_25.6_armeabi-v7a.apk"), platform.downloaded.map { it.name })
        assertEquals(listOf("/cache/updates/SmartTube_stable_25.6_armeabi-v7a.apk" to false), platform.installed)
        assertEquals(listOf("/cache/updates/SmartTube_stable_25.6_armeabi-v7a.apk"), platform.deletedFiles)
    }

    @Test
    fun `a failed download or install is told`() = runTest {
        val updates = updates()
        updates.setSource(SMARTTUBE, REPO)

        platform.downloadFails = true
        updates.update(SMARTTUBE)
        assertEquals(UpdateProblem.DownloadFailed, updates.smartTube.problem)

        platform.downloadFails = false
        platform.installResult = InstallResult.Failed("INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        updates.update(SMARTTUBE)
        assertEquals(UpdateProblem.InstallFailed, updates.smartTube.problem)
    }

    @Test
    fun `being offline is told without losing the source`() = runTest {
        platform.updateSources = mapOf(SMARTTUBE to REPO)
        platform.releases = null
        val updates = updates()

        updates.checkSources()

        assertEquals(UpdateProblem.Offline, updates.smartTube.problem)
        assertEquals(REPO, updates.smartTube.source)
    }

    @Test
    fun `a source can be removed`() = runTest {
        platform.updateSources = mapOf(SMARTTUBE to REPO)
        val updates = updates()

        updates.setSource(SMARTTUBE, "")

        assertNull(updates.smartTube.source)
        assertEquals(emptyMap<String, String>(), platform.updateSources)
    }

    @Test
    fun `the Home updates itself from this project's releases, without asking after the first time`() = runTest {
        platform.packages = platform.packages + InstalledPackage(HOME, "Free My TV", "0.1.0", installer = null, isSystem = false)
        platform.releases = mapOf(HOME_REPO to ReleaseLookup.Found(release("v0.2.0", "free-my-tv-0.2.0-tv.apk")))
        val updates = updates()

        updates.checkSources()
        val home = updates.state.value.sideloaded.first { it.packageName == HOME }
        assertEquals(HOME_REPO, home.source)
        assertTrue(home.fixedSource)
        assertEquals("0.2.0", home.update?.version)

        updates.update(HOME)

        assertEquals(listOf("/cache/updates/free-my-tv-0.2.0-tv.apk" to true), platform.installed)
    }

    @Test
    fun `the Home's update installs with its dex metadata, so Android compiles it as it installs`() = runTest {
        platform.packages = platform.packages + InstalledPackage(HOME, "Free My TV", "0.1.0", installer = null, isSystem = false)
        platform.releases = mapOf(HOME_REPO to ReleaseLookup.Found(release("v0.2.0", "free-my-tv-0.2.0-tv.apk", "free-my-tv-0.2.0-tv.dm")))
        val updates = updates()
        updates.checkSources()

        updates.update(HOME)

        assertEquals(listOf("/cache/updates/free-my-tv-0.2.0-tv.apk" to true), platform.installed)
        assertEquals(listOf("/cache/updates/free-my-tv-0.2.0-tv.dm"), platform.installedDexMetadata)
        assertTrue(platform.deletedFiles.containsAll(listOf("/cache/updates/free-my-tv-0.2.0-tv.apk", "/cache/updates/free-my-tv-0.2.0-tv.dm")))
    }

    @Test
    fun `when Android refuses the dex metadata, the update installs without it`() = runTest {
        platform.packages = platform.packages + InstalledPackage(HOME, "Free My TV", "0.1.0", installer = null, isSystem = false)
        platform.releases = mapOf(HOME_REPO to ReleaseLookup.Found(release("v0.2.0", "free-my-tv-0.2.0-tv.apk", "free-my-tv-0.2.0-tv.dm")))
        platform.dexMetadataRefused = true
        val updates = updates()
        updates.checkSources()

        updates.update(HOME)

        assertEquals(listOf("/cache/updates/free-my-tv-0.2.0-tv.dm", null), platform.installedDexMetadata)
        assertNull(updates.state.value.sideloaded.first { it.packageName == HOME }.problem)
    }

    @Test
    fun `dex metadata goes with the APK of the same name only`() {
        val release = release("v1.0", "app-tv.apk", "app-tv.dm", "other.dm")

        assertEquals("app-tv.dm", release.dexMetadataFor(release.apkForTv()!!)?.name)
        assertNull(release("v1.0", "app-tv.apk", "other.dm").let { it.dexMetadataFor(it.apkForTv()!!) })
    }

    @Test
    fun `the Home's Update Source can't be changed or removed`() = runTest {
        platform.packages = platform.packages + InstalledPackage(HOME, "Free My TV", "0.1.0", installer = null, isSystem = false)
        val updates = updates()

        updates.setSource(HOME, "someone/else")
        updates.setSource(HOME, "")

        assertEquals(HOME_REPO, updates.state.value.sideloaded.first { it.packageName == HOME }.source)
        assertEquals(emptyMap<String, String>(), platform.updateSources)
    }

    @Test
    fun `versions compare by their numbers`() {
        assertTrue(isNewer("v25.6", "25.4"))
        assertTrue(isNewer("1.10", "1.9"))
        assertTrue(isNewer("2.0.1", "2.0"))
        assertFalse(isNewer("2.0", "2.0.0"))
        assertFalse(isNewer("v1.3.9", "1.3.9"))
        assertFalse(isNewer("nightly", "1.0"))
    }

    @Test
    fun `the APK chosen is the one that runs on a 32-bit TV`() {
        assertEquals("app-tv.apk", release("1", "app-arm64.apk", "app-tv.apk", "app-universal.apk").apkForTv()?.name)
        assertEquals("app-armeabi-v7a.apk", release("1", "app-arm64-v8a.apk", "app-armeabi-v7a.apk", "app-universal.apk").apkForTv()?.name)
        assertEquals("app-universal.apk", release("1", "app-arm64-v8a.apk", "app-x86.apk", "app-universal.apk").apkForTv()?.name)
        assertEquals("app.apk", release("1", "app.apk", "notes.txt").apkForTv()?.name)
        assertNull(release("1", "app-arm64-v8a.apk", "app-x86_64.apk").apkForTv())
    }

    @Test
    fun `repositories are read from what the owner types`() {
        assertEquals(REPO, parseRepository("yuliskov/SmartTube"))
        assertEquals(REPO, parseRepository("github.com/yuliskov/SmartTube"))
        assertEquals(REPO, parseRepository("https://github.com/yuliskov/SmartTube.git"))
        assertEquals(REPO, parseRepository("https://github.com/yuliskov/SmartTube/releases/latest"))
        assertNull(parseRepository("SmartTube"))
    }

    private fun release(tag: String, vararg files: String) = Release(tag, files.map { ReleaseAsset(it, "https://github.com/download/$it", 30_000_000) })

    private companion object {
        const val SMARTTUBE = "com.teamsmart.videomanager.tv"
        const val REPO = "yuliskov/SmartTube"
        const val HOME = "com.freemytv.home"
        const val HOME_REPO = "felipevgomes10/free-my-tv"
    }
}
