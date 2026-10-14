# 관리자 명령어

서버 관리자용 명령어입니다. 아래 권한은 기본적으로 **OP에게만** 주어집니다. 게임 안에서는 `/<명령어> help admin`(예: `/financial help admin`)으로 관리자 명령어를 따로 볼 수 있고, 그 첫 줄의 **[관리자 명령어]**를 누르면 이 페이지가 열립니다.

일반 플레이어 명령어는 [능력](ability.md), [금융](financial.md), [상자](chest.md), [공지](notice.md) 페이지를 보세요.

## 능력 { #ability }

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/ability set <접속 플레이어> <능력 ID>` | `limpi.ability.admin.set` | 선택권 없이 대상에게 능력을 적용합니다. 능력 ID 대신 `none`을 쓰면 능력을 없앱니다. 콘솔에서도 쓸 수 있습니다. |
| `/ability dictionary` | `limpi.ability.admin.set` | 관리자는 도감에서 능력을 좌클릭해 자신에게 바로 적용할 수 있습니다. 우클릭은 일반 플레이어와 같이 상세 정보를 봅니다. |
| `/ability reload` | `limpi.ability.admin.reload` | `ability/<능력>.yml`을 모두 검사한 뒤 한꺼번에 다시 불러옵니다. 하나라도 잘못되면 아무것도 바뀌지 않습니다. |

대상에게 끝나지 않은 지급·회수·능력 전환 기록이 있으면 `/ability set`이 거부됩니다. 아래 [거래 복구](#recovery)를 먼저 처리하세요.

## 계좌 { #financial }

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial spy <플레이어>` | `limpi.financial.admin.spy` | 오프라인 플레이어를 포함해 계좌 잔액을 봅니다. |
| `/financial set <플레이어> <금액>` | `limpi.financial.admin.set` | 잔액을 그 금액으로 정합니다. 손상된 잔액을 복구할 때도 씁니다. |
| `/financial add <플레이어> <금액>` | `limpi.financial.admin.add` | 잔액에 금액을 더합니다. 음수를 쓰면 뺍니다. |

잔액 변경은 모두 거래 기록(원장)에 남습니다.

## 상점·판매

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial sell set <아이템> <금액>` | `limpi.financial.admin.catalog` | 아이템의 판매 가격을 정합니다. |
| `/financial sell remove <아이템>` | `limpi.financial.admin.catalog` | 판매 품목에서 뺍니다. |
| `/financial shop set <아이템> <비율>` | `limpi.financial.admin.catalog` | 상점 구매 가격 비율을 정합니다. 구매 가격은 판매 가격 × 비율입니다. |
| `/financial shop remove <아이템>` | `limpi.financial.admin.catalog` | 상점 품목에서 뺍니다. |

## 주식

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial stock add <이름> <코드> <아이템> <가격>` | `limpi.financial.admin.stock` | 종목을 추가합니다. 가격은 기준 가격입니다. |
| <code>/financial stock remove &lt;코드&#124;all&gt;</code> | `limpi.financial.admin.stock` | 종목을 **상장 중지**합니다. 플레이어 보유 기록은 지워지지 않습니다. |
| <code>/financial stock restore &lt;코드&#124;all&gt;</code> | `limpi.financial.admin.stock` | 상장 중지한 종목을 다시 상장합니다. |
| <code>/financial stock refix &lt;코드&#124;all&gt;</code> | `limpi.financial.admin.stock` | 현재 가격을 새 기준 가격으로 저장합니다. 가격은 기준 가격 쪽으로 조금씩 돌아옵니다. |

한 번 쓴 종목 코드와 이름은 새 종목에 다시 쓸 수 없습니다.

## 장터·수표

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial market remove <코드>` | `limpi.financial.admin.market` | 다른 사람이 올린 매물도 내릴 수 있습니다. 아이템은 올린 사람의 미수령함으로 돌아갑니다. |
| `/financial check inspect [수표 ID]` | `limpi.financial.admin.check` | 손에 든 수표(콘솔은 수표 ID)의 발행 기록을 확인합니다. |

## 상자 { #chest }

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/chest inspect <플레이어>` | `limpi.chest.admin.inspect` | 상자 크기, 자동 저장·엔더화 여부, 들어 있는 아이템과 끝나지 않은 상자 창 기록을 봅니다. 오프라인 플레이어도 됩니다. |
| <code>/chest resolve &lt;소유자 UUID&gt; &lt;창 기록 ID&gt; before&#124;after &lt;근거&gt;</code> | `limpi.chest.admin.resolve` | 판단할 수 없게 된 상자 창 기록을 정리합니다. 아래 [거래 복구](#recovery) 참고. |

`config.yml`의 `EnableChestSystem`을 꺼도 이미 해금한 상자는 `/chest`로 열 수 있습니다. 구매, 자동 저장, 죽었을 때 떨어뜨리기는 멈춥니다. 상자 구매에는 금융 시스템도 켜져 있어야 합니다.

## 거래 복구 { #recovery }

