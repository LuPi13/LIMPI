# 전체 정비 구현 기록

## 작업 계약

- 사용자 승인: 브랜치를 만들고 정비 계획의 전체 문제 수정과 구조 개선을 진행한다. 커밋·GitHub push 가능. **main 병합 금지.**
- 작업 브랜치: `refactor/maintenance-paper26.3`.
- 실제 서버를 지금 실행할 수 없으므로 **전체 구현 후 서버 시험**을 한다. 미검증 사실은 남기되 서버 시험을 이유로 구현을 중단하지 않는다.
- 항목의 `구현 완료`와 `서버 검증 완료`를 분리한다. 전체 목표는 현재 진행 중이다.
- 기존 UUID/능력 코드명/잔액/해금/퀘스트/알 수 없는 사용자 필드를 보존한다.

## 완료한 변경

### 2026-10-10 / 기준점

- `b20b545`: Paper 26.3·Java 25 전환과 최초 분석 문서. origin에 push 완료.
- main에서 작업 브랜치를 생성했다. main 자체에는 커밋/병합하지 않았다.

### 2026-10-10 / 저장·조회 기반 정비

- `82b7912`: 저장·조회 기반 정비와 테스트. origin에 push 완료.

- `player/PlayerRepository`, `PlayerProfile`, `storage/YamlPlayerRepository` 도입. 기존 `FileManager`는 호출부 전환을 위한 얇은 호환 facade로 줄였다.
- 접속 프로필을 캐시하고 오프라인 프로필은 LRU 256개로 제한한다. 이탈 시 해당 캐시를 해제한다. 이름/UUID 인덱스를 초기 로드 때 구성하고 이름 변경 시 갱신한다.
- `.ability` 조회는 캐시된 코드명 → 단일 능력 레지스트리의 ID map 조회를 사용한다. `AbilityManager()` 생성 47곳을 제거했다. 능력 정의·엔티티 분류 목록을 조회 때마다 다시 만들지 않는다.
- YAML 편집은 격리된 스냅샷으로 한다. 저장 전 cache를 수정하지 않으며, 오래된 스냅샷은 변경한 필드만 최신 데이터에 병합한다. 동일 필드가 충돌하면 덮어쓰지 않고 실패한다.
- `AtomicFiles`는 대상과 같은 디렉터리의 임시 파일을 force 후 atomic move로 교체한다. 지원되지 않는 atomic move를 비원자적 덮어쓰기로 대체하지 않는다.
- 플레이어/경제/능력 YAML의 저장 실패를 더 이상 출력만 하고 숨기지 않는다. 기존 파일을 보존하고 예외를 전달한다. YAML 문법 오류도 빈 설정으로 바꾸지 않고 전달한다.
- 신규/기존 서버 플레이어 모두 접속 시 `ensure()`를 사용한다. 기존 잔액/해금/퀘스트는 보존하며 누락된 기본값만 채운다. 플레이어 이름은 현재 이름으로 갱신한다.
- 최초 schemaVersion=1 보완 전 기존 파일을 `playerData/backups/{uuid}.pre-schema-1.yml`에 보존한다. 더 높은 schemaVersion은 다운그레이드하지 않고 거부한다.
- `economy/Money`에 비음수 Long 금액, checked 합/차/곱, 정수 수수료 계산을 구현했다. **기존 경제 호출부 전체 연결은 다음 단계로 남아 있다.**
- 서버 전역 `System.out` 교체를 제거했다.

### 2026-10-10 / 주식 정책·거래·메뉴 정비

- `ffd1f0c`: 주식 정책·거래·메뉴 정비와 테스트. origin에 push 완료.

- `stock/StockPricePolicy`, `StockHolding`, `StockSchedule`, `StockTradingService`를 분리했다. 가격/보유 계산은 Paper 이벤트나 GUI와 독립적으로 시험한다.
- 주식 금액·보유 수량·취득 원가는 Long으로 읽고 Money로 검증/계산한다. 매수/매도의 계좌와 보유 갱신은 같은 플레이어 프로필의 단일 원자 저장으로 수행하며 성공한 뒤에 메시지를 보낸다.
- `StockPriceDrag` 키를 사용하고 평균 회귀를 실수 비율로 계산한다. 최종 확률 배율을 설정 범위에 제한하고 가격을 1..Long.MAX_VALUE로 유지한다. 기준가/현재가/설정/주기 유효성을 검증한다.
- 매도 대금으로 평균단가를 낮추던 오류를 없앴다. `costBasis`에 취득 원가를 저장하고 비례 원가만 해제한다. 분할 계산은 BigInteger로 중간 오버플로를 막는다. 취득 원가의 나머지는 남은 주식에 보존한다.
- 기존 `averagePrice * count`는 최초 거래 때 취득 원가로 보완한다. 과거 정수 평균의 이미 잃어버린 소수/오류 원가는 자동 복원할 수 없다. 음수/오버플로 등 손상 보유는 거래를 거부하고 GUI에서 관리자 확인 대상으로 표시하며 원본 값을 지우지 않는다.
- 매 틱 Calendar/경고 flag 대신 20틱 주기의 시간 경계 검사로 갱신한다. 이전 가격과 `_meta.lastUpdate`를 가격과 같은 stock.yml에 저장한다. 재시작/장시간 오프라인 후에는 현재 경계에서 1회 갱신하며 경고 틱을 놓쳐도 실행한다.
- 메타데이터를 종목 목록으로 처리하지 않는다. 전체 기준가 재설정 시간은 misc에 저장하도록 고쳤다. 기존 stock 안의 잘못된 기준 시간은 보고서 표시에서 호환 조회한다.
- 주식 GUI는 `MenuHolder`의 슬롯별 코드/표시 가격을 사용한다. 아이템 이름을 substring으로 파싱하지 않는다. 하단 클릭·숫자키·offhand·doubleclick는 거래로 처리하지 않고, 삭제된 종목/변경된 가격은 거부한 후 화면을 갱신한다.
- `ReadOnlyMenuListener`로 해당 Holder의 vanilla 클릭/상단 drag를 막고 커스텀 입력만 처리한다. 화면 전환은 다음 틱으로 예약하며 기존 화면이 바뀌었거나 플레이어가 이탈하면 열지 않는다.
- 공유 `Pagination`과 주식 이전/다음 페이지를 구현하고 명령어의 54종목 제한을 제거했다. 목록 0/45/46/90/91개와 큰 목록 경계값을 시험했다.
- 등락률은 변화 없음 0.00%로 표시한다. LOSPI는 기준 100 지수로 명시하고 초기값/현재값 합계를 BigDecimal로 계산한다. 보유 현재가치 표시는 Long 합계 범위를 넘어도 BigInteger로 표시한다.
- 주식 반복 작업은 명시적 시작/stop을 사용하고 plugin disable 시 정리한다. 다른 능력 작업의 수명 관리는 아직 남아 있다.

