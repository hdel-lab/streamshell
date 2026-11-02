# StreamShell 0.5.0 — specification

Written with the `feature-forge` method from github.com/jeffallan/claude-skills (MIT): EARS requirements, acceptance
criteria, implementation checklist. The interview step was the conversation with the user (analysis of six diagnostics
logs and the option list of 2026-10-31); the user chose **all** fixes and **all** options A1–A8, B1–B5, C1–C4, D1–D6.
Nothing here is device-tested; "verified" below means JVM tests, page-script simulations and static analysis.

Constraints that stay in force: no media stream extraction, no UA spoofing, no password/cookie/token capture, same signing
key, no push/publication without the user's consent.

## 1. Fixes

| ID | Requirement (EARS) | Approach | Acceptance |
|---|---|---|---|
| F1 | WHILE a video plays over the YouTube feed, the feed page SHALL NOT be told it is hidden. | Visibility shield (document-start script in YouTube instance tabs: `visibilityState` stays `visible`, `visibilitychange` swallowed); renderer priority of the instance tab not waived while its player plays. | Minimizing shows the feed as it was (no re-layout, no reload); log shows no `diagnostics on` for the feed. |
| F2 | WHEN the page process of a tab ends or a tab is fully loaded again, the diagnostics log SHALL say so with the reason and the page kind (home / video / other, no address). | `onRenderProcessGone` + `onPageStarted` lines. | Lines `page process ended …` / `page load (…)` in the log. |
| F3 | WHEN a finger that scrolled (moved > 10 px, or the page scrolled within 350 ms before the touch) lifts on a video tile, the tap SHALL NOT open the video. | Capture-phase tap guard in route tabs (blocks `click` after a scroll gesture, for links and YouTube's own tile handlers). | Simulation: scroll-end click blocked, plain tap passes. |
| F4 | WHERE "Feed previews" is off (default), the YouTube feed SHALL NOT load video streams. | `googlevideo.com` requests of instance tabs refused while their page is not a video page. | Engine test; feed shows thumbnails only. |
| F5 | WHEN the user swipes down in the middle third of a fullscreen video, fullscreen SHALL end; an exit button SHALL appear top-left with YouTube's controls. | Compose gesture on the video node (observe-only) + small button (3 s). | UI test: swipe/ button call exit. |
| F6 | WHEN an ad's length changes by ≤ 1.5 s while loading, it SHALL be treated as the same ad. | Tolerance 0.5 → 1.5 s. | Simulation 6 s → 7 s ad: one jump, no wait. |
| F7 | WHEN the app is left while nothing plays, the log SHALL say "nothing playing, stays paused". | Wording. | Unit/log. |

## 2. Player (A)

| ID | Requirement | Approach |
|---|---|---|
| A1 | WHEN the mini player is swiped sideways past 35 % of its width, it SHALL close (as X). | Horizontal drag on the mini player. |
| A2 | WHERE "Leaving the player" = "Close video", Back from the player SHALL close the video instead of showing the mini player. | Setting `leavePlayer` (mini / close). |
| A3 | WHILE the mini player shows, it SHALL show play/pause and a progress bar. | Position probe every 1 s while mini. |
| A4 | WHEN the user pinches out/in on a fullscreen video, the video SHALL fill / fit the screen. | `object-fit: cover` toggle script. |
| A5 | WHERE "Remember speed per channel" is chosen, a new video of that channel SHALL start at that speed. | Channel name from YouTube's player data; map ≤ 300 channels. |
| A6 | The playback dialog SHALL offer "Repeat video" and "Repeat part A–B". | `loop` / A–B `timeupdate` script. |
| A7 | WHERE captions are set to On/Off, new YouTube videos SHALL start with captions on (language) / off. | YouTube player captions module. Untested on device (internal API). |
| A8 | The player SHALL offer "Next chapter" when the video has chapters. | Seek to the next chapter start. |

## 3. Feed and content (B)

| ID | Requirement | Approach |
|---|---|---|
| B1 | Setting "Feed previews" (default off). | See F4. |
| B2 | WHERE "Shorts as normal videos" is on, a Shorts link SHALL open as a normal video. | `/shorts/ID` → `/watch?v=ID` (router + URL change). |
| B3 | WHERE "Hide watched videos" is on, feed tiles of videos in history / finished SHALL be hidden. | Feed filter with video IDs (≤ 800). |
| B4 | Blocked channels and keywords SHALL hide matching feed tiles; "Block this channel" from the video page. | Feed filter (title / channel text, case-insensitive). |
| B5 | Local playlists: create, rename, delete, add video, remove/move item, play all (queue). | `WatchData.playlists`, Library panel. |

## 4. Data (C)

| ID | Requirement | Approach |
|---|---|---|
| C1 | Backup SHALL include playlists, channel speeds, channel stats and all new settings (format v2, v1 still readable). | `Backup` v2. |
| C2 | Import SHALL accept NewPipe `subscriptions.json` as well as Takeout CSV. | Format detected by content. |
| C3 | A private tab SHALL record no history, positions, watch time or searches; with WebView multi-profile support it SHALL use its own cookie profile, deleted when the last private tab closes. | `BrowserTab.private`; androidx.webkit Profile API when supported (else said in the UI). |
| C4 | Watch time SHALL show the last 7 days, a 30-day total and the top channels. | `WatchData.channelStats`. |

## 5. UI (D)

| ID | Requirement | Approach |
|---|---|---|
| D1 | The YouTube bottom bar SHALL offer a StreamShell "Library" item (Continue watching, Watch later + queue, playlists, subscriptions, watch time, history). | New bar item + Library panel; in the default bar. |
| D2 | Themes SHALL offer AMOLED black and a choice of accent colour. | `amoledBlack`, `accent` settings. |
| D3 | On a video page a slim action row (Watch later, Queue, Playlist, Speed, Next chapter, Repeat) SHALL sit above the bottom bar (switchable). | Native row; the page is resized, not covered. |
| D4 | A home-screen widget SHALL list up to 3 "Continue watching" videos and open them. | `AppWidgetProvider` + RemoteViews. |
| D5 | Launcher shortcuts SHALL include Watch later and Library. | `shortcuts.xml`. |
| D6 | Settings SHALL be searchable and grouped. | Search field filtering sections by keywords. |

## Non-functional
- Security: new page scripts only for YouTube origins, top frame; page → app messages through the typed parser; imported
  files size-limited and validated; no new permissions except none (widget needs no permission).
- Performance: no new timers faster than 1 s (mini progress only while the mini player shows).
- Privacy: nothing new leaves the device; private tabs keep nothing.

## Checklist
- [x] F1–F7 · [x] A1–A8 · [x] B1–B5 · [x] C1–C4 · [x] D1–D6 (implemented; not device-tested)
- [x] JVM tests (302) and page-script simulations (170)
- [x] ktlint, detekt (baseline extended, see KNOWN_LIMITATIONS), Android lint, ESLint, gitleaks (only the git-ignored keystore.properties)
- [x] KNOWN_LIMITATIONS, DEVICE_TEST_PLAN (162–185), FEATURE_MATRIX updated
- [ ] Signed release APK (same key)
