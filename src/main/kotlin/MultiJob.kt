import Utils.Companion.averageAssignFixLength
import Utils.Companion.debug
import Utils.Companion.info
import kotlinx.coroutines.*
import java.math.BigDecimal
import kotlin.math.roundToInt

class MultiJob(private var mJobsSize: Int = 8) {

    fun setJobNumber(nums: Int) = nums.also { mJobsSize = it }

    @DelicateCoroutinesApi
    private fun runJobs(numberJobs: Int, job: (jobIndex: Int) -> Unit) {
        runBlocking {
            val jobs = mutableListOf<Job>()
            for (index in 0 until numberJobs) {
                GlobalScope.launch {
//                    info("[${Thread.currentThread().id}] next start job $index")
                    job(index)
                }.let { jobs.add(it) }
            }
            jobs.joinAll()
        }
    }

    fun <T> runJobs(data: List<T>, work: (List<T>) -> Unit) {
        val numberJobs = data.size.coerceAtMost(mJobsSize)
        info("numberJobs $numberJobs")
        val lists = averageAssignFixLength(data, numberJobs)
        debug("data size ${data.size}")
        debug("runJobs size ${lists.sumOf { it.size }}")

        lists.forEachIndexed { index, list ->
            debug("job[$index] size ${list.size}")
        }
        check2("job totalSize",data.size == lists.sumOf { it.size })
        check2("job number",lists.size == numberJobs)
        runJobs(numberJobs) { index ->
            work(lists[index])
        }
    }
}