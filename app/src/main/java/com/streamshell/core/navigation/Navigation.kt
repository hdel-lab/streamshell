package com.streamshell.core.navigation

import com.streamshell.core.model.Overlay

/** Which panel is open above the browser, and (for Privacy) which panel it was opened from. */
data class NavState(
    val overlay: Overlay = Overlay.None,
    val returnTo: Overlay = Overlay.None
)

sealed interface NavAction {
    data class Open(val overlay: Overlay) : NavAction
    data object CloseOverlay : NavAction
}

object NavReducer {
    fun reduce(state: NavState, action: NavAction): NavState = when (action) {
        is NavAction.Open -> when {
            action.overlay == Overlay.None -> NavState()

            // Privacy can be reached from Settings or from the page shield; it returns to where it came from.
            action.overlay == Overlay.Privacy -> NavState(Overlay.Privacy, returnTo = state.overlay.takeIf { it != Overlay.Privacy } ?: state.returnTo)

            else -> NavState(action.overlay)
        }

        NavAction.CloseOverlay -> when (state.overlay) {
            Overlay.Privacy -> NavState(state.returnTo)
            else -> NavState()
        }
    }
}

/** What system Back should do, in strict priority order. */
sealed interface BackAction {
    data object ExitFullscreenVideo : BackAction
    data object CloseOverlay : BackAction

    /** Go back in the current tab's page history. */
    data object PageBack : BackAction

    /** The tab has no earlier page but was opened from the start page: show the start page again. */
    data object ShowStartPage : BackAction

    /** Nothing left to undo: let the system close/background the app. */
    data object Finish : BackAction
}

object BackPolicy {
    fun resolve(fullscreenVideo: Boolean, nav: NavState, pageCanGoBack: Boolean, canReturnToStart: Boolean): BackAction = when {
        fullscreenVideo -> BackAction.ExitFullscreenVideo
        nav.overlay != Overlay.None -> BackAction.CloseOverlay
        pageCanGoBack -> BackAction.PageBack
        canReturnToStart -> BackAction.ShowStartPage
        else -> BackAction.Finish
    }
}
