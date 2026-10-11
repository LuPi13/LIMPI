package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.platform.AbilityEffect
import com.github.lupi13.limpi.platform.AbilityRuntime
import com.github.lupi13.limpi.quests.MoebiusReactor
import com.github.lupi13.limpi.quests.Quest
import com.github.lupi13.limpi.quests.QuestManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

/**
 * Shared double-sneak cloak (source: GotchaAbility Cloak/SuperCloak).
 *
 * The gauge is kept in milli-points so per-tick drain/regen stays exact. Armor is never moved: instead of the
 * source's take-off/put-on, the armor display is faked away for viewers and the armor attributes are nullified
 * through the effect scope, so everything is restored on toggle-off, death, quit, world/ability change or disable.
 */
abstract class CloakAbility(
    grade: Grade,
    element: Element,
    displayName: Component,
    codeName: String,
    material: Material,
    description: List<Component>,
    acquisition: AcquisitionType = AcquisitionType.GACHA,
    relatedQuest: List<Quest>? = null,
) : Ability(
    grade = grade, element = element, displayName = displayName, codeName = codeName, material = material,
    description = description, acquisition = acquisition, relatedQuest = relatedQuest, needFile = true
) {
    protected class Session(val effect: AbilityEffect, val settings: com.github.lupi13.limpi.config.AbilitySettings) {
        var consumed = 0L
        var ticks = 0L
        var last: Location? = null
        var still = false
        var questChecked = false
    }

    /** Super cloak: standing still does not drain the gauge. */
    protected abstract val stillSaves: Boolean
    protected abstract val label: String

    private val gauge = mutableMapOf<UUID, Long>()
    private val sessions = mutableMapOf<UUID, Session>()
    private val lastSneak = mutableMapOf<UUID, Long>()
    private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    protected fun points(key: String): Long = Math.round(config!!.getDouble(key) * 1000.0)
    /** "per second" setting converted to milli-points per tick. */
    protected fun perTick(key: String): Long = Math.round(config!!.getDouble(key) * 1000.0 / 20.0)
    protected fun format(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    fun isCloaked(owner: UUID): Boolean = sessions[owner]?.effect?.scope?.closed == false
    protected fun sessionSettings(owner: UUID) = sessions[owner]?.settings ?: requireNotNull(config)

    protected fun addGauge(owner: UUID, amount: Long) {
        val current = gauge[owner] ?: return
        gauge[owner] = (current + amount).coerceAtMost(Math.round(sessionSettings(owner).getDouble("max_gauge") * 1000.0))
    }

    /** Hook called every cloaked tick after the drain was applied. */
    protected open fun afterDrain(player: Player, consumed: Long, markChecked: () -> Boolean) {}

    override fun start() {
        AbilityRuntime.global(1) {
            for (player in plugin.server.onlinePlayers) {
                if (player.ability === this) gauge.putIfAbsent(player.uniqueId, points("initial_gauge"))
                else if (player.uniqueId in gauge || player.uniqueId in sessions) release(player.uniqueId)
            }
            for (owner in gauge.keys.toList()) {
                val player = plugin.server.getPlayer(owner)
                if (player == null) { release(owner); continue }
                if (player.isDead || FileManager.delivery.blocked(owner)) continue
                val session = sessions[owner]
                if (session == null) {
                    val max = points("max_gauge")
                    val value = gauge[owner] ?: continue
                    if (value < max) gauge[owner] = (value + perTick("regen_per_second")).coerceAtMost(max)
                }
                display(player, gauge[owner] ?: 0L, session)
            }
        }
    }

    private fun display(player: Player, value: Long, session: Session?) {
        val max = points("max_gauge").coerceAtLeast(1L)
        val filled = (value * 10 / max).toInt().coerceIn(0, 10)
        val color = when {
            session == null -> NamedTextColor.YELLOW
            session.still -> NamedTextColor.DARK_AQUA
            else -> NamedTextColor.DARK_GREEN
        }
        player.sendActionBar(Component.text("$label Gauge 【${"■".repeat(filled)}${"□".repeat(10 - filled)}】 ${value / 1000}", color, TextDecoration.BOLD))
    }

    @EventHandler(ignoreCancelled = true)
    fun onDoubleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (!event.isSneaking || player.ability !== this || player.isDead || FileManager.delivery.blocked(player.uniqueId)) return
        val owner = player.uniqueId
        val now = System.currentTimeMillis()
        val previous = lastSneak.put(owner, now)
        if (previous == null || now - previous > config!!.getInt("double_sneak_ms")) return
        lastSneak.remove(owner)
        if (isCloaked(owner)) {
            sessions.remove(owner)?.effect?.close()
            player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1f)
            return
        }
        activate(player)
    }

    private fun activate(player: Player) {
        val owner = player.uniqueId
        val cost = points("activation_cost")
        val value = gauge.getOrPut(owner) { points("initial_gauge") }
        if (value < cost) return
        val effect = AbilityRuntime.begin(player, codeName)
        val session = Session(effect, requireNotNull(config))
        try {
            sessions[owner] = session
            effect.own { if (sessions[owner] === session) sessions.remove(owner); restoreArmor(owner) }
            if (config!!.getBoolean("disable_armor_stats")) {
                // Source took the armor off; armor stays equipped here, so its protection is nullified instead.
                effect.attribute(Attribute.ARMOR, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                effect.attribute(Attribute.ARMOR_TOUGHNESS, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
            }
            effect.repeat(0L, 1L) { tick(it, session) }
        } catch (failure: Exception) {
            effect.close()
            throw failure
        }
        gauge[owner] = value - cost
        session.consumed = cost
        player.playSound(player.location, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 2f)
    }

    private fun tick(effect: AbilityEffect, session: Session) {
        val config = session.settings
        val player = effect.player() ?: return effect.close()
        val owner = player.uniqueId
        val location = player.location
        val previous = session.last
        session.last = location
        session.still = stillSaves && previous != null && previous.world == location.world &&
            previous.distanceSquared(location) < config.getDouble("still_distance").let { it * it }
        if (!session.still) {
            val drain = Math.round(config.getDouble("drain_per_second") * 1000.0 / 20.0)
            val value = gauge[owner] ?: 0L
            if (drain > 0 && value < drain) {
                effect.close()
                player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1f)
                return
            }
            gauge[owner] = value - drain
            session.consumed += drain
        }

        effect.potion(player, PotionEffect(PotionEffectType.INVISIBILITY, 2, 0, true, false, true))
        if (config.getBoolean("hide_armor")) hideArmor(player)
        if (session.ticks % 20 == 0L) {
            val horizontal = config.getDouble("mob_reset_horizontal")
            val vertical = config.getDouble("mob_reset_vertical")
            player.getNearbyEntities(horizontal, vertical, horizontal)
                .filterIsInstance<Mob>()
                .filter { it.target?.uniqueId == owner }
                .forEach { it.target = null }
        }
        session.ticks++
        afterDrain(player, session.consumed) { if (session.questChecked) false else { session.questChecked = true; true } }
    }

    private fun hideArmor(player: Player) {
        val empty = armorSlots.associateWith { ItemStack.empty() }
        for (viewer in player.trackedBy) viewer.sendEquipmentChange(player, empty)
    }

    private fun restoreArmor(owner: UUID) {
        val player = plugin.server.getPlayer(owner) ?: return
        if (!player.isOnline) return
        val equipment = player.equipment
        val real = armorSlots.associateWith { equipment.getItem(it) }
        for (viewer in player.trackedBy) viewer.sendEquipmentChange(player, real)
    }

    /** Armor stays equipped, which raises vanilla mob detection; keep the source's bare-body stealth instead. */
    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val target = event.target as? Player ?: return
        if (event.reason != EntityTargetEvent.TargetReason.CLOSEST_PLAYER || !isCloaked(target.uniqueId)) return
        if (target.ability !== this) return
        val range = (sessions[target.uniqueId]?.settings ?: config)!!.getDouble("mob_detect_range")
        val from = event.entity.location
        if (from.world != target.world || from.distanceSquared(target.location) > range * range) event.isCancelled = true
    }

    override fun release(owner: UUID) {
        sessions.remove(owner)?.effect?.close()
        gauge.remove(owner)
        lastSneak.remove(owner)
    }

    override fun stop() {
        sessions.values.toList().forEach { it.effect.close() }
        sessions.clear()
        gauge.clear()
        lastSneak.clear()
    }
}

