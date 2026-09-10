# Blocking 대기와 완료 통보 비교

핵심은 **대기 중인 작업마다 워커를 하나씩 점유하지 않는다**는 것입니다. [runEx09NonBlocking](../src/main/kotlin/concurrency/Ex09NonBlocking.kt)의 타이머 모사 실험이며, 아래 시간은 관찰 예시입니다. (A)와 (B)에서 Thread가 하는 일이 완전히 다릅니다.

| | Pool Thread가 하는 일 | 한 작업이 Thread를 잡는 시간 |
| --- | --- | --- |
| (A) | 400ms 동안 **기다린다** | 400ms |
| (B) | 완료 통보를 받고 **로그 한 줄 찍는다** | 짧은 로그 처리 시간 |

Thread 2개로 400ms짜리 일 4개는 800ms가 걸리지만, 후속 처리가 짧으면 2개 워커로 4개 콜백을 빠르게 처리할 수 있습니다.

## 실행 로그에서 관찰할 점

```
[  818 ms] [main ] (B) Non-blocking 방식 시작
[ 1231 ms] [cb-1 ] (B) 작업 1 완료 통보 후 처리
[ 1255 ms] [cb-2 ] (B) 작업 2 완료 통보 후 처리
[ 1255 ms] [cb-1 ] (B) 작업 3 완료 통보 후 처리     ← cb-1 재등장
[ 1255 ms] [cb-2 ] (B) 작업 4 완료 통보 후 처리     ← cb-2 재등장
```

**콜백 풀의 워커는 여전히 2개입니다.** `cb-1`이 작업 1을 처리하고 곧바로 작업 3을 처리했습니다. 각 처리가 순식간에 끝나 Thread가 바로 반납되니까요.

(A)에서는 이게 불가능했습니다. `io-1`이 작업 1을 잡으면 400ms 동안 다른 일을 못 합니다.

```
[   13 ms] [io-1] (A) 작업 1 시작    ← 400ms 점유
[   13 ms] [io-2] (A) 작업 2 시작    ← 400ms 점유
[  414 ms] [io-1] (A) 작업 3 시작    ← 이제서야 반납되어 재사용
[  414 ms] [io-2] (A) 작업 4 시작
```

## 단계별로 무슨 일이 일어나나

**① 빈 CompletableFuture 4개 생성** — 작업별 대기 스레드를 만들지 않음

```kotlin
val future = CompletableFuture<String>()
```

그냥 "아직 값이 없는 상자"입니다. 객체 하나 만든 것뿐입니다.

**② 타이머에 등록** — 작업별 대기 스레드를 만들지 않음

```kotlin
timer.schedule(Runnable { future.complete("응답$i") }, taskMillis, TimeUnit.MILLISECONDS)
```

이 예제의 스케줄러 구현은 내부에 **마감 시각으로 정렬된 큐**(`DelayedWorkQueue`)를 갖고 있습니다. 4개를 전부 큐에 넣습니다. `timer` Thread 하나는 "가장 빠른 마감까지" 잠들어 있습니다.

여기가 핵심입니다. 4개를 기다리는 데 **Thread 1개**만 씁니다. 등록 수가 늘어도 이 단일 스레드 스케줄러의 워커 수는 1개이지만, 큐 메모리와 완료 처리 지연은 증가할 수 있습니다.

**③ 콜백 등록** — 작업별 대기 스레드를 만들지 않음

```kotlin
future.thenApplyAsync({ ... }, callbackExecutor)
```

future가 아직 미완성이라 **실행하지 않습니다.** future 내부의 콜백 목록에 매달아 두기만 합니다. `callbackExecutor`는 이 시점에 손도 안 댑니다.

**④ 400ms 경과** — timer Thread가 깨어남

큐에서 4개를 꺼내 차례로 `complete()`를 호출합니다. `complete()`가 불리는 순간, 매달려 있던 콜백이 `callbackExecutor`에 **제출**됩니다.

**⑤ cb Pool이 처리**

짧은 로그 처리를 수행하므로 2개 워커가 4개 콜백을 처리합니다. 후속 작업이 무거워지면 콜백 풀도 병목이 됩니다.

## 그림으로

```
(A) Blocking
  io-1  [========== 400ms 대기 ==========][===== 작업3 =====]
  io-2  [========== 400ms 대기 ==========][===== 작업4 =====]
                                                        총 800ms

(B) Non-blocking
  timer [......... 4개를 한꺼번에 기다림(Thread 1개) .........]→ 4번 complete()
  cb-1                                                       [1][3]
  cb-2                                                       [2][4]
                                                        총 400ms
```

(A)는 대기가 **Thread 위에** 쌓이고, (B)는 대기가 **큐 안에** 쌓입니다.

## 실제 I/O와의 관계

타이머 예제는 완료 통보 뒤 후속 작업을 실행하는 개념을 보여줍니다. 실제 네트워크 I/O를 구현한 것은 아닙니다.
OS의 준비/완료 통지와 라이브러리 실행기는 유사한 역할을 나누지만, 구현·스레드 수·동작 방식은 플랫폼과 라이브러리에 따라 다릅니다.
이 예제의 시간이나 워커 수를 실제 HTTP 처리량으로 해석하지 않습니다. 가상 스레드를 사용하는 Blocking I/O 모델도 별도로 구분해야 합니다.

## 대신 지켜야 할 규칙이 생깁니다

**공유 완료 처리 스레드를 오래 점유하면 다른 완료 처리도 지연됩니다.**

`timer` Thread는 1개이고 4개의 `complete()`를 **순차로** 실행합니다. 만약 그 안에서 무거운 일을 하면 뒤의 작업들이 줄줄이 밀립니다.

```kotlin
timer.schedule(Runnable {
    sleepMillis(1_000)          // 의도적으로 병목을 만드는 실험
    future.complete("응답$i")
}, ...)
```

WebFlux에서 "이벤트 루프에서 블로킹하지 마라"고 하는 게 정확히 이 이야기입니다. 그래서 예제도 무거운 후속 처리는 `thenApplyAsync`로 별도 Pool에 넘깁니다.

## 실험해 보면 좋은 것

`runEx09NonBlocking`의 (B) 구간에서 `thenApplyAsync`를 `thenApply`로 바꿔 보세요.

```kotlin
future.thenApply { value -> Log.log("(B) 작업 $i 완료 통보 후 처리"); value }
```

완료 전에 콜백을 등록한 이 실험에서는 **`timer-1`** 에서 후속 처리가 실행되는 것을 관찰할 수 있습니다. 이미 완료된 Future에 등록하는 경우 등에는 호출 스레드에서 실행될 수도 있습니다. `complete()`를 호출한 Thread가 후속 처리까지 직접 이어서 하기 때문입니다. `callbackExecutor`는 만들어만 놓고 한 번도 안 쓰이게 됩니다.

Ex06 (2)번에서 다룬 `thenApply` vs `thenApplyAsync` 차이가 여기서 눈에 보입니다.
[01 안내](../README.md) · [스레드 풀 병목](../../02-executor-threadpool/docs/sizing-and-bottlenecks.md)

근거: [CompletableFuture 실행 정책](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html).
