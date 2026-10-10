# 최종 서버 시험 계획

2026-10-10 작성. 전체 정비 구현 후 수행할 실제 Paper 시험을 한곳에 모은 순서표다. 각 항목의 세부 계약은 링크한 문서가 정본이며, 이 문서는 순서와 빠짐없는 확인을 위한 목록이다. 구현 상태는 [구현 기록](IMPLEMENTATION_LOG.md)을 따른다. **이 문서의 항목은 아직 하나도 실제 서버에서 수행하지 않았다.**

결과는 항목마다 `통과 / 실패(재현 절차) / 해당 없음`으로 기록하고, 실패는 [정비 계획](MAINTENANCE_PLAN.md)에 새 ID로 추가한다. 단위 시험 통과를 서버 통과로 기록하지 않는다.

## 0. 준비 (운영 서버 금지)

1. 운영 서버를 정상 종료하고 `plugins/LIMPI` 전체, 월드/`playerdata` 전체를 **같은 시점**으로 복사한다. 이후 모든 시험은 이 복사본을 쓰는 격리 서버에서만 한다.
2. Java 25, Paper 26.3 build 169 이상. `build/libs/LIMPI-1.0.0.jar`만 `plugins/`에 넣는다. operations JAR은 서버 플러그인이 아니다.
3. 복사본에 `java -jar LIMPI-operations-1.0.0.jar legacy-check <복사본 plugins/LIMPI>`를 실행해 플레이어 수·잔액 합계·오류/경고를 기록한다. 오류가 있으면 enable 전에 원본을 확인한다. [저장소·복구](STORAGE_AND_RECOVERY.md#운영-도구-서버-없이-실행)
4. 별도 저장 환경이면 `config.yml`의 `PlayerDataDirectory`를 실제 playerdata 위치로 지정하고, enable 로그의 `Delivery checkpoint directory`가 맞는지 확인한다.

## 1. enable·최초 이전·disable

- [ ] 최초 enable 로그: 가져온 플레이어 수·잔액 합계가 `legacy-check`와 같다. `backups/sqlite-v1/`에 원본이 생기고, 두 번째 enable에서 재가져오기를 하지 않는다. [저장소·복구](STORAGE_AND_RECOVERY.md#최초-이전)
- [ ] `check limpi.sqlite3`(서버 종료 후) 결과의 플레이어 수·합계가 이전 직후와 같다.
- [ ] 능력 설정 31개 생성/누락 보완, 손상·미래 schema 설정 원본 보존. [설정](ABILITY_SETTINGS.md)
- [ ] 주식 반복 작업 시작, 간격 경계, 재시작 후 누락 구간 정책. [주식](STOCK_TRANSACTIONS.md)
- [ ] disable 로그 예외 없음, 반복 작업/표시 엔티티/속성 modifier/포션 lease 정리. `--enable-native-access=ALL-UNNAMED` 경고 여부 기록.
- [ ] 접속 중 플레이어가 있는 상태의 enable(플러그인 재적재 상황). 손상 프로필 한 명이 다른 플레이어 초기화를 막지 않는다.

## 2. 프로필·경제

- [ ] 기존 플레이어 첫 접속: 잔액/해금/퀘스트/알 수 없는 필드 보존, 현재 이름 갱신, 신규 plugin profile만 초기 보상 지급(기존은 EXEMPT). [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md)
- [ ] 손상 잔액(소수/문자열) 프로필: 거래 거부, `/financial spy`와 주식 GUI에 "계좌 복구 필요". 프로필 로드 실패 시 kick·UUID 안내.
- [ ] 음수 legacy 잔액: 거래 거부, `/financial set <UUID> <금액>` 복구와 원장의 이전 값.
- [ ] 송금/관리자 set·add: 0/음수/Long 경계, 수수료, 같은 거래 UUID 재시도 replay. [저장소·복구](STORAGE_AND_RECOVERY.md#거래-재시도)
- [ ] 수표 발행/입금/legacy 전환. [수표](CHECKS_AND_LEGACY_MIGRATION.md#최종-서버-시험)
- [ ] 상점 구매/판매/능력 상점/에메랄드 수리. [상점·판매](SHOP_AND_SALE_RECOVERY.md#검증과-남은-확인)
- [ ] 마켓 등록 escrow/구매/회수/경쟁. [마켓](MARKET_RECOVERY.md#최종-서버-시험)
- [ ] 주식 매수/매도·가격 변경 직후 오래된 화면·상장 중지 종목 보유 유지·실현 손익. [주식](STOCK_TRANSACTIONS.md)

## 3. 지급·회수 복구 (강제 종료 포함)

각 거래에서 **회수 저장 전 / 회수 저장 후 DB commit 전 / commit 후 지급 전**에 서버 프로세스를 강제 종료하고 재시작·재접속한다. 물품 수와 잔액이 보존되고 중복 지급이 없어야 한다. [지급 복구](DELIVERY_RECOVERY.md#다음-구현서버-시험)

- [ ] 만석 인벤토리 미수령 → 공간 확보 → `/financial claim`.
- [ ] REVIEW 상태 로그인 차단 → 오프라인 관리자 `removed|retained` 판단 → 원장 감사 기록.
- [ ] 회수가 지급보다 먼저 복구되는 순서, 소유자 예약 공유(마켓/공통 회수/능력 전환 동시 준비 불가).
- [ ] 별도 인벤토리 플러그인·보호 플러그인과 playerdata 저장 위치 호환.

## 4. 능력

- [ ] 선택권/뽑기 양손 사용, useItemInHand DENY 존중, 연출 중 입력 무소모, 결과 조회 `/ability result`. [뽑기](GACHA_RULES_AND_RECOVERY.md)
- [ ] 천장 경계(LEGENDARY 100번째, EPIC 조건부 10번째), 픽업 만료 경계, 후보 0개 거부.
- [ ] 능력 전환: 같은 능력 no-op, 제한 슬롯의 일반 물품 relocation, 공간 부족 시 티켓 소비 전 거부, 전환 중 강제 종료. [전환](ABILITY_TRANSITIONS.md)
- [ ] 이탈/사망/월드 변경/disable 후 반복 작업·표시 엔티티·속성 modifier·포션 잔여 없음, 외부 플러그인 속성/포션 공존. [runtime](ABILITY_RUNTIME.md), [포션](ABILITY_POTIONS.md)
- [ ] 연금술/소매치기(플레이어·NPC)/설탕/검 반환 물품 보존. [물품](ABILITY_ITEM_TRANSACTIONS.md), [설탕](SUGAR_CONSUMPTION.md)
- [ ] WebShooter 레이저(GuardianBeam NMS)·ZombieKing 헬멧/낮밤·OverBalance 단발·ImpactLanding 비용 상한. [물리](ABILITY_PHYSICS.md), [Paper 전환](PAPER_26_3_UPGRADE.md#남은-서버-확인-순서)
- [ ] 퀘스트 4종 완료/해금, 취소된 사망 미집계, LetsBoom이 취소된 사망에 폭발하지 않음.

## 5. 전투·대상

- [ ] 보호 플러그인이 취소한 피해에 게이지/부가 효과 없음, 토템/흡수/방패, 처치 귀속. [전투](COMBAT_AND_TARGETS.md)
- [ ] ChainLightning 연쇄 상한, 아군/중립/길들인 몹(오프라인 주인)/팀 대상 정책과 대상 메뉴 일치.

## 6. 메뉴·명령

- [ ] 모든 메뉴에서 drag/shift/숫자키/offhand/doubleclick/하단 클릭 차단, 다음 틱 화면 전환, 목록 0/45/46/90/91개 페이지. [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md)
- [ ] 명령 권한/콘솔 허용/탭 완성/도움말 책, 경제·능력 시스템 비활성 시 복구 명령만 노출. [명령](COMMANDS.md)

## 7. 운영·성능

- [ ] 실행 중 `backup`(서버 켜짐)과 종료 후 `export` → `restore-copy` → 새 DB로 교체한 격리 서버 enable → 잔액·원장·능력·퀘스트 비교. 서버 DB 교체는 운영자가 직접, 기존 DB·WAL 별도 보존 후.
- [ ] 플레이어 수별 틱 시간, 이벤트당 DB 접근, 거래/접속 저장 지연(현재 메인 스레드 동기 SQLite). [정비 계획](MAINTENANCE_PLAN.md#6-필수-회귀-시나리오)

## 결과 기록

| 날짜 | 서버/빌드 | 범위 | 결과 | 비고 |
| --- | --- | --- | --- | --- |
| (미수행) | | | | |
