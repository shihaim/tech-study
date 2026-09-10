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
        val submitted = System.nanoTime()
        check(permits.tryAcquire(10, TimeUnit.SECONDS)) { "$name 획득 시간 초과" }
        val count = active.incrementAndGet()
        peak.accumulateAndGet(count, ::maxOf)
        val acquired = System.nanoTime()
        try {
            log("$name 획득 대기=${(acquired - submitted) / 1_000_000}ms, 사용 중=$count")
            return block()
        } finally {
            log("$name 점유=${(System.nanoTime() - acquired) / 1_000_000}ms")
            active.decrementAndGet()
            permits.release()
        }
    }
}

private fun resourceCase(workers: Int, holdDbDuringHttp: Boolean) {
    val executor = pool("resource", workers, queue = ArrayBlockingQueue(12))
    val db = Resource("DB", 2)
    val http = Resource("HTTP", 2)
    val start = System.nanoTime()
    try {
        val jobs = (1..8).map { id ->
            val submitted = System.nanoTime()
            executor.submit(Callable {
                log("task$id 큐 대기=${(System.nanoTime() - submitted) / 1_000_000}ms")
                if (holdDbDuringHttp) {
                    db.withPermit {
                        Thread.sleep(30)
                        http.withPermit { Thread.sleep(120) }
                    }
                } else {
                    db.withPermit { Thread.sleep(30) }
                    http.withPermit { Thread.sleep(120) }
                }
            })
        }
        jobs.forEach { it.result() }
        check(db.peak.get() in 1..2 && http.peak.get() in 1..2)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log("workers=$workers, HTTP 동안 DB 보유=$holdDbDuringHttp: ${elapsed}ms, HTTP 최대 동시 사용=${http.peak.get()}")
    } finally { executor.stop() }
}

fun runEx06() {
    section("06. 외부 자원 상한과 점유 시간")
    resourceCase(2, false)
    resourceCase(8, false)
    resourceCase(8, true)
    log("시간은 관찰값이다. 스레드 수와 처리량의 비례나 특정 속도 차이를 검증하지 않는다.")
}

fun main() = runEx06()
