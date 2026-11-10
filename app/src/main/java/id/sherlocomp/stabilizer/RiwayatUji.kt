package id.sherlocomp.stabilizer

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Riwayat hasil uji kualitas (terbaru di atas, maksimal 15 baris).
 * Disimpan di SharedPreferences "stabilizer", kunci "riwayat_uji", satu baris teks per hasil.
 * Tujuannya supaya pola antar-menit (sinyal vs latency vs kecepatan) bisa dilihat.
 */
object RiwayatUji {
    private const val PREFS = "stabilizer"
    private const val KUNCI = "riwayat_uji"
    private const val MAKS = 15

    var daftar by mutableStateOf<List<String>>(emptyList())
        private set

    private fun baca(context: Context): List<String> {
        val teks = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KUNCI, null)
        return if (teks.isNullOrEmpty()) emptyList() else teks.split("\n")
    }

    fun muat(context: Context) {
        daftar = baca(context)
    }

    fun hapus(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KUNCI)
            .apply()
        daftar = emptyList()
    }

    /** Mencatat satu hasil uji. Hasil yang dibatalkan atau kosong tidak dicatat. */
    fun catat(context: Context, hasil: QualityResult, nr: SignalMetrics?, lte: SignalMetrics?) {
        if (hasil.note?.startsWith("Dibatalkan") == true) return
        val ukur = hasil.hosts.filter { it.samples > 0 }
        if (ukur.isEmpty()) return

        val jam = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val rata = ukur.mapNotNull { it.avgMs }
        val latency = if (rata.isEmpty()) "-" else "${Math.round(rata.average())} ms"
        val jitterMax = ukur.mapNotNull { it.jitterMs }.maxOrNull()
        val jitter = if (jitterMax == null) "-" else "${Math.round(jitterMax)} ms"
        val loss = ukur.maxOf { it.lossPercent }
        val unduh = hasil.downloadMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "-"
        val sinyal = "5G ${nr?.rsrp ?: "-"}/${nr?.sinr ?: "-"}, 4G ${lte?.rsrp ?: "-"}/${lte?.sinr ?: "-"}"

        val baris = "$jam | ${ringkasan(hasil)} | latency $latency, jitter $jitter, " +
            "gagal $loss% | unduh $unduh | sinyal $sinyal"

        val baru = (listOf(baris) + baca(context)).take(MAKS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KUNCI, baru.joinToString("\n"))
            .apply()
        daftar = baru
    }
}

/** Kartu daftar riwayat hasil uji. Hanya tampil bila sudah ada riwayat. */
@Composable
fun RiwayatUjiCard() {
    val context = LocalContext.current
    val daftar = RiwayatUji.daftar

    LaunchedEffect(Unit) { RiwayatUji.muat(context) }

    if (daftar.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Riwayat uji kualitas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sinyal ditulis RSRP/SINR (dBm/dB). Bandingkan baris-barisnya untuk " +
                    "melihat apakah latency dan kecepatan mengikuti sinyal.",
                style = MaterialTheme.typography.labelSmall
            )
            daftar.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { RiwayatUji.hapus(context) }) {
                Text("Hapus riwayat uji")
            }
        }
    }
}
