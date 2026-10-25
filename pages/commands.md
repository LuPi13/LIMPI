# 명령어

모든 플레이어가 기본으로 쓸 수 있는 명령어를 한곳에 모았습니다. 서버 관리자가 권한을 바꿨다면 일부 명령어가 보이지 않을 수 있습니다. 자세한 설명은 각 시스템 페이지에 있습니다.

| 시스템 | 게임 안 도움말 | 다루는 것 |
| --- | --- | --- |
| [능력](ability.md) | `/ability help` (`/ab help`) | 능력, 뽑기, 선택권, 도감, 천장·픽업, 퀘스트, 적 범위 |
| [금융](financial.md) | `/financial help` (`/fi help`, `/fc help`) | 계좌, 송금, 수표, 판매·상점, 장터, 미수령함, 주식 |
| [상자](chest.md) | `/chest help` (`/ch help`) | 돈으로 사는 개인 상자, 자동 저장, 엔더화 |
| [사유지](estate.md) | `/estate help` (`/es help`) | 땅 구매·보호·공유·매각 |
| [공지](notice.md) | `/notice help` (`/no help`, `/nt help`) | 서버 공지 보기, 지난 공지 다시 보기 |

## 빠른 참조

### 능력

| 명령어 | 하는 일 |
| --- | --- |
| `/ability me` | 내 능력 정보 |
| `/ability shop` | 뽑기권·선택권 구매 |
| `/ability select` | 선택권으로 능력 선택 |
| `/ability dictionary` (`dict`) | 능력 도감 |
| `/ability enemy` | 적 범위 설정 |
| `/ability rates` | 뽑기 확률 규칙 |
| `/ability ceiling` (`pity`) | 내 천장 현황 |
| `/ability pickup` | 픽업 능력 확인 |
| `/ability quest` | 퀘스트 진행도 |
| `/ability result <거래 UUID>` | 뽑기·선택 결과 다시 보기 |

### 금융

| 명령어 | 하는 일 |
| --- | --- |
| `/financial me` | 내 잔액 |
| `/financial send <플레이어> <금액>` | 송금 |
| `/financial check <금액>` | 수표 발행 |
| `/financial check redeem` | 손에 든 수표 입금 |
| `/financial sell` | 판매창 열기 |
| `/financial sell price [아이템]` | 판매 가격 확인 |
| `/financial shop` | 서버 상점 |
| `/financial market` | 장터 열기 |
| `/financial market add <코드> <가격>` | 손에 든 아이템 등록 |
| `/financial market remove <코드>` | 내 매물 내리기 |
| `/financial stock` | 주식 거래창 |
| `/financial stock show` | 종목 정보 |
| `/financial stock holdings` | 내 보유 주식·손익 |
| `/financial claim` | 미수령 물품 받기 |

### 상자

| 명령어 | 하는 일 |
| --- | --- |
| `/chest` (`open`) | 내 상자 열기 |
| `/chest shop` | 상자 상점 |
| <code>/chest auto [on&#124;off]</code> | 자동 저장 켜기/끄기 |

### 사유지

| 명령어 | 하는 일 |
| --- | --- |
| `/estate select` | 구역 선택 모드(빈손 좌클릭·우클릭) |
| `/estate full` | Y축 전체 포함 전환 |
| `/estate accept <사유지이름>` | 구매 확정 |
| `/estate deny` | 구매 취소 |
| `/estate overlaps` | 겹친 부분 보기 |
| `/estate show [사유지이름]` | 구역 테두리 보기 |
| `/estate list` | 내 사유지 목록 |
| `/estate where` | 현재 위치 확인 |
| `/estate sell <사유지이름>` | 사유지 매각 |
| `/estate trust <사유지이름> <플레이어>` | 건축 권한 공유 |
| `/estate untrust <사유지이름> <플레이어>` | 건축 권한 회수 |

### 공지

| 명령어 | 하는 일 |
| --- | --- |
| `/notice show <날짜> [번호]` | 지난 공지 보기 |
| `/notice list` | 보관 중인 공지 목록 |
| `/notice unread` | 읽지 않은 공지 보기 |

### 기타

| 명령어 | 하는 일 |
| --- | --- |
| `/limpi help` | 모든 시스템의 도움말을 모아 봅니다. 줄을 누르면 해당 도움말이 나옵니다. |

게임 안 도움말 첫 줄의 **[웹 도움말]**을 누르면 그 시스템의 페이지가 바로 열립니다.
