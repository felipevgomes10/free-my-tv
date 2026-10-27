package com.freemytv.home.android

import android.content.Context
import com.freemytv.home.webremote.AdbLinkResult
import dadb.AdbAuthException
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The Home as a client of the TV's own ADB daemon on 127.0.0.1:5555, the network debugging
 * Setup needs anyway (free-my-tv-remote-controller's ADR 0005). Its key pair is made once and
 * kept in the Home's private storage; the TV trusts it once the Owner accepts Android's
 * "Allow debugging?" prompt with "always allow".
 */
internal class TvAdb(private val context: Context) {

    private val dir get() = File(context.filesDir, "adb")
    private val privateKey get() = File(dir, "adbkey")
    private val publicKey get() = File(dir, "adbkey.pub")
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDebuggingOn(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(HOST, PORT), REACH_TIMEOUT_MS) }
        true
    } catch (_: IOException) {
        false
    }

    fun isLinked(): Boolean = prefs.getBoolean(KEY_LINKED, false)

    /** The one connection to the TV's ADB, opened by Linking and kept for keys. */
    private var adb: Dadb? = null

    /** Connects with the Home's key; an unknown key makes Android show its prompt, which this waits for. */
    @Synchronized
    fun link(): AdbLinkResult {
        if (!isDebuggingOn()) return AdbLinkResult.DebuggingOff
        close()
        val result = try {
            val connection = Dadb.create(HOST, PORT, keyPair(), REACH_TIMEOUT_MS, PROMPT_TIMEOUT_MS, keepAlive = true)
            if (connection.shell("echo linked").output.trim() == "linked") {
                adb = connection
                AdbLinkResult.Linked
            } else {
                connection.close()
                AdbLinkResult.NotAccepted
            }
        } catch (_: Exception) {
            // Refused, unanswered until the timeout, or the Owner dismissed the prompt: adbd says the same.
            AdbLinkResult.NotAccepted
        }
        prefs.edit().putBoolean(KEY_LINKED, result == AdbLinkResult.Linked).apply()
        return result
    }

    /** One key as `cmd input keyevent`; the connection is reopened once when it broke since the last key. */
    @Synchronized
    fun sendKey(keyCode: Int, longPress: Boolean, blocked: () -> Boolean = { false }): Boolean {
        // Check under the same monitor as awaitKeys: a key waiting behind another key
        // must not slip into Settings after the close has drained the current one.
        if (blocked()) return true
        val command = "cmd input keyevent ${if (longPress) "--longpress " else ""}$keyCode"
        repeat(2) { attempt ->
            try {
                val connection = adb ?: Dadb.create(HOST, PORT, keyPair(), REACH_TIMEOUT_MS, KEY_TIMEOUT_MS, keepAlive = true).also { adb = it }
                connection.shell(command)
                return true
            } catch (_: AdbAuthException) {
                // The TV no longer trusts the Home's key: Linking is lost, and only the Owner can redo it.
                prefs.edit().putBoolean(KEY_LINKED, false).apply()
                close()
                return false
            } catch (_: Exception) {
                close()
                if (attempt == 1) return false
            }
        }
        return false
    }

    private fun close() {
        runCatching { adb?.close() }
        adb = null
    }

    /** Force stop drains an already-started key before it opens Settings. New keys are dropped while busy. */
    @Synchronized
    fun awaitKeys() = Unit

    private fun keyPair(): AdbKeyPair {
        if (!privateKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
            // The comment is what Android lists under the debugging authorisations.
            publicKey.writeText(publicKey.readText().replace(" unknown@unknown", " FMTV Controller@Free My TV Home"))
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val REACH_TIMEOUT_MS = 1_000
        /** Long enough for the Owner to find the Remote and accept the prompt. */
        const val PROMPT_TIMEOUT_MS = 60_000
        const val KEY_TIMEOUT_MS = 5_000
        const val PREFS = "web_remote"
        const val KEY_LINKED = "adb_linked"
    }
}
