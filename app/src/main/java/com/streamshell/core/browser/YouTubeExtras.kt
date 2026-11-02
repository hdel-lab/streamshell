package com.streamshell.core.browser

/**
 * Feed filter: parts of YouTube's mobile feeds the user can hide with a style (best effort: YouTube's element names,
 * checked against m.youtube.com in 2026; when YouTube renames them, the part simply shows again).
 */
object FeedFilter {
    val PARTS = listOf(
        "shorts" to "Shorts in feeds and search",
        "posts" to "Community posts",
        "playables" to "Playables (games)"
    )

    private val selectors = mapOf(
        "shorts" to listOf(
            "ytm-reel-shelf-renderer",
            "ytm-shorts-lockup-view-model",
            "ytm-shorts-lockup-view-model-v2",
            "ytm-rich-section-renderer:has(ytm-reel-shelf-renderer)",
            "ytm-rich-section-renderer:has(ytm-shorts-lockup-view-model)",
            "ytm-rich-item-renderer:has(a[href^=\"/shorts/\"])",
            "ytm-video-with-context-renderer:has(a[href^=\"/shorts/\"])",
            "ytm-item-section-renderer ytm-reel-shelf-renderer"
        ),
        "posts" to listOf(
            "ytm-backstage-post-thread-renderer",
            "ytm-post-renderer",
            "ytm-rich-section-renderer:has(ytm-backstage-post-thread-renderer)",
            "ytm-rich-section-renderer:has(ytm-post-renderer)"
        ),
        "playables" to listOf(
            "ytm-rich-section-renderer:has(a[href*=\"/playables\"])",
            "ytm-mini-game-card-view-model",
            "ytm-rich-section-renderer:has(ytm-mini-game-card-view-model)"
        )
    )

    /** The style for [parts]; empty when nothing is hidden. Never on the Shorts player itself (/shorts/… pages use other elements). */
    fun css(parts: Set<String>): String {
        val sel = PARTS.map { it.first }.filter { it in parts }.flatMap { selectors[it].orEmpty() }
        return if (sel.isEmpty()) "" else sel.joinToString(",") + "{display:none!important}"
    }
}

/** Colours of SponsorBlock's categories on the timeline (the colours its own extension uses). */
object SponsorColors {
    val MAP = mapOf(
        "sponsor" to "#00d400",
        "selfpromo" to "#ffff00",
        "interaction" to "#cc00ff",
        "intro" to "#00ffff",
        "outro" to "#0202ed",
        "preview" to "#008fd6",
        "filler" to "#7300ff",
        "music_offtopic" to "#ff9900"
    )
}

/**
 * Extra features on YouTube pages, switched by the app through `window.__ssExtras` ({feedCss, dislikes, deArrow, markers}):
 *  - feed filter style (see [FeedFilter]),
 *  - Return YouTube Dislike: the page asks the app for the count of the open video ("rytd|<id>"); the app answers with
 *    window.__ssDislikes(id, count) and the count is shown next to YouTube's dislike button,
 *  - DeArrow: the ids of the videos on screen are sent to the app ("dearrow|id,id,…"); the app answers with
 *    window.__ssDeArrow({id: title}) and those titles replace the shown ones (the original stays as tooltip),
 *  - SponsorBlock marks: the segments the app installed (window.__streamshellSB) are drawn on YouTube's timeline.
 * Only YouTube's own top page; everything is read from / written to the page, nothing else is sent anywhere.
 */
object YouTubeExtras {
    const val DISLIKES = "rytd|"
    const val DEARROW = "dearrow|"

    /** "diag|<line>": one diagnostics line (only while Diagnostics is on). */
    const val DIAG = "diag|"

    /** Most watched video IDs sent to the page for "Hide watched videos". */
    const val MAX_WATCHED = 800

