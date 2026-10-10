# 플레이어 저장소·거래·복구 절차

2026-10-10. `refactor/maintenance-paper26.3`의 구현 기준이다. 실제 서버 설치와 시험은 전체 정비 이후에 진행한다. 구현 진척은 [구현 기록](IMPLEMENTATION_LOG.md)을 확인한다.

## 정본과 호환 경계

| 대상 | 현재 저장 위치 | 비고 |
| --- | --- | --- |
| 플레이어 UUID·이름·잔액·능력·해금·퀘스트·보유 주식 | `plugins/LIMPI/limpi.sqlite3`, `documents`의 `player/<UUID>` | payload에 기존 YAML 필드를 보존. 캐시와 격리 편집 API 사용 |
| 매물 | 같은 DB의 `market_listing/<UUID>`·`market_code/<code>` | 원장/등록 escrow/미수령·GUI/명령 연결. `financial/market`은 최초 이전 원본으로 보존 |
| 송금·관리자 잔액 변경 원장 | 같은 DB의 `transactions` | UUID·fingerprint·영수증·변경 키·시각 |
| 수표 | 같은 DB의 `check/<UUID>`·`check_legacy/<fingerprint>` | 발행/사용 원장·미수령 연결. [기존 수표 전환](CHECKS_AND_LEGACY_MIGRATION.md)은 관리자 근거 확인 후 수행 |
| 판매/교환 회수 | 같은 DB의 `removal_pending/<owner>/<id>`·`removal_done/<owner>/<id>` | frozen terms/전체 슬롯/checkpoint·정산. [공통 회수 복구](SHOP_AND_SALE_RECOVERY.md) 참조 |
| 최초 이전 정보 | 같은 DB의 `migration/legacy_yaml_v1` | 플레이어 수·잔액 합계·원본 SHA-256 |
| 운영 설정·판매 가격·상점 비율·종목 가격 | 기존 YAML | DB 이전 대상이 아님 |
| 기존 플레이어/market YAML | 기존 파일 + `backups/sqlite-v1/` | 최초 이전 원본. 이후 DB와 자동 동기화하지 않음 |

플레이어 코드명/UUID를 재발급하지 않는다. 알 수 없는 필드와 빈 섹션도 보존한다. 같은 과거 이름이 여러 UUID에 연결되면 이름 조회를 거부한다. 이 경우 명령어에 UUID를 지정한다.

## 최초 이전

1. 실제 배포 전에 서버를 정상 종료하고 기존 플러그인 데이터와 월드/playerdata 전체의 복사본을 보관한다. 구현 중에는 실제 서버를 변경하지 않았다.
2. 복사본을 사용하는 격리 서버에서 최초 enable을 검증한다. importer는 모든 `playerData/<UUID>.yml`과 `financial/market.yml`을 읽고 YAML/금액/schema를 검증한 뒤 저장을 시작한다. enable 전에 아래 운영 도구의 `legacy-check`로 복사본의 UUID 파일명·금액·schema·주식 보유를 구조 검사할 수 있다(가져오기는 하지 않음).
3. 각 원본을 `backups/sqlite-v1/playerData/`와 `backups/sqlite-v1/financial/market.yml`에 보존한다. 이미 있는 백업이 현재 원본과 다르면 덮지 않고 거부한다.
4. 모든 payload와 완료 marker를 한 SQLite 트랜잭션으로 저장한다. 로그에 가져온 플레이어 수와 잔액 합계가 나온다. 합계는 Long 합산 오버플로를 피하도록 BigInteger로 계산한다. marker의 각 원본 hash를 비교하고 UUID/능력/해금/퀘스트/매물도 복사본과 대조한다.
5. commit 전에 실패하면 프로필/매물/marker가 부분 저장되지 않는다. YAML과 만들어진 백업은 보존한다. 오류 원인을 수정하고 재시도한다. 이미 성공한 DB는 재시작 때 과거 YAML을 다시 가져오지 않는다.

소수/문자열/Long 초과 잔액, 잘못된 schemaVersion, 미래 버전, 깨진 YAML은 자동 0 초기화 대신 이전을 거부한다. 음수 legacy 잔액은 원본 그대로 남기고 은행 거래를 거부한다. 관리자가 기록을 확인한 후 `/financial set <UUID> <정상 금액>`으로 복구하면 원장에 이전 음수 잔액이 남는다.

## 거래 재시도

