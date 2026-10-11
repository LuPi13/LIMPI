package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import com.github.lupi13.limpi.events.AbilityPickUp
import com.github.lupi13.limpi.gacha.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import kotlin.math.ceil
import kotlin.math.min

/** Player-facing /ability help, ceiling and pickup text. Wording/colors follow the original plugin. */
class GachaInformation(private val plugin: Plugin) {

    /**
     * 해당 등급의 능력을 뽑을 확률이 100%가 되는 횟수를 반환합니다.
     * @param grade 확률 계산할 등급, LEGENDARY, EPIC, TROLL만 계산.
     */
    fun getCeiling(grade: Grade): Int {
        val config = plugin.config
        if (grade == Grade.TROLL) return 1
        val prefix = if (grade == Grade.LEGENDARY) "LEGENDARYProbability" else "EPICProbability"
        val base = config.getDouble("$prefix.Base")
        val increment = config.getDouble("$prefix.Increment")
        val increaseCount = config.getInt("$prefix.IncreaseCount")
        return increaseCount + ceil((1.0 - base) / increment).toInt() + 1
    }

    /**
     * 능력 뽑기의 기댓값을 계산합니다.
     * @param grade 확률 계산할 등급, TROLL, LEGENDARY, EPIC만 계산.
     */
    fun getExpected(grade: Grade): Double {
        val config = plugin.config
        if (grade == Grade.TROLL) return 1.0 / config.getDouble("TROLLProbability")
        val prefix = if (grade == Grade.LEGENDARY) "LEGENDARYProbability" else "EPICProbability"
        var expected = 0.0
        var pFail = 1.0
        val base = config.getDouble("$prefix.Base")
        val increment = config.getDouble("$prefix.Increment")
        val increaseCount = config.getInt("$prefix.IncreaseCount")
        val ceiling = increaseCount + ceil((1.0 - base) / increment).toInt() + 1
        for (n in 1..ceiling) {
            val p: Double = if (n < increaseCount) base else min(base + increment * (n - increaseCount + 1), 1.0)
            expected += n * p * pFail
            pFail *= (1 - p)
            if (p >= 1.0) break
        }
        return expected
    }

