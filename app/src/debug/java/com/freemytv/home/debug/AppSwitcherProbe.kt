package com.freemytv.home.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freemytv.home.HomeApplication
import com.freemytv.home.android.KeyFilterService
import kotlinx.coroutines.launch

/** Debug-only window probe for adb tests; shell permission required, absent from release builds. */
class AppSwitcherProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as HomeApplication).scope.launch {
            try {
                pending.resultData = KeyFilterService.showAppSwitcher().name
            } finally { pending.finish() }
        }
    }
}
