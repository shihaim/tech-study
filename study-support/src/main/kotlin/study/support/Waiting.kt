package study.support

import java.util.concurrent.*

// 실습 실패 시 무한 대기로 남지 않도록 모든 조율에 상한을 둔다.
fun CountDownLatch.awaitChecked() = check(await(10, TimeUnit.SECONDS)) { "신호 대기 시간 초과" }
fun <T> Future<T>.result(): T = get(10, TimeUnit.SECONDS)
fun eventually(description: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (!condition()) {
        check(System.nanoTime() < deadline) { description }
        Thread.sleep(5)
    }
}

// use/close의 무기한 대기 대신 실패 경로에서도 제한 시간 내 정리를 시도한다.
fun ExecutorService.stop() {
    shutdown()
    try {
        if (!awaitTermination(10, TimeUnit.SECONDS)) {
            shutdownNow()
            check(awaitTermination(10, TimeUnit.SECONDS)) { "풀 종료 시간 초과" }
        }
    } catch (e: InterruptedException) {
        shutdownNow()
        Thread.currentThread().interrupt()
        throw e
    }
}
