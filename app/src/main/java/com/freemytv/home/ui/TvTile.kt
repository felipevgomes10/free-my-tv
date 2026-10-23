package com.freemytv.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.freemytv.home.ui.theme.Tokens

/** A focusable rounded tile that grows and gains a ring when focused. */
@Composable
fun TvTile(
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    focusedScale: Float,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(width, height),
        shape = CardDefaults.shape(RoundedCornerShape(11.dp)),
        scale = CardDefaults.scale(focusedScale = focusedScale),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(1.dp, Tokens.FocusRing))),
        colors = CardDefaults.colors(containerColor = Tokens.Glass, focusedContainerColor = Tokens.Glass),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
