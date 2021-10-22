import ParseLog.Companion.assignPkg2Line
import ParseLog.Companion.parseByPackage
import ParseLog.Companion.parseByTag
import ParseLog.Companion.readOneFile
import ParseLog.Companion.restoreGlobalData
import ParseLog.Companion.restoreLogLineFromFile
import ParseLog.Companion.saveGlobalData
import ParseLog.Companion.saveLogLine2File
import Utils.Companion.debug
import Utils.Companion.info
import Utils.Companion.warn
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess


fun main(args: Array<String>) {

    try {
        TimeDumper(1 * 1000L).start()
        mainImpl(args)
    } catch (e: Exception) {
        e.printStackTrace()
        warn("Exception $e")
    }
}

fun check2(tag: String = "", predicate: Boolean) {
    if (!predicate) {
        warn("$tag >>>>>>>>>>>>>>>>>>>>>>>> check2 failed")
        Exception("here").printStackTrace()
//        throw java.lang.Exception()
    }
}

class TimeDumper(private val intervalMs: Long) {
    private var job: Job? = null
    fun start() {
        job?.cancel()
        val start = System.currentTimeMillis()
        job = GlobalScope.launch {
            while (true) {
                delay(intervalMs)
                info("------------------- ${(System.currentTimeMillis() - start) / 1000}s")
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}

private fun mainImpl(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments at Run/Debug configuration
    println("Program arguments: ${args.joinToString()}")
    val tracker = TimeTracker("Main")
    if (args.isNotEmpty()) {
        val fileList = mutableListOf<String>()
        var nextFile = false
        var nextDir = false
        var nextLevel = false
        var fromCache = false
        var nextPkg = false
        var nextTag = false
        var save2File = false

        var dumpAllPkg = false
        var dumpAllTag = false

        var dumpSbp = false
        var dumpSbt = false
        val filterPkg = mutableSetOf<String>()
        val filterTag = mutableSetOf<String>()
        args.forEach {
            debug("arg $it")
            when (it) {
                "-h" -> {
                    info("-h Help")
                    info("-D Print debug log")
                    info(
                        "-l <Int> Min filter log level. Default is [${GlobalFilterLogLevel.level}].${
                            LogLevel.values().map { "${it.tag} ${it.level}" }
                        }"
                    )
                    info("-d <String> Log dir")
                    info("-f <String> Log file")
                    info("-j Enable multi job")
                    info("-s Save cache data to file.")

                    info("-c Enable cache mode")
                    info("\n")
                    info("--pkg <String> Print the pkg detail.")
                    info("--tag <String> Print the tag detail.")
                    info("--allpkg Print all package detail.")
                    info("--alltag Print all tag detail.")
                    info("--sbp Print package summary ")
                    info("--sbt Print tag summary")
                    tracker.dump()
                    exitProcess(0)
                }
                "-D" -> debug = true
                "-l" -> nextLevel = true
                "-f" -> nextFile = true
                "-d" -> nextDir = true
                "-c" -> fromCache = true
                "-j" -> GlobalUseMultiJob = true
                "--pkg" -> nextPkg = true
                "--tag" -> nextTag = true
                "-s" -> save2File = true
                "--allpkg" -> dumpAllPkg = true
                "--alltag" -> dumpAllTag = true
                "--sbp" -> dumpSbp = true
                "--sbt" -> dumpSbt = true
                else -> {
                    if (nextPkg) {
                        filterPkg.add(it)
                        nextPkg = false
                    }
                    if (nextTag) {
                        filterTag.add(it)
                        nextTag = false
                    }
                    if (nextFile)
                        fileList.add(it)
                    else if (nextDir) {
                        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                        File(it).listFiles().map { it.path }.filter { it.endsWith(".log") }.also {
                            debug(it.toString())
                        }.let { fileList.addAll(it) }
                    } else if (nextLevel) {
                        runCatching {
                            it.toInt().toLogLevel().let { GlobalFilterLogLevel = it }
                        }.takeIf { it.isFailure }.let {
                            warn("bad args. pls check2 args")
                            tracker.dump()
                            exitProcess(-1)
                        }
                    }

                    nextFile = false
                    nextDir = false
                    nextLevel = false
                }
            }
        }

        info("filelist size ${fileList.size}")

        val resultList = mutableListOf<LogLine>()
        if (fromCache) {
            restoreGlobalData()
            resultList.addAll(restoreLogLineFromFile())
        } else {
            fileList.takeIf { it.isNotEmpty() }?.let { l ->
                // check2 result can write
                if (!createNewDir(DIR_PARSE_RESULT)) {
                    warn("LINE_DATA_FILE can not create or write.")
                    tracker.dump()
                    exitProcess(-2)
                }

                if (GlobalUseMultiJob) {
                    val suggestJobs = (l.size / 3).coerceAtMost(64)
                    MultiJob(suggestJobs).runJobs(data = l) {
                        it.forEach { f ->
                            val fName = File(f).name
                            readOneFile(f).let {
                                synchronized(resultList) {
                                    val size = it.size
                                    debug("file $fName lines $size , now result size ${resultList.size}")
                                    resultList.addAll(it)
                                }
                            }
                        }
                    }
                } else {
                    l.forEach { f ->
                        val fName = File(f).name
                        debug("parse file $fName")
                        readOneFile(f).let {
                            debug("file $fName lines ${it.size}")
                            resultList.addAll(it)
                        }
                    }
                }
                info("log line size ${resultList.size}")
                if (save2File) {
                    saveGlobalData()
                    saveLogLine2File(resultList)
                }
            }
        }
        // next output
        if (dumpAllPkg) {
            assignPkg2Line(resultList)
            val timer = TimeTracker("dump all package")
            resultList.map { it.pkg }.flatten().toSet()
//                .filter { it != DEFAULT_PKG }
                .forEach {
                    parseByPackage(it, resultList)
                }
            timer.dump()
        } else {
            info("filterPkg is $filterPkg")
            if (filterPkg.isNotEmpty()) {
                filterPkg.forEach { parseByPackage(it, resultList) }
            }
        }

        if (dumpSbp) ParseLog.dumpPkgSummary(resultList)

        if (dumpAllTag) {
            assignPkg2Line(resultList)
            resultList.groupBy { it.tag }.forEach {
                parseByTag(it.key, it.value)
            }
        } else {
            info("filterTag is $filterTag")
            if (filterTag.isNotEmpty()) {
                filterTag.forEach { parseByTag(it, resultList) }
            }
        }

        if (dumpSbp) ParseLog.dumpTagSummary(resultList)
    }
    tracker.dump()
}

fun List<*>.dump(): String {
    return this.map { it.toString() }.toString()
}