package com.freemytv.home.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens from design.pen. The design is drawn at 1920×1080 px, which is 960×540 dp on a 1080p TV. */
object Tokens {
    val Background = Color(0xFF000000)

    /** Translucent "glass" without any live blur (ADR 0004). */
    val Glass = Color(0x1FFFFFFF)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0x8CFFFFFF)
    val Attention = Color(0xFFFFB340)
    val FocusRing = Color(0x59FFFFFF)

    val SafeAreaHorizontal = 48.dp
    val SafeAreaTop = 24.dp
}
