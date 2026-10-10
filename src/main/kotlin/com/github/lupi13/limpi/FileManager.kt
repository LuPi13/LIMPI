package com.github.lupi13.limpi

import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.delivery.DeliveryJournal
import com.github.lupi13.limpi.delivery.DeliveryService
import com.github.lupi13.limpi.delivery.RemovalJournal
import com.github.lupi13.limpi.economy.SaleSettlement
import com.github.lupi13.limpi.economy.ItemExchange
import com.github.lupi13.limpi.gacha.GachaSettlement
import com.github.lupi13.limpi.ability.AbilitySelection
import com.github.lupi13.limpi.ability.AbilityItemUse
import com.github.lupi13.limpi.ability.AbilityTransitions
import com.github.lupi13.limpi.ability.InitialRewards
import com.github.lupi13.limpi.ability.InitialTicketCounts
import com.github.lupi13.limpi.quest.QuestRewards
import com.github.lupi13.limpi.platform.DeliveryController
import com.github.lupi13.limpi.platform.PaperItemCodec
import com.github.lupi13.limpi.market.*
import com.github.lupi13.limpi.checks.CheckService
import com.github.lupi13.limpi.economy.ShopService
import com.github.lupi13.limpi.platform.PaperShopCatalog
import org.bukkit.inventory.ItemStack
import com.github.lupi13.limpi.storage.*
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Level

