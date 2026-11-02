package com.streamshell.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The single icon vocabulary of the app: Material Symbols-style outlined glyphs from the
 * (Apache-2.0) Material icon set, with the filled twin ONLY for a selected/active state. Nothing
 * else in the app imports `Icons.*` directly, so styles cannot be mixed by accident.
 */
object StreamIcons {
    val Tabs: ImageVector get() = Icons.Outlined.FilterNone
    val NewTab get() = Icons.Rounded.Add
    val History get() = Icons.Outlined.History
    val Share get() = Icons.Outlined.Share
    val Download get() = Icons.Outlined.FileDownload
    val Next get() = Icons.Rounded.SkipNext
    val Previous get() = Icons.Rounded.SkipPrevious
    val Globe get() = Icons.Outlined.Language
    val Play get() = Icons.Outlined.PlayCircle
    val YouTube get() = Icons.Outlined.SmartDisplay
    val YouTubeMusic get() = Icons.Outlined.MusicNote
    val Queue get() = Icons.AutoMirrored.Outlined.PlaylistPlay
    val Volume get() = Icons.AutoMirrored.Outlined.VolumeUp
    val Brightness get() = Icons.Outlined.LightMode
    val Group get() = Icons.Outlined.Folder
    val YtHome get() = Icons.Outlined.Home
    val YtHomeOn get() = Icons.Rounded.Home
    val YtShorts get() = Icons.Outlined.SlowMotionVideo
    val YtShortsOn get() = Icons.Rounded.SlowMotionVideo
    val YtLive get() = Icons.Outlined.LiveTv
    val YtLiveOn get() = Icons.Rounded.LiveTv
    val YtSubs get() = Icons.Outlined.Subscriptions
    val YtSubsOn get() = Icons.Rounded.Subscriptions
    val YtYou get() = Icons.Outlined.VideoLibrary
    val YtYouOn get() = Icons.Rounded.VideoLibrary
    val Explore get() = Icons.Outlined.Explore
    val ExploreOn get() = Icons.Rounded.Explore
    val Pause get() = Icons.Rounded.Pause
    val PlayArrow get() = Icons.Rounded.PlayArrow
    val ExpandUp get() = Icons.Rounded.KeyboardArrowUp
    val ExpandDown get() = Icons.Rounded.KeyboardArrowDown
    val Account get() = Icons.Outlined.AccountCircle
    val AccountFilled get() = Icons.Rounded.AccountCircle
    val Person get() = Icons.Outlined.Person
    val Back get() = Icons.AutoMirrored.Rounded.ArrowBackIos
    val Forward get() = Icons.AutoMirrored.Rounded.ArrowForwardIos
    val HomePage get() = Icons.Outlined.Home
    val Reload get() = Icons.Rounded.Refresh
    val Stop get() = Icons.Rounded.Close
    val Close get() = Icons.Rounded.Close
    val Pip get() = Icons.Outlined.PictureInPictureAlt
    val PrivacyOff get() = Icons.Outlined.Shield
    val PrivacyOn get() = Icons.Rounded.Shield
    val PrivacyPaused get() = Icons.Outlined.PauseCircle
    val PrivacyUnavailable get() = Icons.Outlined.ErrorOutline
    val Bookmarks get() = Icons.Outlined.Bookmarks
    val Bookmark get() = Icons.Outlined.BookmarkBorder
    val BookmarkFilled get() = Icons.Rounded.Bookmark
    val Settings get() = Icons.Outlined.Settings
    val ThemeSystem get() = Icons.Outlined.SettingsBrightness
    val ThemeLight get() = Icons.Outlined.LightMode
    val ThemeDark get() = Icons.Outlined.DarkMode
    val ClearData get() = Icons.Outlined.DeleteOutline
    val External get() = Icons.AutoMirrored.Outlined.OpenInNew
    val HomeOn get() = Icons.Rounded.Home
    val WatchLater get() = Icons.Outlined.WatchLater
    val WatchTime get() = Icons.Outlined.Timer
    val Speed get() = Icons.Outlined.Speed
    val Chapters get() = Icons.AutoMirrored.Outlined.ViewList
    val More get() = Icons.Rounded.MoreHoriz
    val Secure get() = Icons.Outlined.Lock
    val Search get() = Icons.Rounded.Search
    val SignIn get() = Icons.Outlined.Login
    val SignOut get() = Icons.Outlined.Logout
    val Offline get() = Icons.Outlined.CloudOff
    val Warning get() = Icons.Outlined.Warning
}
