package com.streamshell.core.sponsor

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * SponsorBlock (https://sponsor.ajay.app): a community database of segments inside videos (sponsor reads,
 * self-promotion, "like and subscribe" reminders, intros ...). StreamShell looks segments up and jumps over
 * them while the video plays. This skips parts of the creator's own video; it does NOT remove YouTube's ads.
 *
 * Privacy: only the first 4 characters of the SHA-256 hash of the video ID are sent (k-anonymity: the
 * server answers with all videos sharing that prefix and the match happens on the phone).
 * Data: SponsorBlock contributors, licensed CC BY-NC-SA 4.0.
 */
enum class SponsorCategory(val key: String, val label: String) {
    SPONSOR("sponsor", "Sponsor"),
    SELF_PROMO("selfpromo", "Self-promotion"),
    INTERACTION("interaction", "Interaction reminder"),
    INTRO("intro", "Intro"),
    OUTRO("outro", "Endcards / credits"),
    PREVIEW("preview", "Preview / recap"),
    FILLER("filler", "Filler tangent"),
    MUSIC_OFFTOPIC("music_offtopic", "Non-music part")
    ;

    companion object {
        val defaults: Set<String> = setOf(SPONSOR.key, SELF_PROMO.key, INTERACTION.key)
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

data class SkipSegment(val start: Double, val end: Double, val category: String, val uuid: String)

object SponsorBlockApi {
    const val HOST = "sponsor.ajay.app"
    private val idRe = Regex("^[A-Za-z0-9_-]{11}$")

    /** The YouTube video ID of a watch, short, live or youtu.be URL; null for anything else. */
    fun videoIdOf(url: String?): String? {
        if (url == null) return null
        val host = com.streamshell.core.browser.UrlHosts.hostOf(url) ?: return null
        val isYt = host == "youtube.com" || host.endsWith(".youtube.com")
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('#')
        val id = when {
            host == "youtu.be" -> path.substringBefore('?').substringBefore('/')

            !isYt || host == "music.youtube.com" -> null

            path.startsWith("watch") -> path.substringAfter('?', "").split('&').firstOrNull { it.startsWith("v=") }?.substring(2)

            path.startsWith("shorts/") || path.startsWith("live/") || path.startsWith("embed/") ->
                path.substringAfter('/').substringBefore('?').substringBefore('/')

            else -> null
        }
        return id?.takeIf { idRe.matches(it) }
    }

    fun hashPrefix(videoId: String): String = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }.take(4)

    fun url(videoId: String, categories: Set<String>): String {
        val cats = categories.filter { SponsorCategory.of(it) != null }.sorted()
        val json = cats.joinToString(",", "[", "]") { "\"$it\"" }
        return "https://$HOST/api/skipSegments/${hashPrefix(videoId)}?categories=" + URLEncoder.encode(json, "UTF-8") +
            "&actionTypes=" + URLEncoder.encode("[\"skip\"]", "UTF-8") + "&service=YouTube"
    }

    /** Picks [videoId]'s segments out of the prefix answer; anything malformed is ignored. */
    fun parse(body: String, videoId: String, categories: Set<String>): List<SkipSegment> = try {
        val arr = JSONArray(body)
        val out = ArrayList<SkipSegment>()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            if (v.optString("videoID") != videoId) continue
            val segs = v.optJSONArray("segments") ?: continue
            for (j in 0 until minOf(segs.length(), 100)) {
                val s = segs.optJSONObject(j) ?: continue
                val cat = s.optString("category")
                if (cat !in categories || SponsorCategory.of(cat) == null) continue
                if (s.optString("actionType", "skip") != "skip") continue
                val seg = s.optJSONArray("segment") ?: continue
                val start = seg.optDouble(0, Double.NaN)
                val end = seg.optDouble(1, Double.NaN)
                if (start.isNaN() || end.isNaN() || start < 0 || end - start < 0.5 || end > 86_400) continue
                out += SkipSegment(start, end, cat, s.optString("UUID").take(80))
            }
        }
        out.sortedBy { it.start }
    } catch (_: Exception) {
        emptyList()
    }
}

