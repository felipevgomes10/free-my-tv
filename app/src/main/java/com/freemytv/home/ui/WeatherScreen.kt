package com.freemytv.home.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.weather.Condition
import com.freemytv.home.weather.DaySlot
import com.freemytv.home.weather.HourSlot
import com.freemytv.home.weather.WeatherReport
import com.freemytv.home.weather.WeatherAtmosphere
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Weather Widget's detail: now, the next 7 days, and the next 12 hours. Focus rests on the
 * widget's own pill in the Status Bar, from where the other widgets are one press away. While the
 * screen arrives, or the first forecast is on its way, a skeleton of the same layout stands in.
 */
@Composable
fun WeatherScreen(
    report: WeatherReport?,
    loading: Boolean,
    clock: StatusClock,
    soundMuted: Boolean,
    onToggleSound: () -> Unit,
    active: Boolean,
    statusWidgets: @Composable () -> Unit,
    showSoundControl: Boolean = true,
) {
    val pill = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { pill.requestFocus() } }

    val settled = LocalScreenSettled.current
    val page = when {
        report != null && settled -> WeatherPage.Ready
        report == null && !loading && settled -> WeatherPage.Unavailable
        else -> WeatherPage.Loading
    }
    val sky = if (report?.isDay == false) Color(0xFF0B1524) else Color(0xFF16304D)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to sky, 0.85f to Tokens.Background)),
    ) {
        val atmosphere = report?.let { WeatherAtmosphere.fromWmo(it.weatherCode, it.isDay) }
        WeatherAtmosphereBackground(atmosphere, active && settled && report != null, soundMuted)
        CompositionLocalProvider(LocalWeatherPillFocus provides pill) {
            StatusBar(clock, Modifier.padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop), statusWidgets)
        }
        Crossfade(page, Modifier.fillMaxSize(), animationSpec = tween(REVEAL_MILLIS), label = "weather") { shown ->
            Box(Modifier.fillMaxSize()) {
                when (shown) {
                    WeatherPage.Ready -> if (report != null) {
                        Now(report, Modifier.padding(start = Tokens.SafeAreaHorizontal, top = 95.dp))
                        NextDays(report.days, Modifier.align(Alignment.TopEnd).padding(end = Tokens.SafeAreaHorizontal, top = 100.dp).width(330.dp))
                        NextHours(report.hours, Modifier.align(Alignment.BottomStart).padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, bottom = 48.dp).fillMaxWidth())
                        if (atmosphere != null && showSoundControl) PillButton(
                            label = stringResource(if (soundMuted) R.string.weather_unmute else R.string.weather_mute),
                            icon = if (soundMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on,
                            onClick = onToggleSound,
                            modifier = Modifier.padding(start = Tokens.SafeAreaHorizontal, top = 350.dp),
                        )
                    }
                    WeatherPage.Unavailable -> Text(
                        stringResource(R.string.weather_unavailable),
                        color = WeatherSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).width(360.dp),
                        textAlign = TextAlign.Center,
                    )
                    WeatherPage.Loading -> Skeleton {
                        NowBones(Modifier.padding(start = Tokens.SafeAreaHorizontal, top = 95.dp))
                        NextDaysBones(Modifier.align(Alignment.TopEnd).padding(end = Tokens.SafeAreaHorizontal, top = 100.dp).width(330.dp))
                        NextHoursBones(Modifier.align(Alignment.BottomStart).padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, bottom = 48.dp).fillMaxWidth())
                    }
                }
            }
        }
    }
}

private enum class WeatherPage { Loading, Ready, Unavailable }

/** [Now]'s shape: the place, the big temperature, the sky, and four facts. */
@Composable
private fun NowBones(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextBone(110.dp, 14)
        Bone(170.dp, 96.dp, Modifier.padding(vertical = 12.dp), RoundedCornerShape(14.dp))
        TextBone(150.dp, 18)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf(34.dp, 34.dp, 78.dp, 60.dp).forEach { TextBone(it, 12) }
        }
    }
}

