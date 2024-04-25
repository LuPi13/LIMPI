# LIMPI: LuPi13's Integrated Minecraft Plug-In
### 주인장이 객체지향, Kotlin, Git을 연습하기 위해 막무가내로 시작한 프로젝트

#### 라고 써뒀지만 나중가면 스파게티지향, Java식 코드, GitHub에 직접 드래그앤드롭으로 떡칠될 예정
###### 초기 개발중에 엄청난 문제점을 찾았다. Java가 아닌 Kotlin으로 개발하니까, 플러그인에 Kotlin Runtime도 포함되어 패키징되는데, 용량이 엄청 불어남. 기능 잔뜩 들어간 월드에딧이 6MB인데, 이자식 기능 넣지도 않았는데 5MB를 바라보고 있다. 용량이 크다고 렉도 늘어나는 건지는 좀 더 봐야 알겠는데 이딴 식으로 용량 뻥튀기 되면 실사용 용도로는 못 쓸지도

- - -

## 1. Download
- 오른쪽 Release 항목을 눌러 원하는 버전을 다운로드 하거나, [여기(아직 안만듬)](https://github.com/LuPi13/LIMPI)를 눌러 최신 버전을 다운 받을 수 있게 만들겠읍니다

- - -

## 2. Changed log
- 0.0.2: 플레이어 별 데이터 파일 입/출력(LIMPI\playerData\\{uuid}.yml), 계좌 시스템 및 수표 발행 기능 추가, ~~실행 시 쌈@뽕한 아스키 아트 출력~~
- 0.0.1: gradle wrapper, Kotlin, git initiate (FIRST COMMIT)

- - -

## 3. Schedule
- 1.0.0까지 목표
    + 서버 내 가상 화폐 단위 및 거래
    + 유사 초능력 이것저것
    + ~~강력한 어드민 전용 무기~~


- 이후 언젠가 할 목표
    + 서버 폭파 방지 기능
    + 어떤 후레자식이 서버에 몹쓸 짓을 했는 지 다 볼 수 있는 logger
