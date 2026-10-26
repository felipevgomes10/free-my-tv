package com.freemytv.home.android

import android.util.Log
import com.freemytv.home.apkdrop.UploadVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * The APK Drop page: a tiny HTTP server on the home network, alive only while collected.
 * `GET /` is the page; `POST /upload` takes the raw file with its code in an `X-Code` header,
 * so nothing is accepted before the Home has seen the code and the size.
 */
internal class ApkDropServer(private val downloads: TemporaryDownloads) {

    fun serve(port: Int, verdict: (code: String, sizeBytes: Long) -> UploadVerdict): Flow<String> = callbackFlow {
        val server = try {
            ServerSocket(port)
        } catch (e: IOException) {
            // The port is taken: no page, and the screen says so by showing no address.
            Log.w(TAG, "Can't serve on port $port", e)
            close()
            return@callbackFlow
        }
        downloads.directory()
        launch {
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (e: SocketException) {
                    break
                }
                // One phone at a time is plenty, and keeps a TV from being flooded.
                socket.use { runCatching { handle(it, verdict) }.onFailure { e -> Log.w(TAG, "Upload failed", e) }.getOrNull()?.let { path -> trySend(path) } }
            }
        }
        awaitClose { runCatching { server.close() } }
    }.flowOn(Dispatchers.IO)

    /** Answers one request; returns the received file's path for an accepted upload. */
    private fun handle(socket: Socket, verdict: (String, Long) -> UploadVerdict): String? {
        socket.soTimeout = TIMEOUT_MILLIS
        val input = BufferedInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        val requestLine = input.readLine() ?: return null
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .mapNotNull { line -> line.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().lowercase() to it[1].trim() } }
            .toMap()
        val (method, target) = requestLine.split(" ").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "/" } }
        return when {
            method == "GET" && target.substringBefore('?') == "/" -> {
                out.respond(200, "text/html; charset=utf-8", PAGE)
                null
            }
            method == "POST" && target == "/upload" -> upload(input, out, headers, verdict)
            else -> {
                out.respond(404, "text/plain; charset=utf-8", "Não encontrado")
                null
            }
        }
    }

    private fun upload(input: InputStream, out: OutputStream, headers: Map<String, String>, verdict: (String, Long) -> UploadVerdict): String? {
        val size = headers["content-length"]?.toLongOrNull()
        if (size == null || size <= 0) {
            out.respond(411, "text/plain; charset=utf-8", "Escolha um arquivo .apk.")
            return null
        }
        when (verdict(headers["x-code"].orEmpty(), size)) {
            UploadVerdict.WrongCode -> out.respond(403, "text/plain; charset=utf-8", "Código errado. Confira o código na TV.")
            UploadVerdict.TooBig -> out.respond(413, "text/plain; charset=utf-8", "Não cabe na TV: libere espaço e tente de novo.")
            UploadVerdict.Busy -> out.respond(409, "text/plain; charset=utf-8", "A TV já tem um arquivo esperando. Instale ou cancele na TV primeiro.")
            UploadVerdict.Accept -> {
                val file = File(downloads.directory(), "${System.currentTimeMillis()}.apk")
                try {
                    file.outputStream().use { sink -> copy(input, sink, size) }
                } catch (e: IOException) {
                    file.delete()
                    throw e
                }
                out.respond(200, "text/plain; charset=utf-8", "Recebido! Continue na TV.")
                return file.absolutePath
            }
        }
        return null
    }

    private fun copy(input: InputStream, sink: OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var left = size
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) throw IOException("Upload ended after ${size - left} of $size bytes")
            sink.write(buffer, 0, read)
            left -= read
        }
    }

    private fun InputStream.readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return line.takeIf { it.isNotEmpty() }?.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
            if (line.length > MAX_LINE) throw IOException("Header line too long")
        }
    }

    private fun OutputStream.respond(status: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        write("HTTP/1.1 $status ${REASONS[status] ?: "OK"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        write(bytes)
        flush()
    }

    private companion object {
        const val TAG = "ApkDropServer"
        const val TIMEOUT_MILLIS = 60_000
        const val MAX_LINE = 8 * 1024
        val REASONS = mapOf(200 to "OK", 403 to "Forbidden", 404 to "Not Found", 409 to "Conflict", 411 to "Length Required", 413 to "Payload Too Large")

        // The code comes in the QR code's link (?code=), so scanning it fills the field.
        val PAGE = """
            <!doctype html>
            <html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Enviar APK para a TV</title>
            <style>
              body { font-family: system-ui, sans-serif; background: #000; color: #fff; margin: 0; padding: 24px; }
              main { max-width: 480px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }
              h1 { font-size: 28px; margin: 0; }
              p { color: #ffffffa6; margin: 0; line-height: 1.4; }
              label { display: flex; flex-direction: column; gap: 8px; font-weight: 600; }
              input { font-size: 18px; padding: 14px; border-radius: 14px; border: 0; background: #ffffff1f; color: #fff; }
              input[name=code] { font-size: 32px; letter-spacing: 12px; text-align: center; }
              button { font-size: 18px; font-weight: 600; padding: 16px; border-radius: 999px; border: 0; background: #fff; color: #000; }
              button:disabled { opacity: .5; }
              #status { min-height: 24px; color: #fff; }
            </style></head>
            <body><main>
              <h1>Enviar APK para a TV</h1>
              <p>Digite o código que aparece na TV e escolha o arquivo. A TV pede confirmação antes de instalar.</p>
              <label>Código <input name="code" inputmode="numeric" maxlength="4" autocomplete="off"></label>
              <label>Arquivo <input name="file" type="file" accept=".apk,application/vnd.android.package-archive"></label>
              <button id="send">Enviar</button>
              <p id="status"></p>
            </main>
            <script>
              const code = document.querySelector('[name=code]');
              const file = document.querySelector('[name=file]');
              const send = document.getElementById('send');
              const status = document.getElementById('status');
              code.value = new URLSearchParams(location.search).get('code') || '';
              send.onclick = async () => {
                if (!file.files[0]) { status.textContent = 'Escolha um arquivo .apk.'; return; }
                send.disabled = true;
                status.textContent = 'Enviando…';
                try {
                  const response = await fetch('/upload', { method: 'POST', headers: { 'X-Code': code.value }, body: file.files[0] });
                  status.textContent = await response.text();
                } catch (e) {
                  status.textContent = 'Não deu para falar com a TV. "Enviar APK" ainda está aberto nela?';
                }
                send.disabled = false;
              };
            </script>
            </body></html>
        """.trimIndent()
    }
}
