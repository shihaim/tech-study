package concurrency

import study.support.Log
import study.support.section
import study.support.sleepMillis

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool

/*
 * ============================================================================
 * Ex05. CompletableFuture<T> 기본
 * ============================================================================
 *
 * Java:
 *     public class CompletableFuture<T>
 *             implements Future<T>, CompletionStage<T> { }
 *
 *   Future<T>          → 완료 여부, 취소, get()
 *   CompletionStage<T> → 완료 이후 실행할 작업을 연결
 *
 * 즉 CompletableFuture 는 "접수증" 이면서 동시에 "후속 처리 흐름" 이다.
 */
fun runEx05CompletableFutureBasics() {
    section("Ex05. CompletableFuture 기본")

    // -----------------------------------------------------------------------
    // (1) 값을 반환하지 않는 작업 → runAsync(Runnable)
    // -----------------------------------------------------------------------
    val voidFuture: CompletableFuture<Void> = CompletableFuture.runAsync {
        Log.log("(1) runAsync - 이메일 발송")
    }
    voidFuture.join()

    // -----------------------------------------------------------------------
    // (2) 값을 반환하는 작업 → supplyAsync(Supplier<T>)
    // -----------------------------------------------------------------------
    val valueFuture: CompletableFuture<String> = CompletableFuture.supplyAsync {
        sleepMillis(200)
        "result"
    }
    Log.log("(2) supplyAsync 결과 = ${valueFuture.join()}")

    // -----------------------------------------------------------------------
    // (3) Executor 를 지정하지 않으면 어디서 도는가
    // -----------------------------------------------------------------------
    // 기본값은 ForkJoinPool.commonPool() 이다. Thread 이름이 ForkJoinPool.commonPool-worker-N 으로 찍힌다.
    // commonPool 은 JVM 전체가 공유하는 Pool 이고, parallelism 은 보통 (CPU 코어 수 - 1) 이다.
    // 여기에 Blocking I/O 를 올리면 애플리케이션 전체의 병렬 처리가 같이 막힌다.
    Log.log("(3) commonPool parallelism = ${ForkJoinPool.commonPool().parallelism}")
    CompletableFuture.supplyAsync { Log.log("(3) Executor 미지정 → 여기서 실행됨"); Unit }.join()

    // -----------------------------------------------------------------------
    // (4) 그래서 서버 애플리케이션에서는 Executor 를 명시하는 편이 안전하다
    //     용도별로 Pool 을 나눠 두면 한쪽이 막혀도 다른 쪽이 살아 있다.
    // -----------------------------------------------------------------------
    val ioExecutor = namedFixedPool("io", 10)   // Blocking I/O 용: 코어 수보다 크게
    val cpuExecutor = namedFixedPool("cpu", 4)  // 계산 작업용: 코어 수 정도

    try {
        val explicit = CompletableFuture.supplyAsync({
            Log.log("(4) 지정한 ioExecutor 에서 실행됨")
            findUser(1)
        }, ioExecutor)

        Log.log("(4) 결과 = ${explicit.join()}")

        // -------------------------------------------------------------------
        // (5) 이미 값이 있는 CompletableFuture / 직접 완료시키기
        // -------------------------------------------------------------------
        val done = CompletableFuture.completedFuture("이미 완료된 값")
        Log.log("(5) completedFuture = ${done.join()}, isDone=${done.isDone}")

        // "Completable" 이라는 이름 그대로, 바깥에서 결과를 채워 넣을 수 있다.
        // Callback 기반 라이브러리를 CompletableFuture 로 감쌀 때 이 방식을 쓴다. (Ex09 참고)
        val manual = CompletableFuture<String>()
        cpuExecutor.execute {
            sleepMillis(300)
            manual.complete("외부에서 채워 넣은 값") // 실패라면 manual.completeExceptionally(e)
        }
        Log.log("(5) 수동 완료 = ${manual.join()}")

    } finally {
        // Java 19+ 의 close() 는 shutdown() 후 종료까지 대기한다.
        ioExecutor.close()
        cpuExecutor.close()
    }

    println(
        """
        |
        |정리)
        |  runAsync(Runnable)    → CompletableFuture<Void>
        |  supplyAsync(Supplier) → CompletableFuture<T>
        |  Executor 미지정 시 ForkJoinPool.commonPool() 사용 → 운영에서는 직접 지정하자
        """.trimMargin()
    )
}

fun main() = runEx05CompletableFutureBasics()
