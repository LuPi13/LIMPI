package com.github.lupi13.limpi.notice

import com.github.lupi13.limpi.storage.SqliteStateStore
import com.github.lupi13.limpi.storage.StateStore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

class NoticeBoardTest {
    @TempDir lateinit var directory: Path
    private val day = LocalDate.of(2026, 10, 14)
    private val player = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun withBoard(body: (StateStore, NoticeBoard) -> Unit) =
        SqliteStateStore(directory.resolve("state.db")).use { body(it, NoticeBoard(it)) }

    @Test fun `numbers count per day and are not reused after a delete`() = withBoard { _, board ->
        assertEquals("2026-10-14 (001)", board.write("a", "admin", day, 1).id.label)
        val second = board.write("b", "admin", day, 2)
        assertEquals(2, second.id.number)
        assertTrue(board.delete(second.id))
        assertFalse(board.delete(second.id))
        assertEquals(3, board.write("c", "admin", day, 3).id.number)
        assertEquals(1, board.write("d", "admin", day.plusDays(1), 4).id.number)
        assertEquals(listOf(1, 3), board.onDate(day).map { it.id.number })
        assertEquals("c", board.find(NoticeId(day, 3))!!.json)
        assertNull(board.find(NoticeId(day, 2)))
    }

    @Test fun `a lost meta never overwrites a notice of the same day`() = withBoard { store, board ->
        board.write("a", "admin", day, 1)
        store.atomic { it.put(NoticeBoard.META, null) }
        assertEquals(2, board.write("b", "admin", day, 2).id.number)
        assertEquals("a", board.find(NoticeId(day, 1))!!.json)
    }

    @Test fun `unread starts with every kept notice and read lists drop deleted ids`() = withBoard { store, board ->
        val first = board.write("a", "admin", day, 1); val second = board.write("b", "admin", day, 2)
        assertEquals(listOf(first, second), board.unread(player))
        board.markRead(listOf(player, other), listOf(first.id))
        assertEquals(listOf(second), board.unread(player))
        board.delete(first.id)
        board.markRead(listOf(player), listOf(second.id))
        assertTrue(board.unread(player).isEmpty())
        assertFalse(store.get(NoticeBoard.readKey(player))!!.contains(first.id.toString()))
        assertEquals(listOf(second), board.unread(other))
    }

    @Test fun `a damaged read list is refused instead of reset`() = withBoard { store, board ->
        val notice = board.write("a", "admin", day, 1)
        store.atomic { it.put(NoticeBoard.readKey(player), "schemaVersion: 1\nread: broken\n") }
        assertThrows(IllegalArgumentException::class.java) { board.unread(player) }
        assertThrows(IllegalArgumentException::class.java) { board.markRead(listOf(player), listOf(notice.id)) }
        assertEquals("schemaVersion: 1\nread: broken\n", store.get(NoticeBoard.readKey(player)))
    }

    @Test fun `prune removes notices older than the retention including damaged ones`() = withBoard { store, board ->
        board.write("old", "admin", day.minusDays(31), 1)
        board.write("edge", "admin", day.minusDays(30), 2)
        board.write("new", "admin", day, 3)
        store.atomic { it.put("notice/2026-09-01/001", "broken: [") }
        val damaged = mutableListOf<String>()
        assertEquals(3, board.all { key, _ -> damaged.add(key) }.size)
        assertEquals(listOf("notice/2026-09-01/001"), damaged)
        assertEquals(0, board.prune(day, 0))
        assertEquals(2, board.prune(day, 30))
        assertEquals(listOf("edge", "new"), board.all().map { it.json })
    }

    @Test fun `plain text keeps brackets and newline escapes`() {
        val plain = NoticeText.parse("[공지] 점검\\n오후 3시")
        assertEquals("[공지] 점검\n오후 3시", PlainTextComponentSerializer.plainText().serialize(plain))
        assertFalse(NoticeText.looksLikeJson("[공지] 점검"))
    }

    @Test fun `json text accepts string decoration flags and keeps styles through storage`() {
        val component = NoticeText.parse("""[{"text":"공지입니다\n","bold":"true","color":"gold"},{"text":"hello\n","bold":"false","italic":"true"}]""")
        val restored = NoticeText.deserialize(NoticeText.serialize(component))
        assertEquals("공지입니다\nhello\n", PlainTextComponentSerializer.plainText().serialize(restored))
        val parts = listOf(restored) + restored.children()
        val first = parts.first { (it as? net.kyori.adventure.text.TextComponent)?.content() == "공지입니다\n" }
        val second = parts.first { (it as? net.kyori.adventure.text.TextComponent)?.content() == "hello\n" }
        assertEquals(TextDecoration.State.TRUE, first.decoration(TextDecoration.BOLD))
        assertEquals(NamedTextColor.GOLD, first.color())
        assertEquals(TextDecoration.State.FALSE, second.decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, second.decoration(TextDecoration.ITALIC))
        assertEquals(Component.text("x"), NoticeText.parse("""{"text":"x"}"""))
    }

    @Test fun `broken json is refused instead of broadcast raw`() {
        assertThrows(IllegalArgumentException::class.java) { NoticeText.parse("""[{"text":"a","bold":true}""") }
        assertThrows(IllegalArgumentException::class.java) { NoticeText.parse("""{"text":""}""") }
        assertThrows(IllegalArgumentException::class.java) { NoticeText.parse("   ") }
    }
}
