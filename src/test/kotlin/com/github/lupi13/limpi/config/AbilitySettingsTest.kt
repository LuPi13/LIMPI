package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.ability.RailgunSettings
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AbilitySettingsTest {
    @TempDir lateinit var directory: Path
    private fun file(text: String) = directory.resolve("ability.yml").also { Files.writeString(it, text) }
    @Test fun `all ability defaults validate without a server or constructed ability singletons`() {
        for ((code, spec) in AbilitySpecs.all) {
            val (settings, data) = spec.prepare(YamlConfiguration())
            assertEquals(1, data.getInt("schemaVersion"), code)
            for (field in spec.fields) when (field.type) {
                SettingType.INTEGER -> assertEquals((field.default as Number).toInt(), settings.getInt(field.key), code)
                SettingType.NUMBER -> assertEquals((field.default as Number).toDouble(), settings.getDouble(field.key), code)
                SettingType.FLAG -> assertEquals(field.default, settings.getBoolean(field.key), code)
                SettingType.ITEM_LIST -> assertEquals(field.default, settings.getItemList(field.key), code)
            }
        }
    }
    @Test fun `hawk eye exclusions default to netherite block and accept namespaced ids but not garbage`() {
        val spec = AbilitySpecs.all.getValue("hawk_eye_plus")
        assertEquals(listOf("netherite_block"), spec.load(file("cooldown_ticks: 200\n")).getItemList("excluded_items"))
        assertEquals(listOf("netherite_block", "elytra"),
            spec.load(file("excluded_items: [minecraft:netherite_block, elytra, elytra]\n")).getItemList("excluded_items"))
        assertEquals(emptyList<String>(), spec.load(file("excluded_items: []\n")).getItemList("excluded_items"))
        for (text in listOf("excluded_items: netherite_block\n", "excluded_items: [Netherite Block]\n", "excluded_items: [1]\n")) {
            val path = file(text)
            assertThrows(IllegalArgumentException::class.java) { spec.load(path) }
            assertEquals(text, Files.readString(path))
        }
    }
    @Test fun `partial config adds only missing fields while preserving zero false and unknown values`() {
        val path = file("cooldown: 0\nallowJump: false\ncustom:\n  nested: keep\n")
        val result = AbilitySpecs.all.getValue("dodge").load(path)
        assertEquals(0, result.getInt("cooldown")); assertFalse(result.getBoolean("allowJump")); assertEquals(2.0, result.getDouble("velocity"))
        val saved = YamlFiles.read(path); assertEquals("keep", saved.getString("custom.nested")); assertEquals(0, saved.getInt("cooldown"))
    }
    @Test fun `validated runtime settings and exported YAML are independent snapshots`() {
        val source = YamlConfiguration().apply { set("cooldown", 0) }
        val settings = AbilitySpecs.all.getValue("dodge").prepare(source).first
        source.set("cooldown", 99); settings.asConfiguration().set("cooldown", 100)
        assertEquals(0, settings.getInt("cooldown")); assertEquals(0, settings.asConfiguration().getInt("cooldown"))
    }
    @Test fun `failed type validation preserves original bytes and does not backfill a damaged file`() {
        val text = "cooldown: wrong\ncustom: keep\n"; val path = file(text)
        val failure = assertThrows(IllegalArgumentException::class.java) { AbilitySpecs.all.getValue("dodge").load(path) }
        assertTrue(failure.message!!.contains(path.toAbsolutePath().toString())); assertEquals(text, Files.readString(path))
    }
    @Test fun `malformed YAML and future schema are never overwritten`() {
        for (text in listOf("cooldown: [bad\n", "schemaVersion: 999\ncooldown: 0\n")) {
            val path = file(text)
            assertThrows(IllegalArgumentException::class.java) { AbilitySpecs.all.getValue("dodge").load(path) }
            assertEquals(text, Files.readString(path))
        }
    }
    @Test fun `invalid zero divisor remains zero in file and fails before runtime`() {
        for ((code, key) in listOf("blunder_booster" to "energy_max", "knee_propel" to "increment", "transcendence" to "additional_sword_ticks", "harmony_sword" to "required_damage")) {
            val text = "$key: 0\n"; val path = file(text)
            assertThrows(IllegalArgumentException::class.java) { AbilitySpecs.all.getValue(code).load(path) }
            assertEquals(text, Files.readString(path))
        }
    }
    @Test fun `zero cooldown and zero damage remain valid disabling settings`() {
        val settings = AbilitySpecs.all.getValue("blade_leaper").load(file("coolTimeTicks: 0\ndamage: 0.0\n"))
        assertEquals(0, settings.getInt("coolTimeTicks")); assertEquals(0.0, settings.getDouble("damage"))
    }
    @Test fun `fractional acceleration and armor survive integer defaults`() {
        val rail = AbilitySpecs.all.getValue("over_balance").load(file("accelerateTimeTicks: 2.5\nshootingDelayTicks: 0\n"))
        assertEquals(2.5, RailgunSettings.read(rail.asConfiguration(), false).acceleration)
        val armor = AbilitySpecs.all.getValue("zombie_king").load(file("armor_point: 2.5\n"))
        assertEquals(2.5, armor.getDouble("armor_point"))
    }
    @Test fun `integer overflow nonfinite physics and out of range probabilities fail without changes`() {
        for ((code, text) in listOf("dodge" to "cooldown: 9223372036854775807\n", "web_shooter" to "range: .nan\n", "chain_lightning" to "probability: 1.01\n", "dodge" to "allowJump: 0\n")) {
            val path = file(text)
            assertThrows(IllegalArgumentException::class.java) { AbilitySpecs.all.getValue(code).load(path) }
            assertEquals(text, Files.readString(path))
        }
    }
    @Test fun `related time overflow and invalid probability totals are rejected atomically`() {
        for ((code, text) in listOf("knee_propel" to "increaseTick: 2147483647\n", "blade_leaper" to "throwLifeTimeTicks: 2147483647\n", "over_balance" to "shootingDelayTicks: 2147483647\n", "alchemy" to "coal: 0.5\n")) {
            val path = file(text)
            assertThrows(IllegalArgumentException::class.java) { AbilitySpecs.all.getValue(code).load(path) }
            assertEquals(text, Files.readString(path))
        }
    }
    @Test fun `fully initialized config is read without rewriting or dropping its formatting`() {
        val path = directory.resolve("new.yml"); val spec = AbilitySpecs.all.getValue("dodge")
        spec.load(path); Files.writeString(path, Files.readString(path) + "# custom comment\n")
        val original = Files.readString(path); spec.load(path)
        assertEquals(original, Files.readString(path))
    }
}
