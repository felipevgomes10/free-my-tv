package com.freemytv.home.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheFilesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `a new process removes abandoned installs without touching settings or images`() {
        val root = temp.newFolder()
        val updates = File(root, "updates").apply { mkdirs() }
        File(updates, "old.apk").writeBytes(ByteArray(50))
        File(updates, "old.dm").writeBytes(ByteArray(10))
        val settings = File(root, "settings").apply { writeText("paired phones and preferences") }
        val image = File(root, "tmdb/picture.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }

        TemporaryDownloads(updates).directory()

        assertTrue(updates.listFiles()!!.isEmpty())
        assertTrue(settings.exists())
        assertTrue(image.exists())
    }

    @Test
    fun `startup and writers share preparation so new files survive repeated access`() {
        val dir = temp.newFolder()
        val downloads = TemporaryDownloads(dir)
        val apk = File(downloads.directory(), "base.apk").apply { writeBytes(ByteArray(50)) }
        File(downloads.directory(), "base.dm").writeBytes(ByteArray(10))
        downloads.directory()
        assertTrue(apk.exists())
        assertEquals(2, dir.listFiles()!!.size)

        // The next process has no active writer from the previous one.
        TemporaryDownloads(dir).directory()
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `existing images are trimmed by bytes on startup oldest first`() {
        val dir = temp.newFolder()
        val old = image(dir, "old.jpg", 40, 1_000)
        val middle = image(dir, "middle.jpg", 40, 2_000)
        val recent = image(dir, "recent.jpg", 40, 3_000)

        ImageFiles(dir, 80).trim()

        assertFalse(old.exists())
        assertTrue(middle.exists())
        assertTrue(recent.exists())
        assertEquals(80L, dir.listFiles()!!.sumOf { it.length() })
    }

    @Test
    fun `reading an image keeps it ahead of an unused image when the cache fills`() {
        val dir = temp.newFolder()
        val used = image(dir, "used.jpg", 40, 1_000)
        val unused = image(dir, "unused.jpg", 40, 2_000)
        val cache = ImageFiles(dir, 80)
        assertEquals(used, cache.find(used.name))

        val partial = cache.partial().apply { writeBytes(ByteArray(40)) }
        assertTrue(cache.commit(partial, "new.jpg")!!.exists())

        assertTrue(used.exists())
        assertFalse(unused.exists())
    }

    @Test
    fun `simultaneous downloads use separate partials and publish only complete files`() {
        val cache = ImageFiles(temp.newFolder(), 80)
        val first = cache.partial().apply { writeBytes(ByteArray(40) { 1 }) }
        val second = cache.partial().apply { writeBytes(ByteArray(40) { 2 }) }
        assertNotEquals(first, second)
        assertNull(cache.find("picture.jpg"))
        cache.trim()
        assertTrue(first.exists())
        assertTrue(second.exists())

        cache.commit(first, "picture.jpg")
        assertEquals(1.toByte(), cache.find("picture.jpg")!!.readBytes().first())
        cache.commit(second, "picture.jpg")
        assertEquals(2.toByte(), cache.find("picture.jpg")!!.readBytes().first())
    }

    @Test
    fun `an image larger than the entire budget is not retained`() {
        val dir = temp.newFolder()
        val cache = ImageFiles(dir, 80)
        val partial = cache.partial().apply { writeBytes(ByteArray(81)) }
        assertNull(cache.commit(partial, "large.jpg"))
        assertNull(cache.find("large.jpg"))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `only abandoned partials are removed while current transfers stay intact`() {
        val dir = temp.newFolder()
        val abandoned = image(dir, "old.part", 50, 1_000)
        val cache = ImageFiles(dir, 80)
        val current = cache.partial().apply { writeBytes(ByteArray(50)) }
        cache.trim()
        assertFalse(abandoned.exists())
        assertTrue(current.exists())
    }

    private fun image(dir: File, name: String, bytes: Int, modified: Long) = File(dir, name).apply {
        writeBytes(ByteArray(bytes))
        check(setLastModified(modified))
    }
}
