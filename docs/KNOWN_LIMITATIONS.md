# Known limitations

Status words: **untested on device** (implemented, not verified), **engine limit** (Android WebView cannot do it), **by choice**.

## General
- **Untested on device.** No emulator could run on the build machine (no KVM). Use `DEVICE_TEST_PLAN.md`.
- Release build (R8) untested; there are no instrumented tests.

## Sign-in
- Google decides whether it accepts sign-in inside an embedded WebView. If it refuses ("This browser or app may not be secure"), that is a Google check of the browser, not a StreamShell bug. StreamShell does **not** disguise its user agent to get around it (that is the kind of security check Google uses against phishing and it would also break when Google changes detection). The technically clean alternative is a different engine (GeckoView), which could not be built here because the Mozilla Maven repository is blocked from the build machine (see `BROWSER_AUDIT.md`).
- Sessions are the WebView cookie jar of this app on this device; they are flushed to disk when the app leaves the screen.

## Playback
- **Background playback:** StreamShell keeps the page "visible" to the site (`MediaWebView.onWindowVisibilityChanged`) and runs a media foreground service. Whether a site keeps playing is still the site's choice: some pause anyway. On YouTube, background play is a YouTube Premium feature under YouTube's terms; using it without Premium is at your own discretion. **Untested on device.**
- Media control (notification, PiP button, audio focus) uses the standard HTML5 `play()`/`pause()` of `<video>/<audio>` in the page's top document; players inside cross-origin iframes cannot be reached. Starting playback from the notification may be refused by a site.
- Media state is probed every 1.5 s, so the notification/PiP reacts with that delay.
- Closing the PiP window stops playback (like Chrome).
- PiP (fixed 2026-10-19 after "PiP stays white"): the page is no longer re-created when PiP starts; the web view keeps its size and is scaled so only the video fills the window. The video position comes from the 1.5 s media probe, so a video that just moved may be framed slightly off; without a known video the whole page is shown. **Untested on device.**
- Fullscreen forces sensor-landscape; portrait videos in fullscreen are shown in landscape.
- Audio focus is handled only while the playback service runs (background playback on and media playing).

## YouTube app mode ("app in the app")
- On YouTube and YouTube Music StreamShell hides its browser bars and shows the site's sections at the bottom (YouTube: Home, Shorts, Live, Subscriptions, You; Music: Home, Explore, Library) plus "More" (search, start page, tabs, bookmark, ads & trackers, PiP, share, reload, "Show browser bars", settings). Back still goes back in the page, then to the start page.
- On YouTube a user style hides YouTube's own top and bottom bars, and a small fixer removes the ~48 px YouTube keeps free for its top bar (paddings and sticky offsets of the outer layout boxes, once per element; it never touches video, iframes or scripts). Selectors and offsets were taken from public userstyles/filter lists and could not be checked against the live site from the build machine; if YouTube changes its layout, a gap or its bars may come back. Settings > YouTube app mode off restores the normal page (reloads it).
- YouTube Music keeps its own top bar (its search lives there); whether music.youtube.com offers its full mobile site inside a WebView is decided by Google and is untested.
- "Live" opens YouTube's own Live channel page.

## Ad skip and black picture (update 2026-10-19, second fix)
- Skip was no longer pressed: the visibility test used `offsetParent`, which is null for YouTube's fixed-position Skip button. Now any Skip button with a layout box is pressed as soon as it appears.
- Unskippable ads: muted, 16x and one jump to the end again, but strictly tied to the ad media seen when the ad began (its source and duration). If the <video> switches while YouTube's ad marker lingers, speed is restored at once and nothing happens for 1 s, so the real video is never sped up or jumped.
- Black picture after an ad (even after pressing Skip by hand): 1.5 s after the ad, the autoplay script checks whether new frames arrive and the time moves; if not, it pauses and plays again once (what helped by hand). Normal playback is not interrupted.
- Verified with `tools/js-sim/run.sh` (22 checks, fake DOM and clock), not on a device.

## Smoothness (update 2026-10-19)
- Minimize/restore no longer moves the WebView between two containers: both positions are one keyed composition and the move is a GPU transform (scale, translate, clip, shadow) with a spring. YouTube Home in the new tab starts loading after the 0.42 s animation.
- Panels (tabs, bookmarks, history, settings) slide and fade; the loading bar animates; "reduce motion" in Android turns animations off.
- The visible page pre-rasterises off-screen tiles and gets renderer priority; the Compact YouTube layout fixer now only runs for 6 s after a page load or YouTube navigation, in idle time, so it no longer competes with scrolling.
- Smoothness itself was not measured on a device.

## Autoplay and swipe to minimize
- "Autoplay videos" (on): a YouTube video starts by itself once its page is visible and resumes right after an ad break; each video is started at most once, so your own pause is respected. If YouTube refuses to start (e.g. a sign-in or age check), it tries a few times and then leaves it.
- "Swipe down to minimize" (on): a one-finger swipe down that starts on the playing video shrinks it into the mini player. The page still receives the same touches, so YouTube may also scroll a little. Swipes below the video, sideways or upwards are ignored. **Untested on device.**

## Video page layout

- On a video page only the video stays fixed at the top; the related-chips row is removed completely and any other sticky block right under it scrolls with the list, so no gap can open (2026-10-19, second fix: found by position under the player, not by class names). Description and comments open in YouTube's own panel under the player as before. **Untested on device.**

## YouTube instances

