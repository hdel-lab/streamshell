package com.streamshell.core.browser

/** One button in a site app's bottom bar. [icon] is a key the UI maps to an icon. */
data class SiteDestination(val id: String, val label: String, val url: String, val icon: String)

/**
 * "App in the app": when one of these sites is open, StreamShell hides its own browser bars and shows a
 * bottom bar with the site's main sections instead, so the site feels like an installed app. A user style
 * (CSS plus a small layout fixer, like a userstyle/userscript in a desktop browser) removes the site's own
 * duplicate bars where that is known to be safe. The site's player, its controls and its ads are not touched.
 */
enum class SiteApp(val title: String, val searchUrl: String) {
    YOUTUBE("YouTube", "https://m.youtube.com/results?search_query="),
    YOUTUBE_MUSIC("YouTube Music", "https://music.youtube.com/search?q=");

    val destinations: List<SiteDestination>
        get() = when (this) {
            YOUTUBE -> listOf(
                SiteDestination("home", "Home", "https://m.youtube.com/", "home"),
                SiteDestination("shorts", "Shorts", "https://m.youtube.com/shorts/", "shorts"),
                SiteDestination("live", "Live", "https://m.youtube.com/channel/UC4R8DWoMoI7CAwX8_LjQHig", "live"),
                SiteDestination("subscriptions", "Subscriptions", "https://m.youtube.com/feed/subscriptions", "subscriptions"),
                SiteDestination("you", "You", "https://m.youtube.com/feed/library", "you")
            )

            YOUTUBE_MUSIC -> listOf(
                SiteDestination("home", "Home", "https://music.youtube.com/", "home"),
                SiteDestination("explore", "Explore", "https://music.youtube.com/explore", "explore"),
                SiteDestination("library", "Library", "https://music.youtube.com/library", "library")
            )
        }

    fun destinationOf(url: String?): SiteDestination? {
        if (url == null || of(UrlHosts.hostOf(url)) != this) return null
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#').trimEnd('/')
        val id = when (this) {
            YOUTUBE -> when {
                path.isEmpty() -> "home"
                path.startsWith("shorts") -> "shorts"
                path.startsWith("channel/UC4R8DWoMoI7CAwX8_LjQHig") -> "live"
                path.startsWith("feed/subscriptions") -> "subscriptions"
                path.startsWith("feed/library") || path.startsWith("feed/you") || path.startsWith("feed/history") -> "you"
                else -> null
            }

            YOUTUBE_MUSIC -> when {
                path.isEmpty() -> "home"
                path.startsWith("explore") -> "explore"
                path.startsWith("library") -> "library"
                else -> null
            }
        }
        return destinations.firstOrNull { it.id == id }
    }

    fun searchUrlFor(query: String): String = searchUrl + java.net.URLEncoder.encode(query.trim().take(200), "UTF-8")

    companion object {
        fun of(host: String?): SiteApp? = when {
            host == null -> null
            host == "music.youtube.com" -> YOUTUBE_MUSIC
            host == "youtube.com" || host.endsWith(".youtube.com") -> YOUTUBE
            else -> null
        }
    }
}

/**
 * The YouTube user style. CSS hides YouTube's own top bar (with its search; StreamShell offers search in
 * its menu) and bottom bar (replaced by StreamShell's bar). YouTube reserves room for its 48 px top bar
 * with paddings and sticky offsets that change between releases, so a small fixer looks at the page's
 * outer layout boxes and removes exactly those ~48 px offsets (once per element, never touching the player
 * controls). To keep scrolling smooth it only works for 6 s after a page load or YouTube navigation, in idle time,
 * with a small budget per run. It is best effort and can be switched off (Settings > YouTube app mode), which reloads the page.
 */
object YouTubeSite {
    const val STYLE_ID = "streamshell-compact-youtube"
    const val HEADER_PX = 48

    /** "panel|1" / "panel|0": a description/comments panel is open on the video page (gestures stand back). */
    const val PANEL_MESSAGE = "panel|"

    val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")

    /** Only YouTube itself gets the style; YouTube Music keeps its own bars (they hold its search). */
    fun isYouTube(host: String?): Boolean = SiteApp.of(host) == SiteApp.YOUTUBE

