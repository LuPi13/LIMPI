package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import kotlin.random.Random
import com.github.lupi13.limpi.gacha.*
import com.github.lupi13.limpi.abilities.AcquisitionType

class GachaEvents(private val owner: Plugin) : Listener {
    private val animation = GachaAnimation(owner)
    fun close() = animation.closeAll()

    fun recoverApplications(player: Player) {
        check(FileManager.gacha.pendingApplications(player.uniqueId).size + FileManager.selections.pending(player.uniqueId).size <= 1)
        for (result in FileManager.gacha.pendingApplications(player.uniqueId)) {
            val ability = requireNotNull(AbilityManager.getAbilityByCodeName(result.terms.outcome.ability))
            require(ability.grade == Grade.TROLL && ability.restrictedSlot == null)
            AbilityManager.applyAbility(player, ability)
            FileManager.gacha.acknowledgeApplication(
                java.util.UUID.nameUUIDFromBytes("gacha-apply:${result.owner}:${result.id}".toByteArray(Charsets.UTF_8)),
                result.owner, result.id, ability.codeName)
        }
        for (result in FileManager.selections.pending(player.uniqueId)) {
            val ability = requireNotNull(AbilityManager.getAbilityByCodeName(result.terms.ability))
            require(ability.grade.name == result.terms.grade)
            val current = AbilityManager.getPlayerAbilityCodeName(player).orEmpty()
            // A consumed single-use ability leaves no ability; the paid selection still applies. Any other change is held
            // for an administrator (/ability set to the selected ID, then claim acknowledges) instead of kicking every login.
            if (current.isNotEmpty() && current != result.terms.previous && current != result.terms.ability) {
                plugin.logger.warning("Selection ${result.id} for ${player.uniqueId} held: current ability $current is neither ${result.terms.previous} nor ${result.terms.ability}")
                player.sendMessage(Component.text("선택한 능력 적용이 보류되었습니다. 관리자에게 거래 ${result.id}를 알려주세요.", NamedTextColor.YELLOW))
                continue
            }
            try {
                AbilityManager.applyAbility(player, ability)
            } catch (_: com.github.lupi13.limpi.delivery.InventoryFull) {
                // Nothing was prepared; the paid selection stays pending and must not block or kick the player.
                player.sendMessage(Component.text("선택한 능력을 적용하려면 인벤토리 공간을 확보한 뒤 /financial claim을 사용하세요.", NamedTextColor.YELLOW))
                continue
            }
            FileManager.selections.acknowledge(
                java.util.UUID.nameUUIDFromBytes("ability-select:${result.owner}:${result.id}".toByteArray(Charsets.UTF_8)),
                result.owner, result.id, ability.codeName)
        }
        AbilityManager.restoreActiveItem(player)
        com.github.lupi13.limpi.abilities.SuspiciousSugar.recover(player)
    }

    companion object {
        val plugin: Plugin = JavaPlugin.getPlugin(LIMPI::class.java)

        /**
         * 능력 뽑기권 아이템을 생성합니다.
         * @return 능력 뽑기권 아이템
         */
        fun getGachaTicketItem(): ItemStack {
            val item = ItemStack(Material.NETHER_STAR)
            val meta = item.itemMeta
            meta.itemName(Component.text("능력 뽑기권", NamedTextColor.GOLD))
            meta.lore(
                listOf(
                    Component.text("능력을 뽑을 수 있는 티켓입니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                    Component.text("손에 들고 우클릭하여 능력을 뽑습니다.", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                )
            )
            meta.itemFlags.add(ItemFlag.HIDE_ENCHANTS)
            item.itemMeta = meta
            return com.github.lupi13.limpi.platform.AbilityTickets.mark(item, "GACHA")
        }
        /**
         * 플레이어가 뽑기권을 사용하여 능력을 뽑을 때, 등급을 결정합니다. (픽업 계산 안함)
         * @param player 능력을 뽑는 플레이어
         * @return 뽑힌 능력의 등급
         */
        fun getGradeForGacha(player: Player): Grade {
            val policy = GachaPolicy(GachaConfiguration.settings(plugin.config))
            return Grade.valueOf(policy.grade(GachaConfiguration.state(FileManager.getPlayerData(player)), Random::nextDouble).name)
        }
    }


    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    fun onClickGacha(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return
        val player = event.player
        val hand = event.hand ?: return
        val item = player.inventory.getItem(hand)
        if (com.github.lupi13.limpi.platform.AbilityTickets.identify(item) != "GACHA") return
        event.isCancelled = true
        if (!player.isOnline || player.isDead || animation.active(player.uniqueId)) return
        if (!FileManager.delivery.claim(player)) return
        try {
            val config = FileManager.getPlayerData(player)
            val state = GachaConfiguration.state(config)
            val settings = GachaConfiguration.settings(owner.config)
            val eligible = AbilityManager.abilities.filter { it.acquisition == AcquisitionType.GACHA }.toList()
            val candidates = DrawGrade.entries.associateWith { grade -> eligible.filter { it.grade.name == grade.name }.map { it.codeName } }
            val pickupTime = AbilityPickUp.getRoundedTime()
            val pickups = mapOf(DrawGrade.LEGENDARY to AbilityPickUp.getLegendaryPickUp(pickupTime).codeName,
                DrawGrade.EPIC to AbilityPickUp.getEpicPickUp(pickupTime).codeName)
            val outcome = GachaPolicy(settings).draw(state, candidates, pickups, Random::nextDouble)
            val ability = requireNotNull(AbilityManager.getAbilityByCodeName(outcome.ability))
            val before = com.github.lupi13.limpi.platform.PaperItemCodec.snapshot(player, includeOffHand = true)
            val slot = if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) before.lastIndex else player.inventory.heldItemSlot
            val ticket = requireNotNull(before[slot])
            require(com.github.lupi13.limpi.platform.AbilityTickets.identify(
                com.github.lupi13.limpi.platform.PaperItemCodec.item(ticket.payload, ticket.count)) == "GACHA")
            val after = before.toMutableList().apply { this[slot] = if (ticket.count == 1) null else ticket.copy(count = ticket.count - 1) }.toList()
            val terms = GachaTerms(ticket.payload, slot, state, outcome, pickupTime, owner.config.getBoolean("ForceTroll") && outcome.grade == DrawGrade.TROLL)
            val id = java.util.UUID.randomUUID()
            if (!FileManager.delivery.remove(player, id, GachaSettlement.KIND, terms.encode(), before, after)) return
            val result = requireNotNull(FileManager.gacha.find(player.uniqueId, id))
            player.sendMessage(Component.text("뽑기 결과를 저장했습니다. 조회: /ability result ${result.id}", NamedTextColor.GREEN))
            if (!FileManager.delivery.claim(player)) return
            animation.start(player, ability, getGachaTicketItem())
        } catch (failure: Exception) {
            owner.logger.log(java.util.logging.Level.WARNING, "Gacha request failed for ${player.uniqueId}", failure)
            player.sendMessage(Component.text("뽑기를 완료하지 못했습니다. 저장된 거래 기록은 유지됩니다. 관리자에게 문의하세요.", NamedTextColor.RED))
        }
    }

    @EventHandler fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) = animation.close(event.player.uniqueId)
    @EventHandler fun onWorldChange(event: org.bukkit.event.player.PlayerChangedWorldEvent) = animation.close(event.player.uniqueId)
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: org.bukkit.event.entity.PlayerDeathEvent) = animation.close(event.entity.uniqueId)
}
