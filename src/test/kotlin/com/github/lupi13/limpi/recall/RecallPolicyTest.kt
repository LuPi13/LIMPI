package com.github.lupi13.limpi.recall

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecallPolicyTest {
    @Test fun `the cast takes three seconds`() = assertEquals(60, RecallPolicy.CAST_TICKS)

    @Test fun `standing still or bobbing a little is not moving`() {
        assertFalse(RecallPolicy.moved(10.5, 64.0, -3.5, 10.5, 64.0, -3.5))
        assertFalse(RecallPolicy.moved(10.5, 64.0, -3.5, 10.55, 64.3, -3.45))
        assertFalse(RecallPolicy.moved(0.0, 64.0, 0.0, 0.0, 63.6, 0.0))
    }

    @Test fun `a step, a knockback or a fall is moving`() {
        assertTrue(RecallPolicy.moved(10.5, 64.0, -3.5, 10.62, 64.0, -3.5))
        assertTrue(RecallPolicy.moved(0.0, 64.0, 0.0, 0.08, 64.0, 0.08))
        assertTrue(RecallPolicy.moved(0.0, 64.0, 0.0, 0.0, 63.4, 0.0))
        assertTrue(RecallPolicy.moved(0.0, 64.0, 0.0, 0.0, 64.6, 0.0))
    }

    @Test fun `the countdown shows whole seconds left`() {
        assertEquals(3, RecallPolicy.secondsLeft(0))
        assertEquals(3, RecallPolicy.secondsLeft(1))
        assertEquals(2, RecallPolicy.secondsLeft(20))
        assertEquals(1, RecallPolicy.secondsLeft(40))
        assertEquals(1, RecallPolicy.secondsLeft(59))
        assertEquals(0, RecallPolicy.secondsLeft(60))
        assertEquals(0, RecallPolicy.secondsLeft(80))
    }

    @Test fun `the held scroll is used first, then the off hand, then the rest in order`() {
        assertEquals(listOf(4, 40) + (0 until 40).filter { it != 4 }, RecallPolicy.slotOrder(4, 41))
        val order = RecallPolicy.slotOrder(0, 41)
        assertEquals(listOf(0, 40, 1), order.take(3))
        assertEquals((0 until 41).toSet(), order.toSet())
        assertEquals(41, order.size)
        assertEquals(listOf(0, 1, 2), RecallPolicy.slotOrder(0, 3)) // 왼손이 없는 인벤토리
    }
}
