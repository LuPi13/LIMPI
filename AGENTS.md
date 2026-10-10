# LIMPI 작업 지침

- 작업 전에 [정비 계획](docs/MAINTENANCE_PLAN.md), [Paper 26.3 기록](docs/PAPER_26_3_UPGRADE.md), [구현 기록](docs/IMPLEMENTATION_LOG.md)을 읽고 항목 ID로 진행 상태를 관리한다.
- 사용자 승인 범위: refactor/maintenance-paper26.3에서 전체 문제 수정·구조 개선·문서·커밋·가능한 push. **main 변경/병합 금지. 실제 서버 시험은 전체 구현 이후([서버 시험 계획](docs/SERVER_TEST_PLAN.md)).** 사용자가 전체 작업이 끝날 때까지 계속 진행하라고 명시했다. 중간 커밋을 전체 목표 완료로 취급하거나 서버 미검증 때문에 구현을 중단하지 않는다.
- 기존 UUID/능력 ID/잔액/해금/퀘스트/알 수 없는 사용자 필드를 보존한다. schema/migration/복구 없는 초기화·덮어쓰기를 하지 않는다.
- 정본은 limpi.sqlite3이며 이전 YAML은 최초 가져오기 원본이다. stale YAML 자동 동기화/DB 삭제 후 재가져오기를 하지 않는다. [저장소·복구](docs/STORAGE_AND_RECOVERY.md)를 따른다.
- Money는 비음수 Long/checked arithmetic. 음수 legacy balance는 임의 초기화하지 않고 원장을 남기는 관리자 복구만 허용한다.
- DB와 live inventory는 하나의 원자 transaction이 아니다. [지급 복구](docs/DELIVERY_RECOVERY.md), [마켓 복구](docs/MARKET_RECOVERY.md), [상점·공통 회수](docs/SHOP_AND_SALE_RECOVERY.md)의 pending/checkpoint/REVIEW·오프라인 관리자 판단을 유지한다.
- owner의 market escrow/common removal checkpoint 예약을 공유한다. 회수는 지급보다 먼저 복구한다. 실패한 live inventory를 정상 재시도 대상으로 사용하지 않는다. RemovalEffect는 주어진 DB transaction에서만 정산하며 Bukkit/메시지/nested DB transaction을 호출하지 않는다.
- [수표·legacy 전환](docs/CHECKS_AND_LEGACY_MIGRATION.md): 공개 해시는 발행 증거가 아니며 legacy 수표를 자동 입금하지 않는다.
- [뽑기 규칙·복구](docs/GACHA_RULES_AND_RECOVERY.md): typed 획득 분류/후보/픽업 snapshot, LEGENDARY 우선·100번째, EPIC 조건부 10번째. frozen 결과→회수 확인→해금/천장/결과 commit→표현이며 복구 중 재추첨하지 않는다.
- [선택권·보상](docs/ABILITY_SELECTION_AND_REWARDS.md): 선택권 회수→적용 대기/영수증→platform 적용/ack. 초기 보상은 새 plugin profile만 frozen once-only 지급, migrated profile은 EXEMPT. 퀘스트 진행/완료/해금은 원자 정산한다. 새 PDC 티켓과 exact legacy template을 함께 인식한다.
- 선택/도감은 MenuHolder의 ability ID/page/sort를 사용한다. GUI에서 아이템 이름·title·현재 목록 index를 권한으로 쓰지 않는다. 다음 틱 전환과 drag/shift/hotbar/offhand 취소를 유지한다.
- 능력 전환의 슬롯/속성/작업·세션 전체 정비는 진행 중이다. 일반 제한 슬롯 물품은 전체 relocation plan으로 보존하고 공간 부족이면 티켓 소비 전 거부한다. 단위 시험을 실제 Paper save/이벤트/표시 정리/능력 효과 검증으로 과장하지 않는다.
- 배포 산출물은 build/libs에만 생성한다. 자동 서버 복사/실행을 하지 않는다.
- 최신 기준: **342개 테스트 통과**, test build 성공. 능력 계약은 [runtime](docs/ABILITY_RUNTIME.md), [전환](docs/ABILITY_TRANSITIONS.md), [전투](docs/COMBAT_AND_TARGETS.md), [설정](docs/ABILITY_SETTINGS.md), [물품 거래](docs/ABILITY_ITEM_TRANSACTIONS.md), [포션](docs/ABILITY_POTIONS.md), [설탕](docs/SUGAR_CONSUMPTION.md), [물리](docs/ABILITY_PHYSICS.md), 경제 추가 계약은 [주식 원장](docs/STOCK_TRANSACTIONS.md), 운영 도구는 [저장소·복구](docs/STORAGE_AND_RECOVERY.md), 명령 계약은 [명령 정의](docs/COMMANDS.md). 미완료 범위·다음 작업·검증 경계는 구현 기록에서 확인하며 상태를 중복 복사해 오래된 지침을 남기지 않는다.
