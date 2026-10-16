package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import com.github.lupi13.limpi.events.AbilityPickUp
import com.github.lupi13.limpi.gacha.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import kotlin.math.ceil
import kotlin.math.min

/** Player-facing /ability rates, ceiling and pickup text. Wording/colors follow the original plugin. */
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
    /** /ability rates: the drawing rules from the current config, in chat. */
    fun rates(sender: CommandSender) {
        val config = plugin.config
        fun number(value: Double) = java.math.BigDecimal(value).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        fun green(text: String) = Component.text(text, NamedTextColor.GREEN)
        fun white(text: String) = Component.text(text, NamedTextColor.WHITE)
        sender.sendMessage(CommandHelp.header("뽑기 확률", emptyList()))
        for (grade in listOf(Grade.LEGENDARY, Grade.EPIC)) {
            val prefix = if (grade == Grade.LEGENDARY) "LEGENDARYProbability" else "EPICProbability"
            var line = grade.displayGrade.append(white(" "))
            if (grade == Grade.EPIC) line = line.append(white("(")).append(Grade.LEGENDARY.displayGrade).append(white("이(가) 아닐 때) "))
            sender.sendMessage(line.append(white("기본 ")).append(green("${number(config.getDouble("$prefix.Base") * 100)}%"))
                .append(white(", 마지막 획득 후 ")).append(green("${config.getInt("$prefix.IncreaseCount")}"))
                .append(white("번째부터 ")).append(green("${number(config.getDouble("$prefix.Increment") * 100)}%p"))
                .append(white("씩 증가")))
            sender.sendMessage(white("  평균 ").append(green("${getExpected(grade).toInt()}"))
                .append(white("번에 한 번, 늦어도 ")).append(green("${getCeiling(grade) - 2}")).append(white("번 안에 등장")))
            sender.sendMessage(white("  등장하면 ").append(green("${number(config.getDouble("$prefix.PickUp") * 100)}%"))
                .append(white(" 확률로 픽업 능력, 픽업이 아닌 능력을 ")).append(green("${config.getInt("$prefix.SemiCeiling")}"))
                .append(white("번 연속 얻으면 다음은 픽업 확정")))
        }
        var troll = Grade.TROLL.displayGrade.append(white(" (위 등급이 아닐 때) "))
            .append(green("${number(config.getDouble("TROLLProbability") * 100)}%"))
        if (config.getBoolean("ForceTroll")) troll = troll.append(white(", 등장하면 ")).append(Component.text("강제 적용", NamedTextColor.RED))
        sender.sendMessage(troll)
        sender.sendMessage(Grade.RARE.displayGrade.append(white(" (위 등급이 아닐 때) "))
            .append(green("${number(config.getDouble("RAREProbability") * 100)}%")).append(white(", 그 밖에는 ")).append(Grade.COMMON.displayGrade))
        if (sender is Player && sender.hasPermission("limpi.ability.pity")) sender.sendMessage(CommandHelp.link("[내 천장 현황 보기]", "/ability pity", NamedTextColor.AQUA)
            .append(Component.text(" ")).append(CommandHelp.link("[픽업 능력 보기]", "/ability pickup", NamedTextColor.AQUA)))
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
        player.sendMessage(CommandHelp.link("[전체 확률 규칙 보기]", "/ability rates", NamedTextColor.AQUA))
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
                .append(Component.text(" / ", NamedTextColor.WHITE))
                .append(Grade.EPIC.displayGrade)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(epicPickUp.displayName))
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