### 2026-10-10 / SQLite·송금 원장

- `5870a95`: SQLite·이전·송금/관리자 원장 구현과 테스트. origin에 push 완료.

- `storage/StateStore`와 `SqliteStateStore`를 도입했다. 플레이어 프로필과 마켓 상태를 `limpi.sqlite3`의 documents 테이블에 보존한다. 설정/종목 가격 YAML은 유지한다. 저장된 프로필 payload는 기존 YAML 필드 구조를 보존하며, 캐시/격리 편집/충돌 처리는 `CachedPlayerRepository`와 저장 어댑터로 분리했다.
- SQLite JDBC 3.53.4.0을 배포 JAR에 포함했다. WAL, synchronous=FULL, busy timeout 5초, BEGIN IMMEDIATE를 사용한다. schema/application ID를 검사해 다른 DB와 미래 schema를 덮지 않는다. native 라이브러리를 재배치하지 않는다. Paper가 제공하는 SLF4J/Paper API와 JUnit은 배포 JAR에 넣지 않는다.
- `LegacyStateMigration`은 모든 UUID YAML·market YAML을 먼저 읽고 검증한다. 원본 payload와 SHA-256, 플레이어 수/BigInteger 잔액 합계를 기록한다. `backups/sqlite-v1/`에 원본을 보존한 뒤 한 트랜잭션으로 가져오기와 완료 marker를 저장한다. 실패하면 일부 프로필이나 marker만 남지 않는다. 기존 YAML을 삭제하지 않는다. 이미 완료된 가져오기는 재시작 때 오래된 YAML로 현재 DB를 덮지 않는다.
- 소수·문자열·Long 초과 금액은 가져오기를 거부한다. 음수 legacy 잔액은 지우지 않고 보존한다. schemaVersion은 정수·범위를 직접 검사해 소수나 Int 범위 초과 버전이 getInt() 변환으로 통과하지 않게 했다.
- `StateStore.execute()`는 거래 UUID/요청 fingerprint/완료 영수증/변경한 문서 키를 같은 commit에 기록한다. 같은 ID·같은 요청은 기존 결과를 반환하며 mutation을 다시 실행하지 않는다. 같은 ID·다른 요청은 거부한다. 중첩/범위 밖 transaction 사용을 막는다. rollback 실패 시 연결을 닫아 autoCommit 복구로 일부 변경이 commit되는 것을 막는다.
- `BankService`의 송금은 양쪽 잔액·5% 정수 수수료·원장 영수증을 원자적으로 저장한다. 관리자 set/add도 원장에 이전/이후 잔액과 actor를 남긴다. 오버플로·잔액 부족·자기 송금·손상 계좌를 거부한다. 음수 legacy 잔액의 복구는 관리자가 명시적으로 set을 사용하는 경로로 제한한다.
- `/financial send|set|add <이름 또는 UUID> <금액> [거래 UUID]`를 서비스에 연결했다. 오프라인 기존 계좌를 지원한다. set/add는 콘솔도 사용한다. 권한 노드를 추가했고 이전의 파일별 송금/관리자 변경 코드는 제거했다. 메시지는 commit 이후에 보낸다. 실패 결과가 불확실하면 같은 ID로 재시도할 수 있도록 ID를 안내한다.
- 이름 인덱스에서 같은 과거 이름을 가진 UUID가 여러 개면 임의 계좌로 송금하지 않고 이름 조회를 거부한다. UUID를 직접 지정할 수 있다. 원장 변경 이후 프로필 캐시를 무효화하므로 오래된 능력 편집을 저장해도 최신 송금 잔액을 되돌리지 않는다.
- 운영/복구 경로와 아직 남은 경계를 [저장소·복구 절차](STORAGE_AND_RECOVERY.md)에 기록했다. **마켓은 저장 위치만 옮겼으며 거래 서비스 연결은 아직 남아 있다. 아이템/DB 동시 원자성은 구현하지 않았다.**

### 2026-10-10 / 미수령 지급·마켓 거래 도메인

- `94c4bed`: 지급/마켓 도메인 구현과 테스트. origin에 push 완료.

- `delivery/InventoryPlan`을 추가했다. 아이템별 전체 수량과 중첩 상한을 확인하고, 부분 스택을 먼저 채운 다음 빈 슬롯을 배정한다. 모든 아이템이 들어가지 않으면 원본을 바꾸지 않고 전체 계획을 거부한다. 단일 아이템의 payload에는 이름/lore/PDC를 포함한 원본 구성요소가 들어가야 하며 다른 payload는 같은 material이어도 합치지 않는다. Long 수량을 항목 수만큼 객체로 만들지 않고 슬롯 단위로 처리한다.
- `DeliveryJournal.stage()`는 잔액/매물/보상 트랜잭션 안에서 미수령 기록을 생성한다. 완료 후 같은 ID로 stage를 반복해도 새 지급을 만들지 않는다. 지급 예정 전체 수량, owner UUID, 원본 슬롯/완료 슬롯, 상태를 보존한다. 완료는 별도 멱등 원장과 함께 pending→done 문서 이동으로 저장한다.
- `DeliveryService`는 PENDING→APPLYING 기록 후 인벤토리를 변경한다. 저장된 플레이어 파일의 checkpoint와 after 이미지가 확인된 뒤에만 DONE을 기록한다. 공간 부족은 PENDING 그대로 남긴다. 물리 저장/최종 DB commit 실패 후 같은 live inventory로 재시도하지 않으며 fresh login에서 기록과 저장 상태로 복구한다. 불일치는 REVIEW로 남기고 임의 덮어쓰기/재지급을 하지 않는다. 이전 APPLYING 처리 전에 다른 지급이 checkpoint를 덮지 않게 한다.
- `market/MarketCatalog`에는 표시 코드와 별개의 영구 매물 UUID, seller UUID, 원본 아이템, ACTIVE/OWNERSHIP_REVIEW/SOLD/RETURNED 상태를 도입했다. 이름만 있고 소유자 확인이 안 되는 기존 매물을 보호할 수 있다. 원본 legacy market payload는 별도로 보존한다.
- `MarketService.buy()`는 구매자 차감/판매자 입금/정수 수수료/매물 SOLD/미수령 아이템/영수증을 같은 SQLite commit에 넣는다. 판매자의 변경 전 이름을 지급 계좌로 사용하지 않는다. 인벤토리 공간이 없어도 매물/아이템을 지우는 대신 미수령 권리를 보존하는 구조다. withdraw도 소유자에게 미수령 반환을 생성하고 거래 ID 재시도를 지원한다.
- 은행·마켓의 원장 내부 계좌 조회/정수·schema 검증을 `economy/Accounts`로 공유했다. 마켓 정산은 캐시가 아닌 해당 DB 트랜잭션의 최신 값을 사용한다.
- **이번 단계는 순수 정책/저장·거래 도메인 구현이다. FileManager 초기화·Paper 인벤토리 어댑터·기존 마켓 이벤트/명령은 아직 새 도메인에 연결하지 않았다.** 따라서 기존 마켓 화면의 결함을 해결 완료로 처리하지 않는다. [지급 복구 설계와 연결 조건](DELIVERY_RECOVERY.md)을 다음 작업 기준으로 삼는다.

