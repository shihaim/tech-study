# 작업 특성에 맞는 Executor 선택

[01 안내](../README.md) · [02 풀 크기와 병목](../../02-executor-threadpool/docs/sizing-and-bottlenecks.md)

## 헷갈렸던 점

[Ex05CompletableFutureBasics.kt](../src/main/kotlin/concurrency/Ex05CompletableFutureBasics.kt)의 로그에는 `commonPool parallelism`이 나옵니다.
이 값은 해당 실행 환경의 관찰값입니다. 모든 환경에서 CPU 수 - 1로 고정된다고 해석하면 안 됩니다.
commonPool은 공유 풀이고 병렬성은 JDK·환경·설정의 영향을 받습니다.

## 공유 풀을 사용할 때의 영향

기본 CompletableFuture의 Executor 없는 async 메서드는 일반적으로 commonPool을 사용합니다.
다만 commonPool이 최소 2의 병렬성을 지원하지 못하면 작업별 스레드 생성으로 대체되는 예외가 있습니다.
그러므로 모든 async 호출이 반드시 동일한 풀에서 실행된다는 설명은 부정확합니다.
근거: [JDK 25 CompletableFuture 정책](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

공유 풀에서 긴 Blocking I/O를 수행하면 같은 풀의 다른 작업도 지연될 수 있습니다.
특정 작업 때문에 다른 모든 코드가 반드시 멈추는 것은 아닙니다. 실제 영향은 작업 특성과 사용 가능한 워커에 달려 있습니다.

## Blocking I/O에서 전용 실행기를 고려하는 이유

예를 들어 각각 1초 동안 워커를 점유하는 작업 100개를 처리한다고 가정합니다.

| 가정 | 단순 계산 |
| --- | --- |
| 워커 3개, 다른 병목 없음 | 약 34묶음, 약 34초 |
| 워커 50개, 다른 병목 없음 | 약 2묶음, 약 2초 |

이는 고정된 워커 수를 가정한 설명이며 commonPool의 실제 성능 보장이나 측정 결과가 아닙니다.
외부 연결 상한·rate limit이 먼저 제한하면 워커를 늘려도 이 계산대로 빨라지지 않습니다.

## 권장 패턴과 적용 범위

학습 코드의 구성 예:

```kotlin
val ioExecutor = namedFixedPool("io", 10)
val cpuExecutor = namedFixedPool("cpu", 4)
```

[Ex06Chaining.kt](../src/main/kotlin/concurrency/Ex06Chaining.kt)는 I/O 조회와 후속 변환의 실행기를 구분합니다.
숫자 10과 4는 학습 설정이며 운영 권장값이 아닙니다. CPU 작업은 가용 병렬성, I/O 작업은 대기 시간과 외부 자원 상한을 함께 측정합니다.

결제와 알림처럼 용도가 다른 실행기를 분리하면 한쪽 작업의 적체가 다른 쪽 워커를 점유하는 것을 줄일 수 있습니다.
그러나 공통 DB·HTTP 연결·CPU를 공유하면 장애가 전파될 수 있으므로 풀 분리만으로 완전한 격리가 보장되지는 않습니다.
고정 풀의 큐 정책까지 검토하려면 [02 실행 흐름](../../02-executor-threadpool/docs/execution-flow.md)을 읽으세요.

## 관찰과 종료

용도별 스레드 이름은 로그와 덤프를 읽기 쉽게 합니다. 스레드 이름만으로 원인을 확정하지 말고 스택과 대기 자원도 확인합니다.
commonPool은 애플리케이션이 만든 일반 풀처럼 shutdown으로 관리하지 않습니다. 완료가 필요한 작업은 결과를 관찰하고 생명주기를 설계해야 합니다.
근거: [JDK 25 ForkJoinPool](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ForkJoinPool.html).

가상 스레드는 많은 Blocking 작업을 표현하는 다른 선택지이지만, CPU·연결 풀·외부 서비스의 동시성 제한까지 제거하지는 않습니다.
이번 모듈에서는 플랫폼 스레드 풀의 실행 위치와 작업 점유를 먼저 학습합니다.