/** Compatibility facade while feature services replace direct YAML access. */
class FileManager {
    companion object {
        private lateinit var plugin: Plugin
        private lateinit var repository: PlayerRepository
        private lateinit var state: StateStore
        private lateinit var bankService: BankService
        private lateinit var deliveryJournal: DeliveryJournal
        private lateinit var deliveryController: DeliveryController
        private lateinit var marketCatalog: MarketCatalog
        private lateinit var marketService: MarketService
        private lateinit var marketEscrow: MarketEscrow
        private lateinit var checkService: CheckService
        private lateinit var shopService: ShopService
        private lateinit var removalJournal: RemovalJournal
        private lateinit var itemExchange: ItemExchange
        private lateinit var gachaSettlement: GachaSettlement
        private lateinit var abilitySelection: AbilitySelection
        private lateinit var abilityTransitions: AbilityTransitions
        private lateinit var initialRewards: InitialRewards
        private lateinit var initialItems: List<com.github.lupi13.limpi.delivery.ItemGrant>
        private lateinit var questRewards: QuestRewards
        private val financial = mutableMapOf<String, YamlConfiguration>()
        val players: PlayerRepository get() = repository
        val bank: BankService get() = bankService
        val transactions: StateStore get() = state
        val deliveries: DeliveryJournal get() = deliveryJournal
        val delivery: DeliveryController get() = deliveryController
        val market: MarketCatalog get() = marketCatalog
        val marketTrades: MarketService get() = marketService
        val escrow: MarketEscrow get() = marketEscrow
        val checks: CheckService get() = checkService
        val shop: ShopService get() = shopService
        val removals: RemovalJournal get() = removalJournal
        val exchanges: ItemExchange get() = itemExchange
        val gacha: GachaSettlement get() = gachaSettlement
        val selections: AbilitySelection get() = abilitySelection
        val transitions: AbilityTransitions get() = abilityTransitions
        val quest: QuestRewards get() = questRewards

        fun initialize(owner: Plugin) {
            plugin = owner
            state = SqliteStateStore(owner.dataFolder.toPath().resolve("limpi.sqlite3"))
            val report = LegacyStateMigration.import(owner.dataFolder.toPath(), state)
            owner.logger.info("Player storage ready: ${report.players} legacy profiles, balance sum ${report.balanceSum}, imported=${!report.reused}")
            repository = CachedPlayerRepository(SqliteProfilePersistence(state))
            bankService = BankService(state, repository)
            deliveryJournal = DeliveryJournal(state)
            initialRewards = InitialRewards(state, deliveryJournal, repository)
            questRewards = QuestRewards(state, repository)
            val counts = InitialTicketCounts.read(owner.config)
            initialItems = listOf(null to counts.gacha, com.github.lupi13.limpi.abilities.Grade.COMMON to counts.common,
                com.github.lupi13.limpi.abilities.Grade.RARE to counts.rare, com.github.lupi13.limpi.abilities.Grade.EPIC to counts.epic)
                .filter { it.second > 0 }.map { (grade, count) ->
                    PaperItemCodec.grant(com.github.lupi13.limpi.events.AbilityShop.getSelectTicketItem(grade, false, null)).copy(count = count)
                }
            checkService = CheckService(state, deliveryJournal, repository)
            shopService = ShopService(state, deliveryJournal, repository, PaperShopCatalog(owner)::offer)
            marketCatalog = MarketCatalog(state)
            val importedListings = LegacyMarketMigration.import(state, marketCatalog, repository::findByName) {
                PaperItemCodec.grant(it as ItemStack)
            }
            owner.logger.info("Market catalog ready: $importedListings legacy listings imported")
            marketService = MarketService(state, marketCatalog, deliveryJournal, repository)
            marketEscrow = MarketEscrow(state, marketCatalog)
            itemExchange = ItemExchange(state, deliveryJournal)
            gachaSettlement = GachaSettlement(state)
            abilitySelection = AbilitySelection(state)
            abilityTransitions = AbilityTransitions(state, repository)
            removalJournal = RemovalJournal(state, repository, mapOf(SaleSettlement.KIND to SaleSettlement, ItemExchange.KIND to itemExchange, GachaSettlement.KIND to gachaSettlement, AbilitySelection.KIND to abilitySelection, AbilityItemUse.KIND to AbilityItemUse(deliveryJournal)))
            deliveryController = DeliveryController(owner, DeliveryService(deliveryJournal), marketEscrow, removalJournal, abilityTransitions)
            financial.clear()
        }

        fun setup() {
            for (name in listOf("misc", "sellPrice", "shopRatio", "stock")) {
                financial[name] = YamlFiles.read(financialPath(name))
            }
        }

        fun getMiscConfig(): FileConfiguration = financial.getValue("misc")
        fun getSellPriceConfig(): FileConfiguration = financial.getValue("sellPrice")
        fun getShopRatioConfig(): FileConfiguration = financial.getValue("shopRatio")
        fun getStockConfig(): FileConfiguration = financial.getValue("stock")

        fun saveMisc() = saveFinancial("misc")
        fun saveSellPrice() = saveFinancial("sellPrice")
        fun saveShopRatio() = saveFinancial("shopRatio")
        fun saveStock() = saveFinancial("stock")

        /** Idempotent initialization: existing money, abilities and unknown fields are preserved. */
        fun makePlayerData(player: Player) = persist("initialize ${player.uniqueId}") {
            val raw = plugin.config.get("InitialMoney")
            require(raw is Int || raw is Long) { "InitialMoney must be an integer" }
            val balance = com.github.lupi13.limpi.economy.Money((raw as Number).toLong())
            initialRewards.register(player.uniqueId, player.name, balance, initialItems)
            repository.ensure(player.uniqueId, player.name, balance.units)
            repository.pin(player.uniqueId)
            if (plugin.config.getBoolean("EnableAbilitySystem")) initialRewards.claim(player.uniqueId)
        }

        fun getPlayerData(player: OfflinePlayer): FileConfiguration = repository.snapshot(player.uniqueId)
        fun getPlayerData(player: Player): FileConfiguration = repository.snapshot(player.uniqueId)
        fun getPlayerData(uuid: String): FileConfiguration = repository.snapshot(UUID.fromString(uuid))

        fun savePlayerData(player: OfflinePlayer, config: FileConfiguration) = persist("save ${player.uniqueId}") {
            repository.save(player.uniqueId, config)
        }
        fun savePlayerData(uuid: String, config: FileConfiguration) = persist("save $uuid") {
            repository.save(UUID.fromString(uuid), config)
        }

        fun findPlayerByName(name: String): Player? = Bukkit.getOnlinePlayers().firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }

        fun getPlayerDataByName(name: String): FileConfiguration? = repository.findByName(name)?.let(repository::snapshot)
        fun getUUIDByName(name: String): String? = repository.findByName(name)?.toString()

        fun releasePlayer(player: Player) = repository.release(player.uniqueId)
        fun close() {
            if (::repository.isInitialized) repository.close()
            financial.clear()
            if (::state.isInitialized) state.close()
        }

        private fun financialPath(name: String): Path = plugin.dataFolder.toPath().resolve("financial/$name.yml")
        private fun saveFinancial(name: String) = persist("save financial/$name.yml") {
            try {
                YamlFiles.write(financialPath(name), financial.getValue(name))
            } catch (failure: Exception) {
                financial[name] = YamlFiles.read(financialPath(name))
                throw failure
            }
        }

        private inline fun persist(description: String, action: () -> Unit) {
            try {
                action()
            } catch (failure: Exception) {
                plugin.logger.log(Level.SEVERE, "Unable to $description; operation was not completed", failure)
                throw failure
            }
        }
    }
}
