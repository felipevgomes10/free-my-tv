# Home storage and memory, 2026-10-26

The owner's TCL, Home 0.6.0 (27), reported about 512 MB storage and 207 MB RAM in
the System Monitor. Both repositories were clean before this investigation.

## Measurements before changes

The Android storage service's `queryStatsForPackage`, called as the ADB shell for
`com.freemytv.home`, returned these **bytes**:

| Reading | Bytes |
| --- | ---: |
| App (APK and compiled code) | 17,864,192 |
| Data, including cache | 253,255,680 |
| Cache (a subset of data) | 240,746,496 |
| Actual total: app + data | 271,119,872 |
| Old Monitor calculation: app + data + cache | 511,866,368 |

The temporary read-only shell probe was removed after measurement. Android's
[StorageStats documentation](https://developer.android.com/reference/android/app/usage/StorageStats#getDataBytes())
confirms that dataBytes includes cacheBytes. Correcting the displayed total does
not itself free storage. The cache is still real.

`adb -s 192.168.100.18:5555 shell dumpsys meminfo com.freemytv.home`, with Início in
front, measured 271,445 KiB PSS (265.1 MiB), including 118,320 KiB graphics
(115.5 MiB). RAM varies with navigation, image transitions and Android's sampling;
the Monitor reads Android's last process PSS, which can lag a fresh meminfo query.
There were no live WebViews. `dumpsys gfxinfo` reported a 40.43 MiB GPU cache,
31.45 MiB purgeable; this is part of, not additional to, the process readings.

The local release APK was 10,279,899 bytes (9.8 MiB). Its embedded FMTV Controller
page occupied less than 1 MiB uncompressed. The page runs on the phone; its server
and picture cache run inside the Home. Neither package size explains 241 MB of cache.

## Changes

- System Monitor counts app + data once.
- Install downloads in `cache/updates` and `cache/apkdrop` are cleared once per
  process before their first writer. Startup invokes the same synchronized
  preparation. A self-update can kill the old process before its normal cleanup;
  the new process no longer retains the abandoned source APK or dex metadata.
  Settings, credentials and Up Next artwork are outside these directories.
- Explorar images have a 32 MiB disk budget, evicting least recently used files;
  startup trims existing images. Concurrent network writes have separate partial
  files, and partials older than a day are removed.
- App artwork is cached within 4 MiB, notification icons within 512 KiB. On
  Android's memory-pressure or UI-hidden callback, the Home drops these cache
  references, the prepared Top Shelf pictures and the FMTV Controller picture
  cache. Bitmaps still used by a screen are never forcibly recycled. These limits
  apply to caches, not all live bitmaps or the GPU's buffers.

## Validation and remaining measurement

- 304 JVM tests passed, including cache eviction, startup cleanup, partial-file
  isolation and fetching authenticated pictures again after memory pressure.
- Debug and optimized release APKs built; release lint passed.
- Android TV emulator: seeded abandoned APKs in both install directories and
  34 MiB of artificial images, restarted the process and verified both APKs and
  the older image disappeared while the newer image survived (`CACHE_STARTUP_PASS`).
  Artificial files were removed afterwards.
- Sent `am send-trim-memory com.freemytv.home RUNNING_LOW` to the emulator; the
  Home remained usable and a screenshot confirmed the rendered screen.
- No release was published and no replacement APK was installed on the TCL.
  The actual storage reduction depends on the cache's contents, which the shell
  cannot list inside a non-debuggable app. The changes do not establish that the
  Home meets ADR 0004's 150 MB RAM target: before/after memory profiling of the same
  navigation flow on the TCL is still required, especially for its GPU allocations.
