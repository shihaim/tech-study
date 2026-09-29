package executor

import study.support.Log.log
import study.support.section
import study.support.awaitChecked
import study.support.eventually
import study.support.stop

import java.util.concurrent.*

private fun compareQueue(
    label: String,
    queue: BlockingQueue<Runnable>,
    expectedWorkers: Int,
    expectedQueued: Int,
    expectedRejected: Int,
    expectedStartedIds: Set<Int>,
) {
    val executor = pool(name = label, core = 2, max = 4, queue = queue)
    val releaseWorkers = CountDownLatch(1)
    val started = ConcurrentHashMap.newKeySet<Int>()
    var rejected = 0
    try {
        // (1) 호출자: 8개를 차례로 제출한다. 워커는 신호를 받기 전까지 끝나지 않는다.
        // 완료로 큐가 비지 않아야 core → queue → max → reject 배치를 관찰할 수 있다.
        for (id in 1..8) {
            try {
                executor.execute {
                    started.add(id)
                    log("$label task$id 시작")
                    // 워커: 시작한 작업 ID를 기록한 뒤 호출자가 관찰을 끝낼 때까지 대기한다.
                    releaseWorkers.awaitChecked()
                }
            } catch (_: RejectedExecutionException) {
                rejected++
                log("$label task$id 거절")
            }
            executor.snapshot("task$id 제출 후")
        }
        // (2) 제출 반환과 워커의 실행 시작은 별개다. 시작 기록이 채워진 뒤 검증한다.
        eventually("워커 시작 대기") { started.size == expectedWorkers }
        check(executor.poolSize == expectedWorkers)
        check(executor.queue.size == expectedQueued)
        check(rejected == expectedRejected)
        check(started == expectedStartedIds)
        log("$label: 시작한 작업=${started.sorted()}")
    } finally {
        // (3) 관찰이 끝나면 워커를 풀어 준다. 큐에 남은 작업도 실행한 뒤 풀을 종료한다.
        releaseWorkers.countDown()
        executor.stop()
    }
}

fun runEx02() {
    section("02. core → queue → max → reject")
    // 1~2: core 워커 / 3~5: 큐 / 6~7: 추가 워커 / 8: 거절.
    // 따라서 FIFO 큐여도 3~5보다 6~7이 먼저 시작한다.
    compareQueue(
        label = "bounded",
        queue = ArrayBlockingQueue(3),
        expectedWorkers = 4,
        expectedQueued = 3,
        expectedRejected = 1,
        expectedStartedIds = setOf(1, 2, 6, 7),
    )
    // 큐가 계속 받아 주므로 max까지 워커를 늘릴 필요가 없다.
    compareQueue(
        label = "unbounded",
        queue = LinkedBlockingQueue(),
        expectedWorkers = 2,
        expectedQueued = 6,
        expectedRejected = 0,
        expectedStartedIds = setOf(1, 2),
    )
    // 저장 공간이 없는 큐다. 기존 워커가 모두 붙잡혀 있어 max 이후 제출은 거절된다.
    compareQueue(
        label = "handoff",
        queue = SynchronousQueue(),
        expectedWorkers = 4,
        expectedQueued = 0,
        expectedRejected = 4,
        expectedStartedIds = setOf(1, 2, 3, 4),
    )
}

fun main() = runEx02()
