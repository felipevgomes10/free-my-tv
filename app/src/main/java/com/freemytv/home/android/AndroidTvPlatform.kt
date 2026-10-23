package com.freemytv.home.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.Dispatchers
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

    override fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun loadPinnedApps(): List<String>? = withContext(Dispatchers.IO) {
        prefs.getString(PINNED_APPS, null)?.let { saved -> saved.split(SEPARATOR).filter { it.isNotEmpty() } }
    }

    override suspend fun savePinnedApps(packageNames: List<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PINNED_APPS, packageNames.joinToString(SEPARATOR)).apply()
    }

    override fun now(): ZonedDateTime = ZonedDateTime.now()

    private companion object {
        const val PINNED_APPS = "pinned_apps"
        const val SEPARATOR = ","
    }
}
