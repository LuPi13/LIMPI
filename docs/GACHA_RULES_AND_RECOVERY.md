# 뽑기 규칙·후속 복구 연결

2026-10-10. 규칙/후보/도움말 분리 구현·단위 검증 완료. **티켓 소비·해금·애니메이션의 내구성 연결은 아직 남아 있다.** 실제 서버 검증은 전체 정비 이후로 미룬다.

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

## 남은 연결과 검증

현재 `GachaEvents`에는 **직접 이벤트 티켓 차감→프로필 카운터 저장→애니메이션 종료 후 해금** 경로가 남아 있다. 후보/규칙 보호를 이 경로의 원자성 해결로 처리하지 않는다. 저장 실패/서버 종료/이탈/중첩 애니메이션/ForceTroll 적용의 손실·정리 문제(GACHA-01/AB-04)는 다음 단계다.

1. 결과·before/after 카운터·후보/픽업 시점·ticket 원본을 frozen terms로 저장하고 공통 회수에서 물리 소비를 확인한다.
2. 해금/카운터/결과 영수증·필요한 강제 적용/후속 지급을 한 commit에 확정한다. 복구 중 다시 추첨하지 않는다. 이미 해금한 능력의 중복 결과도 기존 의미를 보존한다.
3. 애니메이션은 저장된 결과 표현으로 분리하고 종료/이탈/disable/예외 때 모든 task/display를 정리한다. 저장된 결과 조회와 접속 시 복구·ForceTroll 처리 경계를 정의한다.
4. 선택권 사용도 같은 원장/회수/해금 구조로 연결하고 PDC 티켓 식별/legacy 호환과 이동 제한을 통합한다. 초기·퀘스트 지급도 연결한다.

단위 시험은 기본 천장/확률 합·순차 우선순위·0/1 경계·픽업 보장/일반 픽업·카운터 변화·후보 snapshot/누락·invalid config·Long 상태/알 수 없는 필드 보존을 포함한다. Paper 이벤트/실제 티켓 소비·저장·애니메이션은 시험하지 않았다. 최종 서버에서는 보호 플러그인/item DENY·양손 입력·빠른 반복·후보/픽업 경계·저장 전후 실패/재시작·이탈/disable과 결과 복구를 확인한다.
