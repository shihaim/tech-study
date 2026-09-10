package executor

import study.support.Log.log
import study.support.section
import study.support.awaitChecked
import study.support.eventually
import study.support.stop

import java.util.concurrent.*

private fun compareQueue(label: String, queue: BlockingQueue<Runnable>, workers: Int, queued: Int, rejects: Int) {
    val executor = pool(label, 2, 4, queue)
    val release = CountDownLatch(1)
    val started = ConcurrentHashMap.newKeySet<Int>()
    var rejected = 0
    try {
        // 모든 시작 작업을 붙잡는다. 작업 완료 타이밍에 따라 큐가 비는 것을 방지한다.
        for (id in 1..8) {
            try {
                executor.execute {
                    started.add(id)
                    log("$label task$id 시작")
                    release.awaitChecked()
                }
            } catch (_: RejectedExecutionException) { rejected++; log("$label task$id 거절") }
            executor.snapshot("task$id 제출 후")
        }
        eventually("워커 시작 대기") { started.size == workers }
        check(executor.poolSize == workers && executor.queue.size == queued && rejected == rejects)
        if (label == "bounded") {
            check(started == setOf(1, 2, 6, 7))
            log("3~5는 큐에서 대기하는 동안 6~7이 먼저 시작했다")
        }
    } finally { release.countDown(); executor.stop() }
}

fun runEx02() {
    section("02. core → queue → max → reject")
    compareQueue("bounded", ArrayBlockingQueue(3), 4, 3, 1)
    compareQueue("unbounded", LinkedBlockingQueue(), 2, 6, 0)
    compareQueue("handoff", SynchronousQueue(), 4, 0, 4)
}

fun main() = runEx02()
