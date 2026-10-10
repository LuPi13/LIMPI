# 상점·판매·공통 회수 복구

2026-10-10. 상점/판매/능력 상점 구매 구현·단위 검증 완료, 실제 Paper 서버 검증은 전체 정비 이후로 미룬다. MONEY-01/02와 ITEM-01은 선택권 사용/뽑기/보상/초기 지급 등의 연결이 남아 있어 부분 해결 상태다.

## 상점 구매

`/financial shop`은 `MenuHolder(SHOP)`와 material code/단가 quote를 사용한다. 표시 이름/lore를 거래 근거로 사용하지 않는다. 45개 단위 페이지를 사용하고, 상단 좌클릭/Shift+좌클릭만 구매로 처리한다. 하단·숫자키·offhand·doubleclick·drag로 표시 아이템을 가져갈 수 없다. 화면 전환은 다음 틱에 해당 화면이 유지되는지 확인한다.

`ShopService.buy()`는 최신 DB 계좌·현재 catalog 가격을 확인하여 잔액 차감/전체 수량의 미수령 물품/원장 영수증을 같은 commit에 넣는다. 전체 아이템이 들어갈 공간이 없어도 권리를 지우지 않는다. `/financial claim`과 접속 지급 복구를 사용한다. 동일 거래 UUID의 replay는 catalog가 나중에 삭제/변경되었어도 기존 영수증을 반환하며 다시 차감하거나 지급하지 않는다. 일반 GUI 클릭은 새 요청 UUID를 사용한다.

Shift 구매는 아이템 중첩 상한과 현재 구매 가능한 수량 중 작은 값이다. 0원 상품도 기존 설정대로 유지하고 Shift 시 한 세트를 생성하며 0으로 나누지 않는다. 음수/손상 계좌는 무료 상품도 거래를 거부한다. 삭제/변경된 quote는 거래를 거부하고 화면을 갱신한다.

## 가격·운영 설정

- `financial/sellPrice.yml`에 없는 material의 기본 판매 단가는 기존대로 1원이다. 있는 값은 비음수 Long 정수만 허용한다. 소수/문자열/음수는 0이나 기본가로 변환하지 않는다.
- 상점은 `financial/shopRatio.yml`에 등록된 유효한 item material만 표시한다. 구매가는 `판매 단가 × 비음수 유한 비율`의 원 단위 버림이다. BigDecimal 계산과 Long exact 변환으로 기존 Double→Int 포화/큰 금액 잘림을 제거했다. 정수 비율도 Double 변환 없이 읽는다.
- 잘못된 material/비율/Long 범위 초과 상품은 해당 상품을 표시하지 않고 로그에 남긴다. 원본 설정을 자동 삭제하지 않는다. 운영자가 원인을 고친다.
- `/financial sell set <material> <비음수 Long 금액>`과 `remove <material>`은 판매 단가 설정/기본값 복귀다. `/financial shop set <material> <비음수 유한 비율>`과 `remove <material>`은 상품 비율 설정/목록 제외다. `limpi.financial.admin.catalog`(기본 op), 콘솔 지원. 저장 실패 시 성공 메시지를 보내지 않는다. 금융 활성화 때 사용한다.
- 일반 판매/상점은 `limpi.financial.sell`/`limpi.financial.shop`(기본 true)을 사용한다. 명령·메뉴·탭에서 해당 권한을 적용한다.

## 판매 화면과 회수

기존 임시 상단 인벤토리로 물품을 이동한 뒤 틱별로 삭제하는 판매/종료 반환 코드를 제거했다. Player map/틱 작업도 제거했다. 새 화면은 storage 36슬롯의 읽기 전용 복사본이다. 원본은 선택 중 계속 플레이어 인벤토리에 남으며, 닫기/이탈/서버 종료로 임시 화면의 물품을 잃거나 반환 중 addItem 잔여물을 버리지 않는다.