fun interface SponsorHttp {
    /** Returns HTTP status and body; throws IOException when offline. */
    @Throws(IOException::class)
    fun get(url: String): Pair<Int, String>
}

/** https only, sponsor.ajay.app only, no redirects, 10 s timeouts, 1 MB limit, no cookies or identifiers. */
class UrlConnectionSponsorHttp : SponsorHttp {
    override fun get(url: String): Pair<Int, String> {
        val u = URL(url)
        require(u.protocol == "https" && u.host == SponsorBlockApi.HOST) { "refusing non-SponsorBlock URL" }
        val c = (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) return code to ""
            val bytes = c.inputStream.use { s ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = s.read(chunk)
                    if (n < 0) break
                    if (buf.size() + n > 1_000_000) throw IOException("answer too large")
                    buf.write(chunk, 0, n)
                }
                buf.toByteArray()
            }
            return code to bytes.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }
}

/** Looks up segments with a small in-memory cache (per video and category set). Failures mean "no segments". */
class SponsorBlockRepository(private val http: SponsorHttp = UrlConnectionSponsorHttp(), private val maxEntries: Int = 64) {
    private val cache = object : LinkedHashMap<String, List<SkipSegment>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>?) = size > maxEntries
    }

    suspend fun segments(videoId: String, categories: Set<String>): List<SkipSegment> {
        if (categories.isEmpty()) return emptyList()
        val key = videoId + "|" + categories.sorted().joinToString(",")
        synchronized(cache) { cache[key]?.let { return it } }
        val result = withContext(Dispatchers.IO) {
            try {
                val (code, body) = http.get(SponsorBlockApi.url(videoId, categories))
                when (code) {
                    200 -> SponsorBlockApi.parse(body, videoId, categories)

                    404 -> emptyList()

                    // no segments for this prefix
                    else -> null // temporary problem: do not cache
                }
            } catch (_: Exception) {
                null
            }
        } ?: return emptyList()
        synchronized(cache) { cache[key] = result }
        return result
    }
}

/**
 * The page side: one timer that jumps over the current video's segments, like the SponsorBlock browser
 * extension. It waits while YouTube shows an ad (it never touches ads) and reports skips back to
 * StreamShell through a message channel restricted to YouTube's origin.
 */
object SponsorScripts {
    const val CHANNEL = "streamShellSponsor"

    fun install(videoId: String, segments: List<SkipSegment>): String {
        val segs = segments.joinToString(",", "[", "]") { "[${it.start},${it.end},'${it.category}']" }
        return """
(function(){
var S=window.__streamshellSB||(window.__streamshellSB={map:{},done:{},timer:0});
S.map['$videoId']=$segs;
function vid(){var u=new URL(location.href);var v=u.searchParams.get('v');if(v)return v;var m=u.pathname.match(/^\/(shorts|live|embed)\/([A-Za-z0-9_-]{11})/);return m?m[2]:null;}
function tick(){var id=vid();var segs=id&&S.map[id];if(!segs)return;
if(document.querySelector('.ad-showing,.ad-interrupting'))return;
var v=document.querySelector('video');if(!v||!(v.duration>0)||v.paused)return;var t=v.currentTime;
for(var i=0;i<segs.length;i++){var s=segs[i];var k=id+':'+i;
if(t>=s[0]&&t<s[1]-0.3&&!S.done[k]){S.done[k]=1;var to=Math.min(s[1],v.duration-0.1),mp=document.getElementById('movie_player');try{if(mp&&mp.seekTo)mp.seekTo(to,true);else v.currentTime=to;}catch(x){v.currentTime=to;}
try{if(window.__ssDiagLog)window.__ssDiagLog('sponsorblock: skip '+s[2]+' '+Math.round(s[0])+'→'+Math.round(to));}catch(x){}
if(window.$CHANNEL)window.$CHANNEL.postMessage(s[2]+'|'+Math.round(s[1]-s[0]));}}}
if(!S.timer)S.timer=setInterval(tick,300);
return 'ok';})()
""".trim()
    }
}
