package com.freemytv.home.monitor

/** Readings the platform takes from the system for the System Monitor Widget. */
data class StorageUsage(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes get() = totalBytes - freeBytes
}

data class MemoryUsage(val totalBytes: Long, val availableBytes: Long) {
    val usedBytes get() = totalBytes - availableBytes
}

data class AppStorage(
    val packageName: String,
    val bytes: Long,
    /** False for system apps, which can't be uninstalled. */
    val removable: Boolean,
)

/** One process's memory, as Android ranks its importance. */
data class ProcessMemory(
    /** The package that owns the process, without a ":process" suffix. */
    val packageName: String,
    val bytes: Long,
    val importance: Importance,
)

enum class Importance {
    /** Native daemons and persistent system processes. */
    System,
    Foreground,
    Visible,

    /** Noticeable by the owner though not on screen, such as playing music. */
    Perceptible,

    /** Services, the previous app and cached apps: what Android kills first. */
    Background,
}
