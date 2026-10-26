package com.freemytv.home.android

import java.io.File

/** A byte-limited image cache. Network writes use separate partial files outside the lock. */
internal class ImageFiles(private val dir: File, private val budgetBytes: Long) {
    @Synchronized
    fun find(name: String): File? {
        val file = File(dir, name).takeIf { it.isFile } ?: return null
        return keep(file)
    }

    @Synchronized
    fun partial(): File {
        dir.mkdirs()
        return File.createTempFile("image-", ".part", dir)
    }

    @Synchronized
    fun commit(partial: File, name: String): File? {
        val file = File(dir, name)
        if (!partial.renameTo(file)) return null
        return keep(file)
    }

    @Synchronized
    fun trim() = trim(null)

    private fun keep(file: File): File? {
        if (file.length() > budgetBytes) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        trim(file)
        return file
    }

    private fun trim(kept: File?) {
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        // A killed process can leave a partial; a current transfer is never evicted beneath its writer.
        files.filter { it.extension == "part" && it.lastModified() < System.currentTimeMillis() - DAY_MILLIS }.forEach { it.delete() }
        val images = files.filter { it.extension != "part" }
        var bytes = images.sumOf { it.length() }
        for (file in images.filter { it != kept }.sortedBy { it.lastModified() }) {
            if (bytes <= budgetBytes) break
            val size = file.length()
            if (file.delete()) bytes -= size
        }
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
