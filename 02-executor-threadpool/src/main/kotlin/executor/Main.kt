package executor

import study.support.Log.log

private val examples = linkedMapOf<String, () -> Unit>(
    "01" to ::runEx01, "02" to ::runEx02, "03" to ::runEx03,
    "04" to ::runEx04, "05" to ::runEx05, "06" to ::runEx06, "07" to ::runEx07,
)

fun main(args: Array<String>) {
    val selected = if (args.isEmpty()) examples.keys.toList() else args.toList()
    require(selected.all { it in examples }) { "예제 번호는 01~07입니다: ${args.toList()}" }
    selected.forEach { examples.getValue(it)() }
    log("선택한 예제의 검증 완료")
}
