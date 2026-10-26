package com.freemytv.home.webremote

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/** The page's side of one WebSocket, as the bridge sees it. */
interface RemotePeer {
    fun send(text: String)
    fun close()
}

/**
 * The bridge between FMTV Controller pages and the TV (free-my-tv-remote-controller's
 * protocol/README.md and ADR 0005): each connection says hello with a Paired Phone's credential,
 * learns whether the bridge is ready, and sends keys as intentions, which become Android key codes
 * on the TV's own ADB, one at a time, in the order they came. Nothing is queued for a phone that
 * isn't connected.
 */
class WebRemoteBridge(private val platform: TvPlatform, private val phones: PairedPhones) {

    private val sessions = CopyOnWriteArraySet<Session>()
    /** Keys from every phone go to the TV one at a time, in the order they came. */
    private val keys = Mutex()

    fun connect(peer: RemotePeer): Session = Session(peer).also(sessions::add)

    /** A phone was revoked: its open connections learn it and close. */
    suspend fun revoked(credentialSha256: String) {
        sessions.filter { it.phone?.credentialSha256 == credentialSha256 }.forEach { it.revoke() }
    }

    /** Linking changed: every connection hears the bridge's new state. */
    suspend fun linkChanged() {
        val state = readiness()
        sessions.filter { it.phone != null }.forEach { it.tell(state) }
    }

    private suspend fun readiness() = if (platform.isNetworkDebuggingOn() && platform.isAdbLinked()) "ready" else "not-linked"

    inner class Session internal constructor(private val peer: RemotePeer) {
        @Volatile internal var phone: PairedPhone? = null
            private set
        @Volatile private var ready = false

        suspend fun onText(text: String) {
            val message = try {
                JSONObject(text)
            } catch (_: JSONException) {
                return
            }
            when (message.optString("type")) {
                "hello" -> hello(message)
                "key" -> key(message)
            }
        }

        fun onClosed() {
            sessions.remove(this)
        }

        private suspend fun hello(message: JSONObject) {
            if (message.optInt("protocol") != PROTOCOL_VERSION) {
                peer.send(JSONObject().put("type", "reload").put("protocol", PROTOCOL_VERSION).toString())
                return close()
            }
            val known = phones.find(message.optString("credential"))
            if (known == null) {
                peer.send(state("revoked"))
                return close()
            }
            phone = known
            tell(readiness())
        }

        private suspend fun key(message: JSONObject) {
            if (!ready) return
            val id = message.optInt("id")
            val code = KEY_CODES[message.optString("key")] ?: return
            val sent = when (message.optString("action")) {
                "press" -> keys.withLock { platform.sendAdbKey(code, longPress = false) }
                // cmd input on Android 12 can't keep a key down: a hold is a long press, and its end has nothing left to do.
                "hold-start" -> keys.withLock { platform.sendAdbKey(code, longPress = true) }
                "hold-end" -> true
                else -> return
            }
            if (sent) {
                peer.send(JSONObject().put("type", "ack").put("id", id).toString())
            } else {
                // The TV refused the Home's key, or network debugging went off.
                linkChanged()
            }
        }

        internal suspend fun tell(state: String) {
            ready = state == "ready"
            peer.send(state(state))
        }

        internal suspend fun revoke() {
            ready = false
            peer.send(state("revoked"))
            close()
        }

        private suspend fun state(state: String) =
            JSONObject().put("type", "state").put("state", state).put("tvName", platform.tvName() ?: "TV").toString()

        private fun close() {
            ready = false
            sessions.remove(this)
            peer.close()
        }
    }

    companion object {
        const val PROTOCOL_VERSION = 1

        /** The protocol's intentions as Android key codes. */
        val KEY_CODES = mapOf(
            "up" to 19,
            "down" to 20,
            "left" to 21,
            "right" to 22,
            "ok" to 23,
            "back" to 4,
            "home" to 3,
            "play-pause" to 85,
            "volume-up" to 24,
            "volume-down" to 25,
            "mute" to 164,
            "power" to 26,
            // KEYCODE_WAKEUP: turns the TV on, never off.
            "wake" to 224,
        )
    }
}