    fun config(
        feedCss: String,
        dislikes: Boolean,
        deArrow: Boolean,
        markers: Boolean,
        diag: Boolean = false,
        trace: Boolean = false,
        watched: Collection<String> = emptyList(),
        channels: Collection<String> = emptyList(),
        words: Collection<String> = emptyList()
    ): String {
        val ids = watched.filter { Regex("[A-Za-z0-9_-]{11}").matches(it) }.distinct().take(MAX_WATCHED)
        val o = org.json.JSONObject().put("feedCss", feedCss).put("dislikes", dislikes).put("deArrow", deArrow).put("markers", markers).put("diag", diag || trace).put("trace", trace)
            .put("watched", org.json.JSONArray(ids))
            .put("chans", org.json.JSONArray(channels.map { it.trim().lowercase() }.filter { it.isNotEmpty() }))
            .put("words", org.json.JSONArray(words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }))
        return "window.__ssExtras=$o;if(window.__ssExtrasApply)window.__ssExtrasApply();"
    }

    /** The app's answer: [count] dislikes for [videoId] (null = unknown, nothing is shown). */
    fun dislikes(videoId: String, count: Long?): String = "window.__ssDislikes&&window.__ssDislikes(${org.json.JSONObject.quote(videoId)},${count ?: -1});"

    /** The app's answer: DeArrow titles by video id. */
    fun deArrowTitles(titles: Map<String, String>): String {
        val o = org.json.JSONObject()
        titles.forEach { (k, v) -> o.put(k, v) }
        return "window.__ssDeArrow&&window.__ssDeArrow($o);"
    }

    private val colors = org.json.JSONObject(SponsorColors.MAP).toString()

    val INSTALL: String = """
(function(){
if(window.top&&window.top!==window)return 'frame';
if(window.__streamshellExtras)return 'ok';window.__streamshellExtras=true;
function cfg(){return window.__ssExtras||{};}
function post(m){if(window.streamShellSponsor)window.streamShellSponsor.postMessage(m);}
function vid(){try{var u=new URL(location.href);if(u.pathname!=='/watch')return null;var v=u.searchParams.get('v');return /^[A-Za-z0-9_-]{11}${'$'}/.test(v||'')?v:null;}catch(e){return null;}}
// ---- feed filter
function feed(){var css=cfg().feedCss||'';var s=document.getElementById('ss-feed-filter');
if(!css){if(s)s.textContent='';return;}if(!s){s=document.createElement('style');s.id='ss-feed-filter';(document.head||document.documentElement).appendChild(s);}if(s.textContent!==css)s.textContent=css;}
// ---- Hidden tiles (0.5.0): watched videos, blocked channels, blocked words. Only tiles are hidden (never the watch page).
var hidSet=null,hidKey='';
function tileOf(a){return a.closest('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-media-item,ytm-rich-item-renderer,ytm-video-card-renderer,ytm-playlist-video-renderer,yt-lockup-view-model,ytm-reel-item-renderer');}
function byline(t){var b=t.querySelector('ytm-badge-and-byline-renderer,[class*="byline"],.media-item-metadata,[class*="metadata"]');var x=((b&&b.textContent)||'').trim().toLowerCase();return x.split(/\s[·•]\s|\n/)[0].trim();}
function headline(t){var h=t.querySelector('h3,h4,.media-item-headline,[class*="headline"],[class*="video-title"]');return ((h&&h.textContent)||'').toLowerCase();}
function hideTiles(){var c=cfg();var w=c.watched||[],ch=c.chans||[],wd=c.words||[];var key=w.length+'|'+ch.join(',')+'|'+wd.join(',');
if(key!==hidKey){hidKey=key;hidSet={};for(var i=0;i<w.length;i++)hidSet[w[i]]=1;var old=document.querySelectorAll('[data-ss-hidden]');for(var j=0;j<old.length;j++){old[j].style.removeProperty('display');delete old[j].dataset.ssHidden;}}
if(!w.length&&!ch.length&&!wd.length)return;var as=document.querySelectorAll('a[href*="/watch?v="],a[href^="/shorts/"]');
for(var k=0;k<as.length&&k<600;k++){var t=tileOf(as[k]);if(!t||t.dataset.ssHidden)continue;var hide=false;var id=idOf(as[k]);
if(id&&hidSet[id])hide=true;
if(!hide&&ch.length){var by=byline(t);for(var m=0;m<ch.length&&!hide;m++)if(by&&by.indexOf(ch[m])===0)hide=true;}
if(!hide&&wd.length){var hl=headline(t);for(var n=0;n<wd.length&&!hide;n++)if(hl.indexOf(wd[n])>=0)hide=true;}
if(hide){t.dataset.ssHidden='1';t.style.setProperty('display','none','important');}}}
// ---- Return YouTube Dislike
var dis={},asked={};
function fmt(n){try{return new Intl.NumberFormat(undefined,{notation:'compact',maximumFractionDigits:1}).format(n);}catch(e){return String(n);}}
window.__ssDislikes=function(id,n){dis[id]=n;show();};
function disButton(){var b=document.querySelector('ytm-watch dislike-button-view-model button,dislike-button-view-model button');if(b)return b;
var seg=document.querySelector('ytm-watch ytm-segmented-like-dislike-button-renderer,ytm-segmented-like-dislike-button-renderer,segmented-like-dislike-button-view-model');
if(seg){var bs=seg.querySelectorAll('button');if(bs.length>=2)return bs[bs.length-1];}return null;}
function show(){var id=vid();var old=document.querySelectorAll('.ss-dislikes');
if(!cfg().dislikes||!id||!(id in dis)||dis[id]<0){for(var i=0;i<old.length;i++)old[i].remove();return;}
var b=disButton();if(!b)return;var s=b.querySelector('.ss-dislikes');var txt=fmt(dis[id]);
for(var j=0;j<old.length;j++)if(old[j]!==s)old[j].remove();
if(!s){s=document.createElement('span');s.className='ss-dislikes';s.style.cssText='margin-left:6px;font:500 14px Roboto,Arial,sans-serif;white-space:nowrap;pointer-events:none';b.appendChild(s);
b.style.setProperty('width','auto','important');b.style.setProperty('padding','0 12px','important');}
if(s.textContent!==txt)s.textContent=txt;}
function dislikes(){if(!cfg().dislikes)return show();var id=vid();if(id&&!asked[id]){asked[id]=1;post('$DISLIKES'+id);}show();}
// ---- DeArrow
var da={},sent={},queue=[];
function itemOf(a){return a.closest('ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-media-item,ytm-rich-item-renderer,ytm-video-card-renderer,ytm-playlist-video-renderer,yt-lockup-view-model,ytm-reel-item-renderer')||a;}
function idOf(a){try{var u=new URL(a.getAttribute('href'),location.href);if(u.pathname!=='/watch')return null;var v=u.searchParams.get('v');return /^[A-Za-z0-9_-]{11}${'$'}/.test(v||'')?v:null;}catch(e){return null;}}
function titleEl(item){var h=item.querySelector('h3,h4,.media-item-headline,[class*="headline"],[class*="video-title"],[class*="title"]');if(!h)return null;
var all=h.querySelectorAll('span,a,div');for(var i=all.length-1;i>=0;i--){var e=all[i];if(e.children.length===0&&(e.textContent||'').trim().length>2)return e;}return h.children.length===0?h:null;}
// The first selector that matches, in this order (one combined selector would take whatever comes first in the page:
// the heading around the text instead of the text itself).
function first(list){for(var i=0;i<list.length;i++){var e=document.querySelector(list[i]);if(e)return e;}return null;}
function rename(el,t){if(!el||!t)return;if(el.dataset.ssOrig===undefined){el.dataset.ssOrig=el.textContent;el.title=el.textContent;}if(el.textContent!==t)el.textContent=t;}
function restore(){var r=document.querySelectorAll('[data-ss-orig]');for(var i=0;i<r.length;i++){r[i].textContent=r[i].dataset.ssOrig;delete r[i].dataset.ssOrig;r[i].removeAttribute('title');}}
window.__ssDeArrow=function(m){for(var k in m)if(Object.prototype.hasOwnProperty.call(m,k))da[k]=m[k];titles();};
function titles(){if(!cfg().deArrow)return;var as=document.querySelectorAll('a[href*="/watch?v="]');
for(var i=0;i<as.length&&i<300;i++){var id=idOf(as[i]);if(!id)continue;if(!sent[id]){sent[id]=1;queue.push(id);}var t=da[id];if(t)rename(titleEl(itemOf(as[i])),t);}
var w=vid();if(w){if(!sent[w]){sent[w]=1;queue.push(w);}if(da[w])rename(first(['ytm-watch .slim-video-information-title span','ytm-watch .slim-video-metadata-title span','ytm-watch ytm-slim-video-information-renderer h2 span','ytm-watch ytm-slim-video-information-renderer h2']),da[w]);}
if(queue.length){post('$DEARROW'+queue.splice(0,40).join(','));}}
// ---- SponsorBlock marks on the timeline
var COLORS=$colors;
function bar(){var p=document.querySelector('.html5-video-player')||document.getElementById('movie_player');if(!p)return null;var pw=p.getBoundingClientRect().width||0;
var c=p.querySelectorAll('.ytm-progress-bar,ytm-progress-bar,[class*="ProgressBarLine"],[class*="progress-bar-line"],.ytp-progress-bar,[class*="progress-bar"],[role="slider"]');var best=null,bw=0;
for(var i=0;i<c.length;i++){var r=c[i].getBoundingClientRect();if(r.height>0&&r.height<=24&&r.width>pw*0.5&&r.width>bw){best=c[i];bw=r.width;}}return best;}
function marks(){var box=document.getElementById('ss-sb-marks');var id=vid();var S=window.__streamshellSB;var segs=id&&S&&S.map&&S.map[id];
var v=document.querySelector('.html5-video-player video');var d=v&&v.duration;
if(!cfg().markers||!segs||!segs.length||!(d>0)||!isFinite(d)){if(box)box.remove();return;}
var b=bar();if(!b){return;}var key=id+'|'+Math.round(d);
if(box&&box.parentElement===b&&box.dataset.k===key)return;if(box)box.remove();
box=document.createElement('div');box.id='ss-sb-marks';box.dataset.k=key;box.style.cssText='position:absolute;left:0;top:0;right:0;bottom:0;pointer-events:none;z-index:40';
if(getComputedStyle(b).position==='static')b.style.position='relative';
for(var i=0;i<segs.length;i++){var s=segs[i];var m=document.createElement('div');var l=Math.max(0,s[0]/d*100),w=Math.max(0.4,(Math.min(d,s[1])-s[0])/d*100);
m.style.cssText='position:absolute;top:0;bottom:0;left:'+l+'%;width:'+w+'%;background:'+(COLORS[s[2]]||'#00d400')+';opacity:.85';box.appendChild(m);}
b.appendChild(box);}
// ---- Diagnostics (Settings › YouTube › Diagnostics): what the player does around ads and jumps, as short lines for the
// app's log. Only while switched on; read-only (listens to the video's events, reads YouTube's player data); at most 15
// lines per second. The other scripts call window.__ssDiagLog for their own actions.
var diagOn=false,dq=0,dqAt=0,diagV=null,lastT=0;
function dlog(m){if(!cfg().diag)return;var now=Date.now();if(now-dqAt>1000){dqAt=now;dq=0;}if(++dq>(cfg().trace?40:15))return;post('$DIAG'+String(m).slice(0,300));}
function vstate(v){var p=document.querySelector('.html5-video-player');return 't='+(v.currentTime||0).toFixed(1)+' d='+(isFinite(v.duration)?Math.round(v.duration):v.duration)+(p&&p.classList.contains('ad-showing')?' AD':'')+(v.playbackRate!==1?' x'+v.playbackRate:'')+(v.muted?' muted':'');}
function summary(){var id=vid();if(!id||id===diagV)return;diagV=id;try{var mp=document.getElementById('movie_player');var r=mp&&mp.getPlayerResponse&&mp.getPlayerResponse();
if(!r){dlog('video '+id+': no player data yet');diagV=null;return;}var ap=(r.adPlacements||[]).map(function(a){var c=a&&a.adPlacementRenderer&&a.adPlacementRenderer.config&&a.adPlacementRenderer.config.adPlacementConfig;return (c&&c.kind)||'?';});
var s=JSON.stringify(r);var kw={};(s.match(/ssa[ip]|serverStitch\w*|stitched\w*|adBreakHeartbeat\w*|sabr\w*/ig)||[]).forEach(function(k){kw[k.toLowerCase()]=1;});
dlog('video '+id+': adPlacements '+ap.length+(ap.length?' ['+ap.slice(0,6).join(',')+']':'')+', adSlots '+((r.adSlots||[]).length)+', length '+((r.videoDetails&&r.videoDetails.lengthSeconds)||'?')+' s'+(Object.keys(kw).length?', keys: '+Object.keys(kw).join(','):''));}catch(e){dlog('video '+id+': '+e);}}
function diag(){if(!cfg().diag){window.__ssDiagLog=undefined;return;}window.__ssDiagLog=dlog;summary();if(diagOn)return;diagOn=true;dlog('diagnostics on ('+navigator.userAgent.replace(/^.*?(Chrome\/[0-9.]+).*$/,'$1')+')');
['seeking','seeked','ratechange','pause','playing','waiting','emptied','durationchange','ended'].forEach(function(t){document.addEventListener(t,function(e){var v=e.target;if(!v||v.tagName!=='VIDEO'||!cfg().diag)return;
dlog(t+(t==='seeking'?' from '+lastT.toFixed(1):'')+' '+vstate(v));},true);});
document.addEventListener('timeupdate',function(e){var v=e.target;if(v&&v.tagName==='VIDEO'&&!v.seeking)lastT=v.currentTime||0;},true);
var pl=null;setInterval(function(){if(!cfg().diag)return;var p=document.querySelector('.html5-video-player');if(!p)return;var ad=p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting');if(pl!==ad){pl=ad;var v=p.querySelector('video');dlog('player '+(ad?'AD':'video')+(v?' '+vstate(v):''));}},250);}
// ---- Trace everything (Settings › Diagnostics): taps and drags on the page (kind of element, never its text or address),
// scroll sessions, buffering times, dropped frames and quality every 5 s, page and script errors. Only while switched on.
var trOn=false,wAt=0,scr=null,lastQ=null,errAt={};
function tlog(m){if(cfg().trace)dlog(m);}
function clean(s){return String(s||'?').replace(/https?:\/\/\S+/g,'<address>').slice(0,160);}
function terr(n,e){if(!cfg().trace)return;var now=Date.now();if(errAt[n]&&now-errAt[n]<10000)return;errAt[n]=now;tlog('script error in '+n+': '+clean(e&&e.message||e));}
function kind(el){if(!el||!el.closest)return 'page';var a=el.closest('a,button,[role=button],input,textarea,select,[role=tab],[role=menuitem]');
var t=a||el;var tag=t.tagName?t.tagName.toLowerCase():'?';var custom='';var p=el;while(p&&p!==document.body&&p.tagName){var n=p.tagName.toLowerCase();if(n.indexOf('-')>0){custom=n;break;}p=p.parentElement;}
var k='element';if(a&&a.tagName==='A'){var h=a.getAttribute('href')||'';k=/\/watch/.test(h)?'video link':/\/shorts\//.test(h)?'shorts link':/\/(@|channel\/|c\/|user\/)/.test(h)?'channel link':/\/playlist/.test(h)?'playlist link':/\/feed\//.test(h)?'feed link':'link';}
else if(a)k=(tag==='input'||tag==='textarea')?'text field':'button';
if(el.closest('#movie_player'))k='player '+k;return k+' <'+tag+'>'+(custom&&custom!==tag?' in '+custom:'');}
function ahead(v){try{var b=v.buffered,t=v.currentTime;for(var i=0;i<b.length;i++){if(b.start(i)<=t&&t<=b.end(i))return (b.end(i)-t).toFixed(1);}}catch(e){}return '0';}
function trace(){if(!cfg().trace||trOn)return;trOn=true;tlog('trace everything on');var down=null;
document.addEventListener('pointerdown',function(e){if(cfg().trace)down={x:e.clientX,y:e.clientY,t:Date.now(),n:e.isPrimary===false?2:1};},true);
document.addEventListener('pointerup',function(e){if(!cfg().trace||!down)return;var dx=e.clientX-down.x,dy=e.clientY-down.y,dt=Date.now()-down.t,d=Math.sqrt(dx*dx+dy*dy);
tlog((d<10?(dt>=500?'long press':'touch'):'drag '+Math.round(dx)+','+Math.round(dy))+' '+dt+' ms on '+kind(e.target)+' at '+Math.round(e.clientX)+','+Math.round(e.clientY));down=null;},true);
window.addEventListener('click',function(e){if(cfg().trace)tlog('click on '+kind(e.target));},true);
window.addEventListener('scroll',function(){if(!cfg().trace)return;var n=Date.now();if(!scr)scr={y:window.scrollY||0,t:n,h:0};scr.e=n;clearTimeout(scr.h);
scr.h=setTimeout(function(){if(scr)tlog('scroll '+Math.round((window.scrollY||0)-scr.y)+' px in '+(scr.e-scr.t)+' ms');scr=null;},300);},{passive:true,capture:true});
document.addEventListener('waiting',function(e){if(e.target&&e.target.tagName==='VIDEO')wAt=Date.now();},true);
document.addEventListener('playing',function(e){if(e.target&&e.target.tagName==='VIDEO'&&wAt){tlog('buffering '+(Date.now()-wAt)+' ms');wAt=0;}},true);
document.addEventListener('stalled',function(e){if(e.target&&e.target.tagName==='VIDEO')tlog('stalled (network) '+vstate(e.target));},true);
document.addEventListener('fullscreenchange',function(){tlog('page fullscreen '+(document.fullscreenElement?'on':'off'));});
window.addEventListener('error',function(e){if(cfg().trace&&e&&e.message)tlog('page error: '+clean(e.message));},true);
window.addEventListener('unhandledrejection',function(e){if(cfg().trace)tlog('page promise error: '+clean(e&&e.reason&&e.reason.message||(e&&e.reason)));});
setInterval(function(){if(!cfg().trace)return;var v=document.querySelector('#movie_player video')||document.querySelector('video');if(!v||v.paused||!v.getVideoPlaybackQuality)return;
var q=v.getVideoPlaybackQuality(),mp=document.getElementById('movie_player'),ql='';try{ql=mp&&mp.getPlaybackQuality?mp.getPlaybackQuality():'';}catch(x){}
var dd=q.droppedVideoFrames,tt=q.totalVideoFrames;if(lastQ&&tt>=lastQ.t){dd-=lastQ.d;tt-=lastQ.t;}lastQ={d:q.droppedVideoFrames,t:q.totalVideoFrames};
tlog('frames: dropped '+dd+' of '+tt+' in 5 s (total '+q.droppedVideoFrames+'/'+q.totalVideoFrames+')'+(ql?', quality '+ql:'')+', size '+v.videoWidth+'x'+v.videoHeight+', buffered ahead '+ahead(v)+' s');},5000);}
// ----
var lastDa=false;
function apply(){try{diag();}catch(e){terr('diagnostics',e);}try{trace();}catch(e){terr('trace',e);}try{feed();}catch(e){terr('feed filter',e);}try{hideTiles();}catch(e){terr('hide tiles',e);}try{dislikes();}catch(e){terr('dislikes',e);}try{if(cfg().deArrow)titles();else if(lastDa)restore();lastDa=!!cfg().deArrow;}catch(e){terr('DeArrow',e);}try{marks();}catch(e){terr('SponsorBlock marks',e);}}
window.__ssExtrasApply=apply;
var pend=0;function soon(){if(!pend)pend=setTimeout(function(){pend=0;apply();},500);}
if(window.MutationObserver)new MutationObserver(soon).observe(document.documentElement,{childList:true,subtree:true});
setInterval(apply,1500);document.addEventListener('DOMContentLoaded',apply);apply();
return 'ok';})()
""".trim()
}
