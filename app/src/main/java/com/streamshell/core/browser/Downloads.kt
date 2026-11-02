package com.streamshell.core.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil

/**
 * Ordinary file downloads a site offers (Content-Disposition attachments) go to Android's DownloadManager,
 * with the page's cookies so signed-in downloads work. Streaming media is never downloaded by StreamShell.
 * Android 10+: public Downloads folder; Android 8-9: the app's own Downloads folder (needs no storage permission).
 */
object Downloads {
    fun enqueue(context: Context, url: String, userAgent: String, contentDisposition: String?, mimeType: String?): String? {
        if (!url.startsWith("https://")) return null
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        return try {
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } else {
                req.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            }
            context.getSystemService(DownloadManager::class.java).enqueue(req)
            name
        } catch (_: Exception) {
            null
        }
    }
}
