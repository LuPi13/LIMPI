# 마켓 운영·이전·회수 복구

2026-10-10. 마켓의 저장/거래/회수·접속 복구·GUI/명령을 연결했다. 테스트 121개와 빌드가 통과했으며 **실제 Paper 서버 동작은 전체 정비 이후에 확인한다.** [지급 복구](DELIVERY_RECOVERY.md)와 [전체 저장소 복구](STORAGE_AND_RECOVERY.md)를 함께 읽는다.

## 정본과 이전

매물은 `market_listing/<UUID>`가 정본이다. 활성/보호/예약 코드는 `market_code/<CODE>`가 예약한다. 매물 UUID는 코드를 재사용해도 달라지며, GUI에는 해당 UUID·표시 가격 quote를 보관한다. SOLD/RETURNED 이력은 지우지 않는다.

`LegacyMarketMigration`이 기존 `financial/market` payload를 모두 검사한 뒤 catalog와 완료 marker를 한 트랜잭션으로 저장한다. seller의 이름이 유일한 기존 UUID에 연결되면 그 UUID를 저장한다. 조회가 모호하거나 계좌가 없으면 OWNERSHIP_REVIEW로 보존한다. 원본 payload/아이템은 지우지 않는다. 원본의 0원 매물도 무료 거래로 보존한다. 손상 item/음수·비정수 가격/잘못된 코드는 전체 이전을 거부해 일부 매물만 반영하지 않는다.

## 사용자 명령과 화면

```text
/financial market
/financial market add <대문자·숫자 4글자 코드> <0 이상 Long 가격>
/financial market remove <코드>
/financial claim
```

마켓 권한은 `limpi.financial.market`(기본 true)다. 등록은 손에 든 **전체 스택**을 회수한다. 원본 아이템의 NBT/PDC를 보존한다. 마켓 화면은 45개 단위 페이지를 제공하고 54개 매물 제한을 제거했다. 상단의 등록된 action만 거래로 처리한다. 좌클릭은 구매, 우클릭은 온라인 seller UUID에게 흥정을 요청한다.

구매 시 구매자 차감·판매자 입금·5% 원 단위 버림 수수료·SOLD·미수령 아이템·영수증을 함께 commit한다. 수용 공간이 없으면 소유 권리를 미수령으로 남기고 claim을 안내한다. owner 회수는 RETURNED와 미수령 반환을 함께 저장한다. 관리자가 다른 사람의 매물을 회수하더라도 **물품은 원래 소유자에게 반환한다.** 실제 요청 actor도 기록한다.

## 등록 회수 프로토콜

| 단계 | 저장/복구 |
| --- | --- |
| 준비 | 원본 전체 슬롯/회수 후 슬롯·선택 슬롯·checkpoint를 `market_escrow_pending/<owner>/<listing>`에 저장. 매물은 ESCROW로 숨기고 코드를 예약 |
| 물리 회수 | 저장 경로 probe 확인 → 계획대로 선택 슬롯 회수 → playerdata 저장·checkpoint 확인 |
| 공개 | ACTIVE·escrow 완료/이력·완료 원장을 같은 DB commit으로 저장 |
| 재시작 | 새 접속에서 미수령 지급보다 먼저 escrow를 복구. saved checkpoint와 after이면 재회수하지 않고 공개. before이며 checkpoint가 다르면 회수 재실행 |
| 불일치 | REVIEW로 보호. 임의 슬롯 덮어쓰기·매물 공개·중복 반환 금지 |

등록 실패 시 거래 UUID와 owner를 로그에 남기고 추가 조작을 차단/이탈시킨다. 이미 완료한 회수의 이전 영수증으로 나중에 획득한 동일 스택을 다시 회수하지 않는다. reload 시 온라인 플레이어를 새 접속으로 취급하지 않는다. DB와 인벤토리가 하나의 트랜잭션이라는 보장은 없다.

## 관리자 확인

`limpi.financial.admin.market`(기본 op) 권한이 필요하다. 금융 기능을 꺼도 복구 명령은 유지한다.

```text
/financial market escrow <소유자 UUID>
/financial market resolve <소유자 UUID> <매물 UUID> removed <회수 확인 근거>
/financial market resolve <소유자 UUID> <매물 UUID> retained <원본 보유 확인 근거>
/financial market owner <매물 UUID> <소유자 UUID> <소유자 확인 근거>
```

resolve는 owner가 오프라인일 때 저장된 playerdata·원본/후 슬롯·checkpoint를 확인한 뒤 실행한다. removed는 실제 회수가 확인됐으므로 매물을 공개한다. retained는 원본이 실제로 남아 있는 것이 확인됐으므로 숨긴 등록을 취소·코드를 해제하고 추가 반환을 만들지 않는다. **판단 없이 retained 뒤 물품 반환까지 하면 중복이 된다.** actor/결정/근거/이전 evidence는 원장에 보존한다.

owner는 OWNERSHIP_REVIEW의 기존 매물에 명시적인 소유자 UUID를 지정하는 경로다. 기존 계좌가 존재해야 하고 원래 이름·아이템·이전 evidence를 감사 기록에 남긴다. 임의 이름 추정으로 자동 지정하지 않는다.

## 최종 서버 시험

- 기존 매물 복사본의 수/원본 아이템/PDC·Long·0원 가격·owner UUID를 대조한다. 모호한 owner의 보호/해결을 확인한다.
- 등록 저장 전·후/ACTIVE commit 전·후 강제 종료, 재접속, 저장 실패, REVIEW 해결에서 물품 수량과 매물 공개 상태를 확인한다.
- 두 구매자 경쟁/오프라인·이름 변경 seller/코드 재사용·오래된 화면/페이지 경계·drag·shift·숫자키·offhand·doubleclick를 확인한다.
- 만석 인벤토리에서 구매·소유자 회수·관리자 회수 후 권리가 보존되고 claim/재시작에서 중복 지급되지 않는지 확인한다.
- 서버 playerdata 저장 위치와 별도 인벤토리 플러그인의 호환성, 보호 중인 플레이어에게 기존 능력 이벤트가 물품을 바꾸지 않는지도 확인한다.