    const val CSS = "#header-bar,ytm-mobile-topbar-renderer,header.mobile-topbar-header,ytm-pivot-bar-renderer{display:none!important}" +
        "ytm-app,#app,.page-container{padding-top:0!important;padding-bottom:0!important;margin-top:0!important}" +
        "ytm-watch .player-container,.player-container.sticky-player,ytm-header-bar.sticky-player{top:0!important}" +
        // YouTube's own mobile CSS (checked in its m.youtube bundle, 2026-10-20) reserves its 48 px top bar in more places:
        // `.player-container{position:absolute;top:48px}` while a video opens — the player sat under a white band until
        // YouTube switched it to `.sticky-player` seconds later — the feed's chip bar, the description/comments panel
        // (`top:calc(56.25vw + 48px)`), the page's scroll padding and the landscape player height. All set without it.
        ".player-container{top:0!important}" +
        // YouTube's watch page paints its page black (`.page-container.page-black-background`, behind the flag
        // mweb_watch_background_colors) and lays its rounded white sheet over it: the black showed in the sheet's corners.
        ".page-container.page-black-background{background:transparent!important}" +
        ".rich-grid-sticky-header ytm-feed-filter-chip-bar-renderer{top:0!important}" +
        ".mobile-topbar-header-background{display:none!important}" +
        "html.watch-scroll{scroll-padding-top:0!important}html.watch-scroll.sticky-player{scroll-padding-top:56.25vw!important}" +
        "@media (max-width:930px){ytm-engagement-panel{top:56.25vw!important}}@media (max-aspect-ratio:1/1){ytm-engagement-panel{top:56.25vw!important}}@media (min-width:931px) and (orientation:landscape){ytm-engagement-panel{top:0!important}}" +
        ".mweb-landscape-full-size-player .player-container .player-size,.mweb-landscape-full-size-player ytm-watch .player-size{max-height:100vh!important;max-height:100svh!important}" +
        "ytm-browse,ytm-search,ytm-single-column-browse-results-renderer{padding-top:0!important}" +
        // Video page: no related-chips row under the player (it stuck with a gap after the removed top bar).
        "ytm-watch ytm-chip-cloud-renderer,ytm-watch ytm-related-chip-cloud-renderer,ytm-watch [class*=\"chip-cloud\"],ytm-watch [class*=\"chip-bar\"]{display:none!important}" +
        // The player (found by the script, class ss-player) stays at the very top, also while loading, above the list
        // (items scroll under it), flat — no shadow.
        ".ss-player{top:0!important;margin-top:0!important;z-index:4!important;box-shadow:none!important}" +
        // The video title under the player (marked by the script): one line that moves slowly and can be swiped sideways,
        // instead of being cut off with "…".
        ".ss-marquee{display:block!important;white-space:nowrap!important;overflow-x:auto!important;overflow-y:hidden!important;text-overflow:clip!important;-webkit-line-clamp:unset!important;max-height:none!important;scrollbar-width:none!important;-webkit-mask-image:linear-gradient(90deg,#000 90%,transparent);mask-image:linear-gradient(90deg,#000 90%,transparent)}" +
        ".ss-marquee::-webkit-scrollbar{display:none!important}.ss-marquee *{white-space:nowrap!important;-webkit-line-clamp:unset!important}" +
        // Flat surfaces under the player (marked by the script; also their ::before/::after layers).
        ".ss-flat,.ss-flat::before,.ss-flat::after{border-top-left-radius:0!important;border-top-right-radius:0!important}" +
        // A video opened from the feed in the same page: YouTube keeps the feed (with its sticky chip row "All · Gaming ·
        // Live …") in the page while the video page builds up, and it showed over and around the player — at the top of
        // the mini player and while a video loads. On a video page (html.ss-watch, set by the script) it is hidden.
        "html.ss-watch ytm-browse,html.ss-watch ytm-search,html.ss-watch .rich-grid-sticky-header,html.ss-watch ytm-feed-filter-chip-bar-renderer{display:none!important}" +
        ".ss-player-pad{padding-top:0!important;margin-top:0!important}" +
        // Recommendations under the video: rounded pictures and a little air between items.
        "ytm-watch ytm-video-with-context-renderer,ytm-watch ytm-compact-video-renderer,ytm-watch ytm-media-item{margin:6px 10px!important}" +
        "ytm-media-item .media-item-thumbnail-container,ytm-video-with-context-renderer .media-item-thumbnail-container,ytm-compact-video-renderer .compact-media-item-image," +
        "ytm-media-item ytm-thumbnail-cover,ytm-video-with-context-renderer ytm-thumbnail-cover{border-radius:12px!important;overflow:hidden!important}" +
        // Video information under the player: a clear title (prominent, not oversized), owner row and action bar on one
        // 16 px grid, actions as outlined pills that scroll sideways instead of overflowing.
        "ytm-watch .slim-video-information-title,ytm-watch .slim-video-metadata-title,ytm-watch ytm-slim-video-information-renderer h2{font-size:17px!important;line-height:1.32!important;font-weight:600!important;letter-spacing:-.005em!important}" +
        "ytm-watch ytm-slim-video-information-renderer{padding:4px 16px 6px!important}" +
        "ytm-watch ytm-slim-owner-renderer{padding:6px 16px!important}" +
        "ytm-watch .slim-video-action-bar-actions{gap:8px!important;padding:6px 16px 10px!important;overflow-x:auto!important;scrollbar-width:none!important}" +
        "ytm-watch .slim-video-action-bar-actions::-webkit-scrollbar{display:none!important}" +
        "ytm-watch ytm-slim-video-action-bar-renderer .yt-spec-button-shape-next{border-radius:999px!important;box-shadow:inset 0 0 0 1px rgba(15,15,15,.22)!important}" +
        "html[dark] ytm-watch ytm-slim-video-action-bar-renderer .yt-spec-button-shape-next{box-shadow:inset 0 0 0 1px rgba(255,255,255,.3)!important}"

