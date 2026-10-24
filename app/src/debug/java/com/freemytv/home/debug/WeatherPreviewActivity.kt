package com.freemytv.home.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.freemytv.home.HomeApplication
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.PillButton
import com.freemytv.home.ui.WeatherScreen
import com.freemytv.home.weather.Condition
import com.freemytv.home.weather.DaySlot
import com.freemytv.home.weather.HourSlot
import com.freemytv.home.weather.WeatherReport
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Debug only: exercise every WMO scene on the existing emulator, without changing its forecast. */
class WeatherPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getIntExtra("weather_code", 61)
        val day = intent.getBooleanExtra("is_day", true)
        val now = LocalDateTime.of(2026, 10, 24, if (day) 14 else 21, 0)
        val report = WeatherReport(
            place = "Belém", temperature = 28, condition = Condition.fromWmo(code), isDay = day,
            todayMax = 31, todayMin = 24, humidity = 82, rainChance = 80,
            hours = (0..11).map { HourSlot(now.plusHours(it.toLong()), it == 0, 28 - it % 4, Condition.fromWmo(code), day) },
            days = (0..6).map { DaySlot(now.toLocalDate().plusDays(it.toLong()), it == 0, Condition.fromWmo(code), 24 + it % 3, 31 + it % 2) },
            weatherCode = code,
        )
        val platform = (application as HomeApplication).platform
        setContent {
            var muted by remember { mutableStateOf(false) }
            var visible by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            androidx.compose.runtime.LaunchedEffect(Unit) { muted = platform.loadWidgetPreferences().weatherSoundMuted }
            if (visible) WeatherScreen(
                report = report, loading = false, clock = StatusClock(if (day) "14:00" else "21:00", "sábado, 24 de outubro"),
                soundMuted = muted,
                onToggleSound = {
                    muted = !muted
                    scope.launch {
                        platform.saveWidgetPreferences(platform.loadWidgetPreferences().copy(weatherSoundMuted = muted))
                    }
                },
                active = true,
                statusWidgets = { PillButton("Sair", null, { visible = false }) },
            )
        }
    }
}
