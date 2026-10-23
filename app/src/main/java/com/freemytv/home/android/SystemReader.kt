package com.freemytv.home.android

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.view.Choreographer
import android.view.Display
import com.freemytv.home.monitor.AppStorage
import com.freemytv.home.monitor.Importance
import com.freemytv.home.monitor.MemoryUsage
import com.freemytv.home.monitor.ProcessMemory
import com.freemytv.home.monitor.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Storage, memory and frame readings for the System Monitor Widget. */
class SystemReader(private val context: Context) {

    private val packageManager = context.packageManager
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        StorageUsage(stat.totalBytes, stat.availableBytes)
    }

    fun memoryUsage(): MemoryUsage {
        val info = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return MemoryUsage(info.totalMem, info.availMem)
    }

    /** Needs usage access (granted by Setup). */
    suspend fun appStorage(): List<AppStorage>? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext null
        val stats = context.getSystemService(StorageStatsManager::class.java)
        packageManager.getInstalledApplications(0).mapNotNull { app ->
            runCatching {
                val usage = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle())
                AppStorage(app.packageName, usage.appBytes + usage.dataBytes + usage.cacheBytes, removable = app.flags and ApplicationInfo.FLAG_SYSTEM == 0)
            }.getOrNull()
        }
    }

    /**
     * Android only shows an app its own processes' memory, and SELinux keeps apps away from
     * `dumpsys meminfo`. The activity service is reachable, though, and with the DUMP
     * permission (a development permission Setup grants over ADB) `dumpsys activity processes`
     * gives every process's last measured PSS and its process state.
     */
    suspend fun processMemory(): List<ProcessMemory>? = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) return@withContext null
        val output = runCatching {
            val process = ProcessBuilder("dumpsys", "activity", "processes").redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
        }.getOrNull() ?: return@withContext null
        ProcessesParser.parse(output).map { it.copy(packageName = packageOf(it.packageName)) }.takeIf { it.isNotEmpty() }
    }

    /** Processes may be named after their package plus a suffix, such as "com.google.android.gms.persistent". */
    private fun packageOf(processName: String): String {
        var candidate = processName
        while (true) {
            if (runCatching { packageManager.getPackageInfo(candidate, 0) }.isSuccess) return candidate
            if ('.' !in candidate) return processName
            candidate = candidate.substringBeforeLast('.')
        }
    }

    fun appLabel(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun closeApp(packageName: String) = activityManager.killBackgroundProcesses(packageName)

    /** Counts Choreographer frames: a busy main thread shows up as a lower rate. */
    val homeFrameRate: Flow<Int> = callbackFlow {
        val choreographer = Choreographer.getInstance()
        var windowStart = 0L
        var frames = 0
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (windowStart == 0L) windowStart = frameTimeNanos
                frames++
                val elapsed = frameTimeNanos - windowStart
                if (elapsed >= 1_000_000_000L) {
                    trySend((frames * 1_000_000_000.0 / elapsed).roundToInt())
                    windowStart = frameTimeNanos
                    frames = 0
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        awaitClose { choreographer.removeFrameCallback(callback) }
    }.flowOn(Dispatchers.Main)

    fun displayRefreshRate(): Int =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
}

/** Reads `dumpsys activity processes`: one record per process with its last PSS and process state. */
object ProcessesParser {

    private val RECORD = Regex("""^\s+\*(APP|PERS)\* UID \d+ ProcessRecord\{\w+ \d+:([^/\s}]+)""")
    // Decimals follow the TV's language: "4,0MB" on the owner's pt-BR TV, "4.0MB" elsewhere.
    private val PSS = Regex("""\blastPss=([\d.,]+)(KB|MB|GB)?""")
    private val STATE = Regex("""\bcurProcState=(-?\d+)""")

    fun parse(output: String): List<ProcessMemory> {
        val processes = mutableListOf<ProcessMemory>()
        var name: String? = null
        var persistent = false
        var bytes: Long? = null
        fun flush(state: Int) {
            val process = name ?: return
            val pss = bytes ?: return
            processes += ProcessMemory(process.substringBefore(':'), pss, if (persistent) Importance.System else importanceOf(state))
            name = null
            bytes = null
        }
        output.lineSequence().forEach { line ->
            RECORD.find(line)?.let {
                name = it.groupValues[2]
                persistent = it.groupValues[1] == "PERS"
                bytes = null
                return@forEach
            }
            PSS.find(line)?.let { bytes = toBytes(it.groupValues[1].replace(',', '.').toDouble(), it.groupValues[2]) }
            STATE.find(line)?.let { flush(it.groupValues[1].toInt()) }
        }
        return processes
    }

    private fun toBytes(value: Double, unit: String): Long = (value * when (unit) {
        "GB" -> 1_073_741_824.0
        "MB" -> 1_048_576.0
        else -> 1024.0
    }).toLong()

    /** ActivityManager.PROCESS_STATE_* (Android 12). */
    private fun importanceOf(state: Int): Importance = when (state) {
        0, 1 -> Importance.System
        2, 3 -> Importance.Foreground
        6 -> Importance.Visible
        4, 5, 7, 8, 9, 13 -> Importance.Perceptible
        else -> Importance.Background
    }
}
