package executor

import study.support.Log.log
import study.support.section
import study.support.result
import study.support.stop

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

// 실제 DB/HTTP 연결 대신 permit으로 동시 사용 상한과 획득 대기를 모사한다.
private class Resource(private val name: String, capacity: Int) {
    private val permits = Semaphore(capacity, true)
    private val active = AtomicInteger()
    val peak = AtomicInteger()

    fun <T> withPermit(block: () -> T): T {
        // 워커는 자원이 빌 때까지 여기서 기다린다. 워커 수와 자원 동시 사용 수는 다르다.
        val submitted = System.nanoTime()
        check(permits.tryAcquire(10, TimeUnit.SECONDS)) { "$name 획득 시간 초과" }
        val count = active.incrementAndGet()
        peak.accumulateAndGet(count, ::maxOf)
        val acquired = System.nanoTime()
        try {
            log("$name 획득 대기=${(acquired - submitted) / 1_000_000}ms, 사용 중=$count")
            return block()
        } finally {
            // 작업이 실패해도 permit을 돌려줘 다음 워커가 자원을 사용할 수 있게 한다.
            log("$name 점유=${(System.nanoTime() - acquired) / 1_000_000}ms")
            active.decrementAndGet()
            permits.release()
        }
    }
}

private fun releaseDbBeforeHttp(db: Resource, http: Resource) {
    // DB 사용이 끝나는 즉시 반납한다. HTTP를 기다리는 동안 다른 작업이 DB를 쓸 수 있다.
    db.withPermit { Thread.sleep(30) }
    http.withPermit { Thread.sleep(120) }
}

private fun holdDbDuringHttp(db: Resource, http: Resource) {
    // 바깥 블록을 나갈 때 DB를 반납하므로, HTTP 대기와 사용 시간도 DB 점유에 포함된다.
    db.withPermit {
        Thread.sleep(30)
        http.withPermit { Thread.sleep(120) }
    }
}

private fun observeResourceUsage(
    workers: Int,
    description: String,
    useResources: (Resource, Resource) -> Unit,
) {
    val executor = pool(name = "resource", core = workers, queue = ArrayBlockingQueue(12))
    val db = Resource("DB", 2)
    val http = Resource("HTTP", 2)
    val start = System.nanoTime()
    try {
        // (1) 호출자: 8개 작업을 제출하고, 제출 시각을 각 작업에 전달한다.
        val jobs = (1..8).map { id ->
            val submitted = System.nanoTime()
            executor.submit(Callable {
                // (2) 워커: 실행 시작까지의 지연과, 자원 획득 후의 점유 시간을 구분한다.
                log("task$id 제출→시작=${(System.nanoTime() - submitted) / 1_000_000}ms")
                useResources(db, http)
            })
        }
        // (3) 호출자: 모든 작업이 끝난 뒤 최대 동시 사용 수와 전체 시간을 관찰한다.
        jobs.forEach { it.result() }
        check(db.peak.get() in 1..2)
        check(http.peak.get() in 1..2)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log("workers=$workers, $description: ${elapsed}ms, HTTP 최대 동시 사용=${http.peak.get()}")
    } finally {
        executor.stop()
    }
}

fun runEx06() {
    section("06. 외부 자원 상한과 점유 시간")
    // 먼저 자원 사용 방식을 고정하고 워커 수만 늘려 본다.
    observeResourceUsage(workers = 2, description = "HTTP 전에 DB 반납", useResources = ::releaseDbBeforeHttp)
    observeResourceUsage(workers = 8, description = "HTTP 전에 DB 반납", useResources = ::releaseDbBeforeHttp)
    // 같은 워커 수에서 DB를 오래 점유하는 방식으로 바꾸어 비교한다.
    observeResourceUsage(workers = 8, description = "HTTP 동안 DB 보유", useResources = ::holdDbDuringHttp)
    log("시간은 관찰값이다. 스레드 수와 처리량의 비례나 특정 속도 차이를 검증하지 않는다.")
}

fun main() = runEx06()
