package com.streamshell.core.browser

/**
 * "Skip YouTube ads" (opt-out in Settings): a page script for YouTube, of the same kind as public
 * ad-skipper userscripts. While YouTube's player is in its ad state (`.ad-showing`) it mutes the ad, plays
 * it at 16x, jumps once to its end and presses YouTube's own "Skip" button as soon as it is on screen.
 * A style hides ad cards in the feed and ad overlays on the player.
 *
 * Safety: everything is tied to the ad media seen when the ad state began (its source and duration). If the
 * <video> switches to something else while YouTube's ad marker is still set (YouTube removes the marker a
 * moment after switching to the real video), speed is restored at once and nothing is done for 1 s; only if
 * the marker is still there then is it treated as the next ad of an ad break. Media longer than 3 minutes is
 * never sped up or jumped. The Skip button counts as on screen when it has a layout box (getClientRects),
 * which also works for YouTube's fixed-position buttons (2026-10-19 fix: the previous visibility test
 * used offsetParent, which is null for fixed elements, so Skip was never pressed).
 *
 * 2026-10-19 (second change): at the start of an ad the video is re-loaded through YouTube's own player,
 * movie_player.loadVideoById(id, position) — the method the public "Auto Skip YouTube Ads" userscript (tientq64)
 * uses instead of clicking — at most twice per video per minute; the mute/16x/jump path is the fallback. A
 * scripted click() on Skip can be ignored by YouTube (untrusted event), so the app additionally presses the
 * visible Skip button with a real touch at its position (TAP message, max. 3 per ad, rate-limited in the app too).
 *
 * It works on what the page shows (DOM level): it does not rewrite network traffic or YouTube's data.
 * YouTube changes its ad markup often and can detect such scripts; using it is against YouTube's terms.
 */
object YouTubeAds {
    const val MESSAGE = "ad|0"

    /** Longest ad media sped up / jumped (0.4.5: 600, was 180 — a 183 s ad was reloaded instead, about 6 s instead of 1). */
    const val MAX_AD_SECONDS = 600

    /** Message asking the app to press the Skip button with a real touch at these page coordinates. */
    const val TAP = "adtap"

    /** At most this many video reloads (YouTube's own loadVideoById) per video per minute, then the fallback. */
    const val MAX_RELOADS = 2

    /** An ad that cannot be jumped is re-loaded only after this long (and only then). */
    const val RELOAD_AFTER_MS = 4000

    /** localStorage flag: StreamShell muted an ad and has not given the sound back yet. */
    const val MUTED_KEY = "streamshell-ad-muted"

    /** One-time repair of a "muted" YouTube stored because of earlier versions (0.2.1 and older). */
    const val REPAIR_KEY = "streamshell-unmute-v2"

    const val CSS = "ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,ytm-companion-slot," +
        "ad-slot-renderer,ytm-ad-slot-renderer,ytd-ad-slot-renderer,.ytp-ad-overlay-container,.ytp-ad-overlay-slot," +
        "ytm-rich-item-renderer:has(ad-slot-renderer),ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer){display:none!important}"

    private val skipButtons = listOf(
        ".ytp-ad-skip-button", ".ytp-ad-skip-button-modern", ".ytp-skip-ad-button", ".ytp-ad-skip-button-slot button",
        ".ytp-ad-skip-button-container button", "button.ytp-ad-skip-button-modern", ".ytm-skip-ad-button",
        "button[class*='skip-ad']", "button[class*='ad-skip']"
    ).joinToString(",")

