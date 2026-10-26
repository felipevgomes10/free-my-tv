package com.freemytv.home.webremote

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlin.random.Random

/** The bridge's port on the home network; APK Drop's is 8765. */
const val WEB_REMOTE_PORT = 8766

/** A phone that completed Pairing, known by the name its Household Member gave it. Only its credential's hash is kept. */
data class PairedPhone(val name: String, val credentialSha256: String, val pairedAt: Instant)

/** The bridge's answer to `POST /pair` (free-my-tv-remote-controller's protocol/README.md). */
sealed interface PairingAnswer {
    data class Paired(val credential: String) : PairingAnswer
    data object WrongCode : PairingAnswer
    /** Parear aparelho isn't open on the TV. */
    data object Closed : PairingAnswer
    /** Too many wrong codes: the TV shows a new one. */
    data object TooManyTries : PairingAnswer
}

data class PairingState(
    /** True while Parear aparelho is open: the only time Pairing is possible. */
    val open: Boolean = false,
    /** The bridge's address on the home network, or null when the TV has none. */
    val address: String? = null,
    val code: String = "",
    /** The phone just paired, for the TV to confirm by name. */
    val justPaired: String? = null,
)

/** The Paired Phones, kept by the Home; revoking one closes its open connection. */
class PairedPhones(private val platform: TvPlatform) {

    private val _phones = MutableStateFlow<List<PairedPhone>>(emptyList())
    val phones: StateFlow<List<PairedPhone>> = _phones.asStateFlow()

    private val _revoked = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** The credential hash of each phone revoked, for the bridge to close its connection. */
    val revoked: SharedFlow<String> = _revoked

    suspend fun load() {
        _phones.value = platform.loadPairedPhones()
    }

    fun find(credential: String): PairedPhone? = sha256(credential).let { hash -> _phones.value.firstOrNull { it.credentialSha256 == hash } }

    suspend fun add(phone: PairedPhone) {
        _phones.update { it + phone }
        platform.savePairedPhones(_phones.value)
    }

    suspend fun revoke(phone: PairedPhone) {
        _phones.update { phones -> phones.filterNot { it.credentialSha256 == phone.credentialSha256 } }
        platform.savePairedPhones(_phones.value)
        _revoked.emit(phone.credentialSha256)
    }
}

/**
 * Pairing, from the TV's side: a session that lives only while Parear aparelho is open, with a
 * short code that changes after a few wrong tries. A right code and a name make a Paired Phone,
 * and the phone gets a credential it sends with every connection from then on.
 */
class WebRemotePairing(
    private val platform: TvPlatform,
    private val phones: PairedPhones,
    private val random: Random = Random.Default,
) {

    private val _state = MutableStateFlow(PairingState())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var wrongTries = 0

    suspend fun open() {
        wrongTries = 0
        _state.value = PairingState(open = true, address = platform.localAddress()?.let { "$it:$WEB_REMOTE_PORT" }, code = newCode())
    }

    fun close() {
        _state.value = PairingState()
    }

    @Synchronized
    fun pairNow(code: String, name: String, now: Instant): Pair<PairingAnswer, PairedPhone?> {
        val state = _state.value
        if (!state.open) return PairingAnswer.Closed to null
        if (code != state.code) {
            wrongTries++
            if (wrongTries < MAX_WRONG_TRIES) return PairingAnswer.WrongCode to null
            wrongTries = 0
            _state.update { it.copy(code = newCode()) }
            return PairingAnswer.TooManyTries to null
        }
        val credential = Base64.getUrlEncoder().withoutPadding().encodeToString(random.nextBytes(16))
        val phone = PairedPhone(name.trim().take(MAX_NAME_LENGTH).ifEmpty { "iPhone" }, sha256(credential), now)
        wrongTries = 0
        // A code is good for one phone: the next one gets a new code.
        _state.update { it.copy(code = newCode(), justPaired = phone.name) }
        return PairingAnswer.Paired(credential) to phone
    }

    suspend fun pair(code: String, name: String): PairingAnswer {
        val (answer, phone) = pairNow(code, name, platform.now().toInstant())
        phone?.let { phones.add(it) }
        return answer
    }

    private fun newCode(): String = random.nextInt(0, 10_000).toString().padStart(4, '0')

    private companion object {
        const val MAX_WRONG_TRIES = 3
        const val MAX_NAME_LENGTH = 40
    }
}

internal fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
