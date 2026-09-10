# 01. Concurrency Basics — Runnable / Future / CompletableFuture

Java 의 `Runnable`, `Future`, `CompletableFuture` 를 Kotlin 예제로 정리한 모듈입니다.
세 타입은 서로 대체 가능한 대안이 아니라 역할이 다릅니다.

| 타입 | 역할 | 비유 |
| --- | --- | --- |
| `Runnable` | 실행할 작업 자체 | 작업 지시서 |
| `Future<T>` | 제출된 작업의 결과·상태를 조회하는 Handle | 접수증 |
| `CompletableFuture<T>` | 결과 조회 + 후속 작업 연결 | 접수증 + 후속 처리 흐름 |

## 예제 목록

| 번호 | 파일 | 내용 |
| --- | --- | --- |
| 01 | [Ex01Runnable.kt](src/main/kotlin/concurrency/Ex01Runnable.kt) | `Runnable` 은 "무엇을" 만 정한다. 실행 위치는 Thread/Executor 가 정한다 |
| 02 | [Ex02Callable.kt](src/main/kotlin/concurrency/Ex02Callable.kt) | 값을 반환하는 `Callable<T>`, `submit()` 오버로드 주의점 |
| 03 | [Ex03Future.kt](src/main/kotlin/concurrency/Ex03Future.kt) | `get()` Blocking, Timeout, `cancel(true)` 는 강제 종료가 아니다 |
| 04 | [Ex04FutureLimits.kt](src/main/kotlin/concurrency/Ex04FutureLimits.kt) | 왜 `Future` 만으로는 부족한가 |
| 05 | [Ex05CompletableFutureBasics.kt](src/main/kotlin/concurrency/Ex05CompletableFutureBasics.kt) | `runAsync` / `supplyAsync`, commonPool 대신 Executor 지정 |
| 06 | [Ex06Chaining.kt](src/main/kotlin/concurrency/Ex06Chaining.kt) | `thenApply` / `thenApplyAsync` / `thenCompose` / `thenCombine` / `allOf` |
| 07 | [Ex07ExceptionHandling.kt](src/main/kotlin/concurrency/Ex07ExceptionHandling.kt) | `exceptionally` / `whenComplete` / `handle` / `orTimeout` |
| 08 | [Ex08GetVsJoin.kt](src/main/kotlin/concurrency/Ex08GetVsJoin.kt) | `get()` 과 `join()` 의 예외 체계 차이 |
| 09 | [Ex09NonBlocking.kt](src/main/kotlin/concurrency/Ex09NonBlocking.kt) | `CompletableFuture` 라고 자동으로 Non-blocking 이 되지는 않는다 |
| 10 | [Ex10KotlinNotes.kt](src/main/kotlin/concurrency/Ex10KotlinNotes.kt) | Java 로 쓰던 사람이 Kotlin 에서 걸리는 지점들 |

로깅·스레드 이름 생성 등 공통 실습 유틸리티는 [study-support](../study-support/README.md)에서 가져옵니다.
이 모듈에서 사용하는 고정 크기 풀 생성과 예제용 도메인은
[Support.kt](src/main/kotlin/concurrency/Support.kt)에 남겨 둡니다.

## 실행 방법

관련 문서:

- [작업 정의와 실행 전략](docs/task-and-execution.md) — Ex01, ThreadLocal과 예외 경로
- [Executor 선택](docs/choosing-an-executor.md) — Ex05·06, 공유 풀과 작업 격리
- [Blocking과 완료 통보](docs/blocking-vs-nonblocking.md) — Ex09의 관찰과 한계
- [Kotlin 빠른 찾기](../docs/kotlin/README.md) — 기존 Kotlin 문법 문서의 새 위치
- [Java 연동 문법](../docs/kotlin/java-interop.md) — SAM·Void·null·예외

각 예제 파일에 `main()` 이 있어 IDE 에서 파일별로 바로 실행할 수 있습니다.
CLI 에서는 다음과 같이 실행합니다.

전체 실행:

```bash
./gradlew :01-concurrency-basics:run
```

특정 예제만 실행 (번호를 공백으로 구분):

```bash
./gradlew :01-concurrency-basics:run --args="03 07"
```

> 이 모듈은 `jvmToolchain(25)` 를 사용합니다. Gradle 을 JDK 25 로 실행해야 합니다.
> (`ExecutorService.close()` 등 Java 19+ API 를 예제에서 사용합니다.)

## 출력 읽는 법

모든 로그에 경과 시간과 Thread 이름이 붙습니다. 동시성 코드는
"어떤 Thread 가 언제 무엇을 했는가" 가 핵심이기 때문입니다.

```
[  501 ms] [io-1          ] findUser(1) 완료
   ^경과시간   ^실행한 Thread
```

## 핵심 요약

```
Runnable          = 무엇을 실행할 것인가
Thread / Executor = 어디서 어떻게 실행할 것인가
Future            = 결과 조회 + 취소 (조합은 불가)
CompletableFuture = Future + CompletionStage (후속 작업 조립 가능)
```

- `future.get()` 은 호출한 Thread 를 Blocking 한다. 비동기 흐름의 "경계" 에서만 쓴다.
- FutureTask의 `cancel(true)`와 실행기의 `shutdownNow()`는 interrupt를 통한 중단을 시도한다. CompletableFuture의 취소는 같은 방식으로 실행 작업을 interrupt하지 않는다.
- `thenApply` 는 map, `thenCompose` 는 flatMap 이다.
- 체인의 예외는 `CompletionException` 으로 감싸지므로 `cause` 를 확인한다.
- Blocking I/O 를 `supplyAsync` 로 감싸도 Worker Thread 는 그대로 묶인다.
  작업 격리가 필요하면 전용 Executor를 사용하고, 크기는 외부 자원 상한과 함께 측정할 것.
