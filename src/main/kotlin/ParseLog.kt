import Utils.Companion.debug
import Utils.Companion.info
import Utils.Companion.warn
import com.google.gson.Gson
import java.io.File

import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.Exception

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
//                    debug("size=${it.size}")
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
                            log.size = line.length
                            val content = it.subList(6, it.size).joinToString(separator = " ") { it }
                            if (log.tag == "am_pss") {
//                                debug("am_pss $line")
                                try {
                                    content.split(",").takeIf { it.size == 6 }?.let {
                                        val pkg = it[2]
                                        val pid = it[0].split("[")[1].trim().toInt()
                                        GlobalPkgPids.addDao(pkg, pid)
                                        GlobalPidPkgs.addDao(pid, pkg)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            if (log.level.toLogLevel().level >= GlobalFilterLogLevel.level) {
                                result.add(log)
                                debug("$path $index ${result.size}")
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
                output.writeText("Size \t tag \t package")
                if (dumpPid) output.appendText("\t pid")
                output.appendText("\n")
                output.appendText("Total ${dataList.sumOf { it.size }.toReadString()} \n")
                // summary
                val totalSize = dataList.sumOf { it.size }
                dataList.groupBy {
                    it.tag
                }.let {
                    // key tag
                    val dataMap = it
                    // except : Size Tag Pkgs Pids
                    dataMap.map { it.key to it.value.sumOf { it.size } }.toMap().let { mapdata ->
                        mapdata.keys.sortedByDescending { it }.forEach {
//                            val values = mapdata[it].let { dataMap[it] }
//                            val pids = values!!.map { it.pid }.toSet()
//                            val tag = mapdata.get()
//                            val percent = it.toPercent(totalSize)
//                            val size = it.toReadString()
//                            output.appendText("$percent \t $size \t $tag")
//                            if (dumpPid) output.appendText("\t $pids")
//                            output.appendText("\n")
                        }
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
                assignPkg2Line(dataList)
                output.writeText("Percent Size \t package")
                if (dumpPid) output.appendText("\t pid")
                output.appendText("\n")
                output.appendText("Total ${dataList.sumOf { it.size }.toReadString()} \n")
                // summary
                val totalSize = dataList.sumOf { it.size }
                val totalPkg = dataList.map { it.pkg }.flatten().toSet()

                val dump2File = { size: Int, pkg: String, pids: String ->
                    val percent = size.toPercent(totalSize)
                    output.appendPercent(percent)
                    output.appendSize(size.toReadString())
                    output.appendPkg(pkg)
                    if (dumpPid) output.appendText("\t $pids")
                    output.appendText("\n")
                }
                // 按pkg把数据分组
                val groups = DAOS<String, LogLine>(outputFilePath = "")
                dataList.forEach { item ->
                    item.pkg.forEach { groups.addDao(it, item) }
                }

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
            info("parseByPackage $pkg outFile: $outPath")
            val outFd = File(outPath)
            check(createNewFile(file = outFd))
            val pids = GlobalPkgPids.data.find {
                it.key.split(":").first() == pkg
            }?.value ?: return;
            outFd.appendText("Pids ${pids.joinToString(" ")}\n\n")
            data.filter { it.pid in pids }.let {
                val totalSize = it.sumOf { it.size }
                outFd.appendText("Total size ${totalSize.toReadString()}\n\n")
                outFd.appendText("Percent Size Tag Pids\n\n")
                it.groupBy { it.tag }.let {
                    // tag logline
                    it.map {
                        // tag Size
                        it.key to it.value.sumOf { it.size }
                    }.sortedByDescending { it.second }.joinToString("\n") {
                        "${it.second.toPercent(totalSize)}% \t ${it.second.toReadString()} \t ${it.first}"
                    }.let {
                        outFd.appendText(it)
                    }
                }
            }
        }

        fun parseByTag(tag: String, data: List<LogLine>) {
            val outPath = "$DIR_PARSE_RESULT_TAG/${tag.escape()}"
            val outFd = File(outPath)
            info("parseByTag $tag file:${outFd.name} path: $outPath")
            check(createNewFile(path = outPath))
            data.filter { it.tag == tag }.let { logline ->
                val totalSize = logline.sumOf { it.size }
                outFd.appendText("Total size ${totalSize.toReadString()}\n\n")
                outFd.appendText("Percent Size Pkg pids\n\n")
                // 按Pkg分组
                assignPkg2Line(logline)
                logline.asSequence().map { it.pkg }.flatten().toSet().map { pkg ->
                    pkg to logline.filter { pkg in it.pkg }.sumOf { it.size }
                }.sortedByDescending { it.second }.joinToString("\n") {
                    // percent size pkg
                    val percent = it.second.toPercent(totalSize)
                    val size = it.second
                    val pkg = it.first
                    "$percent% \t $size \t $pkg"
                }.let { outFd.appendText(it) }
            }
        }

        fun assignPkg2Line(data: List<LogLine>) {
            if (isAssignedPkg) return
            data.forEach {
                it.pkg.addAll(GlobalPidPkgs.filterDao(it.pid).takeIf { it.isNotEmpty() } ?: setOf(DEFAULT_PKG))
            }
            isAssignedPkg = true
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
const val DIR_PARSE_RESULT = "log-parse-result"
const val DIR_PARSE_RESULT_PKG = "$DIR_PARSE_RESULT/detail/pkg"
const val DIR_PARSE_RESULT_TAG = "$DIR_PARSE_RESULT/detail/tag"
const val DIR_PARSE_RESULT_DATA = "$DIR_PARSE_RESULT/data"

const val FILE_LINE_DATA_FILE: String = "$DIR_PARSE_RESULT_DATA/line-data"
const val FILE_TAG_SUMMARY_FILE = "$DIR_PARSE_RESULT/tag-summary"
const val FILE_PKG_SUMMARY_FILE = "$DIR_PARSE_RESULT/pkg-summary"

const val PID2PKG_FILE: String = "$DIR_PARSE_RESULT_DATA/pid2pkg"
const val PKG2PID_FILE: String = "$DIR_PARSE_RESULT_DATA/pkg2pid"

var GlobalUseMultiJob = false
var GlobalFilterLogLevel: LogLevel = LogLevel.INFO

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

fun Int.toReadString(): String {


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

fun Int.toPercent(total: Int): String {
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
    var size: Int = 0
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