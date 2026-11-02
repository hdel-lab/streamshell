# Device test plan

Nothing here has been run on a device yet: the build machine has no emulator (no KVM). These are the reproducible checks to run on your phone. For each failure, note the step and Android/WebView versions (Settings > About in StreamShell) and, if possible, capture `adb logcat | grep -iE "chromium|streamshell|WebView"`.

| # | Steps | Expected |
|---|---|---|
| 1 | Start the app | Start page, one toolbar, no second navigation bar |
| 2 | Type `kotlin flow` in the bar, Go | Search results of the chosen engine |
| 3 | Type `vimeo.com`, Go | Vimeo opens (https) |
| 4 | Start page > Sign in to Google, sign in | You end on m.youtube.com, signed in (your avatar top right) |
| 5 | Kill the app (recents), reopen, open YouTube | Still signed in |
| 6 | Play a YouTube video, tap YouTube's fullscreen button | Landscape, no system bars; Back leaves fullscreen |
| 7 | Rotate / fold / unfold while playing | Playback continues, no reload |
| 8 | Play a video (not fullscreen), press Home (auto-PiP on) | Floating PiP window shows **only the video** (not white, not the whole page); play/pause works; tap expands back |
| 8b | Same from fullscreen; and via menu > Picture-in-picture | Video only in PiP |
| 9 | Swipe the PiP window away | Playback stops |
| 10 | Settings > Automatic PiP off; play, press Home | Audio continues; media notification with pause/stop; lock screen controls |
| 11 | During 10: call yourself / play another app | StreamShell pauses; resumes after a short interruption |
| 12 | During 10: unplug headphones | Pauses |
| 13 | Notification > Stop | Audio stops, notification disappears |
| 14 | Settings > Background playback off; play, press Home | Audio stops |
| 15 | Open a news site, tap the shield | Count of blocked requests > 0; page works |
| 16 | Shield > switch "Block on <site>" off | Page reloads, count 0 |
| 17 | YouTube: home feed, search, comments, thumbnails, subtitles (CC) with blocking on | All work |
| 18 | AdBlock & Privacy > Update all lists now | Lists show a new "updated" time or a visible error |
| 19 | Airplane mode, restart app | Opens normally; filter engine ready from cached lists |
| 20 | Open several tabs, kill app, reopen | Tabs restored (pages reload when shown) |
| 22 | Start page > YouTube | No StreamShell bars and no YouTube top bar; the page starts right under the status bar (no empty strip); a watch page starts with the player; YouTube-style bar at the bottom |
| 23 | Tap Home / Shorts / Live / Subscriptions / You | The matching YouTube page; active item bold |
| 24 | More > search "lofi" | YouTube results; More > StreamShell start page returns to the start page |
| 25 | More > Show browser bars | Address bar and browser toolbar are back for this visit |
| 26 | Start page > YouTube Music | Home / Explore / Library / More at the bottom |
| 27 | Settings > YouTube app mode off | Normal browser bars, YouTube's own bars back |
| 28 | Open a YouTube video with a known sponsor read (e.g. a tech review), play | At the marked spot the video jumps ahead; message "Skipped: Sponsor (N s)" |
| 29 | Settings > SponsorBlock off | No more skips (from the next video) |
| 30 | Play a YouTube video, tap the arrow at the top left (or More > Minimize video) | Video keeps playing in a small window at the bottom right; YouTube Home opens; you can scroll and search |
| 31 | Tap the mini player / its play button / its X | Back to the video page / pause-play / stops and closes |
| 32 | On YouTube in app mode use the floating ‹ › buttons and the system edge-swipe back | Page back/forward |
| 33 | Open several YouTube videos (ads usually appear after a few) | Ads are silent and gone within about a second; "Ad skipped"; the video starts with sound |
| 34 | Settings > Skip YouTube ads off | Ads play normally again |
| 35 | Open several YouTube videos | Each starts by itself (also right after an ad); a video you paused stays paused |
| 36 | Swipe down on a playing video | It shrinks into the mini player; swiping below the video only scrolls |
| 37 | Tap the YouTube tile, scroll the feed, open a video, minimize it | The feed is exactly where you left it (no reload); the video keeps playing in the mini player |
| 38 | On the video, press Back | The video shrinks (or, if paused, the feed returns) — the feed does not reload |
| 39 | More > YouTube Music chip, then More > YouTube chip | Each instance comes back as it was; only Reload refreshes it |
| 40 | Settings > Open on start: YouTube; restart the app | The app opens straight into the YouTube instance |
| 41 | Open a video after an ad | It starts by itself without tapping play |
| 42 | In the YouTube instance open five videos one after another (back to the feed in between) | The tab counter stays the same; videos open quickly in the same player |
| 43 | Open videos until a pre-roll ad appears | The ad disappears by itself within ~1–2 s (no tap on Skip); the video starts |
| 45 | Open a video and scroll the recommendations | Only the video stays at the top; there is no chip row (All / Uploader …); nothing shows between video and list |
| 44 | Minimize from a YouTube page opened via the address bar, twice | No new tab is opened; the YouTube instance is shown |
| 21 | Long-press a file download link on a site that offers one | "Downloading …" and Android's download notification |
| 46 | Fresh install / Settings > Appearance | Dark Liquid Glass look by default; Light and System still work; text readable on all glass bars |
| 47 | Start page, then open a site | Start page fades/scales in and out; floating toolbar; violet indicator glides between YouTube sections |
| 48 | Settings > Accessibility > Remove animations | No animations, everything still works |
| 49 | At the top of a page pull down below the video and let go | Round refresh chip follows the finger, page reloads; pulling on the video still minimizes |
| 50 | Play a YouTube video, leave the app (background playback on) | Notification shows the video picture, -10 s, play/pause, +10 s, Exit; buttons work, Exit closes the app |
| 51 | Look at the app icon | Red icon with white play inside an open arc; nothing blue |
| 52 | Watch a YouTube video 2 min, leave, open it again | "Continuing at 2:xx"; start page shows it under Continue watching with a red progress bar |
| 53 | Menu › Save to Watch later; start page › Watch later | Video listed with picture; opens in the YouTube player |
| 54 | Menu › Playback › 1.5x; next video | Plays at 1.5x; ads still skipped |
| 55 | Playback › Sleep timer 15 min / End of video | Playback pauses at the time / at the end, YouTube does not continue |
| 56 | Vimeo video: double-tap right/left half | Jumps +10 s / -10 s with a bubble |
| 57 | Settings › Audio only in the background, leave app | Sound continues; (data use lower if YouTube honours it) |
| 58 | Volume boost 200 % / Even out loudness on a quiet YouTube video | Louder / more even from the next video |
| 59 | Break reminder 30 min, watch 30 min | Message (notification when in background) |
| 60 | Subscriptions › choose Takeout subscriptions.csv | Channels listed; Latest shows newest videos with pictures |
| 61 | Long-press the app icon | YouTube / Search / Continue shortcuts work (release build) |
| 62 | Tablet/foldable landscape, open a YouTube video | Player left, YouTube feed right; tapping a video on the right plays it on the left |
| 63 | Settings › Back up, then Restore | File saved; restore brings back bookmarks, Watch later, settings |
| 64 | Open a video, tap the description | The description panel starts right under the video; no title row between them |
| 65 | Open several videos (background playback on) | Each keeps playing; none pauses by itself after starting |
| 66 | Minimize a video, tap the mini player | Full video comes back quickly, no black band at the top |
| 67 | Open a video for the first time | Black where the video will be, no white area at the top |
| 68 | Open several videos (with ads) | Each plays with sound; muting one yourself stays muted |
| 69 | Open videos (first time and later ones) | Each starts by itself |
| 70 | Rotate to fullscreen and back while playing | Keeps playing both ways |
| 71 | Tap the description | Opens directly as one panel under the video, no split moment |
| 72 | Open comments, scroll them, scroll back up | The close X is there; swiping on the comments does not minimize the video or refresh |
| 73 | Open a video page | No white band at the top while it loads |
| 74 | Tap a video in the feed | Black player from the top edge while loading, no white band above it |
| 75 | Scroll the recommendations under a video | The player has a fine line and soft shadow; items have rounded pictures and spacing |
| 76 | In a video's description tap a timestamp | The video jumps there and keeps playing; no reload, no black picture |
| 77 | Minimize, tap a timestamp of that video in the feed's description | The player opens at that time and plays |
| 78 | Tap a video in the feed (also the next one in the same player) | Black at the top while loading, no white band |
| 79 | Fullscreen on a video with timestamps, tap the chapter title above the timeline (or "Chapters") | Strip of chapter cards above the timeline; slide sideways, tap one: the video jumps there and stays fullscreen |
| 78 | Tap a video in the feed (also the next one in the same player) | Black at the top while loading, no white band |
| 79 | Fullscreen on a video with timestamps, tap the chapter title above the timeline (or "Chapters") | Strip of chapter cards above the timeline; slide sideways, tap one: the video jumps there and stays fullscreen |
| 80 | Foldable opened (or tablet), open a YouTube video | Left: larger player, below it title/description/comments; right: recommendations only, no second video playing |
| 81 | Tap a video in the right column; fold the phone again | It plays on the left; folded: normal one-column layout |
| 82 | Open foldable, open a video | Right column starts at the top (no black band); left: title and description fully visible under the player |
| 83 | Phone: open a live stream | Under the description a rounded "Live chat" card with the chat; tapping its header folds it; expanding the description moves it down |
| 84 | Open foldable: open a live stream | Right column: "Live chat" card on top, recommendations below; left: player, description |
| 85 | Phone: open a video (normal and live), wait 10 s, scroll a little and back | Title/description directly under the player, no white gap that grows |
| 86 | Open foldable, YouTube in light theme, open a live stream | Chat card and recommendations on white, no black frame or black band at the top |
| 87 | Phone and open foldable: open several videos (normal, live), also the next one from the list | Title starts right under the player, no white band; expanding the description and the chat still work |
| 88 | Phone: open a video | Small gap (about 10 px) between player and the section below, rounded top edge, no dark shadow |
| 89 | Open foldable: open a live stream | Left: title and description fully visible under the player; right: white column, chat card and each recommendation framed by a fine dark line |
| 90 | App in dark theme, YouTube in light theme: open Home and a video | Bottom bar is light glass on white (no black band), labels not cut off, gesture handle visible |
| 91 | Open a video | Title clear and not oversized; like/share/save as outlined pills, sliding sideways if they do not fit |
| 92 | Open foldable, open a normal video | Left: title, channel, actions, comments right under the player (no gap), no recommendations below; right: shaded rounded tiles, no lines |
| 93 | Phone: open a video | Title section directly under the player, recommendations below as before |
| 94 | Open foldable, light YouTube: open a live stream and a normal video | No black corners around the white sheet or at the top of the right column |
| 95 | Open a video with a description containing time codes | Card under the actions: 3 lines + Show more; tapping 1:05 jumps there; next video shows its own description |
| 96 | Phone and open foldable: open a video | Sheet under the player is flat, no dark corners at the sides; right column top flat too |
| 97 | Watch a video, close the app (Back to exit or swipe it away), open it again | YouTube home (default instance) or the start page, not the last video |
| 98 | In the feed, tap a video whose preview was playing muted | The video plays with sound; mute it yourself → stays muted |
| 99 | Tap Search | Recent searches at once; type "gta" → YouTube suggestions; tapping one searches it; arrow puts it into the field |
| 96 | Phone and open foldable: open a video | Sheet under the player is flat, no dark corners at the sides; right column top flat too |
| 97 | Watch a video, close the app (Back to exit or swipe it away), open it again | YouTube home (default instance) or the start page, not the last video |
| 98 | In the feed, tap a video whose preview was playing muted | The video plays with sound; mute it yourself → stays muted |
| 99 | Tap Search | Recent searches at once; type "gta" → YouTube suggestions; tapping one searches it; arrow puts it into the field |
| 100 | More → Customize bottom bar: add Shorts, move Search to the end, hide You | Bar changes at once in that order; You is under More; Reset restores the default; survives an app restart |
| 101 | Fullscreen: tap the video | YouTube's controls appear; play/pause, timeline and exit fullscreen work |
| 102 | Fullscreen on a video with chapters: tap the chapter title next to the time | Slim chapter strip above the timeline; tapping one jumps there; no extra button over the video |
| 103 | Video at 2:00: fullscreen on, then off | Continues at about 2:00 both times, does not start over |
| 104 | Settings → Start page: choose YouTube Music, close and reopen the app | YouTube Music home opens |
| 105 | Fullscreen on a video whose description has chapters (0:00 …): tap the chapter title next to the time | Strip of chapters above the time row; tapping one jumps there, still fullscreen; dragging the timeline still seeks |
| 106 | Phone and open foldable: open a video | No rounded corners where the white part meets the player; description card full width, flat, no grey line above it |
| 107 | Open a video | No grey card with the description text between comments and recommendations (phone and foldable) |
| 108 | Fullscreen, video with chapters: tap the chapter title next to the time | Row of chapter cards with pictures from the video at those moments; tap one → jumps there, stays fullscreen; row closes after 6 s |
| 109 | Tap a video in the feed (several times, also the next one) | The player is at the very top from the start; no white band that disappears after a few seconds |
| 110 | Open the description / comments | The panel opens right under the player without a jump |
| 111 | Open foldable upright, open a live stream | Player full width at the top, title/description under it; below: recommendations left, live chat right |
| 112 | Same with a normal video | Recommendations across the full width under the description |
| 113 | Video with a long title (phone and foldable) | The title slides along slowly; swiping it sideways works; no "…" |
| 114 | Open foldable, normal video | Video page fills the screen; recommendations under the description; no lower area, no chat button |
| 115 | Open foldable, live stream | "Live chat" button bottom right → chat window under the video (landscape: beside it); X closes it |
| 116 | Install per docs/ANDROID_AUTO.md, connect to the car | StreamShell in the Android Auto launcher; opens YouTube on the car screen |
| 117 | In the car: allow "car speed" on the phone; parked | Picture shows; taps work; pan + swipe scrolls; Search opens the car keyboard |
| 118 | Drive off | Picture covered within ~1 s, sound continues; stop → picture returns |
| 119 | Refuse the speed permission | Cover until "Parked" is tapped |
| 120 | Fullscreen, video with chapters: tap the chapter title next to the time 10 times (controls visible) | The chapter strip opens every time; the first tap on hidden controls only shows them |
| 121 | Minimize a video; drag the mini player to the left | It rests at the left edge; tap opens the video |
| 122 | Push the mini player over the edge | Hidden, thin tab at that edge, sound continues; tap the tab → back |
| 123 | Same on the open foldable | Same behaviour |
| 124 | YouTube video: double tap right/left 5× quickly, then ±10 from the notification and the playback dialog | Each jump lands once, no back-and-forth |
| 125 | Toggle fullscreen, then within 5 s jump back 10 s | Stays where you jumped (not pulled forward) |
| 126 | Right after an ad ends, jump ±10 s | No pause/play stutter |
| 127 | Live stream on a phone: open the inline live chat | Chat looks like YouTube's own chat (no missing header/offsets) |
| 128 | Notification play/pause on a YouTube video, then open the app | YouTube's controls show the same state |
| 129 | Pick 1.5x in YouTube's ⋮ menu, pause and play | Stays 1.5x |
| 130 | "Continue where you stopped" off, sleep timer "End of video" | Pauses at the end |
| 131 | Type in the address bar while a YouTube video plays | Typed text stays |
| 132 | Share a youtu.be link to StreamShell from another app | Opens in the YouTube player |
| 133 | Settings › Reset settings | App settings default, AdBlock lists/rules unchanged |
| 134 | Open a YouTube video | Dislike count next to the dislike button |
| 135 | Share page on a YouTube video | Link without si= |
| 136 | Settings › Jump length 30 s; notification ± | Jumps 30 s, plain rewind/forward icons |
| 137 | Quality on Wi-Fi 1080p; open a video | YouTube's quality menu shows 1080p (if offered) |
| 138 | Fullscreen: swipe up on the right / left half | Volume / brightness change with HUD; taps still control YouTube |
| 139 | Hide in feeds › Shorts | No Shorts shelf on Home and in search |
| 140 | Two videos "Add to queue", play the first to its end | The second opens by itself |
| 141 | Subscriptions › Channels › group button › "Music" | Group chip filters Latest |
| 142 | DeArrow on, open Home | Some titles change; long-press shows the original |
| 143 | Video with SponsorBlock segments, show controls | Coloured marks on the timeline |
| 144 | YouTube home in a normal tab, tap a video, minimize | Mini player shows only the video (no white chip row) |
| 145 | Open a video from the feed and watch it load | No chip row / feed strip over the player |
| 146 | Android Auto (release APK, installed via Play installer) | Still listed and works (host check passes for Google's host) |
| 147 | Update 0.3.6 → 0.3.7 over the installed app | Installs as update (same signature), data kept |
| 148 | Settings › Search suggestions off; type in YouTube search | Only recent searches |
| 149 | Settings › Third-party cookies off; reload YouTube | Pages work; note whether Google keeps you signed in |
| 150 | Diagnostics on; video with ads | Log: "ads: 16x" / "jump", no "reload" for short ads; one ad per break |
| 151 | Mini player plays, scroll the YouTube feed | Feed previews stay paused; mini player plays smoothly |
| 152 | Fullscreen on/off while playing | No restart; at most one "fullscreen guard" line, only after the media is ready |
| 153 | Diagnostics on; mini player plays; tap another video in the feed | Log: no "playing tab: <feed tab>" between "mini player off" and the player tab |
| 154 | Fullscreen on at about 3 s into a video | Continues at about 3 s (log: "fullscreen guard: back to 3" if YouTube restarted it) |
| 155 | Diagnostics on; open several videos one after another | Log: "open video: switched in page", or the reason for a full page load |
| 156 | Mini player plays; tap it to open the full player (several times) | Keeps playing; log: no "playing tab: <feed tab>" in between, no "pause" |
| 157 | Watch a video past 10 s in one tab, open it again from the feed | Log: "resume: continue at …" (or the reason after "resume: none") |
| 158 | Start the app on the YouTube home page (diagnostics on) | No "resume: continue at …" for the feed preview |
| 159 | Video with a long ad (over 3 min) | Log: "ads: 16x" / "jump", no "reload" |
| 160 | Fullscreen on in the first second of a video | Keeps playing (log: at most one "fullscreen: still standing after the switch, play") |
| 161 | Pause, then fullscreen on | Stays paused |
| 162 | YouTube: play a video, minimize, wait, minimize again (diagnostics on) | Feed stays as it was; no `page load` line for the feed tab |
| 163 | Scroll the feed fast, stop the scroll with a finger on a video | Nothing opens (log: `tap guard`); a normal tap opens it |
| 164 | Feed previews off (default): scroll the feed | Only pictures, no previews; Settings › Feed › Feed previews on brings them back |
| 165 | Fullscreen: swipe down in the middle; tap, then the exit button top-left | Fullscreen ends both times |
| 166 | Fullscreen: swipe up/down on the left / right third | Brightness / volume change |
| 167 | Fullscreen: pinch out / in | Video fills the screen / whole picture again |
| 168 | Mini player: drag it far beyond an edge and let go | Closed (as X); a short drag still docks/hides it |
| 169 | Mini player shows | Thin progress bar moves every second; play/pause works |
| 170 | Settings › Playback › Back from the player: Close video; Back on a video | Video stops, feed shows, no mini player |
| 171 | Playback menu: Remember speed for this channel; open another video of that channel / of another channel | Same speed / normal speed |
| 172 | Playback menu: Repeat › Whole video; Part: set start, set end | Video loops; part loops; next video does not loop |
| 173 | Settings › Playback › Captions: Always on (en) / off | New videos start with / without captions (may not work on every video) |
| 174 | Video with chapters: action row › Chapter | Jumps to the next chapter, its title shows |
| 175 | Settings › Feed: Shorts as normal videos; tap a Short in the feed | Opens in the normal player; Shorts tab still swipes |
| 176 | Settings › Feed: Hide watched, add a blocked channel and word | Matching tiles disappear; removing the entries brings them back |
| 177 | Library: create a playlist, add videos (action row › Playlist), move, remove, Play all | List order kept; plays one after another |
| 178 | Backup, then restore on a clean install | Playlists, blocked lists, accent, AMOLED, captions are back |
| 179 | Import NewPipe subscriptions.json | Channels listed; their newest videos load |
| 180 | Library › New private tab: open YouTube, watch, close the tab | No history, no Continue watching entry; signed-out in the private tab (if multi-profile is supported) |
| 181 | Watch time | 30-day total; most watched channels after some watching |
| 182 | Settings: AMOLED black, accent green; search "amoled" | Black background, green accent; search shows only Appearance |
| 183 | Video page | Action row above the bottom bar; switch off in Settings › Playback |
| 184 | Add the widget; stop a video mid-way | Widget lists it; tap opens the video |
| 185 | Long-press the app icon | Watch later and Library shortcuts open their panels |
| 186 | Open a YouTube video | Bottom bar shows Queue · Playlist · Speed (highlighted, middle) · Repeat · More, same look as the normal bar |
| 187 | Slide the bar sideways and let go | It snaps an item into the middle; a light tick per item; Search and sections reachable to the right |
| 188 | Tap items (Speed, Repeat, Playlist, More) | Click vibration; dialog / repeat / playlist picker / menu with the action grid |
| 189 | Minimize the video / go to the feed | Normal bar again |

| 190 | Sliding bar: slide to Search, then keep pulling right; let go | Stops firmly, stronger buzz, slides back to Speed |
| 191 | Sliding bar: slide to Search and let go without pulling on | Stays on Search; a tap opens search |
| 192 | Sliding bar: slide fully left / right | Always five items, no empty space; the end item sits at the capsule's edge |
| 193 | Sliding bar: at an end, keep pulling and let go | Row stretches a little (harder and harder), springs back; pulled far: buzz, then back to Speed |
| 194 | Open a live stream and a vertical / 4:3 video | No black band above the player once it plays (at most ~3 s while loading) |
| 195 | Open a video | Its picture with a loading ring shows until it plays (no plain black) |
| 196 | Start the app, wait 3 s on the feed, open the first video (diagnostics on) | Log: `player prepared …`, then `open video: switched in page`; starts in about 1 s |
| 197 | Start the app and open a video within 2 s (diagnostics on) | Log: `player prepared: still loading, full page load`; the video plays |
| 198 | Start the app, wait 5 s on the feed, open a video | Log: `player prepared: used`, then `open video: switched in page (player playing)` |
| 199 | Android Auto (DS7): open StreamShell | Page sized like a phone held sideways (no oversized text/tiles); rail at the left edge |
| 200 | Car: tap Search / Home / Back / ▲ / ▼ on the rail | Car keyboard / YouTube home / back one page / page scrolls up / down |
| 201 | Car: open a video | Play bar at the bottom: −10 s, play/pause, +10 s, time and progress move each second |
| 202 | Car: while driving (or before "Parked") tap pause on the play bar | Sound pauses; page stays covered; taps on the page do nothing |
| 203 | Phone: Settings › Playback › "Android Auto: controls on the left" off; reopen in the car | Rail at the right edge |
| 204 | Diagnostics on, Trace everything on; tap a few bar items, open Settings, change a switch | Log: `ui · tap on bar-item-… at x,y dp`, `ui · open Settings`, `settings · setting … → …` |
| 205 | Trace: play a video for 30 s, scroll the feed | `frames: dropped …`, `scroll … px`, `page finished (…) after … ms · requests …`, maybe `buffering … ms` |
| 206 | LOG button: drag it, tap it on the feed, in Library, in Settings | Moves; opens the log from every screen; hidden in fullscreen and PiP |
| 207 | Log › Save as file | File `StreamShell-log-….txt` with header, `== summary ==` and all lines |
| 208 | Android Auto with Diagnostics on (see CAR_ARCHITECTURE.md §8) | `car · session started`, `surface available`, `gate: …` lines in the phone's log |
| 209 | YouTube video playing → switch to YouTube Music (menu); if it crashes, start again | Message about the previous crash; report at the top of the diagnostics log — send it |
| 210 | YouTube Music | No second tab bar at the bottom; StreamShell's bar: search, Home, Explore, Library |
| 211 | Video playing, receive a call; end it | Pauses at the ring/call, plays again ~1 s after; log `audio · call …` |
| 212 | Video playing, navigation/radar app speaks | Quieter while it speaks, then full volume; if it had stopped, plays again |
| 213 | Car: rail music/YouTube button | Switches to YouTube Music home / YouTube home; search hint follows |
| 214 | Car while driving (or before "Parked") | Cover shows title, artist and previous / play / next; they work; page taps ignored |
| 215 | Car play bar on a music or video page | Previous and next work (log `car · next track (handler|button)`) |
| 216 | YouTube (video in mini player) → YouTube Music → back, several times | No crash; no "previous crash" message on the next start |
| 217 | Mini player during a video and during an ad | No YouTube buttons in the mini picture; mini play/pause and X work; the ad is still skipped |
| 218 | Open videos (normal, live, vertical) | While loading: the picture over the whole strip, no black band above/below |
| 219 | YouTube Music: play a song | Sliding bar with previous/next; swipe left/right over the picture = next/previous |
| 220 | YouTube Music: swipe down / Back with a song playing | Mini player shows the song's picture or video |
| 221 | YouTube Music in the background: headset / lock-screen next and previous | Next / previous track |
| 222 | Open a video from the feed | Loading screen: picture + ring, placeholders below in the page colour; no white page; fades when playing |
| 223 | Mini player | No previous/play/next/time/fullscreen of YouTube in the picture; mini play/pause and X work |
| 224 | YouTube Music: song open, swipe down from the picture | Mini player, no reload, no "leave this page?" dialog |
| 225 | YouTube Music: free account with an ad | Ad skipped or jumped (log `ads: …`) |
