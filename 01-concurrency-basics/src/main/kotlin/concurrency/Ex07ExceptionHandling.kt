package concurrency

import study.support.Log
import study.support.section
import study.support.sleepMillis

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

/*
 * ============================================================================
 * Ex07. 예외 처리 - exceptionally / whenComplete / handle / orTimeout
 * ============================================================================
 *
 * Future 는 예외 처리 파이프라인이 없어서 get() 을 try-catch 로 감싸는 수밖에 없었다.
 * CompletableFuture 는 실패도 흐름의 일부로 다룬다.
 *
 * ★ 중요: 작업 안에서 던진 예외는 그대로 전달되지 않고 CompletionException 으로 감싸진다.
 *         실제 원인을 보려면 cause 를 확인해야 한다.
 *         단, completeExceptionally() 나 orTimeout() 처럼 "바깥에서 실패시킨" 경우에는
 *         감싸지 않고 그대로 전달된다. (5)번 예제에서 직접 확인한다.
 */
fun runEx07ExceptionHandling() {
    section("Ex07. CompletableFuture 예외 처리")

    val ioExecutor = namedFixedPool("io", 4)

    try {
        // -------------------------------------------------------------------
        // (1) 예외가 발생하면 뒤따르는 thenApply 들은 전부 건너뛴다
        // -------------------------------------------------------------------
        Log.log("(1) 중간에 실패하면 이후 단계는 실행되지 않는다")
        val skipped = CompletableFuture
            .supplyAsync({ requestFailingApi() }, ioExecutor)
            .thenApply { value -> Log.log("이 줄은 실행되지 않는다"); value.uppercase() }
            .exceptionally { ex ->
                // ex 는 CompletionException 이고, 진짜 원인은 ex.cause 에 있다.
                Log.log("(1) exceptionally - ${ex.javaClass.simpleName} / 원인=${ex.cause?.message}")
                "fallback"
            }
        Log.log("(1) 결과 = ${skipped.join()}")

        // -------------------------------------------------------------------
        // (2) exceptionally - 실패를 "정상값" 으로 복구한다
        //     복구되면 체인은 다시 성공 경로로 이어진다.
        // -------------------------------------------------------------------
        val recovered = CompletableFuture
            .supplyAsync({ requestFailingApi() }, ioExecutor)
            .exceptionally { "default" }
            .thenApply { value -> "복구 후 이어지는 단계: $value" }
        Log.log("(2) exceptionally 결과 = ${recovered.join()}")

        // -------------------------------------------------------------------
        // (3) whenComplete - 성공/실패를 "관측" 만 한다 (결과를 바꾸지 않는다)
        //     로깅, 메트릭, 자원 정리에 적합하다. Java 의 try-finally 느낌.
        // -------------------------------------------------------------------
        val observed = CompletableFuture
            .supplyAsync({ findUser(1) }, ioExecutor)
            .whenComplete { result, exception ->
                if (exception != null) {
                    Log.log("(3) 실패: ${exception.message}")
                } else {
                    Log.log("(3) 성공: ${result.name}")
                }
            }
        Log.log("(3) whenComplete 는 원래 결과를 그대로 흘려보낸다 → ${observed.join().name}")

        // whenComplete 안에서 값을 return 해도 결과가 바뀌지 않는다는 점이 handle 과의 차이다.
        // (실패한 Future 에 whenComplete 를 걸어도 여전히 실패 상태로 남는다)

        // -------------------------------------------------------------------
        // (4) handle - 성공/실패를 모두 받아 "새로운 결과" 로 변환한다
        // -------------------------------------------------------------------
        val handled: CompletableFuture<String> = CompletableFuture
            .supplyAsync({ requestFailingApi() }, ioExecutor)
            .handle { result, exception ->
                if (exception != null) "fallback(handle)" else result
            }
        Log.log("(4) handle 결과 = ${handled.join()}")

        // 정리
        //   exceptionally : 실패일 때만 호출, 정상값으로 복구
        //   whenComplete  : 항상 호출, 결과를 바꾸지 않음 (관측용)
        //   handle        : 항상 호출, 결과를 바꿈 (변환용)

        // -------------------------------------------------------------------
        // (5) orTimeout / completeOnTimeout (Java 9+)
        // -------------------------------------------------------------------
        // orTimeout        : 시간 내 완료되지 않으면 TimeoutException 으로 실패시킨다
        // completeOnTimeout: 시간 내 완료되지 않으면 지정한 기본값으로 완료시킨다
        //
        // 주의: 타임아웃은 "기다리는 쪽" 을 끊을 뿐, 실행 중인 작업을 멈추지는 않는다.
        //       Worker Thread 는 계속 돌고 있다. (Ex03 의 cancel 이야기와 동일한 맥락)
        val timedOut = CompletableFuture
            .supplyAsync({ sleepMillis(3_000); "느린 응답" }, ioExecutor)
            .orTimeout(500, TimeUnit.MILLISECONDS)
            .exceptionally { ex ->
                // (1)번에서는 ex 가 CompletionException 이었지만 여기서는 TimeoutException 자체다.
                // orTimeout 은 completeExceptionally() 로 직접 실패시키기 때문에 감싸지 않는다.
                // → 예외 타입으로 분기할 때는 unwrap 헬퍼를 하나 두는 편이 안전하다.
                Log.log("(5) orTimeout - 받은 예외=${ex.javaClass.simpleName}, cause=${ex.cause?.javaClass?.simpleName}")
                Log.log("(5) unwrap() 결과 = ${unwrap(ex).javaClass.simpleName}")
                "fallback(timeout)"
            }
        Log.log("(5) 결과 = ${timedOut.join()}")

        val defaulted = CompletableFuture
            .supplyAsync({ sleepMillis(3_000); "느린 응답" }, ioExecutor)
            .completeOnTimeout("기본값", 300, TimeUnit.MILLISECONDS)
        Log.log("(5) completeOnTimeout 결과 = ${defaulted.join()}")

        // -------------------------------------------------------------------
        // (6) 예외를 삼키지 않기
        // -------------------------------------------------------------------
        // 아무 것도 붙이지 않은 CompletableFuture 는 실패해도 조용하다.
        // join()/get() 을 호출하지 않으면 예외가 어디에도 드러나지 않는다.
        // → 체인의 끝에는 항상 exceptionally / handle / whenComplete 중 하나를 두자.
        CompletableFuture.supplyAsync({ requestFailingApi() }, ioExecutor)
        Log.log("(6) 위 Future 는 실패하지만 아무도 모른다 - 조용한 실패에 주의")

        sleepMillis(400)

        // -------------------------------------------------------------------
        // (7) Kotlin 에서의 주의점: CompletionException 언래핑
        // -------------------------------------------------------------------
        try {
            CompletableFuture.supplyAsync({ requestFailingApi() }, ioExecutor).join()
        } catch (e: CompletionException) {
            // 도메인 예외로 분기하려면 cause 를 봐야 한다.
            val root = unwrap(e)
            Log.log("(7) join() 예외 = ${e.javaClass.simpleName}, 원인 = ${root.javaClass.simpleName}")
            when (root) {
                is IllegalStateException -> Log.log("(7) → 외부 API 오류로 분기 처리")
                else -> throw e
            }
        }
    } finally {
        // 타임아웃 데모의 3초짜리 작업이 아직 돌고 있으므로 close() 는 그만큼 기다린다.
        ioExecutor.shutdown()
        ioExecutor.awaitTermination(5, TimeUnit.SECONDS)
    }

    println(
        """
        |
        |정리)
        |  exceptionally(fn)      : 실패 → 정상값 복구
        |  whenComplete(fn)       : 성공/실패 관측, 결과 유지
        |  handle(fn)             : 성공/실패 → 새 결과로 변환
        |  orTimeout / completeOnTimeout : 대기 시간 제한 (작업 자체를 멈추진 않는다)
        |  작업이 던진 예외는 CompletionException 으로 감싸지므로 cause 를 확인할 것
        |  (감싸지 않는 경우도 있으니 unwrap 헬퍼를 두는 편이 안전하다)
        """.trimMargin()
    )
}

/**
 * CompletionException / ExecutionException 껍데기를 벗겨 실제 원인을 꺼낸다.
 *
 * 감싸져 있을 수도, 아닐 수도 있기 때문에 이런 헬퍼를 하나 두고 일관되게 쓰는 편이 안전하다.
 * Kotlin 이라면 확장 함수로 만들어도 좋다.
 */
fun unwrap(throwable: Throwable): Throwable = when (throwable) {
    is CompletionException, is java.util.concurrent.ExecutionException -> throwable.cause ?: throwable
    else -> throwable
}

fun main() = runEx07ExceptionHandling()
