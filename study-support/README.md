# study-support

학습 예제를 위한 작은 공통 라이브러리입니다. 외부 라이브러리나 DI 프레임워크 없이 Gradle 프로젝트 의존성으로 사용합니다.

[전체 문서 지도](../docs/README.md) · [모듈 구조](../docs/architecture.md) · [Kotlin 확장 함수 문법](../docs/kotlin/lambdas-and-collections.md#topic-15)

```text
01-concurrency-basics ──→ study-support
02-executor-threadpool ─→ study-support
```

## 사용 방법

학습 모듈의 `build.gradle.kts`에 선언합니다.

```kotlin
dependencies {
    implementation(project(":study-support"))
}
```

Kotlin에서는 필요한 함수만 가져옵니다.

```kotlin
import study.support.Log.log
import study.support.namedThreadFactory
import study.support.section

fun main() {
    section("새 학습 주제")
    val worker = namedThreadFactory("study").newThread { log("작업 실행") }
    worker.start()
    worker.join()
}
```

## 제공 기능

| API | 의미 |
| --- | --- |
| `Log.log`, `section` | 경과 시간·스레드 이름 출력; section에서 시간 기준 초기화 |
| `namedThreadFactory` | 접두사와 순번이 있는 스레드 생성 |
| `sleepMillis` | interrupt 상태를 복구하고 InterruptedException을 다시 전달 |
| `CountDownLatch.awaitChecked` | 최대 10초 대기 후 미완료면 검증 실패 |
| `Future.result` | 최대 10초 결과 대기; Future의 예외 계약 유지 |
| `eventually` | 5ms 간격으로 조건 확인, 최대 10초 후 검증 실패 |
| `ExecutorService.stop` | 정상 종료를 10초 기다린 뒤 필요하면 shutdownNow, 추가 10초 대기 |

로거는 한 JVM 안에서 순차 실행하는 학습 예제를 기준으로 합니다. 예제를 병렬로 실행하며 section을 호출하면 시간 기준이 공유됩니다.
`stop`은 interrupt를 무시하는 작업을 강제 종료하지 못하며, 미실행 작업의 Future 취소까지 대신하지 않습니다.
종료 동작 자체를 공부하는 예제에서는 직접 shutdown/awaitTermination 등을 호출하세요.

풀 크기·큐·거절 정책과 예제 도메인은 학습 모듈의 책임입니다.
`buildSrc`는 Kotlin/JDK 등 빌드 설정을 공유하며 이 라이브러리와 역할이 다릅니다.
