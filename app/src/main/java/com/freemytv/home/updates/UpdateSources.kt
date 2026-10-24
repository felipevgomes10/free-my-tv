package com.freemytv.home.updates

/** A GitHub release, as far as updating goes. */
data class Release(val tag: String, val assets: List<ReleaseAsset>)

data class ReleaseAsset(val name: String, val url: String, val sizeBytes: Long)

sealed interface ReleaseLookup {
    data class Found(val release: Release) : ReleaseLookup
    /** No such repository, or it has no releases. */
    data object NotFound : ReleaseLookup
    data object Offline : ReleaseLookup
}

/** "owner/name" from what the owner typed: "owner/name", "github.com/owner/name" or a full GitHub link. */
fun parseRepository(input: String): String? {
    val match = REPOSITORY.matchEntire(input.trim()) ?: return null
    return "${match.groupValues[1]}/${match.groupValues[2]}"
}

private val REPOSITORY = Regex("""(?:https?://)?(?:www\.)?(?:github\.com/)?([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?(?:/.*)?""")

/**
 * Whether [candidate] is newer than [installed], comparing their numbers part by part:
 * "v25.6" against "25.4", "1.10" against "1.9". Missing parts count as zero.
 */
fun isNewer(candidate: String, installed: String): Boolean {
    val a = numbers(candidate)
    val b = numbers(installed)
    if (a.isEmpty() || b.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun numbers(version: String): List<Long> =
    Regex("""\d+""").findAll(version.trimStart('v', 'V')).map { it.value.toLongOrNull() ?: 0 }.toList()

/**
 * The dex metadata that goes with [apk], named like it with ".dm": with it, Android compiles the
 * app as it installs instead of running it slowly until its next compilation (ADR 0013).
 */
fun Release.dexMetadataFor(apk: ReleaseAsset): ReleaseAsset? {
    val name = apk.name.substringBeforeLast('.') + ".dm"
    return assets.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/** The release's version: its tag without a leading "v". */
val Release.version: String get() = tag.trim().removePrefix("v").removePrefix("V")

/**
 * The APK that runs on this TV (32-bit ARM): one made for TVs first, then ARMv7, then a
 * universal one, then the only APK there is. 64-bit and x86 builds never run here.
 */
fun Release.apkForTv(): ReleaseAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    val runnable = apks.filterNot { name -> listOf("arm64", "aarch64", "x86").any { name.name.contains(it, ignoreCase = true) } }
    fun first(vararg marks: String) = runnable.firstOrNull { asset -> marks.any { asset.name.contains(it, ignoreCase = true) } }
    return first("tv") ?: first("armeabi-v7a", "armeabi", "arm32", "armv7") ?: first("universal") ?: runnable.singleOrNull() ?: apks.singleOrNull()
}
