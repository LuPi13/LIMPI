# 미수령 지급·마켓 거래의 연결 조건

2026-10-10. 지급/마켓의 정책·저장 도메인·Paper 어댑터·접속 복구·수령/관리자 검토·등록 escrow·기존 마켓 GUI/명령을 연결했다. 실제 서버 실행은 전체 정비 이후에 한다. [마켓 복구](MARKET_RECOVERY.md)와 [전체 저장소 복구](STORAGE_AND_RECOVERY.md)를 함께 읽는다.

## 저장 구조

- `delivery_pending/<owner UUID>/<거래 UUID>`: 원본 아이템 payload/수량/중첩 상한, 인벤토리 slotLimit, 목적, 지급 상태, 시도별 checkpoint UUID, 전/후 슬롯 이미지, 검토 이유.
- `delivery_done/<owner UUID>/<거래 UUID>`: 지급 완료 기록. pending 삭제와 done 기록/완료 원장은 한 commit으로 저장한다. 같은 지급을 다시 stage해도 pending을 재생성하지 않는다.
- `market_listing/<매물 UUID>`: 코드/표시 이름/판매자 UUID·표시 이름/아이템/Long 가격/상태/구매자. 코드를 재사용하더라도 매물 UUID는 달라야 한다. 판매자 이름 변경이나 오래된 GUI에서 다른 매물로 오인하지 않는다.

## 지급 프로토콜

| 상태 | 동작/복구 |
| --- | --- |
| PENDING | 잔액/매물/보상 commit에 아이템 권리가 포함됨. 전체 수량이 안 들어가면 그대로 보관 |
| APPLYING | before/after 전체 슬롯 이미지가 DB에 저장됨. 그 뒤 물리 슬롯 변경과 checkpoint 저장을 진행 |
| DONE | 저장된 playerdata checkpoint와 after 이미지가 확인되고 완료 원장이 commit됨. 추가 지급하지 않음 |
| REVIEW | 저장 증거와 슬롯이 before/after 계약에 맞지 않음. 자동 재지급/덮어쓰기를 막고 운영자 확인 |

작업 도중 실패하면 같은 live inventory로 복구를 계속하지 않는다. 새 접속에서 먼저 중단된 지급을 확인한다. saved checkpoint가 해당 지급이고 슬롯이 after이면 아이템을 다시 넣지 않고 완료 처리한다. 다른 checkpoint이며 슬롯이 before이면 저장 전 실패로 판단해 after를 적용할 수 있다. 그 외 상태는 REVIEW로 보호한다. 한 플레이어에게 중단된 APPLYING이 둘 이상이면 정상 흐름으로 처리하지 않는다.

