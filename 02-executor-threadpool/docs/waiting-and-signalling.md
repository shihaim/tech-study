# 결과 공개와 대기자 깨우기

[모듈 안내](../README.md) · [전체 문서 지도](../../docs/README.md)

관련 코드: [MiniResult](../src/main/kotlin/executor/Ex07MiniResult.kt).
Kotlin 표기는 [Result와 실패 전달](../../docs/kotlin/idioms-and-pitfalls.md#result), [동시성 어노테이션](../../docs/kotlin/idioms-and-pitfalls.md#concurrency)을 참고하세요.

## MiniResult의 경쟁 조건

1. get이 대기 스레드를 먼저 등록합니다.
2. 완료 상태를 읽고, 미완료라면 parkNanos로 기다립니다.
3. complete는 결과를 volatile로 공개한 다음 등록된 스레드를 unpark합니다.
4. get은 깨어난 뒤 완료·interrupt·남은 시간을 다시 확인합니다.

완료가 등록보다 먼저면 get은 이미 공개된 결과를 읽습니다.
조건 확인과 park 사이에 완료되면 unpark의 permit으로 대기가 풀립니다.
park 이후 완료되면 대기자가 깨어납니다. 결과의 가시성은 volatile이 담당합니다.
permit은 스레드별 최대 하나이며 누적되지 않습니다. 중간에 다른 park 기반 동기화를 끼우면 permit이 소비될 수 있어 대기 루프를 작게 유지합니다.
MiniResult는 완료 실패를 원래 예외로 전달합니다. ExecutionException으로 감싸는 FutureTask와 계약이 다릅니다.

단일 완료·단일 get만 지원하는 학습용 구현이며, 취소나 다중 대기자는 지원하지 않습니다.
근거: [LockSupport API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/LockSupport.html).
