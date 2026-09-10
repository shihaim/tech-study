# Kotlin 권장 패턴과 실수

[빠른 찾기](README.md) · [전체 문서 지도](../README.md)

Java 개발자가 01·02 코드를 읽거나 바꿀 때의 상황을 기준으로 합니다.
코드는 설명용 발췌이며 `executor`, `value` 등 주변 변수는 생략한 경우가 있습니다.
짧게 쓰는 것보다 반환 타입·실행 위치·실패 처리가 드러나는 것을 우선합니다.
기준: Kotlin 2.4.0 / JDK 25, 공식 문서 확인 2026-09-10.

<a id="sam"></a>

## submit의 작업 종류를 명시하기

헷갈리는 코드:

```kotlin
executor.submit { 42 } // 반환값만 보고 Callable이 선택되었다고 단정하지 않는다.
```

의도를 명시한 코드:

```kotlin
val answer = executor.submit(Callable { 42 })
val notification = executor.submit(Runnable { println("전송") })
```

Java API에 Runnable/Callable 오버로드가 함께 있을 때 SAM 생성자를 쓰면 코드 독자가 작업의 계약을 확인하기 쉽습니다.
모든 람다에 SAM 생성자가 필요한 것은 아닙니다. [문법과 적용 예](java-interop.md#topic-7), [01 실제 코드](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt).

<a id="scope-functions"></a>

## apply와 let: 무엇을 반환하는지 보기

| 함수 | 블록에서 객체를 부르는 이름 | 전체 식의 반환 |
| --- | --- | --- |
| `apply` | `this` | 원래 객체 |
| `let` | `it` 또는 명시한 인자 이름 | 블록의 마지막 식 |

02의 객체 설정 패턴:

```kotlin
factory.newThread(task).apply {
    uncaughtExceptionHandler = handler
} // 설정한 Thread를 그대로 반환
```

`let`으로 바꾸면 마지막 식이 반환되므로 위 대입만 있는 블록은 `Unit`이 됩니다. 설정한 객체를 반환해야 하는 자리에 맞지 않습니다.

nullable 값의 변환:

```kotlin
thread.get()?.let { worker -> LockSupport.getBlocker(worker) === late } == true
```

스레드 참조가 null이면 let을 실행하지 않습니다. 바깥의 `== true`는 nullable Boolean을 조건으로 읽기 쉽게 만듭니다.
중첩 `it`이 여러 객체를 가리키게 되면 이름을 붙이거나 지역 변수로 나누세요. 객체 설정이 한두 줄이면 일반 대입도 충분합니다.

실제 코드: [예외 처리기의 apply](../../02-executor-threadpool/src/main/kotlin/executor/Ex04Exceptions.kt), [MiniResult 실습의 let](../../02-executor-threadpool/src/main/kotlin/executor/Ex07MiniResult.kt).
근거: [Kotlin 스코프 함수](https://kotlinlang.org/docs/scope-functions.html).

<a id="preconditions"></a>

## require와 check: 입력 오류와 상태 오류 구분

헷갈리는 코드:

```kotlin
check(timeoutMillis > 0) // 호출자가 잘못 넘긴 인자인데 상태 오류로 표현
```

02에서 사용하는 구분:

```kotlin
require(timeoutMillis in 1..60_000) // IllegalArgumentException
check(outcome == null) { "이미 완료됨" } // IllegalStateException
```

`require`는 인자의 전제, `check`는 객체 상태나 실습의 불변 조건에 사용합니다.
뒤의 람다는 실패 메시지를 만듭니다. 둘 다 조건이 거짓이면 예외를 던지며 JVM의 assertions 활성화 옵션을 요구하지 않습니다.
불특정 외부 오류를 무조건 이 두 함수로 변환하지 말고 원래 API의 예외 계약도 유지하세요.

실제 코드: [MiniResult](../../02-executor-threadpool/src/main/kotlin/executor/Ex07MiniResult.kt), [실행기 입력 검증](../../02-executor-threadpool/src/main/kotlin/executor/Main.kt).
근거: [Kotlin 예외와 전제 조건](https://kotlinlang.org/docs/exceptions.html).

<a id="result"></a>

## Result는 성공과 실패를 담는 값이다

```kotlin
val success: Result<Int> = Result.success(42)
val failure: Result<Int> = Result.failure(IllegalStateException("실패"))
success.getOrThrow() // 42
failure.getOrThrow() // 저장한 예외를 던짐
```

Result를 만들었다고 작업이 비동기로 실행되거나 대기자가 깨워지지는 않습니다.
02에서는 [MiniResult](../../02-executor-threadpool/src/main/kotlin/executor/Ex07MiniResult.kt)가 결과 공개와 대기를 별도로 구현합니다.
`getOrNull()`로 실패를 null로 바꾸면 실패 원인을 잃을 수 있으므로, null로 취급한다는 계약이 있을 때만 사용하세요.

권장 방향은 경계에서 실패를 명시적으로 처리하거나 `getOrThrow()`로 전달하는 것입니다.
Future의 `ExecutionException` 래핑과 Result의 원래 예외 전달은 서로 다른 계약입니다.
근거: [Kotlin Result API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/).

<a id="extensions"></a>

## 제네릭 확장 함수는 공통 정책을 이름으로 드러내기

```kotlin
fun <T> Future<T>.result(): T = get(10, TimeUnit.SECONDS)
```

`<T>`는 결과 타입을 보존하고 `Future<T>.`는 수신 객체 타입입니다.
호출할 때는 `import study.support.result` 후 `future.result()`로 씁니다.
Future 자체를 수정한 것이 아니며 이 레포가 정한 10초 대기 정책입니다.

피할 방식은 표준 API와 비슷한 이름으로 취소·timeout·예외를 조용히 바꾸는 것입니다.
공통 함수의 정책은 [study-support 계약](../../study-support/README.md)에 남기고, timeout 자체를 배우는 예제에서는 `get(timeout, unit)`을 직접 씁니다.
문법: [확장 함수](lambdas-and-collections.md#topic-15), 실제 코드: [Waiting.kt](../../study-support/src/main/kotlin/study/support/Waiting.kt).

<a id="null-and-mutability"></a>

## null과 val로 안전성을 과대평가하지 않기

- `value!!`는 null을 처리하는 코드가 아니라 null이면 실패하겠다는 단언입니다. 누락이 정상이라면 `?.`나 기본값을, 오류라면 명시적인 예외를 사용하세요.
- Java 플랫폼 타입은 실제 null 계약을 확인해 nullable 타입으로 받습니다. 특히 `CompletableFuture<Void>`의 정상 결과 null과 Kotlin `Unit`을 혼동하지 않습니다.
- `val`은 재할당을 막습니다. MutableList의 내용 수정이나 동시 접근까지 막지는 않습니다.
- `List<T>`는 읽기 전용 인터페이스입니다. 공유된 mutable 원본이 있으면 값이 변할 수 있습니다.

예: `val items = mutableListOf(1); items.add(2)`는 가능합니다.
독립된 목록이 필요하면 복사 여부를 결정해야 하며, 얕은 복사는 원소 객체까지 불변으로 만들지 않습니다.
관련 설명: [val](basics.md#topic-2), [null](java-interop.md#topic-9), [컬렉션](lambdas-and-collections.md#topic-13).

<a id="concurrency"></a>

## 언어 표기와 동시성 계약을 구분하기

`@Volatile`이나 `@Synchronized`를 붙인 이유는 Kotlin 표기와 실제 동시성 규칙으로 나누어 이해합니다.
JVM에서 전자는 필드의 가시성·순서, 후자는 메서드의 모니터 동기화와 관련됩니다.
`@Volatile var count`에 `count++`를 쓰는 것만으로 복합 연산이 원자적이 되지는 않습니다.

예외를 처리할 때도 `catch (e: Exception) { null }`처럼 interrupt까지 삼키면 종료 의도가 사라질 수 있습니다.
InterruptedException을 그대로 전파할지, 전파할 수 없는 경계에서 상태를 복구하고 종료할지 계약을 정하세요.
`use`는 close를 호출할 뿐 종료 대기 시간에 상한을 추가하지 않습니다.

동시성 구현은 [결과 공개와 대기](../../02-executor-threadpool/docs/waiting-and-signalling.md), 공통 정리는 [Waiting.kt](../../study-support/src/main/kotlin/study/support/Waiting.kt)를 참고하세요.
이 절은 JVM 예제 기준이며 모든 플랫폼의 동기화 모델을 설명하지 않습니다.
