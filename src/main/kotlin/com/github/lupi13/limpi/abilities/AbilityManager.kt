package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.Functions
import com.github.lupi13.limpi.LIMPI
import io.papermc.paper.entity.SchoolableFish
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin.getPlugin
import java.io.File

object AbilityManager : Listener {
    private lateinit var plugin: Plugin
    fun initialize(owner: Plugin) {
        check(!::plugin.isInitialized || plugin === owner || !plugin.isEnabled)
        plugin = owner
        abilities.forEach { it.bind(owner) }
    }
    /**
     * 능력들의 리스트입니다.
     * 이 리스트는 플러그인 시작 시 초기화되어야 합니다.
     */
    val abilities = listOf(
        SafetyFirst, LetsBoom, DoubleExp, TerraBurning, ExpRich, Dodge, Alchemy, LongThrow, HeavyEnchant,
        PickPocket, EmeraldFix, CookieRun, AdaptiveDefense, LittleEat, HotPickaxe, EntityThrower, WebShooter,
        FriendlyUndead, FriendlyCreeper, WaterMeleeBoost, HeavySnowball, SuspiciousSugar, KneePropel,
        ShieldStrike, PullingBow, UpperCut, DefenseAllIn, KillingDice, GatheringStorm, ImpactLanding,
        BladeLeaper, ChainLightning, OverBalance, JudgementRay, Muspell, BlunderBooster, ZombieKing,
        HarmonySword, HarmonyArrow, Transcendence, CometStrike, Titan
        )

