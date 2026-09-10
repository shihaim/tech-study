# tech-study

기술 개념을 Kotlin 코드로 실습하고 정리하는 학습 레포입니다.
학습 주제별로 Gradle 모듈을 분리합니다.

문서를 찾으려면 [전체 문서 지도](docs/README.md), Kotlin 표기가 낯설다면 [Kotlin 빠른 찾기](docs/kotlin/README.md)에서 시작하세요.

## 학습 목차

| 모듈 | 내용 |
| --- | --- |
| [01-concurrency-basics](01-concurrency-basics/README.md) | Runnable, Callable, Future, CompletableFuture와 동시성 기초 |
| [02-executor-threadpool](02-executor-threadpool/README.md) | Executor, 작업 배치, 포화 정책, 종료와 자원 병목 |

## 실행 방법

Gradle 실행에도 JDK 25가 필요합니다. `JAVA_HOME`을 설치된 JDK 25 경로로 설정해 주세요.
Gradle Wrapper를 사용하므로 Gradle을 별도로 설치할 필요는 없습니다.

Windows PowerShell에서 전체 빌드:

```powershell
.\gradlew.bat build
```

동시성 예제 전체 실행:

```powershell
.\gradlew.bat :01-concurrency-basics:run
```

특정 예제만 실행:

```powershell
.\gradlew.bat :01-concurrency-basics:run --args="03 07"
```

macOS/Linux에서는 `.\gradlew.bat` 대신 `./gradlew`를 사용합니다.
예제별 설명은 각 모듈의 README와 `docs/`에서 확인할 수 있습니다.

## 레포 구조

- `01-concurrency-basics/`: 동시성 학습 코드와 문서
- `02-executor-threadpool/`: 스레드 풀 학습 코드와 문서
- `study-support/`: 학습 모듈이 함께 사용하는 로깅·스레드 생성·대기·종료 유틸리티
- `docs/`: Kotlin 공통 지식, 문서 관리 규칙, 모듈 구조
- `buildSrc/`: Kotlin JVM, JDK 25, 콘솔 UTF-8, 테스트 실행 공통 설정
- `gradle/libs.versions.toml`: Kotlin 버전과 빌드 플러그인 의존성
- `gradle/wrapper/`, `gradlew`, `gradlew.bat`: Gradle Wrapper
- `settings.gradle.kts`: 학습 모듈 등록

새 학습 주제를 추가할 때는 모듈을 만들고 `settings.gradle.kts`에 등록합니다.
공통 빌드 설정은 `buildsrc.convention.kotlin-jvm` 플러그인을 적용해 재사용합니다.
테스트를 작성하는 모듈에는 `testImplementation(kotlin("test"))` 의존성을 추가합니다.

## 공통 코드 사용

`01-concurrency-basics`와 `02-executor-threadpool`은 각각 `study-support`에 의존합니다.
학습 모듈끼리는 의존하지 않으며, `study-support`도 학습 모듈을 참조하지 않습니다.
`buildSrc`는 빌드 설정을 공유하고 `study-support`는 실행 시 사용하는 코드를 공유합니다.

책임 구분과 새 학습 모듈 추가 절차는 [모듈 구조](docs/architecture.md)에서 확인합니다.

새 학습 모듈에서도 다음 설정으로 [공통 유틸리티](study-support/README.md)를 사용할 수 있습니다.

```kotlin
dependencies {
    implementation(project(":study-support"))
}
```