### 2026-10-10 / Paper 지급 어댑터·접속 복구·관리자 검토

- `87a96b1`: Paper 지급 어댑터·접속/관리자 복구 연결과 테스트. origin에 push 완료.

- `platform/PaperDeliveryInventory`는 storage 슬롯 전체를 계획대로 갱신하고, 아이템 NBT/Base64를 사용해 원본 구성요소를 보존한다. Bukkit 접근은 메인 스레드로 제한한다. decode와 before 확인을 모두 마친 뒤 물리 슬롯을 변경한다. 계획에는 아이템의 상한 외에 해당 인벤토리 중첩 상한도 반영한다.
- `PlayerCheckpointFile`은 실제 `<UUID>.dat`의 gzip NBT `BukkitValues`에서 지급 checkpoint를 읽는다. 메모리 PDC를 저장 증거로 사용하지 않는다. 먼저 probe checkpoint를 saveData/파일 force/판독으로 확인하여 잘못된 저장 경로나 저장 불가 상태가 물품 변경 뒤에 처음 발견되지 않게 한다. 그 뒤 슬롯과 지급 checkpoint를 저장하고 파일에서 확인한다. 파일 판독·계획 확인에 실패하면 완료 원장을 기록하지 않는다.
- `PlayerDataDirectory` 설정을 추가했다. 비어 있으면 첫 서버 월드의 playerdata를 사용하고, 상대 경로는 worldContainer 기준이다. 실제 사용 경로를 startup 로그에 남긴다. 특수 playerdata 저장 플러그인/별도 저장 환경은 실제 경로와 복구 호환성을 최종 서버 시험에서 확인해야 한다.
- `FileManager`에 지급 서비스/controller를 초기화하고, 새 접속에서 복구를 수행하도록 연결했다. plugin enable 시 이미 접속한 플레이어는 fresh login으로 취급하지 않는다. 공간이 부족하면 미수령을 남겨 `/financial claim`으로 받도록 안내한다. 실패 시 추가 인벤토리 조작/명령을 막고 이탈시키며, REVIEW가 있으면 다음 로그인 전에도 접속을 거부한다. 이탈 시 메모리 UUID 보호 상태를 해제한다.
- `/financial claim`과 관리자 `/financial delivery <owner UUID> [delivery UUID]`, `delivery resolve <owner UUID> <delivery UUID> delivered|retry <확인 근거>`를 연결했다. 해결은 owner 오프라인일 때만 수행하고 권한 노드로 제한한다. actor/결정/근거/원본 evidence를 멱등 원장에 보존한다. 일반 PENDING을 임의 DONE으로 처리하지 않는다.
- 물리 checkpoint를 거래 UUID와 구분했다. 관리자 retry 후 새 물리 시도는 새 checkpoint를 사용하므로 과거 저장 흔적이 새 시도를 완료 처리하지 않는다. 예전 checkpoint 필드가 없는 지급 기록은 기존 지급 UUID를 사용해 읽는다. slotLimit이 없는 이전 기록도 읽을 수 있다.
- adventure-nbt 5.2.0을 포함하고 NBT 패키지를 LIMPI 내부 namespace로 재배치했다. Paper의 Adventure text/API 클래스는 배포 JAR에 포함하지 않는다.
- **지급 플랫폼·복구 경로는 연결했지만 기존 마켓 GUI/명령·매물 등록 escrow는 아직 연결하지 않았다.** 실제 서버 실행 없이 컴파일·파일/원장 시험만 수행했다.

### 2026-10-10 / 마켓 등록 escrow·이전·메뉴/명령 연결

- `840d3b0`: 마켓 연결/escrow/이전 구현과 테스트. origin에 push 완료.

- `MarketEscrow`는 원본/회수 후 슬롯·시도 checkpoint를 원장과 함께 저장하고 매물을 ESCROW로 예약한다. 실제 회수와 playerdata 저장을 확인한 뒤 ACTIVE와 escrow 완료를 같은 DB commit으로 저장한다. 저장 전/후 실패는 fresh login에서 복구하며 상태 불일치는 REVIEW로 남긴다. 같은 prepare/apply를 재시도해도 나중에 획득한 동일 물품을 다시 회수하지 않는다.
- 마켓 복구는 지급보다 먼저 실행해 중단된 회수의 checkpoint를 다른 지급이 덮지 않게 했다. REVIEW는 로그인 보호와 관리자 해결 경로에 연결했다. 등록 준비 저장 실패도 결과가 불확실하면 플레이어를 보호/이탈시킨다.
- `LegacyMarketMigration`을 실제 초기화에 연결했다. 모든 기존 매물을 검증한 뒤 한번에 새 catalog로 이전한다. seller 이름은 UUID 인덱스로 확인하고 모호한 소유자는 OWNERSHIP_REVIEW로 보존한다. 원본 `financial/market` 문서는 지우지 않는다. 기존 무료(0원) 매물도 보존한다. malformed item/가격은 전체 이전을 거부한다.
- `market_code/<code>`로 활성/보호/예약 코드를 원자 예약하고 SOLD/RETURNED 시 해제한다. 매물은 별도 영구 UUID를 사용해 코드 재사용 후 오래된 화면에서 새 매물을 구매하는 것을 막는다. 메뉴 목록은 현재 예약된 코드 인덱스를 사용하며 거래 이력을 지우지 않는다.
- `MarketEvents`를 Holder/슬롯 action/매물 UUID·가격 quote로 다시 연결했다. 45개 단위 페이지, 상단 클릭만 처리, 특수 click/vanilla 이동 취소, 다음 틱 화면 전환, 갱신/삭제 quote 검증을 적용했다. 아이템 이름·lore·현재 목록 index를 거래 권한으로 사용하지 않는다. 기존 Int 잔액/가격과 분리 저장·직접 addItem 경로를 제거했다.
- 구매는 원자 정산→미수령 수령으로 연결했고 공간 부족은 권리를 보존한다. 우클릭 흥정은 seller UUID로 온라인 여부를 확인한다. 등록/회수 명령은 `MarketCommands`로 분리해 Long·코드·권한·owner UUID를 검증한다. 기존 54개 제한을 제거했다. 관리자 회수도 원래 소유자에게 미수령으로 반환하고 실제 actor를 원장에 남긴다.
- 관리자 escrow 조회/removed·retained 해결, legacy owner 확인을 원장과 연결했다. 확인 근거/actor/이전 evidence를 보존한다. 회수 해결은 owner가 오프라인일 때만 수행한다. 금융 기능을 꺼도 escrow/소유자 복구 명령은 유지한다. [마켓 운영·복구](MARKET_RECOVERY.md)에 명령과 판단 기준을 기록했다.
- FileManager의 기존 market FileConfiguration cache와 saveMarket/getMarketConfig를 제거했다. 새 거래와 과거 payload가 서로 덮는 경로를 남기지 않는다. 원본 payload는 최초 이전/복구 증거로만 사용한다.
- **마켓 구현과 연결은 완료했고 서버 검증은 아직 남아 있다.** 수표/판매/상점/능력 상점/보상은 계속 정비해야 한다.