/** [NextDays]' shape: seven rows of day, sky, low, range and high, with their hairlines. */
@Composable
private fun NextDaysBones(modifier: Modifier) {
    Column(modifier) {
        repeat(7) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextBone(if (it == 0) 36.dp else 52.dp, 12, Modifier.width(72.dp))
                Bone(14.dp, 14.dp, shape = CircleShape)
                TextBone(22.dp, 12, Modifier.width(28.dp), Alignment.CenterEnd)
                Bone(null, 4.dp, Modifier.weight(1f), RoundedCornerShape(2.dp))
                TextBone(22.dp, 12, Modifier.width(28.dp), Alignment.CenterEnd)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1AFFFFFF)))
        }
    }
}

/** [NextHours]' shape: twelve columns of hour, sky and temperature. */
@Composable
private fun NextHoursBones(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(12) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextBone(if (it == 0) 30.dp else 22.dp, 11)
                Bone(16.dp, 16.dp, shape = CircleShape)
                TextBone(24.dp, 13)
            }
        }
    }
}

@Composable
private fun Now(report: WeatherReport, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(report.place, color = WeatherSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("${report.temperature}°", color = Tokens.TextPrimary, fontSize = 120.sp, fontWeight = FontWeight.Light, letterSpacing = (-5).sp, lineHeight = 120.sp)
        Text(stringResource(report.condition.label()), color = Tokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Fact(R.drawable.ic_arrow_up, "${report.todayMax}°")
            Fact(R.drawable.ic_arrow_down, "${report.todayMin}°")
            Fact(R.drawable.ic_water_drop, stringResource(R.string.humidity, report.humidity))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(Condition.Rain, true, 12.dp, WeatherSecondary)
                Text(stringResource(R.string.rain_chance, report.rainChance), color = Tokens.TextPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Fact(icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = WeatherSecondary, modifier = Modifier.size(12.dp))
        Text(text, color = Tokens.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun NextDays(days: List<DaySlot>, modifier: Modifier) {
    val low = days.minOfOrNull { it.min } ?: 0
    val high = days.maxOfOrNull { it.max } ?: 1
    Column(modifier) {
        days.forEach { day ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayLabel(day), color = Tokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
                WeatherIcon(day.condition, true, 14.dp, Tokens.TextPrimary)
                Text("${day.min}°", color = WeatherSecondary, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                RangeBar(day.min, day.max, low, high, Modifier.weight(1f))
                Text("${day.max}°", color = Tokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1AFFFFFF)))
        }
    }
}

/** A day's temperature range placed on the week's range. */
@Composable
private fun RangeBar(min: Int, max: Int, weekLow: Int, weekHigh: Int, modifier: Modifier) {
    BoxWithConstraints(modifier.height(4.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(2.dp))) {
        val span = (weekHigh - weekLow).coerceAtLeast(1).toFloat()
        val start = maxWidth * ((min - weekLow) / span)
        val width = maxWidth * ((max - min).coerceAtLeast(1) / span)
        Box(
            Modifier
                .offset(x = start)
                .width(width)
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF64D2FF), Color(0xFFFFD60A))), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun NextHours(hours: List<HourSlot>, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        hours.forEach { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (hour.isNow) stringResource(R.string.now) else "${hour.time.hour}h",
                    color = if (hour.isNow) Tokens.TextPrimary else WeatherSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (hour.isNow) FontWeight.Bold else FontWeight.Medium,
                )
                WeatherIcon(hour.condition, hour.isDay, 16.dp, Tokens.TextPrimary)
                Text("${hour.temperature}°", color = Tokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun dayLabel(day: DaySlot): String =
    if (day.isToday) stringResource(R.string.today) else day.date.format(WEEKDAY).removeSuffix("-feira").replaceFirstChar { it.titlecase(PT_BR) }

fun Condition.label(): Int = when (this) {
    Condition.Clear -> R.string.condition_clear
    Condition.PartlyCloudy -> R.string.condition_partly_cloudy
    Condition.Cloudy -> R.string.condition_cloudy
    Condition.Fog -> R.string.condition_fog
    Condition.Drizzle -> R.string.condition_drizzle
    Condition.Rain -> R.string.condition_rain
    Condition.Snow -> R.string.condition_snow
    Condition.Thunderstorm -> R.string.condition_thunderstorm
}

private const val REVEAL_MILLIS = 300
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", PT_BR)

// Brighter than the Home's usual secondary text, to stay readable over moving footage.
private val WeatherSecondary = Color(0xD9FFFFFF)
