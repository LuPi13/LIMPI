package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.commands.EconomyText
import com.github.lupi13.limpi.economy.BankException
import com.github.lupi13.limpi.economy.BankFailure
import com.github.lupi13.limpi.estate.EstateOverlap
import com.github.lupi13.limpi.estate.EstateRegion
import com.github.lupi13.limpi.estate.EstateSettings
import com.github.lupi13.limpi.estate.EstateUnavailable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Level

/** Selecting and buying an estate, showing its edges, and the action bar when walking into one. */
class EstateEvents : Listener {
    private class Point(val world: UUID, val worldName: String, val x: Int, val y: Int, val z: Int)
    private class Quote(val region: EstateRegion, val worldName: String, val price: Long)
    private class Selection {
        var active = false
        var first: Point? = null
        var second: Point? = null
        var sky = false
        var quote: Quote? = null
        var overlaps: List<EstateRegion> = emptyList()
    }

    companion object {
        private val plugin get() = JavaPlugin.getPlugin(LIMPI::class.java)
        val enabled: Boolean get() = plugin.config.getBoolean("EnableEstateSystem")
        private val selections = HashMap<UUID, Selection>()
        private val displays = HashMap<UUID, BukkitTask>()
        private val names = HashMap<UUID, String>()
        private val inside = HashMap<UUID, UUID>()
        private var notice: BukkitTask? = null

        /** null when config.yml has an invalid estate setting (logged); purchases are refused then. */
        fun settings(): EstateSettings? = try { EstateSettings.read(plugin.config) } catch (failure: IllegalArgumentException) {
            plugin.logger.log(Level.WARNING, "Invalid estate settings in config.yml", failure); null
        }

        fun ownerName(owner: UUID): String = names.getOrPut(owner) {
            Bukkit.getPlayer(owner)?.name ?: runCatching { FileManager.players.profile(owner).name }.getOrNull() ?: owner.toString()
        }

        private fun coords(x: Int, y: Int, z: Int) = "($x, $y, $z)"

        /** Selection mode: an empty hand left click sets the first corner, a right click the second. */
        fun toggle(player: Player) {
            val selection = selections.getOrPut(player.uniqueId) { Selection() }
            selection.active = !selection.active
            if (selection.active) player.sendMessage(Component.text("구역 선택 모드가 켜졌습니다. 빈손으로 블록을 ", NamedTextColor.WHITE)
                .append(Component.text("좌클릭", NamedTextColor.GREEN)).append(Component.text("하면 첫번째 좌표, ", NamedTextColor.WHITE))
                .append(Component.text("우클릭", NamedTextColor.GREEN)).append(Component.text("하면 두번째 좌표가 설정됩니다.", NamedTextColor.WHITE)))
            else player.sendMessage(Component.text("구역 선택 모드가 꺼졌습니다.", NamedTextColor.WHITE))
        }

        fun toggleSky(player: Player) {
            val selection = selections[player.uniqueId]
            if (selection?.first == null || selection.second == null) {
                player.sendMessage(Component.text("지금은 이용할 수 없습니다.", NamedTextColor.RED)); return
            }
            selection.sky = !selection.sky
            quote(player, selection)
        }

        private fun quote(player: Player, selection: Selection) {
            selection.quote = null; selection.overlaps = emptyList()
            val first = selection.first ?: return
            val second = selection.second ?: return
            if (first.world != second.world) {
                player.sendMessage(Component.text("두 좌표가 서로 다른 월드에 있습니다.", NamedTextColor.RED)); return
            }
            var region = EstateRegion.of(first.world, first.x, first.y, first.z, second.x, second.y, second.z)
            if (selection.sky) Bukkit.getWorld(first.world)?.let { region = region.withHeight(region.minY, it.maxHeight - 1) }
            show(player, listOf(region))
            val settings = settings() ?: run {
                player.sendMessage(Component.text("사유지 설정에 오류가 있어 지금은 구매할 수 없습니다.", NamedTextColor.RED)); return
            }
            val price = try { FileManager.estates.quote(player.uniqueId, region, first.worldName, settings) } catch (failure: EstateOverlap) {
                selection.overlaps = failure.parts
                player.sendMessage(Component.text(failure.message!!, NamedTextColor.YELLOW))
                player.sendMessage(Component.text("거래가 취소되었습니다.", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("여기를 누르면 겹치는 부분에 효과가 나타납니다.", NamedTextColor.LIGHT_PURPLE)
                    .hoverEvent(HoverEvent.showText(Component.text("/estate overlaps"))).clickEvent(ClickEvent.runCommand("/estate overlaps")))
                return
            } catch (failure: EstateUnavailable) {
                player.sendMessage(Component.text(failure.message!!, NamedTextColor.RED)); return
            }
            selection.quote = Quote(region, first.worldName, price.units)
            player.sendMessage(Component.text(region.describe(), NamedTextColor.GREEN).append(Component.text(", ", NamedTextColor.WHITE))
                .append(Component.text("${region.volume}칸", NamedTextColor.AQUA))
                .append(Component.text("(${region.width}×${region.depth}×높이 ${region.height})", NamedTextColor.GRAY))
                .append(Component.text(" X ", NamedTextColor.WHITE))
                .append(Component.text("${settings.pricePerBlock.units}원/칸 = ${price.units}원", NamedTextColor.GOLD))
                .append(Component.text("입니다.", NamedTextColor.WHITE)))
            fun button(text: String, color: NamedTextColor, hover: String, click: ClickEvent<*>) =
                Component.text(text, color).hoverEvent(HoverEvent.showText(Component.text(hover))).clickEvent(click)
            player.sendMessage(Component.text("구매하시겠습니까? ", NamedTextColor.WHITE)
                .append(button("[승인]", NamedTextColor.GREEN, "/estate accept <사유지이름>", ClickEvent.suggestCommand("/estate accept ")))
                .append(Component.text(" "))
                .append(button("[취소]", NamedTextColor.RED, "/estate deny", ClickEvent.runCommand("/estate deny")))
                .append(Component.text(" "))
                .append(button("[구역 보기]", NamedTextColor.LIGHT_PURPLE, "/estate show", ClickEvent.runCommand("/estate show")))
                .append(Component.text(" "))
                .append(button(if (selection.sky) "[하늘 포함 해제]" else "[하늘까지 포함]", NamedTextColor.AQUA, "/estate sky", ClickEvent.runCommand("/estate sky"))))
        }

        fun accept(player: Player, name: String) {
            val selection = selections[player.uniqueId]
            val quote = selection?.quote ?: run { player.sendMessage(Component.text("지금은 이용할 수 없습니다.", NamedTextColor.RED)); return }
            val settings = settings() ?: run {
                player.sendMessage(Component.text("사유지 설정에 오류가 있어 지금은 구매할 수 없습니다.", NamedTextColor.RED)); return
            }
            try {
                FileManager.estates.buy(UUID.randomUUID(), player.uniqueId, name, quote.region, quote.worldName, quote.price, settings)
            } catch (failure: BankException) {
                if (failure.reason != BankFailure.INSUFFICIENT_FUNDS) throw failure
                selection.quote = null
                player.sendMessage(EconomyText.insufficient(player, "원 있습니다."))
                player.sendMessage(Component.text("거래가 취소되었습니다.", NamedTextColor.WHITE))
                return
            } catch (failure: EstateUnavailable) {
                // The name can be fixed and retried; anything else needs a new quote.
                if (failure.message != "같은 이름의 사유지가 이미 존재합니다." && !failure.message!!.startsWith("사유지 이름은")) selection.quote = null
                player.sendMessage(Component.text(failure.message!!, NamedTextColor.RED)); return
            }
            selections.remove(player.uniqueId)
            player.sendMessage(Component.text(name, NamedTextColor.GREEN).append(Component.text(" 사유지를 구매했습니다. 거래가 완료되었습니다.", NamedTextColor.WHITE)))
        }

        fun deny(player: Player) {
            if (selections[player.uniqueId]?.quote == null) { player.sendMessage(Component.text("지금은 이용할 수 없습니다.", NamedTextColor.RED)); return }
            selections.remove(player.uniqueId)
            player.sendMessage(Component.text("거래가 취소되었습니다.", NamedTextColor.WHITE))
        }

        fun showSelection(player: Player) {
            val quote = selections[player.uniqueId]?.quote ?: run { player.sendMessage(Component.text("지금은 이용할 수 없습니다.", NamedTextColor.RED)); return }
            show(player, listOf(quote.region))
        }

        fun showOverlaps(player: Player) {
            val overlaps = selections[player.uniqueId]?.overlaps.orEmpty()
            if (overlaps.isEmpty()) { player.sendMessage(Component.text("지금은 이용할 수 없습니다.", NamedTextColor.RED)); return }
            show(player, overlaps)
        }

        /** Draws the outer edges of [regions] to [player] only, every half second for five seconds. */
        fun show(player: Player, regions: List<EstateRegion>) {
            displays.remove(player.uniqueId)?.cancel()
            var runs = 0
            displays[player.uniqueId] = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (!player.isOnline || ++runs > 10) { displays.remove(player.uniqueId)?.cancel(); return@Runnable }
                regions.filter { it.world == player.world.uid }.forEach { draw(player, it) }
            }, 0L, 10L)
        }

