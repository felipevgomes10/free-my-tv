package com.freemytv.home.android

import android.content.Context
import com.freemytv.home.inputs.TvInputSource

/** Release builds always use the TV's real inputs. */
object DemoInputs {
    fun sources(context: Context): List<TvInputSource>? = null
}
