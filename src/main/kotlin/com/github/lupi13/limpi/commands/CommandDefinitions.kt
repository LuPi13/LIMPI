package com.github.lupi13.limpi.commands

object CommandDefinitions {
    private fun route(path: String, permission: String?, player: Boolean, min: Int, max: Int, usage: String, description: String,
                      recovery: Boolean = false, suggestions: Map<Int, String> = emptyMap(), id: String = path, short: String = description,
                      adminHelp: Boolean = false) =
        CommandRoute(id, path.split(' '), permission, player, min, max, usage, description, recovery, suggestions, short, adminHelp)
    val ability = CommandIndex(listOf(
        route("help", null, false, 1, 1, "help", "사용 가능한 능력 명령을 확인합니다.", true, short = "명령어 사용법"),
        route("help admin", null, false, 2, 2, "help admin", "사용 가능한 관리자 명령을 확인합니다.", true, short = "관리자 명령어", adminHelp = true),
        route("enemy", "limpi.ability.enemy", true, 1, 1, "enemy", "능력의 적 범위를 설정합니다.", short = "적 범위 설정"),
        route("me", "limpi.ability.me", true, 1, 1, "me", "현재 능력과 사용법을 확인합니다.", short = "내 능력 정보"),
        route("select", "limpi.ability.select", true, 1, 1, "select", "선택권으로 해금한 능력을 적용합니다.", short = "선택권으로 능력 선택"),
        route("dictionary", "limpi.ability.dictionary", true, 1, 1, "dictionary (dict)", "능력 도감을 엽니다.", short = "능력 도감"),
        route("dict", "limpi.ability.dictionary", true, 1, 1, "dictionary (dict)", "능력 도감을 엽니다.", id = "dictionary", short = "능력 도감"),
        route("shop", "limpi.ability.shop", true, 1, 1, "shop", "에메랄드로 티켓을 구매합니다.", short = "뽑기권·선택권 구매"),
        route("pickup", "limpi.ability.pickup", false, 1, 1, "pickup", "현재/예정 픽업을 확인합니다.", short = "픽업 능력 확인"),
        route("ceiling", "limpi.ability.pity", true, 1, 1, "ceiling (pity)", "천장과 다음 실제 확률을 확인합니다.", short = "내 천장 현황"),
        route("pity", "limpi.ability.pity", true, 1, 1, "ceiling (pity)", "천장과 다음 실제 확률을 확인합니다.", id = "ceiling", short = "내 천장 현황"),
        route("set", "limpi.ability.admin.set", false, 3, 3, "set <접속 플레이어> <능력 ID|none>", "티켓 없이 능력을 적용하거나 제거합니다.", suggestions = mapOf(1 to "online", 2 to "abilities"), short = "능력 적용·제거"),
        route("result", "limpi.ability.result", true, 2, 2, "result <거래 UUID>", "확정된 자신의 뽑기/선택 결과를 확인합니다.", true, short = "뽑기·선택 결과 다시 보기"),
        route("reload", "limpi.ability.admin.reload", false, 1, 1, "reload", "모든 능력 설정을 검증한 뒤 함께 다시 불러옵니다.", short = "능력 설정 다시 불러오기"),
        route("rates", "limpi.ability.pity", false, 1, 1, "rates", "등급별 뽑기 확률과 천장·픽업 규칙을 확인합니다.", short = "뽑기 확률 규칙"),
        route("quest", "limpi.ability.quest", true, 1, 1, "quest", "퀘스트 달성 여부와 보상·누적 진행도를 확인합니다.", short = "퀘스트 진행도"),
    ))
    val financial = CommandIndex(listOf(
        route("help", null, false, 1, 1, "help", "사용 가능한 금융 명령을 확인합니다.", true, short = "명령어 사용법"),
        route("help admin", null, false, 2, 2, "help admin", "사용 가능한 관리자 명령을 확인합니다.", true, short = "관리자 명령어", adminHelp = true),
        route("me", "limpi.financial.me", true, 1, 1, "me", "자신의 잔액을 확인합니다.", short = "내 잔액"),
        route("check", "limpi.financial.check", true, 2, 3, "check <금액>", "수표를 발행하고 공간 부족분을 미수령에 보관합니다.", short = "수표 발행"),
        route("check redeem", "limpi.financial.check", true, 2, 2, "check redeem", "주 손 수표를 입금합니다.", true, short = "손에 든 수표 입금"),
        route("check inspect", "limpi.financial.admin.check", false, 2, 3, "check inspect [수표 UUID]", "수표 기록/플레이어의 주 손 수표를 확인합니다.", true, short = "수표 발행 기록 확인"),
        route("send", "limpi.financial.send", true, 3, 4, "send <플레이어> <금액>", "5% 내림 수수료로 송금합니다. 불확실하면 같은 UUID로 재시도합니다.", suggestions = mapOf(1 to "players"), short = "송금"),
        route("sell", "limpi.financial.sell", true, 1, 1, "sell", "판매창을 엽니다.", short = "판매창 열기"),
        route("sell set", "limpi.financial.admin.catalog", false, 4, 4, "sell set <아이템> <금액>", "판매 가격을 설정합니다.", suggestions = mapOf(2 to "materials"), short = "판매 가격 설정"),
        route("sell remove", "limpi.financial.admin.catalog", false, 3, 3, "sell remove <아이템>", "판매 품목을 삭제합니다.", suggestions = mapOf(2 to "materials"), short = "판매 품목 삭제"),
        route("shop", "limpi.financial.shop", true, 1, 1, "shop", "서버 상점을 엽니다.", short = "서버 상점 열기"),
        route("shop set", "limpi.financial.admin.catalog", false, 4, 4, "shop set <아이템> <비율>", "구매 가격/판매 가격 비율을 설정합니다.", suggestions = mapOf(2 to "materials"), short = "구매 가격 비율 설정"),
        route("shop remove", "limpi.financial.admin.catalog", false, 3, 3, "shop remove <아이템>", "구매 품목을 삭제합니다.", suggestions = mapOf(2 to "materials"), short = "구매 품목 삭제"),
        route("stock", "limpi.financial.stock", true, 1, 1, "stock", "주식 거래창을 엽니다.", short = "주식 거래창 열기"),
        route("stock show", "limpi.financial.stock", true, 2, 2, "stock show", "상장된 종목의 정보를 확인합니다.", short = "종목 정보"),
        route("stock holdings", "limpi.financial.stock", true, 2, 2, "stock holdings", "상장 중지 종목을 포함해 보유/원가/손익을 확인합니다.", short = "내 보유 주식·손익"),
        route("stock add", "limpi.financial.admin.stock", false, 6, 6, "stock add <이름> <코드> <아이템> <가격>", "사용하지 않은 코드로 종목을 추가합니다.", suggestions = mapOf(4 to "materials"), short = "종목 추가"),
        route("stock remove", "limpi.financial.admin.stock", false, 3, 3, "stock remove [<이름>/all]", "보유 기록을 유지하고 상장을 중지합니다.", suggestions = mapOf(2 to "stocks"), short = "상장 중지"),
        route("stock restore", "limpi.financial.admin.stock", false, 3, 3, "stock restore <코드|all>", "보존한 종목을 다시 상장합니다.", suggestions = mapOf(2 to "archivedStocks"), short = "다시 상장"),
        route("stock refix", "limpi.financial.admin.stock", false, 3, 3, "stock refix [<이름>/all]", "현재 가격을 기준 가격으로 저장합니다.", suggestions = mapOf(2 to "stocks"), short = "기준 가격 재설정"),
        route("market", "limpi.financial.market", true, 1, 1, "market", "플레이어 장터를 엽니다.", short = "장터 열기"),
        route("market add", "limpi.financial.market", true, 4, 4, "market add <코드> <가격>", "주 손 묶음을 저장 확인 후 등록합니다.", short = "손에 든 아이템 등록"),
        route("market remove", "limpi.financial.market", true, 3, 3, "market remove <코드>", "자신의 매물을 회수해 미수령으로 반환합니다.", suggestions = mapOf(2 to "listings"), short = "내 매물 회수"),
        route("market escrow", "limpi.financial.admin.market", false, 3, 3, "market escrow <owner UUID>", "중단된 매물 회수를 조회합니다.", true, short = "중단된 매물 회수 조회"),
        route("market resolve", "limpi.financial.admin.market", false, 6, Int.MAX_VALUE, "market resolve <owner UUID> <매물 UUID> removed|retained <근거>", "오프라인 매물 회수 판단을 기록합니다.", true, mapOf(4 to "removalDecision"), short = "매물 회수 판단 기록"),
        route("spy", "limpi.financial.admin.spy", false, 2, 2, "spy <플레이어>", "오프라인을 포함한 계좌 잔액을 조회합니다.", suggestions = mapOf(1 to "players"), short = "계좌 잔액 조회"),
        route("set", "limpi.financial.admin.set", false, 3, 4, "set <플레이어> <금액>", "잔액을 설정/복구하고 원장을 남깁니다.", suggestions = mapOf(1 to "players"), short = "잔액 설정·복구"),
        route("add", "limpi.financial.admin.add", false, 3, 4, "add <플레이어> <금액>", "잔액을 조정하고 원장을 남깁니다.", suggestions = mapOf(1 to "players"), short = "잔액 조정"),
        route("claim", "limpi.financial.claim", true, 1, 1, "claim", "미수령 물품을 수령합니다.", true, short = "미수령 물품 받기"),
        route("delivery", "limpi.financial.admin.delivery", false, 2, 3, "delivery <owner UUID> [지급 UUID]", "지급/저장 상태를 조회합니다.", true, short = "지급 기록 조회"),
        route("delivery resolve", "limpi.financial.admin.delivery", false, 6, Int.MAX_VALUE, "delivery resolve <owner UUID> <지급 UUID> delivered|retry <근거>", "오프라인 물리 저장을 확인한 뒤 복구 판단을 기록합니다.", true, mapOf(4 to "deliveryDecision"), short = "지급 복구 판단 기록"),
        route("removal", "limpi.financial.admin.removal", false, 2, 3, "removal <owner UUID> [회수 UUID]", "물품 회수를 조회합니다.", true, short = "회수 기록 조회"),
        route("removal resolve", "limpi.financial.admin.removal", false, 6, Int.MAX_VALUE, "removal resolve <owner UUID> <회수 UUID> removed|retained <근거>", "오프라인 회수 판단을 기록합니다.", true, mapOf(4 to "removalDecision"), short = "회수 판단 기록"),
        route("transition", "limpi.financial.admin.transition", false, 2, 3, "transition <owner UUID> [전환 UUID]", "능력/인벤토리 전환을 조회합니다.", true, short = "능력 전환 기록 조회"),
        route("transition resolve", "limpi.financial.admin.transition", false, 6, Int.MAX_VALUE, "transition resolve <owner UUID> <전환 UUID> applied|retained <근거>", "오프라인 전환 판단을 기록합니다.", true, mapOf(4 to "transitionDecision"), short = "전환 판단 기록"),
        route("sugar", "limpi.financial.admin.sugar", false, 2, 2, "sugar <owner UUID>", "적용 대기 중인 설탕 결과를 조회합니다.", true, short = "설탕 결과 조회"),
        route("sugar void", "limpi.financial.admin.sugar", false, 5, Int.MAX_VALUE, "sugar void <owner UUID> <결과 UUID> <근거>", "적용할 수 없는 설탕 결과를 환불 없이 종료하고 원장을 남깁니다.", true, short = "설탕 결과 종료"),
        route("review", "limpi.financial.admin.delivery", false, 1, 1, "review", "모든 소유자의 확인 대기 기록과 해결 명령을 조회합니다.", true, short = "확인 대기 기록 전체 조회"),
    ))
    val chest = CommandIndex(listOf(
        route("help", null, false, 1, 1, "help", "사용 가능한 상자 명령을 확인합니다.", true, short = "명령어 사용법"),
        route("help admin", null, false, 2, 2, "help admin", "사용 가능한 관리자 명령을 확인합니다.", true, short = "관리자 명령어", adminHelp = true),
        route("open", "limpi.chest.open", true, 1, 1, "open", "상자를 엽니다. 해금하지 않았다면 상자 상점을 엽니다.", true, short = "내 상자 열기"),
        route("shop", "limpi.chest.shop", true, 1, 1, "shop", "돈으로 상자 해금·업그레이드·자동 저장·엔더화를 구매합니다.", short = "상자 상점"),
        route("auto", "limpi.chest.auto", true, 1, 2, "auto [on|off]", "자동 저장을 켜거나 끕니다. 생략하면 전환합니다.", suggestions = mapOf(1 to "toggle"), short = "자동 저장 켜기/끄기"),
        route("inspect", "limpi.chest.admin.inspect", false, 2, 2, "inspect <플레이어>", "상자 크기·기능·내용물과 미해결 창 기록을 조회합니다.", true, mapOf(1 to "players"), short = "상자 조회"),
        route("resolve", "limpi.chest.admin.resolve", false, 6, Int.MAX_VALUE, "resolve <owner UUID> <창 기록 UUID> before|after <근거>", "오프라인 상자 창 기록 판단을 기록합니다.", true, mapOf(4 to "chestDecision"), short = "상자 창 기록 판단"),
    ))
    val notice = CommandIndex(listOf(
        route("help", null, false, 1, 1, "help", "사용 가능한 공지 명령을 확인합니다.", true, short = "명령어 사용법"),
        route("help admin", null, false, 2, 2, "help admin", "사용 가능한 관리자 명령을 확인합니다.", true, short = "관리자 명령어", adminHelp = true),
        route("show", "limpi.notice.show", false, 2, 3, "show <날짜> [번호]", "지난 공지를 다시 봅니다. 번호를 생략하면 그날의 공지를 모두 봅니다.", true, mapOf(1 to "noticeDates", 2 to "noticeNumbers"), short = "지난 공지 보기"),
        route("list", "limpi.notice.show", false, 1, 1, "list", "보관 중인 공지의 날짜와 번호를 확인합니다.", true, short = "보관 중인 공지 목록"),
        route("unread", "limpi.notice.show", true, 1, 1, "unread", "읽지 않은 공지를 모두 봅니다.", true, short = "읽지 않은 공지 보기"),
        route("write", "limpi.notice.admin.write", false, 2, Int.MAX_VALUE, "write <내용|JSON 텍스트>", "공지를 등록하고 접속 중인 플레이어에게 보여줍니다.", true, short = "공지 등록"),
        route("delete", "limpi.notice.admin.delete", false, 3, 3, "delete <날짜> <번호>", "공지를 삭제합니다. 번호는 다시 쓰지 않습니다.", true, mapOf(1 to "noticeDates", 2 to "noticeNumbers"), short = "공지 삭제"),
    ))
    val limpi = CommandIndex(listOf(
        route("help", null, false, 1, 1, "help", "LIMPI의 모든 도움말을 모아 봅니다.", true, short = "전체 도움말"),
    ))
    val all: Map<String, CommandIndex> get() = mapOf("ability" to ability, "financial" to financial, "chest" to chest, "notice" to notice)
}
