# Code review 0.4.0

Review of the whole app with the skill set github.com/jeffallan/claude-skills (MIT): code-reviewer, kotlin-specialist,
javascript-pro, security-reviewer / secure-code-guardian, test-master, debugging-wizard. Behaviour was kept; every step
was checked with the JVM tests, the page-script simulations and the static-analysis tools. Nothing is device-tested.

## Summary
StreamShell is an Android video browser (System WebView) with YouTube page scripts. The code is in a good, tested state;
the main open problem is the ad-skipping / ±10 s regression, which is not caused by the app's code or libraries (bisected
with 0.3.8) and needs evidence from a device (diagnostics log, added in 0.4.0).

## Critical issues
- None found in the code. (Open: ads / ±10 s on the user's device — see KNOWN_LIMITATIONS 0.4.0.)

## Major issues (fixed in 0.4.0)
| Area | Finding | Fix |
|---|---|---|
| Kotlin | `runBlocking` on the main thread at start (tabs, settings) | read in a coroutine; Activity waits on `awaitReady()` |
| Kotlin / security | page messages parsed ad hoc in one long listener (untrusted input) | sealed `PageMessage.parse`: known formats only, ids/lengths/ranges checked, unit-tested |
| Structure | `BrowserViewModel` > 1000 lines | community look-ups extracted (`CommunityLookups`), tested with fakes |
| Debuggability | no way to see what happens around ads on a device | opt-in in-memory diagnostics log |
| JavaScript | `hasOwnProperty` called on data objects, useless regex escape | `Object.prototype.hasOwnProperty.call`, regex fixed |
| Formatting | inconsistent formatting, unused imports | ktlint format over the code base, `.editorconfig` |

## Minor issues (recorded, not changed)
- detekt baseline (555): long lines in one-line page scripts, magic numbers in UI layout, complex but tested functions
  (ad skipper tick, gesture handling). Splitting them without device tests would risk behaviour; new code is checked in full.
- `BrowserScreen.kt` and `BrowserViewModel.kt` are still large; further splitting is planned step by step.
- targetSdk stays 35 until a device test (Android 16/17 behaviour changes).

## Security (security-reviewer)
| Check | Result |
|---|---|
| Secrets (gitleaks over the source tree, signing files excluded) | no leaks |
| Signing key / passwords | only in git-ignored `signing/` and `keystore.properties` |
| WebView | JS only for pages; file/content access off; mixed content blocked; SSL errors cancel; permissions denied |
| Page → app messages | YouTube origins, main frame only, typed parser with validation |
| Exported components | car service guarded by host validator (release); receivers not exported; PendingIntents immutable |
| Third parties | SponsorBlock/DeArrow hash prefixes; RYD opt-in (video ID); suggestions switchable |
| Test tooling (npm audit) | 0 vulnerabilities |

## Positive
- Pure, unit-tested rules for tabs, queue, groups, settings, backup, filters; page scripts simulated in a fake DOM and jsdom.
- Clear privacy defaults and documentation (SECURITY_PRIVACY.md, KNOWN_LIMITATIONS.md).

## Verdict
Approve for a device test. Before a public release: device test plan, diagnostics evidence for the ad problem.
