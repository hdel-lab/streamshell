package com.streamshell.car

import android.app.Presentation
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.streamshell.R
import com.streamshell.core.browser.BrowserEngine
import com.streamshell.core.browser.PageKind
import com.streamshell.core.browser.SiteApp
import com.streamshell.core.media.MediaState
import com.streamshell.core.media.PlayerScripts

/**
 * StreamShell on the car screen. Android Auto gives a navigation app a surface to draw on; a private virtual display
 * is put on that surface and a [Presentation] on it holds the browser (the same engine and YouTube scripts as on the
 * phone). Taps and drags from the car screen are passed on as touches.
 *
 * Layout (0.6.0): a control rail at the left or right edge (search, home, back, scroll up/down), the page on the rest,
 * and on a video page a play bar at the bottom (−10 s, play/pause, +10 s, time and progress). The virtual display's
 * density is chosen by [CarDisplay.optimalDpi], not taken from the car.
 *
 * While [gate] does not allow video, a black cover hides the page (the sound keeps playing) and taps on the page are
 * ignored; the rail and the play bar stay usable.
 */
class CarBrowser(
    private val context: Context,
    private val engine: BrowserEngine,
    private val gate: ParkGate,
    /** Diagnostics line (only written while Diagnostics is on). */
    private val log: (String) -> Unit = {}
) : SurfaceCallback {
    private val main = Handler(Looper.getMainLooper())
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: LinearLayout? = null
    private var pageFrame: FrameLayout? = null
    private var cover: View? = null
    private var coverText: TextView? = null
    private var nowTitle: TextView? = null
    private var nowArtist: TextView? = null
    private var coverControls: View? = null
    private var coverPlay: ImageButton? = null
    private var sourceButton: ImageButton? = null
    private var bar: View? = null
    private var playButton: ImageButton? = null
    private var timeText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var playing = true

    /** What the car shows: YouTube or YouTube Music (rail button; first one from the setting "Android Auto: opens with"). */
    @Volatile var source: SiteApp = SiteApp.YOUTUBE

    /** Rail at the left edge (setting "Android Auto: controls on the left"); read when the surface is created. */
    @Volatile var railLeft = true

    /** Called by the rail's Search button (the screen opens the car keyboard). */
    var onSearchRequested: () -> Unit = {}

    /** Chosen density and page size, for the log / About. */
    var describe: String = ""
        private set

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        release()
        val dm = context.getSystemService(DisplayManager::class.java) ?: return
        val dpi = CarDisplay.optimalDpi(container.width, container.height, container.dpi)
        describe = "car screen ${container.width}×${container.height} px, car dpi ${container.dpi} → $dpi " +
            "(${CarDisplay.dp(container.width, dpi)}×${CarDisplay.dp(container.height, dpi)} dp)"
        val vd = dm.createVirtualDisplay(
            "StreamShell car",
            container.width,
            container.height,
            dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        ) ?: run {
            log("surface: virtual display could not be created")
            return
        }
        log("surface available: $describe")
        display = vd
        val p = Presentation(context, vd.display)
        val c = p.context
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BG)
        }
        val column = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val frame = FrameLayout(c).apply { setBackgroundColor(Color.BLACK) }
        frame.addView(engine.attach(PAGE_ID, c), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val cv = coverView(c)
        frame.addView(cv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val b = playBar(c)
        column.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(c, CarDisplay.BAR_DP)))
        val rail = rail(c)
        val railParams = LinearLayout.LayoutParams(px(c, CarDisplay.RAIL_DP), ViewGroup.LayoutParams.MATCH_PARENT)
        val columnParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        if (railLeft) {
            box.addView(rail, railParams)
            box.addView(column, columnParams)
        } else {
            box.addView(column, columnParams)
            box.addView(rail, railParams)
        }
        p.setContentView(box)
        runCatching { p.show() }.onFailure { log("surface: presentation could not be shown (${it.javaClass.simpleName})") }
        presentation = p
        root = box
        pageFrame = frame
        bar = b
        engine.setKeepVisible(PAGE_ID)
        refresh()
        main.removeCallbacks(tick)
        main.post(tick)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        // Android Auto draws its own buttons over the edges; rail, page and bar use the free area.
        val r = root ?: return
        log("visible area ${visibleArea.left},${visibleArea.top}–${visibleArea.right},${visibleArea.bottom}")
        main.post { r.setPadding(visibleArea.left, visibleArea.top, (r.width - visibleArea.right).coerceAtLeast(0), (r.height - visibleArea.bottom).coerceAtLeast(0)) }
    }

    /**
     * The surface (picture) ends — Android Auto closed the screen or switched app. Only the presentation goes; the page
     * and its sound belong to the car session (engine) and keep running until the session ends.
     */
    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        log("surface destroyed (picture gone, page and sound stay with the car session)")
        release()
    }

    private var coverShown: Boolean? = null
    private var ignoredTapLogged = false

    /**
     * The cover while the picture may not show: a short message, what plays (title and artist from the page's media
     * session — text only, no picture) and three large buttons: previous, play/pause, next. Sound controls only.
     */
    private fun coverView(c: Context): View = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
        setPadding(px(c, 32), px(c, 24), px(c, 32), px(c, 24))
        val msg = TextView(c).apply {
            setTextColor(MUTED)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val title = TextView(c).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val artist = TextView(c).apply {
            setTextColor(MUTED)
            textSize = 20f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val big = LinearLayout.LayoutParams(px(c, COVER_BUTTON_DP), px(c, COVER_BUTTON_DP)).apply { setMargins(px(c, 12), 0, px(c, 12), 0) }
        row.addView(button(c, R.drawable.ic_car_prev, "Previous") { previous() }, big)
        val play = button(c, R.drawable.ic_notif_pause, "Pause") { togglePlay() }
        row.addView(play, LinearLayout.LayoutParams(big))
        row.addView(button(c, R.drawable.ic_car_next, "Next") { next() }, LinearLayout.LayoutParams(big))
        addView(msg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(c, 16) })
        addView(artist, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(c, 4) })
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(c, 20) })
        cover = this
        coverText = msg
        nowTitle = title
        nowArtist = artist
        coverControls = row
        coverPlay = play
    }

    /** Shows or hides the cover after the gate changed. */
    fun refresh() {
        val c = cover ?: return
        val msg = coverText ?: return
        main.post {
            val show = !gate.videoAllowed
            if (coverShown != show) {
                coverShown = show
                ignoredTapLogged = false
                log(if (show) "cover on: picture hidden, page taps ignored; sound, rail and play bar unchanged" else "cover off: picture shown, page taps on")
            }
            c.visibility = if (show) View.VISIBLE else View.GONE
            msg.text = if (gate.needsConfirmation) {
                "The picture shows only while the car is parked. Tap \"Parked\" when you have stopped."
            } else {
                "Sound only while driving — the picture comes back when the car stands still."
            }
        }
    }

    // ---------------------------------------------------------------- controls

    private fun rail(c: Context): View = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(BG)
        setPadding(0, px(c, 8), 0, px(c, 8))
        val src = button(c, sourceIcon(), sourceLabel()) { switchSource() }
        sourceButton = src
        addView(src, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        listOf<Triple<Int, String, () -> Unit>>(
            Triple(R.drawable.ic_car_search, "Search") { onSearchRequested() },
            Triple(R.drawable.ic_car_home, "Home") { home() },
            Triple(R.drawable.ic_car_back, "Back") { back() },
            Triple(R.drawable.ic_car_up, "Scroll up") { scroll(-1) },
            Triple(R.drawable.ic_car_down, "Scroll down") { scroll(1) }
        ).forEach { (icon, label, run) ->
            addView(button(c, icon, label, run), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun playBar(c: Context): View = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(BG)
        setPadding(px(c, 12), 0, px(c, 16), 0)
        visibility = View.GONE
        val size = LinearLayout.LayoutParams(px(c, CarDisplay.BAR_DP - 8), ViewGroup.LayoutParams.MATCH_PARENT)
        addView(button(c, R.drawable.ic_car_prev, "Previous") { previous() }, size)
        addView(button(c, R.drawable.ic_notif_replay10, "Back 10 seconds") { engine.seekMedia(PAGE_ID, -SEEK_S) }, LinearLayout.LayoutParams(size))
        val play = button(c, R.drawable.ic_notif_pause, "Pause") { togglePlay() }
        addView(play, LinearLayout.LayoutParams(size))
        addView(button(c, R.drawable.ic_notif_forward10, "Forward 10 seconds") { engine.seekMedia(PAGE_ID, SEEK_S) }, LinearLayout.LayoutParams(size))
        addView(button(c, R.drawable.ic_car_next, "Next") { next() }, LinearLayout.LayoutParams(size))
        val pb = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = CarDisplay.PROGRESS_MAX
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(TRACK)
            isClickable = false
        }
        addView(pb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(c, 16), 0, px(c, 16), 0) })
        val t = TextView(c).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        playButton = play
        progressBar = pb
        timeText = t
    }

    private fun button(c: Context, icon: Int, label: String, onTap: () -> Unit): ImageButton = ImageButton(c).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        val pad = px(c, 18)
        setPadding(pad, pad, pad, pad)
        contentDescription = label
        val shape = GradientDrawable().apply {
            cornerRadius = px(c, 18).toFloat()
            setColor(Color.TRANSPARENT)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = px(c, 18).toFloat()
            setColor(Color.WHITE)
        }
        background = RippleDrawable(ColorStateList.valueOf(PRESSED), shape, mask)
        setOnClickListener { onTap() }
    }

    /** Every second while the car screen shows: play bar on video pages, its time, progress and play/pause icon. */
    private val tick = object : Runnable {
        override fun run() {
            if (root == null) return
            val video = PageKind.isVideoPage(engine.pages.value[PAGE_ID]?.url)
            bar?.visibility = if (video) View.VISIBLE else View.GONE
            if (cover?.visibility == View.VISIBLE) {
                engine.nowPlaying(PAGE_ID) { np ->
                    nowTitle?.text = np?.first ?: "Nothing playing"
                    nowArtist?.text = np?.second.orEmpty()
                }
                engine.probeMedia(PAGE_ID) { st -> showPlaying(st == MediaState.PLAYING) }
            }
            if (video) {
                engine.run(PAGE_ID, PlayerScripts.POSITION) { r ->
                    val p = PlayerScripts.parsePositionAndId(r)
                    progressBar?.progress = p?.let { CarDisplay.progress(it.first, it.second) } ?: 0
                    timeText?.text = p?.let { CarDisplay.clock(it.first) + " / " + CarDisplay.clock(it.second) }.orEmpty()
                }
                engine.probeMedia(PAGE_ID) { st -> showPlaying(st == MediaState.PLAYING) }
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun showPlaying(now: Boolean) {
        playing = now
        listOfNotNull(playButton, coverPlay).forEach { b ->
            b.setImageResource(if (now) R.drawable.ic_notif_pause else R.drawable.ic_notif_play)
            b.contentDescription = if (now) "Pause" else "Play"
        }
    }

    // ---------------------------------------------------------------- input from the car screen

    override fun onClick(x: Float, y: Float) {
        val r = root ?: return
        main.post {
            // While the picture is covered, taps on the page are ignored; rail and play bar still work.
            if (!gate.videoAllowed && inside(pageFrame, r, x, y) && !inside(coverControls, r, x, y)) {
                if (!ignoredTapLogged) log("tap on the covered page ignored (further ones not logged until the cover changes)")
                ignoredTapLogged = true
                return@post
            }
            val t = SystemClock.uptimeMillis()
            send(r, t, t, MotionEvent.ACTION_DOWN, x, y)
            send(r, t, t + 60, MotionEvent.ACTION_UP, x, y)
        }
    }

    private var dragDown = 0L
    private var dragX = 0f
    private var dragY = 0f
    private var dragging = false
    private val endDrag = Runnable {
        root?.let { send(it, dragDown, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, dragX, dragY) }
        dragging = false
    }

    /** Pan gestures (in pan mode) become a finger drag on the page: scrolling the feed, the chat, the description. */
    override fun onScroll(distanceX: Float, distanceY: Float) {
        val r = root
        val f = pageFrame
        if (!gate.videoAllowed || r == null || f == null) return
        main.post {
            val now = SystemClock.uptimeMillis()
            val area = boundsIn(f, r)
            if (!dragging) {
                dragging = true
                dragDown = now
                dragX = area.exactCenterX()
                dragY = area.exactCenterY()
                send(r, dragDown, now, MotionEvent.ACTION_DOWN, dragX, dragY)
            }
            dragX = (dragX - distanceX).coerceIn(area.left + 1f, area.right - 1f)
            dragY = (dragY - distanceY).coerceIn(area.top + 1f, area.bottom - 1f)
            send(r, dragDown, now, MotionEvent.ACTION_MOVE, dragX, dragY)
            main.removeCallbacks(endDrag)
            main.postDelayed(endDrag, 120)
        }
    }

    private fun send(target: View, down: Long, time: Long, action: Int, x: Float, y: Float) {
        val e = MotionEvent.obtain(down, time, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        runCatching { target.dispatchTouchEvent(e) }
        e.recycle()
    }

    /** Bounds of [v] in [root]'s coordinates. */
    private fun boundsIn(v: View, root: View): Rect {
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationInWindow(a)
        root.getLocationInWindow(b)
        val l = a[0] - b[0]
        val t = a[1] - b[1]
        return Rect(l, t, l + v.width, t + v.height)
    }

    private fun inside(v: View?, root: View, x: Float, y: Float): Boolean = v != null && boundsIn(v, root).contains(x.toInt(), y.toInt())

    // ---------------------------------------------------------------- actions

    fun home() = engine.load(PAGE_ID, homeOf(source))

    fun next() = engine.mediaAction(PAGE_ID, com.streamshell.core.browser.MediaSessionScripts.NEXT) { how -> log("next track ($how)") }
    fun previous() = engine.mediaAction(PAGE_ID, com.streamshell.core.browser.MediaSessionScripts.PREVIOUS) { how -> log("previous track ($how)") }

    /** YouTube ⇄ YouTube Music: the other one's home page opens in the car's page (one page, one sound). */
    fun switchSource() {
        source = if (source == SiteApp.YOUTUBE) SiteApp.YOUTUBE_MUSIC else SiteApp.YOUTUBE
        log("source: ${source.title}")
        sourceButton?.apply {
            setImageResource(sourceIcon())
            contentDescription = sourceLabel()
        }
        home()
    }

    private fun sourceIcon() = if (source == SiteApp.YOUTUBE) R.drawable.ic_car_music else R.drawable.ic_car_youtube
    private fun sourceLabel() = if (source == SiteApp.YOUTUBE) "Switch to YouTube Music" else "Switch to YouTube"
    fun back() = engine.goBack(PAGE_ID)
    fun scroll(direction: Int) {
        if (gate.videoAllowed) engine.run(PAGE_ID, CarDisplay.scrollScript(direction))
    }
    fun search(query: String) {
        if (query.isNotBlank()) engine.load(PAGE_ID, source.searchUrlFor(query))
    }

    /** Pauses or plays depending on what the page really does (the video may have been paused on the page itself). */
    fun togglePlay(onDone: () -> Unit = {}) {
        engine.probeMedia(PAGE_ID) { st ->
            val play = st != MediaState.PLAYING
            if (play) engine.playMedia(PAGE_ID) else engine.pauseMedia(PAGE_ID)
            main.post { showPlaying(play) }
            onDone()
        }
    }

    val isPlaying get() = playing

    private var started = false
    fun start() {
        if (!started) {
            started = true
            engine.load(PAGE_ID, homeOf(source))
        }
    }

    /** Ends the picture only (presentation and virtual display); the engine and its sound are not touched. */
    fun release() {
        coverShown = null
        main.removeCallbacks(endDrag)
        main.removeCallbacks(tick)
        runCatching { engine.detach(PAGE_ID) }
        runCatching { presentation?.dismiss() }
        runCatching { display?.release() }
        presentation = null
        display = null
        root = null
        pageFrame = null
        cover = null
        coverText = null
        nowTitle = null
        nowArtist = null
        coverControls = null
        coverPlay = null
        sourceButton = null
        bar = null
        playButton = null
        timeText = null
        progressBar = null
    }

    private fun px(c: Context, dp: Int): Int = (dp * c.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val PAGE_ID = 1L
        const val HOME = "https://m.youtube.com/"
        const val MUSIC_HOME = "https://music.youtube.com/"

        fun homeOf(source: SiteApp) = if (source == SiteApp.YOUTUBE_MUSIC) MUSIC_HOME else HOME
        private const val COVER_BUTTON_DP = 88
        private const val MUTED = 0xFFB0B0B0.toInt()
        private const val SEEK_S = 10
        private const val TICK_MS = 1000L
        private const val BG = 0xFF121212.toInt()
        private const val ACCENT = 0xFF4C8DFF.toInt()
        private const val TRACK = 0x40FFFFFF
        private const val PRESSED = 0x33FFFFFF
    }
}
