# 플레이어 저장소·거래·복구 절차

2026-10-10. `refactor/maintenance-paper26.3`의 구현 기준이다. 이 플러그인은 **새 서버에서 처음부터** 사용한다. 이전 버전의 YAML 데이터(playerData, market.yml, 옛 수표·티켓)를 가져오거나 호환하는 기능은 없다. 실제 서버 시험은 [서버 시험 계획](SERVER_TEST_PLAN.md)을 따른다.

## 정본

| 대상 | 저장 위치 | 비고 |
| --- | --- | --- |
| 플레이어 UUID·이름·잔액·능력·해금·퀘스트·보유 주식 | `plugins/LIMPI/limpi.sqlite3`, `documents`의 `player/<UUID>` | 캐시와 격리 편집 API 사용. 알 수 없는 필드도 보존 |
| 매물 | 같은 DB의 `market_listing/<UUID>`·`market_code/<code>` | 등록 escrow·원장·미수령. [마켓 복구](MARKET_RECOVERY.md) |
| 거래 원장 | 같은 DB의 `transactions` | UUID·fingerprint·영수증·변경 키·시각 |
| 수표 | 같은 DB의 `check/<UUID>` | 발행/사용 원장·미수령. [수표](CHECKS.md) |
| 판매/교환/능력 회수 | 같은 DB의 `removal_pending/<owner>/<id>`·`removal_done/<owner>/<id>` | frozen terms/전체 슬롯/checkpoint·정산. [공통 회수](SHOP_AND_SALE_RECOVERY.md) |
| 운영 설정·판매 가격·상점 비율·종목 가격 | `config.yml`, `financial/*.yml` | DB 대상이 아님 |

신규 플레이어는 첫 접속 때 profile이 만들어지고 InitialMoney·초기 티켓을 한 번 받는다. 같은 과거 이름이 여러 UUID에 연결되면 이름 조회를 거부하므로 명령어에 UUID를 지정한다.

손상된 profile(깨진 YAML, 미래 schemaVersion, 정수가 아닌 잔액)은 0으로 초기화하지 않는다. 접속 시 입력이 차단되고 UUID 안내와 함께 연결이 끊긴다. 표시에서는 손상 잔액을 "계좌 복구 필요"로 보인다. 관리자는 기록을 확인한 뒤 `/financial set <UUID> <금액>`으로 원장과 함께 복구한다.

## 거래 재시도

```text
/financial send <이름 또는 UUID> <양수 금액> [거래 UUID]
/financial set <이름 또는 UUID> <0 이상 금액> [거래 UUID]
/financial add <이름 또는 UUID> <0이 아닌 증감액> [거래 UUID]
```

set/add는 콘솔도 지원한다. 권한은 `limpi.financial.send`(기본 true), `limpi.financial.admin.set`/`.add`(기본 op)다. 송금 수수료는 금액의 5%를 원 단위로 버리며 송신자에게 추가 차감한다.

ID를 생략하면 새 UUID를 생성해 메시지로 안내한다. 결과가 불확실하면 **같은 송신자/명령/대상 UUID/금액/거래 UUID**로 재시도한다. 완료된 요청은 기존 영수증을 반환하고 잔액을 다시 변경하지 않는다. 같은 ID에 다른 요청을 넣으면 거부한다. ID를 생략해서 다시 입력하면 별도의 신규 거래다.

양쪽 잔액과 송금 영수증은 같은 commit에 들어간다. DB 저장 실패 시 성공 메시지를 보내지 않는다. 직접 SQL로 잔액을 고치면 원장과 캐시 검증을 우회하므로 관리자 변경에는 set/add를 사용한다.

## 백업과 복구

