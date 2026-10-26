package com.freemytv.home.android

import android.content.res.AssetManager
import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors

/** One WebSocket connection, as the bridge sees it. */
interface WebSocketPeer {
    fun send(text: String)
    fun close()
}

interface WebSocketListener {
    fun onText(text: String)
    fun onClosed()
}

/**
 * The FMTV Controller's bridge on the home network (free-my-tv-remote-controller's ADR 0002), on
 * one port for as long as the Home lives: the page the APK carries in assets/web-remote/,
 * `POST /pair` for Pairing, and `/remote` upgraded to a WebSocket for keys. Hand-rolled like APK
 * Drop's server, to stay within the Home's memory budget: a thread per connection, nothing kept
 * per key.
 */
class WebRemoteServer(
    private val assets: AssetManager,
    private val pair: (body: String) -> Pair<Int, String>,
    private val remote: (WebSocketPeer) -> WebSocketListener,
) {

    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "WebRemote").apply { isDaemon = true } }
    @Volatile private var server: ServerSocket? = null

    fun start(port: Int) {
        if (server != null) return
        val socket = try {
            ServerSocket(port)
        } catch (e: IOException) {
            Log.w(TAG, "Can't serve on port $port", e)
            return
        }
        server = socket
        pool.execute {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                pool.execute { runCatching { handle(client) }.onFailure { Log.w(TAG, "Request failed", it) } }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = REQUEST_TIMEOUT_MILLIS
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val requestLine = input.readLine() ?: return socket.close()
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .mapNotNull { line -> line.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().lowercase() to it[1].trim() } }
            .toMap()
        val (method, target) = requestLine.split(" ").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "/" } }
        val path = target.substringBefore('?')
        when {
            path == "/remote" && headers["upgrade"].equals("websocket", ignoreCase = true) -> webSocket(socket, input, out, headers)
            method == "POST" && path == "/pair" -> socket.use {
                val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_PAIR_BODY) ?: 0
                val body = ByteArray(length).also { DataInputStream(input).readFully(it) }.toString(Charsets.UTF_8)
                val (status, json) = pair(body)
                out.respond(status, "application/json", json.toByteArray(), cache = "no-store")
            }
            method == "GET" -> socket.use { serveAsset(out, path) }
            else -> socket.use { out.respond(405, "text/plain; charset=utf-8", "Método não permitido".toByteArray(), cache = "no-store") }
        }
    }

    private fun serveAsset(out: OutputStream, path: String) {
        val name = if (path == "/" || path.isEmpty()) "index.html" else path.removePrefix("/")
        if (".." in name) return out.respond(404, "text/plain; charset=utf-8", "Não encontrado".toByteArray(), cache = "no-store")
        val bytes = try {
            assets.open("web-remote/$name").use { it.readBytes() }
        } catch (_: IOException) {
            val missing = if (name == "index.html") "Esta versão do Home não inclui o FMTV Controller." else "Não encontrado"
            return out.respond(if (name == "index.html") 503 else 404, "text/plain; charset=utf-8", missing.toByteArray(), cache = "no-store")
        }
        // Hashed build files never change; the page, its manifest and its service worker always may.
        val cache = if (name.startsWith("assets/")) "public, max-age=31536000, immutable" else "no-cache"
        out.respond(200, contentType(name), bytes, cache)
    }

    private fun webSocket(socket: Socket, input: InputStream, out: OutputStream, headers: Map<String, String>) {
        val key = headers["sec-websocket-key"] ?: return socket.close()
        val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray()))
        out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
        out.flush()
        // A connection waits for keys for as long as the phone keeps it open.
        socket.soTimeout = 0
        val peer = SocketPeer(socket, out)
        val listener = remote(peer)
        try {
            val data = DataInputStream(input)
            while (true) {
                val first = data.readUnsignedByte()
                val second = data.readUnsignedByte()
                val opcode = first and 0x0F
                var length = (second and 0x7F).toLong()
                if (length == 126L) length = data.readUnsignedShort().toLong()
                if (length == 127L) length = data.readLong()
                if (length > MAX_FRAME) break
                val mask = if (second and 0x80 != 0) ByteArray(4).also(data::readFully) else null
                val payload = ByteArray(length.toInt()).also(data::readFully)
                if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                when (opcode) {
                    OP_TEXT -> listener.onText(payload.toString(Charsets.UTF_8))
                    OP_PING -> peer.frame(OP_PONG, payload)
                    OP_CLOSE -> break
                }
            }
        } catch (_: IOException) {
            // The phone went away: the app closed, Wi-Fi lost, or the screen locked.
        } finally {
            peer.close()
            listener.onClosed()
        }
    }

    private class SocketPeer(private val socket: Socket, private val out: OutputStream) : WebSocketPeer {
        override fun send(text: String) = frame(OP_TEXT, text.toByteArray())

        @Synchronized
        fun frame(opcode: Int, payload: ByteArray) {
            if (socket.isClosed) return
            try {
                out.write(0x80 or opcode)
                when {
                    payload.size < 126 -> out.write(payload.size)
                    payload.size < 65536 -> {
                        out.write(126)
                        out.write(payload.size shr 8)
                        out.write(payload.size and 0xFF)
                    }
                    else -> return
                }
                out.write(payload)
                out.flush()
            } catch (_: IOException) {
                runCatching { socket.close() }
            }
        }

        override fun close() {
            frame(OP_CLOSE, ByteArray(0))
            runCatching { socket.close() }
        }
    }

    private fun OutputStream.respond(status: Int, type: String, body: ByteArray, cache: String) {
        val reason = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            429 -> "Too Many Requests"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        write("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: $cache\r\nConnection: close\r\n\r\n".toByteArray())
        write(body)
        flush()
    }

    private fun InputStream.readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val c = read()
            if (c == -1) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length > MAX_LINE) return null
            line.append(c.toChar())
        }
    }

    private fun contentType(name: String) = when (name.substringAfterLast('.')) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "webmanifest" -> "application/manifest+json"
        "png" -> "image/png"
        "woff2" -> "font/woff2"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private companion object {
        const val TAG = "WebRemoteServer"
        const val REQUEST_TIMEOUT_MILLIS = 10_000
        const val MAX_LINE = 8_192
        const val MAX_PAIR_BODY = 4_096
        const val MAX_FRAME = 16_384L
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val OP_TEXT = 0x1
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xA
    }
}
