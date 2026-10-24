package com.freemytv.home.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.freemytv.home.MainActivity
import com.freemytv.home.R
import com.freemytv.home.setup.SetupCheck

/** The Android notification telling the owner a part of Setup was lost; it shows in the Home's own panel. */
object SetupNotifications {

    private const val CHANNEL = "setup"

    fun post(context: Context, check: SetupCheck) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.section_setup), NotificationManager.IMPORTANCE_DEFAULT))
        val (title, text) = when (check) {
            SetupCheck.StockLauncherDisabled -> context.getString(R.string.setup_lost_launcher_title) to context.getString(R.string.setup_lost_launcher_text)
            else -> context.getString(R.string.setup_lost_title) to context.getString(R.string.setup_lost_text, context.getString(labelOf(check)))
        }
        val open = PendingIntent.getActivity(
            context,
            check.ordinal,
            MainActivity.homeIntent(context),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_build)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_BASE + check.ordinal, notification)
    }

    fun cancel(context: Context, check: SetupCheck) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + check.ordinal)
    }

    fun labelOf(check: SetupCheck): Int = when (check) {
        SetupCheck.StockLauncherDisabled -> R.string.check_stock_launcher
        SetupCheck.ProcessAccess -> R.string.check_process_access
        SetupCheck.UsageAccess -> R.string.check_usage_access
        SetupCheck.NotificationAccess -> R.string.check_notification_access
        SetupCheck.KeyFilter -> R.string.check_key_filter
        SetupCheck.InstallApps -> R.string.check_install_apps
        SetupCheck.Explorar -> R.string.check_explorar
        SetupCheck.Screensaver -> R.string.section_screensaver
    }

    private const val NOTIFICATION_BASE = 4200
}