## 검증

- JUnit 5와 테스트 실행을 빌드에 추가했다. Paper API는 테스트 classpath에만 추가되고 배포 JAR에는 포함하지 않는다.
- 최종 저장소/Money/파일 테스트 18개와 `test build` 통과(exit code 0). 실패/스킵 0개.
- 시험 내용: Int 범위 초과 금액, Long 오버플로, 수수료 경계, 기존 데이터/알 수 없는 필드 보존, 원본 백업, 이름 변경, 격리 편집, 오래된 스냅샷 병합/동일 필드 충돌, 저장 실패 시 메모리·디스크 보존, 캐시/이탈/LRU, 손상 YAML, UUID 혼동 방지, 미래 schema 보호, UTF-8/atomic 파일 교체.
- 실제 Paper 서버 실행/접속/플레이 시험은 수행하지 않았다.
- 주식/거래/페이지 추가 후 총 **45개** 테스트 통과(실패/에러/스킵 0). `clean test build --warning-mode all` 통과했고, 마지막 손상 보유 처리 추가 후 `test build`도 통과했다. 기존 컴파일 경고 3개는 능력 정비 단계에서 처리한다.
- SQLite/마이그레이션/은행/이름 충돌/schema 보호 추가 후 총 **76개** 테스트 통과(실패/에러/스킵 0). `test build --console=plain` 성공(exit code 0). 실제 임시 SQLite 파일로 실패 rollback, 두 DB 연결의 동일 ID 경쟁, 재시작 후 재시도, 원본 백업/알 수 없는 필드·빈 섹션 보존, 손상 입력 거부, stale profile 편집, 잔액/수수료/Long 경계를 시험했다.
- 배포 JAR에서 SQLite JDBC·Windows/Linux x86_64 native library·JDBC service descriptor 포함을 확인했다. Paper/Bukkit/SLF4J API와 JUnit 클래스는 미포함이다. JDK 25의 SQLite native load 안내 경고가 테스트 실행에 출력되며 테스트는 성공했다. 서버 JVM native access 설정은 최종 운영 점검에서 확인한다.
- 지급/마켓 도메인 추가 후 총 **98개** 테스트 통과(실패/에러/스킵 0), `test build --console=plain` 성공(exit code 0). 아이템 수량 보존·10개 구매 공간·부분 중첩·커스텀 아이템 구분·Long 최대 수량·실패 전/후 재시작·불명확 상태 격리, 실제 SQLite 두 연결의 매물 경쟁·판매자 UUID/오프라인·수수료·부분 commit 방지·구매/회수 재시도를 시험했다. Inventory fake의 저장 증거는 프로토콜 시험이며 실제 Paper playerdata 저장의 보장을 입증하지는 않는다.
- Paper 어댑터/해결 추가 후 총 **107개** 테스트 통과(실패/에러/스킵 0). `test build --console=plain` 성공(exit code 0). 실제 gzip NBT 파일의 checkpoint·force 확인/누락/손상/다른 namespace, 인벤토리 상한, 관리자 해결 감사 기록·rollback·재시도·새 checkpoint와 잘못된 계획 보호를 추가 시험했다. JAR 내부 NBT 패키지 포함, 원본 NBT/Adventure text/Paper/JUnit 클래스 미포함을 확인했다. Paper 이벤트/실제 saveData/보호 플러그인과의 연동은 최종 서버 시험으로 남는다.
- 마켓 연결/escrow/이전 추가 후 총 **121개** 테스트 통과(실패/에러/스킵 0). 최종 `test build --console=plain` 성공(exit code 0). 회수 전/후 저장 실패·ACTIVE commit 실패·재시작·재회수 방지·코드 예약 충돌·관리자 결정/감사·기존 Long/0원/모호한 owner/손상 이전 보호·코드 재사용의 오래된 quote를 추가 시험했다. 실제 GUI/input/접속/물품 회수 저장은 최종 서버 시험으로 남는다.

### 2026-10-10 / 수표 발행·사용 원장·기존 수표 전환

- `0866e31`: 수표 원장/연결·legacy 전환 구현과 테스트. origin에 push 완료.

- `checks/CheckService`는 서버 UUID/ISSUED/REDEEMED/금액·발행인·사용인·입금 거래를 보존한다. 발행인 차감·발행 기록·미수령·영수증을 한 commit에 저장한다. 양수 Long을 사용하고 잔액 전액 발행을 허용한다. 같은 거래 UUID를 다시 발행해도 차감/종이가 늘지 않는다.
- 입금은 표시 금액 대신 저장된 금액으로 사용 상태·입금 잔액·영수증을 함께 저장한다. 알 수 없는 UUID/사용한 수표/오버플로를 거부한다. 독립 DB 연결에서 복사본을 경쟁 입금해도 하나만 성공한다. commit 후 물리 종이 정리에 실패해도 남은 같은 UUID 종이는 가치가 없으며 중복 입금되지 않는다.
- `items/Check`를 PDC UUID·format/중첩 상한 1로 재작성하고 실제 이벤트 손을 읽도록 연결했다. 금융 명령의 기존 공개 해시 발행 코드를 제거하고 `CheckCommands`로 분리했다. 발행 거래 UUID 재시도, 주 손 redeem, 관리자 inspect/import, 권한·help/tab을 연결했다. 금융 비활성화 중 기존 수표 입금과 관리자 조회/전환도 가능하다.
- `LegacyCheck`는 기존 형식만 검사한다. 공개 해시는 발행 증거가 아니므로 자동 입금하지 않는다. 관리자 전환은 owner/issuer UUID·actor·근거·semantic fingerprint를 기록해 새 claim/미수령을 만들며 과거 발행인 차감을 다시 하지 않는다. 원본을 보존하고 같은 fingerprint의 복사본/스택은 중복 승인하지 않는다. [수표·기존 전환 절차](CHECKS_AND_LEGACY_MIGRATION.md)에 운영 판단과 남은 서버 검증을 기록했다.
- 수표 추가 후 총 **134개** 테스트 통과(실패/에러/스킵 0), `test build --console=plain` 성공(exit code 0). 전액/큰 금액·실패 rollback·재시작 replay·중복/경쟁 입금·오버플로·legacy 중복 승인/감사를 시험했다. 실제 Paper 손/PDC/이벤트/종이 정리·보호 플러그인 연동은 최종 서버 시험으로 남긴다.

