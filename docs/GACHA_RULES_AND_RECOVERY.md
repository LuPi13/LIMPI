# 뽑기 규칙·후속 복구 연결

2026-10-10. 규칙/후보/도움말 분리 구현·단위 검증 완료. **티켓 회수·결과 확정·연출 분리를 연결했고 저장소 복구를 단위 검증했다.** 실제 서버 검증은 전체 정비 이후로 미룬다.

## 확률과 천장

`GachaPolicy`/`PityCurve`/`GachaSettings`는 Bukkit 인벤토리·능력 객체·파일 저장에 의존하지 않는다. RNG를 주입하고 불변 설정/카운터/후보/픽업 snapshot으로 결과와 다음 카운터를 계산한다. 후보가 비어 있거나 픽업이 해당 등급 후보에 없으면 sampling 전에 거부한다.

`Count`는 마지막 해당 등급 이후 실패한 횟수다. 다음 시도는 `Count + 1`이며, `IncreaseCount`번째부터 첫 increment를 적용한다. 조건부 확률은 `min(1, Base + Increment × max(0, Count - IncreaseCount + 2))`다. 기본 설정에서 LEGENDARY 조건부 100%는 100번째, EPIC은 10번째다. 기존 구현은 증가/보장을 한 시도 늦게 적용했고 도움말은 또 다른 계산을 사용했다. 이번 변경은 표시된 시도 기준으로 통일하며 기존 저장된 Count/Ceiling 값을 초기화하지 않는다.

- LEGENDARY→EPIC→TROLL→RARE 순서로 독립 조건부 판정을 한다. 앞선 판정이 성공하면 뒤 판정을 실행하지 않는다. 모두 실패하면 COMMON이다.
- EPIC의 조건부 100%는 LEGENDARY 실패 때의 보장이다. LEGENDARY가 우선하므로 “10회 이내 무조건 EPIC”이라고 안내하지 않는다. LEGENDARY 획득 시 EPIC.Count는 증가한다.
- 실제 다음 추첨 확률은 LEGENDARY=`l`, EPIC=`(1-l)e`, TROLL=`(1-l)(1-e)t`, RARE=`(1-l)(1-e)(1-t)r`, COMMON=`(1-l)(1-e)(1-t)(1-r)`다. 조건부 확률을 합산하거나 단일 categorical 추첨으로 바꾸지 않았다.
- probability=0은 RNG=0에서도 성공하지 않으며 probability=1은 항상 성공한다. Base/PickUp/TROLL/RARE는 유한 [0,1], Increment는 유한 비음수, IncreaseCount는 양수 정수, SemiCeiling/카운터는 비음수 정수로 검증한다. 문자열/소수 Count를 0으로 대체하지 않는다.
- Increment=0, Base<1이면 유한 보장이 없다. Base=1이면 첫 시도부터 보장이다. BigDecimal 계산으로 보장 경계의 floating point ceil 오류와 큰 카운터 산술 오류를 피한다.
- 해당 등급의 픽업 아닌 능력을 SemiCeiling회 연속 얻으면 다음 해당 등급은 픽업이다. 일반 후보에도 픽업이 포함되므로 일반 선택에서 픽업이 나와도 Ceiling을 0으로 초기화한다. 실제 픽업 확률이 설정 PickUp 비율보다 높을 수 있는 기존 의미를 유지한다.

`/ability help`와 `ceiling|pity`는 같은 정책을 사용한다. 현재 실패 횟수/실제 다음 등급 확률/조건부 100%까지 남은 시도/픽업 아닌 연속 횟수를 안내한다. 하위 조건부 확률을 무조건 평균 등장 횟수로 표시하던 기존 기대값 설명은 제거했다. EPIC의 경쟁 판정까지 고려하지 않은 평균을 그대로 표시하지 않는다.

## 획득 분류와 snapshot

능력에 `AcquisitionType.GACHA/QUEST`를 넣어 설명 Component를 획득 권한으로 비교하지 않는다. 기존 퀘스트 전용 `exp_rich`, `judgement_ray`, `transcendence`, `zombie_king`을 QUEST로 분류했고 다른 기존 뽑기 후보는 유지한다. 기존 codeName/표시 설명/해금/퀘스트 필드는 보존한다. 낮은 단계 능력의 relatedQuest는 후속 퀘스트 연결이며 그 자체를 QUEST 전용이라고 추정하지 않는다.

추첨 전에 설정·프로필 카운터·eligible code 목록·하나의 rounded pickup 시점을 읽고 결과/다음 카운터를 계산한다. 후보/픽업 조회 실패 때는 아직 티켓을 소비하지 않는다. PickUpChange는 양수 정수/밀리초 변환 overflow를 검사한다. 기존 seed 기반 shuffle 후보 순서를 유지한다.

## 티켓 회수와 결과 확정

실제 GachaEvents는 설정/후보/픽업 한 시점으로 결과를 뽑아 GachaTerms에 티켓 payload·소모 슬롯·before/after 카운터·등급/능력 ID·픽업 여부/시각·ForceTroll 여부를 고정한다. 후보 전체나 설정을 복구 시 재조회해 재추첨하지 않는다. 준비 기록에 결과가 들어 있으므로 재시작/관리자 복구도 동일 결과를 사용한다.

