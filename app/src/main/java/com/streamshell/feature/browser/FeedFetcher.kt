package com.streamshell.feature.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Downloads a channel's public feed from www.youtube.com (HTTPS, that host only, no cookies, size-limited). */
internal object FeedFetcher {
    private const val MAX_BYTES = 1_500_000

    fun allowed(url: String): Boolean {
        val u = runCatching { java.net.URL(url) }.getOrNull() ?: return false
        return u.protocol == "https" && u.host == "www.youtube.com" && u.path == "/feeds/videos.xml"
    }

    fun fetch(url: String): String {
        require(allowed(url))
        val c = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.instanceFollowRedirects = false
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    check(out.size() <= MAX_BYTES) { "too large" }
                }
            }
            return out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }
}

/** Break reminder as a normal notification while StreamShell is in the background. */
internal object Reminders {
    private const val CHANNEL = "reminders"

    fun show(context: Context, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Break reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.streamshell.R.drawable.ic_stat_media)
            .setContentTitle("Time for a break")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(77, n)
    }
}