좌클릭으로 원본 스택 전체를 선택/해제하고 금 버튼으로 확정한다. 선택 초기화/현재 인벤토리 새로고침을 제공한다. 선택의 원본 payload/count/limit와 현재 값이 다른 슬롯은 갱신 때 선택에서 제외한다. 확정 때 가격과 전체 before 이미지가 달라졌으면 거부한다. 갑옷/보조 손/cursor는 판매 화면에 포함하지 않는다. 상단 화면은 실제 물품 저장소가 아니므로 아이템을 화면으로 옮기지 않는다.

`RemovalJournal`이 공통 회수 기록을 관리하고 `SaleSettlement`는 DB 정산만 맡는다. 이후 에메랄드/티켓 등 교환 회수에 같은 상태/증거를 사용할 수 있다. `PhysicalRemoval`은 마켓 등록에도 공유하되 기존 `market_escrow_*` 문서 형식과 복구 경로는 유지한다.

| 순서 | 저장/검증 |
| --- | --- |
| 준비 | `removal_pending/<owner>/<id>`에 kind, frozen terms(선택 슬롯별 단가), checkpoint, 전체 before/after, APPLYING 기록과 준비 영수증을 commit. 판매 이익과 현재 잔액 합의 오버플로를 검사 |
| 물리 회수 | Paper 어댑터의 probe 저장 경로 확인 후 선택 스택 회수·playerdata checkpoint 저장/force/판독. 실제 저장 후 이미지와 checkpoint가 일치해야 다음 단계 진행 |
| 정산 | 회수 완료 후 최신 잔액에 frozen 판매 이익을 가산하고 정산 원장/`removal_done`/pending 삭제를 같은 commit. 원본 선택/단가 evidence 보존 |
| 실패 | 같은 live inventory를 일반 재시도하지 않는다. 플레이어를 보호/이탈시키고 fresh login에서 저장된 before 또는 after/checkpoint로 복구. 불명확한 이미지는 REVIEW |

마켓 등록/공통 회수는 같은 owner에 미해결 작업 하나만 예약한다. 접속/claim에서 두 종류를 지급보다 먼저 복구한다. 두 미해결 회수 기록이 있으면 하나를 임의로 처리해 checkpoint를 덮지 않고 보호한다. REVIEW는 로그인 전에도 차단한다.

완료된 회수를 같은 준비/물리 호출로 재시도해도 나중에 획득한 동일 물품을 다시 회수하지 않는다. 회수 계획은 각 슬롯의 기존 payload/limit를 유지하며 count 감소/비움만 허용한다. 아이템 생성·교체·슬롯 이동·수량 증가·제거 없는 계획은 거부한다.

판매 준비 이후 다른 은행 작업으로 잔액이 Long 상한에 도달하면 정산이 거부될 수 있다. 이미 회수된 물품과 frozen 정산 권리는 pending에 남긴다. 잔액을 임의 덮거나 물품을 자동 재지급하지 않는다. 운영자는 원장을 확인하고 계좌를 정상 범위로 복구한 뒤 회수 상태를 해결한다.

## 관리자 조회·해결

`limpi.financial.admin.removal`(기본 op). 금융 비활성화 중에도 명령 사용 가능.

```text
/financial removal <owner UUID> [회수 UUID]
/financial removal resolve <owner UUID> <회수 UUID> removed|retained <확인 근거>
```

소유자가 오프라인일 때 백업/playerdata/원장/전체 before·after/checkpoint를 확인한 뒤 결정한다. removed는 실제 제거를 운영자가 확인하여 frozen 정산과 완료를 함께 저장한다. retained는 원본이 남아 있음을 확인하여 정산 없이 종료하며 물품을 다시 지급하지 않는다. actor/결정/note/기존 evidence/정산 결과를 해결 원장에 남긴다. 같은 해결 요청 UUID는 중복 정산하지 않는다. 명령 실패의 UUID로 원장을 확인하고 다른 결정을 추측하여 반복하지 않는다.

## 검증과 남은 확인