### 2026-10-10 / 상점·판매 원장·공통 회수·메뉴 연결

- `35e37ae`: 상점/판매·공통 회수 구현과 테스트. origin에 push 완료.

- `CatalogPrices`에 비음수 정수 판매 단가/유한 비음수 비율/BigDecimal 구매가 버림·Long exact 변환을 구현했다. 기본 판매 단가 1과 0원 상품을 보존한다. 큰 금액의 Int 포화, NaN/음수/문자열/소수 판매가의 묵시적 변환을 제거했다. invalid material/가격 상품은 원본 설정을 삭제하지 않고 로그·화면 제외로 보호한다.
- `ShopService`는 최신 계좌/현재 code·단가 quote로 구매 수량을 계산하고 차감/전체 수량의 미수령/영수증을 한 commit에 저장한다. 동일 UUID replay는 catalog가 바뀌어도 기존 결과만 반환한다. Shift는 상한/가용 잔액 내 수량으로 처리하고 무료 상품의 0 나누기를 제거했다. 직접 addItem 경로와 Int 잔액 cast를 제거했다.
- 상점 메뉴를 Holder/action/45개 페이지/허용 클릭/다음 틱 전환에 연결했다. 숫자키·offhand·doubleclick·하단·drag로 상품을 이동/구매하지 않는다. 일반 권한/관리자 catalog 권한을 명령/이벤트/탭에 연결하고 기존 설정 명령을 `CatalogCommands`로 분리했다. 콘솔 설정을 지원한다.
- `PhysicalRemoval`의 저장 checkpoint 프로토콜을 마켓/판매에 공유했다. 기존 market escrow 형식은 유지하고 `RemovalJournal`에 kind/terms/전체 before·after/checkpoint·APPLYING/REVIEW/DONE·감사 원장을 추가했다. 계획은 payload/limit를 유지하며 슬롯별 감소/비움만 허용한다. 마켓과 공통 회수는 owner의 미해결 checkpoint 예약을 공유한다.
- `SaleSettlement`는 저장된 선택 슬롯별 단가와 정확한 감소 수량으로 최신 잔액을 정산한다. 준비 시 총액/계좌 overflow를 검사하고, 정산/완료/영수증을 함께 commit한다. 회수 저장 전·후 및 정산 실패는 fresh login으로 복구하며 완료 replay가 나중에 획득한 동일 물품을 다시 지우지 않는다.
- 판매 화면을 원본 storage 슬롯의 읽기 전용 선택으로 바꿨다. 기존 임시 물품 이동/애니메이션 삭제/종료 addItem 반환·Player map·틱 작업을 제거했다. 확정 전 원본·가격 변경은 거부한다. 선택/해제·새로고침·초기화를 제공한다. 실제 회수/저장 후에만 입금한다.
- 접속/claim에서 마켓·공통 회수를 지급보다 먼저 복구하고, REVIEW 로그인 보호·실패 live inventory 차단에 연결했다. `/financial removal` 조회와 오프라인 owner의 removed/retained 해결을 추가했다. actor/note/원본 evidence·정산을 기록하며 금융 비활성화 중에도 복구 명령을 사용할 수 있다. [상점·판매 복구](SHOP_AND_SALE_RECOVERY.md)에 운영/서버 시험을 기록했다.
- Paper 26.3의 non-null itemName getter에 맞춰 수표 legacy 조회의 불필요한 Elvis를 제거했다. 기존 발행기는 itemName을 사용하므로 이전 형식은 유지한다. 판매/지급의 inventory snapshot을 같은 Paper codec으로 공유했다.
- 총 **151개** 테스트 통과(실패/에러/스킵 0), `test build --console=plain` 성공(exit code 0). 상점 큰 금액/무료·부족/손상·stale·rollback/restart replay, 회수 전·후 저장/정산 실패·REVIEW/해결 rollback·반복 회수 방지·다중/부분 슬롯·마켓 충돌을 추가 시험했다. 실제 GUI/손·저장/보호 플러그인 동작은 서버 시험으로 남긴다.

### 2026-10-10 / 능력 상점 에메랄드 교환·수리 설정

- `1ab5532`: 능력 상점 교환/수리 설정 구현과 테스트. origin에 push 완료.

- `InventoryPlan.count/remove`는 lossless payload가 같은 storage 스택만 집계/회수하고 부분 스택을 보존한다. 이름/lore/PDC가 다른 에메랄드는 일반 에메랄드 결제에 사용하지 않는다. 부족한 수량은 원본 변경 전에 거부한다.
- `ItemExchange`/`ExchangeTerms`를 공통 회수에 연결했다. frozen 상품 코드/정확한 화폐 payload·전체 cost/전체 reward를 보존하고, 실제 감소 슬롯·수량이 cost와 같아야 준비/정산한다. 회수 완료·전체 수량 미수령·원장을 같은 commit에 넣는다. 0원 상품은 물리 회수 없이 별도 멱등 무료 거래로 보상을 저장한다.
- `AbilityShop`을 Holder/상품 코드·단가·원본 보상 quote로 이전했다. 실제 아이템 비교로 등급을 추정하지 않는다. 상단 LEFT/SHIFT_LEFT만 처리하고 다음 틱 화면 전환·권한을 적용했다. Shift는 최대 10개이며 정확한 에메랄드 가용량으로 계산한다. 공개 설정의 음수/소수/누락 가격은 상품을 제외하고 로그에 남긴다. 기존 티켓/선택권 metadata 형식은 유지했다.
- `DeliveryController.remove`를 판매/교환이 공유하도록 분리하고 claim/접속/관리자 removal 해결을 재사용했다. 금융 비활성화 중에도 금융 명령의 claim/removal 복구 경로로 받을 수 있다. 지급 실패 때 물품 권리를 지우지 않는다. [상점·판매·교환 복구](SHOP_AND_SALE_RECOVERY.md)에 운영 조건을 추가했다.
- `EmeraldFix`는 누락된 repair만 새 기본값 50으로 저장한다. 명시적인 0은 보존·비활성 안내하고 에메랄드를 소비하지 않는다. 비음수 Int 범위 정수로 검증하고 Damageable 안전 변환을 사용한다. 취소된 클릭을 존중해 읽기 전용 메뉴와 보호 입력에서 수리하지 않는다. 세부 설명은 현재 설정에서 생성한다. 커서/대상 변경의 실제 서버 저장 시험은 남아 있다.
- 총 **162개** 테스트 통과(실패/에러/스킵 0), `test build --console=plain` 성공(exit code 0). 다중/부분 스택·커스텀 화폐 제외·잘못된 회수량, 교환 저장 실패/restart replay·관리자 결정·무료 거래 rollback·10개 미수령, 수리 기본값/형식/범위를 추가 시험했다.
- **완료 범위는 능력 상점 구매와 EmeraldFix 설정/타입 보호다.** 선택권 실제 사용·뽑기 티켓 소비/확정/애니메이션·PDC 티켓 식별/이전·통합 이동 제한·초기/퀘스트 지급은 다음 단계다. 기존 티켓 사용 코드를 원자적으로 만들었다고 주장하지 않는다.

