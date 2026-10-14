package com.github.lupi13.limpi.commands

import java.util.Locale

data class CommandRoute(val id: String, val path: List<String>, val permission: String?, val playerOnly: Boolean,
                        val minimum: Int, val maximum: Int, val usage: String, val description: String,
                        val recovery: Boolean = false, val suggestions: Map<Int, String> = emptyMap(),
                        val short: String = description, val adminHelp: Boolean = false) {
    init { require(id.isNotBlank() && path.isNotEmpty() && minimum >= path.size && maximum >= minimum) }
    val admin: Boolean get() = permission?.contains(".admin.") == true
    /** Takes no arguments, so a help line can run it directly instead of filling the chat box. */
    val bare: Boolean get() = maximum == path.size
    fun permitted(player: Boolean, hasPermission: (String) -> Boolean) = (!playerOnly || player) && (permission == null || hasPermission(permission))
}

/** The same definitions govern dispatch, help, permissions and literal tab suggestions. */
class CommandIndex(val routes: List<CommandRoute>) {
    init { require(routes.map { it.path }.toSet().size == routes.size) }
    fun resolve(args: List<String>): CommandRoute? = routes.filter { route ->
        args.size >= route.path.size && route.path.indices.all { args[it].equals(route.path[it], true) }
    }.maxByOrNull { it.path.size }
    /** "help admin" is only offered to senders who can use at least one administrative route here. */
    fun allowed(route: CommandRoute, player: Boolean, hasPermission: (String) -> Boolean): Boolean =
        route.permitted(player, hasPermission) && (!route.adminHelp || routes.any { it.admin && it.permitted(player, hasPermission) })
    fun visible(enabled: Boolean, player: Boolean, hasPermission: (String) -> Boolean): List<CommandRoute> = routes
        .filter { (enabled || it.recovery) && allowed(it, player, hasPermission) }.distinctBy { it.id }
    fun complete(args: List<String>, enabled: Boolean, player: Boolean, hasPermission: (String) -> Boolean,
                 values: (String) -> List<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        val prefix = args.dropLast(1); val partial = args.last().lowercase(Locale.ROOT)
        return routes.filter { (enabled || it.recovery) && allowed(it, player, hasPermission) }.flatMap { route ->
            if (prefix.size < route.path.size && prefix.indices.all { prefix[it].equals(route.path[it], true) }) listOf(route.path[prefix.size])
            else if (prefix.size >= route.path.size && route.path.indices.all { prefix[it].equals(route.path[it], true) } && args.size <= route.maximum) {
                route.suggestions[prefix.size]?.let(values).orEmpty()
            } else emptyList()
        }.filter { it.lowercase(Locale.ROOT).startsWith(partial) }.distinct()
    }
}
