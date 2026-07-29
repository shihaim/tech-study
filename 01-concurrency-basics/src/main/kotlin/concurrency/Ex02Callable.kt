package concurrency

import java.util.concurrent.Callable
import java.util.concurrent.Future

/*
 * ============================================================================
 * Ex02. Callable<T> - 값을 반환하는 작업
 * ============================================================================
 *
 * Java:
 *     @FunctionalInterface
 *     public interface Callable<V> {
 *         V call() throws Exception;
 *     }
 *
 *  구분              | Runnable          | Callable<T>
 *  ------------------|-------------------|------------------
 *  메서드            | run()             | call()
 *  반환값            | void              | T
 *  Checked Exception | 선언 불가         | 선언 가능
 *  일반적인 제출     | execute/submit    | submit
 *
 * Runnable 만 보면 Future<T> 를 이해하기 어렵다. 돌려받을 결과가 없기 때문이다.
 */
fun runEx02Callable() {
    section("Ex02. Callable<T> - 값을 반환하는 작업")

    // Java:   Callable<Integer> task = () -> { Thread.sleep(300); return 42; };
    // Kotlin: 타입을 명시해 두면 람다의 마지막 식이 반환값이 된다 (return 키워드 불필요).
    val task: Callable<Int> = Callable {
        sleepMillis(300)
        42
    }

    // call() 도 결국 평범한 메서드다. 직접 부르면 현재 Thread 에서 동기 실행된다.
    Log.log("task.call() 직접 호출 → ${task.call()}")

    namedFixedPool("io", 2).use { executor ->

        // ---------------------------------------------------------------
        // ★ Kotlin 에서 주의할 점: submit() 의 오버로드 모호성
        // ---------------------------------------------------------------
        // ExecutorService 에는 다음 오버로드가 있다.
        //     submit(Callable<T>) : Future<T>
        //     submit(Runnable)    : Future<?>
        //     submit(Runnable, T) : Future<T>
        //
        // Kotlin 에서 `executor.submit { ... }` 처럼 람다만 넘기면
        // Runnable 로 변환될지 Callable 로 변환될지 헷갈리는 상황이 생긴다.
        // Java 처럼 "값을 반환하니 당연히 Callable" 이라고 기대하면 어긋날 수 있으므로
        // SAM 생성자를 명시적으로 써 주는 습관을 들이는 게 안전하다.
        val future: Future<Int> = executor.submit(Callable { sleepMillis(300); 42 })
        Log.log("submit(Callable) 결과 = ${future.get()}")

        // Runnable 을 submit 하면 반환값이 없으므로 완료 결과는 null 이다.
        val voidFuture: Future<*> = executor.submit(Runnable { Log.log("Runnable 실행") })
        Log.log("submit(Runnable) 결과 = ${voidFuture.get()}") // null

        // submit(Runnable, result) 를 쓰면 완료 시 돌려받을 값을 지정할 수 있다.
        val fixed: Future<String> = executor.submit(Runnable { Log.log("작업 수행") }, "완료")
        Log.log("submit(Runnable, \"완료\") 결과 = ${fixed.get()}")

        // ---------------------------------------------------------------
        // Checked Exception 이야기
        // ---------------------------------------------------------------
        // Java 에서는 Runnable.run() 안에서 Checked Exception 을 던질 수 없어서
        // try-catch 로 감싸는 코드가 늘 붙는다. Callable.call() 은 throws Exception 이라 가능.
        // Kotlin 에는 Checked Exception 개념 자체가 없어 둘 다 그냥 던질 수 있지만,
        // "던진 예외가 어디로 가는가" 는 여전히 다르다.
        //   - Callable  → Future 에 담겨 get() 호출 시 ExecutionException 으로 전달된다.
        //   - execute() 로 넘긴 Runnable → 아무도 안 받으면 UncaughtExceptionHandler 로 간다.
        val failing: Future<Int> = executor.submit(Callable<Int> {
            throw IllegalArgumentException("작업 내부 실패")
        })

        try {
            failing.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            // 실제 원인은 e.cause 에 들어 있다. 이게 Future 의 예외 전달 방식이다.
            Log.log("ExecutionException 발생, 원인 = ${e.cause}")
        }
    }
}

fun main() = runEx02Callable()
