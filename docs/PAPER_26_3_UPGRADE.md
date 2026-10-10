# Paper 26.3 전환 기록

작업일: 2026-10-10. 범위: Paper 대상/빌드 환경 전환, 필요한 컴파일 호환 수정, 정비 계획 문서화. 경제·능력·GUI 전체 리팩터링과 데이터 이전은 포함하지 않는다.

이 문서는 최초 Paper 전환 당시의 기록이다. 이후 사용자가 전체 정비 구현을 승인했으며, 현재 변경·테스트·남은 작업은 [구현 기록](IMPLEMENTATION_LOG.md)에서 관리한다. 아래 NO-SOURCE 등의 설명은 전환 당시 결과이며 현재는 자동 테스트가 추가되어 있다.

## 대상 버전

| 항목 | 이전 | 전환 대상 |
| --- | --- | --- |
| Paper API | 1.21.7-R0.1-SNAPSHOT | 26.3.build.169-beta |
| plugin.yml api-version | 1.20 | 26.3 |
| Java toolchain / JVM target | 21 | 25 |
| Gradle wrapper | 8.5 | 9.3.0 |
| Kotlin plugin / stdlib / reflect | 1.9.23 / 혼합 선언 | 2.3.21 |
| Shadow | com.github.johnrengelman.shadow 8.1.1 | com.gradleup.shadow 9.2.2 |
| Foojay toolchain resolver | 0.5.0 | 1.0.0 |
| GuardianBeam | 2.4.3 | 2.4.8 |

조사 시점 Paper 26.3 최신 빌드는 169 BETA이며 STABLE 빌드는 확인되지 않았다. 재현 가능한 빌드를 위해 `26.3.build.+` 대신 구체 버전을 고정했다. Paper 26.3은 Java 25가 필요하다. Gradle은 Java 25 toolchain을 지원하는 버전으로, Kotlin은 Gradle 9.3.0과 호환되는 버전으로 함께 변경했다.

GuardianBeam 2.4.3은 26.x 이전 라이브러리이다. 최신 릴리스 2.4.8에는 26.1/26.2 버전 대응과 패킷 엔티티 좌표 수정이 들어 있다. 릴리스 설명에 26.3 지원 보장은 없으므로 `WebShooter` 레이저 동작은 별도 서버 시험이 필요하다.

## 빌드와 산출물

```powershell
.\gradlew.bat clean build --console=plain
```

- 배포용: `build/libs/LIMPI-1.0.0.jar` — Kotlin 및 GuardianBeam 런타임 포함.
- `*-plain.jar`는 의존성을 포함하지 않는 중간 산출물이며 서버 배포용이 아니다.
- 기존 `D:\BungeeCord\papernonproxytestserver\plugins` 자동 출력은 제거했다. 서버로 복사/배포하는 작업은 빌드와 분리한다.
- Gradle 실행에는 Java 17 이상이 필요하다. Java 25가 없으면 Foojay가 Gradle toolchain 캐시에 내려받는다. 이번 확인에서는 기존 Java 21로 Gradle을 실행하고 자동 설치된 Temurin 25.0.4.1로 컴파일했다. 전역 JAVA_HOME/PATH는 변경하지 않았다. 서버 자체 실행에는 별도로 Java 25를 사용해야 한다.
- 플러그인은 Java 실행형 프로그램이 아니므로 불필요한 `Main-Class` manifest를 제거했다.
- Gradle 배포 SHA-256을 wrapper 설정에 고정하고 9.3.0의 scripts/JAR로 wrapper 파일도 갱신했다.

## 호환 수정

- `ZombieKing.kt`: Paper 26.3의 helmet getter가 비-null ItemStack을 반환하므로 `helmet == null` 대신 `helmet.isEmpty`로 빈 장비를 검사한다. nullable setter와 getter의 타입 차이로 Kotlin 합성 프로퍼티 대입이 실패하여 장비 제거는 `setHelmet(null)`을 명시적으로 호출한다. 낮에 헬멧 내구도를 소모하고 파괴하는 기존 동작 의도는 유지한다.
- `build.gradle`: Gradle 9에서 deprecated된 `filteringCharset 'UTF-8'`를 명시적 프로퍼티 대입으로 변경했다.
- 전체 구조 개편은 수행하지 않았다. `ZombieKing`의 기존 `break`, 기타 능력 수명/속성 복구 문제 등은 [정비 계획](MAINTENANCE_PLAN.md)에 미해결로 남아 있다.

