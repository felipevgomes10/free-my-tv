package com.freemytv.home.android

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.freemytv.home.home.TileArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The picture for an app's tile: its App Banner, or its icon plus a backdrop color taken from it. */
sealed interface AppArtwork {
    data class Banner(val bitmap: Bitmap) : AppArtwork
    data class IconOnColor(val icon: Bitmap, val color: Int) : AppArtwork
}

class AppArtworkLoader(context: Context) {

    private val packageManager = context.packageManager
    private val cache = object : LruCache<String, AppArtwork>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppArtwork): Int = when (value) {
            is AppArtwork.Banner -> value.bitmap.allocationByteCount
            is AppArtwork.IconOnColor -> value.icon.allocationByteCount
        }
    }

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
        artwork.also { cache.put(packageName, it) }
    }

    private val icons = object : LruCache<String, Bitmap>(512 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun cachedIcon(packageName: String): Bitmap? = icons[packageName]

    /** The app's launcher icon, small, for notification badges. */
    suspend fun icon(packageName: String): Bitmap? = icons[packageName] ?: withContext(Dispatchers.IO) {
        runCatching { packageManager.getApplicationIcon(packageName).toBitmap(ICON_SIZE, ICON_SIZE) }.getOrNull()?.also { icons.put(packageName, it) }
    }

    /** Drop cache references, never recycle pictures a visible screen may still be drawing. */
    fun clear() {
        cache.evictAll()
        icons.evictAll()
    }

    private companion object {
        // Decoded at banner size, never at panel resolution (ADR 0004).
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_SIZE = 96
    }
}
