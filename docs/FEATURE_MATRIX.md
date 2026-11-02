# Feature matrix

"Tested" = automated JVM tests (Robolectric, including the WebView shadow for engine wiring). "On device" = verified on a real phone: **nothing yet** (no emulator available on the build machine; see `DEVICE_TEST_PLAN.md`).

| Feature | Implemented | Tested | On device |
|---|---|---|---|
| Start page, quick sites, recent, bookmarks | yes | UI | no |
| Address/search bar (http upgrade, search engines) | yes | unit + UI | no |
| Back / forward / start page / reload / stop | yes | UI + back policy | no |
| Tabs (max 10, restore after restart) | yes | unit + UI | no |
| Bookmarks, history (local, switchable, clearable) | yes | unit + DataStore + UI | no |
| Navigation policy (in-app web, app links on gesture, dangerous schemes refused, intent fallback) | yes | unit + engine | no |
| Google sign-in in the browser, persistent cookies, clear data | yes | settings UI only | **no** |
| Fullscreen video (landscape, immersive, Back) | yes | UI | no |
| Picture-in-picture (auto, menu, play/pause action, source rect) | yes | policy unit + UI | **no** |
| PiP shows only the video (stable web view, scale/crop) | yes | crop math unit + UI (same page node in PiP) | **no** |
| YouTube / YouTube Music app mode (no browser bars, site sections + More menu, YouTube style + offset fixer) | yes | unit (sites, destinations, style/fixer content) + UI | **no** |
| Mini player (minimize video, keep browsing) and floating back/forward in app mode | yes | UI | **no** |
| Skip YouTube ads (mute, jump to end, press Skip, hide ad cards) | yes | unit (script content) + settings UI | **no** |
| Autoplay on YouTube, swipe down to minimize | yes | unit (script) + UI gesture | **no** |
| SponsorBlock (hash-prefix lookup, categories, skip timer, skip message) | yes | unit (API, parsing, cache, script) | **no** |
| Background playback (foreground service, MediaSession, notification, audio focus, noisy) | yes | policy unit | **no** |
| Request filtering at `shouldInterceptRequest`, per-site switch, functional safelist | yes | unit + engine (Robolectric WebView) | no |
| Blocked top-level page prompt | yes | engine + UI | no |
| Lists: update, ETag, rollback, offline cache, WorkManager | yes | unit | no |
| Downloads via DownloadManager | yes | no | no |
| Share page | yes | UI | no |
| Open links from other apps (VIEW intent) | yes | no | no |
| YouTube / YouTube Music persistent instances, video player tab, instance switcher, open on start | yes | unit + UI + JS simulation | no |
| Resume positions, Continue watching, Watch later | yes | unit + UI | no |
| Playback speed, ±10 s, sleep timer, start over | yes | unit + UI | no |
| Double-tap seek (non-YouTube) | yes | UI | no |
| Audio-only background (YouTube quality request), volume boost/normalize | yes | unit (scripts) | no |
| Watch time stats + break reminder | yes | unit | no |
| Takeout subscriptions import + public feeds | yes | unit + UI | no |
| Launcher shortcuts | yes | no | no |
| Tablet split view (player + instance) | yes | UI | no |
| Backup / restore (JSON) | yes | unit | no |
| Feed visibility shield, tap guard, feed previews switch (0.5.0) | yes | JS simulation + unit | no |
| Fullscreen exit swipe/button, pinch zoom (0.5.0) | yes | unit (script) | no |
| Mini player swipe-away, progress, leave-player option (0.5.0) | yes | unit | no |
| Speed per channel, repeat / A–B, captions, next chapter (0.5.0) | yes | unit + JS simulation | no |
| Shorts as videos, hide watched, blocked channels/words (0.5.0) | yes | unit + JS simulation | no |
| Local playlists, Library panel (0.5.0) | yes | unit | no |
| Backup v2, NewPipe import (0.5.0) | yes | unit | no |
| Private tabs (own WebView profile where supported) (0.5.0) | yes | unit (rules) | no |
| AMOLED / accent, settings search (0.5.0) | yes | unit + UI | no |
| Player action row, home-screen widget, more launcher shortcuts (0.5.0) | yes | unit (snapshot) | no |
| Prepared player used only after it has loaded; player-state check after in-page switch (0.6.0) | yes | unit (rules) | no |
| Android Auto: own density, side rail, bottom play bar, rail side setting (0.6.0) | yes | unit (rules) | no |
| App-wide diagnostics: trace everything, save as file, summary, LOG button (0.6.1) | yes | unit + page simulation | no |
| Car: separated lifecycles, ParkGate transitions logged (0.6.1) | yes | unit (rules, architecture) | no |
| Crash report kept for the next start (0.6.2) | yes | unit | no |
| Calls pause / navigation voices duck (0.6.2) | yes | unit (rules) + page simulation | no |
| YouTube Music app mode; car source switch, now-playing cover, previous/next (0.6.2) | yes | unit + page simulation | no |
| Bottom bar crash (YouTube ⇄ YouTube Music) fixed; mini player without page buttons (0.6.3) | yes | UI + page simulation | no |
| YouTube Music player bar, track swipe, mini player, headset next/previous (0.6.3) | yes | unit | no |
| Signed release build (com.streamshell) | yes | build | no |

