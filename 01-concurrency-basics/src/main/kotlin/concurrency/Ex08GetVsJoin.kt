package concurrency

import study.support.Log
import study.support.section
import study.support.sleepMillis

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/*
 * ============================================================================
 * Ex08. get() 과 join() - 무엇이 다른가
 * ============================================================================
 *
 * 공통점: 완료되지 않은 작업에 호출하면 "호출한 Thread" 를 Blocking 한다.
 * 차이점: 던지는 예외 체계.
 *
 *   메서드        | 주요 예외
 *   --------------|--------------------------------------------------
 *   get()         | InterruptedException, ExecutionException  (둘 다 Checked)
 *   join()        | CompletionException                        (Unchecked)
 *   get(timeout)  | 위에 더해 TimeoutException
 *
 * Java 에서는 get() 이 Checked Exception 두 개를 강제해서 코드가 지저분해지고,
 * 그래서 스트림/람다 안에서는 join() 이 선호된다.
 * Kotlin 에는 Checked Exception 이 없으므로 그 차이는 사라지고,
 * "어떤 예외 타입을 잡아야 하는가" 만 남는다.
 */
fun runEx08GetVsJoin() {
    section("Ex08. get() vs join()")

    val ioExecutor = namedFixedPool("io", 4)

    try {
        // -------------------------------------------------------------------
        // (1) 성공했을 때는 완전히 동일하다
        // -------------------------------------------------------------------
        val ok = CompletableFuture.supplyAsync({ sleepMillis(200); "성공" }, ioExecutor)
        Log.log("(1) get()  = ${ok.get()}")
        Log.log("(1) join() = ${ok.join()}")

        // -------------------------------------------------------------------
        // (2) 실패했을 때 예외 타입이 다르다
        // -------------------------------------------------------------------
        val failed = CompletableFuture.supplyAsync({ requestFailingApi() }, ioExecutor)

        try {
            failed.get()
        } catch (e: ExecutionException) {
            Log.log("(2) get()  → ${e.javaClass.simpleName}, cause=${e.cause?.javaClass?.simpleName}")
        }

        try {
            failed.join()
        } catch (e: CompletionException) {
            Log.log("(2) join() → ${e.javaClass.simpleName}, cause=${e.cause?.javaClass?.simpleName}")
        }

        // 두 경우 모두 진짜 원인은 cause 에 있다. 이걸 놓치면 로그에
        // "java.util.concurrent.CompletionException" 만 남아서 원인 파악이 안 된다.

        // -------------------------------------------------------------------
        // (3) get(timeout) - Interrupt 와 Timeout 을 명확히 제어해야 할 때
        // -------------------------------------------------------------------
        val slow = CompletableFuture.supplyAsync({ sleepMillis(2_000); "느린 결과" }, ioExecutor)
        try {
            slow.get(300, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.log("(3) get(300ms) → TimeoutException")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.log("(3) 대기 Thread 가 interrupt 됨")
        }

        // join() 에는 timeout 버전이 없다.
        // 시간 제한이 필요하면 get(timeout) 을 쓰거나, orTimeout() 을 체인에 붙인다. (Ex07 참고)

        // -------------------------------------------------------------------
        // (4) 실무 지침
        // -------------------------------------------------------------------
        // - 체인 중간에서는 join()/get() 을 쓰지 않는 게 원칙이다. 그 순간 비동기의 이점이 사라진다.
        // - 정말로 결과가 필요한 "경계" 에서만 한 번 호출한다.
        //   (예: Controller 의 마지막, 배치 Job 의 끝)
        // - 서비스 코드에서는 join() 이 간결하고,
        //   Interrupt/Timeout 을 명시적으로 제어해야 하면 get(timeout) 이 적합하다.
        Log.log("(4) join()/get() 은 비동기 흐름의 '경계' 에서만 사용할 것")

        sleepMillis(2_000) // 남은 작업 정리 대기
    } finally {
        ioExecutor.shutdown()
        ioExecutor.awaitTermination(5, TimeUnit.SECONDS)
    }
}

fun main() = runEx08GetVsJoin()