    val INSTALL: String = """
(function(){
if(window.__streamshellAds)return 'ok';window.__streamshellAds=true;
// Diagnostics (Settings › YouTube › Diagnostics): a line per action, only when switched on; otherwise nothing happens.
function D(m){try{if(window.__ssDiagLog)window.__ssDiagLog(m);}catch(e){}}
function style(){if(document.getElementById('streamshell-ads-style'))return;var s=document.createElement('style');s.id='streamshell-ads-style';
s.textContent=${js(CSS)};(document.head||document.documentElement).appendChild(s);}
var wasAd=false,prevMuted=false,prevRate=1,rated=false,adSrc=null,adDur=NaN,changedAt=0,jumped={},reported=false,
reloads={},lastPos={},tapAt=0,taps=0,needUnmute=false,longAd=false,adAt=0,reloadTried=false;
// Sound: muting an ad through the <video> makes YouTube store "muted" as your choice, so later videos started
// muted. The sound is now given back through YouTube's own player too; a stale "muted" left by an earlier
// StreamShell version is repaired once (MUTE_REPAIR key); a mute you choose yourself is kept.
function ls(k,v){try{if(v===undefined)return localStorage.getItem(k);if(v===null)localStorage.removeItem(k);else localStorage.setItem(k,v);}catch(e){return null;}}
function unmute(v){try{if(v)v.muted=false;}catch(e){}var mp=document.getElementById('movie_player');
try{if(mp&&mp.isMuted&&mp.isMuted()&&mp.unMute)mp.unMute();}catch(e){}ls('$MUTED_KEY',null);}
function muteAd(v){if(!v.muted){v.muted=true;ls('$MUTED_KEY','1');}}
if(ls('$MUTED_KEY')==='1'||ls('$REPAIR_KEY')!=='1'){needUnmute=true;ls('$REPAIR_KEY','1');}
// The id YouTube's player really plays: right after a switch the address already shows the next video.
function playingId(){try{var mp=document.getElementById('movie_player');if(!mp||!mp.getVideoData)return null;var d=mp.getVideoData();return (d&&d.video_id)||'';}catch(e){return '';}}
function vid(){try{var u=new URL(location.href);if(u.pathname!=='/watch')return null;var v=u.searchParams.get('v');return /^[A-Za-z0-9_-]{11}${'$'}/.test(v||'')?v:null;}catch(e){return null;}}
function reload(){var id=vid();var mp=document.getElementById('movie_player');if(!id||!mp||typeof mp.loadVideoById!=='function')return false;
var r=reloads[id]||(reloads[id]={n:0,at:0});var now=Date.now();if(now-r.at>60000)r.n=0;if(r.n>=$MAX_RELOADS)return false;r.n++;r.at=now;
var start=lastPos[id];if(start===undefined){try{var tq=new URL(location.href).searchParams.get('t')||'';var mm=tq.match(/^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?${'$'}/);
start=mm?(+(mm[1]||0))*3600+(+(mm[2]||0))*60+(+(mm[3]||0)):0;}catch(e){start=0;}}
try{mp.loadVideoById(id,Math.floor(start||0));D('ads: reload '+id+' at '+Math.floor(start||0));return true;}catch(e){D('ads: reload failed '+e);return false;}}
function tap(el){var now=Date.now();if(taps>=3||now-tapAt<1200||!window.streamShellSponsor)return;var r=el.getBoundingClientRect();
if(r.width<8||r.height<8)return;tapAt=now;taps++;
window.streamShellSponsor.postMessage('$TAP|'+Math.round(r.left+r.width/2)+'|'+Math.round(r.top+r.height/2)+'|'+Math.round(window.innerWidth)+'|'+Math.round(window.innerHeight));}
function report(){if(reported)return;reported=true;if(window.streamShellSponsor)window.streamShellSponsor.postMessage('$MESSAGE');}
function onScreen(el){try{return !!(el&&el.getClientRects().length>0&&!el.disabled);}catch(e){return false;}}
function unrate(v){if(rated){rated=false;try{v.playbackRate=prevRate;}catch(e){}}}
function tick(){style();
var player=document.querySelector('.html5-video-player');
var ad=!!(player&&(player.classList.contains('ad-showing')||player.classList.contains('ad-interrupting')));
var v=player?player.querySelector('video'):null;
if(!ad||!v){if(wasAd){D('ads: ad over');wasAd=false;reported=false;taps=0;if(!prevMuted)needUnmute=true;if(v)unrate(v);}
if(needUnmute&&v&&!ad){needUnmute=false;unmute(v);}adSrc=null;changedAt=0;
var cid=vid(),pid=playingId();if(cid&&v&&v.currentTime>0&&(pid===null||pid===cid))lastPos[cid]=v.currentTime;return;}
var src=v.currentSrc||v.src||'';var d=v.duration;
if(!wasAd){wasAd=true;longAd=false;prevMuted=v.muted&&!needUnmute&&ls('$MUTED_KEY')!=='1';prevRate=(v.playbackRate>0&&v.playbackRate<=2)?v.playbackRate:1;adSrc=src;adDur=d;changedAt=0;taps=0;
adAt=Date.now();reloadTried=false;D('ads: ad starts, media '+Math.round(d||0)+' s, src '+String(src).slice(0,24));muteAd(v);}
if(!isFinite(adDur)&&isFinite(d)&&src===adSrc)adDur=d;
var bs=document.querySelectorAll(${js(skipButtons)});for(var i=0;i<bs.length;i++){if(onScreen(bs[i])){D('ads: Skip button pressed');try{bs[i].click();}catch(e){}tap(bs[i]);report();break;}}
// The same ad: its length may still change by a second while it loads (6 s → 7 s in the 0.4.5 log).
var same=src===adSrc&&(!isFinite(adDur)||!isFinite(d)||Math.abs(d-adDur)<=1.5);
if(!same){unrate(v);var now=Date.now();if(!changedAt)changedAt=now;if(now-changedAt<1000)return;adSrc=src;adDur=d;changedAt=0;}
muteAd(v);
if(isFinite(d)&&d>$MAX_AD_SECONDS&&!longAd){longAd=true;D('ads: ad media is '+Math.round(d)+' s long (more than $MAX_AD_SECONDS): not sped up or jumped');}
// Last resort only: an ad that cannot be jumped (no length, longer than $MAX_AD_SECONDS s, or no data) after ${RELOAD_AFTER_MS / 1000} s is
// re-loaded through YouTube's player once. (Reloading first, as before 0.4.1, made YouTube serve a new ad each time.)
if(!reloadTried&&Date.now()-adAt>$RELOAD_AFTER_MS&&(!(isFinite(d)&&d>0&&d<=$MAX_AD_SECONDS)||v.readyState<2)){reloadTried=true;if(reload()){report();return;}}
if(isFinite(d)&&d>0&&d<=$MAX_AD_SECONDS){
if(!rated){try{v.playbackRate=16;rated=true;D('ads: 16x');}catch(e){}}
var k=src+'|'+Math.round(d);if(!jumped[k]&&v.readyState>=1&&v.currentTime<d-1){jumped[k]=1;D('ads: jump to end of '+Math.round(d)+' s ad');v.currentTime=Math.max(0,d-0.25);}
report();}}
style();setInterval(tick,250);
return 'ok';})()
""".trim()