### 2026-10-10 / 뽑기 정책·획득 분류·도움말

- `GachaPolicy`/`PityCurve`/`GachaSettings`/`GachaState`/`DrawOutcome`를 추가했다. Bukkit/저장과 분리하고 RNG를 주입한다. 기존 LEGENDARY→EPIC→TROLL→RARE의 순차 조건부 판정, 일반 후보의 픽업과 SemiCeiling 의미를 유지한다. 0/1 확률·설정/카운터 범위와 overflow를 검증한다.
- Count를 실패 횟수, IncreaseCount를 증가를 시작하는 시도 번호로 통일했다. 기본 LEGENDARY 100번째/EPIC 조건부 10번째에 100%가 되도록 기존 한 시도 지연을 수정했다. BigDecimal로 경계를 계산하고 Increment=0의 유한 보장 없음도 표현한다. 기존 Count/Ceiling 값은 초기화하지 않는다.
- 실제 추첨과 help/pity를 같은 정책에 연결했다. 실제 다음 추첨의 무조건 등급 확률, 남은 조건부 보장 시도와 픽업 연속 횟수를 안내한다. LEGENDARY가 우선하는 EPIC을 무조건 10회 획득으로 안내하지 않는다. 경쟁 판정을 무시한 기존 평균 등장 설명을 제거했다.
- `AcquisitionType`을 추가해 설명 Component 비교를 제거했다. 기존 퀘스트 전용 4개는 QUEST, 나머지 기존 뽑기 후보는 GACHA로 유지한다. 능력 ID/설명/해금 필드는 보존한다. pickup은 같은 rounded 시점을 사용하고 후보/설정/프로필 snapshot으로 결과·다음 상태를 소비 전에 계산한다. 후보 누락·잘못된 픽업은 티켓 소비 전에 거부한다.
- PickUpChange의 양수 정수와 millis overflow를 검사하고, empty pickup의 강제 null 참조를 명시적 validation으로 바꿨다. [뽑기 규칙·후속 복구](GACHA_RULES_AND_RECOVERY.md)에 정확한 의미와 남은 원장/애니메이션 연결을 기록했다.
- 총 **174개** 테스트 통과(실패/에러/스킵 0), `test build --console=plain` 성공(exit code 0). 천장/순차 확률·0/1/equality·픽업/카운터·후보 snapshot·invalid config/Long/unknown field 보존을 시험했다. 실제 이벤트/플레이/애니메이션은 시험하지 않았다.
- **GACHA-01은 아직 미해결이다.** 현재 직접 티켓 소비/카운터 저장/연출 종료 후 해금이 남아 있다. 다음 작업은 frozen 결과를 공통 회수에 연결해 소비 확인→해금/카운터/영수증 확정→표현 순서로 바꾸고, 중복/이탈/disable/오류의 task/display를 정리하는 것이다.

### 2026-10-10 / 뽑기 티켓 회수·결과 확정·연출 분리

- GACHA-01의 직접 event.item 차감/별도 카운터 저장/연출 종료 해금을 제거했다. 서버가 선택한 frozen 결과를 RemovalJournal에 준비하고 물리 회수 checkpoint 확인 후 GachaSettlement에서 해금·천장·결과·회수 완료·영수증을 한 commit에 저장한다. 복구는 재추첨/재소모하지 않는다.
- 정확히 지정한 티켓 슬롯 1개만 차감하는지, 최신 DB before 카운터와 기존 해금 목록 타입이 유효한지 검사한다. 기존 코드·중복 획득 의미·다른 사용자 필드를 보존한다. GachaState.advance를 추첨/저장 결과 검증에서 공유한다.
- storage 36칸+offhand 1칸 기록을 뽑기에 적용했다. 공통 Paper 회수 adapter는 기록 길이를 검증해 36/37칸을 복구하며 지급/판매/마켓의 기존 storage 범위를 유지한다. 실제 양손 이벤트는 서버 검증이 남아 있다.
- TROLL 강제 적용 권한은 gacha_apply_pending으로 결과와 함께 저장한다. 능력 registry 등록 뒤 접속/claim에서 apply→프로필 확인→멱등 ack한다. 실패 시 대기를 남기며 새 추첨을 거부한다. 능력 시스템을 끄면 대기를 보존한다.
- applyAbility의 같은 코드 검사를 속성 reset 앞으로 옮겼다. 기존 제한 슬롯의 일반 아이템을 지우지 않도록 기존 activeItem 일치를 검사한다. 새 슬롯 덮어쓰기·외부 속성 공존·전체 전환/세션 정비는 AB-02/03/04로 남긴다.
- GachaAnimation은 확정 결과 표현만 실행한다. 1 owner/1 task와 표시 목록, 부분 spawn 추적, persistent=false, 정상 종료/quit/world change/death/disable/예외 정리를 구현하고 중첩 지연 작업을 제거했다. 연출 중 입력은 티켓을 소비하지 않는다. itemUse DENY를 존중한다.
- /ability result <거래 UUID> 자신의 확정 결과 조회, 도움말/탭 연결. 회수 대기/관리자 removed|retained는 기존 financial removal 절차를 사용한다.
- **184개** 테스트 통과(실패/에러/스킵 0), test build 성공. 실제 SQLite로 새 10개 시험: 원자 결과/상태 보존, replay/fingerprint, 물리 회수 후 commit 실패→재시작, 중복 해금, 강제 적용/ack 실패, 관리자 판단, invalid snapshot/카운터/목록, strict terms, offhand 복구. Paper 실행·playerdata save·연출/능력 적용은 미검증이다.
- 다음 단계는 선택권 사용/해금의 공통 회수 원장 연결, PDC/legacy 티켓 정책/이동 제한과 초기·퀘스트 보상, 이후 능력 메뉴·세션·속성 전체 정비다. 전체 정비 목표는 완료하지 않았다.

