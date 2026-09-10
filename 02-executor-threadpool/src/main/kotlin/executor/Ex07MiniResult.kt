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
 * park/unpark는 대기 수단이고, 결과 공개는 volatile이 담당한다.
 */
class MiniResult<T> {
    private class Outcome<T>(val result: Result<T>)
    @Volatile private var outcome: Outcome<T>? = null
    private val waiter = AtomicReference<Thread?>()
    private val consumed = AtomicBoolean()

    @Synchronized
    fun complete(result: Result<T>) {
        check(outcome == null) { "이미 완료됨" }
        outcome = Outcome(result) // 결과를 먼저 공개한다.
        LockSupport.unpark(waiter.get())
    }

    fun get(timeoutMillis: Long = 5_000): T {
        require(timeoutMillis in 1..60_000)
        check(consumed.compareAndSet(false, true)) { "get은 한 번만 허용" }
        waiter.set(Thread.currentThread()) // 조건 확인 전에 등록: 완료와의 경쟁에서 신호 유실 방지.
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        try {
            while (true) {
                if (Thread.interrupted()) throw InterruptedException()
                val ready = outcome
                if (ready != null) return ready.result.getOrThrow()
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw TimeoutException("결과 대기 시간 초과")
                LockSupport.parkNanos(this, remaining)
                // permit, interrupt, timeout, spurious wakeup 모두 가능. 반드시 조건 재검사.
            }
        } finally { waiter.set(null) }
    }
}

fun runEx07() {
    section("07. 심화: 결과 공개와 park/unpark")
    val early = MiniResult<String>()
    early.complete(Result.success("완료가 먼저"))
    check(early.get() == "완료가 먼저")
    log("get 이전에 완료되어도 결과를 읽는다")

    val executor = pool("mini-waiter")
    try {
        val late = MiniResult<Int>()
        val thread = AtomicReference<Thread>()
        val waiting = executor.submit(Callable {
            thread.set(Thread.currentThread())
            late.get()
        })
        eventually("결과 대기 진입") { thread.get()?.let { LockSupport.getBlocker(it) === late } == true }
        LockSupport.unpark(thread.get()) // 완료 없이 깨워도 결과를 반환해서는 안 된다.
        check(!waiting.isDone)
        late.complete(Result.success(42))
        check(waiting.result() == 42)
        log("대기 → 결과 공개 → unpark → 결과 42")

        val interrupted = MiniResult<Int>()
        thread.set(null)
        val interruption = executor.submit(Callable {
            thread.set(Thread.currentThread())
            try { interrupted.get(); false }
            catch (_: InterruptedException) { true }
        })
        eventually("interrupt 실습 대기 진입") {
            thread.get()?.let { LockSupport.getBlocker(it) === interrupted } == true
        }
        thread.get().interrupt()
        check(interruption.result())
        log("interrupt에 의해 대기 종료")
    } finally { executor.stop() }

    val failed = MiniResult<Int>()
    failed.complete(Result.failure(IllegalArgumentException("계산 실패")))
    try { failed.get(); error("실패 전달 필요") }
    catch (e: IllegalArgumentException) { check(e.message == "계산 실패") }
    val empty = MiniResult<Int>()
    try { empty.get(20); error("timeout 필요") }
    catch (_: TimeoutException) { log("미완료 결과는 제한 시간 후 timeout") }
    try { early.complete(Result.success("중복")); error("중복 완료 금지") }
    catch (e: IllegalStateException) { check(e.message == "이미 완료됨") }
    try { early.get(); error("다중 get 금지") }
    catch (e: IllegalStateException) { check(e.message == "get은 한 번만 허용") }
}

fun main() = runEx07()
