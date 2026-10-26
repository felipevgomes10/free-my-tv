package com.freemytv.home.android

import java.io.File

/**
 * Install files belong to one process: after a restart no upload or installation can still
 * need its source file. In particular, a self-update kills the Home before its usual cleanup.
 * Prepare once, before the first writer, so startup cleanup never deletes a new download.
 */
internal class TemporaryDownloads(private val dir: File) {
    private var prepared = false

    @Synchronized
    fun directory(): File {
        if (!prepared) {
            dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            dir.mkdirs()
            prepared = true
        }
        return dir
    }
}
