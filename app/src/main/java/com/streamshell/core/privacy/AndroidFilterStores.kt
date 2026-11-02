package com.streamshell.core.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.first

class DataStoreFilterMetaStore(private val store: DataStore<Preferences>) : FilterMetaStore {
    private val key = stringPreferencesKey("filter_config_json")
    override suspend fun load(): String? = store.data.first()[key]
    override suspend fun save(json: String) {
        store.edit { it[key] = json }
    }
}

/** Cached list files in app-private storage. Replacement is write-to-temp then rename. */
class InternalFilterFileStore(context: Context) : FilterFileStore {
    private val dir = File(context.filesDir, "filters").apply { mkdirs() }
    private fun file(id: String) = File(dir, safe(id) + ".txt")
    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" }

    override fun read(id: String): ByteArray? = file(id).takeIf { it.isFile }?.readBytes()

    override fun writeAtomic(id: String, bytes: ByteArray): Boolean {
        val tmp = File(dir, safe(id) + ".tmp")
        return try {
            tmp.writeBytes(bytes)
            tmp.renameTo(file(id)).also { if (!it) tmp.delete() }
        } catch (_: IOException) {
            tmp.delete()
            false
        }
    }

    override fun delete(id: String) {
        file(id).delete()
    }
}

/** HTTPS-only downloader with a host allowlist on every hop, conditional requests and a hard size cap. */
class HttpsFilterListDownloader(private val maxBytes: Int = FilterListValidator.MAX_BYTES) : FilterListDownloader {
    override fun download(url: String, allowedHosts: Set<String>, etag: String?, lastModified: String?): DownloadResult {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val u = URL(current)
            if (!u.protocol.equals("https", true)) throw IOException("Only https is allowed")
            if (u.host.lowercase() !in allowedHosts) throw IOException("Host not trusted: ${u.host}")
            val conn = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "text/plain")
                if (etag != null) setRequestProperty("If-None-Match", etag)
                if (lastModified != null) setRequestProperty("If-Modified-Since", lastModified)
            }
            try {
                val code = conn.responseCode
                if (code in 300..399 && code != 304) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without Location")
                    current = URL(u, location).toString()
                    return@repeat
                }
                if (code == 304) return DownloadResult.NotModified
                if (code != 200) throw IOException("HTTP $code")
                if (conn.contentLengthLong > maxBytes) throw IOException("List too large")
                val bytes = conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("List too large")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                return DownloadResult.Data(bytes, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"))
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        const val MAX_REDIRECTS = 2
    }
}
