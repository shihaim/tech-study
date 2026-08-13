package concurrency

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

fun runEx08_2Interrupt() {
    section("Ex08-2. interrupt")

    val ioExecutor = namedFixedPool("io", 4)

    try {
        // (3) get(timeout) 대기 중 interrupt 를 받는 경우
        // 지금 이 Thread(main)의 참조를 미리 잡아 둔다.
        // 아래 try 블록에 들어가면 main 은 get() 안에서 멈추므로
        // 스스로를 깨울 수 없다. 반드시 다른 Thread 가 필요하다.
        val waiter = Thread.currentThread()

        // 300ms 뒤에 대기 중인 main 을 깨울 Thread
        val interrupter = thread(name = "interrupter") {
            sleepMillis(300)
            Log.log("(3) main Thread 에 interrupt 를 건다")
            waiter.interrupt()
        }

        // 작업 자체는 2초짜리
        val slow = CompletableFuture.supplyAsync({ sleepMillis(2_000); "느린 결과" }, ioExecutor)

        try {
            // timeout 을 2초로 잡았지만 300ms 에 interrupt 가 먼저 도착한다
            slow.get(2_000, TimeUnit.MILLISECONDS)
            Log.log("(3) 정상 완료 - 여기는 실행되지 않는다")
        } catch (e: TimeoutException) {
            Log.log("(3) get(2s) → TimeoutException")
        } catch (e: InterruptedException) {
            // 이제 이 블록이 실제로 실행된다
            Log.log("(3) 대기 Thread 가 interrupt 됨")

            // 실무 코드라면 여기서 Thread.currentThread().interrupt() 로 플래그를 복구해
            // "중단 요청이 있었다" 를 호출자에게 알리는 것이 맞다.
            //
            // 하지만 플래그를 세운 채로 두면 뒤따르는 blocking 호출
            // (join / sleep / awaitTermination) 이 전부 즉시 InterruptedException 을 던진다.
            // 예제를 끝까지 진행시키기 위해 여기서는 플래그를 읽고 지운다.
            //
            //   Thread.interrupted()                 : static. 플래그를 읽고 "지운다"
            //   Thread.currentThread().isInterrupted : 읽기만 한다
            Thread.interrupted()
        }

        // interrupt 는 "기다리던 쪽" 만 풀어 준 것이다.
        // 작업 자체는 아무 영향도 받지 않고 계속 돌고 있다.
        Log.log("(3) 작업은 아직 진행 중 - isDone=${slow.isDone}")

        interrupter.join()

        sleepMillis(2_000) // 남은 작업 정리 대기
        Log.log("(3) 작업 완료 - isDone=${slow.isDone}, 결과=${slow.join()}")
    } finally {
        // 종료 로직은 방어해야 한다.
        // finally 안에서 예외가 나면 try 에서 발생한 진짜 예외를 덮어써 버린다.
        // (ExecutorService javadoc 의 shutdownAndAwaitTermination 패턴)
        Log.log("A")
        ioExecutor.shutdown()
        try {
            Log.log("B")
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Log.log("C")
            ioExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

fun main() = runEx08_2Interrupt()
