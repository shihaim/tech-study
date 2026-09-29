package executor

import study.support.Log.log
import study.support.section
import study.support.awaitChecked
import study.support.result
import study.support.eventually
import study.support.stop
import java.util.concurrent.*

private fun demonstrateIdleWorkerTimeout() {
    val executor = pool(name = "idle", core = 1, max = 2, queue = SynchronousQueue())
    val releaseWorkers = CountDownLatch(1)
    val workersStarted = CountDownLatch(2)
    try {
        // (1) 두 작업을 동시에 붙잡아 core를 넘는 워커까지 생성한다.
        val jobs = (1..2).map {
            executor.submit(Callable {
                workersStarted.countDown()
                releaseWorkers.awaitChecked()
            })
        }
        workersStarted.awaitChecked()
        check(executor.poolSize == 2)
        executor.snapshot("피크")

        // (2) 작업을 완료시키면 초과 워커만 keepAliveTime 이후 사라진다.
        releaseWorkers.countDown()
        jobs.forEach { it.result() }
        eventually("초과 워커 축소") { executor.poolSize == 1 }
        executor.snapshot("유휴: core만 유지")

        // (3) core에도 timeout을 허용하면 남은 워커 역시 줄어든다.
        executor.allowCoreThreadTimeOut(true)
        eventually("core 워커 축소") { executor.poolSize == 0 }
        executor.snapshot("core timeout 허용")
    } finally {
        releaseWorkers.countDown()
        executor.stop()
    }
}

private fun demonstrateWorkerReuse() {
    // 큐가 있는 단일 워커 풀을 사용해 SynchronousQueue의 인계 타이밍과 분리한다.
    val executor = pool("reuse")
    try {
        val firstWorker = executor.submit(Callable { Thread.currentThread() }).result()
        val secondWorker = executor.submit(Callable { Thread.currentThread() }).result()
        check(firstWorker === secondWorker)
        log("두 작업이 같은 ${firstWorker.name} 워커를 재사용")
    } finally {
        executor.stop()
    }
}

private fun demonstrateShutdown() {
    val executor = pool("graceful")
    val workerStarted = CountDownLatch(1)
    val releaseWorker = CountDownLatch(1)
    try {
        // (1) 실행 중인 작업과 큐에서 기다리는 작업을 하나씩 만든다.
        val running = executor.submit(Callable {
            workerStarted.countDown()
            releaseWorker.awaitChecked()
            "완료"
        })
        workerStarted.awaitChecked()
        val queued = executor.submit(Callable { "대기 작업 완료" })

        // (2) 신규 접수만 닫는다. 붙잡힌 작업이 있으므로 아직 종료 완료 상태는 아니다.
        executor.shutdown()
        check(!executor.isTerminated)
        verifyNewSubmissionRejected(executor)

        // (3) 워커를 풀면 실행 중 작업과 큐의 작업 모두 끝난다.
        releaseWorker.countDown()
        check(running.result() == "완료")
        check(queued.result() == "대기 작업 완료")
        check(executor.awaitTermination(10, TimeUnit.SECONDS))
        log("shutdown: 접수된 작업을 모두 완료한 뒤 종료")
    } finally {
        releaseWorker.countDown()
        executor.stop()
    }
}

private fun demonstrateShutdownNow() {
    val executor = pool("stop-now")
    val workerStarted = CountDownLatch(1)
    val releaseWorker = CountDownLatch(1)
    try {
        // (1) 같은 조건에서 시작하지만, 이번 작업은 interrupt를 받으면 중단 결과를 반환한다.
        val running = executor.submit(Callable {
            workerStarted.countDown()
            try {
                releaseWorker.awaitChecked()
                "완료"
            } catch (_: InterruptedException) {
                log("작업이 interrupt에 협조해 종료")
                "중단"
            }
        })
        workerStarted.awaitChecked()
        val queued = executor.submit(Callable { "대기 작업 완료" })

        // (2) 실행 중 작업에 interrupt를 요청하고, 큐의 미실행 작업은 꺼내 반환한다.
        val pendingTasks = executor.shutdownNow()
        check(pendingTasks.size == 1)
        check(pendingTasks.single() === queued)
        check(running.result() == "중단")

        // (3) 큐에서 꺼낸 FutureTask는 자동 취소되지 않는다. 호출자가 직접 정리한다.
        check(!queued.isDone)
        queued.cancel(false)
        log("미실행 작업 1개 반환 → 명시적 cancel")
        verifyNewSubmissionRejected(executor)
        check(executor.awaitTermination(10, TimeUnit.SECONDS))
    } finally {
        releaseWorker.countDown()
        executor.stop()
    }
}

private fun verifyNewSubmissionRejected(executor: ThreadPoolExecutor) {
    try {
        executor.execute {}
        error("종료 이후 신규 제출은 거절되어야 한다")
    } catch (_: RejectedExecutionException) {
        log("종료 후 신규 제출 거절")
    }
}

fun runEx05() {
    section("05. 워커 재사용, 유휴 축소, 종료")
    demonstrateWorkerReuse()
    demonstrateIdleWorkerTimeout()
    demonstrateShutdown()
    demonstrateShutdownNow()
}

fun main() = runEx05()
