package concurrency

import study.support.Log
import study.support.section
import study.support.namedThreadFactory

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/*
 * ============================================================================
 * Ex10. Java 로 쓰던 사람이 Kotlin 에서 걸리는 지점들
 * ============================================================================
 */
fun runEx10KotlinNotes() {
    section("Ex10. Kotlin 에서 주의할 점")

    // -----------------------------------------------------------------------
    // (1) 람다는 Runnable 이 아니다 - SAM 변환은 "Java 타입이 기대되는 자리" 에서만 일어난다
    // -----------------------------------------------------------------------
    val block: () -> Unit = { Log.log("(1) 함수 타입 () -> Unit") }
    val runnable: Runnable = Runnable { Log.log("(1) SAM 생성자로 만든 Runnable") }

    block()          // 함수 타입은 그냥 호출
    runnable.run()   // Runnable 은 run()

    // 변수에 담아 재사용할 거라면 처음부터 Runnable/Callable 타입으로 선언해 두는 편이 헷갈리지 않는다.

    // -----------------------------------------------------------------------
    // (2) submit() 오버로드 모호성
    // -----------------------------------------------------------------------
    // ExecutorService.submit 은 Runnable / Callable 오버로드를 함께 갖고 있어서
    // Kotlin 에서 람다만 넘기면 어느 쪽으로 변환될지 코드만 보고 알기 어렵다.
    // → SAM 생성자를 명시하면 의도가 그대로 드러난다.
    Executors.newFixedThreadPool(2, namedThreadFactory("note")).use { executor ->
        val a: Future<Int> = executor.submit(Callable { 1 })          // 결과가 필요하다
        val b: Future<*> = executor.submit(Runnable { /* 부수효과만 */ })  // 결과가 없다
        Log.log("(2) Callable 결과=${a.get()}, Runnable 결과=${b.get()}")
    }

    // -----------------------------------------------------------------------
    // (3) 플랫폼 타입과 null
    // -----------------------------------------------------------------------
    // Java API 가 돌려주는 값은 Kotlin 입장에서 "null 일 수도 아닐 수도 있는" 플랫폼 타입(T!)이다.
    // 컴파일러가 막아 주지 않으니 실제로 null 이 올 수 있는지 직접 판단해야 한다.
    val voidFuture: CompletableFuture<Void> = CompletableFuture.runAsync { }
    val nothing: Void? = voidFuture.join()   // 항상 null 이다. Void 는 값이 없다는 뜻.
    Log.log("(3) runAsync().join() = $nothing")

    // 값이 없는 비동기 작업은 Kotlin 답게 CompletableFuture<Unit> 으로 다루는 방법도 있다.
    val unitFuture: CompletableFuture<Unit> = CompletableFuture.supplyAsync { Log.log("(3) Unit 반환") }
    Log.log("(3) supplyAsync<Unit>().join() = ${unitFuture.join()}")

    // -----------------------------------------------------------------------
    // (4) Checked Exception 이 없다는 것의 양면성
    // -----------------------------------------------------------------------
    // Java 라면 컴파일러가 try-catch 를 강제했을 InterruptedException/ExecutionException 이
    // Kotlin 에서는 아무 경고 없이 지나간다. 놓치기 쉬우니 의식적으로 처리해야 한다.
    //   - InterruptedException 을 잡으면 Thread.currentThread().interrupt() 로 플래그 복구
    //   - ExecutionException / CompletionException 은 cause 를 확인
    Log.log("(4) Kotlin 은 예외 처리를 강제하지 않는다 - 직접 챙길 것")

    // -----------------------------------------------------------------------
    // (5) ExecutorService 와 use { }
    // -----------------------------------------------------------------------
    // Java 19+ 부터 ExecutorService 가 AutoCloseable 이라 Kotlin 의 use { } 를 쓸 수 있다.
    // close() 는 shutdown() 후 종료될 때까지 기다린다는 점만 기억하자.
    // 끝나지 않는 작업이 남아 있으면 close() 에서 멈춰 버리므로
    // 그런 상황에서는 shutdownNow() + awaitTermination(timeout) 을 쓴다.
    Log.log("(5) use { } = shutdown() + 종료 대기")

    // -----------------------------------------------------------------------
    // (6) 확장 함수로 다듬기
    // -----------------------------------------------------------------------
    // Kotlin 이라면 자주 쓰는 패턴을 확장 함수로 만들어 두면 호출부가 훨씬 읽기 좋아진다.
    val pool = namedFixedPool("ext", 2)
    try {
        val user = pool.async { findUser(7) }
            .thenApply { it.email.lowercase() }
            .join()
        Log.log("(6) 확장 함수 사용 결과 = $user")
    } finally {
        pool.close()
    }

    println(
        """
        |
        |참고) 코루틴과의 관계
        |  kotlinx-coroutines-jdk8 을 추가하면 CompletableFuture 와 코루틴을 오갈 수 있다.
        |    - CompletableFuture<T>.await()  : suspend 함수로 소비 (Thread 를 막지 않는다)
        |    - future { ... } / asCompletableFuture() : 코루틴 결과를 CompletableFuture 로 노출
        |  기존 Java 라이브러리가 CompletableFuture 를 반환할 때 경계에서 이렇게 이어 붙인다.
        """.trimMargin()
    )
}

/**
 * `CompletableFuture.supplyAsync({ ... }, executor)` 는 인자 순서 때문에
 * 람다를 뒤에 둘 수 없어 읽기 불편하다. 확장 함수로 감싸면 후행 람다를 쓸 수 있다.
 */
fun <T> ExecutorService.async(block: () -> T): CompletableFuture<T> =
    CompletableFuture.supplyAsync(block, this)

fun main() = runEx10KotlinNotes()
