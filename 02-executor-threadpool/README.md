# 02. Executor와 ThreadPoolExecutor

01에서 작업·결과를 배웠다면, 이번에는 **작업을 어디에 배치하고, 포화되면 어떻게 처리하는가**를 살펴봅니다.
Kotlin + JDK 25만 사용합니다. Spring, 코루틴, 실제 DB/HTTP 연결은 필요하지 않습니다.

공통 로깅·스레드 이름 생성·제한 시간 대기·종료는 [study-support](../study-support/README.md)를 사용합니다.
각 예제 구분선에서 로그의 경과 시간을 0으로 재설정합니다.
풀 생성 정책과 상태 출력은 학습 대상이므로 이 모듈의 [Support.kt](src/main/kotlin/executor/Support.kt)에 유지합니다.

## 학습 순서

시간이 없다면 **02 → 03 → 06**을 먼저 실행하세요. 07은 내부 동작에 관심이 있을 때 읽는 심화입니다.

| 번호 | 코드 | 관찰할 내용 |
| --- | --- | --- |
| 01 | [Executor와 FutureTask](src/main/kotlin/executor/Ex01ExecutorAndFutureTask.kt) | 동일한 실행 계약, 직접 감싼 FutureTask와 submit |
| 02 | [작업 배치와 큐](src/main/kotlin/executor/Ex02PlacementAndQueues.kt) | core=2, max=4, queue=3에서 작업 8개 배치; 큐 3종 비교 |
| 03 | [거절 정책](src/main/kotlin/executor/Ex03Rejection.kt) | Abort와 CallerRuns, 호출자 지연·ThreadLocal 차이 |
| 04 | [예외 경로](src/main/kotlin/executor/Ex04Exceptions.kt) | UncaughtExceptionHandler와 Future.get의 예외 |
| 05 | [생명주기](src/main/kotlin/executor/Ex05Lifecycle.kt) | 워커 재사용, 유휴 축소, 정상 종료·interrupt 종료 |
| 06 | [자원 병목](src/main/kotlin/executor/Ex06ResourceBottleneck.kt) | 연결 상한과 획득 대기·점유 시간 |
| 07 | [MiniResult](src/main/kotlin/executor/Ex07MiniResult.kt) | 단일 대기자 결과 전달의 volatile·park/unpark |

## 실행

Gradle도 JDK 25로 실행해야 합니다. `JAVA_HOME`을 설치된 JDK 25 경로로 설정하세요.

```powershell
.\gradlew.bat :02-executor-threadpool:run
.\gradlew.bat :02-executor-threadpool:run --args="02 03 06"
.\gradlew.bat build
```

macOS/Linux에서는 `./gradlew`를 사용합니다. 각 예제 파일의 `main()`으로 개별 실행할 수도 있습니다.
모든 예제에는 결과·배치·종료 조건을 확인하는 `check`가 있으며, 실패하면 실행이 실패합니다.
대기에는 상한을 두고 `finally`에서 풀을 정리합니다. 별도 테스트 라이브러리는 추가하지 않았습니다.

## 관찰 포인트

### 02: max보다 큐가 먼저다

작업이 끝나지 않도록 latch로 붙잡은 상태에서 관찰합니다.

| 큐 | 실행 중인 작업 | 대기 | 거절 |
| --- | --- | --- | --- |
| ArrayBlockingQueue(3) | 1, 2, 6, 7 | 3, 4, 5 | 8 |
| LinkedBlockingQueue() | 1, 2 | 3~8 | 없음 |
| SynchronousQueue() | 1~4 | 없음 | 5~8 |

FIFO 큐를 써도 **전체 작업 시작 순서**는 FIFO가 아닙니다. 추가 워커는 새로 제출한 작업부터 실행할 수 있습니다.
`activeCount`와 완료 수는 모니터링용 근사치입니다. 로그 출력 순서도 스레드 스케줄링에 따라 달라집니다.

### 03~05: 호출자와 종료 책임

- CallerRuns의 작업 시간만큼 `execute` 반환이 늦어지는지 확인합니다. 일반 ThreadLocal이 워커와 호출자에서 다르게 보입니다.
- CallerRuns는 shutdown 후 작업을 버립니다. Discard 계열도 조용히 버릴 수 있어, submit이 반환한 Future가 미완료로 남는 상황에 주의해야 합니다.
- execute의 실패는 워커의 예외 처리기로, submit의 실패는 Future.get으로 확인합니다. submit의 Future를 무시하면 실패를 놓칠 수 있습니다.
- shutdown은 대기 작업도 처리합니다. shutdownNow는 실행 중 작업에 interrupt를 시도하고 미실행 작업을 반환합니다. 반환된 FutureTask는 예제가 직접 취소합니다.

### 06: 스레드 수보다 자원 점유 시간을 보자

DB·HTTP permit을 각각 2개로 제한하고 8개 작업을 처리합니다.
워커 2개와 8개, HTTP 대기 동안 DB permit을 계속 보유하는 경우를 비교합니다.
`sleep`은 외부 대기 시간만 모사합니다. 실제 연결 풀이나 트랜잭션 구현은 아닙니다.
시간 차이는 환경에 따라 달라지므로 성능 배수는 검증하지 않습니다. 자원 동시 사용 상한은 검증합니다.

### 07: 깨우기와 결과 공개는 다른 책임이다

MiniResult는 학습용이며 JDK Future를 대체하지 않습니다. 완료·get은 각각 한 번만 허용하고 취소·다중 대기자는 지원하지 않습니다.
성공·실패, 완료 선행, 대기 선행, interrupt, timeout, 중복 호출을 실행 중 검증합니다.
완료 없이 보내는 unpark는 조건 재검사 예제이며 JVM의 spurious wakeup 자체를 강제로 만드는 것은 아닙니다.

## 참고 자료

제공된 Executor 본문과 Q&A를 학습 주제의 기준으로 삼고, JDK 25 API에 맞춰 예제를 작성했습니다.

- [ThreadPoolExecutor — Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [LockSupport — Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/LockSupport.html)
