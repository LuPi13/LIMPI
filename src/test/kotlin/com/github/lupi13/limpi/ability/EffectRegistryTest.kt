package com.github.lupi13.limpi.ability

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class EffectRegistryTest {
    private val owner = UUID.randomUUID()
    @Test fun `partial setup releases in reverse order and repeated cleanup is harmless`() {
        val registry = EffectRegistry { throw AssertionError(it) }; val calls = mutableListOf<Int>()
        val scope = registry.begin(owner)
        val early = scope.own { calls += 1 }; scope.own { calls += 2 }; scope.own { calls += 3 }
        early.close(); registry.closeOwner(owner); scope.close(); early.close()
        assertEquals(listOf(1, 3, 2), calls); assertEquals(0, registry.count(owner)); assertTrue(scope.closed)
    }
    @Test fun `failed cleanup and failed reporter still release every resource`() {
        var reports = 0; var released = false
        val registry = EffectRegistry { reports++; error("reporter failed") }; val scope = registry.begin(owner)
        scope.own { released = true }; scope.own { error("resource failed") }
        registry.closeAll()
        assertTrue(released); assertEquals(1, reports); assertEquals(0, registry.count(owner))
    }
    @Test fun `reentrant owner cleanup cannot create new effects while closing`() {
        val registry = EffectRegistry { throw AssertionError(it) }; val scope = registry.begin(owner)
        scope.own {
            registry.closeOwner(owner)
            assertThrows(IllegalStateException::class.java) { registry.begin(owner) }
            scope.own { assertTrue(scope.closed) }
        }
        registry.closeOwner(owner); assertEquals(0, registry.count(owner))
        assertFalse(registry.begin(owner).closed)
    }
    @Test fun `consumed ability can preserve its projectile without keeping unrelated effects`() {
        val registry = EffectRegistry { throw AssertionError(it) }; val projectile = registry.begin(owner)
        val movement = registry.begin(owner); val otherOwner = registry.begin(UUID.randomUUID())
        registry.closeOwner(owner, projectile.id)
        assertFalse(projectile.closed); assertTrue(movement.closed); assertFalse(otherOwner.closed)
        assertEquals(1, registry.count(owner)); registry.closeAll(); assertTrue(projectile.closed); assertTrue(otherOwner.closed)
    }
    @Test fun `late resource releases immediately and shutdown forbids new effects`() {
        val registry = EffectRegistry { throw AssertionError(it) }; val scope = registry.begin(owner)
        registry.closeAll(); var calls = 0; val late = scope.own { calls++ }; late.close()
        assertEquals(1, calls); assertThrows(IllegalStateException::class.java) { registry.begin(owner) }
    }
}
