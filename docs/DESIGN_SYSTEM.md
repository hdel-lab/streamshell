# StreamShell design system — "Liquid Glass"

Dark is the primary look (default for new installs); Light and System remain selectable. All values live in
`app/src/main/java/com/streamshell/designsystem/` — screens never hard-code colours, radii or shadows.

## Colour tokens (`Color.kt`, `StreamColors`)

2026-10-19 (user's choice): YouTube's own contrast — black, white and YouTube blue. Blue is the only accent;
selected chips are inverted (white on dark / black on light) like on YouTube.

| Token | Dark | Light | Use |
|---|---|---|---|
| background | #0F0F0F | #FFFFFF | app background |
| surface | #181818 | #F9F9F9 | sheets |
| surfaceRaised | #272727 | #F2F2F2 | search capsule, fields, dialogs, chips |
| textPrimary / textSecondary | #F1F1F1 / #AAAAAA | #0F0F0F / #606060 | text |
| accent = accentFill (YouTube blue) | #3EA6FF (label #0F0F0F) | #065FD4 (label white) | links, focus, switches, progress, badges |
| accent2 / highlight | #65B8FF / #3EA6FF | #0B57D0 / #065FD4 | logo and progress gradient |
| success / warning / danger | #2BA640 / #F9AB00 / #FF6B63 | #1E7E34 / #B06000 / #CC0000 | status |
| glass / glassStrong | 70 % #212121 / 92 % #181818 | 85 % / 96 % white | glass fills (strong = bars over web pages) |
| glassEdge / glassSheen | white 12 % / 6 % | black 10 % / white 40 % | contour, top reflection |

Every pair is checked in `DesignTokensTest` (text ≥ 4.5:1, outlines ≥ 3:1, strong glass ≥ 90 % opaque, neutrals
are true greys).

## Shape, depth, spacing, motion
- `Radius`: control 12 · field 16 · button 18 · panel 24 · sheet/dialog 28 · pill (floating bars).
- `Depth`: panel 2 dp, floating 12 dp — soft shadows; depth comes mostly from contour and sheen.
- `Spacing`: 2/4/8/16/24/32, content max width 560 dp, touch targets ≥ 44–48 dp.
- `Motion`: 120/180/260 ms standard easing; springs for the nav indicator and press feedback; everything snaps
  when Android's "Remove animations" is on.
- Typography: Nunito (OFL), role-based scale in `Type.kt`; section labels are small caps-style uppercase labels.

## Glass components (`Glass.kt`)
`Modifier.glass` · `GlassSurface` · `GlassPanel` (titled group) · `SectionLabel` · `GlassDivider` ·
`GlassIconButton` · `GlassChip` · `AccentButton` · `GlassButton` · `GlassNavBar` (floating, gliding indicator) ·
`GlowProgress` (YouTube blue) · `Skeleton` · `Modifier.ambientBackground` · `GlassHeader` ·
`Modifier.pressScale`.

No real backdrop blur: on Android it would blur the content itself or need a copy of the WebView every frame
(battery, video). Glass is translucency + contour + sheen + soft shadow; bars over web content use the strong glass.

## Icons
Material Icons, one rule: line icons (arrows, close, add, search, reload, more) use the Rounded set; shape
icons are Outlined when inactive and Rounded (filled) when active. The app icon and the in-app `LogoMark`
share one mark (2026-10-19): a white rounded play triangle inside an open "shell" arc with a small dot (the stream's head), on a red square (#FF3D3D → #E8112D → #8E0019). No blue in the brand mark; the wordmark is "Stream" + red "Shell".

## Screens
Start page (wordmark, logo, prominent search, glass site tiles, bookmarks, recent, Google sign-in panel) ·
browser top bar (glass address field) and floating glass toolbar on phones · floating glass site navigation
(YouTube / YouTube Music: main sections, a search capsule, More; Shorts and Live under More) · dark glass controls over video (minimize, back/forward, mini player) · menu
(rounded glass dropdown) · site sheet · tabs, bookmarks, history panels · settings in glass groups · AdBlock &
Privacy header/sections.

Design previews (no device needed): `./gradlew :app:testDebugUnitTest --tests '*DesignPreviewTest'` renders the
main screens to `app/build/design-preview/*.png`. Popups (menu, sheets) are separate windows and are not in
those images.

## Page tone (0.2.11)

`PageToned(pageColor)` (designsystem/Theme.kt) gives native chrome that sits on a web page the page's tone: Light
tokens on a light page, Dark tokens on a dark one, whatever the app theme. Used by the site bar; the live chat card on
foldables computes its colours from the page colour the same way. `NavBarHeight` = 56 dp for the floating bar and its
search mode. Web content is separated by soft shading, not lines (0.2.12): recommendation tiles are rgba(0,0,0,.05) on light
pages and rgba(255,255,255,.07) on dark ones, radius 16; the chat cards get only a barely visible contour. The content
under the player starts directly at the player's bottom edge (no gap).
