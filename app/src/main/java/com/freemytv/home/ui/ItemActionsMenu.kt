package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.ui.theme.Tokens

data class MenuItem(
    val label: String,
    val icon: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Where the menu sits relative to the item it acts on. */
enum class MenuPlacement { Beside, Above }

/**
 * The actions menu for one item, anchored to it as in the design. Place it inside the
 * item's layout so the item's bounds are the anchor. It traps focus until dismissed.
 */
@Composable
fun ItemActionsMenu(
    items: List<MenuItem>,
    onDismiss: () -> Unit,
    placement: MenuPlacement = MenuPlacement.Beside,
    title: String? = null,
) {
    val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }
    val first = remember { FocusRequester() }
    Popup(
        popupPositionProvider = remember(placement, gap) { AnchoredPosition(placement, gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(MENU_WIDTH)
                .shadow(30.dp, RoundedCornerShape(12.dp))
                .background(Tokens.Menu, RoundedCornerShape(12.dp))
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (title != null) {
                Text(title, color = Tokens.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp))
            }
            items.forEachIndexed { index, item ->
                MenuRow(item, if (index == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun MenuRow(item: MenuItem, modifier: Modifier) {
    Surface(
        onClick = item.onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (item.destructive) Tokens.Destructive else Tokens.TextPrimary,
            focusedContainerColor = Tokens.TextPrimary,
            focusedContentColor = if (item.destructive) Tokens.DestructiveOnLight else Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(12.dp))
            Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Beside: to the right of the item, top-aligned, flipping left near the screen edge. Above: centered over it. */
private class AnchoredPosition(private val placement: MenuPlacement, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val (x, y) = when (placement) {
            MenuPlacement.Beside -> {
                val right = anchorBounds.right + gap
                val x = if (right + popupContentSize.width <= windowSize.width) right else anchorBounds.left - gap - popupContentSize.width
                x to anchorBounds.top
            }
            MenuPlacement.Above -> anchorBounds.center.x - popupContentSize.width / 2 to anchorBounds.top - gap - popupContentSize.height
        }
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

private val MENU_WIDTH = 180.dp
private val MENU_GAP = 14.dp
