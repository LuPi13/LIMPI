package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.storage.YamlFiles
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ConfigMergeTest {
    @TempDir lateinit var directory: Path
    private val template = javaClass.classLoader.getResourceAsStream("config.yml")!!.use { it.readBytes().toString(Charsets.UTF_8) }.replace("\r\n", "\n")
    private fun lines(text: String) = text.split('\n')
    private fun without(text: String, vararg removed: String) = lines(text).filterNot { line -> removed.any { line.startsWith(it) } }.joinToString("\n")

    /**
     * 2.0.0 replaced shop ratio and emerald ticket prices; 2.6.0 replaced the stock ratio model (min/max multiplier,
     * deviation and drag) with log mean reversion. An upgraded file keeps them, unused and untouched.
     */
    private val replaced = listOf("DefaultShopRatio: 5  # 기본 상점 비율(미사용)", "TicketPrice: 20  # 뽑기권 가격(에메랄드)", "SelectPrice:  # 능력 선택권 가격(에메랄드)",
        "  TROLL: 10", "  COMMON: 15", "  RARE: 20", "  EPIC: 30", "  LEGENDARY: 40", "  MYTHIC: 50",
        "StockMinimumRatio: 0.7  # 주식의 변동 최소 배율", "StockMaximumRatio: 1.3  # 주식의 변동 최대 배율",
        "StockStandardDeviation: 0.1  # 주식 변동 표준 편차(추천: (최대배율 - 최소배율) / 5)",
        "StockPriceDrag: 0.1  # 주식의 가격 회귀율, 반드시 1 이하로 할 것, 0으로 두면 중앙복귀 조정이 없으며 가격이 0으로 수렴 또는 무한대로 발산할 수 있음")
    private fun withoutReplaced(text: String): String {
        val result = lines(text).toMutableList()
        for (line in replaced) result.removeAt(result.indexOf(line).also { assertTrue(it >= 0, line) })
        // 2.0.0 lowered the default starting money; an existing file keeps the value it already has.
        result[result.indexOf("InitialMoney: 10000  # 첫 소지금").also { assertTrue(it >= 0) }] = "InitialMoney: 1000  # 첫 소지금"
        // The replaced block leaves its blank separator next to the inserted one; the template has no double blanks.
        return result.filterIndexed { index, line -> !(line.isEmpty() && index > 0 && result[index - 1].isEmpty()) }.joinToString("\n")
    }

    /** Real config.yml files shipped by earlier commits; each later version added settings, and 2.0.0 also replaced three. */
    @Test fun `every earlier shipped config is upgraded to the current template exactly`() {
        val chest = listOf("EnableChestSystem", "ChestUnlockPrice", "ChestUpgradePrice", "ChestAutoStorePrice", "ChestEnderPrice")
        val tickets = listOf("TicketMoneyPrice", "SelectMoneyPrice")
        val stock = listOf("StockVolatility", "StockReversion", "StockFeePercent")
        val estate = listOf("EnableEstateSystem", "EstatePricePerBlock", "EstateRefundPercent", "EstateMaxWidth", "EstateMaxHeight", "EstateMaxCount", "EstateWorlds")
        val history = mapOf(
            "1.4.0.yml" to listOf("SellRecoveryHours") + stock + tickets + estate,
            "1.1.1.yml" to listOf("SellRecoveryHours") + stock + tickets + estate + "NoticeRetentionDays",
            "before-816e68c.yml" to listOf("SellRecoveryHours") + stock + tickets + chest + estate + "NoticeRetentionDays",
            "before-ca07fea.yml" to listOf("HelpUrl", "SellRecoveryHours") + stock + tickets + chest + estate + "NoticeRetentionDays",
            "before-4722e13.yml" to listOf("HelpUrl", "SellRecoveryHours") + stock + listOf("BackupIntervalMinutes", "BackupKeep", "DebugTransactionTiming") + tickets + chest + estate + "NoticeRetentionDays",
            "before-803fc3f.yml" to listOf("HelpUrl", "SellRecoveryHours") + stock + listOf("RecordRetentionDays", "BackupIntervalMinutes", "BackupKeep", "DebugTransactionTiming") +
                tickets + chest + estate + "NoticeRetentionDays",
        )
        for ((name, added) in history) {
            val old = javaClass.classLoader.getResourceAsStream("config-history/$name")!!.use { it.readBytes().toString(Charsets.UTF_8) }.replace("\r\n", "\n")
            val result = ConfigMerge.merge(template, old)!!
            assertEquals(added, result.added, name)
            assertEquals(template, withoutReplaced(result.text), name)
            assertNull(ConfigMerge.merge(template, result.text), name)
            // The same file with Windows newlines keeps them.
            assertEquals(result.text.replace("\n", "\r\n"), ConfigMerge.merge(template, old.replace("\n", "\r\n"))!!.text, name)
        }
        // 2.6.1 had none of the replaced settings: only the estate section is added, with its comments in place.
        val previous = javaClass.classLoader.getResourceAsStream("config-history/before-estate.yml")!!.use { it.readBytes().toString(Charsets.UTF_8) }.replace("\r\n", "\n")
        val upgraded = ConfigMerge.merge(template, previous)!!
        assertEquals(estate, upgraded.added)
        assertEquals(template, upgraded.text)
    }

    @Test fun `a section added by a new version comes back with its comments in place`() {
        val notice = lines(template).filter { it.startsWith("#=== Notice") || it.startsWith("NoticeRetentionDays") }
        assertEquals(3, notice.size)
        // An older file: no notice section, and the server changed a value and added its own comment.
        val edited = template.replace("InitialMoney: 1000", "InitialMoney: 50000  # 우리 서버")
        val old = edited.replace("\n" + notice.joinToString("\n") + "\n", "")
        assertNotEquals(edited, old)
        val result = ConfigMerge.merge(template, old)!!
        assertEquals(listOf("NoticeRetentionDays"), result.added)
        assertEquals(edited, result.text)
        assertEquals(50000, YamlFiles.parse(result.text).getInt("InitialMoney"))
        assertNull(ConfigMerge.merge(template, result.text))
    }

    @Test fun `only the missing key is added when its section comments are already there`() {
        val old = without(template, "NoticeRetentionDays")
        assertEquals(template, ConfigMerge.merge(template, old)!!.text)
    }

    @Test fun `a missing nested key goes into its own section after the previous sibling`() {
        val all = lines(template)
        val section = all.indexOf("EPICProbability:  # EPIC 등급 등장 확률")
        val target = (section until all.size).first { all[it].startsWith("  SemiCeiling:") }
        val old = all.filterIndexed { index, _ -> index != target }.joinToString("\n")
        val result = ConfigMerge.merge(template, old)!!
        assertEquals(listOf("EPICProbability.SemiCeiling"), result.added)
        assertEquals(template, result.text)
    }

    @Test fun `a whole missing section is restored with its children and the first key goes first`() {
        val all = lines(template)
        val start = all.indexOf("LEGENDARYProbability:  # LEGENDARY 등급 등장 확률")
        val end = (start until all.size).first { all[it].isEmpty() }
        val old = (all.subList(0, start) + all.subList(end + 1, all.size)).drop(1).joinToString("\n")
        val result = ConfigMerge.merge(template, old)!!
        assertEquals(listOf("HelpUrl", "LEGENDARYProbability"), result.added)
        assertEquals(template, result.text)
    }

    @Test fun `consecutive new keys keep one copy of each comment`() {
        val base = "A: 1  # a\n\n#=== Section ===#\nB: 2\n#=== Section End ===#\n"
        val next = "A: 1  # a\n\n#=== Section ===#\nB: 2\n# new one\nC: 3\n# new two\nD: 4\n#=== Section End ===#\n\n#=== New ===#\nE: 5\n#=== New End ===#\n"
        val result = ConfigMerge.merge(next, base)!!
        assertEquals(listOf("C", "D", "E"), result.added)
        assertEquals(next, result.text)
    }

    @Test fun `existing values, removed keys and unknown fields are kept and windows newlines stay`() {
        val next = "Keep: 2  # new default\nNested:\n  a: 1\n  b: 2\nAdded: true\n"
        val current = "Keep: 9\r\nNested:\r\n  a: 7\r\nOld: x  # removed upstream\r\n"
        val result = ConfigMerge.merge(next, current)!!
        assertEquals(listOf("Nested.b", "Added"), result.added)
        assertEquals("Keep: 9\r\nNested:\r\n  a: 7\r\n  b: 2\r\nAdded: true\r\nOld: x  # removed upstream\r\n", result.text)
    }

    @Test fun `a file that would change meaning or is not yaml is refused`() {
        // Four-space indentation: a template child with two spaces would not land inside the section.
        assertThrows(Exception::class.java) { ConfigMerge.merge("S:\n  a: 1\n  b: 2\n", "S:\n    a: 1\n") }
        assertThrows(Exception::class.java) { ConfigMerge.merge(template, "InitialMoney: [\n") }
        assertNull(ConfigMerge.merge(template, template))
    }

    @Test fun `apply keeps a backup and leaves a broken file untouched`() {
        val file = directory.resolve("config.yml")
        val old = without(template, "NoticeRetentionDays")
        Files.writeString(file, old)
        val logs = mutableListOf<String>()
        val now = LocalDateTime.of(2026, 10, 15, 9, 30, 0)
        ConfigMerge.apply(file, template, now, { logs += it }) { message, _ -> logs += "WARN $message" }
        assertEquals(template, Files.readString(file))
        assertEquals(old, Files.readString(directory.resolve("config.yml.bak-20261015-093000")))
        assertEquals(listOf("Added new settings to config.yml: NoticeRetentionDays (previous file: config.yml.bak-20261015-093000)"), logs)

        logs.clear()
        ConfigMerge.apply(file, template, now.plusMinutes(1), { logs += it }) { message, _ -> logs += "WARN $message" }
        assertTrue(logs.isEmpty())

        Files.writeString(file, "InitialMoney: [\n")
        ConfigMerge.apply(file, template, now.plusMinutes(2), { logs += it }) { message, _ -> logs += "WARN $message" }
        assertEquals("InitialMoney: [\n", Files.readString(file))
        assertEquals(1, logs.size); assertTrue(logs[0].startsWith("WARN"))
        assertFalse(Files.exists(directory.resolve("config.yml.bak-20261015-093200")))
    }
}
