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

- `player/PlayerRepository`, `PlayerProfile`, `storage/CachedPlayerRepository` 도입. 기존 `FileManager`는 호출부 전환을 위한 얇은 호환 facade로 줄였다.
- 접속 프로필을 캐시하고 오프라인 프로필은 LRU 256개로 제한한다. 이탈 시 해당 캐시를 해제한다. 이름/UUID 인덱스를 초기 로드 때 구성하고 이름 변경 시 갱신한다.
- `.ability` 조회는 캐시된 코드명 → 단일 능력 레지스트리의 ID map 조회를 사용한다. `AbilityManager()` 생성 47곳을 제거했다. 능력 정의·엔티티 분류 목록을 조회 때마다 다시 만들지 않는다.
- YAML 편집은 격리된 스냅샷으로 한다. 저장 전 cache를 수정하지 않으며, 오래된 스냅샷은 변경한 필드만 최신 데이터에 병합한다. 동일 필드가 충돌하면 덮어쓰지 않고 실패한다.
- `AtomicFiles`는 대상과 같은 디렉터리의 임시 파일을 force 후 atomic move로 교체한다. 지원되지 않는 atomic move를 비원자적 덮어쓰기로 대체하지 않는다.
- 플레이어/경제/능력 YAML의 저장 실패를 더 이상 출력만 하고 숨기지 않는다. 기존 파일을 보존하고 예외를 전달한다. YAML 문법 오류도 빈 설정으로 바꾸지 않고 전달한다.
- 신규/기존 서버 플레이어 모두 접속 시 `ensure()`를 사용한다. 기존 잔액/해금/퀘스트는 보존하며 누락된 기본값만 채운다. 플레이어 이름은 현재 이름으로 갱신한다.
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

- `5870a95`: SQLite·송금/관리자 원장 구현과 테스트. origin에 push 완료.

- `storage/StateStore`와 `SqliteStateStore`를 도입했다. 플레이어 프로필과 마켓 상태를 `limpi.sqlite3`의 documents 테이블에 보존한다. 설정/종목 가격 YAML은 유지한다. 저장된 프로필 payload는 기존 YAML 필드 구조를 보존하며, 캐시/격리 편집/충돌 처리는 `CachedPlayerRepository`와 저장 어댑터로 분리했다.
- SQLite JDBC 3.53.4.0을 배포 JAR에 포함했다. WAL, synchronous=FULL, busy timeout 5초, BEGIN IMMEDIATE를 사용한다. schema/application ID를 검사해 다른 DB와 미래 schema를 덮지 않는다. native 라이브러리를 재배치하지 않는다. Paper가 제공하는 SLF4J/Paper API와 JUnit은 배포 JAR에 넣지 않는다.
- schemaVersion은 정수·범위를 직접 검사해 소수나 Int 범위 초과 버전이 getInt() 변환으로 통과하지 않게 했다.
- `StateStore.execute()`는 거래 UUID/요청 fingerprint/완료 영수증/변경한 문서 키를 같은 commit에 기록한다. 같은 ID·같은 요청은 기존 결과를 반환하며 mutation을 다시 실행하지 않는다. 같은 ID·다른 요청은 거부한다. 중첩/범위 밖 transaction 사용을 막는다. rollback 실패 시 연결을 닫아 autoCommit 복구로 일부 변경이 commit되는 것을 막는다.
- `BankService`의 송금은 양쪽 잔액·5% 정수 수수료·원장 영수증을 원자적으로 저장한다. 관리자 set/add도 원장에 이전/이후 잔액과 actor를 남긴다. 오버플로·잔액 부족·자기 송금·손상 계좌를 거부한다. 손상(음수) 잔액의 복구는 관리자가 명시적으로 set을 사용하는 경로로 제한한다.
- `/financial send|set|add <이름 또는 UUID> <금액> [거래 UUID]`를 서비스에 연결했다. 오프라인 기존 계좌를 지원한다. set/add는 콘솔도 사용한다. 권한 노드를 추가했고 이전의 파일별 송금/관리자 변경 코드는 제거했다. 메시지는 commit 이후에 보낸다. 실패 결과가 불확실하면 같은 ID로 재시도할 수 있도록 ID를 안내한다.
- 이름 인덱스에서 같은 과거 이름을 가진 UUID가 여러 개면 임의 계좌로 송금하지 않고 이름 조회를 거부한다. UUID를 직접 지정할 수 있다. 원장 변경 이후 프로필 캐시를 무효화하므로 오래된 능력 편집을 저장해도 최신 송금 잔액을 되돌리지 않는다.
- 운영/복구 경로와 아직 남은 경계를 [저장소·복구 절차](STORAGE_AND_RECOVERY.md)에 기록했다. **마켓은 저장 위치만 옮겼으며 거래 서비스 연결은 아직 남아 있다. 아이템/DB 동시 원자성은 구현하지 않았다.**

### 2026-10-10 / 미수령 지급·마켓 거래 도메인

- `94c4bed`: 지급/마켓 도메인 구현과 테스트. origin에 push 완료.

