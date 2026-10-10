package com.github.lupi13.limpi.ops

import java.math.BigInteger
import java.util.UUID

data class CheckReport(val players: Int, val balanceSum: BigInteger, val pending: Int,
                       val errors: List<String>, val warnings: List<String>) {
    fun requireValid() = require(errors.isEmpty()) { errors.joinToString("\n") }
}

/** Offline structural preflight. Item NBT and live inventory proof still require Paper. */
object SnapshotCheck {
    private val schemas = setOf("delivery_pending", "delivery_done", "removal_pending", "removal_done",
        "ability_transition_pending", "ability_transition_done", "market_escrow_pending", "market_escrow_done",
        "market_listing", "check", "gacha_result", "ability_selection_result", "initial_rewards", "quest_reward")
    fun inspect(snapshot: DatabaseSnapshot): CheckReport {
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>()
        var players = 0; var sum = BigInteger.ZERO; var pending = 0
        val names = mutableMapOf<String, String>()
        for ((key, payload) in snapshot.documents) {
            val family = key.substringBefore('/')
            if (family.endsWith("_pending")) pending++
            try {
                if (family == "market_code") {
                    val listing = UUID.fromString(payload)
                    require("market_listing/$listing" in snapshot.documents) { "Missing indexed listing" }
                    continue
                }
                // Some application indices deliberately store a UUID or a scalar acknowledgement.
                val parsed = OperationsYaml.parse(payload)
                if (family == "player") {
                    UUID.fromString(key.substringAfter('/'))
                    val data = parsed as? Map<*, *> ?: error("Expected profile mapping")
                    val version = data["schemaVersion"]?.let { OperationsYaml.integer(it, "schemaVersion") } ?: 0L
                    require(version in 0..1) { "Unsupported player schema $version" }
                    val money = data["money"]?.let { OperationsYaml.integer(it, "money") } ?: 0L
                    if (data["money"] == null) warnings.add("$key: missing balance requires administrator recovery")
                    if (money < 0) warnings.add("$key: negative balance requires administrator recovery")
                    sum += BigInteger.valueOf(money); players++
                    data["name"]?.let {
                        require(it is String) { "Invalid player name" }
                        names.put(it.lowercase(java.util.Locale.ROOT), key)?.let { previous ->
                            warnings.add("$key: duplicate name also used by $previous")
                        }
                    }
                    data["stock"]?.let { raw ->
                        val stock = raw as? Map<*, *> ?: error("Invalid stock section")
                        stock.forEach { (code, holding) ->
                            val values = holding as? Map<*, *> ?: error("Invalid holding $code")
                            for (field in listOf("count", "costBasis", "averagePrice")) values[field]?.let {
                                require(OperationsYaml.integer(it, "stock.$code.$field") >= 0) { "Negative holding $field" }
                            }
                            values["realizedProfit"]?.let { OperationsYaml.integer(it, "realizedProfit") }
                        }
                    }
                } else if (parsed is Map<*, *>) {
                    if (family in schemas || parsed.containsKey("schemaVersion")) {
                        require(OperationsYaml.integer(parsed["schemaVersion"], "schemaVersion") == 1L) { "Unsupported document schema" }
                    }
                    if (parsed["state"] == "REVIEW") warnings.add("$key: REVIEW needs inventory evidence")
                    parsed["owner"]?.let { owner ->
                        require(owner is String); UUID.fromString(owner)
                        if (key.split('/').size == 3) require(key.split('/')[1] == owner) { "Owner/key mismatch" }
                    }
                    parsed["id"]?.let { id ->
                        require(id is String); UUID.fromString(id)
                        if (family in schemas && !family.startsWith("initial")) require(key.substringAfterLast('/') == id) { "ID/key mismatch" }
                    }
                } else if (family in schemas) error("Expected document mapping")
            } catch (failure: Exception) {
                // Do not echo the underlying parser's payload excerpt into terminal logs.
                errors.add("$key: ${failure.javaClass.simpleName} (invalid structure or value)")
            }
        }
        return CheckReport(players, sum, pending, errors, warnings)
    }
}
