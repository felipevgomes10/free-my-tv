package com.freemytv.home.android

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.PackageEvent
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class AndroidTvPlatform(private val context: Context) : TvPlatform {

    private val packageManager = context.packageManager
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    override suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    hasBanner = info.banner != 0 || info.applicationInfo.banner != 0,
                )
            }
    }

    // LauncherApps reports an app update as a change, never as a removal, so updates don't unpin apps.
    override val packageEvents: Flow<PackageEvent> = callbackFlow {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Installed(packageName))
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Changed(packageName))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Removed(packageName))
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { trySend(PackageEvent.Changed(it)) }
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { trySend(PackageEvent.Changed(it)) }
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    override fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        start(intent)
    }

    override fun requestUninstall(packageName: String) {
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
    }

    override fun openAppInfo(packageName: String) {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun openPlayStore() = launch(PLAY_STORE)

    override suspend fun loadPinnedApps(): List<String>? = withContext(Dispatchers.IO) {
        prefs.getString(PINNED_APPS, null)?.let { saved -> saved.split(SEPARATOR).filter { it.isNotEmpty() } }
    }

    override suspend fun savePinnedApps(packageNames: List<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PINNED_APPS, packageNames.joinToString(SEPARATOR)).apply()
    }

    override fun now(): ZonedDateTime = ZonedDateTime.now()

    private fun start(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val PINNED_APPS = "pinned_apps"
        const val SEPARATOR = ","
        const val PLAY_STORE = "com.android.vending"
    }
}
