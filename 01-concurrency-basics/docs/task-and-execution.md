# 작업 정의와 실행 전략

[Ex01Runnable.kt](../src/main/kotlin/concurrency/Ex01Runnable.kt)의 `runEx01Runnable`에서 확인할 수 있습니다. 아래 시간은 관찰 예시입니다. **똑같은 `task` 인스턴스 하나**를 세 군데에 넘겼을 뿐인데:

```
[    0 ms] [main          ] (1) task.run() 직접 호출
[   12 ms] [main          ] 작업 실행          ← main 에서 돌았다
[   13 ms] [worker-thread ] 작업 실행          ← 새 Thread 에서 돌았다
[   22 ms] [pool-1        ] 작업 실행          ← Pool Thread 에서 돌았다
```

`task`는 하나도 안 바뀌었습니다. 바뀐 건 **누가 `run()`을 불렀느냐**뿐입니다.

## Runnable 안에 없는 정보

`Runnable`이 가진 건 `void run()` 메서드 하나입니다. 여기엔 이런 게 전혀 안 들어 있습니다.

- 어느 Thread에서 돌 것인가
- 지금 바로 돌 것인가, Queue에서 기다렸다 돌 것인가
- 동시에 몇 개가 돌 것인가
- 예외가 나면 누가 받을 것인가

이걸 **실행 정책(execution policy)** 이라고 부르는데, `Runnable`에는 없고 전부 `Thread`/`Executor` 쪽에 있습니다. 그래서 "실행 주체가 정한다"는 표현을 쓴 겁니다.

## 좋은 점

작업을 정의하는 코드와 실행 방식을 정하는 코드가 분리됩니다. 같은 작업을 테스트에서는 동기로, 운영에서는 Pool 10개로 돌리는 게 `Runnable` 수정 없이 가능합니다. Pool 크기를 4에서 20으로 바꿔도 작업 코드는 그대로입니다.

## 조심할 점 — 이게 실무에서 더 중요합니다

작업을 **만드는 쪽이 실행 환경을 가정하면 안 된다**는 뜻이기도 합니다. 일반 ThreadLocal의 값은 다른 스레드로 자동 전파되지 않기 때문입니다. 원래 스레드의 값이 삭제되는 것은 아닙니다.

```kotlin
// 설명용 의사 코드: 명시적인 문맥 전파 없이 다른 스레드에서 실행하는 경우
transaction {                                   // 트랜잭션은 ThreadLocal 에 묶여 있다
    executor.execute {
        repository.save(entity)                 // 다른 Thread → 이 트랜잭션 밖이다
    }
}
```

Spring에서 확인해야 하는 문맥의 예입니다. 실제 동작은 문맥 전파 설정·프록시·데코레이터에 따라 달라집니다.

| 확인할 문맥 | 전파되지 않았을 때 가능한 증상 |
| --- | --- |
| `@Transactional` 컨텍스트 | 롤백이 안 먹거나, 커밋 전 데이터를 못 읽음 |
| `SecurityContextHolder` | 비동기 작업 안에서 인증 정보가 없음 |
| MDC (`traceId` 등) | 비동기 로그에만 traceId가 안 찍힘 |
| `RequestContextHolder` | 요청이 끝나면 이미 정리돼서 접근 불가 |

기본 ThreadPoolExecutor에서는 예외 경로도 제출 방식에 따라 다릅니다. `execute()`로 넘긴 Runnable이 예외를 던지면 `UncaughtExceptionHandler`로 가서 스택트레이스가 찍히지만, `submit()`으로 넘기면 `Future` 안에 담겨서 결과를 관찰하지 않으면 실패를 놓칠 수 있습니다. Ex07 (6)번의 "조용한 실패"와 같은 이야기입니다.

## 정리

"Runnable은 무엇을 할지만 안다. 언제·어디서·어떤 맥락에서 돌지는 넘겨받는 쪽이 정한다."

그래서 작업 코드를 쓸 때는 **어떤 Thread에서 돌아도 상관없게** 짜야 하고, 실행하는 쪽에서는 **Thread가 바뀌면서 유실되는 컨텍스트가 없는지** 확인해야 합니다.

문맥 차이는 [02 거절 정책 예제](../../02-executor-threadpool/src/main/kotlin/executor/Ex03Rejection.kt), 예외 경로는 [02 예외 예제](../../02-executor-threadpool/src/main/kotlin/executor/Ex04Exceptions.kt)에서 확인합니다.
[01 안내](../README.md) · [Kotlin SAM 문법](../../docs/kotlin/java-interop.md#topic-7)