```text
/financial send <이름 또는 UUID> <양수 금액> [거래 UUID]
/financial set <이름 또는 UUID> <0 이상 금액> [거래 UUID]
/financial add <이름 또는 UUID> <0이 아닌 증감액> [거래 UUID]
```

set/add는 콘솔도 지원한다. 권한은 `limpi.financial.send`(기본 true), `limpi.financial.admin.set`/`.add`(기본 op)다. 송금 수수료는 금액의 5%를 원 단위로 버리며 송신자에게 추가 차감한다.

ID를 생략하면 새 UUID를 생성해 메시지로 안내한다. 결과가 불확실하면 **같은 송신자/명령/대상 UUID/금액/거래 UUID**로 재시도한다. 완료된 요청은 기존 영수증을 반환하고 잔액을 다시 변경하지 않는다. 같은 ID에 다른 요청을 넣으면 거부한다. ID를 생략해서 다시 입력하면 별도의 신규 거래다. 자동화에서는 요청 전에 UUID를 생성·보관해 전달한다.

양쪽 잔액과 송금 영수증은 같은 commit에 들어간다. DB 저장 실패 시 성공 메시지를 보내지 않는다. 오류 로그의 거래 UUID와 `transactions`의 id로 확인할 수 있다. 직접 SQL로 잔액을 고치면 원장과 캐시 검증을 우회하므로 정상적인 관리자 변경에는 set/add를 사용한다.

## 백업과 복구

