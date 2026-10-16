package com.github.lupi13.limpi.config

import com.github.lupi13.limpi.abilities.AbilityManager
import com.github.lupi13.limpi.quests.QuestManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AbilityRegistryTest {
    @Test fun `ability registry can be constructed without a server plugin or starting any tasks`() {
        val abilities = AbilityManager.abilities
        assertEquals(74, abilities.size); assertEquals(74, abilities.map { it.codeName }.toSet().size)
        abilities.forEach { assertSame(it, AbilityManager.getAbilityByCodeName(it.codeName)) }
        assertNull(AbilityManager.getAbilityByCodeName("unknown"))
    }
    @Test fun `every configured ability has exactly one validated specification`() {
        assertEquals(AbilityManager.abilities.filter { it.needFile }.map { it.codeName }.toSet(), AbilitySpecs.all.keys)
    }
    @Test fun `quest and acquisition metadata resolve after registry construction without circular null links`() {
        AbilityManager.abilities.forEach { ability ->
            assertNotNull(ability.howToGet)
            ability.relatedQuest?.forEach { quest -> assertSame(quest, QuestManager.getQuestByCodeName(quest.codeName)) }
        }
        QuestManager.quests.forEach { assertSame(it.rewardAbility, AbilityManager.getAbilityByCodeName(it.rewardAbility.codeName)) }
    }
}
