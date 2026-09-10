package concurrency

import study.support.Log
import study.support.section
import study.support.sleepMillis

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/*
 * ============================================================================
 * Ex03. Future<T> - 비동기 작업의 결과 Handle
 * ============================================================================
 *
 * submit() 은 작업 완료를 기다리지 않고 즉시 Future 를 돌려준다. (= 접수증)
 * 하지만 get() 은 결과가 준비되지 않았다면 "호출한 Thread" 를 Blocking 한다.
 *
 *   main Thread                         Worker Thread
 *       |                                    |
 *       | submit(Callable)                   |
 *       |----------------------------------->|
 *       | Future 반환                        | 작업 실행
 *       |                                    | sleep(1초)
 *       | future.get()  ← 여기서 Blocking     |
 *       |<-----------------------------------|
 *       | 42 반환                            |
 */
fun runEx03Future() {
    section("Ex03. Future<T> - 결과 Handle")

    val executor = namedFixedPool("io", 3)
    try {
        // -------------------------------------------------------------------
        // (1) submit → 즉시 반환, get → Blocking
        // -------------------------------------------------------------------
        val future: Future<Int> = executor.submit(Callable {
            sleepMillis(1_000)
            42
        })

        Log.log("(1) submit() 직후 - isDone=${future.isDone} (아직 실행 중)")
        Log.log("(1) future.get() 호출 → 여기서 main Thread 가 멈춘다")
        Log.log("(1) 결과 = ${future.get()}, isDone=${future.isDone}")

        // -------------------------------------------------------------------
        // (2) Timeout - 무한 대기를 피한다
        //     운영 코드에서 get() 을 인자 없이 쓰는 건 위험하다. 상대가 안 끝나면 영원히 멈춘다.
        // -------------------------------------------------------------------
        val slow: Future<String> = executor.submit(Callable {
            sleepMillis(3_000)
            "느린 결과"
        })

        Log.log("(2) get(500ms) 시도")
        try {
            slow.get(500, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.log("(2) TimeoutException → cancel(true) 로 중단 시도")
            slow.cancel(true)
        } catch (e: InterruptedException) {
            // 대기 중인 "내" Thread 가 interrupt 된 경우. 플래그를 복구하고 빠져나간다.
            Thread.currentThread().interrupt()
            throw IllegalStateException("대기 Thread 가 interrupt 됨", e)
        } catch (e: ExecutionException) {
            // 작업 자체가 예외로 끝난 경우. 진짜 원인은 e.cause.
            throw IllegalStateException("비동기 작업 실패", e.cause)
        }
        Log.log("(2) isCancelled=${slow.isCancelled}, isDone=${slow.isDone}")

        // -------------------------------------------------------------------
        // (3) cancel(true) 는 "강제 종료" 가 아니라 interrupt 요청일 뿐이다
        //     작업 코드가 협조하지 않으면 Thread 는 계속 돌아간다.
        // -------------------------------------------------------------------
        Log.log("(3-A) interrupt 를 무시하는 작업")
        // 데모가 끝나야 하므로 1.5초 뒤에는 스스로 멈추게 했지만,
        // 실무에서 이 조건이 `while (true)` 라면 cancel(true) 로도 절대 멈추지 않는다.
        val deadline = System.nanoTime() + 1_500_000_000L
        val stubborn: Future<*> = executor.submit(Runnable {
            var spin = 0L
            while (System.nanoTime() < deadline) {
                // interrupt 상태를 확인하지 않는다 → cancel 해도 계속 돈다
                spin++
            }
            Log.log("(3-A) 무시한 작업이 스스로 종료 (spin=$spin)")
        })

        sleepMillis(200)
        stubborn.cancel(true)
        // Future 는 "취소됨" 으로 표시되지만, Worker Thread 는 여전히 살아서 돌고 있다.
        Log.log("(3-A) cancel(true) 호출 - isCancelled=${stubborn.isCancelled} (그래도 Thread 는 계속 실행 중)")

        // -------------------------------------------------------------------
        // (3-B) 협조적으로 작성한 작업 - 패턴 1: interrupt 플래그를 주기적으로 확인
        // -------------------------------------------------------------------
        Log.log("(3-B) interrupt 를 확인하는 작업")
        val cooperative: Future<Long> = executor.submit(Callable {
            var processed = 0L
            while (!Thread.currentThread().isInterrupted) {
                processed++ // 작업 한 단위
            }
            Log.log("(3-B) interrupt 감지 → 정상 종료 (processed=$processed)")
            processed
        })

        sleepMillis(200)
        cooperative.cancel(true)
        Log.log("(3-B) cancel(true) → isCancelled=${cooperative.isCancelled}")

        // -------------------------------------------------------------------
        // (3-C) 협조적으로 작성한 작업 - 패턴 2: InterruptedException 을 처리
        //       Thread.sleep / BlockingQueue.take / Lock.lockInterruptibly 등은
        //       interrupt 되면 예외를 던지므로, 잡아서 플래그를 복구하고 빠져나간다.
        // -------------------------------------------------------------------
        Log.log("(3-C) InterruptedException 을 처리하는 작업")
        val sleeping: Future<String> = executor.submit(Callable {
            try {
                Thread.sleep(10_000)
                "정상 완료"
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // 플래그 복구는 관례
                Log.log("(3-C) InterruptedException 수신 → 조기 종료")
                "중단됨"
            }
        })

        sleepMillis(200)
        sleeping.cancel(true)

        // 남은 작업들이 정리될 시간을 준다.
        sleepMillis(1_800)

        println(
            """
            |
            |정리)
            |  Future 가 할 수 있는 것 : isDone / isCancelled / cancel / get / get(timeout)
            |  cancel(true) = interrupt 요청. 작업이 협조하지 않으면 멈추지 않는다.
            |  shutdownNow() 도 마찬가지로 "최선의 중단 시도" 일 뿐이다.
            """.trimMargin()
        )
    } finally {
        // cancel 데모 때문에 아직 도는 Thread 가 있을 수 있으므로
        // use { } (= close, 무기한 대기) 대신 타임아웃을 준다.
        executor.shutdownNow()
        executor.awaitTermination(3, TimeUnit.SECONDS)
    }
}

fun main() = runEx03Future()
