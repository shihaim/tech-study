package executor

import study.support.Log.log
import study.support.section
import study.support.awaitChecked
import study.support.result
import study.support.stop
import java.util.concurrent.*

// 워커 1개와 큐 1칸을 채운 후, 세 번째 제출을 각 정책이 어떻게 처리하는지 비교한다.
private fun demonstrateAbortPolicy() {
    val executor = pool("abort", queue = ArrayBlockingQueue(1), policy = ThreadPoolExecutor.AbortPolicy())
    val workerStarted = CountDownLatch(1)
    val releaseWorker = CountDownLatch(1)
    try {
        // (1) 워커를 붙잡고 다음 작업을 큐에 넣어 포화 상태를 만든다.
        val running = executor.submit(Callable {
            workerStarted.countDown()
            releaseWorker.awaitChecked()
        })
        workerStarted.awaitChecked()
        val queued = executor.submit(Callable { log("큐에 있던 작업 실행") })
        executor.snapshot("Abort: 워커 1 + 큐 1 포화")

        // (2) 호출자: 세 번째 작업은 실행되지 않고 execute 호출 자체가 예외를 던진다.
        try {
            executor.execute { error("거절된 작업은 실행되면 안 된다") }
            error("포화 상태에서는 제출이 거절되어야 한다")
        } catch (_: RejectedExecutionException) {
            log("Abort: 제출자가 거절을 확인")
        }

        // (3) 이미 접수된 두 작업은 정상적으로 완료할 수 있다.
        releaseWorker.countDown()
        running.result()
        queued.result()
    } finally {
        releaseWorker.countDown()
        executor.stop()
    }
}

private fun demonstrateCallerRunsPolicy() {
    val executor = pool("caller-runs", queue = ArrayBlockingQueue(1), policy = ThreadPoolExecutor.CallerRunsPolicy())
    val workerStarted = CountDownLatch(1)
    val releaseWorker = CountDownLatch(1)
    val requestContext = ThreadLocal<String>()
    val caller = Thread.currentThread()
    requestContext.set("request-42")
    try {
        // (1) 정상 제출은 워커에서 실행된다. 호출자의 일반 ThreadLocal 값은 전달되지 않는다.
        val running = executor.submit(Callable {
            check(requestContext.get() == null)
            log("정상 워커: context=${requestContext.get()}")
            workerStarted.countDown()
            releaseWorker.awaitChecked()
        })
        workerStarted.awaitChecked()
        val queued = executor.submit(Callable { log("큐에 있던 작업 실행") })
        executor.snapshot("CallerRuns: 워커 1 + 큐 1 포화")

        // (2) 포화되면 호출자가 직접 실행한다. 같은 작업도 실행 위치와 보이는 문맥이 달라진다.
        var executedByCaller = false
        val submittedAt = System.nanoTime()
        executor.execute {
            check(Thread.currentThread() === caller)
            check(requestContext.get() == "request-42")
            log("포화: 호출자가 직접 실행, context=${requestContext.get()}")
            Thread.sleep(100) // 작업 지연 모사. 실행 순서를 맞추기 위한 sleep은 아니다.
            executedByCaller = true
        }
        // 작업이 끝나야 execute가 반환되므로, 작업 시간만큼 제출자도 지연된다.
        check(executedByCaller)
        log("execute 반환까지 ${(System.nanoTime() - submittedAt) / 1_000_000}ms")

        releaseWorker.countDown()
        running.result()
        queued.result()
    } finally {
        requestContext.remove()
        releaseWorker.countDown()
        executor.stop()
    }
}

private fun demonstrateCallerRunsAfterShutdown() {
    val executor = pool("caller-runs-shutdown", policy = ThreadPoolExecutor.CallerRunsPolicy())
    try {
        // 포화 상황과 구분해서 관찰한다. 종료된 풀에서는 호출자도 작업을 실행하지 않는다.
        executor.shutdown()
        var executed = false
        executor.execute { executed = true }
        check(!executed)
        log("CallerRuns도 shutdown 이후에는 실행하지 않고 버린다")
    } finally {
        executor.stop()
    }
}

fun runEx03() {
    section("03. 포화가 호출자에게 미치는 영향")
    demonstrateAbortPolicy()
    demonstrateCallerRunsPolicy()
    demonstrateCallerRunsAfterShutdown()
}

fun main() = runEx03()
