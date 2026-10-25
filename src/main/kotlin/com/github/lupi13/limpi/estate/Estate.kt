package com.github.lupi13.limpi.estate

import com.github.lupi13.limpi.economy.Money
import com.github.lupi13.limpi.storage.YamlFiles
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.Locale
import java.util.UUID

/** A purchase or management request that cannot be carried out; the message is shown to the player as is. */
open class EstateUnavailable(message: String) : IllegalStateException(message)

/** The selection touches existing estates; [parts] are the overlapping boxes so they can be shown. */
class EstateOverlap(message: String, val parts: List<EstateRegion>) : EstateUnavailable(message)

/** Inclusive block box in one world. */
data class EstateRegion(val world: UUID, val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
    init { require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Estate bounds are reversed" } }

    val width: Long get() = maxX.toLong() - minX + 1
    val depth: Long get() = maxZ.toLong() - minZ + 1
    val height: Long get() = maxY.toLong() - minY + 1
    /** Block count; overflow throws instead of wrapping. */
    val volume: Long get() = Math.multiplyExact(Math.multiplyExact(width, depth), height)

    fun contains(world: UUID, x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && z in minZ..maxZ && y in minY..maxY && world == this.world

    fun intersection(other: EstateRegion): EstateRegion? {
        if (world != other.world) return null
        val x1 = maxOf(minX, other.minX); val x2 = minOf(maxX, other.maxX)
        val y1 = maxOf(minY, other.minY); val y2 = minOf(maxY, other.maxY)
        val z1 = maxOf(minZ, other.minZ); val z2 = minOf(maxZ, other.maxZ)
        return if (x1 <= x2 && y1 <= y2 && z1 <= z2) EstateRegion(world, x1, y1, z1, x2, y2, z2) else null
    }

    fun withHeight(minY: Int, maxY: Int) = copy(minY = minY, maxY = maxY)

    fun describe(): String = "($minX, $minY, $minZ)~($maxX, $maxY, $maxZ)"

    companion object {
        /** Two corners in any order. */
        fun of(world: UUID, x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
            EstateRegion(world, minOf(x1, x2), minOf(y1, y2), minOf(z1, z2), maxOf(x1, x2), maxOf(y1, y2), maxOf(z1, z2))
    }
}

data class Estate(val id: UUID, val owner: UUID, val name: String, val region: EstateRegion, val worldName: String,
                  val paid: Money, val createdAt: Long, val members: Set<UUID>) {
    fun trusted(player: UUID): Boolean = player == owner || player in members
}

/** config.yml Estate* settings. A malformed value refuses purchases instead of guessing. */
data class EstateSettings(val pricePerBlock: Money, val refundPercent: Int, val maxWidth: Int, val maxHeight: Int,
                          val maxCount: Int, val worlds: Set<String>) {
    init {
        require(refundPercent in 0..100) { "EstateRefundPercent must be 0..100" }
        require(maxWidth in 1..MAX_WIDTH) { "EstateMaxWidth must be 1..$MAX_WIDTH" }
        require(maxHeight >= 0) { "EstateMaxHeight must not be negative" }
        require(maxCount >= 0) { "EstateMaxCount must not be negative" }
    }

    fun allows(worldName: String): Boolean = worlds.isEmpty() || worldName in worlds
    fun refund(paid: Money): Money = paid.fee(refundPercent)

    companion object {
        /** Wider boxes would put too many chunk entries into the protection index. */
        const val MAX_WIDTH = 1024
        val DEFAULT = EstateSettings(Money(4), 50, 128, 0, 10, emptySet())

        fun read(config: ConfigurationSection): EstateSettings {
            fun integer(path: String): Long = when (val value = config.get(path)) {
                is Byte, is Short, is Int, is Long -> (value as Number).toLong()
                else -> throw IllegalArgumentException("$path must be an integer")
            }
            fun int(path: String): Int = integer(path).let { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$path is too large" }; it.toInt() }
            val price = integer("EstatePricePerBlock").also { require(it >= 0) { "EstatePricePerBlock must not be negative" } }
            val worlds = requireNotNull(config.getList("EstateWorlds")) { "EstateWorlds must be a list" }
                .map { requireNotNull(it as? String) { "EstateWorlds must contain world names" } }.toSet()
            return EstateSettings(Money(price), int("EstateRefundPercent"), int("EstateMaxWidth"), int("EstateMaxHeight"), int("EstateMaxCount"), worlds)
        }
    }
}

/** Selection checks shared by the quote shown to the player and the purchase transaction. */
object EstatePolicy {
    const val MAX_NAME = 16

    fun validName(name: String): Boolean = name.length in 1..MAX_NAME && name.none { it.isWhitespace() || it == '§' }
    fun sameName(a: String, b: String) = a.lowercase(Locale.ROOT) == b.lowercase(Locale.ROOT)

    /** Price of buying [region] for [owner] now, given every estate that exists. */
    fun price(owner: UUID, region: EstateRegion, worldName: String, settings: EstateSettings, existing: Collection<Estate>): Money {
        if (!settings.allows(worldName)) throw EstateUnavailable("이 월드에서는 사유지를 살 수 없습니다.")
        if (region.width > settings.maxWidth || region.depth > settings.maxWidth)
            throw EstateUnavailable("한 구역의 가로·세로는 최대 ${settings.maxWidth}칸입니다. (선택: ${region.width}×${region.depth})")
        if (settings.maxHeight > 0 && region.height > settings.maxHeight)
            throw EstateUnavailable("한 구역의 높이는 최대 ${settings.maxHeight}칸입니다. (선택: ${region.height})")
        if (existing.count { it.owner == owner } >= settings.maxCount)
            throw EstateUnavailable("사유지는 최대 ${settings.maxCount}개까지 가질 수 있습니다.")
        val others = existing.filter { it.owner != owner }.mapNotNull { region.intersection(it.region) }
        if (others.isNotEmpty()) throw EstateOverlap("선택한 지역의 일부가 다른 사람의 사유지와 겹칩니다.", others)
        val own = existing.filter { it.owner == owner }.mapNotNull { region.intersection(it.region) }
        if (own.isNotEmpty()) throw EstateOverlap("선택한 지역의 일부가 본인의 다른 사유지와 겹칩니다.", own)
        return try { settings.pricePerBlock.times(region.volume) } catch (failure: ArithmeticException) {
            throw EstateUnavailable("선택한 구역이 너무 큽니다.")
        }
    }
}

/** estate/<id>: unknown fields are preserved; a damaged document is reported, never reset or dropped. */
object EstateDocuments {
    const val PREFIX = "estate/"
    fun key(id: UUID) = "$PREFIX$id"

    fun decode(key: String, payload: String): Pair<YamlConfiguration, Estate> {
        val data = YamlFiles.parse(payload)
        require(data.get("schemaVersion") == 1) { "Unsupported estate schema" }
        val id = UUID.fromString(requireNotNull(data.getString("id")) { "Missing estate id" })
        require(key == key(id)) { "Estate id does not match its key" }
        fun int(name: String): Int = when (val value = data.get(name)) {
            is Byte, is Short, is Int -> (value as Number).toInt()
            else -> throw IllegalArgumentException("Invalid estate field $name")
        }
        fun long(name: String): Long = when (val value = data.get(name)) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> throw IllegalArgumentException("Invalid estate field $name")
        }
        val name = requireNotNull(data.getString("name")) { "Missing estate name" }
        require(EstatePolicy.validName(name)) { "Invalid estate name" }
        val members = requireNotNull(data.getList("members")) { "Missing estate members" }
            .map { UUID.fromString(requireNotNull(it as? String) { "Invalid estate member" }) }.toSet()
        val region = EstateRegion(UUID.fromString(requireNotNull(data.getString("world")) { "Missing estate world" }),
            int("minX"), int("minY"), int("minZ"), int("maxX"), int("maxY"), int("maxZ"))
        region.volume
        val paid = long("paid").also { require(it >= 0) { "Negative estate price" } }
        return data to Estate(id, UUID.fromString(requireNotNull(data.getString("owner")) { "Missing estate owner" }), name, region,
            data.getString("worldName") ?: "?", Money(paid), long("createdAt"), members)
    }

    fun encode(data: YamlConfiguration, estate: Estate): String = data.apply {
        set("schemaVersion", 1); set("id", estate.id.toString()); set("owner", estate.owner.toString()); set("name", estate.name)
        set("world", estate.region.world.toString()); set("worldName", estate.worldName)
        set("minX", estate.region.minX); set("minY", estate.region.minY); set("minZ", estate.region.minZ)
        set("maxX", estate.region.maxX); set("maxY", estate.region.maxY); set("maxZ", estate.region.maxZ)
        set("paid", estate.paid.units); set("createdAt", estate.createdAt)
        set("members", estate.members.map(UUID::toString).sorted())
    }.saveToString()

    /** Owner of a document that could not be decoded, when at least that much can be read. */
    fun ownerOf(payload: String): UUID? = runCatching { UUID.fromString(YamlFiles.parse(payload).getString("owner")) }.getOrNull()
}
