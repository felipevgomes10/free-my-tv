package com.freemytv.home.notifications

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** A notification as Android posted it. */
data class TvNotification(
    /** Android's key; an update to a notification keeps its key. */
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Instant,
    /** Ongoing notifications (music playing, apps running in the background) are status, not news. */
    val ongoing: Boolean,
    val clearable: Boolean,
)

data class NotificationsState(
    /** False until Setup grants the Home access to notifications. */
    val hasAccess: Boolean = false,
    /** Current notifications, newest first; the bell shows only while this isn't empty. */
    val notifications: List<TvNotification> = emptyList(),
    /** The Notification Banner on screen, if any. */
    val banner: TvNotification? = null,
)

/**
 * Notifications, in place of the Stock Launcher's panel: a 5-second Notification Banner
 * for each new notification and the Notification Panel listing the current ones.
 * Ongoing notifications are left out of both.
 */
class NotificationCenter(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    private val announced = MutableStateFlow<TvNotification?>(null)

    suspend fun follow() = coroutineScope {
        launch {
            platform.notificationAccess.collect { access -> _state.update { it.copy(hasAccess = access) } }
        }
        launch {
            // Null until the first list arrives: what was already there when the Home started isn't news.
            var known: Set<String>? = null
            platform.activeNotifications.collect { all ->
                val shown = all.filterNot { it.ongoing }.sortedByDescending { it.postedAt }
                // Only a notification the Home hasn't seen yet is announced; updates to one aren't.
                known?.let { seen -> shown.firstOrNull { it.key !in seen }?.let { announced.value = it } }
                known = all.map { it.key }.toSet()
                _state.update { s -> s.copy(notifications = shown, banner = s.banner?.takeIf { b -> shown.any { it.key == b.key } }) }
            }
        }
        launch {
            announced.collectLatest { notification ->
                if (notification == null) return@collectLatest
                _state.update { it.copy(banner = notification) }
                delay(BANNER_FOR)
                _state.update { it.copy(banner = null) }
                announced.value = null
            }
        }
    }

    fun open(notification: TvNotification) = platform.openNotification(notification.key)

    fun clearAll() = platform.clearNotifications()

    companion object {
        val BANNER_FOR = 5.seconds
    }
}
