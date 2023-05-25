import Utils.Companion.debug
import Utils.Companion.info
import Utils.Companion.warn
import com.google.gson.Gson
import java.io.File
import java.math.RoundingMode
import java.text.DecimalFormat

class ParseLog {

    companion object {
        fun readOneFile(path: String): List<LogLine> {
            val f = File(path)
//            val tracker = TimeTracker("readOneFile ${f.name}")
            val result = mutableListOf<LogLine>()
            f.readLines().also {
                debug("${f.name} lines ${it.size}")
            }.forEachIndexed { index, line ->
//                if (index > 10) return@forEachIndexed
//                debug("index=$index")
//                debug(line)
                line.split(' ').filter { it.isNotBlank() }.let {
                   // debug("size=${it.size}")
                    try {
                        if (it.size > 5) {
                            val log = LogLine()
                            log.pid = it[2].trim().toInt()
                            log.ppid = it[3].trim().toInt()
                            log.level = it[4]
                            log.tag = it[5].split(":")[0].trim()
                            if (log.tag.isBlank()) {
                                throw Exception("${f.name} $index $line")
                            }
                            log.size = line.length.toLong()
                            val content = it.subList(6, it.size).joinToString(separator = " ") { it }
                            // debug("log.tag ${log.tag}")
                            if (log.tag.contains("am_pss")) {
                               // debug("am_pss $line")
                                try {
                                    content.split(",").takeIf {
                                        // debug("am_pss size ${it.size}")  
                                        it.size == 10
                                    }?.let {
                                        val pkg = it[2]
                                        val pid = it[0].split("[")[1].trim().toInt()
                                        GlobalPkgPids.addDao(pkg, pid)
                                        GlobalPidPkgs.addDao(pid, pkg)
                                        // debug("pkg $pkg pid $pid")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (log.tag == "ActivityManager") {
                                val pid = log.pid
                                val pkg = "system"
                                GlobalPkgPids.addDao(pkg, pid)
                                GlobalPidPkgs.addDao(pid, pkg)
                            }
                            if (log.level.toLogLevel().level >= GlobalFilterLogLevel.level) {
                                result.add(log)
//                                debug("$path $index ${result.size}")
                            }
                        } else {
//                            warn("$index -- $line")
//                            warn("${f.name} unknown1 line $index")
                        }
                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        warn("Exception $index -- $line")
//                        warn("${f.name} unknown2 line $index")
                    }
                }
            }
//            tracker.dump()
            return result
        }

        fun dumpTagSummary(dataList: List<LogLine>, outFile: String = FILE_TAG_SUMMARY_FILE) {
            try {
                val dumpPid = false
                val output = File(outFile)

                check(createNewFile(file = output))
                output.writeText("Percent \t Size \t package")
                if (dumpPid) output.appendText("\t pids")
                output.appendText("\n")
                output.appendText("Total ${dataList.sumOf { it.size }.toReadString()} \n")
                // summary
                val totalSize = dataList.sumOf { it.size }

                // 给每个元素分配pkg
                assignPkg2Line(dataList)

                // 按Tag把数据分组
                val groups = dataList.groupBy {
                    it.tag
                }.toMutableMap()

                val dump2File = { size: Long, tag: String, pids: String ->
                    val percent = size.toPercent(totalSize)
                    output.appendPercent(percent)
                    output.appendSize(size.toReadString())
                    output.appendPkg(tag)
                    if (dumpPid) output.appendText("\t $pids")
                    output.appendText("\n")
                }


                repeat(groups.size) {
                    groups.maxByOrNull {
                        it.value.sumOf { it.size }
                    }?.let { (tag, loglines) ->
                        groups.remove(tag)
                        val size = loglines.sumOf { it.size }
//                        val pkgs = loglines.map { it.pkg }.flatten().toSet().toList()
                        val pids = loglines.map { it.pid }
                        dump2File(size, tag, pids.joinToString(" "))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun dumpPkgSummary(dataList: List<LogLine>, outFile: String = FILE_PKG_SUMMARY_FILE) {
            val tracker = TimeTracker("dumpPkgSummary")
            try {
                val dumpPid = false
                val output = File(outFile)
                check(createNewFile(file = output))
                assignPkg2Line(dataList)
                output.writeText("Percent Size \t package")
                if (dumpPid) output.appendText("\t pid")
                output.appendText("\n")
                output.appendText("Total ${dataList.sumOf { it.size }.toReadString()} \n")
                // summary
                val totalSize = dataList.sumOf { it.size }
                val totalPkg = dataList.map { it.pkg }.flatten().toSet()

                val dump2File = { size: Long, pkg: String, pids: String ->
                    val percent = size.toPercent(totalSize)
                    output.appendPercent(percent)
                    output.appendSize(size.toReadString())
                    output.appendPkg(pkg)
                    if (dumpPid) output.appendText("\t $pids")
                    output.appendText("\n")
                }
                tracker.dump("before groups")
                // 按pkg把数据分组
                val groups = DAOS<String, LogLine>(outputFilePath = "")
                if (false) {
                    val suggestJobs = (dataList.size / 3).coerceAtMost(64)
                    MultiJob(suggestJobs).runJobs(data = dataList) {
                        it.forEach { item -> item.pkg.forEach { groups.addDao(it, item) } }
                    }
                } else {
                    dataList.forEach { item ->
                        item.pkg.forEach { groups.addDao(it, item) }
                    }
                }

                tracker.dump("after groups")
                info("totalPkg size ${totalPkg.size} ${dataList.size}")
                info("groups size ${groups.data.size} ${groups.data.sumOf { it.value.size }}")
                repeat(totalPkg.size) {
                    groups.data.maxByOrNull { it.value.sumOf { it.size } }?.let {
                        groups.removeDao(it.key)?.let { dump2File(it.value.sumOf { it.size }, it.key, "") }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tracker.dump()
        }


        fun saveLogLine2File(data: List<LogLine>, outFile: String = FILE_LINE_DATA_FILE) {
            val tracker = TimeTracker("saveLogLine2File")
            check(createNewFile(outFile))
            val outFd = File(outFile)
            outFd.writeText("")
            if (!GlobalUseMultiJob) {
                data.forEach {
                    outFd.appendText("${it.toJson()}\n")
                }
            } else {
                MultiJob(16).runJobs(data = data) {
                    it.forEach {
                        // TODO 是否需要加锁?
                        outFd.appendText("${it.toJson()}\n")
                    }
                }
            }
            tracker.dump("end")
        }

        fun saveGlobalData() {
            val tracker = TimeTracker("saveGlobalData")
            GlobalPkgPids.saveFile()
            GlobalPidPkgs.saveFile()
            tracker.dump()
        }

        fun restoreGlobalData() {
            info("Next restore GlobalPkgPids")
            GlobalPkgPids.fromFile()
//            GlobalPkgPids.dump()
            info("Next restore GlobalPidPkgs")
            GlobalPidPkgs.fromFile()
//            GlobalPidPkgs.dump()
        }

        fun restoreLogLineFromFile(outFile: String = FILE_LINE_DATA_FILE): List<LogLine> {
            info("restoreLogLineFromFile")
            val tracker = TimeTracker("restoreLogLineFromFile")
            val fd = File(outFile)
            check(fd.exists())
            val result = mutableListOf<LogLine>()
            fd.also {
                tracker.dump("next readlines")
            }.readLines().also {
                tracker.dump("next map")
            }.let {
                if (GlobalUseMultiJob) {
                    MultiJob(16).runJobs(data = it) {
                        debug("job list size ${it.size}")
                        it.map { GlobalGson.fromJson(it, LogLine::class.java) }
                            .let { synchronized(result) { result.addAll(it) } }
                    }
                } else {
                    it.map { GlobalGson.fromJson(it, LogLine::class.java) }.let {
                        tracker.dump("add before")
                        result.addAll(it)
                    }
                }
            }

            info("restoreLogLineFromFile size ${result.size}")
            tracker.dump("end")
            return result
        }

        fun parseByPackage(pkg: String, data: List<LogLine>) {
            val outPath = "$DIR_PARSE_RESULT_PKG/$pkg"
//            info("parseByPackage $pkg outFile: $outPath")
            val output = File(outPath)
            check(createNewFile(file = output))
            val pids = GlobalPkgPids.data.find {
                it.key == pkg
            }?.value ?: return
            output.appendText("Pids ${pids.joinToString(" ")}\n\n")


            data.filter { it.pid in pids }.let {
                val totalSize = it.sumOf { it.size }
                output.appendText("Total size ${totalSize.toReadString()}\n\n")
                output.appendText("Percent Size Tag \n\n")

                val dump2File = { size: Long, pkg: String ->
                    val percent = size.toPercent(totalSize)
                    output.appendPercent(percent)
                    output.appendSize(size.toReadString())
                    output.appendPkg(pkg)
                    output.appendText("\n")
                }
                it.groupBy { it.tag }.let {
                    // tag logline
                    it.map {
                        // tag Size
                        it.key to it.value.sumOf { it.size }
                    }.sortedByDescending { it.second }.forEach { (tag, size) ->
                        dump2File(size, tag)
                    }
                }
            }
        }

        fun parseByTag(tag: String, data: List<LogLine>) {
            val outPath = "$DIR_PARSE_RESULT_TAG/${tag.escape()}"
            val output = File(outPath)
//            info("parseByTag $tag file:${output.name} path: $outPath")
            check(createNewFile(path = outPath))
            data.filter { it.tag == tag }.let { logline ->
                val totalSize = logline.sumOf { it.size }
                output.appendText("Total size ${totalSize.toReadString()}\n\n")
                output.appendText("Percent Size Pkg\n\n")
                // 按Pkg分组
                assignPkg2Line(logline)

                val dump2File = { size: Long, pkg: String ->
                    val percent = size.toPercent(totalSize)
                    output.appendPercent(percent)
                    output.appendSize(size.toReadString())
                    output.appendPkg(pkg)
                    output.appendText("\n")
                }
                logline.asSequence().map { it.pkg }.flatten().toSet().map { pkg ->
                    pkg to logline.filter { pkg in it.pkg }.sumOf { it.size }
                }.sortedByDescending { it.second }.forEach { (pkg, size) ->
                    dump2File(size, pkg)
                }
            }
        }

        fun assignPkg2Line(data: List<LogLine>) {
            if (isAssignedPkg) return
            val tracker = TimeTracker("assignPkg2Line")
            if (GlobalUseMultiJob) {
                MultiJob(32).runJobs(data = data) {
                    it.forEach {
                        it.pkg.addAll(GlobalPidPkgs.filterDao(it.pid).takeIf { it.isNotEmpty() } ?: setOf(DEFAULT_PKG))
                    }
                }
            } else {
                data.forEach {
                    it.pkg.addAll(GlobalPidPkgs.filterDao(it.pid).takeIf { it.isNotEmpty() } ?: setOf(DEFAULT_PKG))
                }
            }
            isAssignedPkg = true
            MultiJob(32).runJobs(data = data) {
                it.forEach {
                    val pkg = it.pkg
                    val pid = it.pid
                    if (pkg.contains(DEFAULT_PKG)) {
                        GlobalPkgPids.addDao(DEFAULT_PKG, pid)
                        GlobalPidPkgs.addDao(pid, DEFAULT_PKG)
                    }
                }
            }
            tracker.dump()
        }

        private var isAssignedPkg = false
    }
}

fun createNewDir(path: String): Boolean {
    return runCatching {
        val fd = File(path)
        if (!fd.exists()) {
            fd.mkdirs()
        }
    }.isSuccess
}

fun createNewFile(path: String? = null, file: File? = null): Boolean {
    return runCatching {
        val fd = path?.let { File(path) } ?: file!!
        if (!fd.exists()) {
            File(fd.parent).mkdirs()
        }
        fd.writeText("")
    }.also {
        if (it.isFailure) {
            warn("Exception " + it.exceptionOrNull())
            it.exceptionOrNull()?.let { throw it }
        }
    }.isSuccess
}

const val DEFAULT_PKG = "unknown"
var BASE_DIR = "./"
val DIR_PARSE_RESULT: String
    get() = "$BASE_DIR/log-parse-result"
val DIR_PARSE_RESULT_PKG: String
    get() = "$DIR_PARSE_RESULT/detail/pkg"
val DIR_PARSE_RESULT_TAG: String
    get() = "$DIR_PARSE_RESULT/detail/tag"
val DIR_PARSE_RESULT_DATA: String
    get() = "$DIR_PARSE_RESULT/data"

val FILE_LINE_DATA_FILE: String
    get() = "$DIR_PARSE_RESULT_DATA/line-data"
val FILE_TAG_SUMMARY_FILE: String
    get() = "$DIR_PARSE_RESULT/tag-summary"
val FILE_PKG_SUMMARY_FILE: String
    get() = "$DIR_PARSE_RESULT/pkg-summary"

val PID2PKG_FILE: String
    get() = "$DIR_PARSE_RESULT_DATA/pid2pkg"
val PKG2PID_FILE: String
    get() = "$DIR_PARSE_RESULT_DATA/pkg2pid"

var GlobalUseMultiJob = true
var GlobalFilterLogLevel: LogLevel = LogLevel.VER

// key  pkg String
// value pid. Int list
var GlobalPkgPids = DAOS<String, Int>(outputFilePath = PKG2PID_FILE)

// key  pid. Int
// value pkg list. List<String>
var GlobalPidPkgs = DAOS<Int, String>(outputFilePath = PID2PKG_FILE)


const val KB = 1024
const val MB = 1024 * KB
const val GB = 1024 * MB

const val WIDTH_PERCENT = 7
const val WIDTH_SIZE = 15
const val WIDTH_PKG = 50

fun File.appendPercent(percent: String) =
    this.appendText("$percent%\t".fill(WIDTH_PERCENT, rightAlign = true, alignWidth = 7, character = " "))

fun File.appendSize(size: String) =
    this.appendText("$size\t\t".fill(WIDTH_SIZE, rightAlign = true, alignWidth = 15, character = " "))

fun File.appendPkg(pkg: String) = this.appendText(pkg.fill(WIDTH_PKG))

fun String.fill(minWidth: Int, rightAlign: Boolean = false, alignWidth: Int = 0, character: String = " "): String {
    var result = this
    if (rightAlign) {
        repeat((minWidth - this.length).coerceAtLeast(0)) {
            result = character + result
        }
    }
    repeat(minWidth - result.length) {
        result += character
    }
    return result
}

fun Long.toReadString(): String {


    var result = ""
    val per = 3
    val temp = this.toString().reversed()
    val max = temp.length
    var start = 0
    var end = per
    while (end in (start + 1) until max) {
        result += temp.substring(start, end) + ","
//        info("start=$start end=$end result=$result")
        start = end
        end = (end + per).coerceAtMost(max)
    }
    result += temp.substring(start, max)

    return result.reversed()
}

fun Long.toPercent(total: Long): String {
    val format = DecimalFormat("0.##").apply { roundingMode = RoundingMode.FLOOR }
    return format.format(this * 100f / total)
}

fun String.escape(): String =
    this.takeIf { it.contains("/") }?.replace("/", "#") ?: this

fun String.toLogLevel(): LogLevel {
    return LogLevel.values().find { it.tag == this } ?: LogLevel.INFO
}

fun Int.toLogLevel(): LogLevel {
    return LogLevel.values().find { it.level == this } ?: LogLevel.INFO
}

enum class LogLevel(val tag: String, val level: Int) {
    VER("V", 0),
    DEG("D", 1),
    INFO("I", 2),
    WARN("W", 3),
    ERR("E", 4),
}

class ParseItem {
    var tag: String = ""
}


class LogLine {

    var pid: Int = -1
    var ppid: Int = -1
    var level: String = ""
    var tag: String = ""
    var size: Long = 0L
    var timestamp: Long = 0L

    // TODO 如何处理单个PID对应多个Package的问题?
    //  理论每个pid对应一个package,实际可能有多个，原因是重启可能导致应用对应的PID发生变化
    var pkg: MutableSet<String> = mutableSetOf()

    companion object {
        fun fromJson(json: String): LogLine {
            return Gson().fromJson(json, LogLine::class.java)
        }
    }

    fun toJson(): String {
        return Gson().toJson(this, LogLine::class.java)
    }

    override fun toString(): String {
        return "LogLine(pid=$pid, ppid=$ppid, level='$level', tag='$tag', size=$size, timestamp=$timestamp)"
    }
}