        private fun draw(player: Player, region: EstateRegion) {
            val x1 = region.minX.toDouble(); val y1 = region.minY.toDouble(); val z1 = region.minZ.toDouble()
            val x2 = region.maxX + 1.0; val y2 = region.maxY + 1.0; val z2 = region.maxZ + 1.0
            // Fixed budget per box: long sky-high edges get sparser points instead of more packets.
            val step = maxOf(0.5, 4.0 * (region.width + region.depth + region.height) / MAX_POINTS)
            val eye = player.location
            fun line(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double) {
                val length = maxOf(bx - ax, by - ay, bz - az)
                var t = 0.0
                while (t <= length) {
                    val f = if (length == 0.0) 0.0 else t / length
                    val x = ax + (bx - ax) * f; val y = ay + (by - ay) * f; val z = az + (bz - az) * f
                    val dx = x - eye.x; val dy = y - eye.y; val dz = z - eye.z
                    if (dx * dx + dy * dy + dz * dz <= VIEW * VIEW) player.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                    t += step
                }
            }
            for (y in listOf(y1, y2)) {
                line(x1, y, z1, x2, y, z1); line(x1, y, z2, x2, y, z2); line(x1, y, z1, x1, y, z2); line(x2, y, z1, x2, y, z2)
            }
            for ((x, z) in listOf(x1 to z1, x2 to z1, x1 to z2, x2 to z2)) line(x, y1, z, x, y2, z)
        }

