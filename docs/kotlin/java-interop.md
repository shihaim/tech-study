# Java API를 Kotlin에서 사용할 때

[문법 찾아보기](README.md) · [권장 패턴과 실수](idioms-and-pitfalls.md)

01에서 작성한 설명을 주제별로 옮겼습니다. 코드 조각은 문법 설명용 발췌이며, 생략 부호가 있는 코드는 독립 실행용이 아닙니다.
기준: 레포 Kotlin 2.4.0 / JDK 25. 예제의 전체 구현은 각 절의 실제 코드 링크를 참고하세요.

- [프로퍼티 접근 — getter가 사라진다](#topic-3)
- [SAM 생성자 — Java 인터페이스 넘기기](#topic-7)
- [null 안전성](#topic-9)
- [예외 — Checked Exception이 없다](#topic-18)

<a id="topic-3"></a>

## 프로퍼티 접근 — getter가 사라진다

**Java 개발자가 가장 먼저 당황하는 부분입니다.** Java의 `getXxx()` / `isXxx()` 메서드가 Kotlin에서는 **프로퍼티처럼** 보입니다.

```kotlin
// Ex03Future.kt
future.isDone                        // Java: future.isDone()
slow.isCancelled                     // Java: slow.isCancelled()
Thread.currentThread().name          // Java: Thread.currentThread().getName()
Thread.currentThread().isInterrupted // Java: ...isInterrupted()
e.cause                              // Java: e.getCause()
e.javaClass.simpleName               // Java: e.getClass().getSimpleName()
```

괄호가 없다고 필드에 직접 접근하는 게 아닙니다. **내부적으로는 getter를 호출**합니다. Kotlin 컴파일러가 Java의 getter/setter 규약을 인식해 프로퍼티 문법으로 바꿔 주는 것뿐입니다.

반대로 규약에 안 맞는 메서드는 그대로 괄호가 붙습니다.

```kotlin
response.statusCode()                // getStatusCode() 가 아니라 statusCode() 라서
ForkJoinPool.commonPool().parallelism  // getParallelism() 이라서 프로퍼티
```

`javaClass`는 특이한데, Kotlin에서 `.class`에 해당하는 게 `::class`이고 Java의 `Class` 객체가 필요하면 `javaClass`를 씁니다.

---

실제 코드: [Ex03Future.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex03Future.kt)

<a id="topic-7"></a>

## SAM 생성자 — Java 인터페이스 넘기기

**SAM**은 Single Abstract Method, 즉 추상 메서드가 하나뿐인 인터페이스입니다. `Runnable`, `Callable`, `ThreadFactory` 같은 것들입니다.

Kotlin 람다 `{ ... }`는 그 자체로는 `Runnable`이 **아닙니다.** 함수 타입 `() -> Unit`입니다. Java 인터페이스가 기대되는 자리에 넘길 때만 자동 변환(SAM conversion)됩니다.

```kotlin
// Ex01Runnable.kt
executor.execute { Log.log("람다를 바로 넘기면 SAM 변환이 일어난다") }  // 자동 변환
val task = Runnable { Log.log("작업 실행") }                          // SAM 생성자로 명시
```

`Runnable { ... }`처럼 인터페이스 이름을 앞에 붙이는 걸 **SAM 생성자**라고 합니다. 다음 경우에 의도를 명시하기 좋습니다.

**① 변수에 담을 때** — 기대 타입이 없으면 컴파일러가 뭘로 변환할지 모릅니다.

```kotlin
val task = Runnable { ... }         // OK
val task = { ... }                  // 이건 () -> Unit 이지 Runnable 이 아니다
```

**② 오버로드가 모호할 때**

```kotlin
// Ex10KotlinNotes.kt
val a: Future<Int> = executor.submit(Callable { 1 })
val b: Future<*> = executor.submit(Runnable { /* 부수효과만 */ })
```

`submit`에는 `submit(Callable)`과 `submit(Runnable)` 오버로드가 둘 다 있어서, 람다만 넘기면 어느 쪽으로 갈지 코드만 보고 알기 어렵습니다.

```kotlin
// Ex09NonBlocking.kt - schedule() 도 같은 문제
timer.schedule(Runnable { future.complete("응답$i") }, taskMillis, TimeUnit.MILLISECONDS)
```

`complete()`가 `Boolean`을 반환해서 `Callable<Boolean>`으로도 해석될 수 있기 때문에 명시했습니다.

### 파라미터가 있는 SAM

```kotlin
// study-support/src/main/kotlin/study/support/LoggingAndThreads.kt
return ThreadFactory { runnable ->
    Thread(runnable, "$prefix-${counter.getAndIncrement()}")
}
```

`ThreadFactory.newThread(Runnable r)`의 `r`이 `runnable`로 들어옵니다.

---

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt), [Ex09NonBlocking.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex09NonBlocking.kt), [Ex10KotlinNotes.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt), [LoggingAndThreads.kt](../../study-support/src/main/kotlin/study/support/LoggingAndThreads.kt)

<a id="topic-9"></a>

## null 안전성

타입에 `?`가 붙으면 null이 들어갈 수 있다는 뜻입니다. 안 붙으면 **컴파일러가 null을 막습니다.**

```kotlin
val name: String = null     // 컴파일 에러
val name: String? = null    // OK
```

### 세이프 콜 `?.`

```kotlin
// Ex08GetVsJoin.kt
e.cause?.javaClass?.simpleName
```

`e.cause`가 null이면 뒤를 건너뛰고 전체가 null이 됩니다. Java의 `if (x != null) x.getY()`를 한 글자로 줄인 것입니다.

### 엘비스 연산자 `?:`

```kotlin
// Ex07ExceptionHandling.kt
fun unwrap(throwable: Throwable): Throwable = when (throwable) {
    is CompletionException, is java.util.concurrent.ExecutionException -> throwable.cause ?: throwable
    else -> throwable
}
```

왼쪽이 null이면 오른쪽 값을 씁니다. `cause`가 없으면 원래 예외를 그대로 반환합니다.

### 플랫폼 타입 — 안전망이 없는 구간

Java API에서 nullability 정보가 충분하지 않은 참조 타입은 `String!` 같은 **플랫폼 타입**으로 보일 수 있습니다. Kotlin이 인식하는 nullability 어노테이션이 있으면 nullable/non-null 타입으로 해석될 수 있습니다.

```kotlin
// Ex10KotlinNotes.kt
val voidFuture: CompletableFuture<Void> = CompletableFuture.runAsync { }
val nothing: Void? = voidFuture.join()   // 정상 완료 시 null
```

`join()`의 반환은 `Void!`라 `Void?`로 받든 `Void`로 받든 컴파일됩니다. 후자로 받으면 **런타임에** NPE가 납니다. Java API를 쓸 때는 문서를 보고 직접 판단해야 합니다.

---

실제 코드: [Ex07ExceptionHandling.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex07ExceptionHandling.kt), [Ex08GetVsJoin.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex08GetVsJoin.kt), [Ex10KotlinNotes.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt)

<a id="topic-18"></a>

## 예외 — Checked Exception이 없다

Kotlin에는 Checked Exception 개념 자체가 없습니다. `throws` 선언도 없고, 컴파일러가 try-catch를 강제하지도 않습니다.

```kotlin
// study-support/src/main/kotlin/study/support/LoggingAndThreads.kt
fun sleepMillis(millis: Long) {
    try {
        Thread.sleep(millis)     // Java 라면 InterruptedException 처리를 강제당한다
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    }
}
```

이 `try-catch`는 컴파일러가 시켜서가 아니라 **직접 판단해서 쓴 것**입니다.

편하지만 위험합니다. Java에서 컴파일러가 잡아 주던 것들을 이제 의식적으로 챙겨야 합니다.

- `InterruptedException` — 그대로 전파하거나, 전파하지 않는 경계에서는 상태를 복구하고 종료하는 등 계약에 맞게 처리
- `ExecutionException` / `CompletionException` — 진짜 원인은 `cause`에 있음

catch 순서는 Java와 같습니다. 위에서부터 맞는 것을 찾습니다.

```kotlin
// Ex03Future.kt
try {
    slow.get(500, TimeUnit.MILLISECONDS)
} catch (e: TimeoutException) {
    ...
} catch (e: InterruptedException) {
    ...
} catch (e: ExecutionException) {
    ...
}
```

`throw`도 식이라 엘비스 연산자 오른쪽에 놓을 수 있습니다.

```kotlin
val value = map[key] ?: throw IllegalStateException("없음")
```

---

실제 코드: [Ex03Future.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex03Future.kt), [LoggingAndThreads.kt](../../study-support/src/main/kotlin/study/support/LoggingAndThreads.kt)

## 추가 근거

- [Java와 Kotlin 연동](https://kotlinlang.org/docs/java-interop.html)
- [Kotlin 예외 처리](https://kotlinlang.org/docs/exceptions.html)
