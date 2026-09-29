package executor

import study.support.Log.log
import study.support.namedThreadFactory

import java.util.concurrent.*

// 기본값: 워커 1개, 큐 16칸, 포화 시 AbortPolicy.
// 예제에서 생략한 설정을 여기서 확인할 수 있다. max를 생략하면 core와 같다.
fun pool(
    name: String,
    core: Int = 1,
    max: Int = core,
    queue: BlockingQueue<Runnable> = ArrayBlockingQueue(16),
    policy: RejectedExecutionHandler = ThreadPoolExecutor.AbortPolicy(),
) = ThreadPoolExecutor(
    core,
    max,
    200, // 유휴 초과 워커가 줄어드는 시간을 실습에서 관찰하기 위한 짧은 keepAliveTime.
    TimeUnit.MILLISECONDS,
    queue,
    namedThreadFactory(name),
    policy,
)

// 상태 출력은 관찰용이다. activeCount와 completedTaskCount는 근사치다.
fun ThreadPoolExecutor.snapshot(label: String) = log(
    "$label: pool=$poolSize, active≈$activeCount, queue=${queue.size}, completed≈$completedTaskCount",
)
