package com.freemytv.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.weather.Condition
import com.freemytv.home.weather.WeatherState

/** A Widget in its compact form on the Status Bar: a glass pill that opens the Widget's detail. */
@Composable
fun WidgetPill(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.slidingHighlightItem(),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = highlightScale(1.05f),
        colors = highlightColors(container = Tokens.Glass),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The Weather Widget's compact form: icon, temperature, and today's high and low. Nothing for a Hidden Widget. */
@Composable
fun WeatherPill(state: WeatherState, onClick: () -> Unit) {
    if (!state.visible) return
    val report = state.report
    WidgetPill(onClick) {
        val color = LocalContentColor.current
        WeatherIcon(report?.condition ?: Condition.Cloudy, report?.isDay ?: true, 14.dp, color)
        Text(report?.let { "${it.temperature}°" } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (report != null) {
            Text("${report.todayMax}° / ${report.todayMin}°", fontSize = 10.sp, color = color.copy(alpha = 0.55f))
        }
    }
}
