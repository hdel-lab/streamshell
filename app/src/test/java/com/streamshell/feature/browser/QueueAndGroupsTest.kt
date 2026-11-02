package com.streamshell.feature.browser

import com.streamshell.core.watch.ChannelGroups
import com.streamshell.core.watch.FeedVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class QueueAndGroupsTest {
    private fun item(id: String) = QueueItem("https://m.youtube.com/watch?v=$id", id)

    @Test fun queueAddsOnceAndAdvances() {
        var q = PlayQueue()
        q = QueueRules.add(q, item("aaaaaaaaaaa"))
        q = QueueRules.add(q, item("bbbbbbbbbbb"))
        assertSame(q, QueueRules.add(q, QueueItem("https://www.youtube.com/watch?v=aaaaaaaaaaa&t=3", "")))
        q = q.copy(pos = 0)
        val (n, next) = QueueRules.next(q)
        assertEquals(1, n.pos)
        assertEquals("bbbbbbbbbbb", next?.title)
        val (end, none) = QueueRules.next(n)
        assertEquals(-1, end.pos)
        assertNull(none)
    }

    @Test fun removingKeepsTheCurrentItem() {
        val q = PlayQueue(listOf(item("aaaaaaaaaaa"), item("bbbbbbbbbbb"), item("ccccccccccc")), 1)
        assertEquals(0, QueueRules.remove(q, 0).pos)
        assertEquals(-1, QueueRules.remove(q, 1).pos)
        assertEquals(1, QueueRules.remove(q, 2).pos)
    }

    @Test fun channelInOneGroupEmptyGroupsVanish() {
        var g = ChannelGroups.assign(emptyMap(), "UC1", "  Music ")
        g = ChannelGroups.assign(g, "UC2", "Music")
        assertEquals(mapOf("Music" to listOf("UC1", "UC2")), g)
        g = ChannelGroups.assign(g, "UC1", "Medicine")
        assertEquals(listOf("UC2"), g["Music"])
        assertEquals("Medicine", ChannelGroups.groupOf(g, "UC1"))
        g = ChannelGroups.assign(g, "UC1", null)
        assertEquals(setOf("Music"), g.keys)
    }

    @Test fun feedFilteredByGroup() {
        val v = listOf(FeedVideo("aaaaaaaaaaa", "a", "A", 1, "UC1"), FeedVideo("bbbbbbbbbbb", "b", "B", 2, "UC2"))
        val g = mapOf("Music" to listOf("UC2"))
        assertEquals(v, ChannelGroups.filter(v, g, null))
        assertEquals(listOf(v[1]), ChannelGroups.filter(v, g, "Music"))
        assertEquals(emptyList<FeedVideo>(), ChannelGroups.filter(v, g, "Gone"))
    }
}
