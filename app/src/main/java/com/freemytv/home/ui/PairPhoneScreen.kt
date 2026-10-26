package com.freemytv.home.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.webremote.PairingState

/** "Parear aparelho": where an iPhone opens the FMTV Controller, and the code that pairs it, only while this is open. */
@Composable
fun PairPhoneScreen(state: PairingState, clock: StatusClock, statusWidgets: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .padding(top = Tokens.SafeAreaTop, start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, bottom = 30.dp),
    ) {
        StatusBar(clock, trailing = statusWidgets)
        Column(Modifier.padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.web_remote_pair), color = Tokens.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(stringResource(R.string.pair_phone_subtitle), color = Tokens.TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.size(32.dp))
        val address = state.address
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Step(1, stringResource(R.string.pair_phone_step_open)) {
                    Text(address ?: stringResource(R.string.send_apk_no_network), color = Tokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp)
                }
                Step(2, stringResource(R.string.pair_phone_step_code)) {
                    Text(state.code, color = Tokens.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                }
                Step(3, stringResource(R.string.pair_phone_step_name))
            }
            if (address != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val qr = remember(address) { qrCode("http://$address/") }
                    Box(Modifier.size(QR_CARD).background(Color.White, RoundedCornerShape(14.dp)).padding(18.dp)) {
                        Image(qr.asImageBitmap(), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                    }
                    Text(stringResource(R.string.send_apk_qr_hint), color = Tokens.TextSecondary, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        state.justPaired?.let { Text(stringResource(R.string.pair_phone_paired, it), color = Tokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Tokens.TextSecondary, modifier = Modifier.size(11.dp))
            Text(stringResource(R.string.pair_phone_only_open), color = Tokens.TextSecondary, fontSize = 10.sp)
        }
    }
}
