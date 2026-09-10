package concurrency

import study.support.Log
import study.support.sleepMillis
import study.support.namedThreadFactory

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
