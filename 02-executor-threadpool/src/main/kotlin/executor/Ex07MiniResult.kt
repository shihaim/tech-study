package executor

import study.support.Log.log
import study.support.section
import study.support.result
import study.support.eventually
import study.support.stop
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/**
 * 학습용 단일 완료 + 단일 get 객체. Future 구현이 아니다.
 * 다중 대기자, 취소, 재실행을 지원하지 않으며 get은 한 번만 허용한다.
 *
 * 완료하는 스레드: 결과 공개(outcome) → 대기자 깨우기(unpark)
 * 기다리는 스레드: 대기자 등록 → 결과 확인 → 없으면 park → 결과 재확인
 * park/unpark는 대기 수단이고, 결과 공개는 volatile이 담당한다.
 */
class MiniResult<T> {
    private class Outcome<T>(val result: Result<T>)

    @Volatile
    private var outcome: Outcome<T>? = null
    private val waiter = AtomicReference<Thread?>()
    private val getCalled = AtomicBoolean()

    @Synchronized
    fun complete(result: Result<T>) {
        // 동시에 complete가 호출돼도 한 번만 완료하도록 검사와 저장을 묶는다.
        check(outcome == null) { "이미 완료됨" }
        outcome = Outcome(result)
        // 반드시 결과를 먼저 공개한다. 대기자가 아직 없으면 깨울 스레드도 없다.
        LockSupport.unpark(waiter.get())
    }

    fun get(timeoutMillis: Long = 5_000): T {
        require(timeoutMillis in 1..60_000)
        check(getCalled.compareAndSet(false, true)) { "get은 한 번만 허용" }

        // (1) 결과 확인 전에 대기자를 등록한다.
        // 이후 완료되면 unpark 신호를 받고, 이미 완료됐다면 아래에서 결과를 바로 읽는다.
        waiter.set(Thread.currentThread())
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        try {
            while (true) {
                // (2) 깨어났다는 사실만으로 완료를 단정하지 않는다. 매번 상태를 확인한다.
                if (Thread.interrupted()) throw InterruptedException()
                val ready = outcome
                if (ready != null) return ready.result.getOrThrow()

                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw TimeoutException("결과 대기 시간 초과")

                // (3) 확인 직후 complete가 실행돼도 먼저 온 unpark의 permit으로 돌아온다.
                // interrupt, timeout, spurious wakeup도 가능하므로 루프 처음으로 돌아간다.
                LockSupport.parkNanos(this, remaining)
            }
        } finally {
            waiter.set(null)
        }
    }
}

private fun demonstrateCompletionBeforeGet() {
    val result = MiniResult<String>()
    result.complete(Result.success("완료가 먼저"))
    // 아직 대기자가 없어서 unpark할 대상이 없었어도, 저장된 결과를 바로 읽을 수 있다.
    check(result.get() == "완료가 먼저")
    log("get 이전에 완료되어도 결과를 읽는다")
}

private fun demonstrateGetBeforeCompletion() {
    val executor = pool("mini-waiter")
    val result = MiniResult<Int>()
    val waitingThread = AtomicReference<Thread>()
    try {
        // (1) 워커를 get 안에서 기다리게 하고, 호출자가 완료를 담당한다.
        val waiting = executor.submit(Callable {
            waitingThread.set(Thread.currentThread())
            result.get()
        })
        eventually("결과 대기 진입") {
            waitingThread.get()?.let { LockSupport.getBlocker(it) === result } == true
        }

        // (2) 결과 없이 깨우는 신호를 보낸다. unpark 자체는 결과를 만들어 주지 않는다.
        // 이 검사는 관찰 시점의 미완료 상태만 확인하며 재대기까지 증명하지는 않는다.
        LockSupport.unpark(waitingThread.get())
        check(!waiting.isDone)

        // (3) 결과를 공개하고 깨우면 get이 값을 반환하고 워커 작업도 끝난다.
        result.complete(Result.success(42))
        check(waiting.result() == 42)
        log("대기 → 결과 공개 → unpark → 결과 42")
    } finally {
        executor.stop()
    }
}

private fun demonstrateInterruptedGet() {
    val executor = pool("mini-interrupt")
    val result = MiniResult<Int>()
    val waitingThread = AtomicReference<Thread>()
    try {
        val interruptionObserved = executor.submit(Callable {
            waitingThread.set(Thread.currentThread())
            try {
                result.get()
                false
            } catch (_: InterruptedException) {
                true
            }
        })
        // 호출자: 워커가 실제로 park에 진입한 것을 확인한 뒤 interrupt를 요청한다.
        eventually("interrupt 실습 대기 진입") {
            waitingThread.get()?.let { LockSupport.getBlocker(it) === result } == true
        }
        waitingThread.get().interrupt()
        check(interruptionObserved.result())
        log("interrupt에 의해 대기 종료")
    } finally {
        executor.stop()
    }
}

private fun demonstrateFailedResult() {
    val result = MiniResult<Int>()
    result.complete(Result.failure(IllegalArgumentException("계산 실패")))
    // 이 학습용 객체는 Future와 달리 ExecutionException으로 감싸지 않고 원인을 던진다.
    try {
        result.get()
        error("실패 전달 필요")
    } catch (e: IllegalArgumentException) {
        check(e.message == "계산 실패")
    }
}

private fun demonstrateGetTimeout() {
    val result = MiniResult<Int>()
    // complete를 호출하지 않으므로 제한 시간이 지나면 대기만 종료된다.
    try {
        result.get(timeoutMillis = 20)
        error("timeout 필요")
    } catch (_: TimeoutException) {
        log("미완료 결과는 제한 시간 후 timeout")
    }
}

private fun demonstrateSingleUseContract() {
    val result = MiniResult<String>()
    result.complete(Result.success("첫 완료"))
    check(result.get() == "첫 완료")

    // 취소와 다중 대기자 처리를 생략한 대신, 완료와 get을 각각 한 번으로 제한한다.
    try {
        result.complete(Result.success("중복"))
        error("중복 완료 금지")
    } catch (e: IllegalStateException) {
        check(e.message == "이미 완료됨")
    }
    try {
        result.get()
        error("다중 get 금지")
    } catch (e: IllegalStateException) {
        check(e.message == "get은 한 번만 허용")
    }
}

fun runEx07() {
    section("07. 심화: 결과 공개와 park/unpark")
    demonstrateCompletionBeforeGet()
    demonstrateGetBeforeCompletion()
    demonstrateInterruptedGet()
    demonstrateFailedResult()
    demonstrateGetTimeout()
    demonstrateSingleUseContract()
}

fun main() = runEx07()
