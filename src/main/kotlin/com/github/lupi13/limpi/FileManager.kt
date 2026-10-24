package com.github.lupi13.limpi

import com.github.lupi13.limpi.player.PlayerRepository
import com.github.lupi13.limpi.economy.BankService
import com.github.lupi13.limpi.delivery.DeliveryJournal
import com.github.lupi13.limpi.delivery.DeliveryService
import com.github.lupi13.limpi.delivery.RemovalJournal
import com.github.lupi13.limpi.economy.SaleSettlement
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
        private lateinit var stockTrading: com.github.lupi13.limpi.stock.StockTradingService
        private lateinit var deliveryJournal: DeliveryJournal
        private lateinit var deliveryController: DeliveryController
        private lateinit var marketCatalog: MarketCatalog
        private lateinit var marketService: MarketService
        private lateinit var marketEscrow: MarketEscrow
        private lateinit var checkService: CheckService
        private lateinit var shopService: ShopService
        private lateinit var abilityShopService: ShopService
        private lateinit var saleService: com.github.lupi13.limpi.economy.SaleService
        private lateinit var removalJournal: RemovalJournal
        private lateinit var gachaSettlement: GachaSettlement
        private lateinit var abilitySelection: AbilitySelection
        private lateinit var abilityTransitions: AbilityTransitions
        private lateinit var initialRewards: InitialRewards
        private lateinit var initialItems: List<com.github.lupi13.limpi.delivery.ItemGrant>
        private lateinit var questRewards: QuestRewards
        private lateinit var chestService: com.github.lupi13.limpi.chest.ChestService
        private lateinit var noticeBoard: com.github.lupi13.limpi.notice.NoticeBoard
        private val financial = mutableMapOf<String, YamlConfiguration>()
        val players: PlayerRepository get() = repository
        val bank: BankService get() = bankService
        val stocks: com.github.lupi13.limpi.stock.StockTradingService get() = stockTrading
        val transactions: StateStore get() = state
        val deliveries: DeliveryJournal get() = deliveryJournal
        val delivery: DeliveryController get() = deliveryController
        val market: MarketCatalog get() = marketCatalog
        val marketTrades: MarketService get() = marketService
        val escrow: MarketEscrow get() = marketEscrow
        val checks: CheckService get() = checkService
        val shop: ShopService get() = shopService
        val abilityShop: ShopService get() = abilityShopService
        val sales: com.github.lupi13.limpi.economy.SaleService get() = saleService
        val removals: RemovalJournal get() = removalJournal
        val gacha: GachaSettlement get() = gachaSettlement
        val selections: AbilitySelection get() = abilitySelection
        val transitions: AbilityTransitions get() = abilityTransitions
        val quest: QuestRewards get() = questRewards
        val chests: com.github.lupi13.limpi.chest.ChestService get() = chestService
        val notices: com.github.lupi13.limpi.notice.NoticeBoard get() = noticeBoard

        /** Finished delivery/removal/transition/escrow records and their ledger rows are only kept for RecordRetentionDays. */
        private fun pruneRecords(owner: Plugin) {
            val days = owner.config.getInt("RecordRetentionDays", 7)
            if (days <= 0) return
            val cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(days.toLong())
            val pruned = state.prune(cutoff, com.github.lupi13.limpi.storage.RecordRetention.prefixes,
                // Initial rewards rely on their once-only ledger identities; never forget them.
                setOf("initial-register:", "initial-grant:"), com.github.lupi13.limpi.storage.RecordRetention::stillUsed)
            owner.logger.info("Pruned ${pruned.documents} finished records and ${pruned.transactions} ledger rows older than $days days")
        }

        fun initialize(owner: Plugin) {
            plugin = owner
            val timing: ((String, Long) -> Unit)? = if (owner.config.getBoolean("DebugTransactionTiming", false)) {
                { operation, nanos ->
                    val millis = nanos / 1_000_000.0
                    if (nanos >= 50_000_000L) owner.logger.warning("Slow SQLite transaction $operation: $millis ms")
                    else owner.logger.info("SQLite transaction $operation: $millis ms")
                }
            } else null
            state = SqliteStateStore(owner.dataFolder.toPath().resolve("limpi.sqlite3"), transactionTiming = timing)
            pruneRecords(owner)
            repository = CachedPlayerRepository(SqliteProfilePersistence(state))
            bankService = BankService(state, repository)
            stockTrading = com.github.lupi13.limpi.stock.StockTradingService(state, repository, { owner.config.getInt("StockFeePercent") }) { code ->
                val data = financial.getValue("stock")
                if (code in com.github.lupi13.limpi.stock.StockCatalog.codes(data)) com.github.lupi13.limpi.stock.StockCatalog.price(data, code) else null
            }
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
            abilityShopService = ShopService(state, deliveryJournal, repository, com.github.lupi13.limpi.events.AbilityShop::offer, "ABILITY_SHOP_BUY")
            saleService = com.github.lupi13.limpi.economy.SaleService(state, repository)
            marketCatalog = MarketCatalog(state)
            marketService = MarketService(state, marketCatalog, deliveryJournal, repository)
            marketEscrow = MarketEscrow(state, marketCatalog)
            gachaSettlement = GachaSettlement(state)
            abilitySelection = AbilitySelection(state)
            abilityTransitions = AbilityTransitions(state, repository)
            removalJournal = RemovalJournal(state, repository, mapOf(
                SaleSettlement.KIND to SaleSettlement,
                GachaSettlement.KIND to gachaSettlement, AbilitySelection.KIND to abilitySelection,
                AbilityItemUse.KIND to AbilityItemUse(deliveryJournal),
                com.github.lupi13.limpi.ability.PickPocketSettlement.KIND to com.github.lupi13.limpi.ability.PickPocketSettlement(deliveryJournal),
            ))
            chestService = com.github.lupi13.limpi.chest.ChestService(state, repository)
            noticeBoard = com.github.lupi13.limpi.notice.NoticeBoard(state)
            com.github.lupi13.limpi.commands.Notice.prune(owner)
            deliveryController = DeliveryController(owner, DeliveryService(deliveryJournal), marketEscrow, removalJournal, abilityTransitions, chestService)
            financial.clear()
        }

        /** Bundled default prices are copied only when the file does not exist; an edited file is never overwritten. */
        fun setup() {
            for (name in listOf("sellPrice", "shopPrice")) {
                if (!java.nio.file.Files.exists(financialPath(name))) plugin.saveResource("financial/$name.yml", false)
            }
            for (name in listOf("misc", "sellPrice", "shopPrice", "stock")) {
                financial[name] = YamlFiles.read(financialPath(name))
            }
        }

        fun getMiscConfig(): FileConfiguration = financial.getValue("misc")
        fun getSellPriceConfig(): FileConfiguration = financial.getValue("sellPrice")
        fun getShopPriceConfig(): FileConfiguration = financial.getValue("shopPrice")
        fun getStockConfig(): FileConfiguration = financial.getValue("stock")

        fun saveSellPrice() = saveFinancial("sellPrice")
        fun saveShopPrice() = saveFinancial("shopPrice")
        fun saveStock() = saveFinancial("stock")
        fun replaceStock(value: YamlConfiguration) {
            val copy = YamlFiles.parse(value.saveToString())
            YamlFiles.write(financialPath("stock"), copy)
            financial["stock"] = copy
        }

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
