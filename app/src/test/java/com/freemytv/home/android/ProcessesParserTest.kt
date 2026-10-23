package com.freemytv.home.android

import com.freemytv.home.monitor.Importance
import com.freemytv.home.monitor.ProcessMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessesParserTest {

    // Trimmed from `dumpsys activity processes` on the Google TV emulator (Android 12).
    private val sample = """
        ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)
          All known processes:
          *APP* UID 10053 ProcessRecord{4f11217 2291:com.google.android.apps.mediashell/u0a53}
            user #0 uid=10053 gids={50053, 20053, 9997, 3003}
            lastPss=82MB lastSwapPss=0.00 lastCachedPss=0.00 lastCachedSwapPss=0.00 lastRss=174MB
            curProcState=10 mRepProcState=10 setProcState=10 lastStateTime=-50m19s783ms
          *APP* UID 10106 ProcessRecord{1a2b3c4 4915:com.spotify.tv.android/u0a106}
            lastPss=120MB lastSwapPss=0.00 lastCachedPss=0.00 lastCachedSwapPss=0.00 lastRss=210MB
            curProcState=4 mRepProcState=4 setProcState=4 lastStateTime=-3m
          *APP* UID 10061 ProcessRecord{da0428f 4673:com.android.vending:background/u0a61}
            lastPss=512KB lastSwapPss=0.00 lastCachedPss=26MB lastCachedSwapPss=0.00 lastRss=128MB
            curProcState=19 mRepProcState=19 setProcState=19 lastStateTime=-18m11s32ms
          *APP* UID 10084 ProcessRecord{abcdef0 7000:com.freemytv.home/u0a84}
            lastPss=1.5GB lastSwapPss=0.00 lastCachedPss=0.00 lastCachedSwapPss=0.00 lastRss=2GB
            curProcState=2 mRepProcState=2 setProcState=2 lastStateTime=-1s
          *PERS* UID 1000 ProcessRecord{1f24f2a 505:system/1000}
            lastPss=125MB lastSwapPss=0.00 lastCachedPss=0.00 lastCachedSwapPss=0.00 lastRss=263MB
            curProcState=0 mRepProcState=0 setProcState=0 lastStateTime=-50m30s909ms
    """.trimIndent()

    @Test
    fun `reads each process's last PSS and how important Android considers it`() {
        assertEquals(
            listOf(
                ProcessMemory("com.google.android.apps.mediashell", 82L * 1_048_576, Importance.Background),
                ProcessMemory("com.spotify.tv.android", 120L * 1_048_576, Importance.Perceptible),
                ProcessMemory("com.android.vending", 512L * 1024, Importance.Background),
                ProcessMemory("com.freemytv.home", (1.5 * 1_073_741_824).toLong(), Importance.Foreground),
                ProcessMemory("system", 125L * 1_048_576, Importance.System),
            ),
            ProcessesParser.parse(sample),
        )
    }

    @Test
    fun `decimals written with a comma, as on the owner's pt-BR TV, keep their unit`() {
        val tv = """
            All known processes:
              *APP* UID 10096 ProcessRecord{f88d141 1234:com.google.android.ext.services/u0a96}
                lastPss=4,5MB lastSwapPss=3,0MB lastCachedPss=0,00 lastCachedSwapPss=0,00 lastRss=70MB
                curProcState=5 mRepProcState=5 setProcState=5 lastStateTime=-3d0h14m57s614ms
        """.trimIndent()

        assertEquals(listOf(ProcessMemory("com.google.android.ext.services", (4.5 * 1_048_576).toLong(), Importance.Perceptible)), ProcessesParser.parse(tv))
    }

    @Test
    fun `a permission denial yields nothing`() {
        assertEquals(emptyList<ProcessMemory>(), ProcessesParser.parse("Permission Denial: can't dump ActivityManager from pid=1"))
    }
}