저장된 checkpoint는 UUID이지만 live PDC의 값은 증거가 아니다. 슬롯을 바꾼 뒤 메모리 PDC만 바뀌고 playerdata 저장이 실패할 수 있다. `PaperDeliveryInventory`는 먼저 probe를 실제 파일에 저장·확인한 후 슬롯을 바꾸고 해당 시도의 checkpoint를 저장·확인한다. `PlayerCheckpointFile`은 `.dat`를 force하고 gzip NBT의 `BukkitValues`를 읽는다. `Player.saveData()` 반환만으로 commit 성공이라고 간주하지 않는다. [Paper Player.saveData API](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html#saveData())

`PlayerDataDirectory: ''`의 기본 경로는 첫 서버 월드의 `playerdata`다. startup 로그의 `Delivery checkpoint directory`를 확인한다. 별도 저장 위치가 있으면 server worldContainer 기준 상대 경로나 절대 경로를 지정한다. probe가 해당 파일에 나타나지 않으면 물품 변경을 진행하지 않는다. playerdata를 외부 DB에 저장하는 플러그인에는 이 파일 어댑터를 그대로 적용할 수 없으며 해당 저장소의 증거 어댑터가 필요하다.

기존 checkpoint 필드가 없는 기록은 지급 UUID를 읽기 호환으로 사용한다. 새 시도는 별도 UUID를 기록하며 관리자 retry 후에도 새 UUID를 사용한다. 인벤토리의 maxStackSize와 아이템 구성요소의 maxStackSize 중 작은 값으로 계획한다.

실제 어댑터에서는 아이템의 모든 구성요소를 보존하는 직렬화를 사용한다. Paper의 NBT 기반 `serializeAsBytes()`/`deserializeBytes()`는 버전 변환 경로를 제공한다. [Paper 아이템 직렬화 API](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html#serializeAsBytes())

## 마켓 정산

`MarketService.buy()`에서 구매자 차감·판매자 입금·수수료·SOLD·미수령 권리·영수증은 한 DB 트랜잭션이다. 101원 구매의 수수료는 floor(101×5%)=5원, 판매자 입금은 96원이다. 소유자 UUID 확인이 안 되는 매물은 OWNERSHIP_REVIEW로 남긴다. 추정 이름으로 다른 계좌에 입금하지 않는다. 회수는 RETURNED와 소유자의 미수령 반환을 같은 commit으로 저장한다.

공간이 없는 구매자의 권리는 미수령으로 보존한다. 실제 GUI/명령은 정산과 수령을 구분하고 claim을 안내한다. 등록 회수·원본 이전·운영자 해결도 연결했으며 [마켓 복구 절차](MARKET_RECOVERY.md)에 기록했다.

상점 구매·수표 발행도 차감과 미수령을 같은 commit으로 연결했다. 판매/교환의 공통 `RemovalJournal`은 마켓과 owner의 회수 checkpoint 예약을 공유하며, 접속/claim에서 마켓·공통 회수를 지급보다 먼저 복구한다. [상점·판매 복구](SHOP_AND_SALE_RECOVERY.md), [수표](CHECKS.md)를 함께 따른다. 능력 상점/뽑기/선택권/초기·퀘스트 보상은 이후 단계에서 연결했다.

## 수령과 관리자 검토

```text
/financial claim
/financial delivery <소유자 UUID> [지급 UUID]
/financial delivery resolve <소유자 UUID> <지급 UUID> delivered <저장 확인 근거>
/financial delivery resolve <소유자 UUID> <지급 UUID> retry <미지급 확인 근거>
```

claim은 `limpi.financial.claim`(기본 true), 조회/해결은 `limpi.financial.admin.delivery`(기본 op)다. 새 접속 시 복구/수령을 자동 수행하고, 공간 부족은 claim을 안내한다. enable 시 이미 접속한 플레이어를 fresh login으로 취급하지 않는다. 실패 시 UUID와 오류를 로그에 남기고 인벤토리 조작·명령을 막은 뒤 이탈시킨다. REVIEW가 있으면 다음 접속도 거부한다.
`EnableFinancialSystem: false`여도 이미 확정된 미수령 권리가 막히지 않도록 claim/지급 기록·해결 명령은 유지한다. 다른 금융 명령은 비활성화 안내를 반환한다.

해결 명령은 owner가 오프라인일 때만 실행한다. DB의 before/after·checkpoint 기록과 실제 playerdata/아이템 소유를 확인하고 근거를 입력한다. delivered는 지급이 확인됐다는 운영자의 결정으로 DONE을 기록한다. retry는 미지급이 확인됐다는 결정으로 물품 권리를 PENDING으로 돌린다. actor/결정/근거/이전 evidence는 감사 원장에 보존하며 원본 물품을 자동 삭제하지 않는다. 잘못된 운영자 결정까지 자동 판별하는 명령은 아니므로 saved checkpoint/실제 물품 확인이 필요하다.

## 다음 구현·서버 시험

1. 구현한 Paper 어댑터/파일 checkpoint/force/접속 복구/실패 보호를 최종 실제 서버에서 확인한다. 정상 동작과 저장 실패/재시작을 구분한다.
2. 취소를 무시하는 기존 능력 이벤트와 다른 플러그인의 아이템 변경을 검토한다. 지급 실패로 보호 중인 플레이어를 임의 변경하지 않도록 나머지 수명/취소 처리를 정비한다.
3. 구현한 claim/관리자 조회/해결의 실제 명령 동작과 권한, 오프라인 확인, 감사 기록을 최종 서버에서 시험한다.
4. 구현한 Holder/UUID quote·Long 입력·구매/회수·페이지를 최종 서버에서 확인한다.
5. 구현한 신규 등록 escrow의 회수 전/후 저장·공개·실패/재시작 복구·관리자 removed/retained 결정을 최종 서버에서 확인한다.
6. 실제 서버에서 playerdata 저장 실패·DB 완료 실패·강제 종료·다른 플러그인의 인벤토리 변경·재접속·백업 복원을 확인한다. JVM/파일 시스템 전원 장애와 SQLite/인벤토리의 동시 원자성은 단위 시험만으로 보장하지 않는다.

초기 순수 정책 6개·지급 프로토콜 9개·마켓 정산 7개에 인벤토리 상한 1개·실제 gzip NBT 파일 4개·관리자 해결 4개 시험을 추가했다. 전체 저장소/경제/페이지를 포함해 총 121개 시험이 통과했다. 실제 Paper 이벤트/saveData/복구 동작 검증은 아직 하지 않았다. 이후 마켓 escrow·이전·코드 재사용·무료 매물 시험 14개를 추가했다.

## 사망 화면 재접속·정산 거부 (2026-10-10 리뷰 반영)

- 죽은 상태(사망 화면)로 재접속하거나 사망 중 claim이 호출되면 인벤토리 적용/저장이 불가능하다. 예전에는 복구가 매번 같은 이유로 실패해 APPLYING이 남고 매 접속 kick되어 리스폰 버튼에 도달할 수 없었다. 이제 claim은 입력만 차단하고 복구를 미루며, 리스폰 다음 틱에 fresh 복구를 실행한다. 사망 중에는 지급/회수/전환을 시작하지 않는다.
- 물리 회수가 저장된 뒤 계좌 규칙(예: prepare 이후 잔액이 늘어 판매 대금 합산이 Long을 넘는 경우)으로 정산이 거부되면, 매 접속 같은 실패를 반복하지 않고 REVIEW로 넘긴다. 관리자는 잔액을 `/financial set|add`로 확인·조정한 뒤 `removal resolve ... removed`로 frozen 정산을 기록한다. retained는 물품이 실제 남아 있을 때만 사용한다.
