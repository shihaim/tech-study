package concurrency

import java.util.concurrent.Executors
import kotlin.concurrent.thread

/*
 * ============================================================================
 * Ex01. Runnable - "무엇을 실행할 것인가" 만 표현한다
 * ============================================================================
 *
 * Java:
 *     @FunctionalInterface
 *     public interface Runnable {
 *         void run();
 *     }
 *
 * 핵심:
 *     Runnable        = 무엇을 실행할 것인가 (작업 지시서)
 *     Thread/Executor = 어디서 어떻게 실행할 것인가
 *
 * Runnable 을 만들었다고 해서 비동기가 되는 게 아니다.
 * 누가 run() 을 호출하느냐에 따라 동기가 되기도 하고 비동기가 되기도 한다.
 */
fun runEx01Runnable() {
    section("Ex01. Runnable - 작업 자체를 표현한다")

    // Java:   Runnable task = () -> System.out.println("작업 실행");
    // Kotlin: SAM 생성자 `Runnable { ... }` 를 쓴다.
    //
    // 주의: Kotlin 의 `{ ... }` 는 그 자체로 Runnable 이 아니라 `() -> Unit` 함수 타입이다.
    //       Java 인터페이스가 필요한 자리에 넘길 때만 자동 변환(SAM conversion)된다.
    //       변수에 담아 재사용할 때는 이렇게 타입을 명시하는 편이 안전하다.
    val task = Runnable {
        Log.log("작업 실행")
    }

    // -----------------------------------------------------------------------
    // (1) 직접 run() 호출 → 그냥 평범한 메서드 호출. 현재 Thread 에서 동기 실행된다.
    // -----------------------------------------------------------------------
    Log.log("(1) task.run() 직접 호출 - 비동기가 아니다")
    task.run()

    // -----------------------------------------------------------------------
    // (2) 새로운 Thread 에서 실행
    //     start() 가 JVM/OS 에 새 실행 흐름을 요청하고, 그 Thread 가 나중에 run() 을 호출한다.
    //     (thread.run() 을 직접 부르면 새 Thread 가 아니라 현재 Thread 에서 실행되니 주의)
    // -----------------------------------------------------------------------
    Log.log("(2) Thread.start()")
    val worker = Thread(task, "worker-thread")
    worker.start()
    worker.join() // 데모 출력 순서를 맞추기 위해 대기

    // -----------------------------------------------------------------------
    // (2-1) Kotlin 표준 라이브러리의 thread { } 헬퍼
    //       내부적으로 Thread 를 만들고 기본적으로 바로 start() 까지 해 준다.
    // -----------------------------------------------------------------------
    val kotlinThread = thread(name = "kotlin-thread") {
        Log.log("kotlin.concurrent.thread { } 로 실행")
    }
    kotlinThread.join()

    // -----------------------------------------------------------------------
    // (3) Thread Pool 에서 실행
    //     execute(Runnable) 은 결과를 돌려주지 않는다. "던져 놓고 잊는" API.
    // -----------------------------------------------------------------------
    Log.log("(3) ExecutorService.execute()")

    // Java 19+ 부터 ExecutorService 가 AutoCloseable 이라 Kotlin 의 use { } 로 닫을 수 있다.
    // close() 는 shutdown() 후 종료될 때까지 기다린다.
    // (이걸 안 하면 non-daemon Pool Thread 때문에 JVM 이 종료되지 않는다.)
    Executors.newFixedThreadPool(2, namedThreadFactory("pool")).use { executor ->
        executor.execute(task)
        executor.execute { Log.log("람다를 바로 넘기면 SAM 변환이 일어난다") }
    }

    println(
        """
        |
        |정리)
        |  - run() 직접 호출  → 호출한 Thread 에서 동기 실행
        |  - Thread.start()   → 새 Thread 에서 실행
        |  - executor.execute → Pool 의 Worker Thread 에서 실행
        |  Runnable 은 어디서 실행될지 전혀 모른다. 그건 실행 주체가 정한다.
        """.trimMargin()
    )
}

fun main() = runEx01Runnable()
