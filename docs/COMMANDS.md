# 명령 정의·권한·운영

2026-10-10. CommandDefinitions의 route가 실행 선택·사용법·도움말·권한·콘솔 범위·리터럴/동적 탭 완성의 기준이다. 기능을 추가할 때 해당 정의와 기능 handler를 함께 연결한다. 루트 Financial/Ability는 입력 검증과 서비스 호출만 담당하며 static plugin 조회를 하지 않는다.

- 입력은 대소문자를 구분하지 않는 가장 긴 명령 경로로 해석한다. 인자 부족/초과는 사용법을 표시하고 알 수 없는 명령은 help를 안내한다. 허용되지 않은 동적 completion의 저장소를 조회하지 않는다.
- 도움말은 발신자가 실제 사용할 수 있는 명령만 표시한다. 플레이어는 책, 콘솔은 텍스트를 받는다. alias는 탭에 나오되 도움말은 한 번만 나온다. 미구현 reload를 노출하지 않는다.
- 계좌/수표/판매/상점/마켓/회수/전환은 기존 원장·복구 서비스를 호출한다. 주식·뽑기 정보는 별도 handler에 분리했다. 금융 콘솔 TODO/예외를 제거했다.
- 시스템을 꺼도 help와 필요한 저장 결과 조회/claim·수표 입금·관리자 복구는 사용할 수 있다. 새 거래/GUI는 비활성이다. ability executor도 항상 등록해 비활성 상태를 설명한다.
- ability set은 `limpi.ability.admin.set`으로 판단한다. 콘솔도 접속 대상 이름/UUID에 적용할 수 있고 `none`으로 제거한다. 선행 복구 후 전환 journal을 사용한다. me는 알 수 없는 기존 능력 ID를 지우거나 강제 참조하지 않는다.
- financial spy는 `limpi.financial.admin.spy`로 오프라인을 포함한 기존 계좌를 조회한다. stock 관리 권한은 `limpi.financial.admin.stock`이다. 일반 stock 권한은 `limpi.financial.stock`이고 클릭 시에도 재확인한다. 나머지 일반 정보 명령도 plugin.yml의 명시적 default true 노드로 제어한다. 관리자 노드는 default op이다.
- ability set 대상이 미해결 지급/회수/전환으로 차단되어 있으면 실행자에게 선행 복구가 필요하다고 알린다. 콘솔의 `check inspect`는 수표 UUID가 필요하다는 안내를 보인다. `sugar`/`sugar void`는 [설탕](SUGAR_CONSUMPTION.md#적용할-수-없는-결과) 복구 명령이다.
- pickup은 strict 정수 주기·checked 시각 산술과 예고 개수 1..32를 확인한다. 도움말과 pity의 추첨 규칙은 실제 GachaPolicy 설정에서 생성한다.

## 종목 관리

`/financial stock remove <코드|all>`은 **상장 중지**다. 과거의 플레이어 stock 전체 삭제 경로를 제거했다. 종목 정의/가격/알 수 없는 필드·플레이어 수량/원가/손익을 보존하고 listed=false를 한 번에 저장한다. 중지된 종목은 새 거래에서 거부한다.

`stock restore <코드|all>`은 보존한 정의/현재가와 기준가를 모두 검증해 재상장한다(손상된 기준가 하나가 전체 가격 갱신을 멈추지 않게 한다). `stock holdings`는 상장 중지 종목까지 자기 보유/원가/손익을 표시한다. 사용한 코드/이름을 새 종목에 재사용할 수 없다. `stock refix`는 선택된 가격 전부를 검증한 뒤 현재 가격을 기준가로 저장한다. 종목 변경 시각도 같은 stock.yml snapshot에 저장하며 실패하면 runtime 설정을 바꾸지 않는다. profile을 직접 삭제하는 관리 기능은 제공하지 않는다. 손상된 legacy 보유는 [주식 원장](STOCK_TRANSACTIONS.md#손상된-기존-보유의-관리자-복구)의 `stock repair`로 원장과 함께 복구한다. stock add 입력 오류는 이름/코드/material/가격별 메시지를 보인다.

전체 325개 시험과 test build가 통과했다. 새 15개 시험은 alias/최장 경로·권한/콘솔/비활성 help/tab·dynamic provider 접근 제한·잘못된 인자·packaged permission 선언·실제 console adapter·상장 중지/복구/재사용 금지·snapshot/typed 검증을 확인한다. 실제 Paper 도움말 책·클릭/권한 변경·콘솔 관리자 저장은 최종 서버 시험에서 확인한다.
