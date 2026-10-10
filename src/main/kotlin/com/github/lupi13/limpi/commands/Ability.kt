package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import com.github.lupi13.limpi.events.AbilityDictionary
import com.github.lupi13.limpi.events.AbilityEnemySelect
import com.github.lupi13.limpi.events.AbilityPickUp
import com.github.lupi13.limpi.events.AbilitySelectEvent
import com.github.lupi13.limpi.events.AbilityShop
import com.github.lupi13.limpi.events.SortOption
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import com.github.lupi13.limpi.gacha.*

class Ability: CommandExecutor {

    companion object {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)


    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender is Player) {
            val player: Player = sender

            // help
            if (args.isEmpty() || args[0].equals("help", true)) {
                val book = ItemStack(Material.WRITTEN_BOOK)
                val meta = book.itemMeta as BookMeta
                meta.title = "/ability 도움말"
                meta.author = "LIMPI"

                // 책 작성
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

                if (sender.isOp) {
                    page = Component.text("이하 관리자 전용 명령어\n", NamedTextColor.RED)
                    page = page.append(Component.text("/ability set <플레이어> <능력 code>", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                    page = page.append(Component.text(": 대상에게 해당 능력을 적용합니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/ability dictionary(또는 dict)", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 여기서 우클릭하여 해당 능력을 자신에게 적용할 수 있습니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)
                }

                val settings = GachaConfiguration.settings(plugin.config)
                for ((name, curve) in listOf("LEGENDARY" to settings.legendary, "EPIC" to settings.epic)) {
                    val guarantee = curve.guaranteeAttempt()?.toString() ?: "없음"
                    page = Component.text("$name 추첨 규칙\n", NamedTextColor.DARK_AQUA)
                    page = page.append(Component.text("직전 해당 등급 이후 ${curve.increaseAttempt}번째부터 조건부 확률이 증가합니다. 기본 ${curve.base * 100}%, 증가 ${curve.increment * 100}%p. 조건부 100% 도달: $guarantee 번째.\n", NamedTextColor.BLACK))
                    page = page.append(Component.text(if (name == "EPIC") "EPIC 판정은 LEGENDARY가 나오지 않을 때만 실행됩니다. LEGENDARY가 우선하며 EPIC 실패 횟수는 계속 누적됩니다."
                        else "LEGENDARY는 각 추첨에서 먼저 판정합니다.", NamedTextColor.BLACK))
                    meta.addPages(page)
                }
                page = Component.text("픽업 규칙\n", NamedTextColor.DARK_AQUA)
                page = page.append(Component.text("해당 등급이 나올 때 픽업 판정은 LEGENDARY ${settings.legendaryPickup * 100}%, EPIC ${settings.epicPickup * 100}%입니다. 일반 후보에도 픽업이 있어 실제 픽업 비율은 더 높을 수 있습니다.\n", NamedTextColor.BLACK))
                page = page.append(Component.text("픽업 아닌 해당 등급을 연속 LEGENDARY ${settings.legendarySemiCeiling}회, EPIC ${settings.epicSemiCeiling}회 얻으면 다음 해당 등급은 픽업입니다.", NamedTextColor.BLACK))
                meta.addPages(page)
                page = Component.text("하위 등급 규칙\n", NamedTextColor.DARK_AQUA)
                page = page.append(Component.text("LEGENDARY/EPIC 실패 시 TROLL ${settings.troll * 100}%, 그 판정도 실패하면 RARE ${settings.rare * 100}%를 판정하고 나머지는 COMMON입니다. 조건부 확률을 단순 합산하지 않습니다.", NamedTextColor.BLACK))
                if (plugin.config.getBoolean("ForceTroll")) page = page.append(Component.text("\nTROLL 획득 시 강제 적용됩니다.", NamedTextColor.RED))
                meta.addPages(page)
                book.itemMeta = meta
                sender.openBook(book)
                return true
            }

            // /ability me
            else if (args[0].equals("me", true)) {
                val abilityCodeName = AbilityManager.getPlayerAbilityCodeName(player)
                if (abilityCodeName == null || abilityCodeName.isEmpty()) {
                    player.sendMessage(Component.text("현재 능력이 없습니다.", NamedTextColor.RED))
                    return true
                } else {
                    val ability = AbilityManager.getAbilityByCodeName(abilityCodeName)!!
                    player.sendMessage(Component.text("\n", NamedTextColor.WHITE)
                        .append(playerDisplay(sender))
                        .append(Component.text("님의 능력", NamedTextColor.WHITE)))

                    ability.sendAbilityInfo(player)
                }
            }
            // /ability shop
            else if (args[0].equals("shop", true)) {
                AbilityShop.openAbilityShopGUI(player)
            }

            // /ability set <플레이어> <능력 code>
            else if (args[0].equals("set", true) && sender.isOp && args.size == 3) {
                try {
                    val player = FileManager.findPlayerByName(args[1])!!
                    val ability = AbilityManager.getAbilityByCodeName(args[2])
                        ?: throw IllegalArgumentException("능력 코드가 잘못되었습니다.")
                    AbilityManager.applyAbility(player, ability)
                } catch (e: Exception) {
                    sender.sendMessage(Component.text("플레이어를 찾을 수 없거나 능력 코드가 잘못되었습니다.", NamedTextColor.RED))
                    return false
                }
            }

            // /ability enemy
            else if (args[0].equals("enemy", true)) {
                AbilityEnemySelect.openEnemySelectGUI(player)
            }

            // /ability select
            else if (args[0].equals("select", true)) {
                AbilitySelectEvent.openAbilitySelectGUI(player, 1, SortOption.UNLOCKED)
            }

            // /ability dictionary or /ability dict
            else if (args[0].equals("dictionary", true) || args[0].equals("dict", true)) {
                AbilityDictionary.openAbilityDictionaryGUI(player, 1, SortOption.UNLOCKED)
            }

            // /ability ceiling or /ability pity
            else if (args[0].equals("ceiling", true) || args[0].equals("pity", true)) {
                val state = GachaConfiguration.state(FileManager.getPlayerData(player))
                val settings = GachaConfiguration.settings(plugin.config)
                val policy = GachaPolicy(settings)
                player.sendMessage(Component.text("현재 실패 횟수: LEGENDARY ${state.legendaryCount}, EPIC ${state.epicCount}", NamedTextColor.WHITE))
                player.sendMessage(Component.text("다음 추첨 실제 등급 확률: " + policy.rates(state).entries.joinToString { "${it.key} ${"%.2f".format(java.util.Locale.ROOT, it.value * 100)}%" }, NamedTextColor.GREEN))
                fun guarantee(label: String, curve: PityCurve, failures: Long) {
                    val attempt = curve.guaranteeAttempt()
                    val remaining = attempt?.let { if (failures >= it - 1) 1 else it - failures }
                    player.sendMessage(Component.text("$label 조건부 100%까지: ${remaining?.let { "$it 회" } ?: "유한 보장 없음"}" +
                        if (label == "EPIC") " · LEGENDARY가 먼저 나오면 EPIC 판정은 실행되지 않습니다." else "", NamedTextColor.WHITE))
                }
                guarantee("LEGENDARY", settings.legendary, state.legendaryCount)
                guarantee("EPIC", settings.epic, state.epicCount)
                player.sendMessage(Component.text("현재 픽업 아닌 연속 횟수: LEGENDARY ${state.legendaryMiss}/${settings.legendarySemiCeiling}, EPIC ${state.epicMiss}/${settings.epicSemiCeiling}. 기준 도달 후 다음 해당 등급은 픽업입니다.", NamedTextColor.WHITE))
            }

            // /ability pickup
            else if (args[0].equals("pickup", true)) {
                val roundedTime = AbilityPickUp.getRoundedTime()
                val interval = plugin.config.getInt("PickUpChange") * 60 * 1000L // 분 단위로 설정된 픽업 변경 주기 in ms

                for (i in 1..plugin.config.getInt("PickUpSpoiler")) {
                    val time = roundedTime + (i - 1) * interval
                    val legendaryPickUp = AbilityPickUp.getLegendaryPickUp(time)
                    val epicPickUp = AbilityPickUp.getEpicPickUp(time)

                    if (i == 1) {
                        player.sendMessage(Component.text("\n현재 진행중!", NamedTextColor.GREEN))
                    }
                    else {
                        player.sendMessage(Component.text("\n", NamedTextColor.WHITE)
                            .append(Component.text(AbilityPickUp.getTimeText(time), NamedTextColor.YELLOW))
                            .append(Component.text(" 부터~", NamedTextColor.WHITE)))
                    }
                    player.sendMessage(Grade.LEGENDARY.displayGrade
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
        }
        return true
    }
}
