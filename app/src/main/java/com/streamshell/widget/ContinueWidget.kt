package com.streamshell.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.streamshell.MainActivity
import com.streamshell.R
import com.streamshell.core.watch.WatchPosition
import com.streamshell.core.watch.WatchRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * Home-screen widget "Continue watching": up to three videos you stopped in the middle of; a tap opens the video in
 * StreamShell. The app writes a small snapshot (titles, addresses, positions) whenever the list changes; the widget only
 * reads it. Nothing leaves the device.
 */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val items = WidgetSnapshot.decode(prefs(context).getString(KEY, null))
        ids.forEach { manager.updateAppWidget(it, views(context, items)) }
    }

    companion object {
        private const val PREFS = "streamshell_widget"

        /** PendingIntent request codes: one per row, one for the header. */
        private const val HEADER_REQUEST = 9
        private const val KEY = "continue"
        private val rows = listOf(
            Triple(R.id.widget_row1, R.id.widget_title1, R.id.widget_time1),
            Triple(R.id.widget_row2, R.id.widget_title2, R.id.widget_time2),
            Triple(R.id.widget_row3, R.id.widget_title3, R.id.widget_time3)
        )

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Called by the app when "Continue watching" changes: stores the snapshot and redraws placed widgets. */
        fun publish(context: Context, list: List<WatchPosition>) {
            val raw = WidgetSnapshot.encode(list)
            val p = prefs(context)
            if (p.getString(KEY, null) == raw) return
            p.edit().putString(KEY, raw).apply()
            val m = AppWidgetManager.getInstance(context)
            val ids = runCatching { m?.getAppWidgetIds(ComponentName(context, ContinueWidget::class.java)) }.getOrNull() ?: IntArray(0)
            if (m != null && ids.isNotEmpty()) {
                val items = WidgetSnapshot.decode(raw)
                ids.forEach { m.updateAppWidget(it, views(context, items)) }
            }
        }

        private fun views(context: Context, items: List<WidgetSnapshot.Item>): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_continue)
            v.setViewVisibility(R.id.widget_empty, if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
            rows.forEachIndexed { i, (row, title, time) ->
                val item = items.getOrNull(i)
                if (item == null) {
                    v.setViewVisibility(row, android.view.View.GONE)
                } else {
                    v.setViewVisibility(row, android.view.View.VISIBLE)
                    v.setTextViewText(title, item.title.ifBlank { "Video" })
                    v.setTextViewText(time, "${WatchRules.formatClock(item.position)} / ${WatchRules.formatClock(item.duration)}")
                    val open = Intent(Intent.ACTION_VIEW, Uri.parse(item.url), context, MainActivity::class.java)
                    v.setOnClickPendingIntent(row, PendingIntent.getActivity(context, i, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
            }
            val app = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (app != null) v.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(context, HEADER_REQUEST, app, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }
    }
}

/** The widget's data: at most three https video entries (pure, unit-tested). */
object WidgetSnapshot {
    const val MAX = 3

    data class Item(val url: String, val title: String, val position: Double, val duration: Double)

    fun encode(list: List<WatchPosition>): String = JSONArray().also { a ->
        list.filter { it.url.startsWith("https://") }.take(MAX).forEach { a.put(JSONObject().put("u", it.url).put("t", it.title.take(120)).put("p", it.position).put("d", it.duration)) }
    }.toString()

    fun decode(raw: String?): List<Item> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until minOf(a.length(), MAX)).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val u = o.optString("u").takeIf { it.startsWith("https://") && it.length < 2048 } ?: return@mapNotNull null
            Item(u, o.optString("t").take(120), o.optDouble("p", 0.0), o.optDouble("d", 0.0))
        }
    }.getOrDefault(emptyList())
}
