import Utils.Companion.debug
import Utils.Companion.info
import org.junit.Test

class TestCases {

    @Test
    fun test1() {
//        GlobalPidPkgs.put("3140", listOf("com.fuxi.energyconsumptiongraph", "test"))
//        GlobalPidPkgs.put("2399", listOf("com.fuxi.music:fuxi", "com.android.smspush"))
//        GlobalPidPkgs.put("1151", listOf("dfaadfad", "adfadf"))
//
//        val pids = setOf<String>("1151", "2399")
//        pids.mapNotNull { GlobalPidPkgs.get(it) }.flatten().toSet().let {
//            println("${it.map { it }}")
//        }

//        val t = "MediaPlayer/MediaPlayer@13ca4c3".escape()
        val t = "QCNEJ/CndHalConnector".escape()
        info("t=$t")
        createNewFile("log-parse-result/detail/tag/$t").let {
            check(it)
        }
    }

    @Test
    fun test2() {
        val a = listOf(1, 12, 103, 1000, 12345, 50321, 123456, 1234567)
        a.forEach {
            println(">>>>>>>>>>>>   $it -> ${it.toReadString()}")
        }

    }
}