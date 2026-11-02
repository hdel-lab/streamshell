# ADR-001: StreamShell architecture (video browser)

Status: accepted, 2026-10-19. Supersedes the earlier YouTube-API client and the web-mode-with-four-tabs design (see `BROWSER_AUDIT.md`).

## Decision
StreamShell is a video browser on Android System WebView with a Compose UI, in one Gradle module with package boundaries:

| Package | Responsibility |
|---|---|
| `core.browser` | `BrowserEngine` (one `MediaWebView` per tab, clients, fullscreen, media probe, sessions), `NavigationPolicy`, `AddressInput`, `Tabs` (pure reducer + serializer), `Downloads`, platform helpers |
| `core.privacy` | filter engine, lists, updates (WorkManager), `PrivacyCoordinator` (request/navigation decisions, counting), `FunctionalHosts` |
| `core.media` | `MediaPolicy`/`MediaScripts` (pure), `MediaBridge`, `PlaybackService` (foreground service, MediaSession, audio focus) |
| `core.library` | bookmarks and history (pure rules + DataStore repository) |
| `core.navigation`, `core.model`, `core.settings`, `core.adaptive` | panels/back policy, settings model and store, window-size layout policy |
| `feature.browser` | `BrowserViewModel` (state holder, engine listener, lifecycle/media orchestration), Compose screens (toolbar, start page, tabs, bookmarks, history, dialogs) |
| `feature.settings`, `feature.privacy` | settings and AdBlock & Privacy screens with their view models |
| `MainActivity` | window only: edge-to-edge, fullscreen orientation/system bars, PiP, lifecycle forwarding, back handling |

Pure logic (policies, reducers, serializers, filter) has no Android dependency and is unit-tested; the engine's WebView wiring is tested on Robolectric's WebView shadow; UI is tested with fakes (a `PageSurface` lambda instead of a WebView).

## Why WebView (not GeckoView)
See the comparison in `BROWSER_AUDIT.md`: WebView is the only engine buildable from this environment and covers every requirement except a guaranteed Google sign-in. GeckoView is the documented fallback if Google refuses sign-in on the target device.

## Why single module
The dependency direction (feature -> core, platform behind small classes) is kept by packages. A Gradle split adds Hilt/KSP wiring risk without a second consumer; it remains a mechanical follow-up.
