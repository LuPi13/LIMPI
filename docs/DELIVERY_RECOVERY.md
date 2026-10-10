# 미수령 지급·마켓 거래의 연결 조건

2026-10-10. 정책/저장 도메인을 구현·시험했다. **실제 Paper 어댑터와 기존 GUI/명령 연결은 아직 남아 있다.** 서버 실행은 전체 정비 이후에 한다. 실제 정본/백업 절차는 [저장소·복구 절차](STORAGE_AND_RECOVERY.md)를 따른다.

## 저장 구조

- `delivery_pending/<owner UUID>/<거래 UUID>`: 원본 아이템 payload/수량/중첩 상한, 목적, 지급 상태, 전/후 슬롯 이미지, 검토 이유.
- `delivery_done/<owner UUID>/<거래 UUID>`: 지급 완료 기록. pending 삭제와 done 기록/완료 원장은 한 commit으로 저장한다. 같은 지급을 다시 stage해도 pending을 재생성하지 않는다.
- `market_listing/<매물 UUID>`: 코드/표시 이름/판매자 UUID·표시 이름/아이템/Long 가격/상태/구매자. 코드를 재사용하더라도 매물 UUID는 달라야 한다. 판매자 이름 변경이나 오래된 GUI에서 다른 매물로 오인하지 않는다.
- `migration/market_catalog_v1`: 새 catalog의 최초 이전 marker. 아직 실제 초기화 경로에서 이 이전을 실행하지 않는다. 기존 `financial/market` payload는 계속 보존한다.

## 지급 프로토콜

| 상태 | 동작/복구 |
| --- | --- |
| PENDING | 잔액/매물/보상 commit에 아이템 권리가 포함됨. 전체 수량이 안 들어가면 그대로 보관 |
| APPLYING | before/after 전체 슬롯 이미지가 DB에 저장됨. 그 뒤 물리 슬롯 변경과 checkpoint 저장을 진행 |
| DONE | 저장된 playerdata checkpoint와 after 이미지가 확인되고 완료 원장이 commit됨. 추가 지급하지 않음 |
| REVIEW | 저장 증거와 슬롯이 before/after 계약에 맞지 않음. 자동 재지급/덮어쓰기를 막고 운영자 확인 |

작업 도중 실패하면 같은 live inventory로 복구를 계속하지 않는다. 새 접속에서 먼저 중단된 지급을 확인한다. saved checkpoint가 해당 지급이고 슬롯이 after이면 아이템을 다시 넣지 않고 완료 처리한다. 다른 checkpoint이며 슬롯이 before이면 저장 전 실패로 판단해 after를 적용할 수 있다. 그 외 상태는 REVIEW로 보호한다. 한 플레이어에게 중단된 APPLYING이 둘 이상이면 정상 흐름으로 처리하지 않는다.

저장된 checkpoint는 UUID이지만 live PDC의 값은 증거가 아니다. 슬롯을 바꾼 뒤 메모리 PDC만 바뀌고 playerdata 저장이 실패할 수 있다. `DeliveryInventory.applyAndCheckpoint()` 구현은 **실제 저장된 파일/권위 있는 저장 상태**를 확인해야 한다. `Player.saveData()`가 void라는 사실 때문에 반환만으로 commit 성공이라고 간주하지 않는다. [Paper Player.saveData API](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html#saveData())

실제 어댑터에서는 아이템의 모든 구성요소를 보존하는 직렬화를 사용한다. Paper의 NBT 기반 `serializeAsBytes()`/`deserializeBytes()`는 버전 변환 경로를 제공한다. [Paper 아이템 직렬화 API](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html#serializeAsBytes())

## 마켓 정산

`MarketService.buy()`에서 구매자 차감·판매자 입금·수수료·SOLD·미수령 권리·영수증은 한 DB 트랜잭션이다. 101원 구매의 수수료는 floor(101×5%)=5원, 판매자 입금은 96원이다. 소유자 UUID 확인이 안 되는 매물은 OWNERSHIP_REVIEW로 남긴다. 추정 이름으로 다른 계좌에 입금하지 않는다. 회수는 RETURNED와 소유자의 미수령 반환을 같은 commit으로 저장한다.

아이템이 들어갈 공간이 없는 구매자는 수령을 미룰 수 있도록 기존 즉시 지급 UI를 바꿔야 한다. 실제 GUI/명령에서 구매와 물리 지급을 구분하고 미수령 상태를 안내한다. 현재 기존 MarketEvents에는 연결하지 않았으므로 기존 UI의 안전성이 개선됐다고 주장하지 않는다.

## 다음 구현·서버 시험

1. Paper 인벤토리 어댑터/저장 파일 checkpoint 판독. 메인 스레드에서 전체 수량 계획을 적용하고 저장 오류를 확인한다. 성공/실패 시 플레이어 파일과 world inventory의 관계도 검증한다.
2. 실패가 발생하면 거래 UUID를 로그에 남기고 이후 아이템 이동·다른 거래를 차단한다. 재접속 전에 임의 추가 지급을 하지 않는다. 접속 시 복구는 다른 지급보다 먼저 수행한다.
3. 미수령 수령 명령과 공간 부족 안내, 운영자 REVIEW 조회/해결·원장 기록을 구현한다. 검토 항목을 자동 초기화하거나 소유 물품을 없애지 않는다.
4. legacy 매물을 먼저 모두 검사해 UUID/원본 아이템을 보존하며 catalog로 이전한다. 조회가 모호한 판매자는 보호한다. 마켓 Holder/매물 UUID·가격 quote, Long 명령 입력, 페이지, 구매/회수 경로를 연결한다.
5. 신규 등록은 별도 회수 escrow가 필요하다. 인벤토리 물품 회수가 저장되기 전에 매물을 공개하지 않는다. 실패/재시작 후 회수 또는 반환을 판별한다.
6. 실제 서버에서 playerdata 저장 실패·DB 완료 실패·강제 종료·다른 플러그인의 인벤토리 변경·재접속·백업 복원을 확인한다. JVM/파일 시스템 전원 장애와 SQLite/인벤토리의 동시 원자성은 단위 시험만으로 보장하지 않는다.

순수 정책 6개, 실제 SQLite와 inventory fake를 결합한 지급 프로토콜 9개, 마켓 정산 7개 시험을 추가했다. 슬롯/아이템 보존·멱등 정산·실패 rollback·동시 매물 구매의 불변 조건을 시험했으며 실제 Paper 동작 검증은 아직 하지 않았다.
