# StreamShell on Android Auto (unofficial)

**Status: untested in a car.** Android Auto officially allows no video and no browsers (only on Android Automotive OS,
while parked). StreamShell uses the same unofficial route as CarStream or Fermata Auto: it presents itself as a
*navigation* app (Car App Library), which gives it a surface on the car screen, and shows its browser on that surface.

## What it does
- Car screen: YouTube (m.youtube.com, with StreamShell's compact style, ad skipping, autoplay), full screen.
- Layout (0.6.0): StreamShell's own **control rail** at the left edge (or right: phone Settings › Playback › "Android
  Auto: controls on the left") with **Search** (car keyboard, Android Auto offers it only while parked), **Home**,
  **Back**, **scroll up**, **scroll down**; the page fills the rest. On a video page a **play bar** at the bottom:
  −10 s, play/pause, +10 s, time and progress.
- Source (0.6.2): the rail's top button switches between YouTube and YouTube Music (phone Settings › Playback ›
  "Android Auto: opens with" picks the first). The play bar has previous / next as well.
- While driving the page is covered; the cover shows title and artist and large previous / play-pause / next buttons.
- Density: chosen by StreamShell from the surface size (about 480 dp high, at least 640 dp wide, 120–320 dpi), so the
  page looks like a phone held sideways on wide screens such as the DS7 Crossback's.
- Android Auto keeps one button of its own (it requires one): **Parked** when the car does not report its speed, else
  Search; plus the pan button (✥).
- Tap on the car screen = tap on the page. To scroll: rail ▲/▼, or the pan button (✥), then swipe.
- **Picture only while the car stands still.** Speed from the car (permission "car speed", asked on the phone the first
  time): above ~7 km/h the picture is covered and taps on the page are ignored (rail and play bar still work); the sound keeps playing. If the car gives no speed, the
  picture shows only after you tap "Parked", and any reported movement covers it again.

## Install (Android Auto lists such apps only when Google Play is recorded as their installer)
Same signing key as before, so this updates the installed StreamShell and keeps its data.

**With a computer (adb):**
1. Phone: Settings → About phone → tap Build number 7× → Developer options → USB debugging on. Connect the phone.
2. ```
   adb push StreamShell-0.3.0.apk /data/local/tmp/ss.apk
   adb shell pm install -i com.android.vending -r /data/local/tmp/ss.apk
   adb shell rm /data/local/tmp/ss.apk
   ```
3. Check: `adb shell dumpsys package com.streamshell | grep installerPackageName` → `com.android.vending`.

**Without a computer:** Shizuku (started over Wireless debugging) plus an installer app that can set the installer
package (for example "Install With Options" or "InstallerX"): install the APK with installer `com.android.vending`.

**Then in Android Auto (once):**
1. Settings → Apps → Android Auto → Additional settings in the app → About → tap **Version** 10× → OK.
2. ⋮ → Developer settings → **Unknown sources** on.
3. Android Auto → Customize launcher → enable **StreamShell**.

**Updates:** always install new versions the same way. A normal install (file manager, browser) records another
installer and Android Auto hides the app again.

## Known limits
- Android Auto can change its checks with any update; then the app disappears from the car until a new trick is found.
- Reports say Android 14+ phones sometimes hide such apps on wired connections; wireless adapters (e.g. AAWireless) are
  a common workaround.
- Pan/scroll and taps are passed as touches; long-press, pinch and text fields on the page do not work from the car
  screen (search uses the car keyboard).
- The car session has its own browser (not the phone's tab); sign-in cookies are shared with the phone.
- Video quality and smoothness depend on the head unit's resolution and the phone; untested.

## Host check (0.3.7)

Release builds accept only Google's Android Auto / Android Automotive hosts (package name + signing-certificate digest, `app/src/main/res/values/car_hosts.xml`, taken from the Car App Library). Debug builds accept any host, e.g. for testing with the Desktop Head Unit. If Android Auto stops showing StreamShell after an Android Auto update with a new host certificate, the list needs the new digest.

## Architecture

Call flow, what StreamShell decides and what the host decides, and the car test plan: [CAR_ARCHITECTURE.md](CAR_ARCHITECTURE.md).