## 검증 상태

- [x] 공식 Paper 빌드 목록과 Maven에서 `26.3.build.169-beta` 존재 확인.
- [x] 빌드 설정, api-version, Java 목표 버전, 도구 버전 변경.
- [x] 전체 정비 계획과 후속 작업 지침 작성.
- [x] Java 25 toolchain으로 `clean build --console=plain --warning-mode all` 성공(exit code 0).
- [x] Gradle 9.3.0 wrapper 갱신 및 공식 checksum 확인.
- [x] 산출물 plugin.yml의 version=1.0.0 / api-version=26.3, LIMPI.class major version=69(Java 25) 확인.
- [x] Kotlin stdlib/reflect 2.3.21로 의존성 해결 확인. JAR에 Kotlin 및 GuardianBeam 2.4.8 포함, Paper/Bukkit API 미포함 확인.
- [x] `git diff --check` 통과.
- [ ] 격리된 Paper 26.3 서버에서 enable/disable 확인.
- [ ] 실제 접속 후 명령/경제 GUI/뽑기/능력 및 WebShooter 레이저 확인.

배포용 JAR 크기는 6,032,228 bytes이다. wrapper JAR SHA-256은 `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`으로 공식 배포 값과 일치한다.

자동 테스트 소스가 없으므로 Gradle `test`는 NO-SOURCE였다. 컴파일 경고 3개(`Dodge`/`ShieldStrike`의 deprecated attribute defaultValue, `AbilityEnemySelect`의 불필요한 else)가 남아 있으며, 이번 전환에서 동작을 변경할 근거가 없어 별도 정비 대상으로 남겼다(이후 정비에서 세 경고 모두 제거, 현재 Kotlin 컴파일 경고 0개). Gradle 설정의 deprecation 경고는 수정 후 발생하지 않았다.

컴파일과 패키징 확인은 실제 서버 플레이 검증을 대신하지 않는다. 서버 실행/플러그인 배포는 하지 않았으며, 기존 데이터 파일과 외부 서버 설치 경로는 수정하지 않았다.

## 남은 서버 확인 순서

1. Java 25의 격리된 Paper 26.3 build 169 서버에 배포용 JAR만 설치한다.
2. 기본 설정(경제/능력 활성화)에서 enable, 능력 설정 생성/등록, 주식 반복 작업, disable 로그의 예외 유무를 확인한다.
3. 기존 프로필 복사본으로 명령어/상점/주식/마켓/능력 선택/뽑기를 확인한다. 위 정비 계획의 기존 결함과 이번 버전 변경 문제를 구분한다.
4. `WebShooter`의 벽/엔티티 적중, 레이저 생성/이동/종료를 직접 확인한다. GuardianBeam의 NMS reflection은 컴파일만으로 검증되지 않는다.
5. `ZombieKing`의 빈 헬멧/정상 헬멧/파괴 직전 헬멧과 낮/밤 동작을 확인한다.

## 공식 근거

- [Paper 프로젝트 설정과 새 API 버전 형식](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper 26.3 릴리스 안내](https://papermc.io/news/26-3/)
- [Paper 26.3 빌드 목록](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds)
- [Paper API Maven metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)
- [Gradle Java 호환성](https://docs.gradle.org/current/userguide/compatibility.html)
- [Kotlin Gradle 호환성](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Shadow 9.2.2](https://github.com/GradleUp/shadow/releases/tag/9.2.2)
- [Foojay resolver](https://github.com/gradle/foojay-toolchains)
- [GuardianBeam 2.4.8 변경 내역](https://github.com/SkytAsul/GuardianBeam/releases/tag/2.4.8)
