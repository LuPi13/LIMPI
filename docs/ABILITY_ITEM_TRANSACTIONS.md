# 연금술·소매치기 물품 처리

2026-10-10. 플레이어 인벤토리는 SQLite와 같은 트랜잭션으로 저장되지 않는다. 두 능력은 공통 RemovalJournal의 frozen before/after → 실제 playerdata checkpoint → 결과 권리/영수증 commit → 지급 흐름을 사용한다. 불확실한 물리 저장은 REVIEW와 오프라인 관리자 판단으로 처리한다. [공통 회수](SHOP_AND_SALE_RECOVERY.md)를 따른다.

## 연금술

- 오른손/왼손의 실제 구리 묶음과 수량을 확인한다. 선행 미수령 복구 뒤 다시 검사하고 10개만 회수한다. DENY 입력은 소비하지 않는다.
- 검증된 가중치로 결과를 한 번 선택하고 결과 종류와 실제 ItemGrant를 회수 준비에 고정한다. 재시작·복구·관리자 removed 판단에서 재추첨하지 않는다.
- 구리의 물리 저장 확인 뒤 같은 DB commit에서 광물 1개의 미수령 권리를 생성한다. 월드에 직접 드롭하지 않는다. 공간이 없으면 /financial claim으로 나중에 받는다.
- creative도 기존 연금술과 같이 10개를 소비한다. 능력/원본 물품이 선행 복구 중 바뀌면 실행을 거부한다.

## 소매치기

- 낚싯대 클릭/투사체 map 대신 실제 CAUGHT_ENTITY 이벤트를 사용한다. MONITOR에서 다음 틱을 예약하고 모든 리스너가 끝난 뒤 최종 취소 상태를 다시 확인한다. [Paper PlayerFishEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerFishEvent.html).
- 다음 틱에 현재 능력·대상 유효성·적/월드·거리·blocked 상태를 다시 검사한다. 플레이어 Creative/Spectator와 PvP 비활성 월드는 제외한다. PvP는 [Paper GameRuleKeys.PVP](https://jd.papermc.io/paper/26.3/io/papermc/paper/registry/keys/GameRuleKeys.html) 레지스트리로 읽는다.
- 능력 아이템·제한 슬롯·들고 있는 엔티티 placeholder는 훔치지 않는다. 실제 선택된 슬롯의 묶음 전체를 정확히 회수하고 NBT/PDC를 보존한다. 동일한 물품의 다른 슬롯을 제거하지 않는다.
- 플레이어 피해자의 회수 checkpoint와 공격자의 미수령 권리를 연결한다. 상대가 나가거나 공간이 없어도 권리는 보존된다. hook/피해자/공격자 UUID에 따른 거래 ID와 원장은 중복 소비/지급을 막는다.
- 플레이어 회수 이미지 41칸은 storage 0..35, armor 36..39, offhand 40이다. 기존 회수/전환의 37칸은 storage 0..35 + offhand 36이며 그대로 유지한다. 일반 지급은 storage 36칸만 사용한다.
- NPC 물품은 NPC와 드롭 아이템 모두 월드 상태로 유지한다. 회수 후 드롭 실패/취소 시 원본을 복원하고 생성된 드롭을 제거한다. NPC/여러 chunk 저장은 DB나 플레이어 checkpoint의 보장을 받지 않는다. 강제 프로세스 중단과 외부 플러그인이 NPC 슬롯을 변경하는 상황은 실제 서버 시험에서 확인할 경계다.

## 검증

전체 282개 시험과 test build가 통과했다. 새 13개 시험은 가중치 경계, 양손 비용, 갑옷/오프핸드 회수, recipient 권리, 반복/동일 ID 충돌, 물리 저장 직후 실패·재시작, 만석, 잘못된 소모/대상, 오프라인 removed/retained 판정을 확인한다. 실제 Paper 이벤트 취소·인벤토리 저장·NPC 드롭은 미검증이다.
