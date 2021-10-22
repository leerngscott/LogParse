class Utils {
    companion object {
        var debug = false
        fun debug(string: String) {
            if (debug) println(string)
        }

        fun info(string: String) {
            println(string)
        }

        fun warn(string: String) {
            println(string)
        }

        fun <T> averageAssignFixLength(source: List<T>, spiltNumber: Int): List<List<T>> {
            val result = ArrayList<List<T>>()

            debug("averageAssignFixLength spiltNumber $spiltNumber")
            val max = source.size
            val itemSize = max / spiltNumber
            val yushu = max % spiltNumber
            for (index in 0 until spiltNumber) {
                val start = itemSize * index
                val end = if (index == (spiltNumber - 1)) max else start + itemSize
                source.subList(start, end).let { result.add(it) }
            }
            debug("result size ${result.size}")
            return result
        }

    }
}