    /**
     * Installs [CSS] once and starts the offset fixer. Safe to run at document start and again later.
     * The fixer only reads computed styles and sets padding/margin/top on layout containers; it never
     * reads cookies, storage or page content and makes no requests.
     */
    val INSTALL: String = """
(function(){
if(window.top&&window.top!==window)return 'frame';if(window.__streamshellCompact)return 'ok';window.__streamshellCompact=true;
var ID='$STYLE_ID',H=$HEADER_PX,done=new WeakSet();
function style(){if(document.getElementById(ID))return;var s=document.createElement('style');s.id=ID;s.textContent=${jsString(CSS)};(document.head||document.documentElement).appendChild(s);}
function near(v){return v>=H-8&&v<=H+8;}
function fix(el){if(!el||el.nodeType!==1||done.has(el))return;var t=el.tagName;if(t==='VIDEO'||t==='IFRAME'||t==='SCRIPT'||t==='STYLE')return;
var cs=getComputedStyle(el),changed=false;
if(near(parseFloat(cs.paddingTop)||0)){el.style.setProperty('padding-top','0px','important');changed=true;}
if(near(parseFloat(cs.marginTop)||0)){el.style.setProperty('margin-top','0px','important');changed=true;}
if(cs.position==='sticky'||cs.position==='fixed'){var top=parseFloat(cs.top);if(!isNaN(top)&&top>=H-8&&top<=H*3){el.style.setProperty('top',Math.max(0,top-H)+'px','important');changed=true;}}
if(changed)done.add(el);}
function walk(root,depth,budget){if(!root||depth>5)return budget;var c=root.children;for(var i=0;i<c.length&&budget>0;i++){budget--;fix(c[i]);budget=walk(c[i],depth+1,budget);}return budget;}
var pending=false,href='',activeUntil=0,navAt=0,needRun=false;
function run(){pending=false;if(!document.body)return;style();guardPlayer();fix(document.body);walk(document.body,0,300);
var x=document.querySelectorAll('.player-container,ytm-feed-filter-chip-bar-renderer,.rich-grid-sticky-header,ytm-chip-cloud-renderer,#player-container-id');for(var j=0;j<x.length;j++)fix(x[j]);}
function later(){var now=Date.now();var h0=document.documentElement;if(h0&&h0.classList){var w0=location.pathname==='/watch';if(h0.classList.contains('ss-watch')!==w0)h0.classList.toggle('ss-watch',w0);}if(location.href!==href){href=location.href;activeUntil=now+6000;navAt=now;
// First pass at once on a new page (the gap of the removed top bar would show while waiting) — as soon as there is a body.
if(document.body){run();}else{needRun=true;}}
if(needRun&&document.body){needRun=false;run();}
if(now>activeUntil||pending)return;pending=true;
// First 1.5 s of a page: every frame (no visible gap while it builds up); afterwards when idle.
if(now-navAt<1500){requestAnimationFrame(run);return;}
var idle=window.requestIdleCallback;setTimeout(function(){if(idle){idle(run,{timeout:500});}else{run();}},300);}
// The player keeps the very top: YouTube places it under its (removed) top bar while the video loads and can set
// that offset again from its own code. The offset is found on the player and its ancestors (no class names needed),
// removed, and watched: if YouTube puts it back, it is removed again at once.
var guarded=null,guardObs=null;
function guardPlayer(){try{guardNow();}catch(e){}}
function guardNow(){if(location.pathname!=='/watch'||!document.body)return;var p=playerEl();if(!p||!p.classList||!p.getBoundingClientRect)return;
if(guarded!==p){guarded=p;p.classList.add('ss-player');if(guardObs)guardObs.disconnect();
if(window.MutationObserver){guardObs=new MutationObserver(function(){guardPlayer();});guardObs.observe(p,{attributes:true,attributeFilter:['style','class']});}}
if(!p.classList.contains('ss-player'))p.classList.add('ss-player');
var top=p.getBoundingClientRect().top,sy=window.scrollY||0,cs0=getComputedStyle(p);
if(top<=1||top>H+16||(sy>1&&cs0.position!=='sticky'&&cs0.position!=='fixed')){settle(p);return;}
for(var el=p,d=0;el&&el!==document.documentElement&&d<8;el=el.parentElement,d++){var cs=el===p?cs0:getComputedStyle(el);
if((cs.position==='sticky'||cs.position==='fixed'||cs.position==='absolute')&&parseFloat(cs.top)>0&&parseFloat(cs.top)<=H+16)el.style.setProperty('top','0px','important');
var pt=parseFloat(cs.paddingTop)||0,mt=parseFloat(cs.marginTop)||0;
if(pt>0&&pt<=H+16){el.classList.add('ss-player-pad');el.style.setProperty('padding-top','0px','important');}
if(mt>0&&mt<=H+16){el.classList.add('ss-player-pad');el.style.setProperty('margin-top','0px','important');}
if(p.getBoundingClientRect().top<=1)break;}
settle(p);}
// The content starts right under the player (GAP 0), whatever YouTube does there: room reserved for its removed top bar
// (a white band) or content slid under the player (title hidden — seen in the narrower left column of a foldable). The
// first visible thing — text under the player, or text / picture / button / coloured card below it — is found by
// hit-testing; the small box around it (its section, e.g. YouTube's rounded sheet) is put directly under the player by
// moving the outermost in-flow block that holds it. Undo, measure and redo happen in one task (no flicker, never stacks).
var pulled=null,settleAt=0,settleT=0,GAP=12;
function settle(p){var now=Date.now();if(now-settleAt<120){if(!settleT)settleT=setTimeout(function(){settleT=0;var q=playerEl();if(q)settle(q);},130);return;}
settleAt=now;closeGap(p);}
function unpull(){var t=pulled;pulled=null;if(!t||!t.dataset||t.dataset.ssPull===undefined)return;
if(t.dataset.ssPm)t.style.setProperty('margin-top',t.dataset.ssPm,t.dataset.ssPp||'');else t.style.removeProperty('margin-top');
delete t.dataset.ssPull;delete t.dataset.ssPm;delete t.dataset.ssPp;}
function clear(c){return !c||c==='transparent'||c==='rgba(0, 0, 0, 0)';}
function hasText(el){for(var c=el.firstChild;c;c=c.nextSibling)if(c.nodeType===3&&/\S/.test(c.nodeValue||''))return true;return false;}
function isContent(el,pageBg,under){if(hasText(el))return true;if(under)return false;var t=(el.tagName||'').toUpperCase();
if(t==='IMG'||t==='SVG'||t==='BUTTON'||t==='A'||t==='INPUT'||t==='CANVAS')return true;
var bg=getComputedStyle(el).backgroundColor;return !clear(bg)&&bg!==pageBg;}
function invisible(el){for(var a=el,d=0;a&&a!==document.body&&d<8;a=a.parentElement,d++){var cs=getComputedStyle(a);if(cs.visibility==='hidden'||parseFloat(cs.opacity||'1')<0.05)return true;}return false;}
function firstContent(p,r){if(!document.elementsFromPoint)return null;
var pageBg=getComputedStyle(document.body).backgroundColor;if(clear(pageBg))pageBg=getComputedStyle(document.documentElement).backgroundColor;
// Several columns near the left edge (a short title does not reach the middle of a wide page) and the middle; at each
// point every element under the player is looked at (not only the first one), so text hidden behind it is found.
var W=window.innerWidth||360,best=null,bt=1e9,xs=[20,36,56,88,Math.round(W/2)],vh=window.innerHeight||800;
var y0=Math.max(r.top+4,Math.round(r.bottom-Math.min(220,r.height*0.7)));
for(var xi=0;xi<xs.length;xi++){for(var y=y0;y<r.bottom+480&&y<vh-2;y+=6){if(best&&y>bt+4)break;
var st=document.elementsFromPoint(xs[xi],y)||[];var hit=null;
for(var i=0;i<st.length&&i<20;i++){var e=st[i];if(e===p||p.contains(e))continue;if(e.contains(p)||e===document.body||e===document.documentElement)break;
if(inPanel(e))return null;if(isContent(e,pageBg,y<r.bottom)&&!invisible(e)){hit=e;break;}}
if(hit){var t=hit.getBoundingClientRect().top;if(t<bt){bt=t;best=hit;}break;}}}
return best?{el:best,top:bt}:null;}
// YouTube's watch page puts a dark backdrop behind its rounded white sheet (black corners on a light page, also around
// the title). Ancestors of the content with an opaque colour of the opposite tone take the page colour; the video
// itself keeps its own black.
function flatten(el){var h=document.documentElement;var dark=!!(h&&h.hasAttribute&&h.hasAttribute('dark'));var pc=dark?'rgb(15, 15, 15)':'rgb(255, 255, 255)';
for(var a=el,d=0;a&&a!==h&&d<12;a=a.parentElement,d++){var bg=getComputedStyle(a).backgroundColor;var m=/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?/.exec(bg||'');if(!m)continue;
if(m[4]!==undefined&&parseFloat(m[4])<0.5)continue;var lum=(+m[1]*0.3+ +m[2]*0.59+ +m[3]*0.11)/255;
if(dark?lum>0.6:lum<0.35)a.style.setProperty('background-color',pc,'important');}}
// The white sheet under the player is flat (no rounded top corners — they showed the backdrop at the sides).
function square(el,p){for(var a=el,d=0;a&&a!==document.body&&d<8;a=a.parentElement,d++){if(a.contains(p))break;var cs=getComputedStyle(a);
if((parseFloat(cs.borderTopLeftRadius)||0)>0||(parseFloat(cs.borderTopRightRadius)||0)>0)a.style.setProperty('border-radius','0','important');}}
// Also any wide surface right under the player (YouTube's sheet may not be an ancestor of the first text): found by
// hit-testing along both edges and the middle; elements at least 90 % of the page wide lose their rounded top.
function squareWide(p,r){if(!document.elementsFromPoint)return;var W=window.innerWidth||360,xs=[2,Math.round(W/2),W-3];
for(var y=Math.ceil(r.bottom)+1;y<r.bottom+150;y+=10){for(var i=0;i<xs.length;i++){var st=document.elementsFromPoint(xs[i],y)||[];
for(var k=0;k<st.length&&k<14;k++){var e=st[k];if(e===p||p.contains(e)||e.contains(p)||e===document.body||e===document.documentElement)continue;
var b=e.getBoundingClientRect();if(b.width<W*0.9)continue;var cs=getComputedStyle(e);
if((parseFloat(cs.borderTopLeftRadius)||0)>0||(parseFloat(cs.borderTopRightRadius)||0)>0)e.style.setProperty('border-radius','0','important');}}}}
function closeGap(p){if((window.scrollY||0)>1||panelOpen)return;var r=p.getBoundingClientRect();if(r.height<40)return;
unpull();var f=firstContent(p,r);if(!f)return;
var box=f.el;for(var a=box.parentElement;a&&a!==document.body&&!a.contains(p);a=a.parentElement){if(a.getBoundingClientRect().top<f.top-40)break;box=a;}
flatten(box.parentElement);square(box,p);squareWide(p,r);
var delta=Math.round(f.el.getBoundingClientRect().top-(r.bottom+GAP));if(Math.abs(delta)<=2)return;delta=Math.max(-300,Math.min(600,delta));
var target=null;for(var b=box;b&&b!==document.body&&b!==document.documentElement;b=b.parentElement){if(b.contains(p))break;
var pos=getComputedStyle(b).position;if(pos==='fixed'||pos==='absolute')return;target=b;}
if(!target||!target.dataset)return;var mt=parseFloat(getComputedStyle(target).marginTop)||0;
target.dataset.ssPull=String(delta);target.dataset.ssPm=target.style.getPropertyValue('margin-top')||'';target.dataset.ssPp=target.style.getPropertyPriority('margin-top')||'';pulled=target;
target.style.setProperty('margin-top',(mt-delta)+'px','important');
var rest=f.el.getBoundingClientRect().top-(r.bottom+GAP);if(Math.abs(rest)>2){mt=parseFloat(getComputedStyle(target).marginTop)||0;target.style.setProperty('margin-top',(mt-rest)+'px','important');}}

// Video page: only the video stays fixed at the top; everything below it scrolls under it. YouTube's own sticky
// bars below the player (related chips "All / Related …") kept the offset of the removed top bar, so content showed
// through a gap. They are found by position (whatever is right under the player), not by class names, and released.
var released=new WeakSet(),seen=new WeakSet(),lastPin=0,raf=0;
function playerEl(){return document.querySelector('ytm-watch .player-container,.player-container.sticky-player,#player-container-id')||document.querySelector('ytm-watch ytm-player,ytm-watch #player');}
function inPanel(el){for(var a=el,d=0;a&&a!==document.body&&d<12;a=a.parentElement,d++){var n=(a.tagName+' '+(typeof a.className==='string'?a.className:'')).toLowerCase();
if(n.indexOf('engagement')>=0||n.indexOf('bottom-sheet')>=0||n.indexOf('dialog')>=0)return true;if(getComputedStyle(a).position==='fixed')return true;}return false;}
function release(el,p){if(!el||el.nodeType!==1||released.has(el)||el===document.body||el===document.documentElement)return;
if(p&&(el.contains(p)||p.contains(el)))return;if(getComputedStyle(el).position!=='sticky')return;
// Sticky headers inside the description/comments panels (with their close X) stay as YouTube made them.
if(inPanel(el)){seen.add(el);return;}released.add(el);
// A thin sticky row (the chips) is removed completely; anything taller just scrolls normally.
var h=el.getBoundingClientRect?el.getBoundingClientRect().height:0;
var chips=/chip/i.test(typeof el.className==='string'?el.className:'')||/CHIP/.test(el.tagName||'')||!!(el.querySelector&&el.querySelector('[class*="chip"],[role="tablist"]'));
if(chips&&h>0&&h<=90){el.style.setProperty('display','none','important');return;}
el.style.setProperty('position','relative','important');el.style.setProperty('top','auto','important');}
// Every wide surface whose top is near the player's bottom edge (also one that slides a little over the player) gets
// flat top corners — found by walking the page (not by hit-testing: decorative layers can ignore touches), including
// rounded ::before/::after layers. At most once per 700 ms.
var flatAt=0;
function flatAll(p,r){var now=Date.now();if(now-flatAt<700)return;flatAt=now;var W=window.innerWidth||360;
var root=document.querySelector('ytm-watch')||document.body;if(!root)return;var all=root.getElementsByTagName('*');
for(var i=0,n=0;i<all.length&&n<2500;i++,n++){var e=all[i];if(e===p||p.contains(e)||e.contains(p))continue;var t=e.tagName;if(t==='VIDEO'||t==='IMG'||t==='svg'||t==='path')continue;
var b=e.getBoundingClientRect();if(b.width<W*0.88||b.top<r.bottom-48||b.top>r.bottom+260)continue;
var cs=getComputedStyle(e),round=(parseFloat(cs.borderTopLeftRadius)||0)>0||(parseFloat(cs.borderTopRightRadius)||0)>0;
if(!round){var be=getComputedStyle(e,'::before'),af=getComputedStyle(e,'::after');
round=(be&&be.content&&be.content!=='none'&&(parseFloat(be.borderTopLeftRadius)||0)>0)||(af&&af.content&&af.content!=='none'&&(parseFloat(af.borderTopLeftRadius)||0)>0);}
if(round&&e.classList&&!e.classList.contains('ss-flat'))e.classList.add('ss-flat');}}
// Long video title: YouTube cuts it with "…" (or a line limit). The first cut-off text right under the player becomes
// one line that slides slowly to its end, pauses, and starts again; it can also be swiped. A touch pauses it for 4 s.
var mq=null,mqAt=0;
function cutOff(e){var cs=getComputedStyle(e);var lc=cs.webkitLineClamp||cs.getPropertyValue('-webkit-line-clamp');
return (cs.textOverflow==='ellipsis'||(lc&&lc!=='none'&&lc!=='unset'))&&(e.scrollWidth>e.clientWidth+2||e.scrollHeight>e.clientHeight+2);}
function findTitle(p,r){var root=document.querySelector('ytm-watch')||document.body;if(!root)return null;var all=root.getElementsByTagName('*');
for(var i=0,n=0;i<all.length&&n<3000;i++,n++){var e=all[i];if(e===p||p.contains(e)||e.contains(p))continue;var b=e.getBoundingClientRect();
if(b.height<8||b.top<r.bottom-4||b.top>r.bottom+160)continue;if(!(e.textContent||'').trim())continue;if(cutOff(e))return e;}return null;}
function marquee(p,r){var now=Date.now();if(now-mqAt<1000)return;mqAt=now;if(mq&&mq.el.isConnected&&r.bottom-4<=mq.el.getBoundingClientRect().top)return;
var el=findTitle(p,r);if(!el||(mq&&mq.el===el))return;mq=null;el.classList.add('ss-marquee');var st={el:el,wait:now+2000,last:0,user:0,back:false};mq=st;
el.addEventListener('touchstart',function(){st.user=Date.now();},{passive:true});
(function step(){if(mq!==st||!el.isConnected)return;var t=Date.now();var max=el.scrollWidth-el.clientWidth;
if(max>2&&t-st.user>4000&&t>=st.wait&&t-st.last>=30){st.last=t;
if(st.back){el.scrollLeft=0;st.back=false;st.wait=t+2000;}else{var x=el.scrollLeft+1;if(x>=max){x=max;st.back=true;st.wait=t+1500;}el.scrollLeft=x;}}
requestAnimationFrame(step);})();}
function pin(){raf=0;if(location.pathname!=='/watch')return;var p=playerEl();if(!p)return;var r=p.getBoundingClientRect();if(r.height<40)return;
if((window.scrollY||0)<=1){try{flatAll(p,r);}catch(e){}try{marquee(p,r);}catch(e){}}
var b=Math.round(r.bottom),x=Math.round((window.innerWidth||360)/2),ys=[4,24,48,72,100];
for(var k=0;k<ys.length;k++){var st=document.elementsFromPoint?document.elementsFromPoint(x,b+ys[k]):[];
for(var i=0;i<st.length&&i<20;i++){var el=st[i];for(var d=0;d<6&&el;d++){if(!seen.has(el)){release(el,p);if(!released.has(el))seen.add(el);}el=el.parentElement;}}}
var c=document.querySelectorAll('ytm-chip-cloud-renderer,ytm-related-chip-cloud-renderer,[class*="chip-cloud"],[class*="chip-bar"]');
for(var j=0;j<c.length&&j<10;j++){var e=c[j];for(var q=0;q<3&&e;q++){release(e,p);e=e.parentElement;}}
dock(p,b,x);report();}
// Panels YouTube opens under the video (description, comments, chapters) are position:fixed at "top bar + player";
// with the top bar removed that left the title showing in a gap. Dock them to the player's real bottom.
var docked=[],panelOpen=false;
function report(){var open=false;for(var i=0;i<docked.length;i++){var el=docked[i];if(!el.isConnected)continue;var r=el.getBoundingClientRect();var cs=getComputedStyle(el);
if(r.height>60&&r.top<window.innerHeight-40&&r.bottom>0&&cs.display!=='none'&&cs.visibility!=='hidden'&&parseFloat(cs.opacity||'1')>0.05){open=true;break;}}
if(open!==panelOpen){panelOpen=open;if(window.streamShellSponsor)window.streamShellSponsor.postMessage('$PANEL_MESSAGE'+(open?'1':'0'));}}
function dock(p,b,x){var want=b+H,cand=[];
var hits=document.elementsFromPoint?document.elementsFromPoint(x,want+8).concat(document.elementsFromPoint(x,want+40)):[];
cand=cand.concat(hits);var q=document.querySelectorAll('ytm-engagement-panel-section-list-renderer,ytm-engagement-panel,[class*="engagement-panel"],[class*="bottom-sheet"]');
for(var i=0;i<q.length&&i<12;i++)cand.push(q[i]);
for(var k=0;k<cand.length&&k<40;k++){var el=cand[k];for(var d=0;d<6&&el&&el!==document.body;d++){
if(!(p&&(el.contains(p)||p.contains(el)))){var cs=getComputedStyle(el);if(cs.position==='absolute'&&(el.tagName==='YTM-ENGAGEMENT-PANEL'||docked.indexOf(el)>=0)){var ra=el.getBoundingClientRect();
if(ra.height>60&&Math.abs(ra.top-b)>1&&ra.top>b-80&&ra.top<b+120){el.style.setProperty('top',((parseFloat(cs.top)||0)+(b-ra.top))+'px','important');if(docked.indexOf(el)<0)docked.push(el);}break;}
if(cs.position==='fixed'){var r=el.getBoundingClientRect();
if(r.height>60&&(Math.abs(r.top-want)<=14||docked.indexOf(el)>=0&&Math.abs(r.top-b)>1&&Math.abs(r.top-want)<=60)){el.style.setProperty('top',b+'px','important');if(docked.indexOf(el)<0)docked.push(el);
if(r.bottom>=window.innerHeight-2&&cs.height!=='auto'&&cs.bottom==='auto')el.style.setProperty('height',(window.innerHeight-b)+'px','important');}break;}}
el=el.parentElement;}}}
var trail=0;
function soon(){var now=Date.now();if(raf)return;if(now-lastPin<200){if(!trail)trail=setTimeout(function(){trail=0;soon();},220);return;}lastPin=now;raf=requestAnimationFrame(pin);}
window.addEventListener('scroll',soon,{passive:true});window.addEventListener('resize',soon,{passive:true});
style();
new MutationObserver(function(ms){later();soon();
if(location.pathname!=='/watch')return;guardPlayer();
for(var i=0;i<ms.length&&i<40;i++){var a=ms[i].addedNodes;for(var j=0;j<a.length&&j<20;j++){var n=a[j];if(n.nodeType!==1)continue;
var k=(n.tagName+' '+(typeof n.className==='string'?n.className:'')).toLowerCase();if(k.indexOf('engagement')>=0||k.indexOf('panel')>=0||k.indexOf('sheet')>=0){pin();burst(900);return;}}}
}).observe(document.documentElement,{childList:true,subtree:true});
// Panels slide in after a tap: dock them in the very frame they appear (checked every frame for 0.9 s).
var burstUntil=0;function burst(ms){var end=Date.now()+ms;if(end<=burstUntil)return;var running=burstUntil>Date.now();burstUntil=end;
if(running)return;(function f(){if(location.pathname==='/watch'){pin();}if(Date.now()<burstUntil)requestAnimationFrame(f);})();}
document.addEventListener('click',function(){burst(900);},true);
document.addEventListener('touchend',function(){if(panelOpen)burst(400);},true);
document.addEventListener('transitionend',function(){soon();},true);
document.addEventListener('DOMContentLoaded',run);window.addEventListener('load',run);later();
return 'ok';})()
""".trim()

