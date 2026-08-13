# Kotlin 입문 문법 정리 — concurrency 패키지 코드 기준

이 모듈의 예제 코드에 실제로 쓰인 Kotlin 문법만 모았습니다. 전부 `src/main/kotlin/concurrency/` 안의 실제 코드에서 가져왔고, Java와 다른 지점 위주로 정리했습니다.

## 목차

1. [파일 구조와 함수](#1-파일-구조와-함수)
2. [변수 — val과 var](#2-변수--val과-var)
3. [프로퍼티 접근 — getter가 사라진다](#3-프로퍼티-접근--getter가-사라진다)
4. [문자열](#4-문자열)
5. [함수 인자 — 이름 붙이기와 기본값](#5-함수-인자--이름-붙이기와-기본값)
6. [람다와 함수 타입](#6-람다와-함수-타입)
7. [SAM 생성자 — Java 인터페이스 넘기기](#7-sam-생성자--java-인터페이스-넘기기)
8. [함수 참조 `::`](#8-함수-참조-)
9. [null 안전성](#9-null-안전성)
10. [클래스 — data class와 object](#10-클래스--data-class와-object)
11. [모든 것이 식(expression)이다](#11-모든-것이-식expression이다)
12. [when과 스마트 캐스트](#12-when과-스마트-캐스트)
13. [컬렉션](#13-컬렉션)
14. [구조 분해와 라벨](#14-구조-분해와-라벨)
15. [확장 함수](#15-확장-함수)
16. [제네릭과 스타 프로젝션](#16-제네릭과-스타-프로젝션)
17. [스프레드 연산자 `*`](#17-스프레드-연산자-)
18. [예외 — Checked Exception이 없다](#18-예외--checked-exception이-없다)
19. [자잘한 것들](#19-자잘한-것들)

---

## 1. 파일 구조와 함수

### 클래스 없이 함수를 만들 수 있다

Java는 모든 메서드가 클래스 안에 있어야 하지만, Kotlin은 파일에 함수를 바로 씁니다. 이걸 **톱레벨 함수**라고 합니다.

```kotlin
// Support.kt
fun section(title: String) {
    println()
    println("=".repeat(78))
}
```

```java
// Java 라면 이렇게 해야 했다
public final class SupportKt {
    private SupportKt() {}
    public static void section(String title) { ... }
}
```

컴파일하면 실제로 `SupportKt`라는 클래스가 만들어지고 `section`은 그 안의 static 메서드가 됩니다. `build.gradle.kts`의 `mainClass = "concurrency.MainKt"`가 바로 `Main.kt`에서 생성된 클래스 이름입니다.

### 반환 타입은 뒤에, `void`는 `Unit`

```kotlin
fun findUser(id: Long): User { ... }        // Java: User findUser(long id)
fun section(title: String) { ... }          // Java: void section(String title)
```

반환 타입이 없으면 `Unit`입니다. `Unit`은 "값이 없다"가 아니라 **값이 하나뿐인 타입**이라 변수에 담을 수도 있습니다. Java의 `void`와 미묘하게 다른 부분입니다.

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

## 2. 변수 — val과 var

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

## 3. 프로퍼티 접근 — getter가 사라진다

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

## 4. 문자열

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
// Support.kt
println("[%5d ms] [%-14s] %s".format(elapsedMs, Thread.currentThread().name, message))
```

Java의 `String.format("...", args)`를 뒤집어 놓은 형태입니다. 문자열 쪽에 `.format(...)`을 붙입니다. `"=".repeat(78)`도 같은 종류입니다.

---

## 5. 함수 인자 — 이름 붙이기와 기본값

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

## 6. 람다와 함수 타입

### 함수 타입

```kotlin
// Main.kt
private val examples: Map<String, Pair<String, () -> Unit>> = ...

// Ex10KotlinNotes.kt
val block: () -> Unit = { Log.log("...") }
fun <T> ExecutorService.async(block: () -> T): CompletableFuture<T> = ...
```

`() -> Unit`은 "인자 없고 반환 없는 함수" 타입입니다. Java의 `Runnable`이나 `Supplier<T>` 같은 인터페이스 없이 **함수 자체가 타입**입니다.

### 마지막 식이 반환값

```kotlin
// Ex03Future.kt
val future: Future<Int> = executor.submit(Callable {
    sleepMillis(1_000)
    42                  // ← return 없이 이게 반환값
})
```

람다에는 `return`을 쓰지 않습니다. 마지막 식이 자동으로 반환값이 됩니다.

### `it` — 인자가 하나면 이름 생략

```kotlin
// Ex06Chaining.kt
.thenApply { user -> user.email }    // 이름을 붙일 수도 있고
.thenApply { it.email }              // it 으로 줄일 수도 있다
```

중첩되면 `it`이 뭘 가리키는지 헷갈리므로, 람다가 겹칠 때는 이름을 붙이는 게 좋습니다.

```kotlin
// Ex06Chaining.kt - 안쪽 람다에 이름을 준 이유
all.thenApply { futures.map { f -> f.join() } }.join()
```

### 후행 람다 (trailing lambda)

마지막 인자가 함수면 괄호 밖으로 뺄 수 있습니다.

```kotlin
executor.use({ ... })     // 원래 형태
executor.use { ... }      // 인자가 람다뿐이면 괄호 자체를 생략

// Ex01Runnable.kt
Executors.newFixedThreadPool(2, namedThreadFactory("pool")).use { executor ->
    executor.execute(task)
}

// Ex06Chaining.kt - 인자가 더 있으면 괄호는 남는다
userF.thenCombine(accountF) { user, account -> createReport(user, account) }
```

반대로 **람다가 마지막 인자가 아니면** 괄호 안에 넣어야 합니다. `CompletableFuture.supplyAsync(supplier, executor)`가 그런 경우라 보기 불편합니다.

```kotlin
// Ex06Chaining.kt - 람다가 첫 번째 인자라 밖으로 못 뺀다
CompletableFuture.supplyAsync({ findUser(1) }, ioExecutor)
```

이래서 [Ex10KotlinNotes.kt](../src/main/kotlin/concurrency/Ex10KotlinNotes.kt)에서 확장 함수로 감쌌습니다.

---

## 7. SAM 생성자 — Java 인터페이스 넘기기

**SAM**은 Single Abstract Method, 즉 추상 메서드가 하나뿐인 인터페이스입니다. `Runnable`, `Callable`, `ThreadFactory` 같은 것들입니다.

Kotlin 람다 `{ ... }`는 그 자체로는 `Runnable`이 **아닙니다.** 함수 타입 `() -> Unit`입니다. Java 인터페이스가 기대되는 자리에 넘길 때만 자동 변환(SAM conversion)됩니다.

```kotlin
// Ex01Runnable.kt
executor.execute { Log.log("람다를 바로 넘기면 SAM 변환이 일어난다") }  // 자동 변환
val task = Runnable { Log.log("작업 실행") }                          // SAM 생성자로 명시
```

`Runnable { ... }`처럼 인터페이스 이름을 앞에 붙이는 걸 **SAM 생성자**라고 합니다. 두 경우에 꼭 필요합니다.

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
// Support.kt
return ThreadFactory { runnable ->
    Thread(runnable, "$prefix-${counter.getAndIncrement()}")
}
```

`ThreadFactory.newThread(Runnable r)`의 `r`이 `runnable`로 들어옵니다.

---

## 8. 함수 참조 `::`

```kotlin
// Main.kt
"01" to ("Runnable - 작업 자체를 표현한다" to ::runEx01Runnable)
```

`::함수이름`은 그 함수를 **호출하지 않고 함수 자체를 값으로** 넘깁니다. Java의 메서드 참조 `this::runEx01`과 같습니다.

```kotlin
::runEx01Runnable    // 함수 값 () -> Unit
runEx01Runnable()    // 즉시 호출
```

`Map`에 담아 뒀다가 나중에 실행합니다.

```kotlin
entry.second.invoke()    // entry.second() 로도 쓸 수 있다
```

---

## 9. null 안전성

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

Java 코드에는 null 정보가 없습니다. 그래서 Java API가 돌려주는 값은 `String!` 같은 **플랫폼 타입**이 되고, 컴파일러가 검사를 포기합니다.

```kotlin
// Ex10KotlinNotes.kt
val voidFuture: CompletableFuture<Void> = CompletableFuture.runAsync { }
val nothing: Void? = voidFuture.join()   // 항상 null
```

`join()`의 반환은 `Void!`라 `Void?`로 받든 `Void`로 받든 컴파일됩니다. 후자로 받으면 **런타임에** NPE가 납니다. Java API를 쓸 때는 문서를 보고 직접 판단해야 합니다.

---

## 10. 클래스 — data class와 object

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
// Support.kt
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

## 11. 모든 것이 식(expression)이다

Java에서 `if`, `switch`, `try`는 **문(statement)** 이라 값을 못 만듭니다. Kotlin에서는 전부 **식**이라 값을 반환합니다.

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

## 12. when과 스마트 캐스트

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

## 13. 컬렉션

### 생성 함수

```kotlin
// Main.kt
private val examples: Map<String, Pair<String, () -> Unit>> = linkedMapOf(
    "01" to ("Runnable - 작업 자체를 표현한다" to ::runEx01Runnable),
    ...
)
```

`listOf`, `setOf`, `mapOf`, `mutableListOf`, `linkedMapOf`... `new`가 없습니다.

기본 생성 함수는 **읽기 전용** 컬렉션을 만듭니다. 수정하려면 `mutableListOf`를 써야 합니다. (읽기 전용일 뿐 진짜 불변은 아닙니다. 원본이 바뀌면 같이 바뀝니다.)

### `to` — Pair 만들기

```kotlin
"01" to ("Runnable..." to ::runEx01Runnable)
```

`to`는 키워드가 아니라 **중위 함수(infix function)** 입니다. `a to b`는 `Pair(a, b)`와 같습니다.

```kotlin
infix fun <A, B> A.to(that: B): Pair<A, B>
```

`infix`가 붙은 함수는 점과 괄호 없이 `a 함수이름 b`처럼 쓸 수 있습니다. 위 코드는 중첩 Pair라서 `Pair("01", Pair("설명", 함수))` 구조입니다. 그래서 꺼낼 때 `entry.first`, `entry.second`를 씁니다.

### map / forEach

```kotlin
// Ex09NonBlocking.kt
val blockingFutures = (1..taskCount).map { i ->
    CompletableFuture.supplyAsync({ ... }, ioExecutor)
}

// Ex06Chaining.kt
val futures: List<CompletableFuture<User>> = (1L..3L).map { id -> findUserAsync(id, ioExecutor) }
val users: List<User> = ...
Log.log("(5) allOf 결과 = ${users.map { it.name }}")
```

Java Stream의 `.stream().map(...).collect(toList())`에 해당하지만 **Stream 생성/수집이 없습니다.** 컬렉션에 바로 붙습니다.

`1..3`은 **범위(range)** 입니다. `(1L..3L)`처럼 Long 범위도 됩니다. `for (i in 1..5)`로도 쓰고, `map`처럼 컬렉션 함수를 바로 붙일 수도 있습니다.

---

## 14. 구조 분해와 라벨

```kotlin
// Main.kt
examples.forEach { (key, entry) -> println("  $key. ${entry.first}") }

selected.forEach { key ->
    val entry = examples[key]
    if (entry == null) {
        println("알 수 없는 예제 번호: $key")
        return@forEach
    }
    entry.second.invoke()
}
```

### 구조 분해 (destructuring)

`{ (key, entry) -> }`는 `Map.Entry`를 키와 값 두 변수로 쪼갠 것입니다. `component1()`, `component2()`가 있는 타입이면 다 됩니다. `data class`가 이걸 자동 생성해 주므로 `User`도 `val (id, name, email) = user`로 쪼갤 수 있습니다.

### `return@forEach` — 라벨 반환

람다 안에서 그냥 `return`을 쓰면 **바깥 함수 전체**가 끝나 버립니다. 이 람다만 빠져나가려면 라벨을 붙여야 합니다.

```kotlin
return@forEach    // 이번 항목만 건너뛴다 (Java 의 continue 에 해당)
```

Java의 `forEach` 람다 안에서 `return`을 쓰면 그 항목만 건너뛰는 것과 달라서 헷갈리기 쉬운 부분입니다.

---

## 15. 확장 함수

기존 클래스를 상속하거나 고치지 않고 **메서드를 덧붙이는** 문법입니다.

```kotlin
// Ex10KotlinNotes.kt
fun <T> ExecutorService.async(block: () -> T): CompletableFuture<T> =
    CompletableFuture.supplyAsync(block, this)
```

`ExecutorService.` 부분이 **수신 객체 타입**입니다. 함수 안에서 `this`는 그 객체를 가리킵니다. 호출은 원래 있던 메서드처럼 합니다.

```kotlin
val user = pool.async { findUser(7) }
    .thenApply { it.email.lowercase() }
    .join()
```

`supplyAsync(람다, executor)`는 람다가 첫 인자라 후행 람다를 못 쓰는데, 확장 함수로 감싸니 자연스러워졌습니다.

**실제로 클래스에 메서드가 추가되는 건 아닙니다.** 컴파일하면 첫 인자가 수신 객체인 static 메서드가 됩니다. 그래서 private 멤버에는 접근할 수 없습니다.

우리가 쓰는 것들 중에도 확장 함수가 많습니다.

```kotlin
"=".repeat(78)          // String 확장
"...".format(...)       // String 확장
executor.use { ... }    // AutoCloseable 확장
list.toTypedArray()     // Collection 확장
```

### `use` — try-with-resources

```kotlin
// Ex01Runnable.kt
Executors.newFixedThreadPool(2, namedThreadFactory("pool")).use { executor ->
    executor.execute(task)
}
```

Java의 try-with-resources에 해당합니다. 블록이 끝나면 (예외가 나도) `close()`가 호출됩니다. `AutoCloseable` 확장 함수라서 Java 19+에서 `AutoCloseable`이 된 `ExecutorService`에도 쓸 수 있습니다.

---

## 16. 제네릭과 스타 프로젝션

```kotlin
fun <T> ExecutorService.async(block: () -> T): CompletableFuture<T>
```

타입 파라미터는 **함수 이름 앞**에 옵니다. Java는 반환 타입 앞(`<T> CompletableFuture<T> async(...)`)이라 위치가 다릅니다.

### `*` — 스타 프로젝션

```kotlin
// Ex03Future.kt
val stubborn: Future<*> = executor.submit(Runnable { ... })

// Ex10KotlinNotes.kt
val b: Future<*> = executor.submit(Runnable { ... })
```

Java의 와일드카드 `Future<?>`에 해당합니다. "타입 파라미터가 뭔지 모르지만 뭔가는 있다"는 뜻입니다. `Runnable`을 넘긴 `submit`은 결과 타입이 정해지지 않아서 이렇게 받습니다. 값을 꺼내면 `Any?`가 됩니다.

`Any`는 Java의 `Object`에 해당합니다. `Any?`가 최상위 타입입니다.

---

## 17. 스프레드 연산자 `*`

```kotlin
// Ex06Chaining.kt
val all: CompletableFuture<Void> = CompletableFuture.allOf(*futures.toTypedArray())
```

`allOf`는 Java 가변인자 메서드입니다.

```java
public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs)
```

우리가 가진 건 `List`라 두 단계가 필요합니다.

| 단계 | 코드 | 타입 |
| --- | --- | --- |
| 시작 | `futures` | `List<CompletableFuture<User>>` |
| ① 배열로 | `.toTypedArray()` | `Array<CompletableFuture<User>>` |
| ② 펼치기 | `*` | 인자 3개로 전개 |

`vararg`는 내부적으로 배열이라 `List`를 바로 못 받습니다. 그리고 Kotlin은 **배열을 통째로 인자 1개로 넘기는 것**과 **펼쳐서 N개로 넘기는 것**을 구분하기 때문에 `*`를 강제합니다. Java는 배열을 그냥 넘길 수 있어서 이 표시가 없습니다.

```kotlin
val args = arrayOf("a", "b", "c")

listOf(*args)               // ["a", "b", "c"]
listOf(args)                // [배열 객체 하나]
listOf("시작", *args, "끝")  // ["시작", "a", "b", "c", "끝"]
```

`16번`의 스타 프로젝션과 기호가 같지만 완전히 다른 문법입니다. 위치로 구분하세요. 타입 자리면 스타 프로젝션, 인자 자리면 스프레드입니다.

---

## 18. 예외 — Checked Exception이 없다

Kotlin에는 Checked Exception 개념 자체가 없습니다. `throws` 선언도 없고, 컴파일러가 try-catch를 강제하지도 않습니다.

```kotlin
// Support.kt
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

- `InterruptedException` — 잡으면 `Thread.currentThread().interrupt()`로 플래그 복구
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

## 19. 자잘한 것들

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

## 더 볼 것

- [Ex10KotlinNotes.kt](../src/main/kotlin/concurrency/Ex10KotlinNotes.kt) — Java 개발자가 걸리기 쉬운 지점을 실행 가능한 코드로 정리
- [Kotlin 공식 문서 — Java 개발자를 위한 비교](https://kotlinlang.org/docs/comparison-to-java.html)
- [Kotlin 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)
