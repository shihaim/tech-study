package executor

import study.support.Log.log
import study.support.section
import study.support.result
import study.support.stop

import java.util.concurrent.*

fun runEx01() {
    section("01. 실행 전략과 FutureTask")
    // (1) Executor는 실행 계약이다. execute를 호출한다고 반드시 스레드가 바뀌지는 않는다.
    val caller = Thread.currentThread()
    val direct = Executor { it.run() }
    direct.execute {
        check(Thread.currentThread() === caller)
        log("DirectExecutor: 호출자에서 실행")
    }

    val executor = pool("strategy")
    try {
        // (2) 호출자: Callable을 결과 보관함인 FutureTask로 감싸 워커에 전달한다.
        // AbstractExecutorService의 기본 submit 흐름을 직접 펼친 형태다.
        val future = FutureTask(Callable {
            // 워커: FutureTask.run이 이 Callable을 실행하고 반환값을 저장한다.
            check(Thread.currentThread() !== caller)
            log("FutureTask.run → Callable.call")
            42
        })
        executor.execute(future) // FutureTask는 Runnable이면서 Future다.
        // (3) 호출자: result()는 제한 시간을 둔 Future.get이다. 완료까지 여기서 기다린다.
        check(future.result() == 42)
        check(executor.submit(Callable { 42 }).result() == 42)
        log("직접 감싼 FutureTask와 submit 모두 결과 42")
    } finally {
        // 검증에 실패해도 풀을 종료한다. stop()은 공통 종료 헬퍼다.
        executor.stop()
    }
}

fun main() = runEx01()
