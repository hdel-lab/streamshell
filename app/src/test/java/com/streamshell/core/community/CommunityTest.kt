package com.streamshell.core.community

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityTest {
    @Test fun dislikesParsedForTheRightVideoOnly() {
        assertEquals(1234L, DislikeRepository.parse("""{"id":"dQw4w9WgXcQ","likes":10,"dislikes":1234}""", "dQw4w9WgXcQ"))
        assertNull(DislikeRepository.parse("""{"id":"aaaaaaaaaaa","dislikes":5}""", "dQw4w9WgXcQ"))
        assertNull(DislikeRepository.parse("not json", "dQw4w9WgXcQ"))
    }

    @Test fun dislikeLookupUsesOnlyItsHostAndCaches() = runBlocking {
        var calls = 0
        val repo = DislikeRepository { url ->
            calls++
            assertTrue(url.startsWith("https://returnyoutubedislikeapi.com/votes?videoId="))
            200 to """{"id":"dQw4w9WgXcQ","dislikes":7}"""
        }
        assertEquals(7L, repo.dislikes("dQw4w9WgXcQ"))
        assertEquals(7L, repo.dislikes("dQw4w9WgXcQ"))
        assertEquals(1, calls)
    }

    @Test fun deArrowTakesTheFirstCommunityTitle() {
        val body = """{
          "dQw4w9WgXcQ":{"titles":[{"title":"Rick sings >Never Gonna Give You Up","original":false,"votes":3,"locked":false}]},
          "aaaaaaaaaaa":{"titles":[{"title":"Original","original":true,"votes":5}]},
          "bbbbbbbbbbb":{"titles":[{"title":"Downvoted","original":false,"votes":-1,"locked":false}]},
          "ccccccccccc":{"titles":[{"title":"Locked","original":false,"votes":-2,"locked":true}]},
          "bad":{"titles":[{"title":"x"}]}
        }"""
        val m = DeArrowRepository.parse(body)
        assertEquals("Rick sings Never Gonna Give You Up", m["dQw4w9WgXcQ"])
        assertEquals("Locked", m["ccccccccccc"])
        assertEquals(setOf("dQw4w9WgXcQ", "ccccccccccc"), m.keys)
    }

    @Test fun deArrowSendsOnlyHashPrefixes() = runBlocking {
        val urls = ArrayList<String>()
        val repo = DeArrowRepository { url ->
            synchronized(urls) { urls += url }
            200 to """{"dQw4w9WgXcQ":{"titles":[{"title":"Better","original":false,"votes":1}]}}"""
        }
        val t = repo.titles(listOf("dQw4w9WgXcQ", "dQw4w9WgXcQ"))
        assertEquals(mapOf("dQw4w9WgXcQ" to "Better"), t)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("/api/branding/" + com.streamshell.core.sponsor.SponsorBlockApi.hashPrefix("dQw4w9WgXcQ")))
        assertTrue(!urls[0].contains("dQw4w9WgXcQ"))
    }
}
