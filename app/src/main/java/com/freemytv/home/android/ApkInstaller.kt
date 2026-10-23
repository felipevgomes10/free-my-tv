package com.freemytv.home.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.freemytv.home.apkdrop.ApkInfo
import com.freemytv.home.apkdrop.InstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Reads APK files and installs them through Android's package installer. */
class ApkInstaller(private val context: Context) {

    private val packageManager = context.packageManager

    suspend fun inspect(path: String): ApkInfo? = withContext(Dispatchers.IO) {
        val file = File(path)
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val archive = packageManager.getPackageArchiveInfo(path, flags) ?: return@withContext null
        val app = archive.applicationInfo ?: return@withContext null
        // Without these, an archive's label and icon can't be loaded.
        app.sourceDir = path
        app.publicSourceDir = path
        val installed = runCatching { packageManager.getPackageInfo(archive.packageName, flags) }.getOrNull()
        ApkInfo(
            packageName = archive.packageName,
            label = app.loadLabel(packageManager).toString(),
            versionName = archive.versionName ?: archive.longVersionCode.toString(),
            sizeBytes = file.length(),
            installedVersion = installed?.let { it.versionName ?: it.longVersionCode.toString() },
            sameSigner = installed?.let { sameSigner(archive, it) },
        )
    }

    private fun sameSigner(archive: PackageInfo, installed: PackageInfo): Boolean? {
        val theirs = archive.signers() ?: return null
        val ours = installed.signers() ?: return null
        return theirs == ours
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): Set<String>? =
        (signingInfo?.apkContentsSigners ?: signatures)?.map { it.toCharsString() }?.toSet()?.takeIf { it.isNotEmpty() }

    suspend fun install(path: String, silent: Boolean): InstallResult {
        val installer = packageManager.packageInstaller
        val sessionId = withContext(Dispatchers.IO) {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                val file = File(path)
                session.openWrite("base.apk", 0, file.length()).use { sink ->
                    file.inputStream().use { it.copyTo(sink) }
                    session.fsync(sink)
                }
            }
            id
        }
        val action = "$ACTION_PREFIX$sessionId"
        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        // Android's confirmation: show it and keep waiting for the answer.
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { confirm ->
                                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        else -> {
                            runCatching { context.unregisterReceiver(this) }
                            val result = when (status) {
                                PackageInstaller.STATUS_SUCCESS -> InstallResult.Installed
                                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Cancelled
                                else -> InstallResult.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                            }
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(action))
            }
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            runCatching { installer.openSession(sessionId).use { it.commit(callback.intentSender) } }
                .onFailure { e -> if (continuation.isActive) continuation.resume(InstallResult.Failed(e.message)) }
        }
    }

    private companion object {
        const val ACTION_PREFIX = "com.freemytv.home.INSTALL_RESULT."
    }
}