### 2026-10-10 / 선택권·초기/퀘스트 보상·능력 메뉴

- 선택권 직접 차감→즉시 능력 변경을 AbilitySelection의 frozen 회수→적용 대기/결과 원자 정산→platform apply/ack로 연결했다. 양손/기존 metadata를 보존하고 locked/same/stale 상태·중복 요청·실패/restart/관리자 판단을 검증한다.
- 선택/도감 Player page/sort map과 title/item 비교를 제거하고 MenuHolder/능력 ID/공유 Pagination/다음 틱 전환으로 이전했다. 일반 선택/admin 무소모 적용의 permission node를 정의했다.
- 새 티켓 PDC와 exact legacy template 인식, 공통 수동/자동 이동·crafting guard를 연결했다. 예전 지급 대기 NBT는 재발행하지 않는다. 실제 PDC/자동 제작기 시험은 이후다.
- AbilityInventoryPlan으로 제한 슬롯의 일반 물품 전체 relocation을 계획한다. 공간 부족은 티켓 소비 전 거부하고 old activeItem만 제거한다. 인벤토리 클릭 때 원본 물품을 addItem 후 덮어쓰던 경로를 제거했다. 새 apply의 DB 저장 실패는 live inventory를 되돌린다. 전체 전환 내구성/속성/세션 정비는 아직 별도 범위다.
- InitialRewards는 신규 plugin profile 생성과 frozen WAITING을 함께 등록하고 능력 활성 시 원자 미수령/GRANTED를 만든다. 기존 profile은 EXEMPT. 명시적 0/Long·config 변경·비활성 대기/replay/commit 실패를 시험했다.
- 4개 퀘스트 완료/해금을 QuestRewards에 연결하고 legacy 완료/보상 누락을 최초 원장 시 repair한다. 조화 진행은 confirmed positive damage의 MONITOR에서 원자 progress/threshold/완료/해금/영수증으로 저장한다. 기존 progress/ID를 유지한다.
- TerraBurning/ExpRich는 레벨 증가만 보너스·재진입 보호·experience 감소 비배수·Int 상한 포화 정책으로 연결했다.
- **213개** 테스트 통과(실패/에러/스킵 0), test build 성공. 새 29개 저장/정책 시험이며 Paper 실제 실행은 하지 않았다. 자세한 계약/검증 경계는 [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md).
- 중간 기록에서 목표를 끝내지 않는다. 다음은 능력 세션/속성/단발·이동·42개 능력/전투·대상 메뉴, 주식 원장/운영 도구/CI/명령 정비다.

### 2026-10-10 / 능력 runtime·자원·검 반환

- UUID 상태와 start/stop/release 수명, EffectRegistry/AbilityRuntime/AbilityRunnable을 실제 능력 작업에 연결했다. 생성자 반복 작업 제거, 이탈/사망/월드/전환/종료와 예외 정리를 구현했다.
- 웹슈터 laser, 검/통달 display, Muspell Marker, EntityThrower 붙잡기 상태를 소유 범위에 연결했다. 대상 world 검사와 0길이 이동, 폭풍 상태 정리·maxHeight, 엔더맨 텔레포트 성공 후 중단을 수정했다. LetsBoom 사망 후 실행은 명시적 별도 허용으로 보존한다.
- baseValue 초기화/변경을 제거하고 임시 modifier에 연결했다. 레일건 중복 충전/발사 구현을 통합하고 0지연/소수 가속 및 잘못된 설정을 처리한다. ZombieKing 야간 break도 제거했다.
- BladeLeaper 원본 1개 회수/내구도 적용 반환을 공통 원장/미수령으로 연결했다. 종료 시에만 돌려주던 자산 손실을 방지하고 creative/부서진 검/공간 부족/재시작 경계를 구분한다.
- **228개** 테스트 통과(실패/에러/스킵 0), test build 성공. 실제 Paper 실행은 하지 않았다. 계약과 남은 범위는 [능력 runtime](ABILITY_RUNTIME.md).
- 다음은 능력 전환 물리 checkpoint/profile 정산과 전투 규칙을 연결한다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 능력 전환 checkpoint

- 능력 apply/remove/active item 복원에 AbilityTransitions의 frozen 슬롯/코드 계획을 연결했다. 물리 저장 checkpoint 확인 뒤 profile/DONE/대기 삭제/영수증을 원자 확정한다. live rollback으로 프로세스 중단을 해결한다고 가정하지 않는다.
- 전환/마켓/공통 회수의 소유자 예약을 공유하고 접속 복구에서 전환을 먼저 처리한다. 잘못된 이미지/검토 상태는 접속과 인벤토리 사용을 막는다. 설정 변경 후에도 준비된 원본/새 아이템을 다시 만들지 않는다.
- financial transition의 오프라인 applied|retained 감사 판단·조회·permission/tab을 연결했다. 기존 선택권/TROLL 대기의 apply/ack는 전환 성공 뒤에만 완료한다.
- **237개** 테스트 통과(실패/에러/스킵 0), test build 성공. 새 9개 저장/실패/restart/슬롯 보존/예약 시험. 실제 Paper는 미검증. [능력 전환 계약](ABILITY_TRANSITIONS.md).
- 다음은 전투 취소·귀속·연쇄 제한과 대상 메뉴/설정 정비다. 전체 목표는 계속 진행 중이다.

## 항목별 진행 상태

