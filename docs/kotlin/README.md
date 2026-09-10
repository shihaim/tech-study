# Kotlin 빠른 찾기

[전체 문서 지도](../README.md)

Java 개발자가 이 레포의 코드를 읽다가 막히는 표기를 기준으로 찾습니다.
기존 01 문법 문서의 19개 주제는 아래 세 문서에 보존했고, 02에서 등장한 패턴은 실수·대안 문서에 보충했습니다.

| 표기·질문 | 설명 |
| --- | --- |
| `fun`, `Unit`, `fun f() = ...` | [파일 구조와 함수](basics.md#topic-1) |
| `val`과 `var`, 불변성 | [변수](basics.md#topic-2) |
| `future.isDone`, `e.cause` | [Java 프로퍼티 접근](java-interop.md#topic-3) |
| `$name`, `${...}`, 여러 줄 문자열 | [문자열](basics.md#topic-4) |
| `name = ...`, 기본 인자 | [함수 인자](basics.md#topic-5) |
| `() -> T`, `{ it.name }`, 후행 람다 | [람다](lambdas-and-collections.md#topic-6) |
| `Runnable { }`, `Callable { }` | [SAM과 오버로드](java-interop.md#topic-7) |
| `::runEx01`, `.invoke()` | [함수 참조](lambdas-and-collections.md#topic-8) |
| `T?`, `?.`, `?:`, `T!`, `Void?` | [null과 Java 연동](java-interop.md#topic-9) |
| `data class`, `object` | [클래스](basics.md#topic-10) |
| `val x = if (...)`, `try`의 반환값 | [식](basics.md#topic-11) |
| `when`, `is`와 스마트 캐스트 | [분기](basics.md#topic-12) |
| `map`, `forEach`, `to`, 읽기 전용 컬렉션 | [컬렉션](lambdas-and-collections.md#topic-13) |
| `(key, value)`, `return@forEach` | [구조 분해와 반환](lambdas-and-collections.md#topic-14) |
| `fun ExecutorService.async`, `use` | [확장 함수](lambdas-and-collections.md#topic-15) |
| `<T>`, `Future<*>` | [제네릭](lambdas-and-collections.md#topic-16) |
| `*array`, `toTypedArray()` | [가변인자](lambdas-and-collections.md#topic-17) |
| Checked Exception이 없다면? | [예외](java-interop.md#topic-18) |
| `@Volatile`, `===`, 숫자의 `_` | [기타 표기](basics.md#topic-19) |
| `apply`, `let`, 중첩 `it` | [스코프 함수](idioms-and-pitfalls.md#scope-functions) |
| `check`, `require` | [입력과 상태 검증](idioms-and-pitfalls.md#preconditions) |
| `Result<T>`, `getOrThrow()` | [결과와 실패](idioms-and-pitfalls.md#result) |
| `fun <T> Future<T>.result()` | [공통 확장 함수](idioms-and-pitfalls.md#extensions) |
| `@Synchronized`와 동시성 | [언어 표기와 동시성 계약](idioms-and-pitfalls.md#concurrency) |

## 모듈을 읽다가 돌아올 위치

- 01: SAM·Void·예외가 헷갈리면 [Java 연동](java-interop.md), 함수 연결 문법은 [람다](lambdas-and-collections.md).
- 02: 객체 설정과 검증·Result는 [권장 패턴과 실수](idioms-and-pitfalls.md).
- 공통 모듈: 확장 함수 문법은 [확장 함수](lambdas-and-collections.md#topic-15), API의 시간 제한은 [study-support 계약](../../study-support/README.md).