- 단위 시험: 큰 가격/전액/무료 Shift·catalog 변경·손상 계좌, 구매 commit rollback/restart replay/전체 수량; 다중/부분 슬롯 제거, 저장 전·후 오류, 정산 commit 오류, 재접속과 재회수 방지, REVIEW·관리자 removed/retained 감사 및 rollback, 가격 슬롯 일치/overflow, 회수 계획 변조와 마켓 예약 충돌.
- 총 151개 테스트 통과, `test build --console=plain` 성공. 실제 인벤토리/파일 저장·이벤트·외부 플러그인 동작은 이 결과만으로 입증하지 않는다.
- 최종 서버 시험: 인벤토리 가득 참/부분 중첩/64개·중첩 상한 1상품, 45개 초과 상점 페이지, 숫자키/shift/offhand/drag/doubleclick, 빠른 확정 반복, 선택 이후 다른 능력/외부 플러그인의 슬롯 변경, 종료·이탈, 실제 회수 저장 전·후 장애/재시작/계좌 정산 실패, 관리자 해결을 확인한다.
- DB/playerdata 백업 시점을 맞추고 복구한다. [저장소·복구](STORAGE_AND_RECOVERY.md), [지급](DELIVERY_RECOVERY.md), [마켓](MARKET_RECOVERY.md)을 함께 따른다. DB만 되돌려 이미 회수/정산된 거래를 다시 활성화하지 않는다.
- 뽑기/선택권 사용/퀘스트·초기 지급과 나머지 능력 메뉴는 아직 연결/정비가 남아 있다. 능력 상점 구매는 아래 단계에서 연결했다.

## 능력 상점 교환 추가

`/ability shop`은 `limpi.ability.shop`(기본 true)과 `MenuHolder(ABILITY_SHOP)`의 상품 코드/단가/원본 보상 quote를 사용한다. 기존 display item을 다시 만들어 비교하지 않는다. 상단 LEFT/SHIFT_LEFT만 처리하고 화면은 다음 틱에 연다. `TicketPrice`/`SelectPrice.<grade>`를 누락 없는 비음수 Long 정수로 검사한다. 잘못된 상품은 로그에 남기고 표시하지 않는다. 0은 무료 상품으로 보존한다.

일반 클릭은 1개, Shift는 최대 10개와 일반 에메랄드의 실제 가용량으로 계산한다. `InventoryPlan`은 같은 lossless payload만 회수하므로 이름/lore/PDC가 다른 에메랄드를 일반 화폐로 소모하지 않는다. 결제는 storage 슬롯만 사용하며 갑옷/보조 손/cursor를 자동 소비하지 않는다.

`ExchangeTerms`는 상품 코드/화폐 payload/총 cost/전체 reward를 frozen terms로 저장한다. `ItemExchange`가 감소 슬롯의 payload·총 수량과 cost가 같은지 검사한다. 에메랄드 제거가 playerdata에 저장된 후 회수 완료와 전체 수량 미수령 보상을 같은 commit에 기록한다. 공간이 부족해도 10개 권리를 줄이지 않는다. 이미 지불된 교환의 정산 실패는 fresh login에서 제거 없이 보상만 생성한다.

무료 상품은 제거 없는 계획을 억지로 만들지 않고 별도 멱등 DB 거래로 미수령 권리/영수증을 저장한다. 기존 마켓/판매와 동일한 removal 조회·offline removed/retained 해결을 사용한다. retained는 보상을 생성하지 않고, removed는 확인한 frozen 보상만 생성한다. 금융 비활성화 때도 `/financial claim`/`removal`이 등록되어 복구 가능하다.

기존 티켓·선택권 metadata 형식은 유지했다. **실제 티켓 사용/능력 해금·뽑기 확정과 PDC 식별/이전·이동 제한은 아직 정비 중이다.** 능력 상점 구매의 완료를 사용 경로의 완료로 보지 않는다.

추가 검증: 다중/부분 결제 슬롯, 커스텀 화폐 제외, 회수량 변조, 회수 후 보상 commit 오류/restart, 10개 중 1개만 들어갈 공간, 관리자 retained/removed, 무료 rollback/replay. 수리 설정 검사와 함께 총 162개 테스트 통과, `test build --console=plain` 성공. 실제 클릭/손/파일 저장·이탈/재시작·보호 플러그인은 최종 서버 시험에 포함한다.