1. 공통 RemovalJournal에 frozen terms와 인벤토리 before/after를 저장한다. 정확히 해당 슬롯의 일치하는 티켓 1개만 감소하고, 다른 슬롯이 변하면 준비를 거부한다.
2. Paper playerdata checkpoint로 물리 회수를 확인한다. 기존 판매/마켓은 storage 36칸을 유지한다. 뽑기는 storage+offhand 37칸 snapshot으로 양손 모두 지원하며 복구 시 기록 길이에 맞는 port를 사용한다. 방어구는 포함하지 않는다.
3. GachaSettlement가 최신 DB 프로필의 before 카운터와 해금 목록 타입을 확인한 뒤 해금·after 카운터·gacha_result/{owner}/{id}·회수 DONE·정산 영수증을 한 commit에 저장한다. 기존 해금·퀘스트·잔액·알 수 없는 필드를 보존한다. 이미 해금한 결과는 목록에 중복 추가하지 않지만 정상 유료 추첨 결과와 카운터를 기록한다.
4. 정산 실패는 회수 대기를 남긴다. fresh login 복구가 저장된 checkpoint/after를 확인하면 재소모/재추첨 없이 같은 결과를 정산한다. 불일치는 자동 추측하지 않고 REVIEW/관리자 절차를 사용한다.

보호 플러그인의 useItemInHand DENY를 존중한다. 연출 중 새 입력은 무소모로 거부해 중첩/양손 중복 실행을 막는다. /ability result <거래 UUID>로 자신의 확정 결과를 조회한다. 아직 회수 대기 중이면 결과가 없으며 관리자는 /financial removal <owner UUID>와 기존 removed|retained 절차를 확인한다. retained는 해금/카운터/결과를 만들지 않고, removed는 frozen 결과를 원자 정산한다.

## ForceTroll과 연출 수명

TROLL 강제 적용은 해금 commit에서 gacha_apply_pending/{owner}/{id}를 함께 저장한다. 프로필 ability는 정산 내부에서 미리 바꾸지 않는다. 능력 레지스트리를 등록한 후 claim/접속 복구에서 기존 applyAbility를 호출하고, 프로필에 해당 코드가 저장된 것을 확인한 뒤 멱등 ack한다. 적용 또는 ack 실패 시 대기 기록과 결과는 남는다. 미확인 강제 적용이 있으면 다음 추첨 준비를 거부한다. 능력 시스템이 꺼져 있으면 이 대기를 보존하며 다시 켠 뒤 처리한다.

같은 능력 재적용 검사를 속성 초기화보다 앞으로 옮겨 ack 재시도가 속성을 다시 초기화하지 않게 했다. 기존 능력 제한 슬롯을 비울 때는 그 능력의 activeItem과 일치할 때만 제거한다. 새 제한 슬롯 보존·속성 modifier 소유·전환 checkpoint는 이후 [능력 전환](ABILITY_TRANSITIONS.md)/[runtime](ABILITY_RUNTIME.md)에서 연결했다. 현재 TROLL 3개에는 제한 슬롯이 없으며 복구 adapter도 이 조건을 검사한다.

GachaAnimation은 결과 표현만 담당한다. 해금/카운터/능력 적용/저장을 호출하지 않는다. owner UUID마다 하나의 반복 task와 display 목록을 추적하고 부분 spawn부터 소유권을 기록한다. 약 241틱 연출을 유지하되 중첩 지연 작업을 제거했다. 정상 종료·quit·world change·death·disable·spawn/실행 예외에서 task/display를 정리한다. 표시 엔티티는 persistent=false이며 연출이 실패하거나 중단돼도 확정 결과는 유지한다.

## 검증과 남은 작업

전체 **184개** 테스트 통과(실패/에러/스킵 0), test build 성공. GachaSettlementTest 10개는 실제 임시 SQLite로 티켓 확인 전 결과 부재, 원자 해금/천장/결과/DONE, 동일 UUID replay와 fingerprint 충돌, commit 실패 후 재시작 복구, 중복 해금, ForceTroll 대기/ack 실패·retry, 관리자 retained/removed, 잘못된 회수·카운터·해금 목록 거부, strict frozen terms, 37칸 offhand 복구를 확인한다.

Paper 이벤트/실제 saveData와 playerdata·양손 우클릭·보호 플러그인·task/display 정리·TROLL 실제 적용은 서버에서 시험하지 않았다. 최종 서버에서는 빠른 반복/양손 입력, 후보/픽업 경계, 저장 전후 실패/재시작, 연출 중 이탈/월드 변경/사망/disable, 결과 조회/관리자 복구, 능력 시스템 off→on 대기 보존을 확인한다.

선택권 사용/적용 원장, PDC 티켓 식별/공통 이동 제한과 초기·퀘스트 보상을 추가 연결했다. [선택권·보상](ABILITY_SELECTION_AND_REWARDS.md)을 따른다. AB-02/03/04 전환·수명/속성은 이후 단계에서 연결했다. 서버 검증은 남아 있다.
