package com.freemytv.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.ui.theme.Tokens

/** A glass pill that turns white when focused, as in All Apps' "Instalar app"; in a group, the group's pill slides to it. */
@Composable
fun PillButton(label: String, icon: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = Modifier.slidingHighlightItem().then(modifier),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = highlightScale(1.1f),
        colors = highlightColors(container = Tokens.Glass),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(13.dp)) }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
