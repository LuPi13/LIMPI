# 능력 피해·취소·대상 정책

2026-10-10. COMBAT-01/02/03, GUI-01/02/03의 대상 메뉴 및 QUEST-01의 전투 조건.

## 피해 경로

- 별도 능력 피해는 AbilityDamage의 DamageSource와 동기 DamageContext를 사용한다. 소유자 UUID/능력/발동 종류/대상을 호출 중에만 보유하고 예외·중첩 호출 뒤 원래 context를 복원한다. 다른 스레드나 다음 기본 공격으로 넘기지 않는다.
- Paper는 DamageSource를 지정한 damage와 health를 0으로 설정하는 kill을 구분한다([26.3 API](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Damageable.html)). 즉사·회복 반전도 damage 경로를 사용한다. 직접 health=0/health 차감을 제거했다. 피해 이벤트가 취소되거나 양의 최종 피해가 관측되지 않으면 추가 화상·넉백·몹 변환을 적용하지 않는다.
- 수치에는 비음수/유한값 검증과 float 변환 상한을 적용한다. KillingDice는 실제 최대 체력 value로 확률을 계산하고 확인된 기본 공격 이후에만 즉사 시도를 예약한다. 즉사 피해 역시 방어/흡수·취소·토템 경로를 통과하며, 체력을 직접 0으로 만들어 강제로 사망시키지 않는다. 해당 Paper 실제 처리는 서버 검증 대상이다.
- ZombieKing 고정 추가 피해는 기존 공격의 같은 이벤트에 추가한다. 기존 공격의 방어 감소분은 그대로 두고 고정 보너스가 추가되도록 BASE만 변경하며 합산 피해에 흡수를 재계산한다. 따라서 보너스가 일반 방어구 감소를 받는 것으로 바뀌지 않는다. 보호 취소와 흡수·토템은 피해 경로에서 처리한다. DamageModifier 호환 API는 이 한 adapter에 한정한다.
- ZombieKing 마법 회복 반전은 MAGIC 피해, 마법 피해 반전은 CUSTOM heal 이벤트를 사용한다. 자기 생성 피해를 다시 반전하지 않는다. 좀비 지휘/지원·HotPickaxe 화상은 확인된 피해에서만 처리한다.
- Titan의 주변 전체/자기 피해는 기존 능력 의미에 맞춰 명시적 예외를 사용한다. 다른 별도 피해는 공통 대상 정책을 따른다.

## 확정된 전투 상태

조화/통달 게이지, 조화 강화 소모와 퀘스트, 적응형 방어 상태 및 방어 올인 추가 무적 시간은 취소되지 않은 양의 finalDamage를 MONITOR에서 확인한 뒤 기록한다. 조화 강화는 이벤트 동안 소유자별 예약으로 중첩 소모를 막고, 이후 취소되면 게이지를 보존한다. 통달 준비 동작의 false 상태에서 강화 피해가 미리 적용되던 경로를 제거했다. 자체 검격 피해는 또 다른 화살/게이지 발동을 만들지 않는다.

취소 가능한 일반 능력 이벤트는 ignoreCancelled를 적용했다. PlayerInteractEvent의 예측 취소와 커스텀 입력은 별도 조건을 사용하며, 실제 손/보호 플러그인 조합 검증은 최종 서버 시험으로 남긴다. MONITOR는 기본 이벤트의 피해/취소 상태를 바꾸지 않는다.

## 연쇄 번개

확정 finalDamage에 설정 비율을 곱하고 확률을 통과한 경우에만 4틱 뒤 다음 대상을 공격한다. 기본 maximumChainHops=16, 유효 범위 1..256. 동일 연쇄의 시작점과 이미 예약한 대상은 다시 공격하지 않는다. 확률·비율이 1이어도 무한 재발동하지 않는다. 각 예약/실행 시 유효한 같은 월드의 적인지 확인하고, 소유자/능력이 다른 context나 다른 종류의 별도 능력 피해에서는 새 연쇄를 만들지 않는다.

## 대상과 메뉴

EnemySettings/EnemyPolicy를 메뉴·실제 대상 판정에서 함께 사용한다. 기본 PLAYER=1, 나머지=2이며 기존 명시적 0은 유지한다. 자기 자신/다른 월드 대상은 일반 대상에서 제외한다. 길들인 몹은 실제 owner UUID/이름으로 판단해 오프라인 소유자도 온라인 소유자와 같은 정책을 적용한다. 자기 주인은 UUID로 판별하고 팀은 공격자의 scoreboard에 있는 entry team을 사용한다.

