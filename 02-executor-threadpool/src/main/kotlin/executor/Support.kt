package executor

import study.support.Log.log
import study.support.namedThreadFactory

import java.util.concurrent.*

// 풀 정책과 관찰 항목은 이 모듈의 학습 내용이므로 여기에 둔다.
fun pool(
    name: String, core: Int = 1, max: Int = core,
    queue: BlockingQueue<Runnable> = ArrayBlockingQueue(16),
    policy: RejectedExecutionHandler = ThreadPoolExecutor.AbortPolicy(),
) = ThreadPoolExecutor(core, max, 200, TimeUnit.MILLISECONDS, queue, namedThreadFactory(name), policy)

fun ThreadPoolExecutor.snapshot(label: String) = log(
    "$label: pool=$poolSize, active≈$activeCount, queue=${queue.size}, completed≈$completedTaskCount")
