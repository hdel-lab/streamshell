# StreamShell

An Android video browser (Kotlin, Jetpack Compose, Material 3, Android System WebView).

- **StreamShell start page**: video sites (YouTube, YouTube Music, Vimeo, Dailymotion, Twitch, PeerTube, Internet Archive, ARTE), Google sign-in shortcut, bookmarks, recent pages.
- **Browser**: one address/search bar, back, forward, start page, reload/stop, tabs (up to 10, restored after restart), bookmarks, history (local, can be switched off), share, downloads via Android's download manager. One toolbar only: on phones a bottom bar, on tablets/unfolded foldables everything in the top bar. No StreamShell-made YouTube navigation on top of YouTube's own.
- **Sign-in**: you sign in on Google's own pages inside the browser; cookies persist across restarts; "Clear browsing data" signs you out.
- **YouTube app mode**: YouTube and YouTube Music open like apps inside the app: no browser bars, the site's sections at the bottom (YouTube: Home, Shorts, Live, Subscriptions, You; Music: Home, Explore, Library) and a More menu with search and all browser functions. YouTube's own top/bottom bars are hidden so pages start at the player.
- **Mini player**: minimize a YouTube video into a floating window (drag it to either side or hide it at the edge) and keep browsing.
- **Skip YouTube ads**: mutes and jumps over YouTube video ads, presses Skip, hides ad cards (switchable; against YouTube's terms).
- **SponsorBlock**: skips community-marked sponsor reads, self-promotion and reminders inside YouTube videos (not YouTube's own ads); categories in Settings.
- **Video**: fullscreen with landscape and hidden system bars, picture-in-picture (automatic when you leave while a video plays, or from the menu, with a play/pause button), background playback with a media notification, lock-screen/headset controls, audio focus and "headphones unplugged" handling.
- **YouTube Music**: app mode with StreamShell's bar, sliding player bar (previous/next), sideways swipe for tracks, mini player.
- **Android Auto (unofficial)**: YouTube / YouTube Music on the car screen with a side rail and play bar; the picture shows only while parked (see `docs/ANDROID_AUTO.md`).
- **Diagnostics**: optional in-memory log of the whole app with a crash report and "save as file" (nothing leaves the device).
- **AdBlock & Privacy**: requests that pages load are checked against EasyPrivacy, URLhaus and the built-in list (EasyList optional) and really blocked; per-site switch in the shield; own block/allow rules; list updates with rollback; statistics of actually blocked requests.

Docs: `docs/SETUP.md`, `docs/DEVICE_TEST_PLAN.md`, `docs/FEATURE_MATRIX.md`, `docs/KNOWN_LIMITATIONS.md`, `docs/FILTERING.md`, `docs/SECURITY_PRIVACY.md`, `docs/BROWSER_AUDIT.md`, `docs/ADR-001-architecture.md`, `docs/BUILD.md`, `docs/DESIGN_SYSTEM.md`.

Status: **personal project, pre-release (0.6.x)**. Many features are tested with JVM tests and page-script simulations
only; see `docs/KNOWN_LIMITATIONS.md` (per-version notes) and `docs/DEVICE_TEST_PLAN.md`.

## Build

Requirements: JDK 21, Android SDK (compileSdk 37). See `docs/BUILD.md`.

```
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash tools/js-sim/run.sh          # page-script simulations (Node.js)
```

Release builds are signed with a private key that is **not** part of this repository: create `keystore.properties`
(storeFile, storePassword, keyAlias, keyPassword) locally; it and `signing/` are git-ignored. Without it the release
build is unsigned.

## Repository layout

| Path | Content |
|---|---|
| `app/src/main/java/com/streamshell/core` | Engine (WebView), page scripts, settings, privacy filter, media, diagnostics |
| `app/src/main/java/com/streamshell/feature` | Screens (browser, settings, privacy) |
| `app/src/main/java/com/streamshell/car` | Android Auto part |
| `app/src/test` | JVM / Robolectric tests |
| `tools/js-sim` | Node simulations of the page scripts |
| `config` | detekt configuration and baseline |
| `docs` | Specs, limitations, test plan, architecture |

## License

GPL-3.0 — see `LICENSE`. Third-party notices: the font license in `docs/FONT-LICENSE-Nunito-OFL.txt`; filter lists are
downloaded at run time under their own licenses (`docs/FILTERING.md`).

