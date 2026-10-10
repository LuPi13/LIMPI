# 최종 서버 시험 계획

2026-10-10 작성. 전체 정비 구현 후 수행할 실제 Paper 시험을 한곳에 모은 순서표다. 각 항목의 세부 계약은 링크한 문서가 정본이며, 이 문서는 순서와 빠짐없는 확인을 위한 목록이다. 구현 상태는 [구현 기록](IMPLEMENTATION_LOG.md)을 따른다. **이 문서의 항목은 아직 하나도 실제 서버에서 수행하지 않았다.**

결과는 항목마다 `통과 / 실패(재현 절차) / 해당 없음`으로 기록하고, 실패는 [정비 계획](MAINTENANCE_PLAN.md)에 새 ID로 추가한다. 단위 시험 통과를 서버 통과로 기록하지 않는다.

## 0. 준비

1. 새 서버를 준비한다. 이전 버전 LIMPI 데이터(`playerData/`, `financial/market.yml` 등)는 넣지 않는다. 이 플러그인은 이전 데이터를 가져오지 않는다.
2. Java 25, Paper 26.3 build 169 이상. `build/libs/LIMPI-1.0.0.jar`만 `plugins/`에 넣는다. operations JAR은 서버 플러그인이 아니다.
3. 별도 저장 환경이면 `config.yml`의 `PlayerDataDirectory`를 실제 playerdata 위치로 지정하고, enable 로그의 `Delivery checkpoint directory`가 맞는지 확인한다.
4. 강제 종료·복구 시험(3·7단계) 전에는 서버를 정상 종료하고 `plugins/LIMPI`와 월드/`playerdata`를 같은 시점으로 백업해 두면 다시 시작하기 쉽다.

## 1. enable·disable