    /** /ability help 책 */
    fun openHelpBook(player: Player, admin: Boolean) {
        val config = plugin.config
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta
        meta.title = "/ability 도움말"
        meta.author = "LIMPI"

        var page = Component.text("\n\n[/ability 도움말]\n", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
        page = page.append(Component.text("/ab 로도 입력할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
        meta.addPages(page)

        page = Component.text("/ability help", NamedTextColor.GREEN)
        page = page.append(Component.text(": 이 도움말을 보여줍니다.\n\n", NamedTextColor.BLACK))
        page = page.append(Component.text("/ability me", NamedTextColor.GREEN))
        page = page.append(Component.text(": 현재 본인의 능력의 상세정보, 사용법 등을 확인합니다.\n\n", NamedTextColor.BLACK))
        page = page.append(Component.text("/ability shop", NamedTextColor.GREEN))
        page = page.append(Component.text(": 뽑기권 및 선택권을 구매하는 창을 엽니다. 에메랄드로 구입할 수 있습니다.", NamedTextColor.BLACK))
        meta.addPages(page)

        page = Component.text("/ability select", NamedTextColor.GREEN)
        page = page.append(Component.text(": 뽑은 능력을 선택합니다. 해당 능력의 등급에 맞는 선택권이 필요합니다.\n\n", NamedTextColor.BLACK))
        page = page.append(Component.text("/ability dictionary(또는 dict)", NamedTextColor.GREEN))
        page = page.append(Component.text(": 모든 능력의 정보를 볼 수 있는 창을 엽니다. 우클릭하여 상세정보를 볼 수 있습니다.\n\n", NamedTextColor.BLACK))
        page = page.append(Component.text("/ability enemy", NamedTextColor.GREEN))
        page = page.append(Component.text(": 능력이 피해를 주는 ", NamedTextColor.BLACK)
            .append(Component.text("적", NamedTextColor.RED)).append(Component.text("의 범위를 설정하는 창을 엽니다.\n\n", NamedTextColor.BLACK)))
        meta.addPages(page)

        page = Component.text("/ability ceiling(또는 pity)", NamedTextColor.GREEN)
        page = page.append(Component.text(": 현재 천장 시스템의 내 정보를 확인합니다.\n\n", NamedTextColor.BLACK))
        page = page.append(Component.text("/ability pickup", NamedTextColor.GREEN))
        page = page.append(Component.text(": 현재 뽑기에서 천장 시스템을 통해 확정적으로 얻을 수 있는 능력을 확인합니다.\n\n", NamedTextColor.BLACK))
        meta.addPages(page)

        if (admin) {
            page = Component.text("이하 관리자 전용 명령어\n", NamedTextColor.RED)
            page = page.append(Component.text("/ability set <플레이어> <능력 code>", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
            page = page.append(Component.text(": 대상에게 해당 능력을 적용합니다.\n\n", NamedTextColor.BLACK))
            page = page.append(Component.text("/ability dictionary(또는 dict)", NamedTextColor.GREEN))
            page = page.append(Component.text(": 여기서 우클릭하여 해당 능력을 자신에게 적용할 수 있습니다.\n\n", NamedTextColor.BLACK))
            meta.addPages(page)
        }

        page = Component.text("뽑기 확률\n", NamedTextColor.DARK_AQUA)
        page = page.append(Grade.LEGENDARY.displayGrade
            .append(Component.text(" 등급의 기본 등장 확률은 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("LEGENDARYProbability.Base") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text("입니다. 단, 마지막으로 ", NamedTextColor.BLACK))
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급을 뽑은 후 ", NamedTextColor.BLACK))
            .append(Component.text(config.getInt("LEGENDARYProbability.IncreaseCount"), NamedTextColor.DARK_GREEN))
            .append(Component.text("번부터, 확률이 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("LEGENDARYProbability.Increment") * 100}%p", NamedTextColor.DARK_GREEN))
            .append(Component.text("씩 증가합니다.\n 즉, ", NamedTextColor.BLACK))
            .append(Component.text("${config.getInt("LEGENDARYProbability.IncreaseCount")}", NamedTextColor.DARK_GREEN))
            .append(Component.text("번 째는 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("LEGENDARYProbability.Base") * 100 + config.getDouble("LEGENDARYProbability.Increment") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text(", ", NamedTextColor.BLACK))
            .append(Component.text("${config.getInt("LEGENDARYProbability.IncreaseCount") + 1}", NamedTextColor.DARK_GREEN))
            .append(Component.text("번 째는 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("LEGENDARYProbability.Base") * 100 + config.getDouble("LEGENDARYProbability.Increment") * 200}%", NamedTextColor.DARK_GREEN))
            .append(Component.text(", ...의 확률로 등장합니다.\n평균적으로 ", NamedTextColor.BLACK))
            .append(Component.text("${getExpected(Grade.LEGENDARY).toInt()}", NamedTextColor.DARK_GREEN))
            .append(Component.text("번에 한 번씩 등장합니다.", NamedTextColor.BLACK)))
        meta.addPages(page)

        page = Component.text("", NamedTextColor.BLACK)
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급이 등장할 때, ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("LEGENDARYProbability.PickUp") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text("의 확률로, 현재 픽업 능력이 등장합니다. 만약 연속해서 ", NamedTextColor.BLACK))
            .append(Component.text(config.getInt("LEGENDARYProbability.SemiCeiling"), NamedTextColor.DARK_GREEN))
            .append(Component.text("번 "))
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급을 뽑지 못하면, 다음 ", NamedTextColor.BLACK))
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급은 픽업 능력이 확정적으로 등장합니다.", NamedTextColor.BLACK))
        meta.addPages(page)

        page = Component.text("", NamedTextColor.BLACK)
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급의 능력이 등장하지 않았을 때, ", NamedTextColor.BLACK))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급의 기본 등장 확률은 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("EPICProbability.Base") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text("입니다.\n단, 마지막으로 ", NamedTextColor.BLACK))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급을 뽑은 후 ", NamedTextColor.BLACK))
            .append(Component.text(config.getInt("EPICProbability.IncreaseCount"), NamedTextColor.DARK_GREEN))
            .append(Component.text("번부터, 확률이 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("EPICProbability.Increment") * 100}%p", NamedTextColor.DARK_GREEN))
            .append(Component.text("씩 증가합니다.\n 즉, ", NamedTextColor.BLACK))
            .append(Component.text("${config.getInt("EPICProbability.IncreaseCount")}", NamedTextColor.DARK_GREEN))
            .append(Component.text("번 째는 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("EPICProbability.Base") * 100 + config.getDouble("EPICProbability.Increment") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text(", ", NamedTextColor.BLACK))
            .append(Component.text("${config.getInt("EPICProbability.IncreaseCount") + 1}", NamedTextColor.DARK_GREEN))
            .append(Component.text("번 째는 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("EPICProbability.Base") * 100 + config.getDouble("EPICProbability.Increment") * 200}%", NamedTextColor.DARK_GREEN))
            .append(Component.text(", ...의 확률로 등장합니다.\n평균적으로 ", NamedTextColor.BLACK))
            .append(Component.text("${getExpected(Grade.EPIC).toInt()}", NamedTextColor.DARK_GREEN))
            .append(Component.text("번에 한 번씩 등장합니다.", NamedTextColor.BLACK))
        meta.addPages(page)

        page = Component.text("", NamedTextColor.BLACK)
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급이 등장할 때, ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("EPICProbability.PickUp") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text("의 확률로, 현재 픽업 능력이 등장합니다. 만약 연속해서 ", NamedTextColor.BLACK))
            .append(Component.text(config.getInt("EPICProbability.SemiCeiling"), NamedTextColor.DARK_GREEN))
            .append(Component.text("번 "))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급을 뽑지 못하면, 다음 ", NamedTextColor.BLACK))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급은 픽업 능력이 확정적으로 등장합니다.", NamedTextColor.BLACK))
        meta.addPages(page)

        page = Component.text("", NamedTextColor.BLACK)
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(", ", NamedTextColor.BLACK))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급의 능력이 등장하지 않았을 때, ", NamedTextColor.BLACK))
            .append(Grade.TROLL.displayGrade)
            .append(Component.text(" 등급의 등장 확률은 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("TROLLProbability") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text("입니다.\n"))
        if (config.getBoolean("ForceTroll")) {
            page = page.append(Grade.TROLL.displayGrade)
                .append(Component.text(" 등급이 등장하면, 해당 능력이 ", NamedTextColor.BLACK))
                .append(Component.text("강제 적용", NamedTextColor.RED))
                .append(Component.text("됩니다.", NamedTextColor.BLACK))
        }
        meta.addPages(page)

        page = Component.text("", NamedTextColor.BLACK)
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(", ", NamedTextColor.BLACK))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(", ", NamedTextColor.BLACK))
            .append(Grade.TROLL.displayGrade)
            .append(Component.text(" 등급의 능력이 등장하지 않았을 때, ", NamedTextColor.BLACK))
            .append(Grade.RARE.displayGrade)
            .append(Component.text(" 등급이 ", NamedTextColor.BLACK))
            .append(Component.text("${config.getDouble("RAREProbability") * 100}%", NamedTextColor.DARK_GREEN))
            .append(Component.text("의 확률로 등장합니다.\n", NamedTextColor.BLACK))
            .append(Grade.RARE.displayGrade)
            .append(Component.text(" 등급까지 등장하지 않는다면, ", NamedTextColor.BLACK))
            .append(Grade.COMMON.displayGrade)
            .append(Component.text(" 등급의 능력이 등장합니다.", NamedTextColor.BLACK))
        meta.addPages(page)

        if (player.hasPermission("limpi.ability.quest")) meta.addPages(Component.text("/ability quest", NamedTextColor.GREEN)
            .append(Component.text(": 퀘스트 달성 여부, 보상과 누적 진행도를 확인합니다.", NamedTextColor.BLACK)))
        if (player.hasPermission("limpi.ability.admin.reload")) meta.addPages(Component.text("/ability reload", NamedTextColor.GREEN)
            .append(Component.text(": 모든 능력 설정을 검증한 뒤 함께 다시 불러옵니다.", NamedTextColor.BLACK)))
        book.itemMeta = meta
        player.openBook(book)
    }

    /** /ability ceiling, /ability pity */
    fun pity(player: Player) {
        val state = GachaConfiguration.state(FileManager.players.snapshot(player.uniqueId))
        val lCount = state.legendaryCount
        val lCeiling = state.legendaryMiss
        val eCount = state.epicCount
        val eCeiling = state.epicMiss

        player.sendMessage(Component.text("\n", NamedTextColor.WHITE)
            .append(playerDisplay(player)).append(Component.text("님의 천장 시스템 정보\n", NamedTextColor.WHITE)))
        player.sendMessage(Component.text("마지막으로 획득한 ", NamedTextColor.WHITE)
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급의 능력은 ", NamedTextColor.WHITE))
            .append(Component.text("$lCount", NamedTextColor.GREEN))
            .append(Component.text("번 전에 획득했습니다. (", NamedTextColor.WHITE))
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급의 능력은 등장 확률이 점점 증가하여 ", NamedTextColor.WHITE))
            .append(Component.text("${getCeiling(Grade.LEGENDARY) - 2}", NamedTextColor.GREEN))
            .append(Component.text("번 이내에 획득할 수 있음이 보장되어 있습니다.)", NamedTextColor.WHITE)))
        player.sendMessage(Component.text("현재 픽업 능력이 아닌 ", NamedTextColor.WHITE)
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급의 능력을 연속으로 획득한 횟수는 ", NamedTextColor.WHITE))
            .append(Component.text("$lCeiling", NamedTextColor.GREEN))
            .append(Component.text("번입니다. (이 값이 ", NamedTextColor.WHITE))
            .append(Component.text("${plugin.config.getInt("LEGENDARYProbability.SemiCeiling")}", NamedTextColor.GREEN))
            .append(Component.text("에 도달하면 다음에 획득하는 ", NamedTextColor.WHITE))
            .append(Grade.LEGENDARY.displayGrade)
            .append(Component.text(" 등급의 능력은 반드시 픽업능력을 획득합니다.)\n", NamedTextColor.WHITE)))

        player.sendMessage(Component.text("마지막으로 획득한 ", NamedTextColor.WHITE)
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급의 능력은 ", NamedTextColor.WHITE))
            .append(Component.text("$eCount", NamedTextColor.GREEN))
            .append(Component.text("번 전에 획득했습니다. (", NamedTextColor.WHITE))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급의 능력은 등장 확률이 점점 증가하여 ", NamedTextColor.WHITE))
            .append(Component.text("${getCeiling(Grade.EPIC) - 2}", NamedTextColor.GREEN))
            .append(Component.text("번 이내에 획득할 수 있음이 보장되어 있습니다.)", NamedTextColor.WHITE)))
        player.sendMessage(Component.text("현재 픽업 능력이 아닌 ", NamedTextColor.WHITE)
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급의 능력을 연속으로 획득한 횟수는 ", NamedTextColor.WHITE))
            .append(Component.text("$eCeiling", NamedTextColor.GREEN))
            .append(Component.text("번입니다. (이 값이 ", NamedTextColor.WHITE))
            .append(Component.text("${plugin.config.getInt("EPICProbability.SemiCeiling")}", NamedTextColor.GREEN))
            .append(Component.text("에 도달하면 다음에 획득하는 ", NamedTextColor.WHITE))
            .append(Grade.EPIC.displayGrade)
            .append(Component.text(" 등급의 능력은 반드시 픽업능력을 획득합니다.)", NamedTextColor.WHITE)))
    }

    /** /ability pickup */
    fun pickup(sender: CommandSender) {
        fun integer(key: String): Long {
            val value = plugin.config.get(key); require(value is Int || value is Long) { "$key must be integral" }; return (value as Number).toLong()
        }
        val minutes = integer("PickUpChange"); val previews = integer("PickUpSpoiler")
        check(minutes > 0 && previews in 1..32) { "픽업 설정이 올바르지 않습니다. 관리자에게 문의하세요." }
        val interval = Math.multiplyExact(minutes, 60_000L)
        val rounded = AbilityPickUp.getRoundedTime()
        for (i in 1..previews.toInt()) {
            val time = Math.addExact(rounded, Math.multiplyExact((i - 1).toLong(), interval))
            val legendaryPickUp = AbilityPickUp.getLegendaryPickUp(time)
            val epicPickUp = AbilityPickUp.getEpicPickUp(time)
            if (i == 1) {
                sender.sendMessage(Component.text("\n현재 진행중!", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(Component.text("\n", NamedTextColor.WHITE)
                    .append(Component.text(AbilityPickUp.getTimeText(time), NamedTextColor.YELLOW))
                    .append(Component.text(" 부터~", NamedTextColor.WHITE)))
            }
            sender.sendMessage(Grade.LEGENDARY.displayGrade
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(legendaryPickUp.displayName)
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(legendaryPickUp.element.displayElement)
                .append(Component.text(" / ", NamedTextColor.WHITE))
                .append(Grade.EPIC.displayGrade)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(epicPickUp.displayName)
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(epicPickUp.element.displayElement))
        }
    }

    /** /ability result <id>: 확정된 자신의 뽑기/선택 결과(복구 조회용). */
    fun result(player: Player, id: java.util.UUID) {
        val gacha = FileManager.gacha.find(player.uniqueId, id)
        val selection = if (gacha == null) FileManager.selections.find(player.uniqueId, id) else null
        val code = gacha?.terms?.outcome?.ability ?: selection?.terms?.ability
        val ability = code?.let(AbilityManager::getAbilityByCodeName)
        if (code == null) {
            player.sendMessage(Component.text("확인된 결과가 없습니다.", NamedTextColor.RED))
            return
        }
        player.sendMessage(Component.text("능력 ", NamedTextColor.WHITE)
            .append(ability?.displayName ?: Component.text(code, NamedTextColor.WHITE))
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(ability?.grade?.displayGrade ?: Component.empty())
            .append(Component.text(if (gacha != null) " (뽑기)" else " (선택)", NamedTextColor.WHITE)))
    }
}