    /**
     * 능력들의 activeItem을 모은 리스트입니다.
     */
    val activeItems by lazy { abilities.mapNotNull { it.activeItem } }
    private val abilitiesByCode by lazy {
        abilities.associateBy { it.codeName }.also {
            check(it.size == abilities.size) { "Ability code names must be unique" }
        }
    }
    /**
     * activeItem의 상호작용을 제거합니다.
     */
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        for (active in activeItems) {
            if (active.isSimilar(event.item)) {
                event.isCancelled = true
                return
            }
        }
    }
    private fun active(item: ItemStack?): Boolean = item != null && (activeItems.any { it.isSimilar(item) } || item.isSimilar(EntityThrower.getRestrictHand()))

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onClickInventory(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val swapped = if (event.click == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) player.inventory.itemInOffHand
            else if (event.hotbarButton in 0..8) player.inventory.getItem(event.hotbarButton) else null
        val restricted = player.ability?.restrictedSlot
        val clickedRestricted = event.clickedInventory === player.inventory && event.slot == restricted
        if (active(event.cursor) || active(event.currentItem) || active(swapped) || clickedRestricted) event.isCancelled = true
    }
    /** Allays, item frames and armor stands take the interacting hand's item; never let an ability item leave that way. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    fun onInteractEntity(event: org.bukkit.event.player.PlayerInteractEntityEvent) {
        if (active(event.player.inventory.getItem(event.hand))) event.isCancelled = true
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (active(event.mainHandItem) || active(event.offHandItem) || event.player.ability?.restrictedSlot == 40) event.isCancelled = true
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val restricted = player.ability?.restrictedSlot
        if (active(event.oldCursor) || event.rawSlots.any { event.view.getInventory(it) === player.inventory && event.view.convertSlot(it) == restricted }) {
            event.isCancelled = true
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        if (active(event.itemDrop.itemStack)) event.isCancelled = true
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDropItem(event: ItemSpawnEvent) {
        if (!active(event.entity.itemStack)) return
        event.entity.remove()
        event.isCancelled = true
    }
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline && !player.isDead) restoreActiveItem(player) })
    }

    fun restoreActiveItem(player: Player) {
        if (FileManager.delivery.blocked(player.uniqueId)) return
        if (player.uniqueId !in EntityThrower.heldEntity) EntityThrower.clearRestrictHand(player)
        val ability = player.ability ?: return
        if (ability.restrictedSlot == null || ability.activeItem == null) return
        val before = com.github.lupi13.limpi.platform.PaperItemCodec.snapshot(player, true)
        try {
            val terms = transitionTerms(player, ability)
            if (terms.plan(before) != before) FileManager.delivery.transition(player, terms, before)
        } catch (_: com.github.lupi13.limpi.delivery.InventoryFull) {
            player.sendMessage(Component.text("능력 아이템을 장착하려면 인벤토리 공간을 확보하세요.", NamedTextColor.YELLOW))
        }
    }

    /**
     * 능력들을 등록합니다.
     * 이 메소드는 플러그인 시작 시 한 번만 호출되어야 합니다.
     */
    fun registerAbilities() {
        abilities.forEach { it.registerEvents() }
        abilities.forEach { it.start() }
    }

    fun releaseState(owner: java.util.UUID) = abilities.forEach { it.release(owner) }
    fun shutdown() = abilities.forEach { it.stop() }
    @EventHandler fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) = releaseState(event.player.uniqueId)
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: org.bukkit.event.entity.PlayerDeathEvent) = releaseState(event.entity.uniqueId)
    @EventHandler fun onWorldChange(event: org.bukkit.event.player.PlayerChangedWorldEvent) = releaseState(event.player.uniqueId)

    /**
     * 능력 파일을 설정합니다.
     * 이 메소드는 플러그인 시작 시 한 번만 호출되어야 합니다.
     */
    fun setupAbilityFiles() {
        if (!File(plugin.dataFolder.toString() + File.separator + "ability").exists()) {
            File(plugin.dataFolder.toString() + File.separator + "ability").mkdirs()
        }
        abilities.forEach { it.setUpFile() }
    }

    /**
     * 능력의 코드네임으로 능력을 찾습니다.
     * @param codeName 능력의 코드네임
     * @return 해당 코드네임을 가진 Ability, 없으면 null
     */
    fun getAbilityByCodeName(codeName: String): Ability? {
        return abilitiesByCode[codeName]
    }

    /**
     * 능력의 아이템으로 능력을 찾습니다.
     * @param item 능력의 아이템
     * @return 해당 아이템을 가진 Ability, 없으면 null
     */
    fun getAbilityByItem(item: ItemStack): Ability? {
        return abilities.find { it.getItem().isSimilar(item) }
    }

    /**
     * 플레이어의 능력을 codeName으로 불러옵니다.
     * @param player 능력을 불러올 플레이어
     * @return 해당 플레이어의 능력의 codeName(String), 없으면 null
     */
    fun getPlayerAbilityCodeName(player: Player): String? {
        return FileManager.players.abilityCode(player.uniqueId)
    }

    /**
     * 플레이어의 능력을 codeName으로 저장합니다.
     * @param player 능력을 저장할 플레이어
     * @param codeName 저장할 능력의 codeName(String)
     */

    /**
     * 플레이어에게 능력을 적용합니다.
     * restrictedSlot 등을 고려합니다.
     * @param player 능력을 적용할 플레이어
     * @param ability 적용할 능력의 Ability
     */
    fun planInventory(player: Player, ability: Ability?, before: List<com.github.lupi13.limpi.delivery.StoredStack?>): List<com.github.lupi13.limpi.delivery.StoredStack?> {
        return transitionTerms(player, ability).plan(before)
    }

    private fun transitionTerms(player: Player, ability: Ability?): com.github.lupi13.limpi.ability.TransitionTerms {
        val previous = player.ability
        fun logical(slot: Int?): Int? = slot?.let {
            require(it in 0..35 || it == 40) { "Unsupported ability slot $it" }
            if (it == 40) 36 else it
        }
        val nextItem = ability?.activeItem?.let {
            com.github.lupi13.limpi.delivery.StoredStack(com.github.lupi13.limpi.platform.PaperItemCodec.payload(it),
                it.amount, minOf(it.maxStackSize, player.inventory.maxStackSize))
        }
        return com.github.lupi13.limpi.ability.TransitionTerms(getPlayerAbilityCodeName(player).orEmpty(), ability?.codeName.orEmpty(),
            logical(previous?.restrictedSlot), previous?.activeItem?.let(com.github.lupi13.limpi.platform.PaperItemCodec::payload),
            if (nextItem == null) null else logical(ability.restrictedSlot), nextItem)
    }

    fun applyAbility(player: Player, ability: Ability?, preserveEffect: java.util.UUID? = null) {
        if (FileManager.delivery.blocked(player.uniqueId)) error("Ability application is blocked during item recovery")
        if (getPlayerAbilityCodeName(player).orEmpty() == ability?.codeName.orEmpty()) return
        val before = com.github.lupi13.limpi.platform.PaperItemCodec.snapshot(player, true)
        val terms = transitionTerms(player, ability)
        if (!FileManager.delivery.transition(player, terms, before)) throw IllegalStateException("Ability transition requires recovery")
        com.github.lupi13.limpi.platform.AbilityRuntime.closeOwner(player.uniqueId, preserveEffect)
        releaseState(player.uniqueId)
        Functions.resetPlayerAttributes(player)
        player.sendMessage(if (ability == null) Component.text("능력이 제거되었습니다.", NamedTextColor.WHITE)
            else Component.text("능력 ", NamedTextColor.WHITE).append(ability.displayName)
                .append(Component.text("이(가) 적용되었습니다!", NamedTextColor.WHITE)))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
    }

    /**
     * Grade에 맞는 뽑기로 뽑을 수 있는 랜덤 능력을 반환합니다.
     * @param grade 뽑을 능력의 등급
     * @return 뽑을 수 있는 랜덤 능력
     */
    fun getRandomAbilityByGrade(grade: Grade): Ability? {
        val shuffledAbilities = abilities.shuffled()

        for (ability in shuffledAbilities) {
            if (ability.grade != grade) continue
            if (ability.acquisition != AcquisitionType.GACHA) continue
            return ability
        }
        return null
    }


    /**
     * 플레이어 설정에서 해당 엔티티를 적으로 간주하는지 확인합니다.
     * @param player 플레이어
     * @param entity 적인지 확인할 엔티티
     * @return 적이면 true, 아니면 false
     */
    //개노가다 시작, 고마워요 꺼무위키!
    val nonAggressive: List<Class<out Damageable>> = listOf(
        Pig::class.java, Sheep::class.java, AbstractCow::class.java, Chicken::class.java,
        Ocelot::class.java, Cat::class.java, AbstractVillager::class.java, Horse::class.java,
        Donkey::class.java, Mule::class.java, SkeletonHorse::class.java, ZombieHorse::class.java,
        Rabbit::class.java, Turtle::class.java, Fox::class.java, Strider::class.java,
        Axolotl::class.java, Frog::class.java, Tadpole::class.java, Allay::class.java,
        Parrot::class.java, Squid::class.java, GlowSquid::class.java, Snowman::class.java,
        Bat::class.java, SchoolableFish::class.java, Camel::class.java, Sniffer::class.java, Armadillo::class.java,
        HappyGhast::class.java
    )
    val neutral: List<Class<out Damageable>> = listOf(
        Spider::class.java, CaveSpider::class.java, Wolf::class.java, Enderman::class.java,
        IronGolem::class.java, Llama::class.java, TraderLlama::class.java, Panda::class.java,
        Bee::class.java, Goat::class.java, PolarBear::class.java, Dolphin::class.java,
        PigZombie::class.java
    )
    val aggressive: List<Class<out Damageable>> = listOf(
        Zombie::class.java, Skeleton::class.java, Creeper::class.java, Slime::class.java,
        Ghast::class.java, Silverfish::class.java, Blaze::class.java, MagmaCube::class.java,
        Witch::class.java, PufferFish::class.java, Guardian::class.java, ElderGuardian::class.java,
        Endermite::class.java, Shulker::class.java, WitherSkeleton::class.java, Stray::class.java,
        ZombieVillager::class.java, Husk::class.java, Drowned::class.java, Vindicator::class.java,
        Evoker::class.java, Vex::class.java, Pillager::class.java, Ravager::class.java, Phantom::class.java,
        Piglin::class.java, PiglinBrute::class.java, Hoglin::class.java, Zoglin::class.java,
        Warden::class.java, Breeze::class.java, Bogged::class.java, Creaking::class.java,
        EnderDragon::class.java, Wither::class.java
    )
    fun isEnemy(player: Player, entity: Damageable): Boolean {
        if (entity.uniqueId == player.uniqueId || entity.world.uid != player.world.uid) return false
        val settings = com.github.lupi13.limpi.combat.EnemySettings.read(FileManager.getPlayerData(player))
        fun sameTeam(name: String?): Boolean = name != null && player.scoreboard.getEntryTeam(player.name)?.hasEntry(name) == true
        if (entity is Tameable && entity.isTamed) {
            val owner = entity.owner
            val same = owner?.uniqueId == player.uniqueId || sameTeam(owner?.name)
            return com.github.lupi13.limpi.combat.EnemyPolicy.hostile(com.github.lupi13.limpi.combat.TargetGroup.TAMED,
                settings.mode(com.github.lupi13.limpi.combat.TargetGroup.TAMED), sameTeam = same, hasPlayerOwner = owner != null)
        }
        val group = when {
            entity is Player -> com.github.lupi13.limpi.combat.TargetGroup.PLAYER
            nonAggressive.any { it.isInstance(entity) } -> com.github.lupi13.limpi.combat.TargetGroup.NON_AGGRESSIVE
            neutral.any { it.isInstance(entity) } -> com.github.lupi13.limpi.combat.TargetGroup.NEUTRAL
            aggressive.any { it.isInstance(entity) } -> com.github.lupi13.limpi.combat.TargetGroup.AGGRESSIVE
            else -> null
        }
        return com.github.lupi13.limpi.combat.EnemyPolicy.hostile(group, group?.let(settings::mode) ?: 2,
            sameTeam = (entity as? Player)?.let { sameTeam(it.name) } ?: false,
            targetingOwner = (entity as? Mob)?.target?.uniqueId == player.uniqueId)
    }


    /**
     * 남은 쿨타임을 actionbar로 표시합니다.
     * @param player 쿨타임을 표시할 플레이어
     * @param cooldown 쿨타임 in milliseconds
     * @param lastUsed 마지막 사용 시간 in milliseconds
     */
    fun showCooldown(player: Player, cooldown: Int, lastUsed: Long) {
        val text = Component.text("남은 쿨타임: ", NamedTextColor.WHITE)
            .append(
                Component.text(
                    String.format("%.2f", (cooldown - (System.currentTimeMillis() - lastUsed)) / 1000.0),
                    NamedTextColor.RED
                )
            )
            .append(Component.text("초", NamedTextColor.WHITE))
        player.sendActionBar(text)
    }
    fun showCooldown(player: Player, material: Material) {
        val cooldown = player.getCooldown(material)

        val text = Component.text("남은 쿨타임: ", NamedTextColor.WHITE)
            .append(
                Component.text(
                    String.format("%.2f", cooldown / 20.0),
                    NamedTextColor.RED
                )
            )
            .append(Component.text("초", NamedTextColor.WHITE))
        player.sendActionBar(text)
    }
}
