package com.github.lupi13.limpi.quests

import com.github.lupi13.limpi.FileManager
import com.github.lupi13.limpi.abilities.AlchemyPlus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

object Lotto : Quest(
    displayText = Component.text("복권 당첨!", NamedTextColor.GOLD),
    codeName = "lotto",
    isHidden = false,
    howToGet = Component.text("연금술", NamedTextColor.GOLD)
        .append(Component.text(" 능력으로 ", NamedTextColor.WHITE))
        .append(Component.text("네더라이트 주괴", NamedTextColor.YELLOW))
        .append(Component.text("를 만드세요.", NamedTextColor.WHITE)),
    rewardAbility = AlchemyPlus
) {
    /** The settlement leaves permanent evidence so a crash before the notification cannot lose the unlock. */
    fun check(player: Player) {
        val evidence = FileManager.transactions.get("quest_trigger/${player.uniqueId}/lotto") ?: return
        require(java.util.UUID.fromString(evidence).toString() == evidence) { "Invalid lotto settlement evidence" }
        QuestManager.clearQuests(player, this)
    }
}
