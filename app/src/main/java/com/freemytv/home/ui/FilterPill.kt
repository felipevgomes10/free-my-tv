package com.freemytv.home.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.ui.theme.Tokens

/** One choice of a row of filters, as Explorar's "Filmes" or Continuar assistindo's apps; the chosen one on glass. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.slidingHighlightItem(),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = highlightScale(1.1f),
        colors = highlightColors(
            content = if (selected) Tokens.TextPrimary else Tokens.TextSecondary,
            container = if (selected) Color(0x33FFFFFF) else Color.Transparent,
        ),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}
