package com.github.lupi13.limpi.commands

import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.Grade
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
                page = page.append(Component.text(": 현재 본인의 능력을 확인합니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/ability gacha(또는 run)", NamedTextColor.GREEN))
                page = page.append(Component.text(": 능력 뽑기 창을 엽니다.", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/ability select", NamedTextColor.GREEN)
                page = page.append(Component.text(": 뽑은 능력을 선택합니다. 해당 능력의 등급에 맞는 선택권이 필요합니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/ability dictionary(또는 dict)", NamedTextColor.GREEN))
                page = page.append(Component.text(": 모든 능력의 정보를 볼 수 있는 창을 엽니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/ability shop", NamedTextColor.GREEN))
                page = page.append(Component.text(": 뽑기권 및 선택권을 구매하는 창을 엽니다.", NamedTextColor.BLACK))
                meta.addPages(page)

                page = Component.text("/ability pickup", NamedTextColor.GREEN)
                page = page.append(Component.text(": 현재 뽑기에서 확정적으로 얻을 수 있는 능력을 확인합니다.\n\n", NamedTextColor.BLACK))
                page = page.append(Component.text("/ability ceiling(또는 pity)", NamedTextColor.GREEN))
                page = page.append(Component.text(": 현재 천장 시스템의 내 정보를 확인합니다.\n\n", NamedTextColor.BLACK))
                meta.addPages(page)

                if (sender.isOp) {
                    page = Component.text("이하 관리자 전용 명령어\n", NamedTextColor.RED)
                    page = page.append(Component.text("/ability set <플레이어> <능력 code>", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                    page = page.append(Component.text(": 대상에게 해당 능력을 적용합니다.\n\n", NamedTextColor.BLACK))
                    page = page.append(Component.text("/ability reload", NamedTextColor.GREEN))
                    page = page.append(Component.text(": 능력의 .yml 변경 사항을 적용합니다.\n\n", NamedTextColor.BLACK))
                    meta.addPages(page)
                }

                page = Component.text("뽑기 확률\n", NamedTextColor.GREEN)
                page = page.append(Grade.LEGENDARY.displayGrade
                    .append(Component.text("등급은 ")))
                meta.addPages(page)

                book.itemMeta = meta
                sender.openBook(book)
                return true
            }
        }


        return true
    }
}