    private fun js(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}

/**
 * "Autoplay videos": starts a YouTube video by itself when its page opens and continues it after an ad break.
 * Black picture after an ad (2026-10-19): when YouTube switches from the ad to the real video, the WebView
 * sometimes keeps a black picture until playback is paused and resumed. For 8 s after each ad break this
 * script checks, 1.5 s after the switch, whether new video frames arrive (getVideoPlaybackQuality) and the
 * time moves; if not, it does exactly what helped by hand: pause, and play again 0.3 s later (once per break).
 * Starting goes through YouTube's own player (movie_player.playVideo(), what its play button does) and falls back to
 * HTML5 play(): calling only video.play() could leave YouTube's player thinking it is paused (2026-10-19 fix).
 * Each video is auto-started at most once (once it really plays), so pausing it yourself is respected.
 */
object YouTubeAutoplay {
    val INSTALL: String = """
(function(){
if(window.top&&window.top!==window)return 'frame';if(window.__streamshellAutoplay)return 'ok';window.__streamshellAutoplay=true;
function D(m){try{if(window.__ssDiagLog)window.__ssDiagLog(m);}catch(e){}}
var tries={},started={},from={},wasAd=false,watchUntil=0,checkAt=0,kicked=false,resumeAt=0,f0=null,t0=0;
var seekAt=0;if(document.addEventListener)document.addEventListener('seeking',function(){seekAt=Date.now();},true);
function vid(){var u=new URL(location.href);var v=u.searchParams.get('v');if(v)return v;var m=u.pathname.match(/^\/(shorts|live)\/([A-Za-z0-9_-]{11})/);return m?m[2]:null;}
function go(v){var mp=document.getElementById('movie_player');try{if(mp&&typeof mp.playVideo==='function'){mp.playVideo();}}catch(e){}
if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}}
function frames(v){try{var q=v.getVideoPlaybackQuality&&v.getVideoPlaybackQuality();return q?q.totalVideoFrames:null;}catch(e){return null;}}
function tick(){var id=vid();if(!id||document.visibilityState!=='visible')return;var player=document.querySelector('.html5-video-player');
var v=player?player.querySelector('video'):document.querySelector('video');if(!v)return;
var ad=!!(player&&(player.classList.contains('ad-showing')||player.classList.contains('ad-interrupting')));
var now=Date.now();
if(ad){wasAd=true;watchUntil=0;resumeAt=0;return;}
if(wasAd){wasAd=false;watchUntil=now+12000;checkAt=now+1500;kicked=false;resumeAt=0;f0=frames(v);t0=v.currentTime;if(v.paused&&!v.ended)go(v);return;}
if(now<watchUntil&&!v.ended){
if(resumeAt){if(now>=resumeAt){resumeAt=0;go(v);}return;}
if(v.paused){go(v);return;}
if(!kicked&&now>=checkAt&&now-seekAt>3000&&v.readyState>=3){kicked=true;var f=frames(v);
var noPicture=(f===null||f0===null)?false:(f<=f0);var noTime=v.currentTime<=t0;
if(noPicture||noTime){D('autoplay: no picture after ad, pause + play');try{v.pause();}catch(e){}resumeAt=now+300;}}}
if(started[id])return;
// Started = it really played for 1 s. A pause before that is YouTube's own start-up hiccup (try again, up to 6 s);
// a pause after real playback is yours and is respected.
if(!v.paused){if(from[id]===undefined)from[id]=v.currentTime;if(v.currentTime-from[id]>=1)started[id]=1;return;}
if(from[id]!==undefined&&v.currentTime-from[id]>=0.8){started[id]=1;return;}
if(v.readyState>=2&&!v.ended&&(tries[id]||0)<15){tries[id]=(tries[id]||0)+1;D('autoplay: start try '+tries[id]);go(v);}}
setInterval(tick,400);
return 'ok';})()
""".trim()
}

/**
 * In the YouTube *instance* tab, tapping a video opens it in the instance's player tab instead of
 * navigating the instance away, so the instance keeps its feed, search or scroll position (like the app).
 * Only active where StreamShell set `window.__streamshellRouteVideos` (the instance tab); everywhere else
 * links behave normally. Watch links only (/watch?v=); Shorts stay in their own swipe feed.
 */
object YouTubeRouter {
    const val CHANNEL = "streamShellNav"
    const val FLAG = "window.__streamshellRouteVideos=true;"

    /** Tiles and links that open a video (feed, search, channel pages, Shorts shelf). */
    private const val TILES = "a[href*=\"/watch\"],a[href*=\"/shorts/\"],ytm-media-item,ytm-video-with-context-renderer,ytm-rich-item-renderer," +
        "ytm-compact-video-renderer,ytm-reel-item-renderer,ytm-shorts-lockup-view-model,ytm-video-card-renderer"

    /** A finger that moved more than this (CSS px) did not tap. */
    const val MOVE_PX = 10

    /** A touch that starts within this time after the page scrolled stops a fling; it does not tap. */
    const val AFTER_SCROLL_MS = 350

