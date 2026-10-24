package com.freemytv.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.ui.theme.Tokens

/** The Home's sections, in the order the Home Navigation shows them. */
enum class HomeSection { Inicio, Explorar, TodosOsApps }

/**
 * The Home Navigation: Início, Explorar and Todos os apps under the Status Bar, the current
 * section on a glass pill and the white focus pill sliding between them. [current] gets
 * [currentFocus] so a section can land focus on it.
 */
@Composable
fun HomeNavigation(
    current: HomeSection,
    onSelect: (HomeSection) -> Unit,
    modifier: Modifier = Modifier,
    currentFocus: FocusRequester? = null,
) {
    SlidingHighlightGroup(rememberSlidingHighlight(), CircleShape) { group ->
        Row(modifier.padding(start = Tokens.SafeAreaHorizontal).then(group), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSection.entries.forEach { section ->
                val selected = section == current
                Surface(
                    onClick = { onSelect(section) },
                    modifier = Modifier.slidingHighlightItem().then(if (selected && currentFocus != null) Modifier.focusRequester(currentFocus) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = highlightColors(
                        content = if (selected) Tokens.TextPrimary else Tokens.TextSecondary,
                        container = if (selected) Color(0x33FFFFFF) else Color.Transparent,
                    ),
                ) {
                    Text(
                        stringResource(
                            when (section) {
                                HomeSection.Inicio -> R.string.section_home
                                HomeSection.Explorar -> R.string.section_explorar
                                HomeSection.TodosOsApps -> R.string.all_apps
                            },
                        ),
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
