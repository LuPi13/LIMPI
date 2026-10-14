package com.github.lupi13.limpi.notice

import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/** A notice is numbered per day: 2026-10-14 (001). Numbers are never reused, even after a delete. */
data class NoticeId(val date: LocalDate, val number: Int) : Comparable<NoticeId> {
    init { require(number > 0) { "Notice numbers start at 1" } }
    val key: String get() = "notice/$date/${"%03d".format(number)}"
    val label: String get() = "$date (${"%03d".format(number)})"
    override fun compareTo(other: NoticeId) = compareValuesBy(this, other, NoticeId::date, NoticeId::number)
    override fun toString() = "$date/${"%03d".format(number)}"

    companion object {
        fun parseDate(text: String): LocalDate = try { LocalDate.parse(text) }
            catch (_: DateTimeParseException) { throw IllegalArgumentException("날짜는 2026-10-14 형식으로 입력하세요.") }
        fun parseNumber(text: String): Int = text.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("공지 번호는 1 이상의 숫자로 입력하세요.")
        /** "2026-10-14/001" as stored in read lists and keys. */
        fun parse(text: String): NoticeId {
            val (date, number) = text.split('/').takeIf { it.size == 2 } ?: throw IllegalArgumentException("Invalid notice id $text")
            return NoticeId(LocalDate.parse(date), number.toInt())
        }
    }
}

/** [json] is the serialized text component exactly as it is shown. */
data class Notice(val id: NoticeId, val json: String, val author: String, val createdAt: Long)

/**
 * notice/<date>/<number>: the notices; notice_meta: the last issued number so a deleted number is not reused;
 * notice_read/<player>: ids the player has seen. Notices older than the retention are deleted, read lists keep only
 * ids that still exist. Unknown fields are preserved; a damaged meta or read list is refused, never reset.
 */
class NoticeBoard(private val store: StateStore) {
    fun write(json: String, author: String, today: LocalDate, now: Long): Notice {
        require(json.isNotBlank()) { "공지 내용을 입력하세요." }
        lateinit var notice: Notice
        store.atomic { tx ->
            val meta = tx.get(META)?.let(YamlFiles::parse) ?: YamlConfiguration()
            if (meta.contains("schemaVersion")) require(meta.get("schemaVersion") == 1) { "Unsupported notice meta schema" }
            val last = meta.getString("lastDate")?.let(LocalDate::parse)
            val lastNumber = if (meta.contains("lastNumber")) (meta.get("lastNumber") as? Int ?: error("Invalid notice meta")) else 0
            var number = if (last == today) lastNumber + 1 else 1
            // A same-day document without meta (meta lost or restored older) must not be overwritten.
            while (tx.get(NoticeId(today, number).key) != null) number++
            notice = Notice(NoticeId(today, number), json, author, now)
            tx.put(notice.id.key, YamlConfiguration().apply {
                set("schemaVersion", 1); set("date", today.toString()); set("number", number)
                set("author", author); set("createdAt", now); set("json", json)
            }.saveToString())
            meta.set("schemaVersion", 1); meta.set("lastDate", today.toString()); meta.set("lastNumber", number)
            tx.put(META, meta.saveToString())
        }
        return notice
    }

    /** Every stored notice in order; damaged documents are passed to [damaged] and left untouched. */
    fun all(damaged: (String, Exception) -> Unit = { _, _ -> }): List<Notice> =
        store.documents(PREFIX).mapNotNull { (key, payload) ->
            try { decode(key, payload) } catch (failure: Exception) { damaged(key, failure); null }
        }.sortedBy { it.id }

    fun onDate(date: LocalDate, damaged: (String, Exception) -> Unit = { _, _ -> }): List<Notice> =
        store.documents("$PREFIX$date/").mapNotNull { (key, payload) ->
            try { decode(key, payload) } catch (failure: Exception) { damaged(key, failure); null }
        }.sortedBy { it.id }

    fun find(id: NoticeId): Notice? = store.get(id.key)?.let { decode(id.key, it) }

    fun delete(id: NoticeId): Boolean {
        var removed = false
        store.atomic { tx -> if (tx.get(id.key) != null) { tx.put(id.key, null); removed = true } }
        return removed
    }

    fun unread(player: UUID): List<Notice> {
        val read = store.get(readKey(player))?.let { readIds(it) }.orEmpty()
        return all().filter { it.id !in read }
    }

    /** Marks [ids] read for each of [players] and drops read ids whose notice no longer exists. */
    fun markRead(players: Collection<UUID>, ids: Collection<NoticeId>) {
        if (players.isEmpty() || ids.isEmpty()) return
        store.atomic { tx ->
            val existing = tx.documents(PREFIX).keys
            for (player in players) {
                val data = tx.get(readKey(player))?.let(YamlFiles::parse) ?: YamlConfiguration()
                val read = if (data.contains("read")) readIds(data) else emptySet()
                val next = (read + ids).filter { it.key in existing }.sorted()
                data.set("schemaVersion", 1); data.set("read", next.map(NoticeId::toString))
                tx.put(readKey(player), data.saveToString())
            }
        }
    }

    /** Deletes notices dated more than [days] days before [today]; 0 or less keeps everything. Returns the count. */
    fun prune(today: LocalDate, days: Int): Int {
        if (days <= 0) return 0
        val cutoff = today.minusDays(days.toLong())
        var removed = 0
        store.atomic { tx ->
            for (key in tx.documents(PREFIX).keys) {
                // The date comes from the key, so an expired damaged document still expires.
                val date = runCatching { LocalDate.parse(key.removePrefix(PREFIX).substringBefore('/')) }.getOrNull() ?: continue
                if (date < cutoff) { tx.put(key, null); removed++ }
            }
        }
        return removed
    }

    private fun readIds(payload: String): Set<NoticeId> = readIds(YamlFiles.parse(payload))
    private fun readIds(data: YamlConfiguration): Set<NoticeId> {
        if (data.contains("schemaVersion")) require(data.get("schemaVersion") == 1) { "Unsupported notice read schema" }
        val list = data.getList("read") ?: throw IllegalArgumentException("Invalid notice read list")
        return list.map { NoticeId.parse(it as? String ?: throw IllegalArgumentException("Invalid notice read id")) }.toSet()
    }

    companion object {
        const val PREFIX = "notice/"
        const val META = "notice_meta"
        fun readKey(player: UUID) = "notice_read/$player"

        fun decode(key: String, payload: String): Notice {
            val data = YamlFiles.parse(payload)
            require(data.get("schemaVersion") == 1) { "Unsupported notice schema" }
            val id = NoticeId(LocalDate.parse(data.getString("date")), data.get("number") as? Int ?: throw IllegalArgumentException("Invalid notice number"))
            require(id.key == key) { "Notice key mismatch" }
            val json = data.getString("json")?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Missing notice text")
            val createdAt = (data.get("createdAt") as? Number)?.toLong() ?: throw IllegalArgumentException("Invalid notice time")
            return Notice(id, json, data.getString("author") ?: "", createdAt)
        }
    }
}

