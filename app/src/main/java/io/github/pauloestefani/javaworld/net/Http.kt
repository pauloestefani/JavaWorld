package io.github.pauloestefani.javaworld.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, url: String) : IOException("O site respondeu com erro $code ($url)")

object Http {
    const val USER_AGENT = "pauloestefani/JavaWorld/1.0 (github.com/pauloestefani/JavaWorld)"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun connect(url: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", USER_AGENT)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null || ++redirects > 10) throw IOException("Redirecionamento inválido em $url")
                current = URL(URL(current), loc).toString()
                continue
            }
            if (code !in 200..299) {
                c.disconnect()
                throw HttpException(code, current)
            }
            return c
        }
    }

    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        val c = connect(url)
        try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun json(url: String): JSONObject = JSONObject(text(url))

    suspend fun jsonArray(url: String): JSONArray = JSONArray(text(url))

    /** Baixa um arquivo. [progress] recebe (baixado, total) — total pode ser -1. */
    suspend fun download(url: String, dest: File, progress: ((Long, Long) -> Unit)? = null) =
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")
            val c = connect(url)
            try {
                val total = c.contentLengthLong
                var done = 0L
                var lastReport = 0L
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val r = input.read(buf)
                            if (r < 0) break
                            out.write(buf, 0, r)
                            done += r
                            val now = System.currentTimeMillis()
                            if (progress != null && now - lastReport > 200) {
                                lastReport = now
                                progress(done, total)
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw IOException("Download incompleto ($done de $total bytes)")
                progress?.invoke(done, total)
            } catch (e: Throwable) {
                part.delete()
                throw e
            } finally {
                c.disconnect()
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw IOException("Não consegui salvar ${dest.name}")
        }
}
