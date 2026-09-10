# 람다·컬렉션·확장 함수

[문법 찾아보기](README.md) · [권장 패턴과 실수](idioms-and-pitfalls.md)

01에서 작성한 설명을 주제별로 옮겼습니다. 코드 조각은 문법 설명용 발췌이며, 생략 부호가 있는 코드는 독립 실행용이 아닙니다.
기준: 레포 Kotlin 2.4.0 / JDK 25. 예제의 전체 구현은 각 절의 실제 코드 링크를 참고하세요.

- [람다와 함수 타입](#topic-6)
- [함수 참조 `::`](#topic-8)
- [컬렉션](#topic-13)
- [구조 분해와 라벨](#topic-14)
- [확장 함수](#topic-15)
- [제네릭과 스타 프로젝션](#topic-16)
- [스프레드 연산자 `*`](#topic-17)

<a id="topic-6"></a>

## 람다와 함수 타입

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

람다는 보통 마지막 식을 결과로 사용합니다. 중간 반환에는 라벨 반환을 사용할 수 있고, inline 함수의 람다는 조건에 따라 바깥 함수에서 반환할 수도 있습니다.

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

이래서 [Ex10KotlinNotes.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt)에서 확장 함수로 감쌌습니다.

---

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt), [Ex03Future.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex03Future.kt), [Ex06Chaining.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex06Chaining.kt), [Ex10KotlinNotes.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt), [Main.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Main.kt)

<a id="topic-8"></a>

## 함수 참조 `::`

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

실제 코드: [Main.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Main.kt)

<a id="topic-13"></a>

## 컬렉션

### 생성 함수

```kotlin
// Main.kt
private val examples: Map<String, Pair<String, () -> Unit>> = linkedMapOf(
    "01" to ("Runnable - 작업 자체를 표현한다" to ::runEx01Runnable),
    ...
)
```

`listOf`, `setOf`, `mapOf`, `mutableListOf`, `linkedMapOf`... `new`가 없습니다.

`listOf`, `setOf`, `mapOf`는 읽기 전용 타입을 반환합니다. `mutableListOf`와 `linkedMapOf` 등은 수정 가능한 컬렉션을 반환합니다. 읽기 전용 타입으로 mutable 컬렉션을 바라보는 경우, 다른 참조를 통한 변경이 보일 수 있습니다.

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

실제 코드: [Ex06Chaining.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex06Chaining.kt), [Ex09NonBlocking.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex09NonBlocking.kt), [Main.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Main.kt)

<a id="topic-14"></a>

## 구조 분해와 라벨

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

이 예제의 `forEach`는 inline 함수라 람다 안의 일반 `return`이 **바깥 함수 전체**를 종료할 수 있습니다. 모든 람다에서 이런 반환이 허용되는 것은 아닙니다. 이 람다만 빠져나가려면 라벨을 붙여야 합니다.

```kotlin
return@forEach    // 이번 항목만 건너뛴다 (Java 의 continue 에 해당)
```

Java의 `forEach` 람다 안에서 `return`을 쓰면 그 항목만 건너뛰는 것과 달라서 헷갈리기 쉬운 부분입니다.

---

실제 코드: [Main.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Main.kt)

<a id="topic-15"></a>

## 확장 함수

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

실제 코드: [Ex01Runnable.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex01Runnable.kt), [Ex10KotlinNotes.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt)

<a id="topic-16"></a>

## 제네릭과 스타 프로젝션

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

실제 코드: [Ex03Future.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex03Future.kt), [Ex10KotlinNotes.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex10KotlinNotes.kt)

<a id="topic-17"></a>

## 스프레드 연산자 `*`

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

앞의 스타 프로젝션과 기호가 같지만 완전히 다른 문법입니다. 위치로 구분하세요. 타입 자리면 스타 프로젝션, 인자 자리면 스프레드입니다.

---

실제 코드: [Ex06Chaining.kt](../../01-concurrency-basics/src/main/kotlin/concurrency/Ex06Chaining.kt)

## 추가 근거

- [Kotlin 반환과 점프](https://kotlinlang.org/docs/returns.html)
- [Kotlin 확장 함수](https://kotlinlang.org/docs/extensions.html)
