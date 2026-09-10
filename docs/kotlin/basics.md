# Kotlin 기본 문법 — Java 코드와 비교하기

[문법 찾아보기](README.md) · [권장 패턴과 실수](idioms-and-pitfalls.md)

01에서 작성한 설명을 주제별로 옮겼습니다. 코드 조각은 문법 설명용 발췌이며, 생략 부호가 있는 코드는 독립 실행용이 아닙니다.
기준: 레포 Kotlin 2.4.0 / JDK 25. 예제의 전체 구현은 각 절의 실제 코드 링크를 참고하세요.

- [파일 구조와 함수](#topic-1)
- [변수 — val과 var](#topic-2)
- [문자열](#topic-4)
- [함수 인자 — 이름 붙이기와 기본값](#topic-5)
- [클래스 — data class와 object](#topic-10)
- [if와 try를 식으로 사용하기](#topic-11)
- [when과 스마트 캐스트](#topic-12)
- [자잘한 것들](#topic-19)

<a id="topic-1"></a>

## 파일 구조와 함수

### 클래스 없이 함수를 만들 수 있다

Kotlin은 클래스 밖의 파일 수준에 함수를 바로 정의할 수 있습니다. 이를 **톱레벨 함수**라고 합니다.

```kotlin
// study-support/src/main/kotlin/study/support/LoggingAndThreads.kt
fun section(title: String) {
    println()
    println("=".repeat(78))
}
```

```java
// Java 라면 이렇게 해야 했다
public final class LoggingAndThreadsKt {
    private LoggingAndThreadsKt() {}
    public static void section(String title) { ... }
}
```

컴파일하면 기본적으로 파일명에 따라 `LoggingAndThreadsKt`라는 클래스가 만들어지고 `section`은 그 안의 static 메서드가 됩니다. `build.gradle.kts`의 `mainClass = "concurrency.MainKt"`가 바로 `Main.kt`에서 생성된 클래스 이름입니다.

### 반환 타입은 뒤에, `void`는 `Unit`

```kotlin
fun findUser(id: Long): User { ... }        // Java: User findUser(long id)
fun section(title: String) { ... }          // Java: void section(String title)
```

블록 본문 함수에서 반환 타입을 생략하면 `Unit`입니다. 표현식 본문은 식으로부터 반환 타입을 추론합니다. `Unit`은 "값이 없다"가 아니라 **값이 하나뿐인 타입**이라 변수에 담을 수도 있습니다. Java의 `void`와 미묘하게 다른 부분입니다.

### 함수 본문이 식 하나면 `=`로 줄인다

```kotlin
// Support.kt
fun namedFixedPool(prefix: String, size: Int): ExecutorService =
    Executors.newFixedThreadPool(size, namedThreadFactory(prefix))

// Ex01Runnable.kt 맨 아래
fun main() = runEx01Runnable()
```

`{ return ... }`을 `= ...`로 대체한 것입니다. **표현식 본문(expression body)** 이라고 부릅니다.

---

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt), [LoggingAndThreads.kt](../../study-support/src/main/kotlin/study/support/LoggingAndThreads.kt), [Main.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Main.kt), [Support.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Support.kt)

<a id="topic-2"></a>

## 변수 — val과 var

| 키워드 | 의미 | Java |
| --- | --- | --- |
| `val` | 재할당 불가 | `final User user = ...` |
| `var` | 재할당 가능 | `User user = ...` |

```kotlin
val task = Runnable { ... }     // 재할당 못 함 (기본으로 이걸 쓴다)
var spin = 0L                   // spin++ 해야 하므로 var
```

`val`은 **참조가 고정**될 뿐 객체 내부가 불변이 되는 건 아닙니다. Java의 `final`과 같습니다.

타입은 대부분 생략합니다. 우변에서 추론되기 때문입니다.

```kotlin
val executor = namedFixedPool("io", 3)          // ExecutorService 로 추론
val future: Future<Int> = executor.submit(...)  // 명시하면 문서 역할을 한다
```

예제에서는 학습 목적으로 타입을 일부러 적어 둔 곳이 많습니다. 실무에서는 반환 타입이 뻔하면 생략하는 편입니다.

---

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt)

<a id="topic-4"></a>

## 문자열

### 문자열 템플릿

```kotlin
// Support.kt
Log.log("findUser($id) 시작 - 500ms Blocking")
return User(id, "사용자$id", "USER$id@Example.COM")

// 식을 넣을 때는 중괄호
Log.log("(1) 결과 = ${future.get()}, isDone=${future.isDone}")
Log.log("(A) 전체 소요 = ${(System.nanoTime() - startA) / 1_000_000}ms")
```

변수 하나면 `$name`, 그 외 모든 식은 `${...}`입니다. Java의 문자열 연결(`+`)이나 `String.format`을 대체합니다.

### 여러 줄 문자열 (raw string)

```kotlin
// Ex01Runnable.kt
println(
    """
    |
    |정리)
    |  - run() 직접 호출  → 호출한 Thread 에서 동기 실행
    """.trimMargin()
)
```

`"""`로 감싸면 줄바꿈과 `\`가 그대로 살아 있는 문자열이 됩니다. 이스케이프가 필요 없어서 정규식이나 JSON을 적을 때 편합니다.

`|`는 **마진 문자**입니다. `trimMargin()`이 각 줄에서 `|` 앞의 공백과 `|`를 잘라 냅니다. 이게 없으면 소스 코드의 들여쓰기가 그대로 출력됩니다.

### format은 확장 함수

```kotlin
// study-support/src/main/kotlin/study/support/LoggingAndThreads.kt
println("[%5d ms] [%-18s] %s".format(elapsedMs, Thread.currentThread().name, message))
```

Java의 `String.format("...", args)`를 뒤집어 놓은 형태입니다. 문자열 쪽에 `.format(...)`을 붙입니다. `"=".repeat(78)`도 같은 종류입니다.

---

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt), [LoggingAndThreads.kt](../../study-support/src/main/kotlin/study/support/LoggingAndThreads.kt), [Support.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Support.kt)

<a id="topic-5"></a>

## 함수 인자 — 이름 붙이기와 기본값

### 이름 붙인 인자 (named argument)

```kotlin
// Support.kt
return Account(userId, balance = 1_000L * userId)

return Report(
    title = "${user.name} 리포트",
    body = "email=${user.email}, balance=${account.balance}"
)
```

인자에 이름을 붙일 수 있습니다. 인자가 많거나 같은 타입이 연달아 나올 때 실수를 막아 줍니다. Java에서 주석으로 `/* title */ "..."` 하던 걸 언어가 지원하는 셈입니다.

### 기본값

```kotlin
// Ex01Runnable.kt
val kotlinThread = thread(name = "kotlin-thread") { ... }
```

`kotlin.concurrent.thread`의 실제 시그니처입니다.

```kotlin
fun thread(
    start: Boolean = true,
    isDaemon: Boolean = false,
    contextClassLoader: ClassLoader? = null,
    name: String? = null,
    priority: Int = -1,
    block: () -> Unit
): Thread
```

파라미터가 6개인데 우리는 `name`과 마지막 람다만 넘겼습니다. 나머지는 기본값이 쓰입니다. Java였다면 오버로드를 여러 개 만들어야 했을 겁니다.

---

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt), [Support.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Support.kt)

<a id="topic-10"></a>

## 클래스 — data class와 object

### data class

```kotlin
// Support.kt
data class User(val id: Long, val name: String, val email: String)
```

한 줄입니다. `data`를 붙이면 컴파일러가 자동으로 만들어 줍니다.

- `equals()` / `hashCode()`
- `toString()`
- `copy()`
- `component1()`, `component2()`... (구조 분해용)

Java 16+의 `record`와 비슷하지만, `var` 프로퍼티도 쓸 수 있고 `copy()`가 있다는 점이 다릅니다.

`toString()`이 자동 생성되기 때문에 로그가 이렇게 찍힙니다.

```
(4) 결과 = User(id=1, name=사용자1, email=USER1@Example.COM)
```

Java에서 `toString()`을 안 만들면 `User@1b6d3586`이 찍히던 것과 대비됩니다.

### 주 생성자

```kotlin
data class Account(val userId: Long, val balance: Long)
```

클래스 이름 뒤 괄호가 생성자입니다. `val`을 붙이면 **파라미터인 동시에 프로퍼티**가 됩니다. Java의 필드 선언 + 생성자 + getter 세 벌이 한 줄로 줄어듭니다.

### object — 싱글턴

```kotlin
// study-support/src/main/kotlin/study/support/LoggingAndThreads.kt
object Log {
    @Volatile
    private var origin: Long = System.nanoTime()

    fun reset() { origin = System.nanoTime() }
    fun log(message: String) { ... }
}
```

`class`가 아니라 `object`로 선언하면 **인스턴스가 하나만** 만들어집니다. 언어 차원의 싱글턴입니다.

```java
// Java 라면
public final class Log {
    private static final Log INSTANCE = new Log();
    private Log() {}
    ...
}
```

호출은 `Log.log("...")`처럼 클래스 이름으로 바로 합니다.

---

실제 코드: [LoggingAndThreads.kt](../../study-support/src/main/kotlin/study/support/LoggingAndThreads.kt), [Support.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Support.kt)

<a id="topic-11"></a>

## if와 try를 식으로 사용하기

Kotlin의 `if`, `when`, `try`는 값으로 사용할 수 있습니다. 현대 Java도 switch 식을 지원하지만, if와 try의 사용 방식은 다릅니다.

### if

```kotlin
// Main.kt
val selected = if (args.isEmpty()) examples.keys else args.toList()
```

Java의 삼항 연산자 `? :`가 필요 없습니다. Kotlin에는 삼항 연산자가 아예 없습니다.

### try-catch

```kotlin
// Ex03Future.kt
val sleeping: Future<String> = executor.submit(Callable {
    try {
        Thread.sleep(10_000)
        "정상 완료"          // try 블록의 값
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        Log.log("...")
        "중단됨"             // catch 블록의 값
    }
})
```

`try-catch` 전체가 하나의 값이 되어 람다의 반환값이 됩니다. 성공하면 `"정상 완료"`, 중단되면 `"중단됨"`입니다.

Java였다면 변수를 미리 선언하고 각 블록에서 대입해야 했습니다.

```java
String result;
try {
    Thread.sleep(10_000);
    result = "정상 완료";
} catch (InterruptedException e) {
    result = "중단됨";
}
return result;
```

---

실제 코드: [Ex03Future.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex03Future.kt), [Main.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Main.kt)

<a id="topic-12"></a>

## when과 스마트 캐스트

`when`은 Java `switch`의 강화판입니다. 값 비교뿐 아니라 **타입 검사, 범위 검사, 임의의 조건**을 다 받습니다.

```kotlin
// Ex07ExceptionHandling.kt
when (root) {
    is IllegalStateException -> Log.log("(7) → 외부 API 오류로 분기 처리")
    else -> throw e
}

fun unwrap(throwable: Throwable): Throwable = when (throwable) {
    is CompletionException, is java.util.concurrent.ExecutionException -> throwable.cause ?: throwable
    else -> throwable
}
```

- `is Xxx` — 타입 검사 (Java의 `instanceof`)
- `,` — 여러 조건을 한 가지에 묶기
- `else` — default
- `break`가 필요 없습니다. fall-through가 없습니다.

### 스마트 캐스트

```kotlin
if (root is IllegalStateException) {
    root.message      // 캐스팅 없이 바로 쓴다
}
```

`is`로 타입을 확인하면 그 블록 안에서 **컴파일러가 알아서 캐스팅**합니다. Java의 `((IllegalStateException) root).getMessage()`가 필요 없습니다. (Java 16+의 패턴 매칭 `if (root instanceof IllegalStateException ise)`와 같은 개념입니다.)

---

실제 코드: [Ex07ExceptionHandling.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex07ExceptionHandling.kt)

<a id="topic-19"></a>

## 자잘한 것들

### 세미콜론

줄 끝 `;`는 생략합니다. 한 줄에 두 문장을 쓸 때만 씁니다.

```kotlin
// Ex06Chaining.kt
.supplyAsync({ Log.log("(2) supplyAsync   실행"); findUser(2) }, ioExecutor)
```

### 숫자 리터럴 구분자

```kotlin
sleepMillis(1_000)                       // 1000
val deadline = System.nanoTime() + 1_500_000_000L
balance = 1_000L * userId
```

`_`는 가독성용이고 컴파일 시 무시됩니다. (Java 7+에도 있는 기능입니다.)

`L`은 `Long` 접미사입니다. Kotlin은 숫자 타입 간 **자동 변환이 없어서** Java보다 엄격합니다. `Int`를 `Long` 자리에 그냥 못 넣고 `toLong()`을 불러야 합니다.

### 어노테이션

```kotlin
@Volatile
private var origin: Long = System.nanoTime()

@Suppress("unused")
fun httpClientSendAsyncExample() { ... }
```

Java와 형태가 같습니다. `@Volatile`은 Java의 `volatile` 키워드에 해당하는데, Kotlin에는 그런 키워드가 없어서 어노테이션으로 제공합니다.

### 증감 연산자와 비교

```kotlin
spin++
processed++
while (!Thread.currentThread().isInterrupted) { ... }
while (System.nanoTime() < deadline) { ... }
```

Java와 같습니다. 단 `==`가 Kotlin에서는 `equals()` 호출이고, 참조 비교는 `===`입니다. (Java의 `==`가 `===`, `equals()`가 `==`입니다. 헷갈리기 쉬운 부분입니다.)

### 주석

```kotlin
// 한 줄
/* 여러 줄 */
/** KDoc - IDE 에서 문서로 보인다 */
```

`/** */`는 Javadoc에 해당하는 KDoc입니다. `Support.kt`의 함수 설명들이 이 형식이라 IDE에서 함수 위에 마우스를 올리면 툴팁으로 보입니다.

---

실제 코드: [Ex06Chaining.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex06Chaining.kt), [Support.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Support.kt)

## 추가 참고

- [Kotlin과 Java 비교](https://kotlinlang.org/docs/comparison-to-java.html)
- [Kotlin 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)
