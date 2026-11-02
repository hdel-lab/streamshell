package com.streamshell.core.sponsor

import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ID = "dQw4w9WgXcQ"

class SponsorBlockApiTest {
    @Test fun videoIds() {
        assertEquals(ID, SponsorBlockApi.videoIdOf("https://m.youtube.com/watch?v=$ID&t=10"))
        assertEquals(ID, SponsorBlockApi.videoIdOf("https://www.youtube.com/watch?feature=x&v=$ID"))
        assertEquals(ID, SponsorBlockApi.videoIdOf("https://m.youtube.com/shorts/$ID"))
        assertEquals(ID, SponsorBlockApi.videoIdOf("https://youtu.be/$ID?si=abc"))
        assertEquals(ID, SponsorBlockApi.videoIdOf("https://m.youtube.com/live/$ID"))
        assertNull(SponsorBlockApi.videoIdOf("https://m.youtube.com/"))
        assertNull(SponsorBlockApi.videoIdOf("https://m.youtube.com/watch?v=short"))
        assertNull(SponsorBlockApi.videoIdOf("https://vimeo.com/watch?v=$ID"))
        assertNull(SponsorBlockApi.videoIdOf("https://music.youtube.com/watch?v=$ID"))
        assertNull(SponsorBlockApi.videoIdOf(null))
    }

    @Test fun onlyAHashPrefixIsSent() {
        assertEquals("5f6b", SponsorBlockApi.hashPrefix(ID))
        val url = SponsorBlockApi.url(ID, setOf("sponsor", "selfpromo", "bogus"))
        assertTrue(url.startsWith("https://sponsor.ajay.app/api/skipSegments/5f6b?"))
        assertFalse(url.contains(ID))
        val q = URLDecoder.decode(url.substringAfter('?'), "UTF-8")
        assertTrue(q.contains("categories=[\"selfpromo\",\"sponsor\"]"))
        assertFalse(q.contains("bogus"))
        assertTrue(q.contains("actionTypes=[\"skip\"]"))
    }

    @Test fun parsesOnlyTheRequestedVideoAndValidSegments() {
        val body = """[
          {"videoID":"other123456","segments":[{"category":"sponsor","actionType":"skip","segment":[1,20],"UUID":"x"}]},
          {"videoID":"$ID","segments":[
            {"category":"sponsor","actionType":"skip","segment":[60.5,95.2],"UUID":"a"},
            {"category":"selfpromo","actionType":"skip","segment":[10,30],"UUID":"b"},
            {"category":"intro","actionType":"skip","segment":[0,5],"UUID":"not-enabled"},
            {"category":"sponsor","actionType":"mute","segment":[100,110],"UUID":"mute"},
            {"category":"sponsor","actionType":"skip","segment":[50,50.2],"UUID":"too-short"},
            {"category":"sponsor","actionType":"skip","segment":[-1,5],"UUID":"negative"},
            {"category":"sponsor","actionType":"skip","segment":["x",5],"UUID":"bad"}
          ]}]"""
        val segs = SponsorBlockApi.parse(body, ID, setOf("sponsor", "selfpromo"))
        assertEquals(listOf("b", "a"), segs.map { it.uuid }) // sorted by start
        assertEquals(60.5, segs[1].start, 0.0)
        assertTrue(SponsorBlockApi.parse("{broken", ID, setOf("sponsor")).isEmpty())
        assertTrue(SponsorBlockApi.parse("[]", ID, setOf("sponsor")).isEmpty())
    }
}

class SponsorBlockRepositoryTest {
    private class Fake(var code: Int, var body: String, var fail: Boolean = false) : SponsorHttp {
        var calls = 0
        override fun get(url: String): Pair<Int, String> {
            calls++
            if (fail) throw IOException("offline")
            return code to body
        }
    }

    private val ok = """[{"videoID":"$ID","segments":[{"category":"sponsor","actionType":"skip","segment":[5,25],"UUID":"a"}]}]"""

    @Test fun cachesAnswers() = runBlocking {
        val f = Fake(200, ok)
        val r = SponsorBlockRepository(f)
        assertEquals(1, r.segments(ID, setOf("sponsor")).size)
        assertEquals(1, r.segments(ID, setOf("sponsor")).size)
        assertEquals(1, f.calls)
    }

    @Test fun notFoundMeansNoSegmentsAndIsCached() = runBlocking {
        val f = Fake(404, "Not Found")
        val r = SponsorBlockRepository(f)
        assertTrue(r.segments(ID, setOf("sponsor")).isEmpty())
        r.segments(ID, setOf("sponsor"))
        assertEquals(1, f.calls)
    }

    @Test fun failuresAreNotCachedAndNeverThrow() = runBlocking {
        val f = Fake(500, "", fail = true)
        val r = SponsorBlockRepository(f)
        assertTrue(r.segments(ID, setOf("sponsor")).isEmpty())
        f.fail = false
        f.code = 200
        f.body = ok
        assertEquals(1, r.segments(ID, setOf("sponsor")).size)
        assertEquals(2, f.calls)
    }

    @Test fun noCategoriesNoRequest() = runBlocking {
        val f = Fake(200, ok)
        assertTrue(SponsorBlockRepository(f).segments(ID, emptySet()).isEmpty())
        assertEquals(0, f.calls)
    }
}

class SponsorScriptsTest {
    @Test fun scriptSkipsOnlyContentSegmentsAndWaitsDuringAds() {
        val js = SponsorScripts.install(ID, listOf(SkipSegment(10.0, 30.0, "sponsor", "a")))
        assertTrue(js.contains("S.map['$ID']=[[10.0,30.0,'sponsor']]"))
        assertTrue(js.contains(".ad-showing,.ad-interrupting")) // never acts while YouTube shows an ad
        assertTrue(js.contains("var to=Math.min(s[1],v.duration-0.1)") && js.contains("mp.seekTo(to,true)")) // through YouTube's player
        assertTrue(js.contains("if(!S.timer)")) // one timer per page
        for (bad in listOf("cookie", "localStorage", "fetch(", "XMLHttpRequest", "innerHTML", "eval(", "playbackRate")) assertFalse(bad, js.contains(bad))
    }
}
