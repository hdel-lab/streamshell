package com.streamshell.core.media

/**
 * Small page scripts for the player features. They only touch the page's own <video>/<audio> elements
 * (standard HTML5 / Web Audio) or YouTube's own player object; they read nothing else from the page.
 */
object PlayerScripts {
    /**
     * "position|duration|videoId" of the main video, or "" when there is none (or an ad plays). On YouTube the video is
     * the one in YouTube's player and videoId is the id it is really playing — right after a switch the address shows
     * the new video while the player still plays the old one; positions must not be mixed up then.
     */
    const val POSITION = "(function(){var mp=document.getElementById('movie_player');var pv=mp?mp.querySelector('video'):null;" +
        "var m=document.querySelectorAll('video');var v=pv;if(!v){for(var i=0;i<m.length;i++){if(!m[i].paused){v=m[i];break;}}if(!v&&m.length)v=m[0];}" +
        "if(!v||!isFinite(v.duration))return '';" +
        "var p=document.querySelector('.html5-video-player');if(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')))return '';" +
        "var id='';try{var d=mp&&mp.getVideoData&&mp.getVideoData();id=(d&&d.video_id)||'';}catch(e){}" +
        "return v.currentTime+'|'+v.duration+'|'+id;})()"

    /** (position, duration, videoId or "") from [POSITION]. */
    fun parsePositionAndId(raw: String?): Triple<Double, Double, String>? {
        val s = raw?.trim()?.trim('"') ?: return null
        val p = s.split('|')
        if (p.size !in 2..3) return null
        val t = p[0].toDoubleOrNull() ?: return null
        val d = p[1].toDoubleOrNull() ?: return null
        val id = p.getOrNull(2).orEmpty().takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }.orEmpty()
        return if (t.isFinite() && d.isFinite() && d > 0) Triple(t, d, id) else null
    }

    /** True when a position read from the page belongs to the video in [url] (YouTube: same video id). */
    fun belongsTo(playingId: String, url: String?): Boolean {
        val want = com.streamshell.core.sponsor.SponsorBlockApi.videoIdOf(url) ?: return true
        return playingId == want
    }

    fun parsePosition(raw: String?): Pair<Double, Double>? = parsePositionAndId(raw)?.let { it.first to it.second }

    /** Jump to [seconds] in the main video (resume). */
    fun seekTo(seconds: Double) = "(function(t){var mp=document.getElementById('movie_player');var v=mp?mp.querySelector('video'):null;var m=document.querySelectorAll('video');" +
        "if(!v){for(var i=0;i<m.length;i++){if(!m[i].paused){v=m[i];break;}}if(!v&&m.length)v=m[0];}" +
        "if(v&&isFinite(v.duration)&&t<v.duration){try{if(mp&&mp.seekTo){mp.seekTo(t,true);}else{v.currentTime=t;}}catch(e){v.currentTime=t;}}return 'ok';})(" + seconds.coerceAtLeast(0.0) + ")"

    /**
     * Playback speed for every video of the page, kept when the site starts the next video. Ads are left alone
     * (the ad skipper speeds them up itself). Rate in percent, 50…200.
     */
    fun speed(percent: Int): String {
        val r = (percent.coerceIn(50, 200) / 100.0)
        return "(function(r){window.__ssRate=r;function ad(){var p=document.querySelector('.html5-video-player');return !!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));}" +
            "function apply(v){if(!ad()&&v&&v.playbackRate!==window.__ssRate&&v.playbackRate<=2){window.__ssSetting=true;v.playbackRate=window.__ssRate;window.__ssSetting=false;}}" +
            "if(!window.__ssRateHook){window.__ssRateHook=true;document.addEventListener('playing',function(e){if(e.target&&e.target.tagName==='VIDEO')apply(e.target);},true);" +
            // A speed chosen in the site's own menu (YouTube's ⋮ › Playback speed) wins for this page: it is not set back
            // the next time the video plays. Ads (16x) and the app's own changes are not taken as a choice.
            // Only right after a tap (a menu choice needs one): YouTube resetting the speed itself on a new video is no choice.
            "var tapAt=0;['touchend','click'].forEach(function(t){document.addEventListener(t,function(){tapAt=Date.now();},true);});" +
            "document.addEventListener('ratechange',function(e){var v=e.target;if(window.__ssSetting||!v||v.tagName!=='VIDEO'||ad()||Date.now()-tapAt>4000)return;var r=v.playbackRate;if(r>0&&r<=2)window.__ssRate=r;},true);}" +
            "var m=document.querySelectorAll('video');for(var i=0;i<m.length;i++)apply(m[i]);return 'ok';})(" + r + ")"
    }

    /**
     * Volume boost and loudness evening via Web Audio (gain + compressor) on YouTube's own video element.
     * Only same-origin/blob media (cross-origin media without CORS would go silent through Web Audio).
     * Once attached the chain stays; "off" sets it to neutral (gain 1, no compression).
     */
    fun audio(boostPercent: Int, normalize: Boolean): String {
        val g = boostPercent.coerceIn(100, 300) / 100.0
        return "(function(g,n){var v=document.querySelector('.html5-video-player video')||document.querySelector('video');if(!v)return 'none';" +
            "var s=window.__ssAudio;if(!s){if(g===1&&!n)return 'off';var src=v.currentSrc||v.src||'';" +
            "if(src.indexOf('blob:')!==0){try{if(new URL(src,location.href).origin!==location.origin)return 'cors';}catch(e){return 'cors';}}" +
            "var C=window.AudioContext||window.webkitAudioContext;if(!C)return 'na';var ctx=new C();var node=ctx.createMediaElementSource(v);" +
            "var comp=ctx.createDynamicsCompressor();var gain=ctx.createGain();node.connect(comp);comp.connect(gain);gain.connect(ctx.destination);" +
            "s=window.__ssAudio={ctx:ctx,comp:comp,gain:gain,v:v};}" +
            "if(s.v!==v)return 'other';s.gain.gain.value=g;" +
            "if(n){s.comp.threshold.value=-28;s.comp.knee.value=18;s.comp.ratio.value=5;s.comp.attack.value=0.005;s.comp.release.value=0.3;}" +
            "else{s.comp.threshold.value=0;s.comp.knee.value=0;s.comp.ratio.value=1;}" +
            "if(s.ctx.state==='suspended'&&s.ctx.resume)s.ctx.resume();return 'ok';})(" + g + "," + normalize + ")"
    }

    /** Ask YouTube's player for its lowest quality in the background ("tiny"), back to automatic in front. */
    fun youtubeQuality(lowest: Boolean): String = quality(if (lowest) "tiny" else "auto")

    /** Ask YouTube's player for quality [q] (its names: auto, hd1080, hd720, large …). YouTube may ignore it. */
    fun quality(q0: String): String {
        val q = q0.takeIf { Regex("[a-z0-9]{3,8}").matches(it) } ?: "auto"
        return "(function(q){var mp=document.getElementById('movie_player');try{if(mp&&mp.setPlaybackQualityRange){mp.setPlaybackQualityRange(q,q);}" +
            "if(mp&&mp.setPlaybackQuality){mp.setPlaybackQuality(q);}}catch(e){}return 'ok';})('" + q + "')"
    }

    /** Fullscreen zoom: true = the video fills the screen (edges cropped), false = whole picture. */
    fun zoom(fill: Boolean): String = "(function(f){var v=document.querySelector('.html5-video-player video')||document.querySelector('video');if(!v)return 'none';" +
        "if(f){v.style.setProperty('object-fit','cover','important');}else{v.style.removeProperty('object-fit');}return 'ok';})(" + fill + ")"

    /** The channel of the video in YouTube's player ("" when unknown). */
    const val CHANNEL = "(function(){try{var mp=document.getElementById('movie_player');var d=mp&&mp.getVideoData&&mp.getVideoData();return (d&&d.author)||'';}catch(e){return '';}})()"

    /**
     * Repeat: the whole video ([loop]) and/or the part [a]…[b] seconds (both null = no part). The part check runs in
     * the page on the video's own time updates (no app timer); a jump you make out of the part ends nothing — the next
     * time the end of the part is reached, it goes back to its start.
     */
    fun repeat(loop: Boolean, a: Double?, b: Double?): String {
        val ab = if (a != null && b != null && b > a + 0.5) "[$a,$b]" else "null"
        return "(function(loop,ab){var v=document.querySelector('.html5-video-player video')||document.querySelector('video');if(!v)return 'none';" +
            "v.loop=!!loop;window.__ssAB=ab;if(!window.__ssABHook){window.__ssABHook=true;document.addEventListener('timeupdate',function(e){var x=e.target;var r=window.__ssAB;" +
            "if(!r||!x||x.tagName!=='VIDEO')return;if(x.currentTime>=r[1]||x.currentTime<r[0]-1){var mp=document.getElementById('movie_player');" +
            "try{if(mp&&mp.seekTo)mp.seekTo(r[0],true);else x.currentTime=r[0];}catch(err){x.currentTime=r[0];}}},true);}return 'ok';})(" + loop + "," + ab + ")"
    }

    /**
     * YouTube captions for the video in the player: "on" (in [language] when given), "off"; "default" changes nothing.
     * Uses YouTube's own player module (internal API — may change without notice).
     */
    fun captions(mode: String, language: String): String {
        if (mode != "on" && mode != "off") return "'default'"
        val lang = language.takeIf { Regex("[a-z]{2,3}(-[a-z0-9]{2,8})?").matches(it) }.orEmpty()
        return "(function(m,l){var mp=document.getElementById('movie_player');if(!mp)return 'none';try{" +
            "if(m==='off'){if(mp.unloadModule)mp.unloadModule('captions');return 'off';}" +
            "if(mp.loadModule)mp.loadModule('captions');if(l&&mp.setOption)mp.setOption('captions','track',{languageCode:l});return 'on';}catch(e){return 'error';}})('" + mode + "','" + lang + "')"
    }
}