| 계획 ID | 구현 상태 | 남은 내용 |
| --- | --- | --- |
| DATA-01 | 완료(단위 검증) | 캐시·단일 저장소·능력 hot path 구현. 실제 서버 성능 측정은 최종 서버 시험 때 수행 |
| DATA-02 | 부분 해결 | strict YAML/원자 저장/SQLite/마이그레이션/이름 충돌 보호 구현. 전체 사용자 오류 처리, 명령형 export·검사 도구, 운영 복구 시험은 남음 |
| DATA-03 | 완료(단위 검증) | 기존 서버 플레이어 ensure/현재 이름/기존 데이터 보존 구현. 실제 접속 확인은 이후 |
| MONEY-01 | 진행 중 | Money 규칙·주식·송금/관리자·마켓·수표·판매·상점/능력 상점 교환 연결 완료. 보상 등 잔여 경제 경로는 남음 |
| MONEY-02 | 부분 해결 | SQLite 멱등 원장/송금·마켓·수표·판매·상점·능력 상점/공통 회수·미수령 연결 완료. 뽑기 회수/결과 원장 연결 완료. 선택권/초기·퀘스트 보상도 연결 완료. 주식 원장은 남음 |
| MONEY-03 | 구현 완료(단위 검증) | 수표 발행/입금 멱등 원장·PDC·legacy 관리자 전환 연결. 실제 손·Paper 이벤트/저장 확인은 이후 |
| ITEM-01 | 부분 해결 | 상점/능력 상점 전체 수량 미수령·판매/에메랄드 공통 회수/복구·Paper/관리자 연결. 초기 보상 미수령 연결 완료. 실제 서버 검증은 남음 |
| STOCK-01 | 구현 완료(단위 검증) | 가격/설정 정책과 실제 갱신 경로 연결. 실제 서버 실행 확인은 이후 |
| STOCK-02 | 구현 완료(정책 단위 검증) | 시간 경계/재시작 상태/1초 검사 구현. Paper 반복 작업 동작은 서버 시험 이후 |
| STOCK-03 | 구현 완료(단위 검증) | 취득 원가/매도/잔액 저장과 실패 시험. 과거 손상 원가의 자동 추정은 하지 않음 |
| STOCK-04 | 구현 완료(페이지 단위 검증) | 메타데이터 분리, 기준가 시간 저장, 등락률/지수, 주식 페이지/종목 제한 제거. 실제 화면 확인은 이후 |
| GUI-01/02/03 | 부분 해결 | 주식·마켓·상점·판매·능력 상점에 Holder/action/입력 제한/다음 틱 전환 구현. 능력 선택/도감도 이전 완료. 대상 메뉴와 서버 검증은 남음 |
| CFG-01 | 부분 해결 | EmeraldFix 기본값·범위/타입·취소 보호와 능력 상점 가격 검증 구현. InitialTicket/초기 등급 strict/once-only 지급 연결 완료. 전체 능력 설정 검증은 남음 |
| GACHA-01 | 구현 완료(저장 단위 검증) | 양손 티켓 회수·해금/천장/결과 원자 정산·ForceTroll 대기/ack·결과 조회·연출 정리 연결. Paper 실제 검증은 이후 |
| GACHA-02 | 구현 완료(정책 단위 검증) | 공통 확률/천장·실제 추첨/help/pity 연결. EPIC 조건부/픽업 의미 보존. 실제 서버 확인은 이후 |
| AB-07 | 부분 해결 | typed 획득 분류·후보/픽업 snapshot/소비 전 validation 구현. 뽑기 확정 원장/회수 연결 완료. 선택권 사용/PDC·legacy/guard 연결 완료. 서버 검증은 남음 |
| GUI-04 | 구현 완료(페이지 단위 검증) | 주식·선택/도감의 실제 Holder/page/sort에 공유 Pagination 연결. 서버 화면 검증은 이후 |
| QUEST-01 | 부분 해결 | 4개 완료/해금 원자 정산·legacy repair·조화 confirmed progress/threshold 연결. 특수 처치/전투 귀속·서버 검증은 남음 |
| AB-08 | 부분 해결 | 레벨 감소/경험치 감소 보너스 제거·상한/재진입 보호, ZombieKing 야간 반복 수정 완료. 실제 서버 검증은 이후 |
| AB-02 | 구현 완료(저장 단위 검증) | same-code no-op·전체 슬롯 relocation/preflight·전환 checkpoint/profile 원자 정산·restart/REVIEW/관리자 판단 구현. 실제 Paper save/이벤트·복구 시험은 이후 |
| AB-01 | 부분 해결 | 능력 관리자 단일화·ID map·객체 init 반복 작업 제거·start/stop/release 및 공통 runtime 구현. 설정/등록 결합 정비는 계속 |
| AB-04 | 부분 해결 | UUID 능력 상태·작업/일시 엔티티/laser/속성 소유 범위와 이탈/사망/전환/종료 정리 구현. potion 중첩 정책·대상 메뉴와 실제 서버 검증은 남음 |
| OPS-01 | 부분 해결 | System.out 교체 제거, 테스트 기반/schema/백업/저장소 복구 문서 구현. CI/복구 도구·시험 및 기타 결합 제거는 남음 |
| 그 외 모든 항목 | 미해결 | [정비 계획](MAINTENANCE_PLAN.md)의 범위를 그대로 유지 |

## 다음 작업 순서

1. Money와 경제 서비스를 모든 금액 읽기/계산/갱신 호출부에 연결한다. 주식의 단일 프로필 원자 저장은 구현했지만 SQLite 거래 UUID 원장 연결은 남아 있다. 작은 호환 facade에서 단계적으로 규칙을 빼낸다.
2. 뽑기·선택권·PDC/legacy·초기/퀘스트 보상과 선택/도감 이전 완료. 능력 전환/세션/속성/task/entity와 단발·이동·전투 규칙을 실제 42개 능력에 연결한다.
3. 대상 메뉴/명령 정의·전투/특수 처치 귀속과 남은 능력 설정을 정비한다. EmeraldFix/Paper 물리 저장·이벤트·실제 플레이는 최종 서버 시험으로 남긴다.
4. 운영 backup/export/check/dry-run 복구 도구·CI와 패키징/의존성·전체 단위 검증을 정리한다.
5. 능력 세션/전환/작업·속성 정리, 뽑기 확정/연출, 퀘스트 보상, 명령어 정의, 나머지 42개 능력 순으로 진행한다.

## 현재 설계의 경계

- 플레이어/마켓의 정본은 SQLite다. playerData YAML과 market.yml은 최초 이전 원본이며 이후 자동 동기화하지 않는다. 운영 설정·종목 가격은 YAML로 유지한다.
- 송금/관리자 변경·마켓·수표·판매·상점·능력 상점·뽑기는 원장/미수령·필요한 회수 복구에 연결했다. 선택권/초기·퀘스트 보상도 연결했다. 능력 적용/세션과 주식의 추가 원장 연결 등 호환 저장 API가 아직 남아 있다. DB와 인벤토리가 원자적으로 함께 저장되는 것은 아니다.
- 반환하는 FileConfiguration은 호환 편집 API다. 순수 규칙·서비스로 이동하면서 직접 YAML 접근을 줄여야 한다.
- negative legacy balance는 임의로 0으로 덮지 않는다. 은행 거래는 거부하고 `/financial set <UUID> <정상 금액>`으로 관리자가 명시적으로 복구하면 원장에 이전 음수 잔액이 남는다. 나머지 경제 호출부도 같은 보호 규칙으로 연결해야 한다.
- 서버 실행 중 파일을 외부에서 수정해도 캐시는 자동 갱신하지 않는다. 안전한 reload/운영 절차를 별도로 정한다.
- 손상된 UUID YAML은 최초 이전에서 거부하며 손상 DB payload는 파싱 실패를 전달한다. 자동 초기화로 자산을 지우지 않는다. DB가 정본이 된 이후 오래된 YAML을 고쳐도 DB 값이 바뀌지 않는다.
