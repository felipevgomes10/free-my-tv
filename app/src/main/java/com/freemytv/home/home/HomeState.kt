package com.freemytv.home.home

data class HomeState(
    val statusClock: StatusClock,
    val appTray: List<AppTrayItem>,
    /** The app lifted in the App Tray's Edit Mode, or null outside Edit Mode. */
    val lifted: AppTrayItem.App? = null,
)

data class StatusClock(
    val time: String,
    val date: String,
)

sealed interface AppTrayItem {

    data class App(
        val packageName: String,
        val label: String,
        val art: TileArt,
    ) : AppTrayItem

    /** The fixed last item of the App Tray, leading to All Apps. */
    data object AllApps : AppTrayItem
}

enum class TileArt {
    /** The app's own App Banner fills the tile. */
    Banner,

    /** The app has no banner: its icon sits centered over a color taken from it. */
    IconOnColor,
}
