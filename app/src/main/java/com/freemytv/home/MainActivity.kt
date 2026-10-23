package com.freemytv.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.freemytv.home.android.AndroidTvPlatform
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.home.HomeStateHolder
import com.freemytv.home.ui.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var home: HomeStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = HomeStateHolder(AndroidTvPlatform(applicationContext))
        val artwork = AppArtworkLoader(applicationContext)

        // The Home is the bottom of the stack: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                home.refresh()
                while (true) {
                    home.tick()
                    delay(CLOCK_TICK_MILLIS)
                }
            }
        }

        setContent {
            val state by home.state.collectAsStateWithLifecycle()
            HomeScreen(state = state, artwork = artwork, onSelect = home::select)
        }
    }

    private companion object {
        const val CLOCK_TICK_MILLIS = 1_000L
    }
}
