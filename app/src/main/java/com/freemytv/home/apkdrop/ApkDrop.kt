package com.freemytv.home.apkdrop

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** What an APK file says about itself, next to what is installed. */
data class ApkInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val sizeBytes: Long,
    /** The installed version of the same app, or null when it isn't installed. */
    val installedVersion: String? = null,
    /** Whether the file is signed like the installed app; null when not installed or unknown. */
    val sameSigner: Boolean? = null,
)

enum class ApkKind { New, Update, SignerMismatch }

val ApkInfo.kind: ApkKind
    get() = when {
        installedVersion == null -> ApkKind.New
        sameSigner == false -> ApkKind.SignerMismatch
        else -> ApkKind.Update
    }

/** The APK Drop page's answer to an upload, decided before the file is accepted. */
enum class UploadVerdict { Accept, WrongCode, TooBig, Busy }

sealed interface InstallResult {
    data object Installed : InstallResult
    data object Cancelled : InstallResult
    data class Failed(val message: String?) : InstallResult
}

/** What happened last, shown under the instructions. */
sealed interface ApkDropMessage {
    data class Installed(val label: String) : ApkDropMessage
    data object Cancelled : ApkDropMessage
    data object NotAnApk : ApkDropMessage
    data object TooBig : ApkDropMessage
    data class InstallFailed(val label: String) : ApkDropMessage
}

data class ReceivedApk(val path: String, val info: ApkInfo)

data class ApkDropState(
    /** The page's address on the home network, or null when the TV has none. */
    val address: String? = null,
    val code: String = "",
    /** A file waiting for the owner to install or cancel it. */
    val received: ReceivedApk? = null,
    val installing: Boolean = false,
    val message: ApkDropMessage? = null,
)

/**
 * APK Drop: while "Enviar APK" is open the TV serves a page on the home network where a
 * phone or computer sends an APK, guarded by a short code shown on the TV. Android's own
 * confirmation is still the last word on every install.
 */
class ApkDrop(private val platform: TvPlatform, private val random: Random = Random.Default) {

    private val _state = MutableStateFlow(ApkDropState())
    val state: StateFlow<ApkDropState> = _state.asStateFlow()

    // Read by the page's server thread.
    @Volatile private var code = ""
    @Volatile private var failures = 0
    @Volatile private var freeBytes = 0L

    /** Serves the page for as long as it runs; cancel it when the owner leaves "Enviar APK". */
    suspend fun open() {
        code = newCode()
        failures = 0
        freeBytes = platform.storageUsage().freeBytes
        val ip = platform.localAddress()
        _state.value = ApkDropState(address = ip?.let { "$it:$PORT" }, code = code)
        try {
            platform.receiveApks(PORT, ::verdict).collect { path -> receive(path) }
        } finally {
            withContext(NonCancellable) {
                _state.value.received?.let { platform.deleteFile(it.path) }
                _state.value = ApkDropState()
            }
        }
    }

    /** Asks Android to install the received file; the file is deleted whatever happens. */
    suspend fun install() {
        val received = _state.value.received ?: return
        _state.update { it.copy(installing = true, message = null) }
        val result = platform.installApk(received.path)
        platform.deleteFile(received.path)
        freeBytes = platform.storageUsage().freeBytes
        _state.update {
            it.copy(
                received = null,
                installing = false,
                message = when (result) {
                    InstallResult.Installed -> ApkDropMessage.Installed(received.info.label)
                    InstallResult.Cancelled -> ApkDropMessage.Cancelled
                    is InstallResult.Failed -> ApkDropMessage.InstallFailed(received.info.label)
                },
            )
        }
    }

    suspend fun cancel() {
        val received = _state.value.received ?: return
        platform.deleteFile(received.path)
        _state.update { it.copy(received = null, message = ApkDropMessage.Cancelled) }
    }

    private fun verdict(sentCode: String, sizeBytes: Long): UploadVerdict {
        if (sentCode.trim() != code) {
            // Guessing gets a fresh code after a few tries.
            if (++failures >= MAX_FAILURES) {
                failures = 0
                code = newCode()
                _state.update { it.copy(code = code) }
            }
            return UploadVerdict.WrongCode
        }
        if (_state.value.received != null || _state.value.installing) return UploadVerdict.Busy
        if (sizeBytes * 2 + SPARE_BYTES > freeBytes) {
            _state.update { it.copy(message = ApkDropMessage.TooBig) }
            return UploadVerdict.TooBig
        }
        return UploadVerdict.Accept
    }

    private suspend fun receive(path: String) {
        val info = platform.inspectApk(path)
        if (info == null) {
            platform.deleteFile(path)
            _state.update { it.copy(message = ApkDropMessage.NotAnApk) }
            return
        }
        _state.update { it.copy(received = ReceivedApk(path, info), message = null) }
    }

    private fun newCode() = random.nextInt(0, 10_000).toString().padStart(4, '0')

    companion object {
        const val PORT = 8765
        private const val MAX_FAILURES = 3
        /** Room left over once the file has landed and been installed, which takes about its size again. */
        private const val SPARE_BYTES = 100L * 1024 * 1024
    }
}