돈·아이템 거래 도중 서버가 꺼지거나 오류가 나면, 기록이 **확인 대기** 상태로 남습니다. 대부분은 플레이어가 다시 접속할 때 자동으로 정리되고, 자동으로 판단할 수 없는 것만 관리자가 처리합니다.

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial review` | `limpi.financial.admin.delivery` | 모든 플레이어의 확인 대기 기록(상자 창 기록 포함)과, 각각 처리할 명령어 예시를 보여줍니다. **여기서 시작하세요.** |
| `/financial delivery <소유자 UUID> [지급 ID]` | `limpi.financial.admin.delivery` | 아이템 지급 기록을 봅니다. |
| <code>/financial delivery resolve &lt;소유자 UUID&gt; &lt;지급 ID&gt; delivered&#124;retry &lt;근거&gt;</code> | `limpi.financial.admin.delivery` | 지급 판단을 기록합니다. |
| `/financial removal <소유자 UUID> [회수 ID]` | `limpi.financial.admin.removal` | 판매·교환으로 인벤토리에서 아이템을 가져간 기록을 봅니다. |
| <code>/financial removal resolve &lt;소유자 UUID&gt; &lt;회수 ID&gt; removed&#124;retained &lt;근거&gt;</code> | `limpi.financial.admin.removal` | 회수 판단을 기록합니다. |
| `/financial transition <소유자 UUID> [전환 ID]` | `limpi.financial.admin.transition` | 능력 변경에 따른 인벤토리 전환 기록을 봅니다. |
| <code>/financial transition resolve &lt;소유자 UUID&gt; &lt;전환 ID&gt; applied&#124;retained &lt;근거&gt;</code> | `limpi.financial.admin.transition` | 전환 판단을 기록합니다. |
| `/financial market escrow <소유자 UUID>` | `limpi.financial.admin.market` | 중단된 장터 등록·회수 기록을 봅니다. |
| <code>/financial market resolve &lt;소유자 UUID&gt; &lt;매물 ID&gt; removed&#124;retained &lt;근거&gt;</code> | `limpi.financial.admin.market` | 장터 회수 판단을 기록합니다. |
| `/financial sugar <소유자 UUID>` | `limpi.financial.admin.sugar` | 적용 대기 중인 설탕 사용 결과를 봅니다. |
| `/financial sugar void <소유자 UUID> <결과 ID> <근거>` | `limpi.financial.admin.sugar` | 적용할 수 없는 설탕 결과를 환불 없이 종료합니다. |
| <code>/chest resolve &lt;소유자 UUID&gt; &lt;창 기록 ID&gt; before&#124;after &lt;근거&gt;</code> | `limpi.chest.admin.resolve` | 상자 창 기록을 정리합니다. `before`는 상자를 연 시점 내용으로, `after`는 창을 닫으며 기록된 내용으로 확정합니다(닫기 기록이 있을 때만). |

!!! warning "resolve 전에 확인할 것"
    - `resolve`/`void`는 **소유자가 오프라인일 때만** 쓸 수 있습니다.
    - 서버에 저장된 그 플레이어의 인벤토리를 직접 확인한 뒤, 실제 상태에 맞게 고르세요.
        - `delivered` / `removed` / `applied`: 아이템이 이미 인벤토리에 반영됐음
        - `retry` / `retained`: 반영되지 않았음
        - 상자의 `before` / `after`: 저장된 인벤토리가 상자를 열기 전 상태인지, 닫은 뒤 상태인지에 맞춰 고릅니다. 상자 창이 열려 있는 동안 서버 자동 저장이 끼어들었다면 둘 다 아닐 수 있으니, 아이템이 겹치거나 사라지지 않게 판단하세요.
    - `<근거>`에는 무엇을 확인했는지 적습니다. 기록에 남습니다.
    - 상태가 `APPLYING`인 기록은 아직 처리 중일 수 있으니 바로 판단하지 마세요.

## 공지 { #notice }

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/notice write <내용>` | `limpi.notice.admin.write` | 공지를 등록하고 접속 중인 플레이어에게 바로 보여 줍니다. 번호는 날짜마다 001부터 매깁니다. 콘솔에서도 됩니다. |
| `/notice delete <날짜> <번호>` | `limpi.notice.admin.delete` | 공지를 지웁니다. 지운 번호는 다시 쓰지 않습니다. |

`<내용>`은 일반 글자나 게임 안 JSON 텍스트 형식으로 씁니다.

- 일반 글자: `/notice write [공지] 오늘 밤 10시 점검`. `\n`을 쓰면 줄이 바뀝니다.
- JSON 텍스트: `{`나 `[{`로 시작하면 JSON으로 읽습니다. 예: `/notice write [{"text":"공지입니다\n","bold":true,"color":"gold"},{"text":"hello","italic":true}]`. `"bold":"true"`처럼 따옴표로 감싼 값도 됩니다. 형식이 틀리면 등록하지 않고 오류를 알려 줍니다.
- 게임 채팅창은 256자까지만 입력되므로 긴 JSON은 콘솔에서 등록하세요.
- `config.yml`의 `NoticeRetentionDays`(기본 30)보다 오래된 공지는 서버를 켤 때와 새 공지를 등록할 때 지워집니다. 0 이하면 지우지 않습니다.

