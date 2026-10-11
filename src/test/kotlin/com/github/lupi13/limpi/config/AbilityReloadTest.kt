package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.storage.YamlFiles
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AbilityReloadTest {
    @TempDir lateinit var root: Path
    @Test fun `one invalid file rejects complete reload without normalizing any files`() {
        val specs = linkedMapOf("one" to AbilitySpec(listOf(integer("value", 1))), "two" to AbilitySpec(listOf(integer("value", 2))))
        Files.writeString(root.resolve("one.yml"), "value: 9\nunknown: keep\n")
        Files.writeString(root.resolve("two.yml"), "value: wrong\n")
        val before = Files.readAllBytes(root.resolve("one.yml"))
        val failure = assertThrows(IllegalArgumentException::class.java) { AbilityReload.prepare(root, specs) }
        assertTrue(failure.message!!.contains("two.yml"))
        assertArrayEquals(before, Files.readAllBytes(root.resolve("one.yml")))
        Files.writeString(root.resolve("two.yml"), "value: 7\n")
        val candidates = AbilityReload.prepare(root, specs)
        assertEquals(9, candidates.getValue("one").getInt("value"))
        assertEquals(7, candidates.getValue("two").getInt("value"))
        assertArrayEquals(before, Files.readAllBytes(root.resolve("one.yml")))
    }
    @Test fun `changed defaults notify only untouched previous values and do not change them`() {
        val oldSpec = AbilitySpec(listOf(integer("value", 1), number("ratio", 2.0)))
        val path = root.resolve("one.yml")
        oldSpec.load(path)
        val customized = YamlFiles.read(path).apply { set("ratio", 7.0); set("unknown", "keep") }
        YamlFiles.write(path, customized)
        val newSpec = AbilitySpec(listOf(integer("value", 3), number("ratio", 4.0)))
        val notices = mutableListOf<String>()
        val runtime = newSpec.load(path, notices::add)
        assertEquals(1, runtime.getInt("value"))
        assertEquals(7.0, runtime.getDouble("ratio"))
        assertEquals(1, notices.size)
        assertTrue(notices.single().contains("value 현재 1 → 새 기본값 3"))
        assertEquals("keep", YamlFiles.read(path).getString("unknown"))
        newSpec.load(path, notices::add)
        assertEquals(1, notices.size)
    }
    @Test fun `real ability details refresh while existing immutable settings stay unchanged`() {
        val ability = com.github.lupi13.limpi.abilities.Alchemy
        val previous = ability.config
        try {
            val spec = AbilitySpecs.all.getValue("alchemy")
            val old = spec.prepare(org.bukkit.configuration.file.YamlConfiguration()).first
            ability.config = old
            val details = ability.details
            assertSame(details, ability.details)
            val edited = old.asConfiguration().apply { set("coal", 0.2); set("iron_nugget", 0.25) }
            ability.config = spec.prepare(edited).first
            assertNotEquals(details, ability.details)
            assertSame(ability.details, ability.details)
            assertEquals(0.3, old.getDouble("coal"))
            assertEquals(0.2, ability.config!!.getDouble("coal"))
        } finally { ability.config = previous }
    }
}
