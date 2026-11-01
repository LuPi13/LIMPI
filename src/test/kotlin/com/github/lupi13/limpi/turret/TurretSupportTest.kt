package com.github.lupi13.limpi.turret

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurretSupportTest {
    private fun shots(interval: Int, speed: Double, ticks: Int): Int {
        var charge = interval.toDouble(); var count = 0
        repeat(ticks) {
            charge = TurretCharge.fill(charge, speed, interval)
            if (TurretCharge.ready(charge, interval)) { charge = TurretCharge.spend(charge, interval); count++ }
        }
        return count
    }

    @Test fun `gold neighbours add a quarter of speed each`() {
        assertEquals(1.0, TurretCharge.speed(emptyList()))
        assertEquals(1.25, TurretCharge.speed(listOf(0.25)))
        assertEquals(2.0, TurretCharge.speed(List(4) { 0.25 }))
        assertEquals(1.0, TurretCharge.speed(listOf(-1.0)))
    }

    @Test fun `charged firing matches the interval and speeds up by the speed factor`() {
        assertEquals(10, shots(20, 1.0, 200)) // 첫 발은 바로, 이후 20틱마다
        assertEquals(13, shots(20, 1.25, 200)) // 16틱마다
        assertEquals(20, shots(20, 2.0, 200)) // 10틱마다
        assertEquals(200, shots(1, 2.0, 200)) // 1틱 아래로는 빨라지지 않음
        assertEquals(7, shots(40, 1.25, 200)) // 구리 2초 → 1.6초(32틱마다)
    }

    @Test fun `a full charge waits for a target and fires at once`() {
        var charge = TurretCharge.fill(20.0, 1.0, 20)
        assertTrue(TurretCharge.ready(charge, 20))
        repeat(50) { charge = TurretCharge.fill(charge, 2.0, 20) }
        assertEquals(20.0, charge)
    }

    @Test fun `six neighbour offsets are the faces of a block`() {
        assertEquals(6, TURRET_NEIGHBOR_OFFSETS.toSet().size)
        assertTrue(TURRET_NEIGHBOR_OFFSETS.all { (x, y, z) -> kotlin.math.abs(x) + kotlin.math.abs(y) + kotlin.math.abs(z) == 1 })
    }

    @Test fun `ten kills inside one minute complete and older kills fall out`() {
        val window = KillWindow(1200, 10)
        for (i in 0 until 9) assertFalse(window.add(i * 130)) // 0..1040
        assertTrue(window.add(1199)) // 10번째가 첫 처치 뒤 1199틱 → 1분 안
        val late = KillWindow(1200, 10)
        for (i in 0 until 9) assertFalse(late.add(i * 130))
        assertFalse(late.add(1200)) // 첫 처치가 정확히 1분 전 → 빠짐, 9마리
        assertEquals(9, late.size)
    }
}
