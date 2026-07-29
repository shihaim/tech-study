package concurrency

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/*
 * 예제 전체에서 공통으로 쓰는 유틸리티와 도메인 모델.
 *
 * 동시성 코드는 "어떤 Thread가 언제 무엇을 했는가"가 핵심이기 때문에
 * 모든 출력에 경과 시간과 Thread 이름을 같이 찍는다.
 */

/**
 * 경과 시간 + Thread 이름을 붙여 출력하는 로거.
 *
 * Java 였다면 `public final class Log { private Log() {} ... }` 같은
 * static utility class 로 만들었을 부분을 Kotlin 에서는 `object` (싱글턴) 로 표현한다.
 */
object Log {

    /** 각 예제가 시작된 시점. section() 에서 초기화한다. */
    @Volatile
    private var origin: Long = System.nanoTime()

    fun reset() {
        origin = System.nanoTime()
    }

    fun log(message: String) {
        val elapsedMs = (System.nanoTime() - origin) / 1_000_000
        // Kotlin 의 String.format 확장 함수. Java 의 String.format(...) 과 동일하다.
        println("[%5d ms] [%-14s] %s".format(elapsedMs, Thread.currentThread().name, message))
    }
}

/** 예제 구분선을 출력하고 경과 시간 기준점을 리셋한다. */
fun section(title: String) {
    println()
    println("=".repeat(78))
    println("  $title")
    println("=".repeat(78))
    Log.reset()
}

/**
 * Thread.sleep 래퍼.
 *
 * Kotlin 에는 Checked Exception 이 없어서 InterruptedException 을
 * try-catch 하지 않아도 컴파일이 된다. 편하지만 위험한 부분이기도 하다.
 * (Java 였다면 컴파일러가 강제로 처리하게 만들었을 예외를 놓치기 쉽다.)
 *
 * 여기서는 interrupt 되면 "interrupt 상태를 복구하고 예외를 그대로 던진다" 는
 * 표준 관례를 따른다.
 */
fun sleepMillis(millis: Long) {
    try {
        Thread.sleep(millis)
    } catch (e: InterruptedException) {
        // interrupt 플래그는 예외가 던져지는 순간 clear 되므로 다시 세워 준다.
        Thread.currentThread().interrupt()
        throw e
    }
}

/**
 * Thread 이름에 접두사를 붙여 주는 ThreadFactory.
 *
 * Java:  new ThreadFactory() { public Thread newThread(Runnable r) { ... } }
 * Kotlin: SAM 생성자 `ThreadFactory { r -> ... }` 로 동일하게 쓸 수 있다.
 *
 * 기본 Executors 는 "pool-1-thread-1" 같은 이름을 붙이는데,
 * 여러 Pool 을 동시에 쓰면 어느 Pool 인지 구분이 안 되므로 직접 이름을 준다.
 */
fun namedThreadFactory(prefix: String): ThreadFactory {
    val counter = AtomicInteger(1)
    return ThreadFactory { runnable ->
        Thread(runnable, "$prefix-${counter.getAndIncrement()}")
    }
}

/** 이름이 붙은 고정 크기 Thread Pool. */
fun namedFixedPool(prefix: String, size: Int): ExecutorService =
    Executors.newFixedThreadPool(size, namedThreadFactory(prefix))

// ---------------------------------------------------------------------------
// 예제용 도메인 모델
// ---------------------------------------------------------------------------

data class User(val id: Long, val name: String, val email: String)

data class Account(val userId: Long, val balance: Long)

data class Report(val title: String, val body: String)

/** DB 조회를 흉내 낸다. 실제로는 Blocking I/O 라고 생각하면 된다. */
fun findUser(id: Long): User {
    Log.log("findUser($id) 시작 - 500ms Blocking")
    sleepMillis(500)
    Log.log("findUser($id) 완료")
    return User(id, "사용자$id", "USER$id@Example.COM")
}

fun findAccount(userId: Long): Account {
    Log.log("findAccount($userId) 시작 - 700ms Blocking")
    sleepMillis(700)
    Log.log("findAccount($userId) 완료")
    return Account(userId, balance = 1_000L * userId)
}

fun createReport(user: User, account: Account): Report {
    Log.log("createReport() - CPU 작업")
    return Report(
        title = "${user.name} 리포트",
        body = "email=${user.email}, balance=${account.balance}"
    )
}

/** 실패하는 외부 API 호출을 흉내 낸다. */
fun requestFailingApi(): String {
    Log.log("외부 API 호출 시작 - 300ms 후 실패")
    sleepMillis(300)
    throw IllegalStateException("외부 API 응답 코드 500")
}
