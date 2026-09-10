package executor

import study.support.Log.log
import study.support.section
import study.support.namedThreadFactory
import study.support.awaitChecked
import study.support.result
import study.support.stop

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

fun runEx04() {
    section("04. execute와 submit의 예외 경로")
    val executor = pool("errors")
    val caught = CountDownLatch(1)
    val uncaughtCount = AtomicInteger()
    val factory = namedThreadFactory("errors")
    executor.threadFactory = ThreadFactory { task ->
        factory.newThread(task).apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, error ->
                log("UncaughtExceptionHandler: ${thread.name}, ${error.message}")
                uncaughtCount.incrementAndGet()
                caught.countDown()
            }
        }
    }
    try {
        executor.execute { throw IllegalStateException("execute 실패") }
        caught.awaitChecked()
        val future = executor.submit(Callable<Int> { throw IllegalStateException("submit 실패") })
        try { future.result(); error("실패가 전달되어야 한다") }
        catch (e: ExecutionException) {
            check(e.cause is IllegalStateException)
            log("Future.get: ${e.javaClass.simpleName}, cause=${e.cause?.message}")
        }
        executor.submit(Callable { log("풀은 후속 작업을 계속 실행") }).result()
        check(uncaughtCount.get() == 1)
    } finally { executor.stop() }
}

fun main() = runEx04()