    /**
     * The tab is in the mini player ([on]): only the player is painted, everything else on the page is invisible (it keeps
     * its place, nothing moves) on black — whatever the site leaves over or around its player (sticky bars, leftovers of
     * the previous page) cannot show in the small window. Works on any site's main video player and YouTube's.
     */
    fun mini(on: Boolean): String = if (on) {
        "(function(){var h=document.documentElement;if(!h)return 'no';if(!document.getElementById('ss-mini-style')){var s=document.createElement('style');s.id='ss-mini-style';" +
            "s.textContent=${jsString(MINI_CSS)};(document.head||h).appendChild(s);}h.classList.add('ss-mini');return 'ok';})()"
    } else {
        "(function(){var h=document.documentElement;if(h&&h.classList)h.classList.remove('ss-mini');return 'ok';})()"
    }

    /** The player's own controls inside the player container (hidden in the mini player). */
    val MINI_CONTROLS = listOf(
        "ytm-custom-control", "[class*=\"player-control\"]", "[class*=\"ytp-ad\"]", ".ytp-skip-ad", "[class*=\"time-display\"]",
        "ytm-progress-bar", "[class*=\"progress-bar\"]", "[class*=\"fullscreen\"]", ".ytp-unmute", ".ytm-autonav-bar", "[class*=\"ytmClosedCaptioningButton\"]"
    )

