# 관리자 명령어

서버 관리자용 명령어입니다. 아래 권한은 기본적으로 **OP에게만** 주어집니다. 관리자 권한이 있으면 게임 안 도움말 책 첫 장에 이 페이지 링크도 함께 나옵니다.

일반 플레이어 명령어는 [명령어](commands.md) 페이지를 보세요.

## 능력

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/ability set <접속 플레이어> <능력 ID>` | `limpi.ability.admin.set` | 선택권 없이 대상에게 능력을 적용합니다. 능력 ID 대신 `none`을 쓰면 능력을 없앱니다. 콘솔에서도 쓸 수 있습니다. |
| `/ability dictionary` | `limpi.ability.admin.set` | 관리자는 도감에서 능력을 우클릭해 자신에게 바로 적용할 수 있습니다. |
| `/ability reload` | `limpi.ability.admin.reload` | `ability/<능력>.yml`을 모두 검사한 뒤 한꺼번에 다시 불러옵니다. 하나라도 잘못되면 아무것도 바뀌지 않습니다. |

대상에게 끝나지 않은 지급·회수·능력 전환 기록이 있으면 `/ability set`이 거부됩니다. 아래 [거래 복구](#recovery)를 먼저 처리하세요.

## 계좌

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
| `/financial stock remove <코드\|all>` | `limpi.financial.admin.stock` | 종목을 **상장 중지**합니다. 플레이어 보유 기록은 지워지지 않습니다. |
| `/financial stock restore <코드\|all>` | `limpi.financial.admin.stock` | 상장 중지한 종목을 다시 상장합니다. |
| `/financial stock refix <코드\|all>` | `limpi.financial.admin.stock` | 현재 가격을 새 기준 가격으로 저장합니다. 가격은 기준 가격 쪽으로 조금씩 돌아옵니다. |

한 번 쓴 종목 코드와 이름은 새 종목에 다시 쓸 수 없습니다.

## 장터·수표

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial market remove <코드>` | `limpi.financial.admin.market` | 다른 사람이 올린 매물도 내릴 수 있습니다. 아이템은 올린 사람의 미수령함으로 돌아갑니다. |
| `/financial check inspect [수표 ID]` | `limpi.financial.admin.check` | 손에 든 수표(콘솔은 수표 ID)의 발행 기록을 확인합니다. |

## 거래 복구 { #recovery }

돈·아이템 거래 도중 서버가 꺼지거나 오류가 나면, 기록이 **확인 대기** 상태로 남습니다. 대부분은 플레이어가 다시 접속할 때 자동으로 정리되고, 자동으로 판단할 수 없는 것만 관리자가 처리합니다.

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/financial review` | `limpi.financial.admin.delivery` | 모든 플레이어의 확인 대기 기록과, 각각 처리할 명령어 예시를 보여줍니다. **여기서 시작하세요.** |
| `/financial delivery <소유자 UUID> [지급 ID]` | `limpi.financial.admin.delivery` | 아이템 지급 기록을 봅니다. |
| `/financial delivery resolve <소유자 UUID> <지급 ID> delivered\|retry <근거>` | `limpi.financial.admin.delivery` | 지급 판단을 기록합니다. |
| `/financial removal <소유자 UUID> [회수 ID]` | `limpi.financial.admin.removal` | 판매·교환으로 인벤토리에서 아이템을 가져간 기록을 봅니다. |
| `/financial removal resolve <소유자 UUID> <회수 ID> removed\|retained <근거>` | `limpi.financial.admin.removal` | 회수 판단을 기록합니다. |
| `/financial transition <소유자 UUID> [전환 ID]` | `limpi.financial.admin.transition` | 능력 변경에 따른 인벤토리 전환 기록을 봅니다. |
| `/financial transition resolve <소유자 UUID> <전환 ID> applied\|retained <근거>` | `limpi.financial.admin.transition` | 전환 판단을 기록합니다. |
| `/financial market escrow <소유자 UUID>` | `limpi.financial.admin.market` | 중단된 장터 등록·회수 기록을 봅니다. |
| `/financial market resolve <소유자 UUID> <매물 ID> removed\|retained <근거>` | `limpi.financial.admin.market` | 장터 회수 판단을 기록합니다. |
| `/financial sugar <소유자 UUID>` | `limpi.financial.admin.sugar` | 적용 대기 중인 설탕 사용 결과를 봅니다. |
| `/financial sugar void <소유자 UUID> <결과 ID> <근거>` | `limpi.financial.admin.sugar` | 적용할 수 없는 설탕 결과를 환불 없이 종료합니다. |

!!! warning "resolve 전에 확인할 것"
    - `resolve`/`void`는 **소유자가 오프라인일 때만** 쓸 수 있습니다.
    - 서버에 저장된 그 플레이어의 인벤토리를 직접 확인한 뒤, 실제 상태에 맞게 고르세요.
        - `delivered` / `removed` / `applied`: 아이템이 이미 인벤토리에 반영됐음
        - `retry` / `retained`: 반영되지 않았음
    - `<근거>`에는 무엇을 확인했는지 적습니다. 기록에 남습니다.
    - 상태가 `APPLYING`인 기록은 아직 처리 중일 수 있으니 바로 판단하지 마세요.

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

일반 플레이어 명령어의 권한(`limpi.ability.me`, `limpi.financial.send` 등)은 모두 기본값이 `true`입니다. 권한 플러그인으로 빼앗으면 해당 명령어가 도움말과 탭 완성에서 사라집니다.

## 설정 파일

서버를 처음 켜면 `plugins/LIMPI` 폴더에 만들어집니다. 고친 뒤 서버를 다시 켜면 적용됩니다(능력 수치는 `/ability reload`로도 적용).

| 파일 | 바꿀 수 있는 것 |
| --- | --- |
| `config.yml` | 능력·금융 시스템 켜기/끄기, 첫 소지금과 첫 뽑기권 수, 뽑기 등급 확률, 뽑기권 가격, 픽업 주기, 주식 변동 폭, 자동 백업 주기, 게임 안 도움말의 웹 링크 주소(`HelpUrl`) 등 |
| `ability/<능력>.yml` | 능력별 세부 수치(피해량, 쿨타임, 범위, 지속 시간 등) |
| `financial/sellPrice.yml` | 아이템 판매 가격 |
| `financial/shopRatio.yml` | 상점 판매 아이템과 가격 비율 |
| `financial/stock.yml` | 주식 종목 |

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
