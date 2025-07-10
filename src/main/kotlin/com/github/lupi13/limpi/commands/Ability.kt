package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions.Companion.playerDisplay
import com.github.lupi13.limpi.LIMPI
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
import kotlin.math.ceil
import kotlin.math.min

class Ability: CommandExecutor {

    companion object {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        /**
         * 해당 등급의 능력을 뽑을 확률이 100%가 되는 횟수를 반환합니다.
         * @param grade 확률 계산할 등급, LEGENDARY, EPIC, TROLL만 계산.
         * @return 확률이 100%가 되는 횟수.
         */
        fun getCeiling(grade: Grade): Int {
            val config = plugin.config

            if (grade == Grade.TROLL) {
                return 1
            }
            val base = if (grade == Grade.LEGENDARY) {
                config.getDouble("LEGENDARYProbability.Base")
            } else {
                config.getDouble("EPICProbability.Base")
            }
            val increment = if (grade == Grade.LEGENDARY) {
                config.getDouble("LEGENDARYProbability.Increment")
            } else {
                config.getDouble("EPICProbability.Increment")
            }
            val increaseCount = if (grade == Grade.LEGENDARY) {
                config.getInt("LEGENDARYProbability.IncreaseCount")
            } else {
                config.getInt("EPICProbability.IncreaseCount")
            }
            return increaseCount + ceil((1.0 - base) / increment).toInt() + 1
        }

        /**
         * 능력 뽑기의 기댓값을 계산합니다.
         * @param grade 확률 계산할 등급, TROLL, LEGENDARY, EPIC만 계산.
         * @return 평균 기댓값.
         */
        fun getExpected(grade: Grade): Double {
            val config = plugin.config

            if (grade == Grade.TROLL) {
                return 1.0 / config.getDouble("TROLLProbability")
            }
            var expected = 0.0
            var pFail = 1.0
            val base = if(grade == Grade.LEGENDARY) {
                config.getDouble("LEGENDARYProbability.Base")
            } else {
                config.getDouble("EPICProbability.Base")
            }
            val increment = if(grade == Grade.LEGENDARY) {
                config.getDouble("LEGENDARYProbability.Increment")
            } else {
                config.getDouble("EPICProbability.Increment")
            }
            val increaseCount = if(grade == Grade.LEGENDARY) {
                config.getInt("LEGENDARYProbability.IncreaseCount")
            } else {
                config.getInt("EPICProbability.IncreaseCount")
            }
            val ceiling = increaseCount + ceil((1.0 - base) / increment).toInt() + 1
            for(n in 1..ceiling) {
                val p: Double = if (n < increaseCount) {
                    base
                } else {
                    min(base + increment * (n - increaseCount + 1), 1.0)
                }
                val pN = p * pFail
                expected += n * pN
                pFail *= (1 - p)

                if (p >= 1.0) {
                    break
                }
            }
            return expected
        }
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

                page = Component.text("뽑기 확률\n", NamedTextColor.DARK_AQUA)
                page = page.append(Grade.LEGENDARY.displayGrade
                    .append(Component.text(" 등급의 기본 등장 확률은 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("LEGENDARYProbability.Base") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text("입니다. 단, 마지막으로 ", NamedTextColor.BLACK))
                    .append(Grade.LEGENDARY.displayGrade)
                    .append(Component.text(" 등급을 뽑은 후 ", NamedTextColor.BLACK))
                    .append(Component.text(plugin.config.getInt("LEGENDARYProbability.IncreaseCount"), NamedTextColor.DARK_GREEN))
                    .append(Component.text("번부터, 확률이 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("LEGENDARYProbability.Increment") * 100}%p", NamedTextColor.DARK_GREEN))
                    .append(Component.text("씩 증가합니다.\n 즉, ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getInt("LEGENDARYProbability.IncreaseCount")}", NamedTextColor.DARK_GREEN))
                    .append(Component.text("번 째는 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("LEGENDARYProbability.Base") * 100 + plugin.config.getDouble("LEGENDARYProbability.Increment") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text(", ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getInt("LEGENDARYProbability.IncreaseCount") + 1}", NamedTextColor.DARK_GREEN))
                    .append(Component.text("번 째는 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("LEGENDARYProbability.Base") * 100 + plugin.config.getDouble("LEGENDARYProbability.Increment") * 200}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text(", ...의 확률로 등장합니다.\n평균적으로 ", NamedTextColor.BLACK))
                    .append(Component.text("${getExpected(Grade.LEGENDARY).toInt()}", NamedTextColor.DARK_GREEN))
                    .append(Component.text("번에 한 번씩 등장합니다.", NamedTextColor.BLACK)))
                meta.addPages(page)

                page = Component.text("", NamedTextColor.BLACK)
                    .append(Grade.LEGENDARY.displayGrade)
                    .append(Component.text(" 등급이 등장할 때, ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("LEGENDARYProbability.PickUp") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text("의 확률로, 현재 픽업 능력이 등장합니다. 만약 연속해서 ", NamedTextColor.BLACK))
                    .append(Component.text(plugin.config.getInt("LEGENDARYProbability.SemiCeiling"), NamedTextColor.DARK_GREEN))
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
                    .append(Component.text("${plugin.config.getDouble("EPICProbability.Base") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text("입니다.\n단, 마지막으로 ", NamedTextColor.BLACK))
                    .append(Grade.EPIC.displayGrade)
                    .append(Component.text(" 등급을 뽑은 후 ", NamedTextColor.BLACK))
                    .append(Component.text(plugin.config.getInt("EPICProbability.IncreaseCount"), NamedTextColor.DARK_GREEN))
                    .append(Component.text("번부터, 확률이 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("EPICProbability.Increment") * 100}%p", NamedTextColor.DARK_GREEN))
                    .append(Component.text("씩 증가합니다.\n 즉, ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getInt("EPICProbability.IncreaseCount")}", NamedTextColor.DARK_GREEN))
                    .append(Component.text("번 째는 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("EPICProbability.Base") * 100 + plugin.config.getDouble("EPICProbability.Increment") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text(", ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getInt("EPICProbability.IncreaseCount") + 1}", NamedTextColor.DARK_GREEN))
                    .append(Component.text("번 째는 ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("EPICProbability.Base") * 100 + plugin.config.getDouble("EPICProbability.Increment") * 200}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text(", ...의 확률로 등장합니다.\n평균적으로 ", NamedTextColor.BLACK))
                    .append(Component.text("${getExpected(Grade.EPIC).toInt()}", NamedTextColor.DARK_GREEN))
                    .append(Component.text("번에 한 번씩 등장합니다.", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("", NamedTextColor.BLACK)
                    .append(Grade.EPIC.displayGrade)
                    .append(Component.text(" 등급이 등장할 때, ", NamedTextColor.BLACK))
                    .append(Component.text("${plugin.config.getDouble("EPICProbability.PickUp") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text("의 확률로, 현재 픽업 능력이 등장합니다. 만약 연속해서 ", NamedTextColor.BLACK))
                    .append(Component.text(plugin.config.getInt("EPICProbability.SemiCeiling"), NamedTextColor.DARK_GREEN))
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
                    .append(Component.text("${plugin.config.getDouble("TROLLProbability") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text("입니다.\n"))

                if (plugin.config.getBoolean("ForceTroll")) {
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
                    .append(Component.text("${plugin.config.getDouble("RAREProbability") * 100}%", NamedTextColor.DARK_GREEN))
                    .append(Component.text("의 확률로 등장합니다.\n", NamedTextColor.BLACK))
                    .append(Grade.RARE.displayGrade)
                    .append(Component.text(" 등급까지 등장하지 않는다면, ", NamedTextColor.BLACK))
                    .append(Grade.COMMON.displayGrade)
                    .append(Component.text(" 등급의 능력이 등장합니다.", NamedTextColor.BLACK))

                meta.addPages(page)

                book.itemMeta = meta
                sender.openBook(book)
                return true
            }

            // /ability me
            else if (args[0].equals("me", true)) {
                val abilityCodeName = AbilityManager().getPlayerAbilityCodeName(player)
                if (abilityCodeName == null || abilityCodeName.isEmpty()) {
                    player.sendMessage(Component.text("현재 능력이 없습니다.", NamedTextColor.RED))
                    return true
                } else {
                    val ability = AbilityManager().getAbilityByCodeName(abilityCodeName)!!
                    var page = Component.text("\n", NamedTextColor.WHITE)
                    page = page.append(playerDisplay(sender))
                        .append(Component.text("님의 능력\n", NamedTextColor.WHITE))
                        .append(ability.displayName)
                        .append(Component.text(" ", NamedTextColor.WHITE))
                        .append(ability.grade.displayGrade)
                        .append(Component.text(" ", NamedTextColor.WHITE))
                        .append(ability.element.displayElement)
                        .append(Component.text("\n\n", NamedTextColor.WHITE))
                    for (line in ability.details) {
                        page = page.append(line)
                            .append(Component.text("\n", NamedTextColor.WHITE))
                    }
                    page = page.append(Component.text("\n획득 방법: ", NamedTextColor.WHITE))
                        .append(ability.howToGet)

                    val relatedQuests = ability.relatedQuest
                    if (relatedQuests != null) {
                        page = page.append(Component.text("\n\n관련 퀘스트: ", NamedTextColor.WHITE))
                        for (quest in relatedQuests) {
                            page = page.append(Component.text("", NamedTextColor.WHITE)
                                .append(quest.displayName)
                                .append(Component.text(" ", NamedTextColor.WHITE)))
                        }
                    }

                    player.sendMessage(page)
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
                    val ability = AbilityManager().getAbilityByCodeName(args[2])
                        ?: throw IllegalArgumentException("능력 코드가 잘못되었습니다.")
                    AbilityManager().applyAbility(player, ability)
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
                val playerConfig = FileManager.getPlayerData(player)
                val lCount = playerConfig.getInt("LEGENDARY.Count", 0)
                val lCeiling = playerConfig.getInt("LEGENDARY.Ceiling", 0)
                val eCount = playerConfig.getInt("EPIC.Count", 0)
                val eCeiling = playerConfig.getInt("EPIC.Ceiling", 0)

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