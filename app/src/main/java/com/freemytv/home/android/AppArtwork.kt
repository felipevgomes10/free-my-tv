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

    private companion object {
        // Decoded at banner size, never at panel resolution (ADR 0004).
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_SIZE = 96
    }
}
