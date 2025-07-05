package com.github.lupi13.limpi.events

import com.github.lupi13.limpi.LIMPI
import com.github.lupi13.limpi.abilities.Ability
import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.abilities.Grade
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin.getPlugin
import java.util.Collections

class AbilityPickUp {
    companion object {
        private var plugin: Plugin = getPlugin(LIMPI::class.java)

        /**
         * seed를 기반으로 LEGENDARY 등급 능력을 랜덤하게 선택하여 반환합니다.
         * @param seed 랜덤 시드
         * @return LEGENDARY 등급 능력
         */
        fun getLegendaryPickUp(seed: Long): Ability {
            val legendaryAbilities = AbilityManager().abilities.filter { it.grade == Grade.LEGENDARY }
            val random = java.util.Random(seed)
            Collections.shuffle(legendaryAbilities, random)
            return legendaryAbilities.firstOrNull()!!
        }

        /**
         * seed를 기반으로 EPIC 등급 능력을 랜덤하게 선택하여 반환합니다.
         * @param seed 랜덤 시드
         * @return EPIC 등급 능력
         */
        fun getEpicPickUp(seed: Long): Ability {
            val epicAbilities = AbilityManager().abilities.filter { it.grade == Grade.EPIC }
            val random = java.util.Random(seed)
            Collections.shuffle(epicAbilities, random)
            return epicAbilities.firstOrNull()!!
        }

        /**
         * config파일의 PickUpChange(분)으로 내림합니다.
         * 예) PickUpChange가 10분이면, 현재 시간을 10분 단위로 내림림합니다.
         * @return 현재 시간 기반의 seed
         */
        fun getRoundedTime(): Long {
            val currentTime = System.currentTimeMillis()
            val pickUpChangeMinutes = plugin.config.getInt("PickUpChange")
            val pickUpChangeMillis = pickUpChangeMinutes * 60 * 1000L
            return (currentTime / pickUpChangeMillis) * pickUpChangeMillis
        }

        /**
         * currentTimeMillis를 받아 현재 날짜, 시간을 텍스트로 반환합니다.
         * @param currentTimeMillis 현재 시간 밀리초
         * @return 시간 텍스트
         */
        fun getTimeText(currentTimeMillis: Long): String {
            val date = java.util.Date(currentTimeMillis)
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            return formatter.format(date)
        }
    }
}