    val INSTALL: String = """
(function(){
if(window.top&&window.top!==window)return 'frame';if(window.__streamshellRouter)return 'ok';window.__streamshellRouter=true;
function D(m){try{if(window.__ssDiagLog)window.__ssDiagLog(m);}catch(e){}}
// The feed tab stays "visible" for the page (0.5.0): while the player covers it, YouTube was told it is hidden and rebuilt
// the feed (previews restarted, sometimes new content) every time the mini player brought it back. Its media stays
// paused by the app's silent mode; only feed tabs of the YouTube instance get this.
try{var dp=Document.prototype;Object.defineProperty(dp,'visibilityState',{configurable:true,get:function(){return 'visible';}});
Object.defineProperty(dp,'hidden',{configurable:true,get:function(){return false;}});
if(Object.getOwnPropertyDescriptor(dp,'webkitVisibilityState'))Object.defineProperty(dp,'webkitVisibilityState',{configurable:true,get:function(){return 'visible';}});}catch(e){}
if(window.addEventListener)['visibilitychange','webkitvisibilitychange'].forEach(function(t){window.addEventListener(t,function(e){e.stopImmediatePropagation();},true);});
// Tap guard (0.5.0): a touch that scrolled, or that stopped a running scroll, is no tap — YouTube's tiles (and their
// previews) opened videos when a scroll ended on them.
var sx=0,sy=0,moved=false,downAt=0,lastScroll=0,flingStop=false;
document.addEventListener('touchstart',function(e){var t=e.touches&&e.touches[0];if(!t)return;sx=t.clientX;sy=t.clientY;moved=false;downAt=Date.now();flingStop=downAt-lastScroll<$AFTER_SCROLL_MS;},{capture:true,passive:true});
document.addEventListener('touchmove',function(e){var t=e.touches&&e.touches[0];if(t&&(Math.abs(t.clientX-sx)>$MOVE_PX||Math.abs(t.clientY-sy)>$MOVE_PX))moved=true;},{capture:true,passive:true});
if(window.addEventListener)window.addEventListener('scroll',function(){lastScroll=Date.now();},{capture:true,passive:true});
function notATap(e){if(!(moved||flingStop)||Date.now()-downAt>2000)return false;var el=e.target&&e.target.closest?e.target.closest('$TILES'):null;return !!el;}
function guard(e){if(!window.__streamshellRouteVideos||!notATap(e))return false;e.preventDefault();e.stopImmediatePropagation();if(e.type==='click')D('tap guard: '+(moved?'moved':'scroll stop')+', not opened');return true;}
document.addEventListener('touchend',function(e){if(moved||flingStop){var el=e.target&&e.target.closest?e.target.closest('$TILES'):null;if(el&&window.__streamshellRouteVideos)e.stopImmediatePropagation();}},true);
document.addEventListener('click',function(e){
if(guard(e))return;
if(!window.__streamshellRouteVideos||!window.$CHANNEL)return;
var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;if(!a)return;
var u;try{u=new URL(a.getAttribute('href'),location.href);}catch(x){return;}
var sm=u.pathname.match(/^\/shorts\/([A-Za-z0-9_-]{11})/);if(sm&&window.__ssShortsAsVideos){u=new URL('https://m.youtube.com/watch?v='+sm[1]);}
if(!/(^|\.)youtube\.com${'$'}/.test(u.hostname)||u.pathname!=='/watch'||!/^[A-Za-z0-9_-]{11}${'$'}/.test(u.searchParams.get('v')||''))return;
try{if(new URL(location.href).searchParams.get('v')===u.searchParams.get('v'))return;}catch(x){}
e.preventDefault();e.stopImmediatePropagation();window.$CHANNEL.postMessage(u.href);},true);
return 'ok';})()
""".trim()
}

/**
 * Timestamps ("2:13" links) in a video's description or comments: a tap on a link to the SAME video jumps the
 * player there and keeps it playing — no navigation, no reload (a reload of the same video could bring an ad and
 * set the ad skipper, autoplay and resume all going at once). Links to other videos are left alone.
 */
object YouTubeTimestamps {
    val INSTALL: String = """
(function(){
if(window.top&&window.top!==window)return 'frame';if(window.__streamshellTimestamps)return 'ok';window.__streamshellTimestamps=true;
function D(m){try{if(window.__ssDiagLog)window.__ssDiagLog(m);}catch(e){}}
function sec(t){if(!t)return null;if(/^\d+${'$'}/.test(t))return +t;var m=t.match(/^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?${'$'}/);
if(!m||!m[0])return null;return (+(m[1]||0))*3600+(+(m[2]||0))*60+(+(m[3]||0));}
document.addEventListener('click',function(e){
var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;if(!a)return;
var u;try{u=new URL(a.getAttribute('href'),location.href);}catch(x){return;}
if(!/(^|\.)youtube\.com${'$'}/.test(u.hostname)||u.pathname!=='/watch')return;
var cur;try{cur=new URL(location.href).searchParams.get('v');}catch(x){return;}
if(!cur||u.searchParams.get('v')!==cur)return;
var t=sec(u.searchParams.get('t')||'');if(t===null)return;
var mp=document.getElementById('movie_player');var v=document.querySelector('.html5-video-player video')||document.querySelector('video');
if(!mp&&!v)return;
e.preventDefault();e.stopImmediatePropagation();
try{if(mp&&mp.seekTo){mp.seekTo(t,true);}else{v.currentTime=t;}}catch(x){try{v.currentTime=t;}catch(y){}}
try{if(mp&&mp.playVideo)mp.playVideo();}catch(x){}
if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}
},true);
// Chapters for the fullscreen strip: the timestamp links of this video in its description / chapter list
// (not comments), with the text after each link as its title. Sent to the app only; nothing leaves the device.
var lastV='',lastJson='',timer=0;
function post(m){if(window.streamShellSponsor)window.streamShellSponsor.postMessage('$CHAPTERS'+m);}
function inDescription(a){for(var el=a,d=0;el&&d<14;el=el.parentElement,d++){var n=(el.tagName+' '+(typeof el.className==='string'?el.className:'')).toLowerCase();
if(n.indexOf('comment')>=0)return false;if(n.indexOf('description')>=0||n.indexOf('macro-marker')>=0||n.indexOf('chapter')>=0)return true;}return false;}
function label(a){var s='';for(var n=a.nextSibling,k=0;n&&k<12;n=n.nextSibling,k++){
if(n.nodeType===1){if(n.tagName==='BR')break;if(n.tagName==='A'&&/[?&]t=/.test(n.getAttribute('href')||''))break;}
var tx=n.textContent||'';var nl=tx.indexOf('\n');if(nl>=0){s+=tx.slice(0,nl);break;}s+=tx;}
s=s.replace(/^[\s\-\u2013\u2014:|\u2022\u00b7]+/,'').replace(/\s+/g,' ').trim();
if(!s){var p=a.closest?(a.closest('[class*="marker"],[class*="chapter"]')||a):a;s=((p.getAttribute&&p.getAttribute('aria-label'))||p.textContent||'').replace(/\s+/g,' ').trim();}
return s.slice(0,80);}
// First choice: the chapters YouTube itself shows on the timeline — from the page's own watch data (getWatchNextResponse),
// both chapters written in the description and the automatic chapters YouTube generates (which are in no description),
// each with YouTube's own picture. Only for the video that is open (checked by id).
function findMarkers(o,d){if(!o||typeof o!=='object'||d>16)return null;if(Array.isArray(o.markersMap))return o.markersMap;
for(var k in o){if(!Object.prototype.hasOwnProperty.call(o,k))continue;var v=o[k];if(v&&typeof v==='object'){var r=findMarkers(v,d+1);if(r)return r;}}return null;}
function txt(t){if(!t)return '';if(typeof t==='string')return t;if(t.simpleText)return t.simpleText;if(t.runs)return t.runs.map(function(r){return r.text||'';}).join('');if(t.content)return t.content;return '';}
function fromNext(cur){try{var mp=document.getElementById('movie_player');var nx=mp&&mp.getWatchNextResponse&&mp.getWatchNextResponse();if(!nx)return false;
var ep=nx.currentVideoEndpoint&&nx.currentVideoEndpoint.watchEndpoint;if(!ep||ep.videoId!==cur)return false;
var mm=findMarkers(nx.playerOverlays||nx,0);if(!mm)return false;var ent=null;
for(var i=0;i<mm.length;i++){if(mm[i]&&(mm[i].key==='DESCRIPTION_CHAPTERS'||mm[i].key==='AUTO_CHAPTERS')){ent=mm[i];break;}}
var ch=ent&&ent.value&&ent.value.chapters;if(!ch||ch.length<2)return false;var out=[];
for(var j=0;j<ch.length&&j<150;j++){var c=ch[j]&&ch[j].chapterRenderer;if(!c)continue;var th=(c.thumbnail&&c.thumbnail.thumbnails)||[];var best='';
for(var q=0;q<th.length;q++){if(th[q]&&th[q].url&&((th[q].width||0)<=336||!best))best=th[q].url;}
out.push({t:Math.floor((+c.timeRangeStartMillis||0)/1000),l:txt(c.title).replace(/\s+/g,' ').trim().slice(0,80),i:best.indexOf('https://i.ytimg.com/')===0?best:''});}
if(out.length<2)return false;var js=JSON.stringify(out);if(js!==lastJson){lastJson=js;post(js);}return true;}catch(e){return false;}}
function collect(){timer=0;var cur;try{cur=new URL(location.href).searchParams.get('v');}catch(e){return;}if(!cur)return;
if(cur!==lastV){lastV=cur;if(lastJson!==''&&lastJson!=='[]')post('[]');lastJson='[]';}
if(fromNext(cur))return;
var as=document.querySelectorAll('a[href*="t="]'),map={},n=0;
for(var i=0;i<as.length&&i<400;i++){var a=as[i],u;try{u=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
if(u.pathname!=='/watch'||u.searchParams.get('v')!==cur)continue;var t=sec(u.searchParams.get('t')||'');
if(t===null||Object.prototype.hasOwnProperty.call(map,t)||!inDescription(a))continue;map[t]=label(a);n++;}
if(n<2){fromText(cur);return;}var list=Object.keys(map).map(function(k){return {t:+k,l:map[k]};}).sort(function(x,y){return x.t-y.t;}).slice(0,150);
var j=JSON.stringify(list);if(j===lastJson)return;lastJson=j;post(j);}
// The mobile page puts the description into the DOM only when it is opened, so the chapters also come from the
// player's description text for this video: lines that start with a time code (first one 0:00), the rest of the line
// is the title — the same rule YouTube uses for chapters.
function hms(t){var p=t.split(':'),s=0;for(var i=0;i<p.length;i++)s=s*60+(parseInt(p[i],10)||0);return s;}
function fromText(cur){var text=null;try{var mp=document.getElementById('movie_player');var r=mp&&mp.getPlayerResponse&&mp.getPlayerResponse();var d=r&&r.videoDetails;
if(d&&d.videoId===cur&&typeof d.shortDescription==='string')text=d.shortDescription;}catch(e){}if(!text)return;
var lines=text.split(/\n/),out=[],seen={};for(var i=0;i<lines.length&&out.length<150;i++){var m=/^\s*[[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*[-\u2013\u2014:|\u2022\u00b7]?\s*(.*)$/.exec(lines[i]);if(!m)continue;
var t=hms(m[1]);if(Object.prototype.hasOwnProperty.call(seen,t))continue;seen[t]=1;out.push({t:t,l:m[2].replace(/\s+/g,' ').trim().slice(0,80)});}
out.sort(function(x,y){return x.t-y.t;});if(out.length<2||out[0].t!==0)return;var j=JSON.stringify(out);if(j===lastJson)return;lastJson=j;post(j);}
if(window.MutationObserver)new MutationObserver(function(){if(!timer&&location.pathname==='/watch')timer=setTimeout(collect,600);}).observe(document.documentElement,{childList:true,subtree:true});
setInterval(function(){if(location.pathname==='/watch'&&(lastJson===''||lastJson==='[]'))collect();board();},2000);
// The preview pictures YouTube shows while you drag its timeline (storyboard): their address pattern for this video, so
// the app can show a small picture per chapter. Sent once per video; the pictures come from i.ytimg.com like thumbnails.
var boardFor=null;
function board(){var cur;try{cur=new URL(location.href).searchParams.get('v');}catch(e){return;}if(!cur||cur===boardFor)return;
try{var mp=document.getElementById('movie_player');var r=mp&&mp.getPlayerResponse&&mp.getPlayerResponse();if(!r||!r.videoDetails||r.videoDetails.videoId!==cur)return;
var sb=r.storyboards&&r.storyboards.playerStoryboardSpecRenderer;var spec=sb&&typeof sb.spec==='string'?sb.spec:'';
var dur=parseFloat(r.videoDetails.lengthSeconds)||0;boardFor=cur;if(spec.length<4000)window.streamShellSponsor&&window.streamShellSponsor.postMessage('$STORYBOARD'+cur+'|'+dur+'|'+spec);}catch(e){}}
// In fullscreen, a tap on YouTube's chapter title (button ytwPlayerTimeDisplayPlayerBarButton next to the time) opens
// StreamShell's chapter strip instead. Decided when the finger goes DOWN — YouTube reacts to the tap (controls fade,
// the button changes) before the finger is lifted, which made a check at the end miss most taps. The whole gesture is
// then kept away from YouTube. Only visible controls count (the first tap that shows them is YouTube's), never the
// timeline itself (its segments also carry "chapter" names).
var openedAt=0,armedAt=0;
function fsOn(){return !!(document.fullscreenElement||document.webkitFullscreenElement||window.__ssFullscreen);}
function hidden(el){for(var a=el,d=0;a&&a!==document.documentElement&&d<14;a=a.parentElement,d++){var cs=getComputedStyle(a);
if(cs.visibility==='hidden'||cs.display==='none'||parseFloat(cs.opacity||'1')<0.1)return true;}return false;}
function onTimeline(el){for(var q=el,qd=0;q&&qd<10;q=q.parentElement,qd++){var qn=((typeof q.className==='string'?q.className:'')+' '+((q.getAttribute&&q.getAttribute('role'))||'')).toLowerCase();
if(qn.indexOf('progress')>=0||qn.indexOf('scrub')>=0||qn.indexOf('timebar')>=0||qn.indexOf('time-bar')>=0||qn.indexOf('slider')>=0||qn.indexOf('seek')>=0)return true;}return false;}
function chapterControl(el){for(var e=el,d=0;e&&d<6;e=e.parentElement,d++){var k=((typeof e.className==='string'?e.className:'')+' '+((e.getAttribute&&e.getAttribute('aria-label'))||'')).toLowerCase();
var tx=((e.textContent||'').replace(/[\s›>]+$/,'').replace(/^[\s•·]+/,'').replace(/\s+/g,' ').trim());
if(k.indexOf('chapter')>=0||k.indexOf('playerbarbutton')>=0||(tx.length>1&&tx.length<=80&&titles().indexOf(tx)>=0))return true;}return false;}
function down(e){armedAt=0;if(!fsOn())return;var t=e.target;if(!t||onTimeline(t)||!chapterControl(t)||hidden(t))return;
armedAt=Date.now();e.stopImmediatePropagation();}
function up(e){if(!armedAt||Date.now()-armedAt>1500)return;e.preventDefault();e.stopImmediatePropagation();
if(e.type!=='mouseup'){var now=Date.now();if(now-openedAt>500){openedAt=now;post('open');}}if(e.type==='click')armedAt=0;}
['touchstart','pointerdown','mousedown'].forEach(function(t){document.addEventListener(t,down,{capture:true,passive:false});});
['touchend','pointerup','mouseup','click'].forEach(function(t){document.addEventListener(t,up,{capture:true,passive:false});});
function titles(){try{return JSON.parse(lastJson).map(function(c){return c.l;});}catch(e){return [];}}
// Fullscreen on/off must not start the video over: the last real position (not during an ad) is kept, and if the
// video falls back near the start right after the switch (YouTube rebuilding its player), it goes back there and
// keeps playing. Only within 6 s of the switch, once, and only when the new media is ready (0.4.1: while YouTube was
// still re-loading, the jump made it load again — seen twice in a row in the user's log).
// From 1.5 s on (0.4.3: at 2.8 s it started over at 0 — the old 5 s limit missed it); it only acts when the video is
// clearly before the kept spot (at least 1 s), so a position that is really near the start is never "corrected".
var keepT=0,keepAt=0,keepV=null,guardUntil=0,guardN=0,guardPos=0,userSeekAt=0;
// Keep playing (0.4.5): a fullscreen switch right at the start left the video standing after YouTube rebuilt its player
// (0.4.4 log: no "playing" any more). If it played before the switch and you did not pause it yourself, it is started
// once again, only within 6 s of the switch and only when the media is ready. A pause right after your tap is yours
// (the tap on the fullscreen button itself does not count).
var playOn=false,tapT=0,fsAt=0,userPauseAt=0,playUntil=0,playN=0;
if(document.addEventListener){['touchend','click'].forEach(function(t){document.addEventListener(t,function(){tapT=Date.now();},true);});
document.addEventListener('playing',function(e){if(e.target&&e.target.tagName==='VIDEO'&&!adNow())playOn=true;},true);
document.addEventListener('pause',function(e){if(!e.target||e.target.tagName!=='VIDEO')return;var now=Date.now();if(now-tapT<800&&(tapT>fsAt+200||now-fsAt>2000)){playOn=false;userPauseAt=now;}},true);}
// A jump you make yourself (YouTube's double tap, the timeline, the app's ±10 s) is never "corrected".
if(document.addEventListener)document.addEventListener('seeking',function(){userSeekAt=Date.now();},true);
function pv(){return document.querySelector('.html5-video-player video')||document.querySelector('video');}
function adNow(){var p=document.querySelector('.html5-video-player');return !!(p&&p.classList&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));}
function curId(){try{return new URL(location.href).searchParams.get('v');}catch(e){return null;}}
setInterval(function(){var v=pv();var id=curId();if(!v||!id||adNow())return;var now=Date.now();
if(now<guardUntil&&id===keepV&&guardN<1&&guardPos>1.5&&v.currentTime<Math.min(2,guardPos-1)&&now-userSeekAt>1500&&v.readyState>=2&&isFinite(v.duration)&&v.duration>guardPos){guardN++;D('fullscreen guard: back to '+(Math.round(guardPos*10)/10));
var mp=document.getElementById('movie_player');try{if(mp&&mp.seekTo)mp.seekTo(guardPos,true);else v.currentTime=guardPos;}catch(e){try{v.currentTime=guardPos;}catch(x){}}
try{if(mp&&mp.playVideo)mp.playVideo();}catch(e){}if(v.paused){var pp=v.play();if(pp&&pp.catch)pp.catch(function(){});}return;}
if(now<playUntil&&playN<1&&playOn&&now-fsAt>1500&&userPauseAt<fsAt&&v.paused&&!v.ended&&v.readyState>=2&&isFinite(v.duration)){playN++;D('fullscreen: still standing after the switch, play');
var mp2=document.getElementById('movie_player');try{if(mp2&&mp2.playVideo)mp2.playVideo();}catch(e){}if(v.paused){var p2=v.play();if(p2&&p2.catch)p2.catch(function(){});}return;}
if(v.currentTime>0&&!v.seeking&&nowPlaying()===id){keepT=v.currentTime;keepAt=now;keepV=id;}},250);
function nowPlaying(){try{var mp=document.getElementById('movie_player');var d=mp&&mp.getVideoData&&mp.getVideoData();return (d&&d.video_id)||id0();}catch(e){return id0();}}
function id0(){return curId();}
function onFs(){var now=Date.now();fsAt=now;if(playOn){playUntil=now+6000;playN=0;}if(keepV&&now-keepAt<2000&&keepT>1.5){guardPos=keepT;guardUntil=now+6000;guardN=0;}}
document.addEventListener('fullscreenchange',onFs,true);document.addEventListener('webkitfullscreenchange',onFs,true);
return 'ok';})()
""".trim()

