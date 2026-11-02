# Security and privacy

- No telemetry, analytics, crash reporting or backend. Network traffic is only what the pages you open load, filter-list downloads and, for YouTube videos with SponsorBlock on, one request to sponsor.ajay.app carrying the first 4 hex characters of the SHA-256 hash of the video ID (never the ID, account or cookies).
- Passwords are typed into Google's (or a site's) own pages; StreamShell never reads cookies, tokens or form fields and injects no script except the generic media probe/play/pause (`MediaScripts`), which reads only whether `<video>/<audio>` elements play.
- WebView: JavaScript and DOM storage on (needed by sites); file/content access off; mixed content never; certificate errors are never bypassed; Google Safe Browsing on; user agent unchanged; no pop-up windows; camera/microphone/location/MIDI requests denied; third-party cookies on (needed for Google sign-in and embedded players).
- Top-level navigation: only https in the app (http is upgraded); `javascript:`, `file:`, `content:`, `data:` never navigate; app links (`intent:`, `mailto:`, `tel:`, `market:` ...) only on a user tap, and `intent:` links are opened like Chrome does (browsable activities only, no explicit component or selector; an https fallback URL is preferred).
- Stored on the device: settings, bookmarks, history (max 500, can be off), open tabs (URL + title), WebView cookies/cache. "Clear browsing data" removes history, cookies and site data, and cache. Backups are disabled (`allowBackup=false`).
- Permissions: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK (background playback), POST_NOTIFICATIONS (media controls on Android 13+). WAKE_LOCK and RECEIVE_BOOT_COMPLETED are added by WorkManager for the scheduled filter-list updates.

## Notification picture (2026-10-19)
While a YouTube video plays in the background, StreamShell downloads that video's still picture from
`https://i.ytimg.com/vi/<video id>/hqdefault.jpg` (YouTube's image server, the same picture the page shows) to show
it in the media notification. HTTPS only, that one host only, no redirects, max. 1 MB, no cookies are sent.

## Watching data and feeds (0.2.0)
- Positions, Watch later, watch time and imported channels are stored only in the app's DataStore on the device.
- "Latest" requests `https://www.youtube.com/feeds/videos.xml?channel_id=…` for up to 80 imported channels (HTTPS, that
  host and path only, no redirects, max. 1.5 MB each, no cookies). Video pictures come from i.ytimg.com as above.
- Backup files contain bookmarks, history, these lists and settings — no passwords, cookies or sign-ins. They are
  written/read only through the system file picker (no storage permission).
- Release signing key: `signing/streamshell-release.jks` with `keystore.properties` (both git-ignored). Keep them
  safe: updates must be signed with the same key.

## Description card (0.2.13)

The watch-page script reads `videoDetails.shortDescription` from the YouTube player on the page (the same data YouTube shows in its own description panel), only for the video currently open, and shows it as plain text. Nothing is stored or sent anywhere.

## Search suggestions (0.2.14)

While you type in the site search, the typed text and the app language are sent to `suggestqueries.google.com` (YouTube's public suggestion service) over HTTPS, without cookies or account data, at most once per short pause. Recent searches are stored only on this device (up to 20) and can be cleared from the search field.

## Search suggestions (0.2.14)

While you type in the site search, the typed text and the app language are sent to `suggestqueries.google.com` (YouTube's public suggestion service) over HTTPS, without cookies or account data, after a short pause. Recent searches are stored only on this device (up to 20) and can be cleared from the search field.

## Chapter pictures (0.2.17)

For the fullscreen chapter row the app loads YouTube's timeline preview images (the same ones YouTube shows while you drag its timeline) from `i.ytimg.com` over HTTPS, only that host is allowed, at most 1 MB per image, kept in memory only.

## Android Auto (0.3.0)

New permissions: `androidx.car.app.NAVIGATION_TEMPLATES` and `ACCESS_SURFACE` (drawing on the car screen) and `com.google.android.gms.permission.CAR_SPEED` (asked at first use; the speed is used only to decide whether the picture may show, nothing is stored or sent). The car service accepts every Android Auto host (no Play-signed host check) because this build is distributed outside Google Play.

## Community data (0.3.5)

- Return YouTube Dislike (on by default, Settings › YouTube › Show dislikes): the ID of the open video goes to
  returnyoutubedislikeapi.com over https, no cookies or other identifiers.
- DeArrow (off by default): only the first 4 hex characters of the SHA-256 of each video ID go to sponsor.ajay.app.
- Both answers are only shown in the page; nothing is stored except an in-memory cache.

## 0.3.7 changes

- Return YouTube Dislike is off by default (opt-in). Switching it on sends each opened video's ID (and, as with any
  request, the IP address) to returnyoutubedislikeapi.com.
- Search suggestions can be switched off; then nothing typed is sent before you search.
- Third-party cookies can be switched off (Settings › Browsing); on by default for Google sign-in.
- Android Auto car service: only Google's Android Auto / Automotive hosts may bind in release builds.
- Backups are plain JSON (not encrypted); the settings text says so.
- Release APKs: signature schemes v2 + v3, key RSA 4096, certificate SHA-256
  DD:C9:CC:3F:F4:F5:D5:11:BA:8B:9C:EB:62:65:F0:A0:7E:5A:09:05:91:AA:E2:50:A6:01:61:9A:63:2B:15:01.
