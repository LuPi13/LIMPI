package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.storage.AtomicFiles
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Adds the settings a new plugin version introduced to an existing config.yml, as text: each missing key is copied
 * from the bundled template together with its comments and placed where the template has it. Every other line of the
 * server's file is left exactly as it is. Existing keys keep their values even when the default changed, keys the
 * template no longer has are kept, and a file that is not valid YAML is never written.
 */
object ConfigMerge {
    data class Result(val text: String, val added: List<String>)

    private val KEY = Regex("""^( *)([A-Za-z0-9_.-]+):(?:\s|$)""")

    private class Line(val text: String) {
        val trimmed = text.trim()
        val blank get() = trimmed.isEmpty()
        val comment get() = trimmed.startsWith("#")
        val indent = text.length - text.trimStart(' ').length
        val key: String? = KEY.find(text)?.groupValues?.get(2)
    }

    /** One key line with the path of every key line, in file order. */
    private class Document(text: String) {
        val lines = text.split('\n').map { Line(it.removeSuffix("\r")) }
        val paths: List<String?>

        init {
            val stack = ArrayDeque<Pair<Int, String>>()
            paths = lines.map { line ->
                val key = line.key ?: return@map null
                while (stack.isNotEmpty() && stack.last().first >= line.indent) stack.removeLast()
                val path = (stack.map { it.second } + key).joinToString(".")
                stack.addLast(line.indent to key); path
            }
        }

        fun lineOf(path: String) = paths.indexOf(path)

        /** Last line belonging to the key at [index]: deeper lines and same-indent list items, not trailing comments. */
        fun blockEnd(index: Int): Int {
            val indent = lines[index].indent
            var end = index
            for (i in index + 1 until lines.size) {
                val line = lines[i]
                if (line.blank || line.comment) continue
                if (line.indent > indent || (line.indent == indent && line.trimmed.startsWith("- "))) end = i else break
            }
            return end
        }

        /** Comment lines directly above the key at [index] (no blank line in between). */
        fun leadingComments(index: Int): Int {
            var start = index
            while (start > 0 && lines[start - 1].comment && !lines[start - 1].blank) start--
            return start
        }
    }

    /** Returns null when nothing is missing. Throws when [current] is not valid YAML. */
    fun merge(template: String, current: String): Result? {
        val newline = if (current.contains("\r\n")) "\r\n" else "\n"
        var text = current
        val defaults = YamlFiles.parse(template.replace("\r\n", "\n"))
        val source = Document(template.replace("\r\n", "\n"))
        val added = mutableListOf<String>()
        for ((index, path) in source.paths.withIndex()) {
            path ?: continue
            val existing = YamlFiles.parse(text.replace("\r\n", "\n"))
            // Only the outermost missing key: its block carries the children with it.
            val parent = path.substringBeforeLast('.', "")
            if (existing.contains(path, true) || (parent.isNotEmpty() && !existing.isConfigurationSection(parent))) continue
            text = insert(source, index, Document(text.replace("\r\n", "\n")), parent).joinToString(newline) { it }
            added += path
        }
        if (added.isEmpty()) return null
        verify(YamlFiles.parse(current.replace("\r\n", "\n")), YamlFiles.parse(text.replace("\r\n", "\n")), defaults, added)
        return Result(text, added)
    }