    /** Message prefix: "chapters|[{t,l},…]" (the list) or "chapters|open" (open the fullscreen strip). */
    const val CHAPTERS = "chapters|"

    /**
     * Asked by the app after a short tap in fullscreen at ([xPx], [yPx]) device pixels: is the visible element there the
     * chapter title next to the time? "yes" when its text is one of [titles] or it is marked "chapter"; never for the
     * timeline itself or for controls that are hidden (YouTube's controls fade out). Independent of how YouTube handles
     * the tap.
     */
    fun chapterTitleAt(xPx: Float, yPx: Float, titles: List<String>): String {
        val list = org.json.JSONArray(titles.map { it.take(80) }).toString()
        return "(function(px,py,list){var r=window.devicePixelRatio||1;var el=document.elementFromPoint(px/r,py/r);if(!el)return 'no';" +
            "for(var e=el,d=0;e&&d<12;e=e.parentElement,d++){var n=((typeof e.className==='string'?e.className:'')+' '+((e.getAttribute&&e.getAttribute('role'))||'')).toLowerCase();" +
            "if(/progress|scrub|timebar|time-bar|slider|seek/.test(n))return 'no';var cs=getComputedStyle(e);if(cs.visibility==='hidden'||cs.display==='none'||parseFloat(cs.opacity||'1')<0.1)return 'no';}" +
            "for(var e2=el,d2=0;e2&&d2<6;e2=e2.parentElement,d2++){var k=((typeof e2.className==='string'?e2.className:'')+' '+((e2.getAttribute&&e2.getAttribute('aria-label'))||'')).toLowerCase();" +
            "var tx=(e2.textContent||'').replace(/[\\s\\u203a>]+$/,'').replace(/^[\\s\\u2022\\u00b7]+/,'').replace(/\\s+/g,' ').trim();" +
            "if(k.indexOf('chapter')>=0||k.indexOf('playerbarbutton')>=0||(tx.length>1&&tx.length<=80&&list.indexOf(tx)>=0))return 'yes';}return 'no';})(" +
            "$xPx,$yPx,$list)"
    }

