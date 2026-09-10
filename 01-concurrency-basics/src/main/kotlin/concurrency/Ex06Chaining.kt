package concurrency

import study.support.Log
import study.support.section
import study.support.sleepMillis

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/*
 * ============================================================================
 * Ex06. 작업 연결 - thenApply / thenApplyAsync / thenCompose / thenCombine
 * ============================================================================
 */
fun runEx06Chaining() {
    section("Ex06. CompletableFuture 작업 연결")

    val ioExecutor = namedFixedPool("io", 10)
    val cpuExecutor = namedFixedPool("cpu", 4)

    try {
        // -------------------------------------------------------------------
        // (1) thenApply - 값을 다른 값으로 변환한다 (≈ map)
        // -------------------------------------------------------------------
        //     findUser()
        //         ↓ User
        //     getEmail()
        //         ↓ String
        //     lowercase()
        //         ↓ 최종 String
        val emailFuture: CompletableFuture<String> =
            CompletableFuture
                .supplyAsync({ findUser(1) }, ioExecutor)
                .thenApply { user -> user.email }
                .thenApply { email -> email.lowercase() }

        Log.log("(1) thenApply 결과 = ${emailFuture.join()}")

        // -------------------------------------------------------------------
        // (2) thenApply vs thenApplyAsync - 후속 작업을 "어느 Thread" 가 실행하는가
        // -------------------------------------------------------------------
        // thenApply       : 이전 Stage 를 완료시킨 Thread 가 이어서 실행할 수 있다.
        //                   (이미 완료된 Future 에 붙이면 호출한 Thread 가 실행하기도 한다)
        // thenApplyAsync  : 후속 작업을 별도로 제출한다. Executor 를 주면 그 Pool 에서 돈다.
        //
        // → 무거운 계산을 thenApply 로 붙이면 I/O Pool Thread 를 계속 잡아먹는다.
        //   용도가 다르면 Pool 을 갈아타자.
        Log.log("(2) Thread 가 어떻게 바뀌는지 관찰")
        CompletableFuture
            .supplyAsync({ Log.log("(2) supplyAsync   실행"); findUser(2) }, ioExecutor)
            .thenApply { Log.log("(2) thenApply      실행"); it.email }
            .thenApplyAsync({ Log.log("(2) thenApplyAsync 실행 (cpuExecutor)"); it.uppercase() }, cpuExecutor)
            .join()

        // -------------------------------------------------------------------
        // (3) thenApply vs thenCompose - 후속 작업이 "또 다른 Future" 를 반환할 때
        // -------------------------------------------------------------------
        // thenApply   ≈ map      → CompletableFuture<CompletableFuture<T>> 로 중첩된다
        // thenCompose ≈ flatMap  → 평탄화되어 CompletableFuture<T> 가 된다
        Log.log("(3-A) thenApply 로 비동기 메서드를 연결하면 중첩된다")
        val nested: CompletableFuture<CompletableFuture<Account>> =
            findUserAsync(3, ioExecutor)
                .thenApply { user -> findAccountAsync(user, ioExecutor) }

        // 결과를 꺼내려면 join() 을 두 번 해야 한다. 흐름이 그만큼 지저분해진다.
        Log.log("(3-A) 결과 = ${nested.join().join()}")

        Log.log("(3-B) thenCompose 를 쓰면 평탄화된다")
        val flat: CompletableFuture<Account> =
            findUserAsync(4, ioExecutor)
                .thenCompose { user -> findAccountAsync(user, ioExecutor) }

        Log.log("(3-B) 결과 = ${flat.join()}")

        // -------------------------------------------------------------------
        // (4) thenCombine - 독립적인 두 작업이 모두 끝나면 합친다
        // -------------------------------------------------------------------
        // Ex04 에서 get() 두 번으로 Blocking 했던 코드가 여기서는 선언적으로 표현된다.
        // 두 작업은 병렬로 돌고, 둘 다 끝났을 때 세 번째 람다가 실행된다.
        Log.log("(4) thenCombine - 병렬 실행 후 합치기")
        val userF = findUserAsync(5, ioExecutor)
        val accountF = CompletableFuture.supplyAsync({ findAccount(5) }, ioExecutor)

        val reportFuture: CompletableFuture<Report> =
            userF.thenCombine(accountF) { user, account -> createReport(user, account) }

        Log.log("(4) 결과 = ${reportFuture.join().body}")

        // -------------------------------------------------------------------
        // (5) allOf / anyOf - N개 묶기
        // -------------------------------------------------------------------
        Log.log("(5) allOf - 여러 작업을 모두 기다리기")
        val futures: List<CompletableFuture<User>> =
            (1L..3L).map { id -> findUserAsync(id, ioExecutor) }

        // allOf 는 CompletableFuture<Void> 라 결과값을 담지 않는다.
        // 그래서 완료를 기다린 뒤 각 Future 에서 값을 꺼내는 패턴을 쓴다.
        // (모두 완료된 뒤이므로 여기서의 join() 은 Blocking 되지 않는다)
        val all: CompletableFuture<Void> = CompletableFuture.allOf(*futures.toTypedArray())
        val users: List<User> = all.thenApply { futures.map { f -> f.join() } }.join()
        Log.log("(5) allOf 결과 = ${users.map { it.name }}")

        // anyOf 는 가장 먼저 끝난 하나의 결과를 준다. (반환 타입이 CompletableFuture<Object> 인 점 주의)
        val fastest = CompletableFuture.anyOf(
            delayedValue("느림", 400, ioExecutor),
            delayedValue("빠름", 100, ioExecutor)
        )
        Log.log("(5) anyOf 결과 = ${fastest.join()}")

    } finally {
        ioExecutor.close()
        cpuExecutor.close()
    }

    println(
        """
        |
        |정리)
        |  thenApply       : T -> R           (map)
        |  thenCompose     : T -> CF<R>       (flatMap, 중첩 방지)
        |  thenCombine     : (T, U) -> R      (독립 작업 2개 합치기)
        |  allOf / anyOf   : N개 묶기
        |  ...Async 접미사 : 후속 작업을 별도 Executor 에 제출
        """.trimMargin()
    )
}

/** 비동기 메서드는 보통 이렇게 CompletableFuture 를 반환하도록 만든다. */
private fun findUserAsync(id: Long, executor: ExecutorService): CompletableFuture<User> =
    CompletableFuture.supplyAsync({ findUser(id) }, executor)

private fun findAccountAsync(user: User, executor: ExecutorService): CompletableFuture<Account> =
    CompletableFuture.supplyAsync({ findAccount(user.id) }, executor)

private fun delayedValue(value: String, delayMillis: Long, executor: ExecutorService): CompletableFuture<String> =
    CompletableFuture.supplyAsync({ sleepMillis(delayMillis); value }, executor)

fun main() = runEx06Chaining()
