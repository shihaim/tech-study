package executor

import study.support.Log.log
import study.support.section
import study.support.awaitChecked
import study.support.result
import study.support.stop

import java.util.concurrent.*

private fun rejectionCase(callerRuns: Boolean) {
    val label = if (callerRuns) "caller-runs" else "abort"
    val policy = if (callerRuns) ThreadPoolExecutor.CallerRunsPolicy() else ThreadPoolExecutor.AbortPolicy()
    val executor = pool(label, queue = ArrayBlockingQueue(1), policy = policy)
    val release = CountDownLatch(1)
    val started = CountDownLatch(1)
    val context = ThreadLocal<String>()
    val caller = Thread.currentThread()
    context.set("request-42")
    try {
        val first = executor.submit(Callable {
            check(context.get() == null)
            log("정상 워커: context=${context.get()}")
            started.countDown()
            release.awaitChecked()
        })
        started.awaitChecked()
        val second = executor.submit(Callable { log("큐에 있던 작업 실행") })
        executor.snapshot("워커 1 + 큐 1 포화")
        var executed = false
        val before = System.nanoTime()
        try {
            executor.execute {
                check(Thread.currentThread() === caller && context.get() == "request-42")
                log("포화: 호출자가 직접 실행, context=${context.get()}")
                Thread.sleep(100) // 작업 지연 모사. 순서 제어에는 사용하지 않는다.
                executed = true
            }
            check(callerRuns && executed)
        } catch (_: RejectedExecutionException) { check(!callerRuns); log("Abort: 제출자가 거절을 확인") }
        log("execute 반환까지 ${(System.nanoTime() - before) / 1_000_000}ms")
        release.countDown()
        first.result(); second.result()
        executor.shutdown()
        if (callerRuns) {
            var ranAfterShutdown = false
            executor.execute { ranAfterShutdown = true }
            check(!ranAfterShutdown)
            log("CallerRuns도 shutdown 이후에는 실행하지 않고 버린다")
        }
    } finally { context.remove(); release.countDown(); executor.stop() }
}

fun runEx03() {
    section("03. 포화가 호출자에게 미치는 영향")
    rejectionCase(false)
    rejectionCase(true)
}

fun main() = runEx03()
