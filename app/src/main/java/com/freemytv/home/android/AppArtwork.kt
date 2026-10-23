package com.freemytv.home.android

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.freemytv.home.home.TileArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** The picture for an app's tile: its App Banner, or its icon plus a backdrop color taken from it. */
sealed interface AppArtwork {
    data class Banner(val bitmap: Bitmap) : AppArtwork
    data class IconOnColor(val icon: Bitmap, val color: Int) : AppArtwork
}

class AppArtworkLoader(context: Context) {

    private val packageManager = context.packageManager
    private val cache = ConcurrentHashMap<String, AppArtwork>()

    suspend fun load(packageName: String, art: TileArt): AppArtwork? = cache[packageName] ?: withContext(Dispatchers.IO) {
        val info = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return@withContext null
        }
        val banner = if (art == TileArt.Banner) {
            packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?.let { packageManager.getActivityBanner(it) }
                ?: packageManager.getApplicationBanner(info)
        } else {
            null
        }
        val artwork = if (banner != null) {
            AppArtwork.Banner(banner.toBitmap(BANNER_WIDTH, BANNER_HEIGHT))
        } else {
            val icon = packageManager.getApplicationIcon(info).toBitmap(ICON_SIZE, ICON_SIZE)
            val swatch = Palette.from(icon).generate().let { it.darkVibrantSwatch ?: it.dominantSwatch }
            AppArtwork.IconOnColor(icon, swatch?.rgb ?: Color.DKGRAY)
        }
        artwork.also { cache[packageName] = it }
    }

    private val backdrops = ConcurrentHashMap<String, Bitmap>()

    /**
     * The app's banner blurred for the Top Shelf: shrunk to a few pixels, then grown back in
     * steps with filtering, which smooths it into a soft blur. Computed once per app, never
     * live (ADR 0004).
     */
    suspend fun backdrop(packageName: String): Bitmap? = backdrops[packageName] ?: run {
        val art = load(packageName, TileArt.Banner) ?: return null
        withContext(Dispatchers.Default) {
            when (art) {
                is AppArtwork.Banner -> {
                    var blurred = Bitmap.createScaledBitmap(art.bitmap, BACKDROP_WIDTH, BACKDROP_HEIGHT, true)
                    repeat(BACKDROP_STEPS) { blurred = Bitmap.createScaledBitmap(blurred, blurred.width * 2, blurred.height * 2, true) }
                    blurred
                }
                is AppArtwork.IconOnColor -> Bitmap.createBitmap(BACKDROP_WIDTH, BACKDROP_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(art.color) }
            }
        }.also { backdrops[packageName] = it }
    }

    private val icons = ConcurrentHashMap<String, Bitmap>()

    fun cachedIcon(packageName: String): Bitmap? = icons[packageName]

    /** The app's launcher icon, small, for notification badges. */
    suspend fun icon(packageName: String): Bitmap? = icons[packageName] ?: withContext(Dispatchers.IO) {
        runCatching { packageManager.getApplicationIcon(packageName).toBitmap(ICON_SIZE, ICON_SIZE) }.getOrNull()?.also { icons[packageName] = it }
    }

    private companion object {
        // Decoded at banner size, never at panel resolution (ADR 0004).
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_SIZE = 96
        const val BACKDROP_WIDTH = 24
        const val BACKDROP_HEIGHT = 14
        const val BACKDROP_STEPS = 3
    }
}
