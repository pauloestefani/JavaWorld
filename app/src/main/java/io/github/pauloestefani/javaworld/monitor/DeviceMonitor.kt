package io.github.pauloestefani.javaworld.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import io.github.pauloestefani.javaworld.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File

data class DeviceInfo(
    val model: String,
    val android: String,
    val socName: String,
    val socDetail: String,
    val cores: Int,
    val clusters: String,
    val maxGhz: Float?,
    val abi: String,
    val totalRam: Long,
)

data class CoreSample(val index: Int, val curKhz: Long?, val maxKhz: Long?)

data class Sample(
    val cores: List<CoreSample>,
    val cpuFreqLoad: Float?,
    val ramTotal: Long,
    val ramAvail: Long,
    val lowMemory: Boolean,
    val batteryPct: Int?,
    val batteryTemp: Float?,
    val charging: Boolean,
    val storageFree: Long,
    val storageTotal: Long,
    val serverCpu: Float?,
    val serverRss: Long?,
    val serverThreads: Int?,
    val historyCpu: List<Float>,
    val historyRam: List<Float>,
    val historyServerCpu: List<Float>,
) {
    val ramUsed: Long get() = ramTotal - ramAvail
    val ramFraction: Float get() = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f
}

/** Lê processador, memória, bateria e quanto o servidor está usando. */
class DeviceMonitor(private val app: App) {
    private val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val clkTck: Long = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).takeIf { it > 0 } ?: 100L

    val info: DeviceInfo by lazy { readInfo() }

    val samples: StateFlow<Sample?> = flow {
        val hCpu = ArrayDeque<Float>()
        val hRam = ArrayDeque<Float>()
        val hSrv = ArrayDeque<Float>()
        var lastPid = 0
        var lastTicks = 0L
        var lastTime = 0L
        while (true) {
            val cores = (0 until coreCount).map { i ->
                CoreSample(i, readLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"), maxFreq(i))
            }
            val withData = cores.filter { it.curKhz != null && it.maxKhz != null && it.maxKhz > 0 }
            val freqLoad = if (withData.isEmpty()) null
            else withData.sumOf { it.curKhz!! }.toFloat() / withData.sumOf { it.maxKhz!! }.toFloat()

            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

            val battery = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

            val stat = runCatching { StatFs(app.filesDir.path) }.getOrNull()

            var srvCpu: Float? = null
            var srvRss: Long? = null
            var srvThreads: Int? = null
            val pid = app.servers.info.value.pid
            if (pid > 0) {
                val ticks = procTicks(pid)
                val now = System.nanoTime()
                if (ticks != null) {
                    if (pid == lastPid && lastTime > 0) {
                        val dt = (now - lastTime) / 1e9
                        if (dt > 0) {
                            srvCpu = (((ticks - lastTicks).toDouble() / clkTck) / dt / coreCount).toFloat().coerceIn(0f, 1f)
                        }
                    }
                    lastPid = pid
                    lastTicks = ticks
                    lastTime = now
                }
                val status = runCatching { File("/proc/$pid/status").readLines() }.getOrNull()
                if (status != null) {
                    srvRss = status.firstOrNull { it.startsWith("VmRSS:") }?.let { kb(it) }
                    srvThreads = status.firstOrNull { it.startsWith("Threads:") }?.substringAfter(':')?.trim()?.toIntOrNull()
                }
            } else {
                lastPid = 0
                lastTime = 0
            }

            val ramFrac = if (mi.totalMem > 0) (mi.totalMem - mi.availMem).toFloat() / mi.totalMem else 0f
            push(hCpu, freqLoad ?: 0f)
            push(hRam, ramFrac)
            push(hSrv, srvCpu ?: 0f)

            emit(
                Sample(
                    cores = cores,
                    cpuFreqLoad = freqLoad,
                    ramTotal = mi.totalMem,
                    ramAvail = mi.availMem,
                    lowMemory = mi.lowMemory,
                    batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null,
                    batteryTemp = if (temp != Int.MIN_VALUE) temp / 10f else null,
                    charging = plugged,
                    storageFree = stat?.availableBytes ?: 0,
                    storageTotal = stat?.totalBytes ?: 0,
                    serverCpu = srvCpu,
                    serverRss = srvRss,
                    serverThreads = srvThreads,
                    historyCpu = hCpu.toList(),
                    historyRam = hRam.toList(),
                    historyServerCpu = hSrv.toList(),
                )
            )
            delay(1000)
        }
    }.flowOn(Dispatchers.IO).stateIn(app.scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun push(q: ArrayDeque<Float>, v: Float) {
        q.addLast(v)
        while (q.size > HISTORY) q.removeFirst()
    }

    private val maxCache = mutableMapOf<Int, Long?>()
    private fun maxFreq(i: Int): Long? = maxCache.getOrPut(i) {
        readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
    }

    private fun procTicks(pid: Int): Long? = runCatching {
        val s = File("/proc/$pid/stat").readText()
        val after = s.substring(s.lastIndexOf(')') + 2).split(' ')
        // depois do ")" vem o campo 3 (estado); utime = campo 14, stime = campo 15
        after[11].toLong() + after[12].toLong()
    }.getOrNull()

    private fun kb(line: String): Long? =
        line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()?.times(1024)

    private fun readLong(path: String): Long? = runCatching { File(path).readText().trim().toLong() }.getOrNull()

    private fun readInfo(): DeviceInfo {
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val socModel = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
        val socMaker = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else ""
        val hardware = runCatching {
            File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Hardware") }?.substringAfter(':')?.trim()
        }.getOrNull().orEmpty()
        val raw = listOf(socMaker, socModel).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ")
            .ifBlank { hardware.ifBlank { Build.HARDWARE } }
        val friendly = SocNames.lookup(socModel.ifBlank { hardware.ifBlank { Build.HARDWARE } })
        val maxes = (0 until coreCount).map { maxFreq(it) }
        val groups = maxes.filterNotNull().groupBy { it }.toSortedMap()
        val clusters = if (groups.isEmpty()) "$coreCount núcleos"
        else groups.entries.joinToString(" + ") { (khz, list) -> "${list.size}× %.2f GHz".format(khz / 1_000_000f) }
        return DeviceInfo(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            socName = friendly ?: raw.ifBlank { "Processador" },
            socDetail = if (friendly != null) raw else Build.HARDWARE,
            cores = coreCount,
            clusters = clusters,
            maxGhz = maxes.filterNotNull().maxOrNull()?.let { it / 1_000_000f },
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            totalRam = mi.totalMem,
        )
    }

    /** RAM recomendada para o servidor: deixa folga para o Android e outros apps. */
    fun recommendedRamMb(): Int {
        val totalMb = (info.totalRam / 1048576).toInt()
        val rec = when {
            totalMb >= 11_000 -> 4096
            totalMb >= 7_500 -> 3072
            totalMb >= 5_500 -> 2048
            totalMb >= 3_500 -> 1536
            else -> 1024
        }
        return rec
    }

    fun maxRamMb(): Int {
        val totalMb = (info.totalRam / 1048576).toInt()
        return ((totalMb - 1536) / 256 * 256).coerceIn(512, 12_288)
    }

    companion object {
        const val HISTORY = 60
    }
}

/** Nomes comerciais de alguns processadores comuns. */
object SocNames {
    private val names = mapOf(
        "SM8750" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3",
        "SM8635" to "Snapdragon 8s Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM8250" to "Snapdragon 865",
        "SM8150" to "Snapdragon 855",
        "SDM845" to "Snapdragon 845",
        "SM7550" to "Snapdragon 7 Gen 3",
        "SM7450" to "Snapdragon 7 Gen 1",
        "SM7325" to "Snapdragon 778G",
        "SM7225" to "Snapdragon 750G",
        "SM7150" to "Snapdragon 730",
        "SM6450" to "Snapdragon 6 Gen 1",
        "SM6375" to "Snapdragon 695",
        "SM6225" to "Snapdragon 680",
        "SM6125" to "Snapdragon 665",
        "SDM660" to "Snapdragon 660",
        "SM4450" to "Snapdragon 4 Gen 2",
        "SM4375" to "Snapdragon 4 Gen 1",
        "MT6989" to "MediaTek Dimensity 9300",
        "MT6985" to "MediaTek Dimensity 9200",
        "MT6983" to "MediaTek Dimensity 9000",
        "MT6893" to "MediaTek Dimensity 1200",
        "MT6789" to "MediaTek Helio G99",
        "MT6785" to "MediaTek Helio G90T/G95",
        "S5E9945" to "Samsung Exynos 2400",
        "S5E9925" to "Samsung Exynos 2200",
        "S5E8835" to "Samsung Exynos 1380",
        "S5E8825" to "Samsung Exynos 1280",
        "TENSOR G3" to "Google Tensor G3",
        "TENSOR G4" to "Google Tensor G4",
    )

    fun lookup(model: String): String? {
        val m = model.uppercase().trim()
        if (m.isEmpty()) return null
        names[m]?.let { return it }
        return names.entries.firstOrNull { m.contains(it.key) }?.value
    }
}
