# AdBlock & Privacy

## Where filtering happens
| Traffic | Handling | Code |
|---|---|---|
| Sub-resources of pages (scripts, images, frames, XHR/fetch, media requests the WebView reports) | **Blocked** when a rule matches; answered with an empty 403 | `BrowserEngine.Client.shouldInterceptRequest` -> `PrivacyCoordinator.decideRequest` |
| Top-level pages (typed, tapped, redirected) | A match is **not loaded**; dialog "Keep blocked / Open anyway" | `BrowserEngine.load`, `shouldOverrideUrlLoading` -> `decideNavigation` |
| Filter-list downloads | HTTPS only, trusted hosts only (redirects re-checked), size/rule limits | `HttpsFilterListDownloader` |
| Other apps, the device | Not filtered (no VPN, no root) | – |

## YouTube video ads
Domain filtering cannot remove them (they come from the same servers as the video). The separate "Skip YouTube ads" page script handles them on YouTube (see KNOWN_LIMITATIONS.md).

## Precedence (first match wins)
1. your **allow** rule
2. list exception (`@@||domain^`)
3. your **block** rule
4. functional host (sign-in, video, thumbnails, subtitles: `FunctionalHosts`) -> allowed
5. list block rule (`||domain^`, `$third-party` evaluated against the page's site)
6. otherwise allowed

Before that: protection off -> nothing blocked; the page's site in "Sites without blocking" -> nothing blocked on that page.

## Counting
A request is counted as blocked only when StreamShell actually refused it (sub-resource answered with 403, or a top-level page the user kept blocked). Allowed requests are counted as "seen". Logs keep host names only, never URLs, search terms, titles or cookies; "Keep host-level details" off keeps counters only; everything is in memory and can be cleared.

## Lists
Built-in small list, EasyPrivacy and URLhaus on by default; EasyList optional; your own https lists from hosts you explicitly trust. Supported syntax: `||domain^`, `||domain^$third-party`, `@@||domain^`, hosts-file lines. Updates: validated first (size <= 6 MB, text, at least one rule), atomic replace, last-known-good kept on any failure, ETag/Last-Modified, WorkManager schedule (off/12 h/daily/3 days/weekly), offline start from cache. Rules are data and are never executed.

## Verified by tests
Parsing, malformed/oversized input, matching, precedence including functional hosts and site exemptions, third-party evaluation, 200k-rule lookup performance, update/rollback/ETag/offline, and the real WebView hook (Robolectric: blocked sub-resource returns 403 and is counted; main frame is not answered there; blocked page is not loaded and can be opened anyway). **Not verified on a device:** YouTube/Google/news sites with blocking on and off.
