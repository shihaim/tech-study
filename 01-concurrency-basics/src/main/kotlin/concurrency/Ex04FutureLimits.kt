package concurrency

import study.support.Log
import study.support.section

import java.util.concurrent.Callable
import java.util.concurrent.Future

/*
 * ============================================================================
 * Ex04. Future 의 한계 - 왜 CompletableFuture 가 필요한가
 * ============================================================================
 *
 * Future 는 "결과를 조회" 할 수 있을 뿐, "완료되면 이걸 해라" 를 표현하지 못한다.
 *
 *   - 결과를 쓰려면 결국 get() 으로 Blocking 해야 한다
 *   - 완료 시 Callback 을 등록할 수 없다
 *   - 여러 작업의 결합(A와 B가 모두 끝나면 C)이 어렵다
 *   - 예외 처리 파이프라인이 없다
 *   - A의 결과로 B를 시작하는 흐름을 표현하기 복잡하다
 */
fun runEx04FutureLimits() {
    section("Ex04. Future 의 한계")

    namedFixedPool("io", 4).use { executor ->

        // -------------------------------------------------------------------
        // (1) 독립적인 두 작업은 병렬로 돌릴 수 있다 - 여기까지는 Future 로 충분하다
        // -------------------------------------------------------------------
        Log.log("(1) 두 작업 병렬 제출")
        val userFuture: Future<User> = executor.submit(Callable { findUser(1) })
        val accountFuture: Future<Account> = executor.submit(Callable { findAccount(1) })

        // 하지만 결과를 쓰려면 get() 으로 Blocking 해야 한다.
        // main Thread 는 여기서 아무 일도 못 하고 그냥 서 있는다.
        val user = userFuture.get()
        val account = accountFuture.get()
        val report = createReport(user, account)
        Log.log("(1) 리포트 = ${report.body}")

        // -------------------------------------------------------------------
        // (2) 앞 작업의 결과로 뒤 작업을 시작해야 한다면? - 중첩이 시작된다
        // -------------------------------------------------------------------
        Log.log("(2) 의존 관계가 있는 작업 연결")

        // Future 만으로 하려면 "바깥 작업 안에서 다시 get()" 하는 구조가 된다.
        // Worker Thread 하나가 다른 Worker Thread 를 기다리며 놀게 되는 낭비도 생기고,
        // Pool 크기가 작으면 서로를 기다리다 교착(Thread starvation deadlock)까지 갈 수 있다.
        val chained: Future<Report> = executor.submit(Callable {
            val u = executor.submit(Callable { findUser(2) }).get()   // ← Pool Thread 안에서 또 Blocking
            val a = executor.submit(Callable { findAccount(u.id) }).get()
            createReport(u, a)
        })
        Log.log("(2) 리포트 = ${chained.get().body}")

        // -------------------------------------------------------------------
        // (3) Future 에는 조합/변환/예외 처리 API 자체가 없다
        // -------------------------------------------------------------------
        // 아래와 같은 코드는 존재하지 않아서 컴파일되지 않는다.
        //
        //     userFuture.thenApply { it.email }              // (X)
        //     userFuture.thenCombine(accountFuture) { ... }  // (X)
        //     userFuture.exceptionally { "fallback" }        // (X)
        //
        // Future 인터페이스에 있는 건 isDone / isCancelled / cancel / get 뿐이다.
        Log.log("(3) Future 에 있는 것은 조회와 취소뿐 - 조합 API 는 없다")

        println(
            """
            |
            |정리)
            |  Future 로도 "병렬 실행" 은 된다. 안 되는 건 "비동기 흐름의 조립" 이다.
            |  결과를 쓰는 시점마다 Blocking 이 끼어들고, 후속 작업 연결이 중첩 구조가 된다.
            |  → 이 지점을 해결한 것이 CompletableFuture (Ex05 부터)
            """.trimMargin()
        )
    }
}

fun main() = runEx04FutureLimits()
