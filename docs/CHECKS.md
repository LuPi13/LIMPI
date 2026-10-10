# 수표 발행·입금

2026-10-10. MONEY-03 구현/단위 검증 완료, 실제 Paper 서버 검증은 [서버 시험 계획](SERVER_TEST_PLAN.md)에서 한다. 서버 발행 기록(PDC UUID)이 있는 수표만 입금된다. 이전 버전의 수표는 지원하지 않는다.

## 정본과 거래 경계

- `limpi.sqlite3`의 `check/<UUID>`가 발행인 UUID, 양수 Long 금액, ISSUED/REDEEMED, 사용인 UUID와 입금 거래 UUID를 보존한다. 아이템의 이름/lore 금액을 입금 계산에 사용하지 않는다.
- 아이템의 `limpi:check_id`와 `limpi:check_format=1` PDC는 서버 기록을 찾는 식별자다. PDC 자체가 발행 증명이나 사용 원장을 대신하지 않는다. DB에 없는 UUID는 입금되지 않는다. 같은 UUID를 복사해도 서버 기록은 한 번만 사용할 수 있다.
- 발행은 발행인 잔액 차감·ISSUED 기록·미수령 종이·영수증을 같은 SQLite commit에 저장한다. 잔액 전액도 발행 가능하다. 수표는 중첩 상한 1로 만든다. 공간 부족이면 `/financial claim`으로 나중에 받는다.
- 입금은 저장된 금액의 잔액 가산·REDEEMED·영수증을 같은 commit에 기록한다. 오버플로/알 수 없는 UUID/사용한 수표는 금액을 바꾸지 않는다. 성공 뒤 실제 손의 같은 UUID 종이를 1개 정리하고 playerdata를 저장한다.
- 종이 정리/saveData 실패 시 종이가 남을 수 있다. 이미 REDEEMED이면 남은 종이는 가치가 없고 다시 입금되지 않는다. 종이 정리와 DB가 원자적이라고 가정하지 않는다. 마켓처럼 회수 후 가치 있는 물품을 정산하는 경우와 다른 경계다.
- 같은 거래 UUID·같은 요청은 저장된 영수증만 반환한다. 같은 UUID의 다른 요청은 거부한다. 단위 시험은 독립 DB 연결의 경쟁과 재시작/commit 실패를 포함한다. Paper 이벤트와 실제 손 정리는 서버 시험 대상으로 남는다.

## 명령과 권한

| 명령 | 조건/동작 |
| --- | --- |
| `/financial check <금액> [거래 UUID]` | 플레이어, `limpi.financial.check`(기본 true), 금융 활성화. 양수 Long, 잔액/미수령 보존. 오류의 UUID로 같은 요청 재시도 가능 |
| `/financial check redeem` | 플레이어의 주 손 수표 입금. 동일 권한. 금융 비활성화 때도 기존 채무를 입금 가능 |
| 수표 우클릭 | 금융 활성화 때 리스너 등록. 실제 이벤트 손의 서버 인벤토리를 읽는다. item 사용 DENY를 존중하고 vanilla item/block 행동은 막는다 |
| `/financial check inspect [수표 UUID]` | `limpi.financial.admin.check`(기본 op). UUID 지정 시 발행/사용 기록 조회. 인자 생략 시 플레이어 주 손 수표 UUID 조회(콘솔은 UUID 필요) |

발행/입금 전에 기존 지급·escrow 보호를 확인한다. 지급 복구에 막힌 플레이어에게 추가 금융/물리 작업을 진행하지 않는다. 권한이 있어도 보호 상태를 우회하지 않는다.

## 최종 서버 시험

- Paper 26.3에서 새 PDC/중첩 상한/원본 구성요소, 주 손·보조 손·공중/블록 우클릭, 다른 보호 플러그인의 useItemInHand DENY를 확인한다. [PlayerInteractEvent API](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerInteractEvent.html)는 공중의 vanilla 예측으로 event가 취소될 수 있으며 item/block 결과가 별개라고 설명한다. 따라서 모든 취소를 일괄 무시하지 않고 item DENY를 확인한다.
- 0/음수/Long 최대 근처, 잔액 전액 발행, 인벤토리 가득 참, 종이 복사본/이미 사용한 종이/표시 금액 변조/임의 UUID를 시험한다.
- 발행 후 지급 실패/재접속 복구, 입금 commit 후 종이 정리 실패/재시작, 금융 비활성화 중 입금/관리자 조회를 확인한다.
- DB 백업 복원은 발행/입금 원장과 playerdata 전체의 시점을 맞춘다. 원장을 지워 이미 사용한 수표를 다시 활성화하지 않는다. [저장소·복구](STORAGE_AND_RECOVERY.md)와 [지급 복구](DELIVERY_RECOVERY.md)를 함께 따른다.