- YouTube and YouTube Music each live in one persistent tab. Videos tapped in the YouTube instance open in its own player tab so the feed is never reloaded; this works by catching taps on `/watch` links in the page. If YouTube navigates by some other means, the app falls back to sending the instance back one step and opening the video in the player tab — the feed may then re-render once.
- Each instance has exactly one player tab, reused for every video (it counts as part of its instance in the tab counter). The next video is opened inside the already loaded page (YouTube's own history navigation); if the player does not show that video within 2.5 s, the URL is loaded normally in the same tab. Minimizing from an ordinary YouTube tab hands the video to the instance instead of opening a new tab.
- Shorts and YouTube Music play inside their instance (not routed to a player tab).
- Android may still discard a background WebView under memory pressure; then the instance reloads when you return. The app cannot prevent that.
- Not yet tested on a device.

## 0.6.4 (2026-11-02) — **untested on device** (loading screen, mini player buttons, YouTube Music gestures and ads)

- Log of 05:19 (0.6.3): **no crash** any more when switching YouTube ⇄ YouTube Music (several switches); start-up 0.7–1.8 s.
  `page load (app, home)` of the YouTube Music tab at 05:17:16 = the reload by pull-to-refresh the user saw (fixed below).
- Loading a video: instead of a picture over a white page, one loading screen — the picture with a ring in the player's
  16:9 place and placeholders (title, channel, chips, next videos) in the page's colour; everything fades out together
  when the video plays (at the latest after 3 s). It covers the page meanwhile (taps on the page wait until it fades).
- Mini player: m.youtube's controls sit next to the player (not inside it), so 0.6.3 missed them; now hidden
  container-wide (previous/play/next, time, progress, fullscreen, captions button).
- YouTube Music: no pull-to-refresh on its player page; swipe down from the top part of the page minimizes (its songs
  play in a hidden/tiny video element, so the top 40 % of the page counts as the player). Its "leave this page?" question
  is answered "leave" for navigations from StreamShell's bar (YouTube and YouTube Music only; other sites keep it).
- YouTube Music gets the ad skipper (its player is YouTube's). Untested on YouTube Music ads.
- Resume position, SponsorBlock and the page diagnostics are still YouTube-only (YouTube Music lines show
  `resume: none (not a video page)`).

## 0.6.3 (2026-11-01) — **untested on device** (crash YouTube → YouTube Music fixed; YouTube Music player; mini player)

- **Crash found and fixed** thanks to the crash report of 23:41: `ClassCastException` in the bottom bar (GlassNavBar)
  when its items changed (YouTube's bar → YouTube Music's bar: other count, search capsule elsewhere). An early
  `return` inside the item loop let Compose read one item's remembered state for another. Each item is now its own
  keyed composable; the same pattern was removed from the bar editor. Regression test switches the bar's items back and
  forth (it passed before too in the JVM test — the crash came from the release build, so a device check is needed).
- Mini player: YouTube's own buttons (play/pause, Skip, ad badges, unmute, top/bottom player chrome) are hidden while
  the video is mini — they showed but could not be used there. The mini player's own play/pause and close stay. The
  Skip button stays in the page (only invisible), so the ad skipper can still press it.
- Loading a video: the picture now fills the whole loading strip (cropped a little) instead of leaving a 48 dp black band
  under it. Whether the band seen by the user was this one is unconfirmed — a screenshot would settle it.
- YouTube Music: sliding player bar on its watch page (search, Home, Explore, Library | previous, next, Watch later,
  speed, repeat, More); swipe sideways over the player for next/previous track; mini player shows YouTube Music's
  player (video or song picture); headset / lock-screen next/previous = next/previous track on YouTube Music (elsewhere
  still a jump). Track changes use the page's media session, else its buttons; YouTube Music's markup is not verified.

## 0.6.2 (2026-11-01) — **untested on device and in the car** (YouTube Music, calls/navigation voices, car source)

- Crash when switching from YouTube to YouTube Music: **not reproduced** here (the screen was rendered through all
  switching states in a test without fault; the log of 22:59 ends before the switch). New: the stack trace of any crash
  is kept and shown at the top of the diagnostics log on the next start (and in a saved log) — needed to fix it.
- YouTube Music in app mode: its own bottom tab bar is hidden (StreamShell's bar has Home, Explore, Library and search);
  its top bar (search, account) and player stay. Selector `ytmusic-pivot-bar-renderer` — if YouTube renames it, the
  tab bar simply shows again.
- Calls and navigation voices (Settings › Playback, default on): StreamShell does not take audio focus (the WebView
  does; both together fought before). It watches the audio mode and other apps' playback (no permission): calls →
  pause, continue after the call (only what it paused); navigation/radar/assistant voice (usage navigation guidance,
  assistant, accessibility) → quarter volume while it speaks, full volume after, and play again if the page had
  stopped. Radar apps that play their warnings as "media" or "notification" sound are not recognised.
- Car: rail button YouTube ⇄ YouTube Music (one page, one sound); setting "Android Auto: opens with". Play bar with
  previous/next (page's media session, else its buttons). While driving the cover shows title and artist (text only)
  and large previous / play-pause / next buttons — sound controls only; the picture rule is unchanged.

## 0.6.1 (2026-11-01) — **untested on device and in the car** (app-wide diagnostics; car architecture review)

- Diagnostics log for the whole app: one log for phone and car (`car · …` lines), 20000 lines in memory, times to the
  millisecond, **Save as file** (header, summary, every line), Share sends the same text (receiving apps may still cut
  long texts — use the file). LOG button on every screen (switchable, drag up/down).
- "Trace everything" (Settings › YouTube, under Diagnostics log): touches/swipes on StreamShell's screens with the
  element's test tag (elements without a tag show as "untagged element"), page taps by element kind, scrolls, page loads
  with times and request counts, script errors, buffering, dropped frames/quality every 5 s, screen stutter (> 50 ms),
  memory every 30 s, settings changes (values only for switches, numbers, short words). Page lines: YouTube pages only.
  Rate limits: app 50 lines/s, page 40 lines/s while tracing (15 otherwise); the summary counts refused lines.
- Stutter sampling runs only while the app is in front; it keeps a frame callback running (a little more battery).
- Car: see CAR_ARCHITECTURE.md. The picture rule (ParkGate) is unchanged; its transitions are now logged.

## 0.6.0 (2026-11-01) — **untested on device and in the car** (prepared-player fix; Android Auto layout)

- Prepared player (log of 16:11): a video is switched inside the player page only when that page has finished loading;
  while it is still loading (e.g. tapped 1.5 s after the app started) the video address is loaded directly. 2.5 s after an
  in-page switch the player must carry the new video **and** have started (playing, paused or buffering); "not started" or
  "cued" with autoplay on → the address is loaded. Log lines: `player prepared: used` / `player prepared: still loading,
  full page load` / `open video: switched in page (player playing)` / `… player not started after 2.5 s, full page load`.
- Android Auto: StreamShell now chooses the car screen's density itself (about 480 dp of height, at least 640 dp of width,
  120–320 dpi; 1920×720 → 240 dpi = 1280×480 dp) instead of the car's figure. Whether YouTube's mobile page then looks
  right on the DS7 Crossback is untested; the head unit's real surface size is not known here.
- Car layout: control rail (Search, Home, Back, scroll up, scroll down) at the left edge (setting: right edge), the page on
  the rest; on a video page a play bar at the bottom (−10 s, play/pause, +10 s, time, progress — the progress bar shows
  only, it cannot be dragged). Android Auto still requires one button of its own (top right): "Parked" while the car gives
  no speed, else Search; and its pan button (✥) for dragging the page.
- While driving the page stays covered and taps on it are ignored, but rail and play bar work (pause, seek). Whether a
  head unit forwards taps on the rail/bar areas exactly is untested.
- The rail side is read when StreamShell opens in the car (change it on the phone, then reopen in the car).

## 0.5.6 (2026-11-01) — **untested on device** (faster-looking video start; ninth log)

- While a YouTube video's player is being built, its picture (thumbnail, with a loading ring) shows where the player will
  be instead of plain black; it goes when the video is confirmed playing (at the latest after 3 s).
- Player prepared in advance: about 2.5 s after the YouTube feed has loaded, its player tab is set up in the background on
  YouTube's home page (no video streams load there), so the first video switches in the page (about 1 s) instead of
  loading a whole page (about 3 s). Costs one more page in memory and one page load at start. Log:
  `player prepared in the background (tab N)`.
- After a video opens, the app checks six times at 0.35 s intervals (instead of waiting for the next 1.5 s check), so the
  picture/band goes sooner.
- Ninth log: no faults. Shorts played in YouTube's Shorts tab (their loop and swipe are YouTube's); the first video in the
  player tab loaded a whole page (2–3 s) — the case the prepared player addresses.

## 0.5.5 (2026-10-31) — **untested on device** (black band over the player; eighth log)

- The black band that covers the top while a YouTube video page loads now goes as soon as the app has confirmed that this
  video plays in the player (by its ID, the same check as resume), not only when the picture starts at the very top. Live
  streams and videos that are not 16:9 never start at the top, so the band stayed until its 8 s fallback; the fallback is
  now 3 s. Log: `player ready: <id> (loading band off)`.
- Eighth log: both videos were live streams; they played about 2 s after opening. The 6 s wait after returning to the app
  at 19:01 was YouTube going back to the live edge after a 1.5-minute pause (buffering), not the app.

## 0.5.4 (2026-10-31) — **untested on device**

- Sliding bar: Search is an ordinary item (icon and label) — the search capsule squeezed into one slot looked out of
  proportion and was cut by the bar's rounded edge at the left end.

## 0.5.3 (2026-10-31) — **untested on device** (sliding bar: always full, spring at the ends)

- The sliding bar never shows empty space: five items edge to edge at every position (0.5.2 let the first/last item reach
  the middle, which left a gap and shifted the icons — the user's screenshots).
- Spring at the ends: pulled past the first or last item the row moves less and less (at most 56 dp) and springs back when
  let go. Pulled far enough (stronger buzz), the whole bar then returns to its home position (Speed in the middle as far
  as the ends allow — with 10 items that is the right end: Queue · Playlist · Speed · Repeat · More).

## 0.5.2 (2026-10-31) — **untested on device** (ends of the sliding player bar; seventh log)

- The sliding bar stops firmly at its first and last item (no stretch). Pulling on past an end by about 28 dp gives a
  stronger buzz; when let go, the bar slides back to its start (Speed in the middle). Search and More stay reachable and
  tappable — the bar only goes back when pulled past them.
- Seventh log (0.5.1): feed previews no longer play (no `playing` in the feed tab), the tap guard caught a scroll stop,
  the feed tab was not loaded again after the mini player.

## 0.5.1 (2026-10-31) — **untested on device** (sliding player bar)

- On an open YouTube video (not the mini player) the bottom bar is the **sliding player bar**: the same glass bar, highlight,
  icons and labels as the normal bar, five items at a time, the middle one in the highlight. Order: Search · Home ·
  Subscriptions · Library │ Later · Queue · Playlist · Speed · Repeat · (Chapter) · More. Starts on Speed. A tap runs the
  item; it snaps an item into the middle when let go. The separate action row of 0.5.0 is gone.
- Haptics: a light tick when an item reaches the middle while sliding, a click on a tap (system "touch feedback" setting
  applies; strength depends on the phone).
- More on a video: the player actions as a 3-column grid at the top of the menu.
- Settings › Playback › Sliding player bar off: the normal bar on videos too.

## 0.5.0 (2026-10-31) — **untested on device** (fixes from the sixth log and the user's reports; all options A–D)

Spec: `docs/SPEC_0.5.0.md`. Everything below is verified only by JVM tests, page-script simulations and static analysis.

Fixes
- **Feed refreshing behind the player:** the YouTube instance's feed is never told it is hidden (visibility shield), and
  Android keeps it important while the player covers it. Whether YouTube still rebuilds the feed for other reasons is open
  — the log now says why each page loads (`page load (app / reload / back / by the page, kind)`) and when a page process
  ends.
- **A scroll that opened a video:** a touch that moved > 10 px, or that stopped a running scroll (< 350 ms after it), no
  longer opens a video tile (log: `tap guard: … not opened`). Feed previews are **off by default** now (no video streams in
  the feed at all); switch them back on under Settings › Feed.
- **Leaving fullscreen:** swipe down in the middle third, or the exit button top-left (shows with YouTube's controls).
  Brightness / volume swipes are now on the left / right *third* (were halves).
- Ads whose length changes by up to 1.5 s while loading count as one ad (no extra second of waiting).
- Log wording when the app is left while nothing plays.

New
- Player: mini player swiped away (more than two thirds beyond an edge) closes it; progress bar on the mini player;
  "Back from the player: Close video" option; pinch in fullscreen to fill / fit; speed remembered per channel; repeat video
  and repeat part A–B; captions on/off with language (**YouTube's internal player API — may stop working without notice**);
  next chapter.
- Feed: previews switch, Shorts as normal videos (taps in the YouTube instance and Shorts pages in other tabs; the Shorts
  tab itself stays), hide watched videos (from history), blocked channels (matched at the start of a tile's byline) and
  blocked words (in titles). Tile matching depends on YouTube's markup and may miss tiles after YouTube changes it.
- Local playlists in the new Library (bottom bar item, start page, menu): create, rename, delete, add, move, remove, play
  all (queue).
- Backup format v2 (playlists, channel speeds/stats, new settings); v1 files still restore. Older StreamShell versions
  cannot read v2 files.
- NewPipe `subscriptions.json` import next to Google Takeout CSV.
- Private tabs: no history, positions, watch time or searches; with WebView multi-profile support (checked at run time)
  their own cookies/storage, deleted when the last private tab closes (best effort: if Android still holds the profile,
  it is deleted at the next start). Without that support cookies are shared — the app says so when the tab opens.
- Watch time: 30-day total and most watched channels (counted from 0.5.0 on).
- Appearance: AMOLED black, six accent colours. Settings: search field, new "Feed" group.
- Player action row above the bottom bar on video pages (switchable).
- Home-screen widget "Continue watching" (3 videos) and launcher shortcuts Watch later and Library.

Code review notes
- detekt baseline extended (633 entries): long one-line page scripts and UI lines, guard-clause `return`s and value
  limits in the new pure rules — same style as the existing code; the rules are covered by tests.
- `@SuppressLint("SetJavaScriptEnabled")` had slipped onto the wrong declaration during the work; found by ktlint, fixed.
- Repeat state is reset in the page when the next video starts (YouTube reuses its video element).

## 0.4.5 (2026-10-30) — **untested on device** (from the analysis of the fifth log)

- **Positions only for real videos:** on YouTube a position is saved and continued only for a page with a video ID. The
  home feed's muted tile preview was saved under the home page and "continued" at 83 s on every start. Such entries are
  removed once on the first start of 0.4.5.
- **Ads up to 600 s** (was 180) are sped up and jumped; a 183 s ad was re-loaded instead (about 6 s and a new ad).
- **Keep playing after a fullscreen switch:** if the video played before the switch and stands still after YouTube rebuilt
  its player, it is started once again (within 6 s; never after your own pause). Picture-in-picture is not covered by
  this: there the page gets no event; whether it is needed there is open (fifth log, 11:14:28).
- **Diagnostics:** "mini player closed (X)" when the mini player's close button is used.

## 0.4.4 (2026-10-30) — **untested on device** (from the analysis of the fourth log)

- **Back from the mini player:** the video's tab is selected *before* the mini player is cleared (the same order fix as
  0.4.3, in the other direction). Before, the video was paused for a moment and often stayed paused (5 s and longer in
  the log).
- **Resume per tab and video:** a video handed on from the feed tab to the player tab (it started there at 0 in the log)
  is continued again there.
- **Diagnostics:** "resume: none (no saved position / already at N s / saved N s is too near the start or end)"; the
  fullscreen guard line shows one decimal.
- Left as is: the feed tile preview YouTube starts the moment a finger touches a tile (only metadata, no playback, emptied
  at once).

## 0.4.3 (2026-10-30) — **untested on device** (from the analysis of the third log)

- **Mini player → new video from the feed:** the player tab is now selected *before* the mini player is cleared. Before, the
  feed tab was the "playing" tab for a moment and YouTube started loading its preview of the same video (about 1 s of
  double streaming; possibly the cause of the muted starts seen in the log). Not yet confirmed on a device.
- **Fullscreen guard from 1.5 s on** (was 5 s): in the log a fullscreen switch at 2.8 s started the video over at 0. It only
  acts when the video is at least 1 s before the kept spot, once, and not after your own jump.
- **Diagnostics for opening a video in the same page:** the log now says "switched in page" or why a full page load was
  used (no page / other site / switch refused / player still on the old video after 2.5 s). The full reload of the player
  tab seen in the third log can be explained with the next log; nothing was changed about it yet.
- Unchanged and YouTube's own: the re-init of the media on every fullscreen switch, its own resume positions, small
  −0.2 s corrections after a jump.

## 0.4.2 (2026-10-30) — **untested on device** (better diagnostics, from the analysis of the second log)

Second log (08:34–08:37, 400 lines, one video, 43 series of double taps): every tap jumped exactly +10 s; after every
jump YouTube itself stepped back 0.0–0.7 s (mostly 0.4–0.5 s), paused and buffered for about 1 s (36 of 43), up to 2 s
(2 of 43). No StreamShell action appears (no ad handling, no fullscreen guard, no autoplay pause/play, no app jump, no
SponsorBlock skip). The log started mid-session because the buffer held only 400 lines.

- Diagnostics keep 3000 lines (was 400).
- "Share log" starts with the app version, Android version and the settings that change playback (ads, autoplay,
  SponsorBlock, quality, background/audio only, speed, jump length, resume, boost, normalize, YouTube app mode).
- The app's own actions are logged too ("app · …"): app in front / left, picture-in-picture, fullscreen on/off, mini
  player on/off, which tab may play, quality requests (incl. the background "audio only" switch), resume position,
  SponsorBlock skips, queue, sleep timer, a tab opening a video. So events like the media reset at 08:37:03 get a cause.

## 0.4.1 (2026-10-28) — **untested on device** (fixes from the user's diagnostics log)

What the log showed (17:45–17:46, two tabs):
- Ads: at the start of each ad the skipper re-loaded the video through YouTube's player (`loadVideoById`) — and YouTube
  answered each reload with a **new** ad (24 s → 20 s → 20 s) and the player emptied/re-buffered each time. Only the old
  path (mute, 16x, jump to the end) got through, in about a second.
  → The reload is now a last resort only: an ad is muted, sped up, jumped and Skip is pressed; a reload happens only
  for an ad that cannot be jumped (no length, longer than 3 min, or no data) after 4 s, at most 2 per minute.
- Two pages streamed at once: the YouTube feed (muted previews, also of the very video being watched) while the mini
  player played. → Only one page may play now: the mini player's video, else the current page; other tabs' media is
  paused and previews that start by themselves are paused at once (allowed again when that page is in front).
- Fullscreen switch: the guard jumped back to the old position twice while YouTube was still re-loading the media,
  which made it load again. → It waits until the media is ready and tries once.
- After an ad the "no picture" check paused/played the video while it was only buffering. → It acts only when the video
  has data but does not move.
- ±10 s: the only jump in the log went through YouTube's player as intended (seeking → seeked); the stutter around it
  matches the reloads and the second stream above. Please send a new log if it still happens.

## 0.4.0 (2026-10-28) — **untested on device** (review and clean-up with the jeffallan/claude-skills skill set)

- Ads / ±10 s regression: 0.3.8 (old libraries) still showed it, and the page scripts are identical to 0.3.6, so the
  cause is most likely on YouTube's side (2026 reports: new anti-blocker measures, ads stitched into the video stream).
  Not fixed yet — **evidence first** (debugging-wizard): Settings › YouTube › "Diagnostics (ads and jumps)" logs what
  YouTube's player and StreamShell's scripts do around ads and jumps (ad state, ad media length, reloads, Skip presses,
  16x/jump, every seek with times, the video's ad placements and server-stitching keys). Memory only, max 15 lines/s,
  cleared when switched off; open it under More › Diagnostics log and share it. Ads whose media is longer than 3 min
  (a sign of stitched ads) are logged as such and, as before, not sped up or jumped.
- Kotlin (kotlin-specialist): no `runBlocking` on the main thread at start (saved tabs/settings are read in a coroutine,
  the Activity waits for them); messages from the page go through one typed, validated parser (`PageMessage`, sealed);
  SponsorBlock / dislike / DeArrow look-ups moved into `CommunityLookups` (structured concurrency in the view model's
  scope, testable with fake repositories); no `!!` left in Kotlin code.
- Static analysis: ktlint 1.8 (Android Studio style, `.editorconfig`) — whole code base formatted, 0 findings; detekt
  1.23.8 (`config/detekt.yml`, new code checked in full, 555 older style/complexity findings in `config/detekt-baseline.xml`);
  ESLint 9 on all page scripts (0 findings, 3 fixed: prototype-builtins, a useless regex escape); gitleaks: no secrets;
  npm audit (test tooling): 0 vulnerabilities. Script: `tools/lint/static-analysis.sh`.
- Tests: 278 JVM tests, 137 page-script simulations (new: page-message parser, community look-ups with `runTest`,
  diagnostics). Review report: `docs/CODE_REVIEW_0.4.0.md`.
- Libraries as in 0.3.7 (they were shown not to be the cause).

## 0.3.8 (2026-10-27) — **test build** (narrowing down a regression)

- Reported on 0.3.7: ad skipping no longer works (ads have to be skipped by hand) and ±10 s jumps in the player are
  chaotic again. The YouTube page scripts of 0.3.7 are byte-identical to 0.3.6 (checked with diff); what changed
  between the two is the toolchain and the libraries (Compose, WebKit, …). 0.3.8 = the code of 0.3.7 (all fixes of
  phases 1–2) built with the libraries of 0.3.6 (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10, WebKit 1.12.1 …,
  compileSdk 35). If the problems are gone in 0.3.8, the library update caused them and gets redone step by step; if
  not, the cause is elsewhere (or YouTube changed something at the same time). The upgraded build files are kept aside.

## 0.3.7 (2026-10-27) — **untested on device** (audit phases 1–2: security, privacy, clean-up, dependencies)

- Android Auto: release builds accept only Google's Android Auto / Automotive hosts (package + signing-certificate
  digest, res/values/car_hosts.xml). Before, any app on the phone could bind to the car service and drive the browser.
  Debug builds still accept any host (for the desktop head unit).
- "Show dislikes" is now off by default and was switched off once on update (it sends the video ID to a third party).
- New switches: "Search suggestions" (off = nothing typed leaves the device, only own recent searches) and
  "Third-party cookies" (on by default because Google sign-in needs them; off may sign you out of Google).
- Backup: the settings text says the file is not encrypted.
- Release signing: APK Signature Scheme v2 + v3 (same key, same fingerprint; updates install over older versions).
- Removed unused parts: the recommendations column script and its state, the "floating navigation" setting, the unused
  audio-focus code of the playback service, the split-layout CSS, an unused layout parameter, an unused colour.
- Toolchain/dependencies updated: Gradle 9.8.2, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1,
  Compose BOM 2026.09.00, Activity 1.13, Lifecycle 2.11, Core 1.19.1, DataStore 1.3, WebKit 1.18, Window 1.5.1,
  WorkManager 2.12, Car App 1.7, Coroutines 1.11, org.json 20260814, Robolectric 4.17, Roborazzi 1.77, test libs.
  compileSdk 37 (required by the new Compose); **targetSdk stays 35** — targeting Android 16/17 changes runtime
  behaviour (e.g. orientation requests on large screens) and needs a device test first.
- All 272 JVM tests and 129 page-script simulations pass on the new toolchain; behaviour on a device is not verified.

## 0.3.6 (2026-10-27) — **untested on device**

- White strip with YouTube's chip row ("All · Gaming · Live") at the top of the mini player and while a video loads.
  Cause (from the screenshot; not reproduced on a device): a video opened from the feed inside the same page keeps the
  feed in the page, including its sticky chip row, which painted over and around the player. On video pages the feed
  leftovers are now hidden (html.ss-watch). In the mini player only the player itself is painted (everything else is
  invisible on black, nothing moves); back to normal when the video is opened again. Simulated in jsdom.

## 0.3.5 (2026-10-26) — **untested on device** (new features from similar apps)

- **Show dislikes** (Return YouTube Dislike, on by default): the count is an estimate (archived data + users of the
  extension), not YouTube's. The open video's ID is sent to returnyoutubedislikeapi.com (no hash prefix in its API).
  Shown next to YouTube's dislike button; found by YouTube's element names (dislike-button-view-model / the second
  button of the like/dislike pair). If YouTube renames them, no count shows.
- **Clean shared links** (on): "Share page" removes si=, feature=, pp=… on YouTube and utm_…, fbclid… elsewhere.
  YouTube's own Share button inside the page is not changed.
- **Jump length** 5/10/15/30 s for the notification, headset, playback menu and the app's double-tap. YouTube's own
  double-tap stays 10 s. The notification shows the ±10 icons only at 10 s, otherwise plain rewind/forward icons.
- **Video quality** per Wi-Fi / mobile data (metered network = mobile), asked of YouTube's player once per video
  (setPlaybackQualityRange). YouTube may ignore it on the mobile site or go lower on a slow connection.
- **Swipe for brightness and volume** in fullscreen (on): left half brightness (the app window's own, back to the system
  value when fullscreen ends), right half volume. Only clear vertical swipes in the middle band (15–75 % of the
  height); taps, sideways drags, the top buttons and the timeline stay YouTube's. A recognised swipe sends the page a
  cancel, so YouTube may show its controls.
- **Hide in feeds**: Shorts (shelves and Shorts items in feeds/search), community posts, Playables — by YouTube's
  element names (best effort).
- **Queue**: "Add to queue" on video pages, "Play all" in Watch later; the next video opens when the current one is
  within ~1 s of its end (checked every 1.5 s, also before YouTube's own autoplay). The queue is kept only while the
  app runs (not saved).
- **Channel groups** for imported subscriptions: a group button per channel; "All" + group chips filter the Latest
  list and the channel list. Groups are saved and in the backup; empty groups disappear.
- **DeArrow** (off by default): community titles replace clickbait titles in feeds and on the video page (original as
  tooltip; switching off restores them). Only 4-character hash prefixes are sent to sponsor.ajay.app. Thumbnails are
  not replaced (that would load pictures from another server into YouTube's page).
- **SponsorBlock segments on the timeline** (on, with SponsorBlock): coloured marks on YouTube's progress bar, found by
  its element names; when YouTube's bar is not found (e.g. hidden controls) no marks are drawn.
- Simulated: extras script in a real DOM (jsdom) — feed style, dislike count, DeArrow titles, marks, switch-off, frames.

## 0.3.4 (2026-10-26) — **untested on device** (code audit)

- Page scripts run only in YouTube's own top page. They were also injected into frames: the live chat frame under a
  video and YouTube players embedded on other sites got the layout fixer (which removes 40–56 px offsets) and the
  other scripts. The ad skipper still works in embedded players.
- The live chat window (wide screens) no longer gets the ad skipper / autoplay at the end of loading (they were kept
  out at the start of loading only).
- Play/pause from the notification, headset, PiP and Android Auto go through YouTube's own player, like ±10 s in 0.3.3
  (its controls showed the wrong state before).
- A speed picked in YouTube's own menu is kept for that video; the app's speed setting no longer resets it the next
  time the video plays. The app's speed applies again from the next video.
- Sleep timer "end of video" now works also with "Continue where you stopped" off, and is checked every 1.5 s
  (before: every 5 s and only with that setting on). The chosen 15/30/60 min chip is shown as selected.
- Address bar: what you type is no longer replaced while you type (YouTube changes its address during playback).
- "Save to Watch later" is offered on video pages only, the same in the menu and in the YouTube sheet (it was greyed
  out while a mini player was open, and offered on non-video pages in the menu).
- "Reset settings" really resets the app settings (appearance, browsing, start page, playback, YouTube, bottom bar);
  AdBlock & Privacy settings stay (they have their own reset). Before it only reset the theme while its text said
  "settings were reset". The permissions line under About lists the notification and Android Auto permissions.
- YouTube links from other apps (watch and youtu.be, start time kept) open in the YouTube player instead of a new tab.
- Timeline preview sheets are no longer cropped to 16:9 when loaded (could shift chapter pictures).
- SponsorBlock notices also appear for the mini player's video; YouTube Music has its own icon in the menu.
- Android Auto: the Pause/Play button follows the page (checked every 3 s; a tap on the video also pauses it).
- A failing removal of a page script after a renderer crash can no longer close the app.

## 0.3.3 (2026-10-21) — **untested on device**

- ±10 s jumps jerked back and forth before settling. Causes found in the code: (1) the app's own ±10 s (notification,
  playback dialog) set the `<video>` element's time behind YouTube's player, which then pulled the picture back to its
  own position and forward again — on YouTube the jump now goes through YouTube's player (`seekTo`), other sites keep
  the direct way; (2) the fullscreen guard (0.2.x, against "starts over after fullscreen") treated any backward jump
  of more than 4 s in the first 6 s after a fullscreen switch as the bug and jumped forward again — it now only reacts
  to a fall back to the very start (< 2 s) and never right after a jump of yours; (3) the after-ad "frozen picture"
  check could pause/play right after your jump — it now waits 3 s after any jump; (4) SponsorBlock skips also go
  through YouTube's player. The app's own double-tap layer is off on YouTube (YouTube has its own), so one double tap
  is never two jumps.
- Not verified on a device: if YouTube's own double tap still stutters, that is inside YouTube's player.

## 0.3.2 (2026-10-21) — **untested on device**

- Videos starting in the middle / jumping while loading. Cause: positions ("continue where you stopped", every 5 s)
  were read from the player and stored under the page address; right after switching to another video the address
  already shows the new video while the player still plays the old one — so the old video's position was stored for
  the new one, which then "continued" there. The same mix-up fed the ad skipper's reload position and the fullscreen
  position guard. Now every position carries the id YouTube's player is really playing and is used/stored only for
  that video. Positions saved before this fix are cleared once (Watch later, watch time, subscriptions stay).
- "Watched up to …" on the start page is per video (by its YouTube id); wrong entries came from the bug above.
- Minimizing a video that played inside the YouTube tab itself showed the StreamShell start page: the new YouTube tab
  started empty. It now shows YouTube's home right away.
- Mini player drag: no more jerk when letting go — one animated position follows the finger and springs on from
  exactly where it was released (before, the animation restarted from the old place); it is read only while drawing.

## 0.3.1 (2026-10-21) — **untested on device**

- Fullscreen chapters opened only sometimes. Cause: both the page check and the app's own check looked at the chapter
  title when the finger was LIFTED; by then YouTube had already reacted to the tap (its controls fade, the button
  changes), so the check often found nothing. Now the decision is made when the finger goes down — on the page
  (touchstart/pointerdown, then the whole gesture is kept away from YouTube) and in the app (checked at finger-down).
  Faded-out controls do not count (that tap shows them, as before); the timeline never counts. The app also tells the
  page that it is fullscreen (some WebView versions report no fullscreen element).
- Mini player (phone and foldable): drag it sideways — it settles at the nearer side (left/right); push it more than a
  third over an edge to hide it there: a thin tab stays at the edge, the sound keeps playing; tap the tab or pull it
  inwards to bring it back. Picture and controls move together. A tap still opens the video, a drag never does.

## 0.3.0 (2026-10-21) — **untested on device and in a car**

- Android Auto (unofficial, see docs/ANDROID_AUTO.md): StreamShell registers as a Car App Library navigation app,
  draws its browser on the car screen through a private virtual display, passes taps and pan gestures to the page, and
  shows the picture only while the car stands still (car speed; "Parked" confirmation when the car reports none) —
  sound keeps playing while driving. Needs installation with Google Play recorded as installer plus Android Auto's
  "Unknown sources"; every car host is accepted (ALLOW_ALL_HOSTS_VALIDATOR) because the build is not on Play.

## 0.2.25 (2026-10-20) — **untested on device**

- Title pushed under the player on the foldable (0.2.24). Cause: on the full-width page the window is wider than tall,
  YouTube uses its wide layout (lower player), and the search for "the first text under the player" probed only two
  points across the page (24 px and the middle) and only the first element at each point. A short title does not
  reach the middle, so the longer channel line below it was taken as the first text and lifted to 12 px under the
  player — which put the title under the player. Now five columns near the left edge plus the middle are probed and
  every element under the player at each point is checked. Reproduced with the screenshot's geometry in the page
  simulation (title 12 px under the player, channel line below it).
- Live chat window: from just under the player (its real height, which the page reports) to the bottom, half the width,
  at least 260 dp tall; text in the chat at 90 % so more messages fit.

## 0.2.24 (2026-10-20) — **untested on device**

- Title no longer glued to the player: the first text under the player starts 12 px below it (measured to the text,
  not to its container).
- White row between player and the description panel on the foldable: the foldable's window is wider than tall, so
  YouTube's landscape rule placed the panel absolutely at `calc(56.25vw - 48px)`; the style covered only upright
  windows and the docking only fixed panels. Now the panel is placed right under the player up to 930 px width in any
  orientation, and absolutely positioned panels are docked too (moved by their measured distance to the player).
- Live chat on wide screens: a floating window at the bottom right instead of a split — about 45 % of the width and
  3/4 of the player's height (under the player if there is room), with a shadow; the chat scrolls inside it; X closes
  it. The video page keeps the whole screen.

## 0.2.23 (2026-10-20) — **untested on device**

- Open foldable / tablet, normal video: no extra area any more — the video page uses the whole screen with its own
  recommendations under the description (the separate recommendations page next to it is no longer opened).
- Live stream: the same full-screen page plus a "Live chat" button (bottom right). It opens the chat as a window:
  across the width under the video when the window is upright (player and title line stay on top, the rest of the page
  scrolls there), beside the video in landscape. X in the window closes it; it stays open for the next live stream
  until closed.

## 0.2.22 (2026-10-20) — **untested on device**

- Black corners around the white sheet under the title (and at the top of the foldable's right column): YouTube's
  watch page paints its page container black (`.page-container.page-black-background`, behind its flag
  mweb_watch_background_colors) and lays the rounded white sheet over it. Found in YouTube's mobile code; the page
  container is now see-through, so the page colour shows (checked in Chromium against YouTube's stylesheet).
- Stacked foldable layout also on the user's Fold (about 704 × 638 dp of content: player 396 dp, ~240 dp under it):
  used from 200 dp under the player (was 320). Under the player about 96 dp for title and channel line (the rest of
  the video information scrolls there), the remaining ~145 dp for recommendations (left) and live chat (right). That is
  small; hiding the app's bottom bar while watching on the foldable would give ~70 dp more (not done).

## 0.2.21 (2026-10-20) — **untested on device**

- Open foldable held upright (window tall enough: at least 320 dp left under a full-width player): the video page across
  the full width at the top — the player fixed, title/channel/actions/description below it (about 42 % of the room
  under the player, 140–260 dp, scrollable) — and below that the recommendations on the left and the live chat on the
  right; not live: recommendations across the full width. Lower/wider windows (landscape) keep the columns layout.
  The lower part is fairly small on a ~840 dp tall foldable; the split can be tuned.
- Long video title (phone and foldable): instead of being cut with "…", the first cut-off text right under the player
  becomes one line that slides slowly to its end, pauses and starts again; it can be swiped sideways, a touch pauses it
  for 4 s. Found by behaviour (cut-off text near the player), not by class names.

## 0.2.20 (2026-10-20) — **untested on device**

- White band above the player for the first seconds of a video. Cause (from YouTube's own m.youtube stylesheet):
  `.player-container{position:absolute;top:48px}` while a video opens; only seconds later YouTube makes it
  `.sticky-player` (fixed). StreamShell's style covered the sticky state and the fixer only corrected fixed/sticky
  offsets, so the player sat 48 px low under an empty band until the switch. Now the style moves every player
  container to the top from the first paint, and the fixer also corrects absolutely positioned ones. Same source:
  the feed's chip bar (`top:48px`), the description/comments panel (`top:calc(56.25vw + 48px)` — the cause of the
  jump when opening it), the scroll padding and the landscape player height no longer leave room for the hidden bar.
  Checked in Chromium against YouTube's real stylesheet (player top 48 px → 0 px, panel right under the player).

## 0.2.19 (2026-10-20) — **untested on device**

- Fullscreen chapters, found by reading YouTube's mobile code (player c271e571 and the m.youtube app bundle; the watch
  page itself could not be opened from the build machine — YouTube showed a robot check, which is not bypassed):
  - The chapter title next to the time is a button `ytwPlayerTimeDisplayPill ytwPlayerTimeDisplayPlayerBarButton`
    (no "chapter" in its class) — now recognised by that class as well as by its text.
  - YouTube takes the chapters from its watch data (`markersMap`, keys `DESCRIPTION_CHAPTERS` and `AUTO_CHAPTERS`),
    including automatic chapters that are in no description. The app read only the description, so for such videos it
    had no chapters at all and nothing could open. The app now reads the same watch data first
    (movie_player.getWatchNextResponse(), checked for the open video's id), with YouTube's own picture for each chapter;
    description links and description text remain fallbacks.
- Chapter pictures: YouTube's chapter picture first, then the timeline preview, then the video thumbnail.

## 0.2.18 (2026-10-20) — **untested on device**

- Fullscreen chapter row did not open from the chapter title. YouTube's page could not be inspected from here, so the
  opening no longer depends only on catching YouTube's tap: the app itself now sees every short tap in fullscreen and
  asks the page which visible element is at that point; if it is the chapter title (its text is one of the chapter
  titles, or it is marked "chapter"), the row opens. Taps on the timeline and on controls that are faded out never
  count. The page-side catch stays as a second way. The row needs the video's chapters (from the description: lines
  starting with a time code, the first 0:00); videos without them have no row.

## 0.2.17 (2026-10-20) — **untested on device**

- The app's own description card (0.2.13–0.2.16) is gone: YouTube already shows the description, it was a duplicate.
  The description text is still read in the background, only to find the chapters for fullscreen.
- Fullscreen chapters with pictures: a tap on the chapter title next to the time opens a row of chapter cards — a small
  picture of the video at that moment, the time on it, the title below; the current chapter is outlined. Tapping a card
  jumps there and stays fullscreen; the row closes after 6 s without use or with X. The pictures are YouTube's own
  timeline previews (storyboard sprite sheets on i.ytimg.com, cut per chapter); videos without previews (live streams,
  some short videos) show the video's thumbnail instead. Pictures are cached in memory only.

## 0.2.16 (2026-10-20) — **untested on device**

- Fullscreen chapters: a tap on the chapter title next to the time ("Initial deployment ›") opens the chapter strip
  (right above the time row) and the video stays fullscreen. Why it did nothing before: chapters were read only from
  description links, which the mobile page loads only when the description is opened. Now they also come from the
  player's description text (lines starting with a time code, the first at 0:00 — YouTube's own chapter rule). The
  tap is caught on touchend and click, recognised by "chapter" in the class/label or by the text being one of the
  chapter titles; taps on the timeline itself are never caught (seeking keeps working).
- Flat sheet under the player: a page walk (not only hit-testing) marks every surface ≥ 88 % of the page wide whose top
  is within 48 px above to 260 px below the player's bottom edge (the sheet slides over the player) — also rounded
  ::before/::after layers — with flat top corners. Phone and foldable (both pages run the same script).
- Description card: full width, no rounded corners; the grey divider line right above it is removed.

## 0.2.15 (2026-10-20) — **untested on device**

- Fullscreen: YouTube's own controls (play, timeline, exit fullscreen) work again. Cause: since 0.2.6 a full-size
  Compose layer above the video observed taps for the chapter button; in Compose the top-most layer with a pointer
  handler takes the touches, so the video view (YouTube's controls) got none. Taps are now observed on the video
  view's own node.
- Chapters in fullscreen: no separate "Chapters" button over the video any more. A tap on the chapter title in
  YouTube's timeline opens a slim strip of chapter pills right above the timeline; it hides after 6 s without use.
- Fullscreen on/off: the page remembers the last real position; if the video falls back near the start within 6 s of
  the switch, it goes back there and keeps playing (at most twice; a seek back to the start of your own in those 6 s
  can be undone once). The cause of the restart itself (YouTube rebuilding its player) is not under the app's control.
- Sheet under the player: additionally every surface at least 90 % of the page wide right under the player (found by
  hit-testing, not only the ancestors of the title) loses its rounded top; same at the top of the right column.
- Settings: "Start page" is its own section (StreamShell start page / YouTube / YouTube Music).

## 0.2.14 (2026-10-20) — **untested on device**

- The white sheet under the player (and at the top of the foldable's right column) is flat: rounded top corners of
  the content's containers are set to 0, so nothing shows at the sides.
- New app start (after leaving/closing the app): a fresh page instead of the last video. Video player tabs are
  dropped; with YouTube as the default instance its home page loads; otherwise the start page is shown (an empty tab is
  reused, other tabs stay in the tab list). Returning to the app while it is still open (e.g. from the home screen)
  keeps everything as it was.
- Sound: an opened video plays with sound right away (YouTube carried the muted state of a feed preview into the video
  page). Once per video, after any ad, through YouTube's own player; muting it yourself afterwards is kept.
- Search: suggestions in the site search — recent searches at once (stored on this device, "Clear" removes them), and
  YouTube's suggestions while typing. The typed text goes to suggestqueries.google.com (no cookies, no account); the
  arrow puts a suggestion into the field to keep typing. Offline: only recent searches.

## 0.2.14 (2026-10-20) — **untested on device**

- Bottom bar is arranged by the user: More → "Customize bottom bar" — show/hide Search, Home, Shorts, Live,
  Subscriptions, You (YouTube Music: Home, Explore, Library), move them left/right, Reset. At most 5 plus "More"
  (always last, holds the hidden sections). Kept per site app in the settings. The search capsule can stand anywhere;
  on narrow phones it shows only its magnifier so the labels fit. Not part of the backup file yet.
- The white sheet under the player (and at the top of the foldable's right column) is flat: rounded top corners of
  the content's containers are set to 0, so nothing shows at the sides.
- New app start (after leaving/closing the app): a fresh page instead of the last video. Video player tabs are
  dropped; with YouTube as the default instance its home page loads; otherwise the start page is shown (an empty tab is
  reused, other tabs stay in the tab list). Returning to the app while it is still open keeps everything as it was.
- Sound: an opened video plays with sound right away (YouTube carried the muted state of a feed preview into the video
  page). Once per video, after any ad, through YouTube's own player; muting it yourself afterwards is kept.
- Search: recent searches as soon as the field opens (stored on this device, "Clear" removes them) and YouTube's
  suggestions while typing; the arrow puts a suggestion into the field. The typed text goes to
  suggestqueries.google.com (no cookies, no account). Offline: only recent searches.

## 0.2.13 (2026-10-20) — **untested on device**

- Black corners/strips around YouTube's white sheets (under the title, top of the right column). Causes found in the
  code: (1) the page-colour probe fell back to the app's own dark colour when the page's html and body are both
  see-through — that colour was used for the backdrop, the right column and the chat card; now a see-through page
  counts as the browser canvas (white, or YouTube's dark theme). (2) YouTube's watch page has a dark backdrop behind
  its rounded sheet; ancestors of the content with an opaque colour of the opposite tone now take the page colour
  (the video keeps its black).
- Description: a card under the video information on every video page (phone and foldable) — 3-line preview,
  "Show more"/"Show less", time codes jump within the video. Text from the player's own data for the current video
  (checked by video id), inserted as text, never as HTML. YouTube's own "more" panel still works. If the player does
  not provide the data, no card (and no empty space) appears.
- Order on the page: video information → description → live chat (phones, live only) → recommendations (phones).
- Not done from the brief: violet/cyan palette (the user chose YouTube's black/white); a fully native watch screen
  (title, actions, recommendations as Compose) — the page is YouTube's own; rebuilding it natively would need its data
  APIs, which this app deliberately does not use.

## 0.2.12 (2026-10-20) — **untested on device**

- Recommendations (foldable right column): no frame lines any more; each video is a softly shaded, rounded tile
  (a nested list item gets no second tile). The chat cards keep only a barely visible contour.
- The section under the player starts directly at the player's bottom edge (0 px; 0.2.9 used 8 px, 0.2.10 10 px),
  on phones and foldables alike, without the line on top.
- Foldable: the video title was still missing. Likely cause: the script hid every thin sticky row under the player
  (meant for the chips row); on the taller foldable screen YouTube's title header is probably such a row. Now only
  rows with chips are hidden, other sticky rows just scroll. Not confirmed on a device.
- Foldable: the left page ends with the comments; the second recommendations list under them (and the empty space
  before it) is hidden, the recommendations are in the right column.

## 0.2.11 (2026-10-19) — **untested on device**

- Redesign pass (palette stays YouTube black/white, by choice): the bottom bar takes the page's tone (light glass on a
  light YouTube page, dark on dark) instead of a black band under a white page; the system navigation icons follow it.
  The bar is more compact (56 dp), the active icon lifts slightly; on narrow phones the search capsule shows only its
  magnifier so all labels fit.
- Video information on the YouTube page is restyled by CSS (title 17 px semibold, 16 px grid, actions as outlined pills
  that scroll sideways). These are YouTube's own class names — when YouTube renames them, its default look returns
  (nothing breaks). The live chat's inside is YouTube's and is not restyled; only its frame is the app's.

## 0.2.10 (2026-10-19) — **untested on device**

- Under the player: the section below it starts 10 px lower, with a rounded top edge and a fine line (no dark shadow
  any more). The same measurement now also finds a title hidden under the player (foldable left column) and moves it
  out; earlier only a white band was closed. If YouTube uses another structure there, the title can still be hidden.
- Foldable: the right column is the page colour; the live chat card and every recommendation are framed by a fine
  dark line (light line in dark mode). The recommendations page no longer turns see-through while loading.

## 0.2.9 (2026-10-19) — **untested on device**

- The white band between the player and the title was still there in 0.2.8. Now the empty room YouTube reserves under
  the player is measured on the page itself: the first visible thing under the player (text, picture, button, coloured
  card) is found by hit-testing and the block holding it is pulled up to 8 px under the player. Undo, measure and redo
  run in one step, so it never stacks; it only acts at the top of the page (not while scrolled) and not while a
  description/comments panel is open. If YouTube positions its content absolutely there, the band is left as it is.

## 0.2.8 (2026-10-19) — **untested on device**

- White gap under the player (phone and foldable): the 0.2.7 correction that moves content out from under the player
  added its margin again on every pass. It is now computed from the block's natural position and never stacked; small
  overlaps (YouTube's rounded sheet) are left alone, the shift is capped at 160 px and removed when no longer needed.
  If YouTube changes its page structure the correction may not find the right block.
- Foldable: the live chat card and the recommendations column use the colour of the video page (YouTube light or dark)
  instead of the app's dark colour, so no black frame or black band. The chat's own content is drawn by YouTube.

## 0.2.7 (2026-10-19) — **untested on device**

- Recommendations column: the list starts at the top (offsets on its way up removed; anything pinned at the top of that page, e.g. the hidden player's black area, removed).
- Content under the player: the block after the player is moved down by exactly the overlap when it starts under the video (title and description were hidden under the player in the narrower left column). Measured, no class names.
- Live streams (detected via YouTube's player: isLive / endless duration): phones — YouTube's live chat (youtube.com/live_chat, embed_domain=m.youtube.com) in a rounded card right after the description block, scrolling with the page; its header folds it. Open foldables/tablets — the chat in its own rounded card at the top of the right column (popout chat page, 56 % of the height), recommendations below. Writing in the chat needs a Google sign-in. If YouTube refuses the embedded chat, the card stays empty.

## 0.2.6 (2026-10-19) — **untested on device**

- Loading band: it now stays until the new video plays AND its picture starts at the top edge (the probe's video position), not just "playing" (which could still be the previous video in the reused player).
- Fullscreen chapters: chapters are read from the timestamp links of the video's description / chapter list (not comments; nothing is sent anywhere). In fullscreen a tap on YouTube's chapter title (or StreamShell's "Chapters" button, shown for 4 s after a tap) opens a horizontal strip of chapter cards above the timeline; a card jumps there, the current chapter is outlined, the strip hides after 8 s without use. Needs the description's timestamps to be in the page (usually they are, also when the description is collapsed); videos without timestamps show no strip.

## 0.2.6 (2026-10-19) — **untested on device**

- Loading band: stays until the new video plays AND its picture starts at the top edge (the probe's video position), not just "playing" (which could still be the previous video in the reused player).
- Fullscreen chapters: read from the timestamp links of the video's description / chapter list (not comments; nothing is sent anywhere). In fullscreen a tap on YouTube's chapter title (or StreamShell's "Chapters" button, shown for 4 s after a tap) opens a horizontal strip of chapter cards above the timeline; a card jumps there, the current chapter is outlined, the strip hides after 8 s without use. Videos without timestamps in the description show no strip.
- Open foldables / tablets (content width ≥ 600 dp, either orientation): YouTube video page on the left (62 %: player, below it title, description, comments), its recommendations on the right. The right column is a second copy of the same video page showing only its list (everything above the first list item hidden, no video streamed: googlevideo.com requests refused for it, media paused, no autoplay/ad scripts); a tap there plays the video on the left. It loads the video page a second time (without video data). Replaces the 0.2.0 split (player + YouTube instance, ≥ 840 dp landscape).

## 0.2.5 (2026-10-19) — **untested on device**

- Timestamps: a tap on a "2:13" link to the video on the page jumps the player there and plays (YouTube's player seekTo), with no navigation — in the player, and from the YouTube instance (its tap is turned into a jump in the player instead of loading the same video again, which could bring an ad and set the ad skipper, autoplay and resume going at once).
- If an ad starts when a video is opened at a timestamp, the ad skipper's reload keeps that timestamp.

## 0.2.4 (2026-10-19) — **untested on device**

- White band above a loading player: YouTube places the loading player under its (hidden) top bar. The app now covers that 48 dp strip in black until the video plays (max. 8 s), and the script keeps the player at the very top: it finds the offset on the player or its ancestors (no class names), removes it and watches the player, so an offset YouTube sets again is removed at once.
- Scrolling under the player: the player gets a hairline and a soft shadow (it reads as a raised surface the list scrolls under); recommendations get rounded pictures and some spacing. Based on YouTube's current element names for the list items.

## 0.2.3 fixes (2026-10-19) — **untested on device**

- Autoplay: a pause before the video has really played for 1 s is treated as YouTube's start-up hiccup and retried (up to 6 s); only a pause after real playback counts as yours. (0.2.0–0.2.2 gave up after any early pause.)
- Fullscreen: if the video was playing when entering or leaving fullscreen, it is started again 0.5 s / 1.3 s after the switch.
- Description/comments panels are docked in the frame they appear (checked every frame for 0.9 s after a tap and when YouTube adds a panel), so they no longer show split for a moment.
- Sticky headers inside those panels (with the close X) are no longer released or hidden by the layout fixer.
- While such a panel is open, pull to refresh is off and the minimize swipe only works on the video itself.
- The layout fixer runs as soon as the page has a body and every frame during the first 1.5 s (against the white band of the removed top bar).

## 0.2.2 fixes (2026-10-19) — **untested on device**

- Videos started muted: muting an ad through the <video> made YouTube store "muted" as the user's choice. After an ad the sound is now given back through YouTube's player as well (unMute), and a stale stored "muted" from older versions is repaired once. A mute you set yourself is kept (except for that one-time repair).
- While a video page loads, the web view is see-through and the app shows black where the player will be and the page's colour below (no white area at the top); the layout fixer makes its first pass immediately on a new page.

## 0.2.1 fixes (2026-10-19) — **untested on device**

- Description/comments panels on a video page are docked to the player's real bottom (they kept the removed top bar's 48 px, so the title showed in a gap). Found by position, plus YouTube's current panel class names.
- The background playback service no longer requests audio focus. The WebView requests it itself; both asking made them take focus from each other, and each loss paused the video ("starts, then stops").
- Each page's own background colour is used where the WebView has not painted yet, and returning from the mini player is a shorter animation — against the black band at the top on the way back.

## Watching features (0.2.0, 2026-10-19) — all **untested on device**

- **Continue where you stopped**: positions of videos longer than 1 min are saved every 5 s (local, max. 200); opening the video again jumps there once (after any ad). YouTube itself may also resume for signed-in users. "Start over" is in Playback.
- **Watch later**: a local list (max. 300); YouTube entries open in the YouTube instance's player.
- **Playback speed** 0.5–2x for all videos, kept until changed; ads are not affected by it. **Double-tap ±10 s** only on sites other than YouTube/YouTube Music (they have their own).
- **Sleep timer** 15/30/60 min or end of video (stops before YouTube's autoplay continues).
- **Audio only in the background**: asks YouTube's player for the lowest quality ("tiny") while StreamShell is not visible, "auto" again when you return. YouTube may ignore this request; it does not cut the video stream itself.
- **Volume boost / even out loudness** (YouTube): Web Audio gain + compressor on YouTube's video element. Applies from the next video; once switched on for a page it stays in the chain (neutral when off). Not for other sites (cross-origin media would go silent).
- **Break reminder** and **watch time**: counted while media plays in StreamShell, per day, 30 days, local.
- **Subscriptions**: import of Google Takeout `subscriptions.csv`; "Latest" loads the public feeds of up to 80 channels from www.youtube.com (no sign-in; YouTube may limit or change these feeds).
- **Launcher shortcuts** (long-press the icon): YouTube, Search, Continue watching. They target the release package `com.streamshell`; in the debug build (`com.streamshell.debug`) they do not work.
- **Split view** on wide landscape screens (≥ 840 dp): YouTube player on the left, the YouTube instance on the right.
- **Backup / restore**: one JSON file (Storage Access Framework, no storage permission). No passwords, cookies or sign-ins.

## Gestures, pull to refresh and background notification (2026-10-19)

- The floating back/forward buttons and the minimize button on the video were removed: swipe down on the video minimizes, the system back gesture goes back, Minimize/Back remain in the menus.
- Pull to refresh: at the very top of a page, a pull down that starts below the video (or anywhere on pages without a video) and is let go after ~120 dp reloads the page. It needs the page itself to scroll (it checks the WebView's scroll position); pages that scroll inside their own container may not offer it. **Untested on device.**
- Background notification: picture of the YouTube video (fetched from i.ytimg.com, YouTube's image server, once per video), -10 s, play/pause, +10 s and Exit (stops playback and closes StreamShell's window). Other sites show no picture. Headset next/previous also jump 10 s. **Untested on device.**

## Mini player and floating navigation
- "Minimize" (button at the top left of a YouTube video page, or More/menu) keeps the video playing in its own tab, shown as a small floating window, and returns to the YouTube instance exactly as you left it (videos opened from a non-instance tab still get a new YouTube Home tab). Tap the mini player to return, X to stop. It is the same scaling technique as PiP (the page is not re-loaded); the framing relies on the 1.5 s media probe. Uses one tab (max. 10). **Untested on device.**
- Only YouTube watch/Shorts/live pages can be minimized; one mini player at a time. In PiP the mini player's video fills the PiP window.
- Floating back/forward buttons appear only when a site runs in app mode (browser bars hidden). Back from the screen edge is the system back gesture (both edges on most phones incl. Honor/MagicOS). A forward edge gesture was not added because both screen edges already belong to the system back gesture and would conflict.

## Skip YouTube ads

- 2026-10-19: when an ad starts, the video is re-loaded through YouTube's own player (`loadVideoById`, the method of the public "Auto Skip YouTube Ads" userscript) at most twice per video per minute; otherwise the older mute/16x/jump path runs. If a Skip button is visible, the app presses it with a real touch at its position (max. 3 per ad, 1/s), because a scripted click can be ignored. Both depend on YouTube's current page and can stop working with any YouTube update; YouTube may show its ad-blocker warning. **Untested on device.**
- On by default (Settings > YouTube > Skip YouTube ads). While YouTube's player shows an ad, StreamShell mutes it, presses YouTube's "Skip" button when it is visible, otherwise jumps once to the end of the ad (only for media up to 3 minutes); sound comes back afterwards. Ad cards in the feed and ad overlays on the player are hidden.
- Fixed 2026-10-19 ("video stays black after an ad"): the earlier version jumped every 200 ms while YouTube's ad marker was still set; YouTube removes that marker a moment after switching to the real video, so the real video could be thrown to its end. It also set 16x speed. Now: one jump per ad, never on long media, no speed change, and for 8 s after an ad a stalled picture is nudged once. Verified with a behavioural simulation (`tools/js-sim/run.sh`, fake DOM and clock), not on a device.
- DOM-level, like public ad-skipper userscripts: the ad may still start for a fraction of a second. YouTube changes its ad markup regularly and can detect such scripts (warnings, ads that cannot be skipped, playback errors); then switch it off. Using it is against YouTube's terms; YouTube Premium removes ads officially. **Untested on device.**

## SponsorBlock
- Skips segments of the creator's own video that the SponsorBlock community marked (default: sponsor, self-promotion, interaction reminders; more in Settings). It does **not** remove YouTube's own ads (pre-/mid-rolls): while an ad is shown the skipper waits. Videos nobody has marked are not skipped.
- Works on m.youtube.com/www.youtube.com watch, Shorts and live pages, not on YouTube Music.
- The lookup needs internet access to sponsor.ajay.app; when it fails the video simply plays normally. The "Skipped …" message needs WebView 107+ (web message listener); skipping itself works without it.
- Checked every 0.3 s, so a skip can land up to ~0.3 s into a segment. A segment you seek back into is not skipped again.
- Data: SponsorBlock contributors, CC BY-NC-SA 4.0 (non-commercial use). **Untested on device.**

## Filtering
- Only domain rules are supported; EasyList/EasyPrivacy cosmetic and path rules are skipped.
- Sign-in, video, image and subtitle hosts are never blocked by lists (`FunctionalHosts`), so **YouTube video ads, which come from the same servers as the video, are not blocked.** Banner/tracker requests to ad domains are.
- `shouldInterceptRequest` does not see every request (e.g. some service-worker and WebSocket traffic, requests inside the media pipeline); those are not filtered.
- Blocked counts per page reset when a new page document loads; single-page sites (YouTube) accumulate while you stay on them.

## Browser
- Pop-up windows are not created: `target=_blank` and `window.open` load in the same tab.
- File upload fields, camera, microphone and location requests are refused (no such permissions).
- `blob:` downloads are not supported.
- Cleartext http is upgraded to https; sites without https do not load.
- At most 10 tabs; tabs restored after a restart reload when shown.
