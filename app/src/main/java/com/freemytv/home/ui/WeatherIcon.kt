package com.freemytv.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.freemytv.home.weather.Condition
import kotlin.math.cos
import kotlin.math.sin

/** A flat weather glyph built from simple shapes, drawn in one [color]. */
@Composable
fun WeatherIcon(condition: Condition, isDay: Boolean, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val s = this.size.minDimension
        when (condition) {
            Condition.Clear -> if (isDay) sun(Offset(s / 2, s / 2), s / 2, color) else moon(Offset(s / 2, s / 2), s * 0.42f, color)
            Condition.PartlyCloudy -> {
                if (isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.34f, color) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.28f, color)
                cloud(Offset(s * 0.22f, s * 0.42f), s * 0.78f, color, cutout = true)
            }
            Condition.Cloudy -> cloud(Offset(0f, s * 0.2f), s, color)
            Condition.Fog -> repeat(3) { i ->
                val y = s * (0.3f + i * 0.2f)
                drawLine(color, Offset(s * 0.1f, y), Offset(s * 0.9f, y), s * 0.09f, StrokeCap.Round)
            }
            Condition.Drizzle -> {
                cloud(Offset(0f, 0f), s, color)
                listOf(0.3f, 0.5f, 0.7f).forEach { x -> drawCircle(color, s * 0.05f, Offset(s * x, s * 0.88f)) }
            }
            Condition.Rain -> {
                cloud(Offset(0f, 0f), s, color)
                listOf(0.32f, 0.52f, 0.72f).forEach { x ->
                    drawLine(color, Offset(s * x, s * 0.76f), Offset(s * (x - 0.07f), s * 0.96f), s * 0.07f, StrokeCap.Round)
                }
            }
            Condition.Snow -> {
                cloud(Offset(0f, 0f), s, color)
                listOf(0.3f to 0.8f, 0.5f to 0.92f, 0.7f to 0.8f).forEach { (x, y) -> drawCircle(color, s * 0.06f, Offset(s * x, s * y)) }
            }
            Condition.Thunderstorm -> {
                cloud(Offset(0f, 0f), s, color)
                val bolt = Path().apply {
                    moveTo(s * 0.55f, s * 0.62f)
                    lineTo(s * 0.38f, s * 0.84f)
                    lineTo(s * 0.5f, s * 0.84f)
                    lineTo(s * 0.44f, s * 1.0f)
                    lineTo(s * 0.64f, s * 0.76f)
                    lineTo(s * 0.52f, s * 0.76f)
                    close()
                }
                drawPath(bolt, color)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius * 0.42f, center)
    repeat(8) { i ->
        val angle = Math.toRadians(i * 45.0)
        val from = Offset(center.x + cos(angle).toFloat() * radius * 0.62f, center.y + sin(angle).toFloat() * radius * 0.62f)
        val to = Offset(center.x + cos(angle).toFloat() * radius * 0.92f, center.y + sin(angle).toFloat() * radius * 0.92f)
        drawLine(color, from, to, radius * 0.14f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center)
    drawCircle(Color.Black, radius * 0.86f, Offset(center.x + radius * 0.45f, center.y - radius * 0.35f), blendMode = BlendMode.Clear)
}

/** A cloud [width] wide whose top-left corner is at [origin]; [cutout] clears a margin so it reads in front of a sun or moon. */
private fun DrawScope.cloud(origin: Offset, width: Float, color: Color, cutout: Boolean = false) {
    val h = width * 0.56f
    fun shape(inflate: Float, paint: Color, blend: BlendMode) {
        drawCircle(paint, h * 0.42f + inflate, Offset(origin.x + width * 0.36f, origin.y + h * 0.5f), blendMode = blend)
        drawCircle(paint, h * 0.32f + inflate, Offset(origin.x + width * 0.62f, origin.y + h * 0.6f), blendMode = blend)
        drawRoundRect(
            paint,
            topLeft = Offset(origin.x + width * 0.08f - inflate, origin.y + h * 0.55f - inflate),
            size = Size(width * 0.84f + inflate * 2, h * 0.45f + inflate * 2),
            cornerRadius = CornerRadius(h * 0.22f + inflate),
            blendMode = blend,
        )
    }
    if (cutout) shape(width * 0.07f, Color.Black, BlendMode.Clear)
    shape(0f, color, BlendMode.SrcOver)
}
