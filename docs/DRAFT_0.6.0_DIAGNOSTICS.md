# DRAFT 0.6.1 — app-wide diagnostics ("trace everything")

(0.6.0 became the prepared-player fix and the Android Auto layout; this diagnostics work moves to 0.6.1.)

Status: **implemented in 0.6.1** (see KNOWN_LIMITATIONS 0.6.1; in-app analysis view still open). Requested 2026-11-01 10:00; work starts tonight (after ~22:00) when the user is back.
Nothing here is implemented yet. Approach: feature-forge spec → implementation → tests/simulations → signed APK, as for
0.5.0.

## 0. Bug found in the log of 2026-11-01 16:11 (0.5.6) — **fixed in 0.6.0** (see SPEC_0.6.0.md R1–R3)
- 16:11:35 player prepared in the background; 16:11:36 its home page starts loading; **16:11:37** a video is tapped
  (1.5 s later, home page still loading). The in-page switch ran on a page that was not ready: `open video: switched in
  page` was reported (the player already carried the new ID) but the video **never played** (no `playing`, no `player
  ready`). 12 s later the user pressed Reload → `page load (reload, home)` — the player tab showed YouTube's home and was
  then used for browsing (feed, search) until a video was opened from there (16:12:25).
- Fix:
  1. Open in the page only when the player tab has finished loading; while it is still loading, load the video address
     directly (as before 0.5.6).
  2. Stronger check 2.5 s after an in-page switch: the player must have the new ID **and** be playing/buffering (YouTube
     player state 1 or 3); state "unstarted"/"cued" (-1/5) with autoplay on → load the video address directly. Log the
     state.
  3. Log line when the prepared player is used / bypassed (`player prepared: used` / `still loading → full load`).
- Also seen: after a 15 s ad (skipped in ~1 s) YouTube needed ~4 s to start the video (16:12:27 → 16:12:31) — YouTube's
  buffering; the new playback-quality tracing (section 3) will show the waiting time per video.
- Rest of that log: ad skipped with Skip press, ~60 jumps landed correctly, fullscreen and mini player fine.

## 1. Save the log as a file (agreed: "Da")
- Diagnostics panel: button **"Save as file"** (Storage Access Framework, `CreateDocument("text/plain")`), file name
  `StreamShell-log-<date>-<time>.txt`, header + all lines, never cut. Share stays; Share also attaches the file instead of
  plain text (no truncation by the receiving app).
- Log buffer: 3000 → 20000 lines (ring buffer, memory only); the header says how many lines were dropped.

## 2. Trace level (new setting under Diagnostics)
- **Off** · **Player** (today's log: ads, jumps, fullscreen, mini player…) · **Everything** (all below). Default Off.
- Everything is kept in memory only, cleared when switched off; nothing leaves the device unless the user saves/shares.
- Privacy in the log: video IDs and page *kinds* (home/video/…) only — no addresses, no search text, no titles, no
  account data, no typed text.

## 3. What "Everything" records
### Touches and gestures (app UI)
- Every tap / long press / swipe / pinch on StreamShell's own UI: which element (test tag, e.g. `bar-item-speed`,
  `mini-close`), where (x/y in dp), result (action run / ignored). Sliding bar: slides, snaps, centre item, spring pushes,
  haptic ticks.
- Fullscreen gestures: brightness/volume swipes, exit swipe, pinch zoom, double-tap seeks (app side).

### Touches on the page (YouTube)
- Taps on page elements: element kind (tile, player control, link, button) and its tag/role — not its text. Tap guard
  decisions (moved / scroll stop / passed). Scroll sessions (start/stop, distance, fling).

### Navigation and loading
- Every page load: reason, kind, time to `onPageFinished`, time to first video frame; in-page switches; Back; tab
  switches; overlays opened/closed; mini player on/off/closed/restored; player prepared in advance.
- Network: per page — number of requests, blocked by the filter, blocked video streams (feed previews), failed requests;
  WebView errors; page process ends.

### Playback quality ("pauses, stutter")
- Start-up time per video (tap → first `playing`), each `waiting`/`stalled` with its duration (buffering time),
  quality changes (YouTube's chosen quality), dropped/decoded frames (`getVideoPlaybackQuality`) every 5 s while playing,
  rate changes, seeks with how long until `seeked`, ad pods.
- App side: main-thread jank (frame time > 50 ms via `FrameMetrics`/Choreographer), memory (heap, WebView renderer
  priority), app in front/background, PiP, background audio.

### Scripts
- Every page script: installed / re-installed, each action it takes (already partly: ads, autoplay, guard, keep-playing,
  repeat, captions, zoom, feed filter counts — hidden tiles per pass), errors caught in scripts (`try/catch` → log line).

### Settings
- Every settings change (name and new value), backup/restore, imports.

## 4. Analysis
- **Summary at the top of a saved log**: session length; videos opened; median/worst start-up time; total buffering time
  and number of stalls; dropped-frame ratio; page loads by reason; tap-guard blocks; ads (count, time to skip); errors.
- **Timeline markers**: lines grouped per video ("── video XYZ ──") so a session can be read per video.
- Optional later: an in-app "Analyse" view with these numbers and the worst moments (tap to jump to the lines).

## 5. Constraints
- No effect when off (scripts check one flag; app hooks are no-ops).
- Rate limits stay (page: 15 lines/s; app: 50 lines/s) so tracing itself cannot cause stutter; the summary says if lines
  were dropped by the limit.
- Untested on device until the user sends logs; tests: unit (summary maths, privacy filter), JS simulations (new page
  events, error capture), UI (save-as-file, trace level).

## 6. Open questions for tonight
- Trace level names / where in Settings (YouTube › Diagnostics, or its own group "Diagnostics").
- Should "Everything" include frame-time (jank) sampling always, or only while a video plays (less overhead)?
- In-app analysis view now (0.6.0) or later (0.6.1)?