- `delivery/InventoryPlan`을 추가했다. 아이템별 전체 수량과 중첩 상한을 확인하고, 부분 스택을 먼저 채운 다음 빈 슬롯을 배정한다. 모든 아이템이 들어가지 않으면 원본을 바꾸지 않고 전체 계획을 거부한다. 단일 아이템의 payload에는 이름/lore/PDC를 포함한 원본 구성요소가 들어가야 하며 다른 payload는 같은 material이어도 합치지 않는다. Long 수량을 항목 수만큼 객체로 만들지 않고 슬롯 단위로 처리한다.
- `DeliveryJournal.stage()`는 잔액/매물/보상 트랜잭션 안에서 미수령 기록을 생성한다. 완료 후 같은 ID로 stage를 반복해도 새 지급을 만들지 않는다. 지급 예정 전체 수량, owner UUID, 원본 슬롯/완료 슬롯, 상태를 보존한다. 완료는 별도 멱등 원장과 함께 pending→done 문서 이동으로 저장한다.
- `DeliveryService`는 PENDING→APPLYING 기록 후 인벤토리를 변경한다. 저장된 플레이어 파일의 checkpoint와 after 이미지가 확인된 뒤에만 DONE을 기록한다. 공간 부족은 PENDING 그대로 남긴다. 물리 저장/최종 DB commit 실패 후 같은 live inventory로 재시도하지 않으며 fresh login에서 기록과 저장 상태로 복구한다. 불일치는 REVIEW로 남기고 임의 덮어쓰기/재지급을 하지 않는다. 이전 APPLYING 처리 전에 다른 지급이 checkpoint를 덮지 않게 한다.
- `market/MarketCatalog`에는 표시 코드와 별개의 영구 매물 UUID, seller UUID, 원본 아이템, ACTIVE/SOLD/RETURNED 상태를 도입했다.
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
- `market_code/<code>`로 활성/보호/예약 코드를 원자 예약하고 SOLD/RETURNED 시 해제한다. 매물은 별도 영구 UUID를 사용해 코드 재사용 후 오래된 화면에서 새 매물을 구매하는 것을 막는다. 메뉴 목록은 현재 예약된 코드 인덱스를 사용하며 거래 이력을 지우지 않는다.
- `MarketEvents`를 Holder/슬롯 action/매물 UUID·가격 quote로 다시 연결했다. 45개 단위 페이지, 상단 클릭만 처리, 특수 click/vanilla 이동 취소, 다음 틱 화면 전환, 갱신/삭제 quote 검증을 적용했다. 아이템 이름·lore·현재 목록 index를 거래 권한으로 사용하지 않는다. 기존 Int 잔액/가격과 분리 저장·직접 addItem 경로를 제거했다.
- 구매는 원자 정산→미수령 수령으로 연결했고 공간 부족은 권리를 보존한다. 우클릭 흥정은 seller UUID로 온라인 여부를 확인한다. 등록/회수 명령은 `MarketCommands`로 분리해 Long·코드·권한·owner UUID를 검증한다. 기존 54개 제한을 제거했다. 관리자 회수도 원래 소유자에게 미수령으로 반환하고 실제 actor를 원장에 남긴다.
- 관리자 escrow 조회/removed·retained 해결을 원장과 연결했다. 확인 근거/actor/이전 evidence를 보존한다. 회수 해결은 owner가 오프라인일 때만 수행한다. 금융 기능을 꺼도 escrow 복구 명령은 유지한다. [마켓 운영·복구](MARKET_RECOVERY.md)에 명령과 판단 기준을 기록했다.
- FileManager의 기존 market FileConfiguration cache와 saveMarket/getMarketConfig를 제거했다. 새 거래와 과거 payload가 서로 덮는 경로를 남기지 않는다.
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

- `0866e31`: 수표 원장/연결 구현과 테스트. origin에 push 완료.

- `checks/CheckService`는 서버 UUID/ISSUED/REDEEMED/금액·발행인·사용인·입금 거래를 보존한다. 발행인 차감·발행 기록·미수령·영수증을 한 commit에 저장한다. 양수 Long을 사용하고 잔액 전액 발행을 허용한다. 같은 거래 UUID를 다시 발행해도 차감/종이가 늘지 않는다.
- 입금은 표시 금액 대신 저장된 금액으로 사용 상태·입금 잔액·영수증을 함께 저장한다. 알 수 없는 UUID/사용한 수표/오버플로를 거부한다. 독립 DB 연결에서 복사본을 경쟁 입금해도 하나만 성공한다. commit 후 물리 종이 정리에 실패해도 남은 같은 UUID 종이는 가치가 없으며 중복 입금되지 않는다.
- `items/Check`를 PDC UUID·format/중첩 상한 1로 재작성하고 실제 이벤트 손을 읽도록 연결했다. 금융 명령의 기존 공개 해시 발행 코드를 제거하고 `CheckCommands`로 분리했다. 발행 거래 UUID 재시도, 주 손 redeem, 관리자 inspect/import, 권한·help/tab을 연결했다. 금융 비활성화 중 기존 수표 입금과 관리자 조회/전환도 가능하다.
- 수표 추가 후 총 **134개** 테스트 통과(실패/에러/스킵 0), `test build --console=plain` 성공(exit code 0). 전액/큰 금액·실패 rollback·재시작 replay·중복/경쟁 입금·오버플로를 시험했다. 실제 Paper 손/PDC/이벤트/종이 정리·보호 플러그인 연동은 최종 서버 시험으로 남긴다.

### 2026-10-10 / 상점·판매 원장·공통 회수·메뉴 연결

- `35e37ae`: 상점/판매·공통 회수 구현과 테스트. origin에 push 완료.

- `CatalogPrices`에 비음수 정수 판매 단가/유한 비음수 비율/BigDecimal 구매가 버림·Long exact 변환을 구현했다. 기본 판매 단가 1과 0원 상품을 보존한다. 큰 금액의 Int 포화, NaN/음수/문자열/소수 판매가의 묵시적 변환을 제거했다. invalid material/가격 상품은 원본 설정을 삭제하지 않고 로그·화면 제외로 보호한다.
- `ShopService`는 최신 계좌/현재 code·단가 quote로 구매 수량을 계산하고 차감/전체 수량의 미수령/영수증을 한 commit에 저장한다. 동일 UUID replay는 catalog가 바뀌어도 기존 결과만 반환한다. Shift는 상한/가용 잔액 내 수량으로 처리하고 무료 상품의 0 나누기를 제거했다. 직접 addItem 경로와 Int 잔액 cast를 제거했다.
- 상점 메뉴를 Holder/action/45개 페이지/허용 클릭/다음 틱 전환에 연결했다. 숫자키·offhand·doubleclick·하단·drag로 상품을 이동/구매하지 않는다. 일반 권한/관리자 catalog 권한을 명령/이벤트/탭에 연결하고 기존 설정 명령을 `CatalogCommands`로 분리했다. 콘솔 설정을 지원한다.
- `PhysicalRemoval`의 저장 checkpoint 프로토콜을 마켓/판매에 공유했다. 기존 market escrow 형식은 유지하고 `RemovalJournal`에 kind/terms/전체 before·after/checkpoint·APPLYING/REVIEW/DONE·감사 원장을 추가했다. 계획은 payload/limit를 유지하며 슬롯별 감소/비움만 허용한다. 마켓과 공통 회수는 owner의 미해결 checkpoint 예약을 공유한다.
- `SaleSettlement`는 저장된 선택 슬롯별 단가와 정확한 감소 수량으로 최신 잔액을 정산한다. 준비 시 총액/계좌 overflow를 검사하고, 정산/완료/영수증을 함께 commit한다. 회수 저장 전·후 및 정산 실패는 fresh login으로 복구하며 완료 replay가 나중에 획득한 동일 물품을 다시 지우지 않는다.
- 판매 화면을 원본 storage 슬롯의 읽기 전용 선택으로 바꿨다. 기존 임시 물품 이동/애니메이션 삭제/종료 addItem 반환·Player map·틱 작업을 제거했다. 확정 전 원본·가격 변경은 거부한다. 선택/해제·새로고침·초기화를 제공한다. 실제 회수/저장 후에만 입금한다.
- 접속/claim에서 마켓·공통 회수를 지급보다 먼저 복구하고, REVIEW 로그인 보호·실패 live inventory 차단에 연결했다. `/financial removal` 조회와 오프라인 owner의 removed/retained 해결을 추가했다. actor/note/원본 evidence·정산을 기록하며 금융 비활성화 중에도 복구 명령을 사용할 수 있다. [상점·판매 복구](SHOP_AND_SALE_RECOVERY.md)에 운영/서버 시험을 기록했다.
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
- 다음 단계는 선택권 사용/해금의 공통 회수 원장 연결, PDC 티켓 정책/이동 제한과 초기·퀘스트 보상, 이후 능력 메뉴·세션·속성 전체 정비다. 전체 정비 목표는 완료하지 않았다.

