package com.freemytv.home.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.security.MessageDigest

/** Explorar's TMDB read token, kept in the Home's private storage only (ADR 0008). */
object TmdbToken {

    fun read(context: Context): String? = prefs(context).getString(KEY, null)

    fun write(context: Context, token: String?) {
        prefs(context).edit().apply { if (token.isNullOrBlank()) remove(KEY) else putString(KEY, token.trim()) }.commit()
    }

    fun sha256(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "secrets"
    private const val KEY = "tmdb_token"
}

/**
 * Where Setup hands over the TMDB token. The manifest lets only a sender holding
 * WRITE_SECURE_SETTINGS reach it: the ADB shell, or the Home itself. Asked for its state, it
 * answers with the token's SHA-256, so Setup can tell whether to send it again.
 */
class TmdbTokenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET -> TmdbToken.write(context, intent.getStringExtra(EXTRA_TOKEN))
            ACTION_FORGET -> TmdbToken.write(context, null)
            ACTION_STATE -> resultData = TmdbToken.read(context)?.let(TmdbToken::sha256)
        }
    }

    private companion object {
        const val ACTION_SET = "com.freemytv.home.action.SET_TMDB_TOKEN"
        const val ACTION_FORGET = "com.freemytv.home.action.FORGET_TMDB_TOKEN"
        const val ACTION_STATE = "com.freemytv.home.action.TMDB_TOKEN_STATE"
        const val EXTRA_TOKEN = "token"
    }
}
