package com.github.lupi13.limpi.menu

/** 서버 상점과 능력 상점을 오가는 버튼. 상품은 옮기지 않고 서로 찾기 쉽게만 한다. */
object ShopLinks {
    /** 서버 상점: 아랫줄 가운데(45 이전, 46~48·50~52 탭, 53 다음 사이의 빈칸). */
    const val SERVER_SHOP_SLOT = 49

    /** 능력 상점(9칸): 뽑기권과 선택권(1~7칸) 다음의 오른쪽 끝. */
    const val ABILITY_SHOP_SLOT = 8

    /** 건너갈 상점의 시스템이 켜져 있고 그 상점을 열 권한이 있을 때만 버튼을 둔다. */
    fun shown(systemEnabled: Boolean, permitted: Boolean) = systemEnabled && permitted
}
