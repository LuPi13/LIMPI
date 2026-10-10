package com.github.lupi13.limpi.menu

class Pagination(total: Int, requestedPage: Int, pageSize: Int = 45) {
    init { require(total >= 0 && pageSize > 0) }
    val pageCount = if (total == 0) 1 else (total - 1) / pageSize + 1
    val page = requestedPage.coerceIn(0, pageCount - 1)
    val start = page * pageSize
    val end = minOf(total.toLong(), start.toLong() + pageSize).toInt()
    val hasPrevious get() = page > 0
    val hasNext get() = page + 1 < pageCount
}