    val MINI_CSS = "html.ss-mini,html.ss-mini body{background:#000!important}html.ss-mini body *{visibility:hidden!important}" +
        "html.ss-mini .player-container,html.ss-mini .player-container *,html.ss-mini #player-container-id,html.ss-mini #player-container-id *," +
        "html.ss-mini #movie_player,html.ss-mini #movie_player *,html.ss-mini .html5-video-player,html.ss-mini .html5-video-player *,html.ss-mini video{visibility:visible!important}" +
        // YouTube Music: its player (video or song picture).
        "html.ss-mini ytmusic-player,html.ss-mini ytmusic-player #song-video,html.ss-mini ytmusic-player #song-video *,html.ss-mini ytmusic-player #song-image,html.ss-mini ytmusic-player #song-image *{visibility:visible!important}" +
        "html.ss-mini ytmusic-player-bar,html.ss-mini ytmusic-player .song-media-controls,html.ss-mini ytmusic-player .song-media-controls *{visibility:hidden!important}" +
        // 0.6.3: the player's own buttons (play/pause, skip, ad badges, unmute) showed in the mini player but could not be used
        // there (a tap on the mini player opens the video; its own buttons are play/pause and close). Hidden while mini —
        // still in the page, so the ad skipper can press Skip.
        "html.ss-mini #movie_player [class*=\"player-controls\"],html.ss-mini #movie_player [class*=\"player-controls\"] *," +
        "html.ss-mini #movie_player ytm-custom-control,html.ss-mini #movie_player ytm-custom-control *," +
        "html.ss-mini #movie_player [class*=\"ytp-ad\"],html.ss-mini #movie_player [class*=\"ytp-ad\"] *,html.ss-mini #movie_player .ytp-skip-ad,html.ss-mini #movie_player .ytp-skip-ad *," +
        "html.ss-mini #movie_player .ytp-unmute,html.ss-mini #movie_player .ytp-unmute *,html.ss-mini #movie_player .ytp-paid-content-overlay,html.ss-mini #movie_player .ytp-paid-content-overlay *," +
        "html.ss-mini #movie_player .ytp-chrome-top,html.ss-mini #movie_player .ytp-chrome-top *,html.ss-mini #movie_player .ytp-chrome-bottom,html.ss-mini #movie_player .ytp-chrome-bottom *" +
        "{visibility:hidden!important}" +
        // 0.6.4: on m.youtube the controls (previous/play/next, time, progress, fullscreen) sit NEXT TO #movie_player in the
        // player container (screenshot of 2026-11-02), so the rule above missed them. Same selectors, container-wide.
        MINI_CONTROLS.joinToString(",") { c -> "html.ss-mini #player-container-id $c,html.ss-mini #player-container-id $c *,html.ss-mini .player-container $c,html.ss-mini .player-container $c *" } +
        "{visibility:hidden!important}"

    fun searchEngineFor(onYouTube: Boolean, default: com.streamshell.core.model.SearchEngine) = if (onYouTube) com.streamshell.core.model.SearchEngine.YOUTUBE else default

    private fun jsString(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