### 2026-10-10 / 선택권·초기/퀘스트 보상·능력 메뉴

- 선택권 직접 차감→즉시 능력 변경을 AbilitySelection의 frozen 회수→적용 대기/결과 원자 정산→platform apply/ack로 연결했다. 양손/기존 metadata를 보존하고 locked/same/stale 상태·중복 요청·실패/restart/관리자 판단을 검증한다.
- 선택/도감 Player page/sort map과 title/item 비교를 제거하고 MenuHolder/능력 ID/공유 Pagination/다음 틱 전환으로 이전했다. 일반 선택/admin 무소모 적용의 permission node를 정의했다.
- 새 티켓 PDC 인식, 공통 수동/자동 이동·crafting guard를 연결했다. 실제 PDC/자동 제작기 시험은 이후다.
- AbilityInventoryPlan으로 제한 슬롯의 일반 물품 전체 relocation을 계획한다. 공간 부족은 티켓 소비 전 거부하고 old activeItem만 제거한다. 인벤토리 클릭 때 원본 물품을 addItem 후 덮어쓰던 경로를 제거했다. 새 apply의 DB 저장 실패는 live inventory를 되돌린다. 전체 전환 내구성/속성/세션 정비는 아직 별도 범위다.
- InitialRewards는 신규 plugin profile 생성과 frozen WAITING을 함께 등록하고 능력 활성 시 원자 미수령/GRANTED를 만든다. 기존 profile은 EXEMPT. 명시적 0/Long·config 변경·비활성 대기/replay/commit 실패를 시험했다.
- 4개 퀘스트 완료/해금을 QuestRewards에 연결했다. 조화 진행은 confirmed positive damage의 MONITOR에서 원자 progress/threshold/완료/해금/영수증으로 저장한다. 기존 progress/ID를 유지한다.
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

### 2026-10-10 / 전투·대상·확정 게이지

- AbilityDamage/DamageContext의 attributed damage와 context 정리, 양의 피해 관측·추가 효과 조건, float 상한을 별도 능력 피해에 연결했다. 직접 health 차감/즉사 제거, ZombieKing 고정 보너스의 기존 방어 감소 보존과 흡수·취소 경로, 마법 반전 재진입 방지를 구현했다.
- ChainLightning을 MONITOR 확정 피해·same-owner context·visited/max hops·적/월드 검사에 연결했다. 조화/통달·적응형 방어·방어 올인의 상태 기록을 확정 피해로 옮겼고 조화 취소 시 강화 보존·통달 windup 선강화/자체 발동 중첩을 제거했다.
- 기본 취소 가능 이벤트 보호, DoubleExp 감소/오버플로 보너스, HotPickaxe 확정 화상·좀비 지휘/지원 확인을 연결했다. 엔더맨 무피해·언데드 Mob.target의 퀘스트 조건도 수정했다.
- 오프라인 길들인 몹 주인과 UUID/팀 공통 대상 정책, 실제 PLAYER 기본값과 일치하는 9칸 Holder/action 메뉴·순수 클릭·다음 틱 전환/permission을 구현했다.
- **255개** 테스트 통과(실패/에러/스킵 0), test build 성공. 새 18개 정책 시험이며 Paper 피해·보호·토템·메뉴는 미검증이다. [전투 계약](COMBAT_AND_TARGETS.md).
- 다음은 전체 능력 설정/소매치기·커스텀 상호작용·potion 중첩, 주식 원장·명령과 운영 도구/CI다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 전체 능력 설정·등록

- 31개 설정의 기본값·strict 타입·범위·교차 조건을 공통 AbilitySpecs에 통합하고 실행 handler를 불변 설정에 연결했다. 누락만 보완하며 손상/미래 schema 원본을 보존한다. 확률·분모·물리 범위·수명 overflow를 시작 전에 거부한다.
- Ability의 생성 중 static plugin 조회를 제거하고 bootstrap에서 명시적으로 bind한다. 실제 42개 레지스트리와 퀘스트 메타데이터를 서버 없이 시험한다. 사용하지 않는 Titan 설정 생성도 제거했다.
- 부스터 에너지/게이지 overflow·무료 비용, 점프 충전 중복 작업·종료·ceil 계산, 0 레벨/기간 포션과 통달 색상 overflow를 수정했다.
- **269개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 14개 설정/레지스트리 시험. 실제 Paper는 미검증. [설정 계약](ABILITY_SETTINGS.md).
- 다음은 소매치기·연금술·소모성 상호작용과 potion 중첩, 주식 원장·명령 및 운영 도구/CI다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 연금술·소매치기 물품

- 연금술의 선소비/월드 드롭을 제거하고 결과 snapshot·정확히 10개 회수·미수령 지급 권리와 원장을 연결했다. 양손/DENY·복구 뒤 원본 검사를 구현했다.
- 소매치기를 실제 낚시 회수 이벤트/다음 틱 최종 취소·적/월드/PvP/Creative/Spectator 검사에 연결했다. 플레이어 실제 슬롯 전체 묶음을 회수해 공격자 미수령을 원자 정산하고 갑옷 포함 41칸 checkpoint/restart를 지원한다.
- NPC는 회수 후 드롭 실패에 복원한다. 플레이어 checkpoint나 DB의 보장을 NPC/world 저장에 과장하지 않는다. 능력/제한 아이템·placeholder를 보호하고 hook의 중복 실행을 막는다.
- **282개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 13개 정책·저장/실패/restart/recipient/만석/관리자 시험. [물품 계약](ABILITY_ITEM_TRANSACTIONS.md).
- 다음은 설탕 소모·포션 소유권, 남은 물리 효과 경계, 주식 원장·명령 및 운영 도구/CI다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 일시 포션 소유권

- 일시 포션을 AbilityEffect의 scope lease에 연결했다. 기존 외부 포션은 보존하고 자기 scope만 갱신/해제한다. 외부의 동일/숨겨진 포션 변경은 소유권을 무효화한다.
- 자체 mutation/event identity를 연결해 중첩 외부 이벤트를 구별하고, 실제 증폭/표시를 다시 확인해 해제한다. 통달·점프·돌진·붙잡기·칼날 도약·주사위 경고에 적용했다. 쿠키/설탕 소비 보상은 독립 유지한다.
- **289개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 7개 소유권 정책 시험. 실제 Paper/강제 종료 경계는 [포션 계약](ABILITY_POTIONS.md)에 구분했다.
- 다음은 설탕 소비/결과와 남은 물리 효과 경계, 주식 원장·명령 및 운영 도구/CI다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 설탕 소비·결과

