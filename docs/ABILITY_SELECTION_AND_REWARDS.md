# 선택권·티켓·초기/퀘스트 보상

2026-10-10. 실제 경로 연결과 저장소 단위 시험 완료. 전체 능력 전환/수명/속성 정비와 실제 서버 시험은 남아 있다.

## 선택권과 메뉴

AbilitySelection은 해금한 능력을 적용할 권한을 선택권으로 구매한다. 해금되지 않은 능력을 선택권으로 새로 해금하는 것으로 기존 기능 의미를 바꾸지 않았다. 같은 적용 능력은 티켓을 소비하지 않는다.

- SelectionTerms는 실제 ticket payload/소모 슬롯/등급/이전 능력 ID/다음 ID를 저장한다. 실제 어댑터가 알려진 능력과 등급, 해금/권한을 확인하고, 제한 슬롯의 일반 물품을 옮길 전체 공간을 티켓 소비 전 계산한다.
- RemovalJournal이 storage+offhand 37칸의 정확한 source 티켓 1개 회수를 확인한다. DB 검증은 최신 프로필의 이전 ability·해금 목록·미확인 적용 대기를 확인한다. 정산은 selection_result와 selection_pending, 회수 DONE/영수증을 함께 commit한다.
- 프로필 ability를 정산 안에서 먼저 바꾸지 않는다. claim/fresh login에서 알려진 같은 등급의 능력을 적용하고 프로필에 코드가 저장된 뒤 멱등 ack한다. 적용/ack 실패에도 구매 권한과 결과가 남는다. Gacha ForceTroll/selection의 대기는 동시에 하나만 허용하고 확인 전 다음 추첨/선택 준비를 거부한다.
- /ability result <거래 UUID>는 자신의 뽑기/선택 확정 기록을 조회한다. 불확실한 회수는 /financial removal과 오프라인 removed|retained 검토를 사용한다. retained는 적용 권한을 만들지 않는다.

능력 선택/도감은 MenuHolder의 page와 code/sort action으로 연결했다. 45개 내용 페이지에 공유 Pagination을 사용한다. 45/90개에서도 빈 다음 페이지를 만들지 않고 잘못된 요청은 유효 범위로 제한한다. Player map을 제거했다. 하단/특수 click은 실행하지 않고 중앙 읽기 전용 guard가 drag를 막는다. 화면 전환은 다음 틱, 예상 top inventory가 여전히 같은 경우만 열린다. 도감 좌클릭 무소모 적용은 limpi.ability.admin.set(default op), 일반 선택은 limpi.ability.select(default true)다.

## 티켓 식별과 이동

새 지급/구매 티켓에는 limpi:ability_ticket=GACHA|등급, ability_ticket_format=1 PDC를 넣는다. display/lore를 기능 ID로 비교하지 않는다. PDC가 없는 기존 티켓은 기존 전체 metadata template과 정확히 일치할 때만 인식한다. legacy를 자동 재발행하거나 아이템/미수령 NBT를 덮어쓰지 않는다. 이미 지급 대기인 이전 티켓도 소비/이동 제한에서 동일하게 다룬다. 잘못된/future marker를 정상 티켓으로 추정하지 않는다.

AbilityTicketGuard는 수동 click/cursor/hotbar/offhand/shift/drag와 hopper 이동/수집, crafting 입력·완성에 같은 정책을 적용한다. 일반 보관함은 허용하고 소모/가공 입력은 거부한다. marker가 있는 미지원 티켓도 소모 입력으로 이동하지 못하게 보호한다. 능력 시스템 off에서도 이동 guard는 등록한다. 실제 보호 플러그인·자동 제작기/기존 입력에 놓인 아이템·크리에이티브 이벤트는 최종 서버에서 확인한다.

## 초기 지급

InitialTicket/COMMON/RARE/EPIC은 비음수 정수 Long으로 읽으며 명시적 0을 유지하고 소수/문자열/음수를 거부한다. InitialMoney도 strict 정수/Money로 읽는다.

InitialRewards.register는 plugin profile이 없을 때만 신규 계좌와 frozen 지급 항목·WAITING을 함께 생성한다. 기존/이전 profile은 EXEMPT로 기록하고 소급 지급하지 않는다. 이후 설정 변경/이름 변경/재접속에도 최초 보상은 바뀌지 않는다. 능력 시스템이 꺼져 있으면 WAITING을 보존하고 켠 뒤 claim에서 전체 수량 미수령과 GRANTED를 원자 생성한다. 초기 수량이 모두 0이면 빈 미수령을 만들지 않고 GRANTED를 기록한다. 가방이 차면 초기 물품은 delivery pending으로 남으며 이후 claim한다.

## 퀘스트와 레벨

QuestRewards는 완료 목록·능력 해금·quest_reward 증거·원장 영수증을 한 transaction에 저장한다. 4개 실제 퀘스트의 clearQuests 경로를 연결했다. 기존 clearedQuests에 있지만 보상이 누락된 과거 부분 저장은 최초 원장 작성 시 repair한다. registry 등록 후 claim에서 알려진 완료 퀘스트의 누락 보상을 확인한다. 원장 기록 이후 명시적으로 지운 보상은 새 이벤트로 자동 재지급하지 않는다. 기존 알 수 없는 quest/ability 코드와 사용자 필드를 보존한다.

조화:검/화살의 기존 quest.harmony_sword/arrow 필드 의미를 유지한다. 강화 피해를 적용한 이벤트가 최종 취소되지 않았고 finalDamage>0일 때 MONITOR에서 UUID transaction으로 진행을 기록한다. 양쪽 10회 도달과 완료/해금도 같은 commit이다. 완료 후 카운터는 더 증가하지 않으며 기존 10 초과 값은 줄이지 않는다. malformed/negative progress를 0으로 덮지 않는다. 다른 전투 게이지/효과의 전체 취소 규칙과 특수 처치 귀속은 COMBAT-01/02/03 정비 대상이다.

TerraBurning/ExpRich는 실제 레벨 증가 때만 +2/+4를 주고 재진입을 막는다. 레벨 감소/동일 레벨에는 보너스를 붙이지 않으며 경험치 감소에는 배수를 붙이지 않는다. 상한은 Int.MAX_VALUE로 포화시켜 음수 overflow를 막는다. 실제 Paper level/exp 이벤트 순서는 최종 서버 검증에서 확인한다.

## 현재 검증과 다음 작업

전체 213개 테스트 통과. 추가 29개는 실제 SQLite 선택권 회수/replay/실패/재시작/ack/관리자 판단, 제한 슬롯 물품 보존·공간 부족, 신규/legacy/0/비활성 대기 초기 지급과 실패, 퀘스트 완료/legacy repair/threshold/부분 저장 차단/typed progress, 레벨·경험치 감소/상한을 확인한다. GUI 페이지는 기존 공유 Pagination의 0/1/42/45/46/90/91개 및 범위 시험을 실제 두 메뉴에 연결했다.

실제 Paper item PDC/legacy match·이벤트/자동 제작기·saveData·GUI·능력 장착/속성/효과는 서버 미검증이다. AB-02/03/04/05/06 전체 전환/세션/task/entity/AttributeModifier 및 42개 능력의 전투·수명 정비, 주식 원장·운영 도구/CI·남은 명령/대상 메뉴가 다음 범위다.
