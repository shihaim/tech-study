package executor

import study.support.Log.log
import study.support.section
import study.support.result
import study.support.stop

import java.util.concurrent.*

fun runEx01() {
    section("01. 실행 전략과 FutureTask")
    val caller = Thread.currentThread()
    val direct = Executor { it.run() }
    direct.execute { check(Thread.currentThread() === caller); log("DirectExecutor: 호출자에서 실행") }

    val executor = pool("strategy")
    try {
        // AbstractExecutorService의 기본 submit 흐름을 직접 펼친 형태.
        val future = FutureTask(Callable {
            check(Thread.currentThread() !== caller)
            log("FutureTask.run → Callable.call")
            42
        })
        executor.execute(future) // FutureTask는 Runnable이면서 Future다.
        check(future.result() == 42)
        check(executor.submit(Callable { 42 }).result() == 42)
        log("직접 감싼 FutureTask와 submit 모두 결과 42")
    } finally { executor.stop() }
}

fun main() = runEx01()
