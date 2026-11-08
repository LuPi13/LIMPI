package com.github.lupi13.limpi.recall

/** 귀환서 시전 규칙. Bukkit 없이 시험할 수 있게 숫자만 다룬다. */
object RecallPolicy {
    /** 3초 동안 가만히 있어야 한다. */
    const val CAST_TICKS = 60

    /** 고개를 돌리는 것은 괜찮고, 한 걸음이나 넉백은 취소다. */
    const val HORIZONTAL_TOLERANCE = 0.1

    /** 물에 떠 있을 때의 출렁임은 봐주고, 떨어지거나 뛰어오르면 취소다. */
    const val VERTICAL_TOLERANCE = 0.5

    fun moved(fromX: Double, fromY: Double, fromZ: Double, x: Double, y: Double, z: Double): Boolean {
        val dx = x - fromX; val dz = z - fromZ
        return dx * dx + dz * dz > HORIZONTAL_TOLERANCE * HORIZONTAL_TOLERANCE || kotlin.math.abs(y - fromY) > VERTICAL_TOLERANCE
    }

    /** 액션바에 보여 줄 남은 초(올림). [elapsed]가 끝에 닿으면 0. */
    fun secondsLeft(elapsed: Int): Int = ((CAST_TICKS - elapsed).coerceAtLeast(0) + 19) / 20

    /** 손에 든 칸을 먼저, 그다음 왼손(40), 그다음 인벤토리 앞쪽부터 쓴다. */
    fun slotOrder(held: Int, size: Int): List<Int> = (listOf(held, OFFHAND) + (0 until size)).distinct().filter { it in 0 until size }

    const val OFFHAND = 40
}
