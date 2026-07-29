package concurrency

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/*
 * ============================================================================
 * Ex09. CompletableFuture 라고 자동으로 Non-blocking 이 되는 것은 아니다
 * ============================================================================
 *
 *     CompletableFuture.supplyAsync({ restTemplate.getForObject(...) }, ioExecutor)
 *
 *     호출자 관점            : 비동기 (바로 리턴한다)
 *     ioExecutor Worker 관점 : 여전히 Blocking I/O (Thread 하나가 응답까지 붙잡혀 있다)
 *
 * 즉 "Blocking 을 다른 Thread 로 옮긴 것" 이지 "Blocking 이 사라진 것" 이 아니다.
 * Thread 수만큼만 동시 처리가 되고, 그 이상은 Queue 에서 기다린다.
 *
 * 반면 API 자체가 비동기인 경우(예: HttpClient.sendAsync)는
 * 응답을 기다리는 동안 어떤 Thread 도 붙잡고 있지 않는다.
 */
fun runEx09NonBlocking() {
    section("Ex09. Blocking vs 진짜 Non-blocking")

    // Thread 2개짜리 Pool 에 400ms 짜리 작업 4개를 올려 비교한다.
    val taskCount = 4
    val taskMillis = 400L

    // -----------------------------------------------------------------------
    // (A) Blocking I/O 를 Pool 에 올리는 경우
    //     Thread 2개 x 2번 = 약 800ms 가 걸린다. Thread 가 병목이다.
    // -----------------------------------------------------------------------
    val ioExecutor = namedFixedPool("io", 2)
    try {
        Log.log("(A) Blocking 방식 시작 - Pool size 2, 작업 ${taskCount}개 x ${taskMillis}ms")
        val startA = System.nanoTime()

        val blockingFutures = (1..taskCount).map { i ->
            CompletableFuture.supplyAsync({
                Log.log("(A) 작업 $i 시작 - 이 Thread 는 응답까지 묶여 있다")
                sleepMillis(taskMillis) // ← Blocking I/O 흉내
                "응답$i"
            }, ioExecutor)
        }
        CompletableFuture.allOf(*blockingFutures.toTypedArray()).join()

        Log.log("(A) 전체 소요 = ${(System.nanoTime() - startA) / 1_000_000}ms (약 800ms - Thread 2개가 병목)")
    } finally {
        ioExecutor.close()
    }

    // -----------------------------------------------------------------------
    // (B) 완료를 "통보받는" 방식
    //     결과를 기다리는 Worker Thread 가 없다. 타이머(= I/O 이벤트 루프에 해당)가
    //     때가 되면 complete() 를 호출해 줄 뿐이다.
    //     Thread Pool 이 2개여도 4개가 동시에 진행되어 약 400ms 에 끝난다.
    // -----------------------------------------------------------------------
    val timer: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(namedThreadFactory("timer"))
    val callbackExecutor = namedFixedPool("cb", 2)

    try {
        Log.log("(B) Non-blocking 방식 시작 - 대기하는 Thread 가 없다")
        val startB = System.nanoTime()

        val nonBlockingFutures = (1..taskCount).map { i ->
            // Callback 기반 API 를 CompletableFuture 로 감싸는 전형적인 패턴:
            // 빈 CompletableFuture 를 만들어 두고, 완료 통보가 오면 complete() 를 호출한다.
            val future = CompletableFuture<String>()
            // schedule() 도 Runnable/Callable 오버로드가 있어 Kotlin 에서는 모호해질 수 있다.
            // complete() 가 Boolean 을 반환하기 때문에 더욱 그렇다 → SAM 생성자를 명시한다.
            timer.schedule(Runnable { future.complete("응답$i") }, taskMillis, TimeUnit.MILLISECONDS)

            // 후속 처리만 Pool 에서 돌린다. 대기에는 Thread 를 쓰지 않는다.
            future.thenApplyAsync({ value ->
                Log.log("(B) 작업 $i 완료 통보 후 처리")
                value
            }, callbackExecutor)
        }
        CompletableFuture.allOf(*nonBlockingFutures.toTypedArray()).join()

        Log.log("(B) 전체 소요 = ${(System.nanoTime() - startB) / 1_000_000}ms (약 400ms - Pool 크기와 무관)")
    } finally {
        timer.close()
        callbackExecutor.close()
    }

    println(
        """
        |
        |정리)
        |  supplyAsync + Blocking I/O → 호출자만 비동기. Worker Thread 는 그대로 묶인다.
        |  진짜 Non-blocking          → 응답 대기에 Thread 를 쓰지 않는다.
        |
        |  그래서 Blocking I/O 를 CompletableFuture 로 감쌀 때는
        |  전용 Pool 을 두고 크기를 넉넉히 잡아야 한다. commonPool 에 올리면 안 되는 이유이기도 하다.
        """.trimMargin()
    )
}

/**
 * 참고: Java HTTP Client 의 sendAsync() 는 API 자체가 비동기다.
 * 즉시 CompletableFuture<HttpResponse<T>> 를 반환하고, 응답이 준비되면 완료된다.
 *
 * 네트워크가 필요하므로 기본 실행에서는 호출하지 않는다.
 * 직접 확인하고 싶으면 runEx09NonBlocking() 아래에서 이 함수를 호출해 보면 된다.
 */
@Suppress("unused")
fun httpClientSendAsyncExample() {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder(URI.create("https://httpbin.org/delay/1")).build()

    val future: CompletableFuture<String> = client
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply { response -> "status=${response.statusCode()}" }
        .exceptionally { ex -> "요청 실패: ${ex.cause?.message}" }

    Log.log("sendAsync 호출 직후 - 아무 Thread 도 응답을 기다리며 멈춰 있지 않다")
    Log.log(future.join())
}

fun main() = runEx09NonBlocking()
