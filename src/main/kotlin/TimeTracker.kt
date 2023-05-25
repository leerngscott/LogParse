import Utils.Companion.info

class TimeTracker(private val tag: String) {
    var start = 0L

    init {
        start = System.currentTimeMillis()
        info("$tag [start]")
    }

    private fun end(): String = (System.currentTimeMillis() - start).toLong().toReadString()

    fun dump(stage: String = "end") = info("$tag [$stage] Spend time ${end()} ms")
}