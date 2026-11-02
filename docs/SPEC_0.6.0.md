# StreamShell 0.6.0 — specification

Written with the `feature-forge` method from github.com/jeffallan/claude-skills (MIT): EARS requirements, acceptance
criteria, checklist. Input: the 0.5.6 log of 2026-11-01 16:11 and two photos of StreamShell in Android Auto on a DS7
Crossback (page far too large, Android Auto's buttons over the page). Layout chosen by the user: "combination" — side
rail for navigation, play bar at the bottom on video pages (like Spotify's controls in the car).
Nothing here is device- or car-tested; "verified" means JVM tests, simulations and static analysis.

Constraints unchanged: no stream extraction, no UA spoofing, no password/cookie/token capture, same signing key, no
push/publication without consent; picture in the car only while parked.

## Requirements

| ID | Requirement (EARS) | Approach | Acceptance |
|---|---|---|---|
| R1 | WHEN a video is opened while the player tab's page is still loading, the app SHALL load the video address instead of switching in the page. | `PlayerOpen.mode(hasPage, loading)` in `onOpenVideo`. | Unit; test plan 197. |
| R2 | WHEN 2.5 s after an in-page switch the player has the new video but has not started (state -1 / 5 / unknown) while autoplay is on, the app SHALL load the video address. | `PlayerOpen.CHECK` (`id|state`), `PlayerOpen.switched`. | Unit (16:11 case); log names the state. |
| R3 | WHEN the prepared player is first used, the log SHALL say whether it was used or bypassed. | `player prepared: used` / `still loading, full page load`. | Test plan 197–198. |
| C1 | WHEN the car surface is created, the app SHALL choose the density itself: ≈ 480 dp high, ≥ 640 dp wide, 120–320 dpi. | `CarDisplay.optimalDpi` → `createVirtualDisplay`. | Unit (1920×720 → 240 dpi = 1280×480 dp; 800×480, 1280×720, 1920×1080, 1024×768, invalid). |
| C2 | The car screen SHALL show a control rail (Search, Home, Back, scroll up, scroll down) at one edge and the page on the rest. | LinearLayout in the Presentation: rail 84 dp + page. | Test plan 199–200. |
| C3 | WHILE a video page shows, a play bar SHALL sit at the bottom with −10 s, play/pause, +10 s, time and progress, updated every second. | `PageKind.isVideoPage`, `PlayerScripts.POSITION`, `probeMedia`. | Unit (clock, progress); test plan 201. |
| C4 | WHERE "Android Auto: controls on the left" is off, the rail SHALL be at the right edge. | `AppSettings.carRailLeft` (DataStore, backup); read before the surface is requested (≤ 1.5 s). | Unit (backup, settings search); test plan 203. |
| C5 | Android Auto's own action strip SHALL hold one button only (Parked, else Search) plus pan. | NavigationTemplate requires a strip. | Code review. |
| C6 | WHILE video is not allowed (driving / not confirmed parked), taps on the page SHALL be ignored and the page covered; rail and play bar SHALL keep working. | Hit test against the page frame in `onClick`; cover only over the page. | Test plan 202. |

## Non-functional
- Safety: the picture rule (ParkGate) is unchanged; while driving only sound controls and navigation buttons respond.
- Performance: one 1 s timer while the car screen shows (two small page scripts on video pages only).
- Privacy: nothing new leaves the device.

## Checklist
- [x] R1–R3, C1–C6 implemented (not device/car-tested)
- [x] JVM tests 318 (Release060Test: 9 new), page-script simulations 170
- [x] ktlint, detekt (baseline: magic numbers of the car layout only), Android lint 0 errors
- [x] KNOWN_LIMITATIONS, DEVICE_TEST_PLAN 197–203, FEATURE_MATRIX, ANDROID_AUTO updated
- [x] Signed release APK 0.6.0 (versionCode 50), same key
