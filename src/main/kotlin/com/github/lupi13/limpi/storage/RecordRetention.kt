package com.github.lupi13.limpi.storage

/** Which documents [StateStore.prune] may forget once their last write is older than RecordRetentionDays. */
object RecordRetention {
    val prefixes = setOf("delivery_done/", "removal_done/", "ability_transition_done/", "market_escrow_done/", "chest_absorbed/",
        "gacha_result/", "ability_selection_result/", "sugar_result/", "market_listing/", "check/")

    /**
     * Results stay while their application is pending, an unredeemed check stays forever (its record is what makes it
     * valid), and a listing stays while it can still be bought or returned.
     */
    fun stillUsed(key: String, payload: String, get: (String) -> String?): Boolean {
        fun pending(family: String) = get(family + "/" + key.substringAfter('/')) != null
        return when (key.substringBefore('/')) {
            "gacha_result" -> pending("gacha_apply_pending")
            "ability_selection_result" -> pending("ability_selection_pending")
            "sugar_result" -> pending("sugar_pending")
            "check" -> YamlFiles.parse(payload).getString("state") != "REDEEMED"
            "market_listing" -> YamlFiles.parse(payload).getString("state") !in setOf("SOLD", "RETURNED")
            else -> false
        }
    }
}
