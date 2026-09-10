# 작업 제출과 워커의 실행 흐름

[모듈 안내](../README.md) · [전체 문서 지도](../../docs/README.md)

관련 코드: [FutureTask](../src/main/kotlin/executor/Ex01ExecutorAndFutureTask.kt), [큐와 작업 배치](../src/main/kotlin/executor/Ex02PlacementAndQueues.kt), [거절 정책](../src/main/kotlin/executor/Ex03Rejection.kt).

## submit과 워커

AbstractExecutorService의 기본 submit은 다음 흐름입니다. 하위 구현은 newTaskFor 등을 재정의할 수 있습니다.

```text
Callable → FutureTask 생성 → execute(Runnable) → 워커 또는 큐
워커: FutureTask.run → Callable.call → 결과/예외 저장 → 대기자 깨우기
```

워커의 핵심은 `첫 작업 실행 → 큐에서 다음 작업 획득 → 실행`의 반복입니다.
풀 전체를 재구현하면 상태 전이·경쟁 조건이 학습의 대부분을 차지하므로 실제 풀의 행동을 관찰합니다.

정상 실행 상태의 배치 규칙을 단순화하면 다음과 같습니다.

```text
workerCount < core? → 워커 생성 시도
그 외 → 큐 삽입 시도
삽입 실패 → max 범위 안에서 워커 생성 시도
생성도 실패 → 거절 정책
```

실제 구현은 큐 삽입 뒤 shutdown 여부 재확인과 워커가 0인 경우의 보완 등을 수행합니다.
이 의사 코드만으로 운영용 풀을 구현해서는 안 됩니다.

## Spring으로 연결하기

이후 Spring 학습에서는 메서드 호출을 실행기에 넘기는 경로와 실행기 설정을 연결하면 됩니다.
03의 일반 ThreadLocal 실습은 호출 스레드가 바뀌면 문맥이 달라질 수 있음을 보여줍니다.
실제 트랜잭션 참여나 MDC 전파는 프록시·데코레이터·설정에 따라 달라지므로 이 예제만으로 단정하지 않습니다.

관찰할 값은 워커 활성 수, 큐 길이와 대기 시간, 거절 수, 연결 획득 대기·점유 시간입니다.
CPU 사용률만으로 외부 자원을 기다리는 워커의 여유를 판단하지 않습니다.

문법: [SAM과 Java 연동](../../docs/kotlin/java-interop.md#topic-7).
근거: [ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).