    /** "storyboard|<videoId>|<seconds>|<spec>" — YouTube's timeline preview pattern (spec may be empty). */
    const val STORYBOARD = "storyboard|"

    /** Seconds from YouTube's t= parameter ("135", "135s", "2m15s", "1h2m3s"), or null. */
    fun seconds(t: String?): Int? {
        val s = t?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        s.toIntOrNull()?.let { return it.takeIf { v -> v >= 0 } }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?${'$'}").find(s) ?: return null
        if (m.value.isEmpty()) return null
        val (h, mi, se) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
    }

    /** Jumps the page's player to [seconds] and plays (same calls as the tap handler). */
    fun seekScript(seconds: Int) = "(function(t){var mp=document.getElementById('movie_player');var v=document.querySelector('.html5-video-player video')||document.querySelector('video');" +
        "try{if(mp&&mp.seekTo){mp.seekTo(t,true);}else if(v){v.currentTime=t;}}catch(x){}try{if(mp&&mp.playVideo)mp.playVideo();}catch(x){}" +
        "if(v&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}return 'ok';})(" + seconds.coerceAtLeast(0) + ")"
}

/** One chapter: start in seconds and its title. */
data class Chapter(val start: Int, val title: String, val picture: String? = null)

/** Pure chapter rules (unit-tested). */
object ChapterRules {
    /** The first chapter that starts more than 1 s after [position], or null in the last chapter. */
    fun next(chapters: List<Chapter>, position: Double): Chapter? = chapters.sortedBy { it.start }.firstOrNull { it.start > position + 1 }
}

object Chapters {
    /** Parses the "chapters|…" message body; anything malformed gives an empty list. At most 150. */
    fun parse(json: String): List<Chapter> = runCatching {
        val a = org.json.JSONArray(json)
        (0 until minOf(a.length(), 150)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val t = o.optInt("t", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val pic = o.optString("i").takeIf { it.startsWith("https://i.ytimg.com/") && it.length <= 1024 }
            Chapter(t, o.optString("l").take(80), pic)
        }.distinctBy { it.start }.sortedBy { it.start }
    }.getOrDefault(emptyList())

    /** Index of the chapter playing at [position] seconds, or -1. */
    fun current(list: List<Chapter>, position: Double): Int = list.indexOfLast { it.start <= position + 0.5 }
}

/**
 * Live streams: tells the app whether the video on the page is live ("live|1"/"live|0"), and on phones shows
 * YouTube's own live chat (youtube.com/live_chat) in a rounded card right below the description — it scrolls with
 * the page and moves when the description is expanded. The header folds it away and back. On wide screens the app
 * shows the chat in its own column instead (window.__ssChatInline = false).
 */
object YouTubeLiveChat {
    const val MESSAGE = "live|"