- 일반 백업은 서버 정상 종료와 DB 연결 종료 후 **LIMPI 데이터 폴더 전체 + 월드/playerdata**를 같은 시점에 복사한다. DB뿐 아니라 운영 YAML과 인벤토리도 함께 보관한다.
- WAL 모드에서는 실행 중 `limpi.sqlite3` 하나만 복사하면 최신 commit이 빠질 수 있다. 남아 있는 `-wal`을 수동 삭제하지 않는다. 비정상 종료 상태를 확보할 때는 DB·WAL·관련 파일을 함께 보존한다. 실행 중 DB 백업은 아래 `backup`(SQLite backup API)을 사용한다. 이것은 DB만 보존하며 월드/playerdata 인벤토리와 같은 시점 스냅샷이 아니다. [SQLite WAL 설명](https://www.sqlite.org/wal.html)
- 복구 전에 서버를 종료하고 현재 손상 상태를 별도 보존한다. 정상 종료 시점의 전체 백업을 새 복사본에 복원해 DB 무결성/프로필 수/잔액/원장/능력/퀘스트를 확인한다. 실제 복구 검증은 최종 격리 서버 시험 항목이다.
- 최초 가져오기 직후 새 게임 변경이 전혀 없었다면 이전 YAML과 이전 JAR로 돌아갈 수 있다. **DB에서 거래/진행/매물 변경이 발생한 이후에는 오래된 YAML로 돌아가면 이후 변경을 잃는다.** 그때는 DB 백업을 복원하거나 검증된 export/역변환이 필요하다. DB를 지워 과거 YAML을 재가져오는 방식으로 복구하지 않는다.
- SQLite schema는 user_version=1, application_id=`0x4C494D50`이다. 미래 schema/다른 application DB는 거부한다. 정체를 알 수 없는 기존 테이블을 LIMPI 테이블로 덮지 않는다.

## 운영 도구 (서버 없이 실행)

`./gradlew build`가 `build/libs/LIMPI-operations-<version>.jar`를 만든다. Java 25만 필요하며 Paper/Bukkit 클래스를 싣지 않는다. 서버 폴더로 자동 복사하지 않는다.

```text
java -jar LIMPI-operations-1.0.0.jar help
java -jar LIMPI-operations-1.0.0.jar check plugins/LIMPI/limpi.sqlite3
java -jar LIMPI-operations-1.0.0.jar backup plugins/LIMPI/limpi.sqlite3 backups/limpi-2026-10-10.sqlite3
java -jar LIMPI-operations-1.0.0.jar export plugins/LIMPI/limpi.sqlite3 backups/limpi-2026-10-10.yml
java -jar LIMPI-operations-1.0.0.jar export-check backups/limpi-2026-10-10.yml
java -jar LIMPI-operations-1.0.0.jar restore-dry-run backups/limpi-2026-10-10.yml
java -jar LIMPI-operations-1.0.0.jar restore-copy backups/limpi-2026-10-10.yml restore/limpi.sqlite3
java -jar LIMPI-operations-1.0.0.jar legacy-check COPY_OF_plugins/LIMPI
```

- 원본 DB는 read-only로 연다. 파일이 없으면 만들지 않고, application_id/user_version=1/`documents`·`transactions` 두 테이블/`integrity_check`가 맞지 않으면 거부한다. 미래 schema나 다른 DB를 초기화·migration·복구하지 않는다. SQLite는 read-only WAL 접근에도 빈 `-wal`/`-shm`을 만들 수 있으며 commit을 담지 않는다. 이 파일들을 수동 삭제하지 않는다.
- 모든 출력은 **이미 있는 디렉터리의 새 파일**이다. 같은 디렉터리 임시 파일에 쓰고 force·검증 후 덮어쓰기 없이 이동한다. 기존 파일·symlink·원본 자신은 덮지 않는다. 실패하면 새 출력이 게시되지 않는다.
- `backup`은 실행 중인 writer가 있어도 commit된 WAL 내용을 포함한다. 백업 사본을 다시 열어 문서·원장 SHA-256이 원본 스냅샷과 같은지 확인한다.
- `export`는 문서 payload 원문(알 수 없는 사용자 필드 포함)과 원장 id/fingerprint/영수증/변경 키/시각을 YAML로 쓰고 길이 접두 SHA-256을 붙인다. import는 SafeConstructor(객체 tag 금지)·중복 키 거부·checksum 일치를 요구한다. 잔액 한 자리라도 바뀌면 거부한다.
- `restore-copy`는 **새 DB 파일만** 만든다. 서버 DB를 교체하지 않는다. 원장을 그대로 복원하므로 같은 거래 UUID 재시도는 영수증 replay가 되고 재정산되지 않는다. 교체는 서버를 정상 종료하고 현재 DB·WAL을 별도 보존한 뒤 운영자가 직접 한다.
- 검사 결과: 플레이어 수·잔액 합계(BigInteger)·`*_pending` 수·REVIEW·음수 legacy 잔액·중복 이름은 경고, 깨진 YAML/소수·문자열·Long 초과 금액/미래 문서 schema/owner·id와 key 불일치/색인 없는 `market_code`/음수·overflow 주식 보유는 오류다. 오류가 있으면 종료 코드 2이며 `restore-copy`는 거부한다. 오류 상태도 `backup`/`export`는 법의학 보존을 위해 게시한다(종료 코드 2).
- 오류 메시지는 payload 일부를 출력하지 않고 문서 key와 예외 종류만 보인다.
- 검사는 구조 검사다. 직렬화된 아이템 NBT의 유효성, live 인벤토리와 pending/REVIEW의 실제 일치, Paper 이벤트는 판정하지 않는다. REVIEW 판단은 기존 관리자 명령과 인벤토리 증거로 한다.

## 아직 남은 구현·검증

- 지급/마켓 정책·저장·플랫폼·GUI/명령을 연결했다. checkpoint/DONE/REVIEW 계약은 [지급 복구](DELIVERY_RECOVERY.md), 등록 escrow/원본 이전·관리자 판단은 [마켓 복구](MARKET_RECOVERY.md)에 기록했다.
- 선택권 사용/뽑기/보상·초기 지급을 원장과 복구 상태에 연결해야 한다. 마켓·수표·판매·상점·능력 상점 구매 연결 완료를 사용/보상 경로의 완료로 처리하지 않는다.
- Minecraft 인벤토리와 SQLite는 별도의 저장소다. 지급/회수에는 pending 상태와 저장 증거, 재시작 후 판별, 불확실한 상태의 관리자 확인 경로가 필요하다. DB 성공만으로 아이템 지급 완료를 확정하지 않는다.
- 운영자용 검사/export/백업/복사본 복구 도구는 구현했다(단위·JAR 실행 확인). 실제 운영 데이터 복사본에서의 export→restore-copy→enable 비교, 재시작/강제 종료 복구 시험, 서버 JVM native access(`--enable-native-access=ALL-UNNAMED` 경고) 점검과 전체 서버 시험은 남아 있다.
- 현재 DB 접근은 메인 스레드의 동기 처리다. 능력 hot path는 캐시를 사용하지만 거래/접속 저장 지연은 최종 서버에서 측정하고, Bukkit 객체를 넘기지 않는 저장 실행 구조를 추가 검토한다.

사용한 드라이버: [sqlite-jdbc 3.53.4.0](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.4.0). DB 트랜잭션의 보장은 [SQLite atomic commit 설명](https://www.sqlite.org/atomiccommit.html)을 따른다. 전원 장애/파일 시스템/인벤토리 복구까지 단위 테스트로 보장했다고 주장하지 않는다.
