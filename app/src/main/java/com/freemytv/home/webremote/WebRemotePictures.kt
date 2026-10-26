package com.freemytv.home.webremote

/**
 * The Home's pictures for the FMTV Controller (App Banners, Up Next stills), served at
 * `GET /pictures/{id}` only to Paired Phones, with the credential in `Authorization` (ADR 0006
 * in free-my-tv-remote-controller). Each feature registers the pictures of its own prefix
 * (`app:…`, `upnext:…`); rendered ones are kept, least recently used first out, within a small
 * budget (free-my-tv ADR 0004).
 */
class WebRemotePictures(private val phones: PairedPhones, private val budgetBytes: Int = 4 * 1024 * 1024) {

    private val sources = mutableMapOf<String, suspend (String) -> ByteArray?>()
    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
    private var cachedBytes = 0

    /** Pictures whose id starts with `prefix:`; [load] gets the rest of the id and renders a PNG, or null. */
    fun source(prefix: String, load: suspend (String) -> ByteArray?) {
        sources[prefix] = load
    }

    /** The answer to `GET /pictures/{id}`: a status and, for 200, the PNG. */
    suspend fun serve(id: String, authorization: String?): Pair<Int, ByteArray?> {
        val credential = authorization?.removePrefix("Bearer ")?.trim()
        if (credential.isNullOrEmpty() || phones.find(credential) == null) return 401 to null
        cached(id)?.let { return 200 to it }
        val prefix = id.substringBefore(':', "")
        val load = sources[prefix] ?: return 404 to null
        val png = load(id.substringAfter(':')) ?: return 404 to null
        keep(id, png)
        return 200 to png
    }

    @Synchronized
    private fun cached(id: String): ByteArray? = cache[id]

    @Synchronized
    private fun keep(id: String, png: ByteArray) {
        if (png.size > budgetBytes) return
        cache.put(id, png)?.let { cachedBytes -= it.size }
        cachedBytes += png.size
        val oldest = cache.entries.iterator()
        while (cachedBytes > budgetBytes && oldest.hasNext()) {
            val entry = oldest.next()
            cachedBytes -= entry.value.size
            oldest.remove()
        }
    }
}