        /** Action bar when a player walks into an estate. Checked twice a second, only on a change of estate. */
        fun startNotice() {
            notice?.cancel()
            notice = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                for (player in Bukkit.getOnlinePlayers()) {
                    val location = player.location
                    val estate = FileManager.estates.at(player.world.uid, location.blockX, location.blockY, location.blockZ)
                    val previous = if (estate == null) inside.remove(player.uniqueId) else inside.put(player.uniqueId, estate.id)
                    if (estate == null || previous == estate.id) continue
                    player.sendActionBar(Component.text("이 지역은 ", NamedTextColor.YELLOW)
                        .append(Component.text(ownerName(estate.owner), NamedTextColor.GREEN))
                        .append(Component.text("님의 사유지 ", NamedTextColor.YELLOW))
                        .append(Component.text(estate.name, NamedTextColor.GREEN))
                        .append(Component.text("입니다.", NamedTextColor.YELLOW)))
                }
            }, 10L, 10L)
        }

        fun stop() {
            notice?.cancel(); notice = null
            displays.values.forEach(BukkitTask::cancel); displays.clear()
            selections.clear(); inside.clear()
        }

        private const val MAX_POINTS = 1200.0
        private const val VIEW = 64.0
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSelect(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.item != null) return
        val left = event.action == Action.LEFT_CLICK_BLOCK
        if (!left && event.action != Action.RIGHT_CLICK_BLOCK) return
        val player = event.player
        val selection = selections[player.uniqueId]?.takeIf { it.active } ?: return
        if (!enabled) return
        val block = event.clickedBlock ?: return
        event.isCancelled = true
        val point = Point(block.world.uid, block.world.name, block.x, block.y, block.z)
        if (left) selection.first = point else selection.second = point
        player.sendMessage(Component.text(if (left) "첫번째 좌표가 " else "두번째 좌표가 ", NamedTextColor.WHITE)
            .append(Component.text(coords(point.x, point.y, point.z), NamedTextColor.GREEN))
            .append(Component.text("으로 설정되었습니다.", NamedTextColor.WHITE)))
        quote(player, selection)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val id = event.player.uniqueId
        selections.remove(id); inside.remove(id); displays.remove(id)?.cancel()
        names.remove(id)
    }
}
