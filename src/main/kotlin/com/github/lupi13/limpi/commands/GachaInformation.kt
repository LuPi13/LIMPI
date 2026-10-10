package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.Grade
import com.github.lupi13.limpi.events.AbilityPickUp
import com.github.lupi13.limpi.gacha.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class GachaInformation(private val plugin: Plugin) {
    fun pages(): List<Component> {
        val settings = GachaConfiguration.settings(plugin.config)
        val curves = listOf("LEGENDARY" to settings.legendary, "EPIC" to settings.epic).map { (name, curve) ->
            Component.text("$name 추첨 규칙\n기본 ${curve.base * 100}%, ${curve.increaseAttempt}번째부터 ${curve.increment * 100}%p 증가. 조건부 100%: ${curve.guaranteeAttempt() ?: "유한 보장 없음"}.\n${if (name == "EPIC") "LEGENDARY가 나오지 않을 때만 판정하며 실패 횟수는 계속 누적합니다." else "매 추첨에서 먼저 판정합니다."}", NamedTextColor.BLACK)
        }
        return curves + listOf(
            Component.text("픽업 규칙\nLEGENDARY ${settings.legendaryPickup * 100}%, EPIC ${settings.epicPickup * 100}%로 픽업 판정합니다. 일반 후보에도 픽업이 포함되어 실제 비율은 더 높을 수 있습니다.\n연속 픽업 실패 ${settings.legendarySemiCeiling}/${settings.epicSemiCeiling}회 뒤 다음 해당 등급은 픽업입니다.", NamedTextColor.BLACK),
            Component.text("하위 등급\nLEGENDARY/EPIC 실패 뒤 TROLL ${settings.troll * 100}%, 다시 실패하면 RARE ${settings.rare * 100}%, 나머지는 COMMON입니다. 조건부 확률을 합산하지 않습니다.${if (plugin.config.getBoolean("ForceTroll")) "\nTROLL은 강제 적용합니다." else ""}", NamedTextColor.BLACK)
        )
    }
    fun pity(player: Player) {
        val state = GachaConfiguration.state(FileManager.players.snapshot(player.uniqueId))
        val settings = GachaConfiguration.settings(plugin.config)
        player.sendMessage("현재 실패 횟수: LEGENDARY ${state.legendaryCount}, EPIC ${state.epicCount}")
        player.sendMessage("다음 추첨 실제 등급 확률: " + GachaPolicy(settings).rates(state).entries.joinToString { "${it.key} ${"%.2f".format(java.util.Locale.ROOT, it.value * 100)}%" })
        for ((name, curve, failures) in listOf(Triple("LEGENDARY", settings.legendary, state.legendaryCount), Triple("EPIC", settings.epic, state.epicCount))) {
            val remaining = curve.guaranteeAttempt()?.let { if (failures >= it - 1) 1 else it - failures }
            player.sendMessage("$name 조건부 100%까지: ${remaining?.let { "$it 회" } ?: "유한 보장 없음"}${if (name == "EPIC") " · LEGENDARY가 먼저 나오면 EPIC 판정은 실행하지 않습니다." else ""}")
        }
        player.sendMessage("픽업 아닌 연속 횟수: LEGENDARY ${state.legendaryMiss}/${settings.legendarySemiCeiling}, EPIC ${state.epicMiss}/${settings.epicSemiCeiling}. 기준 이후 다음 해당 등급은 픽업입니다.")
    }
    fun pickup(sender: CommandSender) {
        fun integer(key: String): Long {
            val value = plugin.config.get(key); require(value is Int || value is Long) { "$key must be integral" }; return (value as Number).toLong()
        }
        val minutes = integer("PickUpChange"); val previews = integer("PickUpSpoiler")
        require(minutes > 0 && previews in 1..32) { "픽업 주기는 양수, 예고 개수는 1..32이어야 합니다." }
        val interval = Math.multiplyExact(minutes, 60_000L)
        val time = System.currentTimeMillis(); val rounded = Math.multiplyExact(Math.floorDiv(time, interval), interval)
        for (i in 0 until previews.toInt()) {
            val next = Math.addExact(rounded, Math.multiplyExact(i.toLong(), interval))
            val legendary = AbilityPickUp.getLegendaryPickUp(next); val epic = AbilityPickUp.getEpicPickUp(next)
            sender.sendMessage(Component.text(if (i == 0) "현재 진행중!\n" else "${AbilityPickUp.getTimeText(next)} 부터~\n")
                .append(Grade.LEGENDARY.displayGrade).append(Component.text(": ")).append(legendary.displayName)
                .append(Component.text(" / ")).append(Grade.EPIC.displayGrade).append(Component.text(": ")).append(epic.displayName))
        }
    }
    fun result(player: Player, id: java.util.UUID) {
        val gacha = FileManager.gacha.find(player.uniqueId, id)
        val selection = if (gacha == null) FileManager.selections.find(player.uniqueId, id) else null
        player.sendMessage(when {
            gacha != null -> "뽑기 결과: ${gacha.terms.outcome.grade} / ${gacha.terms.outcome.ability}, 픽업=${gacha.terms.outcome.pickup}, 거래=$id"
            selection != null -> "능력 선택: ${selection.terms.grade} / ${selection.terms.ability}, 거래=$id"
            else -> "확정된 뽑기/선택 결과가 없습니다. 회수 대기 기록은 관리자에게 확인하세요."
        })
    }
}
