package com.streamshell.core.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryRulesTest {
    @Test fun newestFirstRevisitMovesUpAndCap() {
        var h = emptyList<HistoryEntry>()
        h = HistoryRules.record(h, "https://a.example/", "A", 1)
        h = HistoryRules.record(h, "https://b.example/", "B", 2)
        h = HistoryRules.record(h, "https://a.example/", "", 3) // blank title keeps the old one
        assertEquals(listOf("https://a.example/", "https://b.example/"), h.map { it.url })
        assertEquals("A", h.first().title)
        repeat(700) { h = HistoryRules.record(h, "https://x$it.example/", "", it.toLong()) }
        assertEquals(LibrarySerializer.MAX_HISTORY, h.size)
    }

    @Test fun onlyHttpsIsKept() {
        assertTrue(HistoryRules.record(emptyList(), "about:blank", "", 1).isEmpty())
        assertTrue(HistoryRules.record(emptyList(), "data:text/html,x", "", 1).isEmpty())
    }

    @Test fun searchMatchesTitleAndUrl() {
        val h = listOf(HistoryEntry("https://vimeo.com/1", "Cats", 1), HistoryEntry("https://m.youtube.com/", "YouTube", 2))
        assertEquals(1, HistoryRules.search(h, "cat").size)
        assertEquals(1, HistoryRules.search(h, "YOUTUBE").size)
        assertEquals(2, HistoryRules.search(h, " ").size)
    }

    @Test fun serializerToleratesCorruption() {
        assertTrue(LibrarySerializer.decodeHistory("{x").isEmpty())
        assertTrue(LibrarySerializer.decodeBookmarks("""[{"u":"javascript:x","t":"x"}]""").isEmpty())
        val b = listOf(Bookmark("https://a.example/", "A", 1))
        assertEquals(b, LibrarySerializer.decodeBookmarks(LibrarySerializer.encodeBookmarks(b)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo(): DataStoreLibraryRepository {
        val f = File(tmp.root, "lib-${System.nanoTime()}.preferences_pb")
        return DataStoreLibraryRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { f })
    }

    @Test fun bookmarksToggleAndPersist() = runTest(UnconfinedTestDispatcher()) {
        val r = repo()
        r.toggleBookmark("https://a.example/", "A", 1)
        r.toggleBookmark("https://b.example/", "", 2)
        assertEquals(listOf("https://b.example/", "https://a.example/"), r.bookmarks.first().map { it.url })
        assertEquals("https://b.example/", r.bookmarks.first().first().title) // blank title falls back to the URL
        r.toggleBookmark("https://a.example/", "A", 3)
        assertEquals(listOf("https://b.example/"), r.bookmarks.first().map { it.url })
        r.toggleBookmark("http://insecure.example/", "x", 4)
        assertEquals(1, r.bookmarks.first().size)
    }

    @Test fun historyRecordRetitleRemoveClear() = runTest(UnconfinedTestDispatcher()) {
        val r = repo()
        r.recordVisit("https://a.example/", "", 1)
        r.updateTitle("https://a.example/", "Title A")
        r.recordVisit("https://b.example/", "B", 2)
        assertEquals("Title A", r.history.first().last().title)
        r.removeHistory("https://b.example/")
        assertEquals(listOf("https://a.example/"), r.history.first().map { it.url })
        r.clearHistory()
        assertTrue(r.history.first().isEmpty())
    }
}
