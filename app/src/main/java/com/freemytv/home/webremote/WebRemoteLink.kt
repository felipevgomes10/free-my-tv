package com.freemytv.home.webremote

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What Linking stands at, as Settings → FMTV Controller tells the Owner. */
enum class LinkState {
    /** Not known yet: Settings hasn't looked. */
    Checking,
    Linked,
    /** Never linked, or the TV forgot the Home's key. */
    NotLinked,
    /** Waiting for the Owner to accept Android's "Allow debugging?" prompt. */
    Linking,
    /** The prompt was refused or left unanswered; Android doesn't tell the two apart. */
    NotAccepted,
    /** Nothing answers at the TV's ADB: network debugging is off, so there's no prompt to accept. */
    DebuggingOff,
}

enum class AdbLinkResult { Linked, NotAccepted, DebuggingOff }

/**
 * Links the Home to the TV's own ADB, through which the Web Remote's keys reach every app
 * (free-my-tv-remote-controller's ADR 0005). Linking is the TV's own "Allow debugging?"
 * prompt, accepted once with the Remote; this only asks for it and remembers the outcome.
 */
class WebRemoteLink(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(LinkState.Checking)
    val state: StateFlow<LinkState> = _state

    /** Looks without asking: never shows the prompt. */
    suspend fun refresh() {
        if (_state.value == LinkState.Linking) return
        _state.value = when {
            !platform.isNetworkDebuggingOn() -> LinkState.DebuggingOff
            platform.isAdbLinked() -> LinkState.Linked
            _state.value == LinkState.NotAccepted -> LinkState.NotAccepted
            else -> LinkState.NotLinked
        }
    }

    /** Asks the TV, which shows its prompt, and waits for the Owner's answer. */
    suspend fun link() {
        if (_state.value == LinkState.Linking) return
        _state.value = LinkState.Linking
        _state.value = when (platform.linkAdb()) {
            AdbLinkResult.Linked -> LinkState.Linked
            AdbLinkResult.NotAccepted -> LinkState.NotAccepted
            AdbLinkResult.DebuggingOff -> LinkState.DebuggingOff
        }
    }
}
