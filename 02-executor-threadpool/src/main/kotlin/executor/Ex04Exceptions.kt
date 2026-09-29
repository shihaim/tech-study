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
    // (1) 워커 밖으로 빠져나온 예외를 관찰할 처리기를 준비한다.
    val uncaughtExceptionObserved = CountDownLatch(1)
    val uncaughtCount = AtomicInteger()
    val factory = namedThreadFactory("errors")
    executor.threadFactory = ThreadFactory { task ->
        factory.newThread(task).apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, error ->
                log("UncaughtExceptionHandler: ${thread.name}, ${error.message}")
                uncaughtCount.incrementAndGet()
                uncaughtExceptionObserved.countDown()
            }
        }
    }
    try {
        // (2) execute: 작업의 예외가 워커 밖으로 나가 UncaughtExceptionHandler에 도달한다.
        executor.execute { throw IllegalStateException("execute 실패") }
        uncaughtExceptionObserved.awaitChecked()

        // (3) submit: FutureTask가 예외를 저장한다. 호출자가 결과를 꺼낼 때 전달받는다.
        val future = executor.submit(Callable<Int> { throw IllegalStateException("submit 실패") })
        try {
            future.result()
            error("실패가 전달되어야 한다")
        } catch (e: ExecutionException) {
            check(e.cause is IllegalStateException)
            log("Future.get: ${e.javaClass.simpleName}, cause=${e.cause?.message}")
        }
        // (4) 후속 작업도 끝까지 기다린다. submit의 실패는 uncaught 횟수를 늘리지 않는다.
        executor.submit(Callable { log("풀은 후속 작업을 계속 실행") }).result()
        check(uncaughtCount.get() == 1)
    } finally {
        executor.stop()
    }
}

fun main() = runEx04()
