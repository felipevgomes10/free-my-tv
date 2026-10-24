package com.freemytv.home.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freemytv.home.MainActivity

/**
 * After the Home updates itself its process ends, and whatever was behind it would come to
 * the front. Android tells the updated app first: the Home puts itself back.
 */
class SelfUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
