package com.github.lupi13.limpi.delivery

object InventoryImages {
    fun encode(image: List<StoredStack?>): List<Map<String, Any>?> = image.map { stack ->
        stack?.let { mapOf("payload" to it.payload, "count" to it.count, "limit" to it.limit) }
    }
    fun decode(values: List<*>): List<StoredStack?> = values.map { value ->
        if (value == null) null else {
            val fields = value as Map<*, *>
            fun number(key: String): Int = when (val raw = fields[key]) {
                is Byte, is Short, is Int, is Long -> Math.toIntExact((raw as Number).toLong())
                else -> throw IllegalArgumentException("Invalid inventory image number")
            }
            StoredStack(fields["payload"] as String, number("count"), number("limit"))
        }
    }
}
