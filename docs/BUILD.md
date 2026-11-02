# Build
Requirements: JDK 17+, Android SDK (platform android-37.0 — compileSdk 37, build-tools 36+), `local.properties` with `sdk.dir`.
Toolchain (0.3.7): Gradle 9.8.2 (wrapper), Android Gradle Plugin 9.4.1 with built-in Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00. targetSdk stays 35 (new Android 16/17 runtime behaviour only after a device test). Maven Central may answer "429 Too Many Requests" on a first, uncached build: run it again.

    ./gradlew :app:assembleDebug               # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew :app:testDebugUnitTest           # 302 JVM tests (offline Robolectric); tools/js-sim/run.sh: 170 page-script simulations
    ./gradlew :app:lintDebug                   # 0 errors; remaining warnings are style hints (KTX helpers, int state) and targetSdk 35
    ./gradlew :app:assembleDebugAndroidTest    # compiles; no instrumented tests exist yet (no emulator)

Release: create `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) next to `build.gradle.kts`; it is git-ignored. Without it `assembleRelease` is unsigned. Release APKs are signed with APK Signature Scheme v2 and v3 (same key; v3 keeps a later key rotation possible). R8 minification is untested on device.

Page-script simulation (needs node): `tools/js-sim/run.sh` exports the YouTube scripts and checks ad skipping, the black-screen case, autoplay and pause handling against a fake DOM.

Static analysis (0.4.0): `tools/lint/static-analysis.sh` — ktlint 1.8 (`.editorconfig`), detekt 1.23.8 (`config/detekt.yml`
+ baseline), ESLint 9 for the exported page scripts (`tools/js-sim/eslint.config.mjs`). All three report 0 findings at
0.4.0 (detekt: beyond the recorded baseline).