- 설탕의 직접 감소를 제거하고 실제 양손 1개 회수/checkpoint·고정 효과/기간/world/대상 UUID·pending 결과를 연결했다. DENY·blocked·0 기간·복구 뒤 원본 변경을 검사한다.
- 소비 저장 직후 중단은 원래 결과로 복구한다. 적용 권리는 DB에서 한 번만 발급하고 live 효과는 재실행하지 않는다. 즉시 효과의 중단 구간과 생략 가능성을 정확히 [설탕 계약](SUGAR_CONSUMPTION.md)에 기록했다.
- 주변 적/PvP/Creative/Spectator/blocked/실제 구면 범위와 최대 256 대상 검사, 확률 0/1 경계, 잘못된 설명 범위를 수정했다.
- **296개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 7개 소비·복구·적용 권리/실패·관리자 시험. 실제 Paper는 미검증.
- 다음은 남은 물리 효과 경계, 주식 원장·명령 및 운영 도구/CI다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 주식 UUID 원장·실현 손익

- 주식 거래를 canonical SQLite 계좌·현재 quote 재검사·UUID fingerprint/영수증·cache 무효화에 연결했다. 잔액/수량/취득 원가/실현 손익은 같은 commit이다. quote 변경/삭제·재시작 후 replay도 재정산하지 않는다.
- 손익을 signed checked Long으로 기록하고 정확한 원가 배분/전량 매도와 알 수 없는 field를 보존한다. malformed 숫자/section을 0으로 읽어 자산을 덮지 않는다. GUI에서 거래 UUID를 표시한다.
- **301개** 시험 통과(실패/에러/스킵 0), test build 성공. 주식 기존 8개를 실제 SQLite로 이전하고 5개 추가. [주식 계약](STOCK_TRANSACTIONS.md). 실제 Paper는 미검증.
- 다음은 남은 물리 효과 경계·명령 정의·운영 backup/export/check 도구/CI·전체 범위 감사다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 능력 물리·표시 경계

- 11개 능력의 모든 normalize를 유한한 정규화 helper에 연결했다. 0/동일 위치/비유한/큰 수/작은 수 방향을 보호하고 Web/돌진의 0 부피 나눗셈도 수정했다.
- Muspell의 다른 월드 대상·동일 위치 rayTrace·방향 상쇄와 BladeLeaper의 정지 rayTrace를 보호했다. ImpactLanding의 검색/파티클 ring/무작위 반복/sound 표시 비용을 제한하며 정상 피해 곡선을 보존한다.
- **310개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 9개 정책 시험. 실제 Paper는 미검증. [물리 계약](ABILITY_PHYSICS.md).
- 다음은 명령 정의·운영 backup/export/check 도구/CI·전체 범위 감사다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 명령 정의·콘솔·종목 관리

- 루트 명령의 긴 help/파싱/직접 설정 처리를 분리하고 CommandDefinitions/Index에서 route·권한·console·인자 범위·help/tab을 공유한다. static plugin 조회·미구현 reload·콘솔 TODO를 제거했다. 비활성 시스템은 복구/help만 노출한다.
- ability admin.set permission/console/UUID/none, unknown 능력 ID 보존, offline spy 및 주식 관리 permission을 연결했다. 정보·GUI 노드를 plugin.yml에 명시하고 stock 클릭에서도 permission을 확인한다.
- Financial의 종목 삭제가 전체 플레이어 보유를 지우던 경로를 제거했다. 상장 중지/재상장·중지 종목 holdings·코드 재사용 금지·검증 후 단일 YAML snapshot 저장을 구현했다.
- **325개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 15개 정의/permission·console adapter·종목 snapshot 시험. [명령 계약](COMMANDS.md). 실제 Paper는 미검증.
- 다음은 운영 backup/export/check/dry-run 복구 도구·CI·패키징·전체 범위 감사다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 운영 검사·백업·export·복사본 복구 도구와 CI

