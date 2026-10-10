package com.github.lupi13.limpi.menu

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test

class PaginationTest {
    @ParameterizedTest
    @CsvSource("0,1", "1,1", "42,1", "45,1", "46,2", "90,2", "91,3")
    fun `page counts never create an empty extra page`(total: Int, pages: Int) {
        assertEquals(pages, Pagination(total, 0).pageCount)
        val last = Pagination(total, pages - 1)
        assertFalse(last.hasNext)
        assertEquals(total, last.end)
        if (total > 0) assertTrue(last.end > last.start)
    }

    @Test fun `invalid requested pages clamp to valid range`() {
        assertEquals(0, Pagination(91, -1).page)
        assertEquals(2, Pagination(91, 999).page)
        assertEquals(90, Pagination(91, 999).start)
        assertEquals(91, Pagination(91, 999).end)
    }

    @Test fun `large lists do not overflow their final bound`() {
        assertEquals(Int.MAX_VALUE, Pagination(Int.MAX_VALUE, Int.MAX_VALUE).end)
    }
}