object Cloak : CloakAbility(
    grade = Grade.LEGENDARY,
    element = Element.UTILITY,
    displayName = Component.text("은폐", NamedTextColor.DARK_GREEN),
    codeName = "cloak",
    material = Material.MUSIC_DISC_CAT,
    description = listOf(
        Component.text("빠르게 두 번 웅크리면 투명화를 시작/해제합니다.", NamedTextColor.WHITE),
        Component.text("투명화 중에는 갑옷이 보이지 않으며,", NamedTextColor.WHITE),
        Component.text("은폐 게이지를 소모합니다.", NamedTextColor.WHITE)
    ),
    relatedQuest = listOf(MoebiusReactor)
) {
    override val stillSaves = false
    override val label = "Cloak"

    override val details: List<Component> by settingsCached {
        listOf(
            Component.text("빠르게 두 번 웅크리면 ", NamedTextColor.WHITE)
                .append(Component.text("투명화", NamedTextColor.BLUE))
                .append(Component.text("를 시작/해제합니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE)
                .append(Component.text(" 중에는 ", NamedTextColor.WHITE))
                .append(Component.text("갑옷", NamedTextColor.YELLOW))
                .append(Component.text("이 보이지 않고 방어력이 적용되지 않습니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE)
                .append(Component.text(" 중에는 은폐 게이지(최대 ", NamedTextColor.WHITE))
                .append(Component.text(format(config!!.getDouble("max_gauge")), NamedTextColor.GREEN))
                .append(Component.text(")가 줄고, 해제하면 다시 찹니다.", NamedTextColor.WHITE)),
            Component.text("투명화", NamedTextColor.BLUE)
                .append(Component.text(" 중에는 주변 몹이 나를 노리지 못합니다.", NamedTextColor.WHITE))
        )
    }

    override fun afterDrain(player: Player, consumed: Long, markChecked: () -> Boolean) {
        if (consumed < Math.round(MoebiusReactor.REQUIRED_GAUGE * 1000.0) || !markChecked()) return
        QuestManager.clearQuests(player, MoebiusReactor)
    }
}
