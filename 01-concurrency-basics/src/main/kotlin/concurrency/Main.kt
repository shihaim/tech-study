package concurrency

/**
 * 예제 목록과 실행기.
 *
 * 예제들과 같은 패키지에 있으므로 import 없이 바로 참조한다.
 *
 * 각 예제 파일에도 main() 이 있으므로 IDE 에서 파일별로 바로 실행할 수 있고,
 * 여기서 번호를 지정해 실행할 수도 있다.
 *
 *   ./gradlew :01-concurrency-basics:run                 → 전체 실행
 *   ./gradlew :01-concurrency-basics:run --args="03 07"  → 3, 7번만 실행
 */
private val examples: Map<String, Pair<String, () -> Unit>> = linkedMapOf(
    "01" to ("Runnable - 작업 자체를 표현한다" to ::runEx01Runnable),
    "02" to ("Callable<T> - 값을 반환하는 작업" to ::runEx02Callable),
    "03" to ("Future<T> - 결과 Handle, 취소와 Timeout" to ::runEx03Future),
    "04" to ("Future 의 한계" to ::runEx04FutureLimits),
    "05" to ("CompletableFuture 기본" to ::runEx05CompletableFutureBasics),
    "06" to ("작업 연결 - thenApply / thenCompose / thenCombine" to ::runEx06Chaining),
    "07" to ("예외 처리 - exceptionally / handle / orTimeout" to ::runEx07ExceptionHandling),
    "08" to ("get() vs join()" to ::runEx08GetVsJoin),
    "09" to ("Blocking vs 진짜 Non-blocking" to ::runEx09NonBlocking),
    "10" to ("Kotlin 에서 주의할 점" to ::runEx10KotlinNotes),
)

fun main(args: Array<String>) {
    val selected = if (args.isEmpty()) examples.keys else args.toList()

    if (args.isEmpty()) {
        println("실행할 예제:")
        examples.forEach { (key, entry) -> println("  $key. ${entry.first}") }
    }

    selected.forEach { key ->
        val entry = examples[key]
        if (entry == null) {
            println("알 수 없는 예제 번호: $key")
            return@forEach
        }
        entry.second.invoke()
    }
}
