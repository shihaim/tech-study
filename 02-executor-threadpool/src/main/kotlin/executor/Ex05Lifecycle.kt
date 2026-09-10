package executor

import study.support.Log.log
import study.support.section
import study.support.awaitChecked
import study.support.result
import study.support.eventually
import study.support.stop

import java.util.concurrent.*

private fun idleWorkers() {
    val executor = pool("idle", 1, 2, SynchronousQueue())
    val release = CountDownLatch(1)
    val started = CountDownLatch(2)
    try {
        val jobs = (1..2).map { executor.submit(Callable { started.countDown(); release.awaitChecked() }) }
        started.awaitChecked()
        check(executor.poolSize == 2)
        executor.snapshot("피크")
        release.countDown(); jobs.forEach { it.result() }
        eventually("초과 워커 축소") { executor.poolSize == 1 }
        executor.snapshot("유휴: core만 유지")
        // 재사용은 큐가 있는 별도 단일 풀에서 확인해 handoff 타이밍과 분리한다.
        executor.allowCoreThreadTimeOut(true)
        eventually("core 워커 축소") { executor.poolSize == 0 }
        executor.snapshot("core timeout 허용")
    } finally { release.countDown(); executor.stop() }
    val single = pool("reuse")
    try {
        val a = single.submit(Callable { Thread.currentThread() }).result()
        val b = single.submit(Callable { Thread.currentThread() }).result()
        check(a === b); log("두 작업이 같은 ${a.name} 워커를 재사용")
    } finally { single.stop() }
}

private fun shutdownCase(immediate: Boolean) {
    val executor = pool(if (immediate) "stop-now" else "graceful")
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    try {
        val running = executor.submit(Callable {
            started.countDown()
            try { release.awaitChecked(); "완료" }
            catch (_: InterruptedException) { log("작업이 interrupt에 협조해 종료"); "중단" }
        })
        started.awaitChecked()
        val queued = executor.submit(Callable { "대기 작업 완료" })
        if (immediate) {
            val pending = executor.shutdownNow()
            check(pending.size == 1 && pending.single() === queued)
            check(running.result() == "중단")
            // 반환된 FutureTask는 자동 취소되지 않는다. 호출자가 정리해야 한다.
            check(!queued.isDone)
            queued.cancel(false)
            log("미실행 작업 1개 반환 → 명시적 cancel")
        } else {
            executor.shutdown()
            check(!executor.isTerminated)
            release.countDown()
            check(running.result() == "완료" && queued.result() == "대기 작업 완료")
        }
        try { executor.execute {}; error("종료 이후 신규 제출은 거절되어야 한다") }
        catch (_: RejectedExecutionException) { log("종료 후 신규 제출 거절") }
        check(executor.awaitTermination(10, TimeUnit.SECONDS))
    } finally { release.countDown(); executor.stop() }
}

fun runEx05() {
    section("05. 워커 재사용, 유휴 축소, 종료")
    idleWorkers(); shutdownCase(false); shutdownCase(true)
}

fun main() = runEx05()
