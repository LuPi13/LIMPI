package com.github.lupi13.limpi.abilities

import com.github.lupi13.limpi.FileManager
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player

object AbilityManager {
    /**
     * 능력들의 리스트입니다.
     * 이 리스트는 플러그인 시작 시 초기화되어야 합니다.
     */
    val abilities = listOf(SafetyFirst)

    /**
     * 능력들을 등록합니다.
     * 이 메소드는 플러그인 시작 시 한 번만 호출되어야 합니다.
     */
    fun registerAbilities() {
        abilities.forEach { it.registerEvents() }
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
    fun setPlayerAbility(player: Player, codeName: String) {
        val config = FileManager.getPlayerData(player)
        config.set("ability", codeName)
        FileManager.savePlayerData(player, config)
    }

    /**
     * 플레이어의 attributes를 초기화합니다.
     */
    fun resetPlayerAttributes(player: Player) {
        for (attribute in Attribute.entries) {
            player.getAttribute(attribute)?.let {
                it.baseValue = it.defaultValue
            }
        }
    }
}