- 서버 없이 실행하는 `LIMPI-operations-<version>.jar`(Main `ops/OperationsCli`)를 build/libs에 추가했다. check/backup/export/export-check/restore-dry-run/restore-copy. Paper/Bukkit 클래스를 싣지 않는 별도 shadow 구성이다. 서버 자동 복사는 없다.
- 원본 DB는 read-only·없는 파일 생성 금지·application_id/user_version/테이블/integrity 검사. 출력은 새 파일만 같은 디렉터리 임시 파일→force→검증→무덮어쓰기 이동. backup은 SQLite backup API로 열린 writer의 commit된 WAL을 포함하고 사본 SHA-256을 비교한다.
- export는 payload 원문·원장 id/fingerprint/영수증/변경 키/시각과 길이 접두 SHA-256을 보존한다. import는 SafeConstructor·중복 키 거부·checksum. restore-copy는 새 DB에 문서/원장을 복원하며 거래 UUID replay가 재정산하지 않는다. 서버 DB 교체는 하지 않는다.
- 구조 검사: 플레이어 수·BigInteger 잔액 합·pending/REVIEW·음수 잔액·중복 이름 경고, 깨진 YAML/비정수 금액/미래 문서 schema/owner·id key 불일치/색인 없는 market_code/주식 보유 음수·overflow 오류. 오류는 restore를 막고 backup/export는 법의학 보존으로 게시한다. payload를 로그에 출력하지 않는다.
- 소스에서 쓰지 않는 kotlin-reflect 의존성을 제거했다. `.github/workflows/build.yml`(Java 25 temurin, `./gradlew test build`, test report 업로드)과 gradlew 실행 비트를 추가했다. CI는 서버를 실행/배포하지 않는다.
- read-only WAL 접근이 빈 `-wal`/`-shm` sidecar를 만들 수 있다는 SQLite 동작을 시험·문서에 반영했다(원본 DB 바이트 불변 확인).
- **339개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 14개 운영 도구 시험. Java 25로 운영 JAR help/없는 DB 거부를 직접 실행했다. 서버 DB 교체 절차는 미검증. [저장소·복구](STORAGE_AND_RECOVERY.md#운영-도구-서버-없이-실행).
- 다음은 전체 범위 감사(남은 MONEY-01/ITEM-01/DATA-02/AB-04/AB-07/QUEST-01/OPS-01 잔여)다. 전체 목표는 계속 진행 중이다.

### 2026-10-10 / 범위 감사 1차: 잔액 표시·사망 취소·잔여 우회 경로

- 손상된 잔액을 관대한 `getLong`으로 0처럼 보여주던 경로를 제거했다. `PlayerProfile.balance`는 정수 Long이 아니면 null이고, `/financial spy`와 주식 GUI는 "계좌 복구 필요"를 표시한다. 주식 매수 수량 계산도 strict `Accounts.balance`를 사용한다. 원본 payload는 바꾸지 않는다.
- LetsBoom은 MONITOR·ignoreCancelled에서만 폭발한다. 다른 플러그인이 취소한 사망에는 폭발하지 않으며 runtime 정리(LOWEST) 뒤에 시작하는 기존 순서를 유지한다. UndeadCooperation 퀘스트도 취소된 사망을 처치로 세지 않고(MONITOR·ignoreCancelled) 첫 일치 후 반복을 멈춘다(보상은 원래 결정적 UUID로 멱등).
- 사용하지 않는 비원자 해금 `AbilityManager.unlockAbility`, 부분 수용만 확인하던 `validInventoryIndex`, 개수 확인과 분리된 `removeInventoryItem`/`getInventoryItemCount`, `getAllPlayersName`의 `name!!`, `saveMisc`를 제거했다. 이후 코드가 원장/회수 계약을 우회해 다시 쓰지 못하게 한다.
- 남은 Kotlin 컴파일 경고(중복 else)를 제거했다. 경고 0개.
- 여러 계약 문서에 남아 있던 "아직 연결되지 않음" 문장(이후 단계에서 연결 완료된 뽑기/선택권/보상/전환/전투/운영 도구)을 현재 상태와 링크로 고쳤다.
- 접속/enable 때 프로필 초기화(손상·미래 schema·DB 오류)가 실패하면 예외를 Bukkit에 흘려 프로필 없는 활성 플레이어를 남기던 경로를 막았다. `DeliveryController.reject`가 해당 UUID의 인벤토리/명령 입력을 차단하고 원인 로그와 함께 연결을 끊는다. enable 중 한 명의 실패가 나머지 플레이어 초기화를 중단하지 않는다. 원본 payload는 바꾸지 않는다.
- 감사 확인: 모든 GUI가 MenuHolder(click 전체/상단 drag 취소), 모든 능력 반복 작업이 AbilityRunnable 소유, 직접 health 대입 없음, 잔액 변경 경로는 Accounts/BankService/InitialRewards뿐이다.
- **340개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 1개는 손상 잔액 null 노출·거래 거부·원본 보존이다. 사망 취소 순서·접속 kick은 실제 Paper 이벤트로 검증하지 않았다.

### 2026-10-10 / 독립 리뷰 반영: 주식·설탕 관리자 복구와 명령 경계

- 최근 Codex 커밋 6개(명령·주식 상장 중지, 주식 원장, 물리, 설탕, 포션)를 별도 리뷰했다. 주식 원장·물리에서는 결함이 없었다. 다음을 수정했다.
- 설탕 대기 결과의 효과 key가 registry에서 사라지거나 대기 payload가 손상되면 claim 예외로 매 접속 kick되던 잠금을 제거했다. 오류 기록 후 건너뛰고, `/financial sugar`·`sugar void`로 환불 없이 감사 종료한다. [설탕](SUGAR_CONSUMPTION.md#적용할-수-없는-결과)
- `stock restore`가 기준가를 검증하지 않아 손상 기준가 하나가 모든 주기 가격 갱신을 멈출 수 있던 문제를 수정했다(refix는 기준가를 새로 쓰므로 제외). stock add 입력 오류에 항목별 메시지를 추가했다.
- Transcendence `duration_ticks: 0`(설정 허용값)이 lease의 양수 기간 요구로 매 발동 예외가 되던 문제를 수정했다(0이면 포션 없음).
- ability set 대상이 차단 상태면 무응답이던 것을 안내 메시지로, 콘솔 `check inspect`의 UUID 필요 안내를 추가했다.
- **342개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 2개는 주식 보유 복구(원장/replay/충돌/이후 거래)와 설탕 대기 관리자 종료다. 기존 catalog 시험에 restore 기준가 검증을 추가했다. 실제 Paper 명령/claim은 미검증.

### 2026-10-10 / 2차 독립 리뷰 반영: 잠금·복제 경로

- 지급/마켓/회수, 뽑기/선택권/보상, 능력 전환/runtime/수표를 별도 리뷰했다. 확률/천장, 재추첨 금지, 초기 보상 1회, GUI 권한, 티켓 위조/제작 우회, checkpoint 비교, 관리자 판단 중복, 수표 이중 입금은 결함이 없었다. 다음을 수정했다.
- **사망 화면 재접속 영구 잠금:** 죽은 플레이어의 claim은 입력 차단 후 복구를 미루고 리스폰 다음 틱에 fresh 복구한다. [지급 복구](DELIVERY_RECOVERY.md#사망-화면-재접속정산-거부-2026-10-10-리뷰-반영)
- **선택권 적용 대기 영구 잠금:** 미수령 지급이 먼저 빈칸을 채워 relocation이 공간 부족이 되거나 현재 능력이 바뀐 경우 claim 예외로 매 접속 kick되던 것을 보류·안내로 바꿨다. 단발 능력 소모로 능력이 비면 그대로 적용한다. [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md#적용-대기-보류-2026-10-10-리뷰-반영)
- **회수 후 정산 거부 반복:** 물리 회수 저장 뒤 계좌 규칙 거부(잔액 overflow 등)는 REVIEW로 넘겨 관리자가 잔액 조정 후 removed로 정산한다.
- **보조 손 능력 아이템 복제:** 엔티티 상호작용으로 반출 후 restore가 사본을 만들던 경로를 차단했다.
- **취소된 사망의 효과 정리:** runtime/능력 상태/뽑기 연출 정리를 MONITOR·ignoreCancelled로 옮겼다. LetsBoom은 등록 순서상 정리 뒤에 시작한다.
- **343개** 시험 통과(실패/에러/스킵 0), test build 성공. 새 1개는 회수 저장 후 overflow→REVIEW→잔액 복구 후 관리자 정산이다. 사망/리스폰/엔티티 상호작용/선택 보류는 Paper 경로라 서버 시험 계획에 추가했다.

### 2026-10-10 / 3차 독립 리뷰 반영: 저장소·경제 GUI

- 저장소와 경제 GUI(판매/상점/마켓/능력 상점/주식)를 별도 리뷰했다. snapshot merge·flatten·cache 무효화·GUI quote 재검증·마켓 이중 구매·판매 snapshot 일치는 결함이 없었다. 다음을 수정했다.
- **판매로 돈 생성:** EntityThrower 임시 손 아이템/능력 아이템을 판매 대상에서 제외했다.
- cache(Long)와 DB 재판독(Int)의 정수 비교 차이로 생기던 가짜 snapshot 충돌을 값 비교로 고쳤다.
- **346개** 시험 통과(실패/에러/스킵 0), test build 성공. (이 시점의 이전 관련 시험은 이후 제거)

### 2026-10-10 / 3차 리뷰 반영: 전투 능력

- 13개 전투/이동 능력과 피해·runtime 공통 경로를 리뷰했다. WebShooter 드래곤 부위 CCE, GatheringStorm 집전 번개 미취소(이벤트 순서), EntityThrower 드래곤 부위 대상, ShieldStrike/GatheringStorm의 시전자 동기 사망 후 `!!` NPE를 수정했다. [전투](COMBAT_AND_TARGETS.md#리뷰-반영-2026-10-10)
- **346개** 시험 통과, test build 성공. 이번 변경은 Paper 이벤트 경로라 단위 시험을 추가하지 않고 서버 시험 계획에 넣었다.

### 2026-10-10 / 4차 리뷰 반영: 나머지 능력

- 나머지 29개 능력(경험치·조화·방어·연금술·소매치기·설탕·레일건 등)과 AbilityPickUp/도감을 리뷰했다. BladeLeaper 반환, 조화 게이지, 경험치 규칙, 연금술/소매치기/설탕 물품 경계는 결함이 없었다.
- LetsBoom 대상 정책/드롭 파괴, PullingBow 비적 대상, 레일 생성 취소 시 단발 소모, LittleEat 비소비 허기 증가, HeavyEnchant 모루 레벨 하락/책, EmeraldFix 결과 슬롯 소모를 수정했다. [전투](COMBAT_AND_TARGETS.md#리뷰-반영-2026-10-10)
- **347개** 시험 통과, test build 성공. 새 1개는 인챈트 최고 레벨 병합이다. 나머지는 Paper 이벤트 경로라 서버 시험 계획에 추가했다.
- 이로써 42개 능력 전체와 경제/저장/복구/명령/GUI 경로가 최소 한 번 독립 리뷰를 거쳤다. 리뷰는 정적 분석이며 실제 서버 검증을 대신하지 않는다.

### 2026-10-10 / 새 서버 전제로 이전 데이터 기능 제거

- **사용자 결정:** 기존 플레이 이력은 버리고 완전히 새로운 서버에서 처음부터 적용한다. 이전 버전 데이터의 가져오기·호환 코드(프로필/매물/수표/티켓/주식 평균가, 운영 도구의 이전 검사, 관련 관리자 명령)와 그 시험·문서·서버 시험 항목을 모두 지웠다. 위 과거 기록에서도 해당 설명을 삭제했다.
- YAML 저장소 기반 캐시 시험은 SQLite persistence의 `CachedPlayerRepositoryTest`로 옮겼다.
- **324개** 시험 통과(실패/에러/스킵 0), test build 성공.

### 2026-10-11 / 첫 서버 시험: 26.1 플레이어 파일 위치

- **서버 실패(첫 접속):** 초기 지급 probe가 `world/dimensions/minecraft/overworld/playerdata/<UUID>.dat`에서 `NoSuchFileException`으로 실패해 APPLYING이 남고 매 접속 kick되었다. 26.1부터 플레이어 파일은 `<월드>/players/data/`에 저장되는데 기본 경로를 주 월드 폴더 기준으로 계산했다.
- 수정: `PlayerDataDirectory`가 비어 있으면 `level.dat` 루트의 `players/data`, `playerdata`, 주 월드 폴더 `playerdata`를 후보로 두고, 첫 probe가 실제로 나타난 곳을 확정해 이후 그 경로만 쓴다. 확정 전 여러 후보에 같은 파일이 있으면 거부한다. 슬롯 변경 전 probe 단계에서 실패했으므로 기존 APPLYING은 다음 fresh login에서 before 상태로 재적용된다.
- 시험 2개 추가, **326개** 통과. 서버 재확인 필요.
- **서버 실패(`/fc check 10`):** 수표가 인벤토리에 들어가고 checkpoint도 저장됐지만 `Inventory serialization changed during delivery`로 실패했다. 새로 만든 Bukkit ItemStack(meta 미적용)의 직렬화 바이트가 인벤토리에서 다시 읽은 같은 아이템과 달랐다. `PaperItemCodec.payload`가 서버 stack으로 한 번 왕복한 정규 바이트를 쓰도록 고쳤다(모든 지급/회수/결제 payload 비교에 적용). 이미 실패한 기록은 정규화 이전 after 이미지를 가지므로 다음 접속에서 REVIEW가 되며 오프라인 관리자 `delivery resolve ... delivered`로 정리한다. 서버 재확인 필요.

### 2026-10-11 / 표시·연출·플레이 UX를 main으로 복원

- **사용자 지적:** 내부 동작만 고치라 했는데 메시지 문구·색, 소리, 뽑기 연출(글씨가 시야 방향으로 기울어 하늘을 보면 안 보임), 연타 동시 뽑기, 거래 메시지의 UUID 등 표시와 UX까지 바뀌었다.
- 복원:
  - 뽑기: 동시 다중 뽑기 허용, TextDisplay pitch 0(수직), main 타임라인·소리·파티클, 해금/TROLL 안내를 연출 끝에.
  - 메뉴·명령: `/ability help`·`/financial help` 원래 책, 천장/픽업/me 문구, 메뉴 소리, 오류 문구("권한이 부족합니다!", "잘못된 형식입니다. …"), 탭 완성 순서, 능력 상점 9칸.
  - 경제: 송금/수표/상점/마켓/주식 문구·색·소리·로어, 상점 원래 목록 순서, 상점·마켓 공간 부족 시 거부, 판매창을 원래처럼 물품을 넣는 방식으로(대금 멱등 확정 후 제거).
  - 능력: 사라진 소리·파티클·문구(KneePropel·BladeLeaper·EntityThrower·Alchemy·HarmonySword/Arrow·ImpactLanding·레일건 색/회전 등).
  - 플레이어 채팅의 거래/요청 UUID를 모두 제거(로그·관리자 복구 명령·복구 kick 화면만 유지).
- 해금 표시: 새 해금 능력은 연출이 끝날 때까지 선택 화면에서 숨겨 main처럼 연출 끝에 해금된다(commit은 클릭 때, 천장 카운트는 main도 클릭 때). 판매 대금은 main처럼 연출 끝에 입금 안내, 닫아도 정산되며 판매창 물품의 강제 종료 시 소실도 main과 같다. 메뉴 클릭 전체 취소와 ZombieKing "소환 시도" 채팅도 main과 같다.
- 판매창 대금도 main처럼 연출 끝에 입금한다(종료 시 대기 정산 처리). 능력 아이템은 main처럼 클릭이 막혀 판매창에 들어가지 않는다.
- 예기치 못한 실패 안내도 main과 같게 했다: 명령은 로그 후 예외를 다시 던져 Bukkit 기본 내부 오류 문구가 나오고, 이벤트는 로그만 남긴다. 54개 장터/주식 제한과 원래 안내, `[set/remove]` 형식 안내, 탭 완성 정의 순서도 복원했다. 플레이어 화면에 남는 새 문구는 복구가 필요한 접속 차단(kick) 사유뿐이다.
- 326개 시험 통과. 서버 재확인 필요.

### 2026-10-11 / 서버 실패: 블런더 부스터 토템 부활 시 능력 전환

- **증상:** 불사의 토템으로 부활하는 `EntityResurrectEvent`에서 능력을 제거하면, 그 순간 플레이어는 아직 사망 상태(체력 0)라 전환 저장이 `Player is unavailable`로 실패했다. 전환 기록이 APPLYING으로 남았고, 토템 소모로 인벤토리가 before와 달라져 다음 접속에서 REVIEW로 막혔다.
- **수정:** BlunderBooster는 다음 틱(부활 후)에 능력을 제거한다(main과 같은 결과). `DeliveryController.transition`은 사망·체력 0 상태에서는 기록을 만들기 전에 거부한다.
- **이미 막힌 기록:** 물품은 바뀌지 않았으므로 오프라인 상태에서 `/financial transition resolve <owner> <전환 UUID> retained <근거>`로 정리한다.

### 2026-10-11 / 서버 시험 결과(docs/고칠것들.md) 반영

- Paper 26.x에서 `DRAGON_BREATH`(Float)·`FLASH`(Color) 파티클이 데이터를 필수로 요구해 LetsBoom 폭발, HarmonySword/HarmonyArrow 강화 적중, Transcendence 통달 진입이 예외로 중단됐다. 바닐라 기본값(세기 1.0, 흰색)을 넘겨 연출은 그대로다.
- WebShooter: 이미 멈춘 GuardianBeam 레이저를 정리 단계에서 다시 멈추던 `Task not started` 예외를 `isStarted` 확인으로 막았고, 몹에 거는 줄 위치를 발 위치 대신 bounding box 중심으로 했다.
- Muspell: 추적 후보의 각도·거리·벽 가림 검사를 발 위치 대신 bounding box 중심으로 해 땅에 선 대상(플레이어)이 발밑 블록에 가려진 것으로 판정되지 않게 했다.
- BladeLeaper에 구리 검(COPPER_SWORD)을 추가했다.
- 능력 사용·구매·선택 직후의 내부 지급에는 "미수령 물품 N건을 지급했습니다." 안내를 보내지 않는다(연금술 등). 공간 부족으로 보관했던 물품을 나중에 받을 때만 안내한다.
- 수표: 모루로 이름을 바꾼 수표도 서버 기록 금액을 입금하되 "이 수표는 위조되어있었습니다!"(빨강)를 추가로 보인다. 발행일 뒤에 서버 시간대를 표시한다.
- `/financial help` 관리자 추가 명령은 `delivery resolve`부터 한 페이지에 2개씩 넣어 설명이 잘리지 않게 했다.
- 326개 시험 통과. 서버 재확인 필요.

### 2026-10-11 / 오래된 완료 기록 자동 정리

- 사용자 요청: 서버 시작 시 7일 지난 기록 자동 정리. `RecordRetentionDays: 7`, `StateStore.prune`. 완료된 지급/회수/전환/장터 등록 문서와 오래된 장부 줄을 지우고 VACUUM한다. 초기 보상 장부(once-only)와 프로필·매물·수표·결과·퀘스트 보상 문서·진행 중 기록은 남긴다. [저장소·복구](STORAGE_AND_RECOVERY.md#기록-보관-기간)
- 화음 검/초월에 구리 검 추가. 327개 시험 통과.

## 항목별 진행 상태

| 계획 ID | 구현 상태 | 남은 내용 |
| --- | --- | --- |
| DATA-01 | 완료(단위 검증) | 캐시·단일 저장소·능력 hot path 구현. 실제 서버 성능 측정은 최종 서버 시험 때 수행 |
| DATA-02 | 구현 완료(단위 검증) | strict YAML/원자 저장/SQLite/이름 충돌 보호, 운영 export·검사·백업 도구, 접속 프로필 실패 차단/kick, 손상 잔액 표시 구현. 운영 데이터 복구 시험은 서버 시험 단계 |
| DATA-03 | 완료(단위 검증) | 기존 서버 플레이어 ensure/현재 이름/기존 데이터 보존 구현. 실제 접속 확인은 이후 |
| MONEY-01 | 구현 완료(단위 검증) | Money 규칙·주식·송금/관리자·마켓·수표·판매·상점/능력 상점 교환·초기 지급 연결. 잔액 변경은 Accounts/BankService/InitialRewards에서만 하고 표시도 strict 읽기. 실제 서버 검증은 이후 |
| MONEY-02 | 구현 완료(저장 단위 검증) | 송금·마켓·수표·판매·상점·능력 상점·뽑기·선택권·초기/퀘스트 보상·주식 원장과 필요한 물품 회수/미수령 연결. 실제 Paper 검증은 이후 |
| MONEY-03 | 구현 완료(단위 검증) | 수표 발행/입금 멱등 원장·PDC 연결(이전 버전 수표 미지원). 실제 손·Paper 이벤트/저장 확인은 이후 |
| SELL-01 | 구현 완료(단위 검증) | 판매 세션 1개·frozen 입력/가격·공통 회수 checkpoint 후 멱등 정산·REVIEW/관리자 판단. 실제 서버 검증은 이후 |
| ITEM-01 | 구현 완료(단위 검증) | 상점/능력 상점/초기 보상/연금술/소매치기/검 반환의 전체 수량 미수령·공통 회수/복구·관리자 연결. 부분 수용 helper 제거. NPC 소매치기 드롭만 world 경계(문서화). 실제 서버 검증은 남음 |
| STOCK-01 | 구현 완료(단위 검증) | 가격/설정 정책과 실제 갱신 경로 연결. 실제 서버 실행 확인은 이후 |
| STOCK-02 | 구현 완료(정책 단위 검증) | 시간 경계/재시작 상태/1초 검사 구현. Paper 반복 작업 동작은 서버 시험 이후 |
| STOCK-03 | 구현 완료(단위 검증) | 취득 원가/매도/잔액 저장과 실패 시험. 과거 손상 원가의 자동 추정은 하지 않음 |
| STOCK-04 | 구현 완료(페이지 단위 검증) | 메타데이터 분리, 기준가 시간 저장, 등락률/지수, 주식 페이지/종목 제한 제거. 실제 화면 확인은 이후 |
| GUI-01/02/03 | 구현 완료(정책 단위 검증) | 주식·마켓·상점·판매·능력 상점·선택/도감·대상 메뉴에 Holder/action/입력 제한/다음 틱 전환 구현. 실제 서버 화면/이벤트 검증은 이후 |
| CFG-01 | 구현 완료(단위 검증) | 초기 보상·능력 상점과 31개 전체 능력 설정의 strict 타입·범위·기본값·schema/원본 보존 구현. 실제 Paper 설정 생성/enable은 이후 |
| GACHA-01 | 구현 완료(저장 단위 검증) | 양손 티켓 회수·해금/천장/결과 원자 정산·ForceTroll 대기/ack·결과 조회·연출 정리 연결. Paper 실제 검증은 이후 |
| GACHA-02 | 구현 완료(정책 단위 검증) | 공통 확률/천장·실제 추첨/help/pity 연결. EPIC 조건부/픽업 의미 보존. 실제 서버 확인은 이후 |
| AB-07 | 구현 완료(단위 검증) | typed 획득 분류·후보/픽업 snapshot/소비 전 validation, 뽑기 확정 원장/회수, 선택권 사용/PDC/guard 연결. 서버 검증은 남음 |
| GUI-04 | 구현 완료(페이지 단위 검증) | 주식·선택/도감의 실제 Holder/page/sort에 공유 Pagination 연결. 서버 화면 검증은 이후 |
| QUEST-01 | 구현 완료(단위 검증) | 4개 완료/해금 원자 정산·조화 confirmed progress/threshold·취소된 사망 제외/엔더맨 무피해 확정 피해 조건 연결. 실제 처치 귀속은 서버 시험에서 확인 |
| AB-08 | 구현 완료(단위 검증) | 레벨 감소/경험치 감소 보너스 제거·상한/재진입 보호, ZombieKing 야간 반복 수정 완료. 실제 서버 검증은 이후 |
| AB-02 | 구현 완료(저장 단위 검증) | same-code no-op·전체 슬롯 relocation/preflight·전환 checkpoint/profile 원자 정산·restart/REVIEW/관리자 판단 구현. 실제 Paper save/이벤트·복구 시험은 이후 |
| AB-01 | 구현 완료(단위 검증) | 단일 ID 레지스트리·명시적 bind/configure/start/stop/release·공통 runtime 구현. 서버 없는 실제 레지스트리/메타데이터 시험 통과. 실제 enable은 이후 |
| AB-04 | 구현 완료(단위 검증) | UUID 능력 상태·작업/일시 엔티티/laser/속성/potion lease 소유 범위와 이탈/사망/전환/종료 정리, 대상 메뉴 구현. 강제 종료 후 potion 정리·실제 서버 검증은 남음 |
| AB-03 | 구현 완료(정책 단위 검증) | transient namespace modifier로 자기 속성만 정리한다. 외부 baseValue를 덮지 않는다. 실제 중첩은 서버 시험에서 확인 |
| AB-05 | 구현 완료(정책 단위 검증) | 소모 이후 expected code의 owned railgun scope와 cleanup 구현. 실제 단발 애니메이션은 서버 시험에서 확인 |
| AB-06 | 구현 완료(정책 단위 검증) | owned Web/붙잡기/레일건·world/quit/death/변경 cleanup·물리 경계 구현. 실제 Paper는 이후 |
| COMBAT-01/02/03 | 구현 완료(정책 단위 검증) | confirmed damage·취소/귀속·상태 보존·typed target·visited/budget chain·오프라인 pet/UI 정책 구현. 보호 플러그인/토템/이벤트는 서버 시험에서 확인 |
| OPS-01 | 구현 완료(단위 검증) | System.out 교체 제거, 테스트 기반/schema/백업/저장소 복구 문서, 운영 check/backup/export/restore-copy 도구, CI, kotlin-reflect 제거 구현. 실제 운영 데이터 복사본 시험·기타 결합 제거는 남음 |
| CMD-01 | 구현 완료(단위 검증) | 공통 route/permission/console/인자/help/tab·기능 handler 분리·미구현 reload/console TODO 제거. 실제 Paper 책/콘솔은 이후 |
| 그 외 모든 항목 | 미해결 | [정비 계획](MAINTENANCE_PLAN.md)의 범위를 그대로 유지 |

## 다음 작업 순서

2026-10-10 기준 정비 계획의 모든 항목이 `구현 완료(단위/정책/저장 단위 검증)` 이상이다. 42개 능력과 경제/저장/복구/명령/GUI 경로는 최소 한 번 독립 정적 리뷰를 거쳤고 발견된 결함은 위 기록대로 수정했다. GitHub Actions(`build.yml`)가 브랜치 push마다 test build를 실행한다.

1. **최종 서버 시험**: [서버 시험 계획](SERVER_TEST_PLAN.md)을 순서대로 수행한다. 운영 데이터 복사본·격리 서버만 사용한다. 실패는 정비 계획에 새 ID로 추가한다.
2. 서버 시험 결과에 따라 결함을 수정하고, 통과한 항목의 상태를 `서버 검증 완료`로 바꾼다. 단위 시험 통과를 서버 통과로 기록하지 않는다.
3. 그 뒤에만 main 병합을 사용자와 별도로 결정한다(현재 승인 범위 밖).
4. 남겨 둔 설계 경계(정적 plugin 조회가 남은 Paper 어댑터, 메인 스레드 동기 SQLite, 강제 종료 시 potion lease)는 서버 성능/동작 측정 뒤 필요하면 별도 ID로 다룬다.

## 현재 설계의 경계

- 플레이어/마켓의 정본은 SQLite다. 새 서버에서 빈 DB로 시작하며 이전 버전 YAML 데이터는 가져오지 않는다. 운영 설정·종목 가격은 YAML로 유지한다.
- 송금/관리자 변경·마켓·수표·판매·상점·능력 상점·뽑기·선택권/초기·퀘스트 보상·능력 전환·주식은 원장/미수령·필요한 회수 복구에 연결했다. 대상 설정(AbilityEnemy) 등 비경제 필드만 호환 snapshot 저장 API를 쓴다. DB와 인벤토리가 원자적으로 함께 저장되는 것은 아니다.
- 반환하는 FileConfiguration은 호환 편집 API다. 순수 규칙·서비스로 이동하면서 직접 YAML 접근을 줄여야 한다.
- 손상(음수/누락/비정수) 잔액은 임의로 0으로 덮지 않는다. 은행 거래는 거부하고 `/financial set <UUID> <정상 금액>`으로 관리자가 명시적으로 복구하면 원장에 이전 값이 남는다. 모든 경제 호출부가 같은 strict 잔액 읽기를 쓴다.
- 서버 실행 중 파일을 외부에서 수정해도 캐시는 자동 갱신하지 않는다. 안전한 reload/운영 절차를 별도로 정한다.
- 손상 DB payload는 파싱 실패를 전달하고 해당 플레이어 접속을 차단한다. 자동 초기화로 자산을 지우지 않는다.
