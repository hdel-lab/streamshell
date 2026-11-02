package com.streamshell.core.adaptive

enum class WidthClass { Compact, Medium, Expanded }
enum class HeightClass { Compact, Medium, Expanded }
enum class NavigationStyle { BottomBar, Rail }
enum class AccountPresentation { FullScreen, SidePanel }
enum class FoldOrientation { Horizontal, Vertical }
enum class FoldState { Flat, HalfOpened }

/**
 * Platform-independent copy of what androidx.window reports for a fold/hinge. Bounds are in
 * window pixels. Not every foldable reports one, so everything here is optional input.
 */
data class FoldInfo(
    val state: FoldState,
    val orientation: FoldOrientation,
    val isSeparating: Boolean,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int
)

/** Two-pane split at a physical fold. */
sealed interface HingeSplit {
    /** Horizontal hinge, half-open: page on top, controls below. Sizes are window pixels. */
    data class Tabletop(val topPx: Int, val bottomStartPx: Int) : HingeSplit

    /** Vertical hinge, half-open: page on the left, side panel on the right. */
    data class Book(val leftPx: Int, val rightStartPx: Int) : HingeSplit
}

data class WindowLayoutSpec(
    val widthClass: WidthClass,
    val heightClass: HeightClass,
    val navigation: NavigationStyle,
    val account: AccountPresentation,
    val hinge: HingeSplit?
)

/** Pure mapping from window size (dp) and optional fold info to a layout. Nothing hardcoded per device. */
object WindowLayoutPolicy {
    const val MEDIUM_MIN_DP = 600
    const val EXPANDED_MIN_DP = 840
    const val HEIGHT_MEDIUM_MIN_DP = 480
    const val HEIGHT_EXPANDED_MIN_DP = 900

    fun widthClass(widthDp: Int) = when {
        widthDp < MEDIUM_MIN_DP -> WidthClass.Compact
        widthDp < EXPANDED_MIN_DP -> WidthClass.Medium
        else -> WidthClass.Expanded
    }

    fun heightClass(heightDp: Int) = when {
        heightDp < HEIGHT_MEDIUM_MIN_DP -> HeightClass.Compact
        heightDp < HEIGHT_EXPANDED_MIN_DP -> HeightClass.Medium
        else -> HeightClass.Expanded
    }

    fun compute(widthDp: Int, heightDp: Int, fold: FoldInfo?): WindowLayoutSpec {
        val wc = widthClass(widthDp)
        val hinge = hingeSplit(fold)
        return WindowLayoutSpec(
            widthClass = wc,
            heightClass = heightClass(heightDp),
            navigation = if (wc == WidthClass.Compact) NavigationStyle.BottomBar else NavigationStyle.Rail,
            account = if (wc == WidthClass.Expanded) AccountPresentation.SidePanel else AccountPresentation.FullScreen,
            hinge = hinge
        )
    }

    /** Only a half-open, separating fold with sane bounds splits the layout. */
    fun hingeSplit(fold: FoldInfo?): HingeSplit? {
        if (fold == null || fold.state != FoldState.HalfOpened || !fold.isSeparating) return null
        return when (fold.orientation) {
            FoldOrientation.Horizontal ->
                if (fold.boundsTop > 0 && fold.boundsBottom >= fold.boundsTop) {
                    HingeSplit.Tabletop(fold.boundsTop, fold.boundsBottom)
                } else {
                    null
                }

            FoldOrientation.Vertical ->
                if (fold.boundsLeft > 0 && fold.boundsRight >= fold.boundsLeft) {
                    HingeSplit.Book(fold.boundsLeft, fold.boundsRight)
                } else {
                    null
                }
        }
    }
}
