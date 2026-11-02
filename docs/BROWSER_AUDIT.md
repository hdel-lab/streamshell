# Browser audit (before the video-browser rewrite, 2026-10-19)

## What the code was
- One Gradle module `:app` (Kotlin 2.0.21, Compose BOM 2024.10, Hilt 2.52, DataStore, WorkManager, androidx.webkit). No Retrofit, Room or Ktor.
- Two parallel UIs in one shell:
  1. **Web mode** (default): `WebViewYouTubeHost`, ONE WebView showing m.youtube.com, plus StreamShell's own bottom bar with Home / Search / Subscriptions / Live.
  2. **Native/API mode** (setting): own lists from the YouTube Data API (`core/youtube`, `feature/feed`), Google OAuth (`core/auth`, Play services), YouTube embed player (`core/player`).
- Filter engine (`core/privacy`): list parsing, updates, WorkManager, diagnostics. In the web view it only **observed** requests (`shouldInterceptRequest` always returned null).

## Concrete causes of the problems
| Symptom | Cause in the code |
|---|---|
| Double navigation | StreamShell's 4-tab bottom bar sits under m.youtube.com, which has its own bottom bar (Home, Shorts, Subscriptions, You). Each tab reloaded a YouTube URL, so the native bar and YouTube's bar competed. |
| Two different products in one app | Native/API mode with its own screens, auth and player lived next to web mode (`ShellScreen` switched on `AppSettings.nativeMode`). |
| Page lost after app switch | `WebViewYouTubeHost.onHostStop()` replaced the page with `about:blank` and reloaded on return (deliberate no-background rule of the API approach). |
| No PiP | Manifest `supportsPictureInPicture="false"`. |
| No background audio | ON_STOP blanked the page; no media service. |
| Blocking did nothing on pages | Requests were only counted, never blocked (API-policy design). |
| Sign-in via native button failed silently | Google Identity authorization needed a Cloud OAuth client; failures were only shown on the Account screen. |
| Only YouTube | `WebNavigationPolicy` allowed only YouTube/Google hosts in-app. |

## Decision
Remove the native/API product and the 4-tab bar; build one browser: StreamShell start page, address bar, back/forward/home, tabs, bookmarks, history, per-page shield, settings. Keep and reuse: design system, adaptive layout policy, filter engine/lists/WorkManager, settings store.

## Engine comparison (decision basis)
| Criterion | Android System WebView + Compose (chosen) | GeckoView (Mozilla) |
|---|---|---|
| Availability in this build environment | in the SDK, works | **maven.mozilla.org blocked (HTTP 403 through the build proxy)**, so it cannot be built here |
| APK size | ~19 MB app | +~50-80 MB per ABI |
| JavaScript/site compatibility | Chromium, updated via Play | Gecko, very good |
| Cookies / persistent sessions | `CookieManager`, persisted to disk | own profile, persisted |
| Google sign-in | Google may refuse embedded WebViews ("This browser or app may not be secure"); it worked for you earlier | Google treats it as Firefox; usually works |
| Fullscreen video | `WebChromeClient.onShowCustomView` | `ContentDelegate.onFullScreen` |
| PiP | Activity PiP works with any view | same |
| Background media | possible with a custom `onWindowVisibilityChanged` + foreground service | built-in media session API |
| Request filtering | `shouldInterceptRequest` for subresources (main-frame via navigation hook) | WebExtensions (could run uBlock Origin) |
| Testability | Robolectric/fakes | heavier |

WebView is the only engine that can be built and shipped from this environment, and it covers every requirement except a guaranteed Google sign-in. If Google blocks sign-in on your device, GeckoView is the technically better alternative and is documented as the next step.