적 범위 메뉴는 9칸 MenuHolder와 category/현재 mode action을 사용한다. 순수 좌/우 클릭만 변경하고 shift/drag/hotbar 등 이동은 공통 메뉴 guard로 차단한다. 오래된 선택값은 다시 표시하며 저장 실패를 알리고 다음 틱에만 화면을 연다. permission limpi.ability.enemy는 기본 true다.

엔더맨을 해치지 않고 드래곤을 처치하는 퀘스트는 해당 레일건에서 실제 양의 엔더맨 피해가 확인되면 제외한다. 언데드 협공은 바라보는 ray target 대신 Mob.target을 확인한다. 기존 퀘스트 완료/보상 원장을 그대로 사용한다.

## 검증 경계

**255개** 테스트 통과, test build 성공. 새 18개 정책 시험은 context 중첩/예외/스레드 분리, 연쇄 방문/상한/수치, 고정 보너스/흡수, 게이지 취소/소모/상한, 오프라인 소유 정책과 메뉴 공통 설정/순환/손상 값 보존이다. 기존 SQLite 거래/복구 시험도 통과했다.

Paper 실제 이벤트 순서·보호 플러그인·shield/흡수/토템·DamageSource 귀속/특수 처치 및 메뉴 화면을 실행한 결과는 아니다. 작성 당시 남았던 설정 검증·소매치기 자산·potion 소유·명령·주식 원장·운영 도구/CI는 이후 단계에서 연결했다([설정](ABILITY_SETTINGS.md), [물품](ABILITY_ITEM_TRANSACTIONS.md), [포션](ABILITY_POTIONS.md), [명령](COMMANDS.md), [주식](STOCK_TRANSACTIONS.md), [저장소·복구](STORAGE_AND_RECOVERY.md)). 서버 검증은 남아 있다.

## 리뷰 반영 (2026-10-10)

- WebShooter 충돌 피해가 엔더 드래곤 부위(Damageable이지만 LivingEntity 아님)에서 ClassCastException으로 작업을 끝내던 문제를 수정했다.
- GatheringStorm: 삼지창 적중(폭풍 생성·쿨다운 시작)이 집전 번개보다 먼저 호출되므로 기존 조건으로는 vanilla 번개가 취소되지 않았다. 같은 틱에 폭풍을 만든 경우 번개를 취소하고, 이전 폭풍의 쿨다운 중일 때만 vanilla 번개를 유지한다.
- EntityThrower: Paper가 드래곤 클릭을 부위로 전달하므로 부위를 드래곤 본체로 바꿔 드래곤 매달리기 분기가 동작하게 했다.
- ShieldStrike/GatheringStorm: 피해 처리 중 시전자가 동기적으로 사망해 상태가 해제되면 `!!` 대신 안전하게 중단한다.
- LetsBoom: 사후 vanilla 폭발이 대상 정책을 무시하고 아군·길들인 동물·본인 사망 드롭까지 파괴했다. 시전자가 죽어 있어 AbilityDamage.hit을 쓸 수 없으므로 폭발 호출 동안 적 LivingEntity가 아닌 피해와 걸이 엔티티 파괴를 취소한다. 지형 비파괴는 기존대로다.
- PullingBow: 적이 아닌 대상에게도 끌어당김을 적용하던 것을 적 대상·MONITOR(취소되지 않은 적중)로 제한했다.
- OverBalance/JudgementRay: 보호/개체 제한 플러그인이 레일 엔티티 생성을 취소하면 단발 능력이 효과 없이 소모되던 것을 막았다(생성 실패 시 소모 전 중단).
- LittleEat: 케이크·포화 효과 같은 비소비 허기 증가에도 2를 빼던 것을 수정했다. 감소는 2배, 증가는 가득 채움. 섭취 처리는 MONITOR·ignoreCancelled.
- HeavyEnchant: 모루 결과에 입력 인챈트를 순서대로 덮어써 더 높은 결과 레벨이 낮아지던 것을 최고 레벨 병합으로 고쳤다. 책의 stored enchant도 반영한다.
- EmeraldFix: 결과 슬롯(미리보기)에 수리하며 에메랄드만 소모되던 것을 막았다.
- 리뷰에서 피해 재귀(DamageContext/ChainBudget), 작업/속성 소유, 임시 엔티티 non-persistent, NaN 정규화, 설정 타입, 시전자 피해(Titan 의도) 경로는 결함이 없었다. 위 변경은 Paper 실제 이벤트 순서로 검증하지 않았다.

