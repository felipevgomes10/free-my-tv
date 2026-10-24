package com.freemytv.home.android

import android.content.Context
import java.io.File

/**
 * Debug builds only: where GitHub's API is, so the Home's self-update can be tried against a
 * stand-in server on an emulator. Point it at the Mac with
 * `adb shell "run-as com.freemytv.home sh -c 'echo http://10.0.2.2:8000 > files/github_api'"`.
 */
object GitHubEndpoint {

    fun api(context: Context): String =
        File(context.filesDir, FLAG).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() } ?: GITHUB

    private const val FLAG = "github_api"
    private const val GITHUB = "https://api.github.com"
}
