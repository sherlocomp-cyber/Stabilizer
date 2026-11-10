package id.sherlocomp.stabilizer

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.abs

data class HostResult(
    val host: String,
    val avgMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Int,
    val samples: Int
)

data class QualityResult(
    val hosts: List<HostResult>,
    val downloadMbps: Double?,
    val note: String?
)

data class QualityState(
    val running: Boolean = false,
    val progress: String = "",
    val result: QualityResult? = null
)

/** Ringkasan kasar dari hasil uji. Bukan standar resmi. */
fun ringkasan(result: QualityResult): String {
    val ukur = result.hosts.filter { it.samples > 0 }
    if (ukur.isEmpty()) return "Belum ada data"
    val loss = ukur.maxOf { it.lossPercent }
    val jitter = ukur.mapNotNull { it.jitterMs }.maxOrNull() ?: 0.0
    return when {
        loss >= 20 -> "Sangat buruk (banyak koneksi gagal)"
        loss > 5 || jitter > 40.0 -> "Tidak stabil"
        loss > 1 || jitter > 15.0 -> "Kurang stabil"
        else -> "Stabil"
    }
}

/**
 * Teks kondisi sinyal yang ditampilkan di samping hasil uji, supaya terlihat
 * apakah hasil jelek/bagus bersamaan dengan sinyal lemah atau SINR rendah.
 * Dihitung dari sinyal pada saat uji selesai. Mengembalikan null bila tidak ada data.
 */
fun konteksSinyal(nr: SignalMetrics?, lte: SignalMetrics?): String? {
    val bagian = ArrayList<String>()

    fun tambah(nama: String, m: SignalMetrics?) {
        if (m == null) return
        val rsrp = m.rsrp
        val sinr = m.sinr
        if (rsrp == null && sinr == null) return
        val a = if (rsrp != null) "RSRP $rsrp dBm (${kualitasRsrp(rsrp)})" else "RSRP -"
        val b = if (sinr != null) "SINR $sinr dB (${kualitasSinr(sinr)})" else "SINR -"
        bagian.add("$nama: $a, $b")
    }

    tambah("5G", nr)
    tambah("4G", lte)
    if (bagian.isEmpty()) return null

    val sinrNr = nr?.sinr
    if (sinrNr != null && sinrNr < 0) {
        bagian.add(
            "Catatan: SINR 5G di bawah 0 dB biasanya berarti interferensi atau sel padat. " +
                "Latency dan kecepatan bisa naik-turun walau RSRP tidak buruk."
        )
    }
    return bagian.joinToString("\n")
}

/**
 * Uji kualitas koneksi tanpa root. ICMP ping butuh root, jadi latency diukur dari
 * waktu membuka koneksi TCP ke port 443. "Packet loss" di sini = persen koneksi gagal.
 */
class QualityTester {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelled = false

    var state by mutableStateOf(QualityState())
        private set

    fun start(denganUnduh: Boolean) {
        if (state.running) return
        cancelled = false
        state = QualityState(running = true, progress = "Menyiapkan...")
        executor.execute { jalankan(denganUnduh) }
    }

    fun cancel() {
        cancelled = true
    }

    private fun kirim(s: QualityState) {
        mainHandler.post { state = s }
    }

    private fun jalankan(denganUnduh: Boolean) {
        val hasil = ArrayList<HostResult>()
        for ((i, host) in HOSTS.withIndex()) {
            if (cancelled) break
            kirim(QualityState(true, "Mengukur latency ke $host (${i + 1}/${HOSTS.size})..."))
            hasil += ukurLatency(host)
        }
        var mbps: Double? = null
        var catatan: String? = null
        if (denganUnduh && !cancelled) {
            kirim(QualityState(true, "Mengukur kecepatan unduh..."))
            try {
                mbps = ukurUnduh()
            } catch (e: Exception) {
                catatan = "Uji unduh gagal: ${e.message}"
            }
        }
        if (cancelled) catatan = "Dibatalkan, hasil sebagian."
        kirim(QualityState(false, "", QualityResult(hasil, mbps, catatan)))
    }

    private fun ukurLatency(host: String): HostResult {
        val rtt = ArrayList<Double>()
        var gagal = 0
        for (n in 1..SAMPEL) {
            if (cancelled) break
            val t0 = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(host, 443), TIMEOUT_MS) }
                rtt += (System.nanoTime() - t0) / 1_000_000.0
            } catch (e: Exception) {
                gagal++
            }
            Thread.sleep(100)
        }
        val total = rtt.size + gagal
        var jitter: Double? = null
        if (rtt.size >= 2) {
            var jumlah = 0.0
            for (i in 1 until rtt.size) jumlah += abs(rtt[i] - rtt[i - 1])
            jitter = jumlah / (rtt.size - 1)
        }
        return HostResult(
            host = host,
            avgMs = if (rtt.isEmpty()) null else rtt.average(),
            jitterMs = jitter,
            lossPercent = if (total == 0) 0 else gagal * 100 / total,
            samples = total
        )
    }

    private fun ukurUnduh(): Double {
        val url = URL("https://speed.cloudflare.com/__down?bytes=$BYTE_UNDUH")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            conn.connect()
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val buf = ByteArray(64 * 1024)
            var total = 0L
            val t0 = System.nanoTime()
            conn.inputStream.use { input ->
                while (!cancelled) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (System.nanoTime() - t0 > BATAS_WAKTU_NS) break
                }
            }
            val detik = (System.nanoTime() - t0) / 1e9
            if (total == 0L || detik <= 0.0) throw IOException("tidak ada data")
            return total * 8 / detik / 1_000_000.0
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        val HOSTS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
        const val SAMPEL = 20
        const val TIMEOUT_MS = 2000
        const val BYTE_UNDUH = 10_000_000
        const val BATAS_WAKTU_NS = 8_000_000_000L
    }
}