    private fun insert(source: Document, index: Int, target: Document, parent: String): List<String> {
        var end = source.blockEnd(index)
        // A new section's closing marker ("#=== ... End ===#") follows its last key and is followed by a blank line;
        // take it along if the target lacks it. Comments directly above the next key belong to that key instead.
        val targetComments = target.lines.filter { it.comment }.map { it.trimmed }.toSet()
        var after = end + 1
        while (after < source.lines.size && source.lines[after].comment) after++
        val trailing = (end + 1 until after).map { source.lines[it] }
        if (trailing.isNotEmpty() && (after == source.lines.size || source.lines[after].blank) && trailing.none { it.trimmed in targetComments }) end = after - 1

        // Anchor: walk back through the template to the nearest line the target also has, staying inside the parent.
        val parentLine = if (parent.isEmpty()) -1 else source.lineOf(parent)
        // The key's own comments are checked too: if the target already has them, the key goes right after them.
        var anchor: Int? = null
        var from = source.leadingComments(index)
        for (i in index - 1 downTo parentLine + 1) {
            val line = source.lines[i]
            val path = source.paths[i]
            if (path != null) {
                if (path.substringBeforeLast('.', "") != parent) continue // a deeper key of the previous sibling
                val found = target.lineOf(path)
                if (found >= 0) { anchor = target.blockEnd(found); from = source.blockEnd(i) + 1; break }
            } else if (line.comment && target.lines.count { it.comment && it.trimmed == line.trimmed } == 1) {
                val found = target.lines.indexOfFirst { it.comment && it.trimmed == line.trimmed }
                // Inside a section the matching comment must also be inside that section of the target.
                val inside = parent.isEmpty() || target.lineOf(parent).let { it in 0 until found && found <= target.blockEnd(it) }
                if (inside) { anchor = found; from = i + 1; break }
            }
        }
        if (anchor == null) {
            if (parent.isNotEmpty()) { anchor = target.lineOf(parent); from = parentLine + 1 }
            else {
                // Nothing before it exists in the target: it goes first, as in the template.
                val block = source.lines.subList(0, end + 1).map { it.text }
                val gap = if (block.last().isBlank() || target.lines.first().blank) emptyList() else listOf("")
                return block + gap + target.lines.map { it.text }
            }
        }
        val block = source.lines.subList(from, end + 1).map { it.text }
        return target.lines.take(anchor + 1).map { it.text } + block + target.lines.drop(anchor + 1).map { it.text }
    }

    /** Every value that was there is unchanged and every added key has the template's value; otherwise nothing is written. */
    private fun verify(before: YamlConfiguration, after: YamlConfiguration, defaults: YamlConfiguration, added: List<String>) {
        for (path in before.getKeys(true)) {
            val value = before.get(path)
            if (value is ConfigurationSection) check(after.isConfigurationSection(path)) { "config.yml merge changed $path" }
            else check(after.get(path) == value) { "config.yml merge changed $path" }
        }
        for (path in added) for (key in listOf(path) + ((defaults.get(path) as? ConfigurationSection)?.getKeys(true)?.map { "$path.$it" }.orEmpty())) {
            val value = defaults.get(key)
            if (value is ConfigurationSection) check(after.isConfigurationSection(key)) { "config.yml merge lost $key" }
            else check(after.get(key) == value) { "config.yml merge lost $key" }
        }
        check(after.getKeys(true).toSet() == before.getKeys(true).toSet() + added.flatMap { path ->
            listOf(path) + ((defaults.get(path) as? ConfigurationSection)?.getKeys(true)?.map { "$path.$it" }.orEmpty())
        }) { "config.yml merge produced unexpected keys" }
    }

    /**
     * Server start: merge [file] with [template], keep a backup of the previous file and report what was added.
     * A malformed config.yml or a failed check leaves the file untouched and is only reported.
     */
    fun apply(file: Path, template: String, now: LocalDateTime, info: (String) -> Unit, warning: (String, Exception) -> Unit) {
        if (!Files.exists(file)) return
        val current = try { Files.readString(file) } catch (failure: Exception) { warning("Unable to read $file", failure); return }
        val result = try { merge(template, current.removePrefix("﻿")) ?: return } catch (failure: Exception) {
            warning("config.yml was not updated with new settings; fix it or add them by hand", failure); return
        }
        val bom = if (current.startsWith("﻿")) "﻿" else ""
        val backup = file.resolveSibling("${file.fileName}.bak-${now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}")
        try {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES)
            AtomicFiles.write(file, bom + result.text)
        } catch (failure: Exception) { warning("Unable to write new settings to $file", failure); return }
        info("Added new settings to config.yml: ${result.added.joinToString()} (previous file: ${backup.fileName})")
    }
}