- [ ] 첫 enable에서 빈 `limpi.sqlite3`가 생기고 예외가 없다. 재시작해도 데이터가 유지된다.
- [ ] 서버 종료 후 operations JAR `check limpi.sqlite3`가 오류 없이 플레이어 수·잔액 합계를 보인다. [저장소·복구](STORAGE_AND_RECOVERY.md#운영-도구-서버-없이-실행)
- [ ] 능력 설정 31개 생성/누락 보완, 손상·미래 schema 설정 원본 보존. [설정](ABILITY_SETTINGS.md)
- [ ] 주식 반복 작업 시작, 간격 경계, 재시작 후 누락 구간 정책. [주식](STOCK_TRANSACTIONS.md)
- [ ] disable 로그 예외 없음, 반복 작업/표시 엔티티/속성 modifier/포션 lease 정리. `--enable-native-access=ALL-UNNAMED` 경고 여부 기록.
- [ ] 접속 중 플레이어가 있는 상태의 enable(플러그인 재적재 상황). 손상 프로필 한 명이 다른 플레이어 초기화를 막지 않는다.

## 2. 프로필·경제

- [ ] 첫 접속: profile 생성, InitialMoney·초기 티켓 1회 지급, 재접속·이름 변경 후에도 재지급 없음. 능력 시스템 off→on 시 대기 지급. [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md)
- [ ] 손상 프로필(DB payload를 직접 깨뜨림, 소수/문자열 잔액): 거래 거부, `/financial spy`와 주식 GUI에 "계좌 복구 필요", 로드 실패 시 kick·UUID 안내, `/financial set`으로 원장과 함께 복구.
- [ ] 송금/관리자 set·add: 0/음수/Long 경계, 수수료, 같은 거래 UUID 재시도 replay. [저장소·복구](STORAGE_AND_RECOVERY.md#거래-재시도)
- [ ] 수표 발행/입금/복사본 재사용 거부. [수표](CHECKS.md#최종-서버-시험)
- [ ] 상점 구매/판매/능력 상점/에메랄드 수리. [상점·판매](SHOP_AND_SALE_RECOVERY.md#검증과-남은-확인)
- [ ] 마켓 등록 escrow/구매/회수/경쟁. [마켓](MARKET_RECOVERY.md#최종-서버-시험)
- [ ] 주식 매수/매도·가격 변경 직후 오래된 화면·상장 중지 종목 보유 유지·실현 손익. [주식](STOCK_TRANSACTIONS.md)

## 3. 지급·회수 복구 (강제 종료 포함)

각 거래에서 **회수 저장 전 / 회수 저장 후 DB commit 전 / commit 후 지급 전**에 서버 프로세스를 강제 종료하고 재시작·재접속한다. 물품 수와 잔액이 보존되고 중복 지급이 없어야 한다. [지급 복구](DELIVERY_RECOVERY.md#다음-구현서버-시험)

- [ ] 만석 인벤토리 미수령 → 공간 확보 → `/financial claim`.
- [ ] 미수령/회수 대기 중 사망 화면에서 종료 → 재접속(사망 화면 유지, kick 없음) → 리스폰 후 복구.
- [ ] 선택권 정산 직후 미수령 지급이 빈칸을 채워 제한 슬롯 relocation 공간 부족 → 보류 안내 → 공간 확보 후 claim.
- [ ] REVIEW 상태 로그인 차단 → 오프라인 관리자 `removed|retained` 판단 → 원장 감사 기록.
- [ ] 회수가 지급보다 먼저 복구되는 순서, 소유자 예약 공유(마켓/공통 회수/능력 전환 동시 준비 불가).
- [ ] 별도 인벤토리 플러그인·보호 플러그인과 playerdata 저장 위치 호환.

## 4. 능력

- [ ] 선택권/뽑기 양손 사용, useItemInHand DENY 존중, 연출 중 입력 무소모, 결과 조회 `/ability result`. [뽑기](GACHA_RULES_AND_RECOVERY.md)
- [ ] 천장 경계(LEGENDARY 100번째, EPIC 조건부 10번째), 픽업 만료 경계, 후보 0개 거부.
- [ ] 능력 전환: 같은 능력 no-op, 제한 슬롯의 일반 물품 relocation, 공간 부족 시 티켓 소비 전 거부, 전환 중 강제 종료. [전환](ABILITY_TRANSITIONS.md)
- [ ] 보조 손 능력 아이템을 알레이/아이템 액자/갑옷 거치대에 넣을 수 없음, 복제 없음.
- [ ] 다른 플러그인이 사망을 취소하면 효과/속성 유지.
- [ ] 이탈/사망/월드 변경/disable 후 반복 작업·표시 엔티티·속성 modifier·포션 잔여 없음, 외부 플러그인 속성/포션 공존. [runtime](ABILITY_RUNTIME.md), [포션](ABILITY_POTIONS.md)
- [ ] 연금술/소매치기(플레이어·NPC)/설탕/검 반환 물품 보존. [물품](ABILITY_ITEM_TRANSACTIONS.md), [설탕](SUGAR_CONSUMPTION.md)
- [ ] WebShooter 레이저(GuardianBeam NMS)·ZombieKing 헬멧/낮밤·OverBalance 단발·ImpactLanding 비용 상한. [물리](ABILITY_PHYSICS.md), [Paper 전환](PAPER_26_3_UPGRADE.md#남은-서버-확인-순서)
- [ ] LetsBoom 폭발이 아군/펫/본인 드롭/액자를 보호하고 적만 피해, PullingBow 비적 대상 무효, 레일 생성 취소 시 OverBalance 유지, LittleEat 케이크/포화, HeavyEnchant 모루(IV+IV, V+III, 책), EmeraldFix 결과 슬롯 무시.
- [ ] 퀘스트 4종 완료/해금, 취소된 사망 미집계, LetsBoom이 취소된 사망에 폭발하지 않음.

## 5. 전투·대상

- [ ] 보호 플러그인이 취소한 피해에 게이지/부가 효과 없음, 토템/흡수/방패, 처치 귀속. [전투](COMBAT_AND_TARGETS.md)
- [ ] 엔더 드래곤 대상 WebShooter 충돌/EntityThrower 매달리기, GatheringStorm 집전 번개 대체(쿨다운 중에는 vanilla 번개).
- [ ] ChainLightning 연쇄 상한, 아군/중립/길들인 몹(오프라인 주인)/팀 대상 정책과 대상 메뉴 일치.

## 6. 메뉴·명령

- [ ] 모든 메뉴에서 drag/shift/숫자키/offhand/doubleclick/하단 클릭 차단, 다음 틱 화면 전환, 목록 0/45/46/90/91개 페이지. [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md)
- [ ] 명령 권한/콘솔 허용/탭 완성/도움말 책, 경제·능력 시스템 비활성 시 복구 명령만 노출. [명령](COMMANDS.md)

## 7. 운영·성능

- [ ] 실행 중 `backup`(서버 켜짐)과 종료 후 `export` → `restore-copy` → 새 DB로 교체 후 enable → 잔액·원장·능력·퀘스트 비교. 서버 DB 교체는 운영자가 직접, 기존 DB·WAL 별도 보존 후.
- [ ] 플레이어 수별 틱 시간, 이벤트당 DB 접근, 거래/접속 저장 지연(현재 메인 스레드 동기 SQLite). [정비 계획](MAINTENANCE_PLAN.md#6-필수-회귀-시나리오)

## 결과 기록

| 날짜 | 서버/빌드 | 범위 | 결과 | 비고 |
| --- | --- | --- | --- | --- |
| (미수행) | | | | |
