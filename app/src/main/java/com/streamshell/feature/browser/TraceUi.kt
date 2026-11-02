package com.streamshell.feature.browser

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamshell.core.diag.Gestures
import kotlin.math.roundToInt

/**
 * "Trace everything" on StreamShell's own screens (0.6.1): every touch, swipe and pinch with the element it started on
 * (its test tag, e.g. `bar-item-speed`; never its text) and where (dp). Observes only: nothing is consumed.
 */
fun Modifier.traceTouches(enabled: Boolean, owner: () -> SemanticsOwner?, density: Float, onLine: (String) -> Unit): Modifier = if (!enabled) {
    this
} else {
    pointerInput(density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.position
            val t0 = System.currentTimeMillis()
            var last = start
            var pointers = 1
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                pointers = maxOf(pointers, ev.changes.count { it.pressed })
                ev.changes.firstOrNull { it.id == down.id }?.let { last = it.position }
                if (ev.changes.none { it.pressed }) break
            }
            val d = density.coerceAtLeast(MIN_DENSITY)
            val what = Gestures.classify((last.x - start.x) / d, (last.y - start.y) / d, System.currentTimeMillis() - t0, pointers)
            val tag = runCatching { owner()?.let { tagAt(it, start) } }.getOrNull() ?: "untagged element"
            onLine("$what on $tag at ${(start.x / d).roundToInt()},${(start.y / d).roundToInt()} dp")
        }
    }
}

private const val MIN_DENSITY = 0.5f

/** Innermost element with a test tag under [pos] (later siblings are drawn on top and win). */
fun tagAt(owner: SemanticsOwner, pos: Offset): String? {
    var best: String? = null
    fun visit(n: SemanticsNode) {
        if (!n.boundsInRoot.contains(pos)) return
        n.config.getOrNull(SemanticsProperties.TestTag)?.let { best = it }
        n.children.forEach { visit(it) }
    }
    visit(owner.unmergedRootSemanticsNode)
    return best
}

/**
 * Screen stutter and memory for "Trace everything": frames more than 50 ms apart (the main thread was busy), summed per
 * 5 s; heap use every 30 s. Runs only while the app is in front and tracing is on.
 */
class FrameMonitor(private val onLine: (String) -> Unit) : Choreographer.FrameCallback {
    private var running = false
    private var last = 0L
    private var windowStart = 0L
    private var memoryAt = 0L
    private var janky = 0
    private var worst = 0L

    fun start() {
        if (running) return
        running = true
        last = 0L
        windowStart = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        flush()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val ms = frameTimeNanos / NANOS_PER_MS
        if (last > 0) {
            val gap = ms - last
            if (gap > JANK_MS) {
                janky++
                worst = maxOf(worst, gap)
            }
        }
        last = ms
        if (windowStart == 0L) windowStart = ms
        if (ms - windowStart >= WINDOW_MS) {
            flush()
            windowStart = ms
        }
        if (ms - memoryAt >= MEMORY_MS) {
            memoryAt = ms
            val r = Runtime.getRuntime()
            onLine("memory: heap ${(r.totalMemory() - r.freeMemory()) / MB} of ${r.maxMemory() / MB} MB")
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun flush() {
        if (janky > 0) onLine("jank: $janky frames over 50 ms in 5 s, worst $worst ms")
        janky = 0
        worst = 0
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val JANK_MS = 50L
        const val WINDOW_MS = 5_000L
        const val MEMORY_MS = 30_000L
        const val MB = 1024L * 1024L
    }
}

/** Small LOG button at the right edge on every screen (drag it up or down); shows how many lines the log holds. */
@Composable
fun BoxScope.LogButton(lines: Int, onOpen: () -> Unit) {
    var y by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, y.roundToInt()) }
            .padding(end = 2.dp)
            .background(Color(0xCC000000), RoundedCornerShape(10.dp))
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> y += dy } }
            .clickable(onClickLabel = "Open the diagnostics log", onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("log-button")
    ) {
        Text("LOG\n$lines", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
    }
}
