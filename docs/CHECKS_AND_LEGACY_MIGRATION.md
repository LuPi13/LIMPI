# 수표 발행·입금·기존 수표 전환

2026-10-10. MONEY-03 구현/단위 검증 완료, 실제 Paper 서버 검증은 전체 정비 이후로 미룬다.

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
| `/financial check inspect [수표 UUID]` | `limpi.financial.admin.check`(기본 op). UUID 지정 시 발행/사용 기록 조회. 인자 생략 시 플레이어 주 손 새 UUID 또는 legacy fingerprint 조회 |
| `/financial check import <현재 소유자 UUID> <확인한 발행인 UUID> <확인 근거>` | 동일 관리자 권한. owner 접속/주 손 legacy 수표/기존 owner·issuer 계좌 필요. 금융 비활성화 때도 가능 |

발행/입금 전에 기존 지급·escrow 보호를 확인한다. 지급 복구에 막힌 플레이어에게 추가 금융/물리 작업을 진행하지 않는다. 권한이 있어도 보호 상태를 우회하지 않는다.

## 기존 수표 정책

기존 공개키·표시 금액·발행인 이름·날짜의 공개 해시는 누구나 다시 계산할 수 있다. 형식 검증 성공으로 과거 서버 발행이나 잔액 차감을 증명할 수 없다. 기존 수표를 새 DB에 자동 발행하거나 바로 입금하지 않는다.

1. 서버 전환 전 전체 DB/playerdata/기존 YAML·로그를 백업한다. 기존 수표 원본을 보존한다.
2. `inspect`로 형식·semantic fingerprint를 확인한다. 형식 성공은 발행 승인 근거가 아니다. 발행인 UUID와 기존 차감/이전 증빙을 운영자가 별도로 확인한다. 이름만으로 현재 UUID를 임의 추정하지 않는다.
3. 확인한 owner/issuer UUID와 구체적인 근거를 넣어 `import`한다. 새 ISSUED 수표와 owner 미수령 지급·전환 영수증·`check_legacy/<fingerprint>`를 한 commit에 저장한다. 과거 차감을 인정하는 전환이므로 발행인에게 다시 차감하지 않는다.
4. 원본 종이는 지우지 않는다. legacy 입금은 계속 거부되며 같은 semantic fingerprint를 다른 거래로 승인할 수 없다. stack 여러 장/복사본의 같은 필드는 하나의 claim으로만 전환한다. 구형 발행 충돌로 서로 다른 정당한 수표가 같은 필드를 가진 경우에도 자동 다중 보상하지 않고 별도 운영 판단이 필요하다.
5. 공간 부족은 새 수표를 미수령으로 보존한다. 성공 메시지의 거래 UUID와 영수증 actor/owner/issuer/amount/fingerprint/note를 기록한다. commit 결과가 불확실하면 UUID로 원장을 먼저 확인하고 재승인하지 않는다. 다른 ID로 같은 fingerprint를 요청하면 중복 전환을 거부한다.

전환은 관리자가 기존 채무를 인정해 새 claim을 만드는 행위다. 공개 해시만으로 승인하면 위조 채무를 인정할 수 있다. 증빙이 없는 원본의 자동 전환은 구현하지 않았다. 별도 일괄 승인/취소 도구는 없다.

## 최종 서버 시험

- Paper 26.3에서 새 PDC/중첩 상한/원본 구성요소, 주 손·보조 손·공중/블록 우클릭, 다른 보호 플러그인의 useItemInHand DENY를 확인한다. [PlayerInteractEvent API](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerInteractEvent.html)는 공중의 vanilla 예측으로 event가 취소될 수 있으며 item/block 결과가 별개라고 설명한다. 따라서 모든 취소를 일괄 무시하지 않고 item DENY를 확인한다.
- 0/음수/Long 최대 근처, 잔액 전액 발행, 인벤토리 가득 참, 종이 복사본/이미 사용한 종이/표시 금액 변조/임의 UUID를 시험한다.
- 발행 후 지급 실패/재접속 복구, 입금 commit 후 종이 정리 실패/재시작, legacy 승인 후 공간 부족과 중복 승인, 금융 비활성화 중 입금/관리자 조회를 확인한다.
- DB 백업 복원은 발행/입금 원장과 playerdata 전체의 시점을 맞춘다. 원장을 지우거나 과거 YAML 재수입으로 이미 사용한 수표를 다시 활성화하지 않는다. [저장소·복구](STORAGE_AND_RECOVERY.md)와 [지급 복구](DELIVERY_RECOVERY.md)를 함께 따른다.
