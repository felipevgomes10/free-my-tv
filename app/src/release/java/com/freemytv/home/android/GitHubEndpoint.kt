package com.freemytv.home.android

import android.content.Context

/** Where GitHub's API is; debug builds can point it elsewhere. */
object GitHubEndpoint {

    @Suppress("UNUSED_PARAMETER")
    fun api(context: Context): String = "https://api.github.com"
}
