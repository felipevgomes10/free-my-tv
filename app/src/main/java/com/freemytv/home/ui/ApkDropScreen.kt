package com.freemytv.home.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.apkdrop.ApkDropMessage
import com.freemytv.home.apkdrop.ApkDropState
import com.freemytv.home.apkdrop.ApkKind
import com.freemytv.home.apkdrop.ReceivedApk
import com.freemytv.home.apkdrop.kind
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale

/** "Enviar APK": where to send an APK from a phone, and the APK once it arrives. */
@Composable
fun ApkDropScreen(
    state: ApkDropState,
    clock: StatusClock,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    statusWidgets: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .padding(top = Tokens.SafeAreaTop, start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, bottom = 30.dp),
    ) {
        StatusBar(clock, trailing = statusWidgets)
        Column(Modifier.padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.send_apk), color = Tokens.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(stringResource(R.string.send_apk_subtitle), color = Tokens.TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.size(32.dp))
        val received = state.received
        if (received != null) {
            ReceivedCard(received, state.installing, onInstall, onCancel)
        } else {
            Waiting(state)
        }
        Spacer(Modifier.weight(1f))
        state.message?.let { MessageLine(it) }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Tokens.TextSecondary, modifier = Modifier.size(11.dp))
            Text(stringResource(R.string.send_apk_only_open), color = Tokens.TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Waiting(state: ApkDropState) {
    val address = state.address
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Step(1, stringResource(R.string.send_apk_step_open)) {
                Text(address ?: stringResource(R.string.send_apk_no_network), color = Tokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp)
            }
            Step(2, stringResource(R.string.send_apk_step_code)) {
                Text(state.code, color = Tokens.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            }
            Step(3, stringResource(R.string.send_apk_step_send))
        }
        if (address != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The link carries the code, so scanning it fills the page's code field.
                val qr = remember(address, state.code) { qrCode("http://$address/?code=${state.code}") }
                Box(Modifier.size(QR_CARD).background(Color.White, RoundedCornerShape(14.dp)).padding(18.dp)) {
                    Image(qr.asImageBitmap(), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                }
                Text(stringResource(R.string.send_apk_qr_hint), color = Tokens.TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

/** A numbered instruction; a one-line instruction sits centered on its number. */
@Composable
private fun Step(number: Int, label: String, value: (@Composable () -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = if (value == null) Alignment.CenterVertically else Alignment.Top) {
        Box(Modifier.size(28.dp).background(Tokens.Glass, CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Tokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Tokens.TextSecondary, fontSize = 12.sp)
            value?.invoke()
        }
    }
}

@Composable
private fun ReceivedCard(received: ReceivedApk, installing: Boolean, onInstall: () -> Unit, onCancel: () -> Unit) {
    val info = received.info
    val install = remember { FocusRequester() }
    LaunchedEffect(received.path) { runCatching { install.requestFocus() } }
    Column(
        Modifier.width(550.dp).background(Tokens.Glass, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).background(Tokens.Glass, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(26.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${info.label}  ${info.versionName}", color = Tokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp)
                when (info.kind) {
                    ApkKind.New -> Text(stringResource(R.string.apk_new), color = Tokens.TextSecondary, fontSize = 11.sp)
                    ApkKind.Update -> Text(stringResource(R.string.apk_update, info.installedVersion.orEmpty()), color = Tokens.TextSecondary, fontSize = 11.sp)
                    ApkKind.SignerMismatch -> Text(stringResource(R.string.apk_other_signer, info.installedVersion.orEmpty()), color = Tokens.Attention, fontSize = 11.sp)
                }
                Text("${info.packageName} · ${megabytes(info.sizeBytes)}", color = Tokens.TextSecondary, fontSize = 10.sp)
            }
        }
        SlidingHighlightGroup(rememberSlidingHighlight(), CircleShape) { group ->
            Row(group, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    stringResource(if (installing) R.string.installing else R.string.install),
                    R.drawable.ic_download,
                    onClick = { if (!installing) onInstall() },
                    modifier = Modifier.focusRequester(install),
                )
                if (!installing) PillButton(stringResource(R.string.cancel), null, onCancel)
            }
        }
    }
}

@Composable
private fun MessageLine(message: ApkDropMessage) {
    val (text, attention) = when (message) {
        is ApkDropMessage.Installed -> stringResource(R.string.apk_installed, message.label) to false
        ApkDropMessage.Cancelled -> stringResource(R.string.apk_cancelled) to false
        ApkDropMessage.NotAnApk -> stringResource(R.string.apk_not_an_apk) to true
        ApkDropMessage.TooBig -> stringResource(R.string.apk_too_big) to true
        is ApkDropMessage.InstallFailed -> stringResource(R.string.apk_install_failed, message.label) to true
    }
    Text(text, color = if (attention) Tokens.Attention else Tokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

private fun megabytes(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return String.format(Locale.forLanguageTag("pt-BR"), if (mb < 10) "%.1f MB" else "%.0f MB", mb)
}

private fun qrCode(text: String): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    // One pixel per module, scaled up without smoothing.
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

private val QR_CARD = 210.dp