복구 조회 명령은 `config.yml`에서 금융 시스템을 꺼도 쓸 수 있고, 콘솔에서도 쓸 수 있습니다.

## 권한 목록

| 권한 | 기본값 | 대상 |
| --- | --- | --- |
| `limpi.ability.admin.set` | OP | 능력 지정, 도감에서 바로 적용 |
| `limpi.ability.admin.reload` | OP | 능력 설정 다시 불러오기 |
| `limpi.financial.admin.spy` | OP | 잔액 조회 |
| `limpi.financial.admin.set` | OP | 잔액 설정 |
| `limpi.financial.admin.add` | OP | 잔액 조정 |
| `limpi.financial.admin.catalog` | OP | 상점·판매 가격 |
| `limpi.financial.admin.stock` | OP | 종목 관리 |
| `limpi.financial.admin.market` | OP | 장터 매물 강제 회수, 장터 복구 |
| `limpi.financial.admin.check` | OP | 수표 기록 확인 |
| `limpi.financial.admin.delivery` | OP | 지급 복구, `/financial review` |
| `limpi.financial.admin.removal` | OP | 회수 복구 |
| `limpi.financial.admin.transition` | OP | 능력 전환 복구 |
| `limpi.financial.admin.sugar` | OP | 설탕 결과 복구 |
| `limpi.chest.admin.inspect` | OP | 상자 조회 |
| `limpi.chest.admin.resolve` | OP | 상자 창 기록 복구 |
| `limpi.notice.admin.write` | OP | 공지 등록 |
| `limpi.notice.admin.delete` | OP | 공지 삭제 |

일반 플레이어 명령어의 권한(`limpi.ability.me`, `limpi.financial.send`, `limpi.chest.open`, `limpi.notice.show` 등)은 모두 기본값이 `true`입니다. 권한 플러그인으로 빼앗으면 해당 명령어가 도움말과 탭 완성에서 사라집니다.

## 설정 파일

서버를 처음 켜면 `plugins/LIMPI` 폴더에 만들어집니다. 고친 뒤 서버를 다시 켜면 적용됩니다(능력 수치는 `/ability reload`로도 적용).

| 파일 | 바꿀 수 있는 것 |
| --- | --- |
| `config.yml` | 능력·금융 시스템 켜기/끄기, 첫 소지금과 첫 뽑기권 수, 뽑기 등급 확률, 뽑기권 가격, 픽업 주기, 주식 변동 폭, 자동 백업 주기, 상자 시스템 켜기/끄기와 상자 상점 가격, 공지 보관 일수(`NoticeRetentionDays`), 게임 안 도움말의 웹 링크 주소(`HelpUrl`) 등 |
| `ability/<능력>.yml` | 능력별 세부 수치(피해량, 쿨타임, 범위, 지속 시간 등) |
| `financial/sellPrice.yml` | 아이템 판매 가격 |
| `financial/shopRatio.yml` | 상점 판매 아이템과 가격 비율 |
| `financial/stock.yml` | 주식 종목 |

### 플러그인을 업데이트하면

새 버전에서 `config.yml`에 설정이 추가됐다면, 서버를 켤 때 기존 `config.yml`에 그 설정만 설명 주석과 함께 원래 자리에 자동으로 들어갑니다.

- 이미 있는 설정은 새 버전에서 기본값이 바뀌었어도 그대로 둡니다. 직접 단 주석, 줄 간격, 순서도 바뀌지 않습니다.
- 새 버전에서 없어진 설정도 지우지 않습니다.
- 바꾸기 전 파일은 `config.yml.bak-<날짜-시각>`으로 남고, 서버 로그에 `Added new settings to config.yml: ...`로 추가한 설정이 나옵니다.
- `config.yml` 문법이 깨져 있거나 들여쓰기가 달라 안전하게 넣을 수 없으면 파일을 건드리지 않고 경고만 남깁니다. 이때도 빠진 설정은 기본값으로 동작합니다.
- `ability/<능력>.yml`도 서버를 켤 때 빠진 수치를 기본값으로 채웁니다.

## 데이터와 백업

- 모든 데이터는 `plugins/LIMPI/limpi.sqlite3` 하나에 저장됩니다.
- 서버를 켤 때와 `BackupIntervalMinutes`(기본 60분)마다 `plugins/LIMPI/backups`에 자동 백업되며, 최근 `BackupKeep`(기본 24)개를 남깁니다.
- 서버를 끈 상태에서 DB를 점검하거나 백업에서 복원본을 만들 때는 플러그인과 함께 빌드되는 `LIMPI-operations-x.x.x.jar`를 씁니다. Paper 서버 없이 Java 25로 실행합니다.

```
java -jar LIMPI-operations-x.x.x.jar help
java -jar LIMPI-operations-x.x.x.jar check limpi.sqlite3
java -jar LIMPI-operations-x.x.x.jar backup limpi.sqlite3 새백업.sqlite3
```

결과물은 항상 새 파일로 만들어지고, 운영 중인 DB를 덮어쓰지 않습니다.
