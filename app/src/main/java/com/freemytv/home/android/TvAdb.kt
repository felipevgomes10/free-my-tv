package com.freemytv.home.android

import android.content.Context
import com.freemytv.home.webremote.AdbLinkResult
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.IOException
import java.net.ConnectException
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

    /** Connects with the Home's key; an unknown key makes Android show its prompt, which this waits for. */
    fun link(): AdbLinkResult {
        if (!isDebuggingOn()) return AdbLinkResult.DebuggingOff
        val result = try {
            Dadb.create(HOST, PORT, keyPair(), REACH_TIMEOUT_MS, PROMPT_TIMEOUT_MS).use { adb ->
                if (adb.shell("echo linked").output.trim() == "linked") AdbLinkResult.Linked else AdbLinkResult.NotAccepted
            }
        } catch (_: ConnectException) {
            AdbLinkResult.DebuggingOff
        } catch (_: Exception) {
            // Refused, unanswered until the timeout, or the Owner dismissed the prompt: adbd says the same.
            AdbLinkResult.NotAccepted
        }
        prefs.edit().putBoolean(KEY_LINKED, result == AdbLinkResult.Linked).apply()
        return result
    }

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
        const val PREFS = "web_remote"
        const val KEY_LINKED = "adb_linked"
    }
}
