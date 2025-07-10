package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
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
import org.bukkit.scheduler.BukkitRunnable
import java.io.File

class AbilityManager : Listener {
    private var plugin: Plugin = getPlugin(LIMPI::class.java)
    /**
     * 능력들의 리스트입니다.
     * 이 리스트는 플러그인 시작 시 초기화되어야 합니다.
     */
    val abilities = listOf(
        SafetyFirst, LetsBoom, DoubleExp, TerraBurning, ExpRich, Dodge, Alchemy, LongThrow, HeavyEnchant,
        PickPocket, EmeraldFix, CookieRun, AdaptiveDefense, LittleEat, HotPickaxe, EntityThrower, WebShooter,
        FriendlyUndead, FriendlyCreeper, WaterMeleeBoost, HeavySnowball, SuspiciousSugar, KneePropel,
        ShieldStrike, PullingBow, UpperCut, DefenseAllIn, KillingDice, GatheringStorm, ImpactLanding,
        BladeLeaper, ChainLightning, OverBalance, JudgementRay
        )

    /**
     * 능력들의 activeItem을 모은 리스트입니다.
     */
    val activeItems by lazy { abilities.mapNotNull { it.activeItem } }
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
    @EventHandler
    fun onClickInventory(event: InventoryClickEvent) {
        // tq 이거 왜 GUI에서 F키 눌러서 swap하는거 안막히냐
        if (event.cursor in activeItems || event.currentItem in activeItems) {
            event.isCancelled = true
            return
        }

        val player = event.whoClicked as? Player ?: return
        val ability = player.ability ?: return
        val activeItem = ability.activeItem ?: return
        val restrictedSlot = ability.restrictedSlot ?: return

        if (event.action == InventoryAction.HOTBAR_SWAP && event.hotbarButton == -1) {
            player.sendMessage(Component.text("인벤토리 내에서 ", NamedTextColor.RED)
                .append(Component.keybind().keybind("key.swapOffhand").color(NamedTextColor.RED))
                .append(Component.text("키를 이용한 아이템 이동을 자제해 주세요! 고스트 아이템 현상 등의 문제가 발생할 수 있습니다.", NamedTextColor.RED)))
            player.sendMessage(Component.text("문제가 발생했다면, 인벤토리 내에서 아무렇게 클릭 몇 번 하면 복구될 수 있습니다.", NamedTextColor.RED))
        }

        // restrictedSlot이 존재하는 능력 소유자의 경우, 인벤토리 클릭때마다 인벤토리를 업데이트
        // 이거 말고 방법이 없음. 이거도 고스트 아이템 생기는데 해결은 됨
        if (player.inventory.getItem(restrictedSlot) != null && player.inventory.getItem(restrictedSlot)!!.isSimilar(activeItem)) {
            for (i in 0..40) {
                if (i == restrictedSlot) continue
                // activeItem이 restrictedSlot이 아닌 곳에 있는 경우 제거
                if (player.inventory.getItem(i) in activeItems) {
                    player.inventory.setItem(i, null)
                }
            }
        }
        // restrictedSlot에 activeItem이 아닌 다른 아이템이 있는 경우, 해당 아이템을 플레이어 인벤토리에 추가
        if (player.inventory.getItem(restrictedSlot) != null && !player.inventory.getItem(restrictedSlot)!!.isSimilar(activeItem)) {
            player.inventory.getItem(restrictedSlot)?.let { player.inventory.addItem(it) }
        }
        // restrictedSlot에 activeItem을 설정
        player.inventory.setItem(restrictedSlot, activeItem)

        // 1틱 후 인벤토리 업데이트
        if (player.gameMode != GameMode.CREATIVE) {
            object: BukkitRunnable() {
                override fun run() {
                    player.updateInventory()
                }
            }.runTaskLater(plugin, 1L)
        }
    }
    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (event.player.inventory.itemInOffHand in activeItems) {
            event.isCancelled = true
        }
    }
    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.cursor == null || event.cursor !in activeItems) return
        if (event.oldCursor !in activeItems) return
        event.isCancelled = true
    }
    @EventHandler
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack !in activeItems) return
        event.isCancelled = true
    }

    /**
     * 플레이어가 죽으면 드랍 아이템에서 restrictItem을 제거하고, 부활 시 다시 장착시킵니다.
     */
    @EventHandler
    fun onDropItem(event: ItemSpawnEvent) {
        if (event.entity.itemStack !in activeItems) return
        event.entity.remove()
        event.isCancelled = true
    }
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val ability = player.ability
        if (ability == null) return
        if (ability.activeItem == null) return
        // 플레이어가 능력을 가지고 있다면, 해당 능력의 activeItem을 장착합니다.
        player.inventory.setItem(ability.restrictedSlot!!, ability.activeItem)
    }

    /**
     * 능력들을 등록합니다.
     * 이 메소드는 플러그인 시작 시 한 번만 호출되어야 합니다.
     */
    fun registerAbilities() {
        abilities.forEach { it.registerEvents() }
    }

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
        return abilities.find { it.codeName == codeName }
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
        val config = FileManager.getPlayerData(player)
        return config.getString("ability")
    }

    /**
     * 플레이어의 능력을 codeName으로 저장합니다.
     * @param player 능력을 저장할 플레이어
     * @param codeName 저장할 능력의 codeName(String)
     */
    fun setPlayerAbilityCodeName(player: Player, codeName: String) {
        val config = FileManager.getPlayerData(player)
        config.set("ability", codeName)
        FileManager.savePlayerData(player, config)
    }

    /**
     * 플레이어에게 능력을 적용합니다.
     * restrictedSlot 등을 고려합니다.
     * @param player 능력을 적용할 플레이어
     * @param ability 적용할 능력의 Ability
     */
    fun applyAbility(player: Player, ability: Ability?) {
        val currentAbility = getAbilityByCodeName(getPlayerAbilityCodeName(player) ?: "")
        if (ability == null) {
            player.sendMessage(Component.text("능력이 제거되었습니다.", NamedTextColor.WHITE))
            if (currentAbility?.restrictedSlot != null) player.inventory.setItem(currentAbility.restrictedSlot!!, null)
            setPlayerAbilityCodeName(player, "")
            return
        }


        if (getPlayerAbilityCodeName(player) == ability.codeName) return // 이미 적용된 능력은 무시

        // 현재 적용된 능력이 있었다면 해당 능력을 비활성화합니다.
        if (currentAbility != null) {
            // 현재 적용된 능력이 restrictedSlot을 가지고 있다면 해당 슬롯을 비웁니다.
            if (currentAbility.restrictedSlot != null) {
                player.inventory.setItem(currentAbility.restrictedSlot!!, null)
            }
        }

        // 새로운 능력을 적용합니다.
        // 새로운 능력이 restrictedSlot을 가지고 있다면 해당 슬롯에 능력을 적용합니다.
        if (ability.restrictedSlot != null) {
            player.inventory.setItem(ability.restrictedSlot!!, ability.activeItem)
        }

        setPlayerAbilityCodeName(player, ability.codeName)
        player.sendMessage(
            Component.text("능력 ", NamedTextColor.WHITE)
                .append(ability.displayName)
                .append(Component.text("이(가) 적용되었습니다!", NamedTextColor.WHITE))
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2F)
    }

    /**
     * 플레이어에게 능력을 해금시킵니다.
     *
     * @param player 능력을 해금할 플레이어
     * @param ability 해금할 능력의 Ability
     */
    fun unlockAbility(player: Player, ability: Ability) {
        val config = FileManager.getPlayerData(player)
        val unlockedAbilities = config.getStringList("unlockedAbilities") ?: mutableListOf()
        if (!unlockedAbilities.contains(ability.codeName)) {
            unlockedAbilities.add(ability.codeName)
            config.set("unlockedAbilities", unlockedAbilities)
            FileManager.savePlayerData(player, config)
            player.sendMessage(Component.text("능력 ", NamedTextColor.WHITE)
                .append(ability.displayName)
                .append(Component.text("이(가) 해금되었습니다!", NamedTextColor.WHITE)))
        }
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
            if (ability.howToGet != Component.text("뽑기로 획득")) continue
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
        Bat::class.java, SchoolableFish::class.java, Camel::class.java, Sniffer::class.java, Armadillo::class.java
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
        // 플레이어의 설정에서 해당 엔티티를 적으로 간주하는지 확인합니다.
        val config = FileManager.getPlayerData(player)

        var result = true

        // 플레이어
        if (entity is Player) {
            val targetLevel = config.getInt("AbilityEnemy.PLAYER", 1)
            val teams = player.scoreboard.teams
            var isTargetSameTeam = false
            if (entity == player) {
                isTargetSameTeam = true // 자기 자신은 항상 같은 팀으로 간주
            } else {
                // 플레이어와 플레이어 팀이 같은 팀에 속하는지 확인
                for (team in teams) {
                    if (team.entries.contains(entity.name) && team.hasEntry(player.name)) {
                        isTargetSameTeam = true
                        break
                    }
                }
            }
            result = when (targetLevel) {
                0 -> false // 적으로 간주하지 않음
                1 -> !isTargetSameTeam // 같은 팀에 속하지 않는 플레이어만 적으로 간주함
                2 -> true // 플레이어는 항상 적으로 간주함
                else -> true // 기본값은 적으로 간주함
            }
            if (entity == player) result = false
        }

        // 비공격적
        else if (nonAggressive.any { it.isInstance(entity) }) {
            val targetLevel = config.getInt("AbilityEnemy.NON_AGGRESSIVE", 2)
            val isTargetingPlayer = (entity is Mob) && entity.target == player
            result =  when (targetLevel) {
                0 -> false // 적으로 간주하지 않음
                1 -> isTargetingPlayer // 플레이어를 공격하는 비공격적 몹만 적으로 간주함
                2 -> true // 비공격적 몹도 적으로 간주함
                else -> true // 기본값은 적으로 간주함
            }
        }

        // 중립적
        else if (neutral.any { it.isInstance(entity) }) {
            val targetLevel = config.getInt("AbilityEnemy.NEUTRAL", 2)
            val isTargetingPlayer = (entity is Mob) && entity.target == player
            result = when (targetLevel) {
                0 -> false // 적으로 간주하지 않음
                1 -> isTargetingPlayer // 플레이어를 공격하는 중립 몹만 적으로 간주함
                2 -> true // 중립 몹도 적으로 간주함
                else -> true // 기본값은 적으로 간주함
            }
        }

        // 공격적
        else if (aggressive.any { it.isInstance(entity) }) {
            val targetLevel = config.getInt("AbilityEnemy.AGGRESSIVE", 2)
            val isTargetingPlayer = (entity is Mob) && entity.target == player
            result = when (targetLevel) {
                0 -> false // 적으로 간주하지 않음
                1 -> isTargetingPlayer // 플레이어를 공격하는 공격적 몹만 적으로 간주함
                2 -> true // 공격적 몹도 적으로 간주함
                else -> true // 기본값은 적으로 간주함
            }
        }

        // Tameable 예외
        if (entity is Tameable && entity.isTamed) {
            val targetLevel = config.getInt("AbilityEnemy.TAMED", 2)
            val playerTeam = player.scoreboard.teams
            val tamer = entity.owner
            if (tamer !is Player) return true
            val teams = player.scoreboard.teams
            var isTargetSameTeam = false
            if (tamer == player) {
                isTargetSameTeam = true // 자기 자신은 항상 같은 팀으로 간주
            } else {
                // 플레이어와 플레이어 팀이 같은 팀에 속하는지 확인
                for (team in teams) {
                    if (team.entries.contains(tamer.name) && team.hasEntry(player.name)) {
                        isTargetSameTeam = true
                        break
                    }
                }
            }
            result = when (targetLevel) {
                0 -> !((entity.owner is Player || entity.owner is OfflinePlayer))
                1 -> !isTargetSameTeam // 플레이어와 플레이어 팀이 소유한 길들여진 몹은 적으로 간주하지 않음
                2 -> true // 길들여진 몹도 적으로 간주함
                else -> true // 기본값은 적으로 간주함
            }
        }

        return result
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