    fun chatUrl(videoId: String, popout: Boolean) = "https://www.youtube.com/live_chat?v=$videoId" + if (popout) "&is_popout=1" else "&embed_domain=m.youtube.com"

    val INSTALL: String = """
(function(){
if(window.top&&window.top!==window)return 'frame';if(window.__streamshellLive)return 'ok';window.__streamshellLive=true;
var lastV=null,live=null,box=null,open=true;
function vid(){try{var u=new URL(location.href);return u.pathname==='/watch'?u.searchParams.get('v'):null;}catch(e){return null;}}
function isLive(){var mp=document.getElementById('movie_player');try{if(mp&&mp.getVideoData){var d=mp.getVideoData();if(d&&d.isLive)return true;}}catch(e){}
var v=document.querySelector('.html5-video-player video');if(v&&v.duration===Infinity)return true;
return !!document.querySelector('.html5-video-player .ytp-live');}
function post(m){if(window.streamShellSponsor)window.streamShellSponsor.postMessage('$MESSAGE'+m);}
function hasItems(el){return !!(el.querySelector&&el.querySelector('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-media-item'));}
// Where the card goes: after the highest block that holds the description but none of the recommended videos.
function anchor(){var d=document.querySelector('ytm-watch [class*="description"],ytm-watch [class*="metadata"],[class*="slim-video-metadata"]');if(!d)return null;
var el=d;while(el.parentElement&&el.parentElement!==document.body&&!hasItems(el.parentElement))el=el.parentElement;return el;}
function removeBox(){if(box&&box.parentElement)box.parentElement.removeChild(box);box=null;}
function ensureBox(v){if(box&&box.isConnected&&box.getAttribute('data-v')===v)return;removeBox();var a=anchor();if(!a||!a.parentElement)return;
box=document.createElement('div');box.setAttribute('data-v',v);box.id='streamshell-live-chat';
box.style.cssText='margin:12px;border-radius:16px;overflow:hidden;border:1px solid '+(document.documentElement&&document.documentElement.hasAttribute&&document.documentElement.hasAttribute('dark')?'rgba(255,255,255,.14)':'rgba(0,0,0,.10)')+';background:inherit';
var head=document.createElement('div');head.style.cssText='display:flex;align-items:center;justify-content:space-between;padding:12px 16px;font:600 15px Roboto,Arial,sans-serif;cursor:pointer';
var t=document.createElement('span');t.textContent='Live chat';var arrow=document.createElement('span');arrow.textContent=open?'\u25B4':'\u25BE';
head.appendChild(t);head.appendChild(arrow);
var f=document.createElement('iframe');f.src='https://www.youtube.com/live_chat?v='+encodeURIComponent(v)+'&embed_domain='+location.hostname;
f.setAttribute('title','Live chat');f.style.cssText='width:100%;height:420px;border:0;display:'+(open?'block':'none');
head.addEventListener('click',function(){open=!open;f.style.display=open?'block':'none';arrow.textContent=open?'\u25B4':'\u25BE';});
box.appendChild(head);box.appendChild(f);a.parentElement.insertBefore(box,a.nextSibling);}
function tick(){var v=vid();if(v!==lastV){lastV=v;live=null;removeBox();}if(!v)return;
var l=isLive();if(l!==live){live=l;post(l?'1':'0');}
if(l&&window.__ssChatInline!==false)ensureBox(v);else removeBox();}
setInterval(tick,1500);tick();
// Sound on: a video you open plays with sound right away. YouTube carries the muted state of a feed preview into the
// video page (the "tap to unmute" button). Once per video, when the real video (not an ad) starts, the sound is turned
// on — through YouTube's own player, so its controls agree; muting it yourself afterwards is respected.
var soundFor=null;
function adOn(){var mp=document.getElementById('movie_player');return !!(mp&&mp.classList&&(mp.classList.contains('ad-showing')||mp.classList.contains('ad-interrupting')));}
// Also checked with the regular tick: the video may already be playing when an ad ends (no new "playing" event).
setInterval(function(){var v=vid();if(!v||soundFor===v||adOn())return;var m=document.querySelector('#movie_player video,.html5-video-player video');
if(m&&!m.paused){soundFor=v;giveSound(m);}},1500);
function giveSound(m){var mp=document.getElementById('movie_player');try{if(mp&&mp.isMuted&&mp.isMuted()&&mp.unMute)mp.unMute();}catch(x){}try{if(m&&m.muted)m.muted=false;}catch(x){}}
document.addEventListener('playing',function(e){var m=e.target;if(!m||m.tagName!=='VIDEO')return;var v=vid();if(!v||soundFor===v)return;
if(m.closest&&!m.closest('#movie_player,.html5-video-player'))return;
if(adOn())return;
soundFor=v;giveSound(m);setTimeout(function(){if(soundFor===v&&vid()===v)giveSound(m);},700);},true);
return 'ok';})()
""".trim()
}
