# 능력 전환과 제한 슬롯 복구

2026-10-10. AB-02. 능력 변경·해제 및 분실된 active item 복원은 AbilityTransitions 원장과 PaperDeliveryInventory의 저장 checkpoint를 사용한다.

## 순서

1. 기존/새 능력 코드, 소유한 active item payload/슬롯, 새 active item, storage 36칸+offhand 이미지를 동결한다. 이전 슬롯의 일반 물품은 제거하지 않는다. 새 제한 슬롯의 일반 물품은 저장 공간으로 전체 이동을 계획하며 공간 부족이면 예약/차감 전에 거부한다.
2. APPLYING 전환과 요청 fingerprint를 저장한다. 프로필의 능력은 아직 변경하지 않는다. 마켓 회수/공통 회수/전환은 소유자별 물리 예약을 공유하고 동시에 준비할 수 없다. 이미 진행 중인 지급 checkpoint도 먼저 마쳐야 한다.
3. Paper의 설정된 playerdata 저장 경로를 probe로 확인하고 전체 슬롯을 적용한다. 독립 checkpoint 저장/파일 확인이 끝난 뒤에만 DB 정산한다.
4. 프로필의 기존 능력을 재확인하고 새 능력·전환 DONE·대기 삭제·영수증을 동일 DB transaction에서 확정한다. 잔액·해금·퀘스트·알 수 없는 기존 필드는 보존한다. 이후에만 이전 능력 작업/상태/속성을 정리한다.

능력이 같으면 일반 적용은 no-op이다. active item 복원은 같은 코드에서도 필요한 슬롯 변경만 동일 원장으로 처리한다. 공간이 부족하면 일반 물품을 버리거나 덮어쓰지 않는다. 선택권/강제 TROLL의 기존 적용 대기와 ack는 전환 성공 뒤에만 완료된다.

## 중단과 판단

접속 복구는 다른 회수·미수령 지급보다 전환을 먼저 확인한다. 저장된 checkpoint와 슬롯이 확정 이후 이미지라면 물리 변경 없이 프로필을 확정한다. 이전 이미지이고 해당 checkpoint가 없다면 동결 계획을 적용한다. 그 외 조합은 REVIEW로 남기고 접속을 차단한다. 불확실한 물리 변경은 live inventory rollback으로 정상 처리하지 않는다.

관리자 권한 `limpi.financial.admin.transition`은 기본 op이며 금융 기능 비활성화 상태에서도 복구 명령을 허용한다.

- `/financial transition <owner UUID> [전환 UUID]`: 대기/완료 기록, 코드와 checkpoint 조회.
- `/financial transition resolve <owner UUID> <전환 UUID> applied|retained <확인 근거>`: 소유자가 오프라인일 때만 사용한다. 실제 저장 슬롯/원본·이후 이미지와 기록을 비교한 뒤 판단한다.
- applied는 물리 전환이 저장됐다는 판단으로 새 프로필을 확정한다. retained는 이전 슬롯이 보존됐다는 판단으로 프로필을 바꾸지 않고 예약을 닫는다. 두 경우 모두 이전 증거·판단·근거·감사 UUID를 남긴다. 명령 자체가 오프라인 슬롯을 다시 쓰거나 물품을 발행하지 않는다.

## 검증 경계

**237개** 테스트 통과, test build 성공. 새 9개 실제 SQLite 시험은 저장 전 프로필 불변, 일반 물품/알 수 없는 필드 보존, replay/conflict, 저장 전·후 restart, commit 실패, REVIEW, 공간 부족/오래된 프로필, 같은 능력 복원, 관리자 판단과 공통 회수 예약 충돌이다.

실제 Paper saveData와 playerdata 확인, 접속 복구/제한 슬롯 이벤트/선택권·TROLL 연동은 실제 서버에서 아직 시험하지 않았다. 원장은 DB와 물리 인벤토리를 단일 transaction으로 만들지 않는다. 저장 확인이 불가능하면 중단·검토 상태를 보존한다.