- 일반 백업은 서버 정상 종료 후 **LIMPI 데이터 폴더 전체 + 월드/playerdata**를 같은 시점에 복사한다. DB와 인벤토리는 별도 저장소이므로 시점을 맞춰야 한다.
- WAL 모드에서는 실행 중 `limpi.sqlite3` 하나만 복사하면 최신 commit이 빠질 수 있다. 남아 있는 `-wal`을 수동 삭제하지 않는다. 실행 중 DB 백업은 아래 `backup`(SQLite backup API)을 사용한다. 이것은 DB만 보존하며 인벤토리와 같은 시점 스냅샷이 아니다. [SQLite WAL 설명](https://www.sqlite.org/wal.html)
- 복구 전에 서버를 종료하고 현재 손상 상태를 별도 보존한다. 백업을 새 복사본에 복원해 DB 무결성/프로필 수/잔액/원장을 확인한 뒤 교체한다.
- DB를 지우면 모든 계좌·진행·매물이 사라지고 빈 DB로 새로 시작한다. 복구는 DB 백업 복원으로만 한다.
- SQLite schema는 user_version=1, application_id=`0x4C494D50`이다. 미래 schema/다른 application DB는 거부한다. 정체를 알 수 없는 기존 테이블을 LIMPI 테이블로 덮지 않는다.

## 운영 도구 (서버 없이 실행)

`./gradlew build`가 `build/libs/LIMPI-operations-<version>.jar`를 만든다. Java 25만 필요하며 Paper/Bukkit 클래스를 싣지 않는다. 서버 플러그인이 아니며 서버 폴더로 자동 복사하지 않는다.

```text
java -jar LIMPI-operations-1.0.0.jar help
java -jar LIMPI-operations-1.0.0.jar check plugins/LIMPI/limpi.sqlite3
java -jar LIMPI-operations-1.0.0.jar backup plugins/LIMPI/limpi.sqlite3 backups/limpi-2026-10-10.sqlite3
java -jar LIMPI-operations-1.0.0.jar export plugins/LIMPI/limpi.sqlite3 backups/limpi-2026-10-10.yml
java -jar LIMPI-operations-1.0.0.jar export-check backups/limpi-2026-10-10.yml
java -jar LIMPI-operations-1.0.0.jar restore-dry-run backups/limpi-2026-10-10.yml
java -jar LIMPI-operations-1.0.0.jar restore-copy backups/limpi-2026-10-10.yml restore/limpi.sqlite3
```

- 원본 DB는 read-only로 연다. 파일이 없으면 만들지 않고, application_id/user_version=1/`documents`·`transactions` 두 테이블/`integrity_check`가 맞지 않으면 거부한다. SQLite는 read-only WAL 접근에도 빈 `-wal`/`-shm`을 만들 수 있으며 commit을 담지 않는다.
- 모든 출력은 **이미 있는 디렉터리의 새 파일**이다. 같은 디렉터리 임시 파일에 쓰고 force·검증 후 덮어쓰기 없이 이동한다. 기존 파일·symlink·원본 자신은 덮지 않는다.
- `backup`은 실행 중인 writer가 있어도 commit된 WAL 내용을 포함하고, 사본의 문서·원장 SHA-256을 원본과 비교한다.
- `export`는 문서 payload 원문과 원장 id/fingerprint/영수증/변경 키/시각을 YAML로 쓰고 길이 접두 SHA-256을 붙인다. import는 SafeConstructor(객체 tag 금지)·중복 키 거부·checksum 일치를 요구한다.
- `restore-copy`는 **새 DB 파일만** 만든다. 서버 DB를 교체하지 않는다. 원장을 그대로 복원하므로 같은 거래 UUID 재시도는 replay가 되고 재정산되지 않는다. 교체는 서버를 정상 종료하고 현재 DB·WAL을 별도 보존한 뒤 운영자가 직접 한다.
- 검사 결과: 플레이어 수·잔액 합계·`*_pending` 수·REVIEW·음수/누락 잔액·중복 이름은 경고, 깨진 YAML/정수가 아닌 금액/미래 문서 schema/owner·id와 key 불일치/색인 없는 `market_code`/음수 주식 보유는 오류다. 오류가 있으면 종료 코드 2이며 `restore-copy`는 거부한다. `backup`/`export`는 오류 상태도 보존 목적으로 게시한다(종료 코드 2). 오류 메시지는 payload를 출력하지 않는다.
- 구조 검사다. 아이템 NBT 유효성, live 인벤토리와 pending/REVIEW의 실제 일치는 판정하지 않는다.

## 남은 검증

- Minecraft 인벤토리와 SQLite는 원자적으로 함께 저장되지 않는다. 지급/회수의 pending·checkpoint·REVIEW 계약은 [지급 복구](DELIVERY_RECOVERY.md)를 따른다.
- 실제 재시작/강제 종료 복구, backup→restore-copy→교체 절차, JVM native access(`--enable-native-access=ALL-UNNAMED` 경고) 점검은 서버 시험에서 확인한다.
- DB 접근은 메인 스레드의 동기 처리다. 거래/접속 저장 지연은 서버에서 측정한다.

사용한 드라이버: [sqlite-jdbc 3.53.4.0](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.4.0). DB 트랜잭션의 보장은 [SQLite atomic commit 설명](https://www.sqlite.org/atomiccommit.html)을 따른다.
