package com.github.lupi13.limpi.economy

import java.util.Locale

/**
 * 바닐라 아이템이 아닌 상점 상품. shopPrice.yml·shopCategory.yml의 키가 아이템 이름이 아니다.
 * 판매창에서는 팔리지 않으므로(판매가 없음) 판매가보다 싸야 한다는 제한을 0원 기준으로 본다.
 */
object ShopSpecials {
    const val RECALL_SCROLL = "recall_scroll"
    val codes = setOf(RECALL_SCROLL)

    /** 명령 인자나 상점 코드(대문자)를 파일 키로. 특수 상품이 아니면 null. */
    fun of(argument: String): String? = argument.lowercase(Locale.ROOT).takeIf { it in codes }
}
