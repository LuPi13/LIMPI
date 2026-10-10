package com.github.lupi13.limpi.ability

import java.util.UUID

/** Runtime resources have explicit ownership, release once and survive partial setup failures. */
class EffectRegistry(private val onFailure: (Exception) -> Unit) {
    private val owners = mutableMapOf<UUID, MutableSet<EffectScope>>()
    private val closing = mutableSetOf<UUID>()
    private var stopped = false
    fun begin(owner: UUID): EffectScope {
        check(!stopped && owner !in closing)
        val scope = EffectScope(onFailure) { ended ->
            owners[owner]?.let { scopes -> scopes.remove(ended); if (scopes.isEmpty()) owners.remove(owner) }
        }
        owners.getOrPut(owner) { linkedSetOf() }.add(scope)
        return scope
    }
    fun count(owner: UUID) = owners[owner]?.size ?: 0
    fun closeOwner(owner: UUID, except: UUID? = null) {
        if (!closing.add(owner)) return
        try { owners[owner]?.toList()?.filter { it.id != except }?.forEach(EffectScope::close) }
        finally { closing.remove(owner) }
    }
    fun closeAll() {
        stopped = true
        owners.keys.toList().forEach { closeOwner(it) }
    }
}

class EffectScope internal constructor(private val onFailure: (Exception) -> Unit, private val onClose: (EffectScope) -> Unit) : AutoCloseable {
    val id: UUID = UUID.randomUUID()
    var closed = false
        private set
    private val resources = mutableListOf<Release>()
    fun own(action: () -> Unit): AutoCloseable {
        val resource = Release(action)
        if (closed) resource.close() else resources.add(resource)
        return resource
    }
    override fun close() {
        if (closed) return
        closed = true
        val pending = resources.toList().asReversed()
        resources.clear()
        try { pending.forEach { it.close() } } finally { onClose(this) }
    }
    private inner class Release(private val action: () -> Unit) : AutoCloseable {
        private var released = false
        override fun close() {
            if (released) return
            released = true
            resources.remove(this)
            try { action() } catch (failure: Exception) {
                // A broken reporting callback must not prevent the remaining resources from releasing.
                try { onFailure(failure) } catch (_: Exception) { }
            }
        }
    }
}
