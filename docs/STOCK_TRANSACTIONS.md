# 주식 거래 원장

2026-10-10. StockTradingService는 거래 UUID·owner·종목·표시 가격·수량·매수/매도를 fingerprint로 사용한다. canonical SQLite profile에서 잔액/보유를 읽고 같은 commit에서 새 잔액·수량·취득 원가·실현 손익과 영수증을 저장한다. cache의 오래된 보유 값으로 정산하지 않는다. commit 뒤 profile cache를 무효화한다.

- 신규 거래는 typed 현재 종목/가격과 표시 가격의 일치를 transaction 안에서 확인한다. 종목 삭제/가격 변경이면 자산을 변경하지 않는다.
- 같은 UUID/입력은 원래 영수증을 반환한다. 종목 가격이 바뀌거나 삭제되고 서버가 재시작해도 재정산하지 않는다. 다른 입력으로 UUID를 재사용하면 거부한다. GUI는 성공한 거래 UUID를 표시한다.
- 잔액/금액/수량은 비음수 checked Long이며 가격/수량은 양수다. 숫자 문자열·소수·boolean·잘못된 portfolio/holding section을 0으로 대체하지 않는다. 사용자 알 수 없는 필드는 보존한다.
- 취득 원가는 매수 시 금액을 더하고 매도 시 기존 원가를 수량 비율로 내림 배분한다. 남은 평균가는 남은 원가/수량이다. 전량 매도는 수량/원가를 0으로 만들며 평균가도 0이다.
- 실현 손익은 매도 대금 − 배분한 취득 원가를 signed Long으로 누적한다. 매수는 기존 손익을 유지한다. 누적 overflow도 거래 전체를 실패시킨다. 기존 데이터에 손익이 없으면 이번 변경 이후부터 기록하며 과거 손익을 추정하지 않는다.
- commit 실패면 잔액·보유·원장이 모두 이전 상태이고 같은 UUID를 재시도할 수 있다. 운영 종목 가격 YAML과 SQLite 계좌를 하나의 저장소라고 가정하지 않는다. 현재 가격 callback과 Bukkit 설정 접근은 메인 스레드 경로에서 수행한다.

## 손상된 기존 보유의 관리자 복구

과거 매도 공식은 음수 `averagePrice`를 저장할 수 있었다. 이런 보유는 0으로 바꾸지 않고 거래를 거부한다. 관리자가 기존 거래 기록/백업으로 올바른 값을 확인한 뒤 `/financial stock repair <이름|UUID> <코드> <수량> <취득 원가> [거래 UUID]`(`limpi.financial.admin.stock.repair`, 콘솔 가능)로 복구한다. 수량/원가/평균가를 같은 commit에 쓰고 이전 raw count/costBasis/averagePrice와 actor를 원장 영수증에 남긴다. 잔액·실현 손익·다른 필드는 바꾸지 않는다. 같은 UUID 재시도는 replay이고 다른 값의 재사용은 거부한다. 수량 0이면 원가도 0이어야 한다. 실현 손익 값이 손상된 경우는 이 명령으로 고치지 않는다.

전체 301개 시험과 test build가 통과했다. 기존 주식 8개 시험을 실제 SQLite 경로로 이전하고 5개를 추가했다. 잔액/원가/손익, legacy 정수 보존, 금액/손익 overflow, commit 실패와 cache 보존, stale cache, 재시작/quote 삭제 후 재시도, UUID 충돌, typed 데이터 거부를 확인한다. 실제 GUI 클릭/주식 스케줄러·서버 성능은 최종 서버 시험에서 확인한다.
