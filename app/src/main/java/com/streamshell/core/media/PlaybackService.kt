package com.streamshell.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.streamshell.MainActivity
import com.streamshell.R

/**
 * Keeps web media playing while StreamShell is in the background or the screen is off:
 *  - foreground service of type mediaPlayback (Android requires it for audio that outlives the UI),
 *  - a MediaSession + media notification with the video's picture (YouTube), -10 s, play/pause, +10 s and Exit
 *    (lock screen, headset buttons, Bluetooth; next/previous on headsets also jump 10 s),
 *  - audio focus is left to the WebView (it pauses for calls/other apps itself; see onCreate),
 *  - "becoming noisy": pauses when headphones are unplugged.
 * The page itself is controlled only through [MediaBridge] (standard HTML5 play()/pause()).
 */
class PlaybackService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession
    private var noisyRegistered = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) MediaBridge.controls?.pause()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "StreamShell").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    MediaBridge.controls?.play()
                }
                override fun onPause() {
                    MediaBridge.controls?.pause()
                }
                override fun onStop() {
                    MediaBridge.controls?.stop()
                }
                override fun onRewind() {
                    MediaBridge.controls?.seekBy(-MediaBridge.seekSeconds)
                }
                override fun onFastForward() {
                    MediaBridge.controls?.seekBy(MediaBridge.seekSeconds)
                }
                override fun onSkipToPrevious() {
                    MediaBridge.controls?.track(false)
                }
                override fun onSkipToNext() {
                    MediaBridge.controls?.track(true)
                }
            })
            isActive = true
        }
        MediaBridge.listener = { main.post { refresh() } }
        // No audio focus request here: the WebView requests audio focus itself whenever a video plays. Asking for it
        // as well made the two take focus from each other, and each loss paused the video ("starts, then stops").
        // Calls and other apps still pause playback through the WebView's own focus handling.
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> MediaBridge.controls?.play()

            ACTION_PAUSE -> MediaBridge.controls?.pause()

            ACTION_STOP -> {
                MediaBridge.controls?.stop()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REWIND -> MediaBridge.controls?.seekBy(-MediaBridge.seekSeconds)

            ACTION_FORWARD -> MediaBridge.controls?.seekBy(MediaBridge.seekSeconds)

            ACTION_EXIT -> {
                // Stop playback, remove the notification and close the app's task.
                val c = MediaBridge.controls
                if (c != null) c.exit() else MediaBridge.onExit?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        refresh()
        // The page owns the media; after a process kill there is nothing to resume, so don't restart.
        return START_NOT_STICKY
    }

    private fun refresh() {
        val playing = MediaBridge.state == MediaState.PLAYING
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
        loadThumbnail()
        session.setMetadata(
            MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, MediaBridge.title.ifBlank { "StreamShell" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, siteLabel())
                .apply {
                    art?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                    }
                }
                .build()
        )
        // Without POST_NOTIFICATIONS (Android 13+) the service keeps running; only the visible notification is missing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun action(icon: Int, title: String, act: String): Notification.Action {
        val pi = PendingIntent.getService(this, act.hashCode(), Intent(this, PlaybackService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controls for media that keeps playing in the background"
                    setShowBadge(false)
                }
            )
        }
        val playing = MediaBridge.state == MediaState.PLAYING
        val step = MediaBridge.seekSeconds
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_media)
            .setContentTitle(MediaBridge.title.ifBlank { "StreamShell" })
            .setContentText(siteLabel())
            .setSubText(if (playing) "Playing" else "Paused")
            .apply { art?.let { setLargeIcon(it) } }
            .setContentIntent(open)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // -10 s · play/pause · +10 s · exit (closes StreamShell)
            .addAction(action(if (step == 10) R.drawable.ic_notif_replay10 else R.drawable.ic_notif_rewind, "Back $step seconds", ACTION_REWIND))
            .addAction(
                if (playing) {
                    action(R.drawable.ic_notif_pause, "Pause", ACTION_PAUSE)
                } else {
                    action(R.drawable.ic_notif_play, "Play", ACTION_PLAY)
                }
            )
            .addAction(action(if (step == 10) R.drawable.ic_notif_forward10 else R.drawable.ic_notif_fastforward, "Forward $step seconds", ACTION_FORWARD))
            .addAction(action(R.drawable.ic_notif_close, "Exit", ACTION_EXIT))
            .setDeleteIntent(PendingIntent.getService(this, 7, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    // ---------------------------------------------------------------- thumbnail

    @Volatile private var art: android.graphics.Bitmap? = null

    @Volatile private var artUrl: String? = null
    private val artLoader = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun siteLabel(): String = when {
        MediaBridge.thumbnailUrl() != null -> "YouTube"
        else -> com.streamshell.core.browser.UrlHosts.hostOf(MediaBridge.pageUrl)?.removePrefix("www.") ?: "StreamShell"
    }

    /**
     * Fetches YouTube's still picture of the playing video once per video (i.ytimg.com, the same image the page
     * shows; nothing else is sent) and shows it in the notification and on the lock screen.
     */
    private fun loadThumbnail() {
        val url = MediaBridge.thumbnailUrl()
        if (url == artUrl) return
        artUrl = url
        art = null
        if (url == null) return
        artLoader.execute {
            val bmp = runCatching { Thumbnails.fetch(url) }.getOrNull()
            main.post {
                if (artUrl == url && bmp != null) {
                    art = bmp
                    refresh()
                }
            }
        }
    }

    override fun onDestroy() {
        artLoader.shutdownNow()
        running = false
        MediaBridge.listener = null
        if (noisyRegistered) runCatching { unregisterReceiver(noisy) }
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 42
        const val ACTION_PLAY = "com.streamshell.media.PLAY"
        const val ACTION_PAUSE = "com.streamshell.media.PAUSE"
        const val ACTION_STOP = "com.streamshell.media.STOP"
        const val ACTION_REWIND = "com.streamshell.media.REWIND"
        const val ACTION_FORWARD = "com.streamshell.media.FORWARD"
        const val ACTION_EXIT = "com.streamshell.media.EXIT"
        const val SEEK_SECONDS = 10

        @Volatile var running = false
            private set

        /** Only called while StreamShell is in the foreground (Android 12+ forbids starting it from the background). */
        fun start(context: Context) {
            running = true
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        }

        fun stop(context: Context) {
            running = false
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}

/** Downloads and scales a notification picture. HTTPS only, YouTube's image host only, size-limited. */
internal object Thumbnails {
    private const val MAX_BYTES = 1_000_000

    fun allowed(url: String): Boolean {
        val u = runCatching { java.net.URL(url) }.getOrNull() ?: return false
        return u.protocol == "https" && u.host == "i.ytimg.com"
    }

    /** [crop16x9] off for timeline preview sheets: their frames are cut out by position, the sheet must stay whole. */
    fun fetch(url: String, crop16x9: Boolean = true): android.graphics.Bitmap? {
        if (!allowed(url)) return null
        val c = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.instanceFollowRedirects = false
        c.useCaches = true
        return try {
            if (c.responseCode != 200) return null
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            if (!crop16x9) return bmp
            // hqdefault is 480x360 with black bars; crop to 16:9 so the picture fills the notification.
            val h = (bmp.width * 9 / 16).coerceAtMost(bmp.height)
            android.graphics.Bitmap.createBitmap(bmp, 0, (bmp.height - h) / 2, bmp.width, h)
        } finally {
            c.disconnect()
        }
    }
}
