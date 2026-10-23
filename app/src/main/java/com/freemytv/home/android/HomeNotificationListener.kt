package com.freemytv.home.android

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.freemytv.home.notifications.TvNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Bound by Android once Setup grants notification access
 * (`cmd notification allow_listener com.freemytv.home/.android.HomeNotificationListener`).
 */
class HomeNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = this
        _connected.value = true
        publish()
    }

    override fun onListenerDisconnected() {
        connected = null
        _connected.value = false
        _notifications.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = publish()

    private fun publish() {
        _notifications.value = runCatching { activeNotifications.orEmpty().mapNotNull { it.toTvNotification() } }.getOrDefault(emptyList())
    }

    private fun StatusBarNotification.toTvNotification(): TvNotification? {
        val n = notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return TvNotification(
            key = key,
            packageName = packageName,
            appLabel = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
                .getOrDefault(packageName),
            title = title,
            text = text,
            postedAt = Instant.ofEpochMilli(postTime),
            ongoing = isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            clearable = isClearable,
        )
    }

    companion object {
        private var connected: HomeNotificationListener? = null
        private val _connected = MutableStateFlow(false)
        private val _notifications = MutableStateFlow<List<TvNotification>>(emptyList())

        val isConnected: StateFlow<Boolean> = _connected.asStateFlow()
        val notifications: StateFlow<List<TvNotification>> = _notifications.asStateFlow()

        fun open(key: String) {
            val listener = connected ?: return
            val sbn = runCatching { listener.activeNotifications.orEmpty() }.getOrDefault(emptyArray()).firstOrNull { it.key == key } ?: return
            val intent = sbn.notification.contentIntent
            if (intent != null) {
                runCatching { intent.send() }
            } else {
                // Nothing to open: bring up the app itself.
                listener.packageManager.getLeanbackLaunchIntentForPackage(sbn.packageName)
                    ?.let { runCatching { listener.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) listener.cancelNotification(key)
        }

        fun clearAll() {
            connected?.cancelAllNotifications()
        }
    }
}
