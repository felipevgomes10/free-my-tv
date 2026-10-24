package com.freemytv.home.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.freemytv.home.screensaver.ScreensaverPreferences

/** Native dream selection and power settings; Setup backs up every original value. */
object AndroidScreensaver {
    const val COMPONENT = "com.freemytv.home/.android.WeatherDreamService"
    private const val BACKUP_PREFIX = "freemytv_screensaver_"
    private fun prefs(context: Context) = context.getSharedPreferences("screensaver", Context.MODE_PRIVATE)

    fun preferences(context: Context) = ScreensaverPreferences(
        prefs(context).getBoolean("enabled", false),
        prefs(context).getInt("minutes", 5).takeIf { it in ScreensaverPreferences.MINUTES } ?: 5,
    )

    fun save(context: Context, value: ScreensaverPreferences) {
        prefs(context).edit().putBoolean("enabled", value.enabled).putInt("minutes", value.afterMinutes).commit()
    }

    fun ready(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
            Settings.System.canWrite(context) &&
            listOf("screen_off_timeout", "sleep_timeout", "screensaver_activate_on_sleep", "screensaver_enabled", "screensaver_components", "stay_on_while_plugged_in").all {
                Settings.Secure.getString(context.contentResolver, BACKUP_PREFIX + it) != null
            } && Settings.Secure.getString(context.contentResolver, "screensaver_components") == COMPONENT

    fun configure(context: Context, value: ScreensaverPreferences): Boolean {
        if (!ready(context)) return false
        return try {
            val resolver = context.contentResolver
            Settings.Secure.putInt(resolver, "screensaver_enabled", 0)
            if (value.enabled) {
                Settings.Global.putInt(resolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0)
                Settings.System.putLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, value.afterMinutes * 60_000L)
                Settings.Secure.putLong(resolver, "sleep_timeout", -1)
                Settings.Secure.putInt(resolver, "screensaver_activate_on_sleep", 1)
                Settings.Secure.putInt(resolver, "screensaver_enabled", 1)
            } else {
                restore(context, "system", "screen_off_timeout")
                restore(context, "secure", "sleep_timeout")
                restore(context, "secure", "screensaver_activate_on_sleep")
                restore(context, "global", "stay_on_while_plugged_in")
            }
            true
        } catch (_: SecurityException) { false }
    }

    private fun restore(context: Context, namespace: String, key: String) {
        val value = Settings.Secure.getString(context.contentResolver, BACKUP_PREFIX + key)?.takeUnless { it == "__unset__" }
        when (namespace) {
            "system" -> Settings.System.putString(context.contentResolver, key, value)
            "global" -> Settings.Global.putString(context.contentResolver, key, value)
            else -> Settings.Secure.putString(context.contentResolver, key, value)
        }
    }
}

/** Only Setup/Restore's shell or the Home may send these broadcasts. */
class ScreensaverSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.freemytv.home.action.APPLY_SCREENSAVER" -> AndroidScreensaver.configure(context, AndroidScreensaver.preferences(context))
            "com.freemytv.home.action.RESET_SCREENSAVER" -> {
                AndroidScreensaver.save(context, AndroidScreensaver.preferences(context).copy(enabled = false))
                WeatherDreamService.finishCurrent()
            }
        }
    }
}
