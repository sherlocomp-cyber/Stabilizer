package id.sherlocomp.stabilizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun angka(nilai: Double?, satuan: String): String =
    if (nilai == null) "-" else String.format(Locale.getDefault(), "%.1f %s", nilai, satuan)

@Composable
fun QualityCard(
    state: QualityState,
    onMulai: (Boolean) -> Unit,
    onBatal: () -> Unit
) {
    var denganUnduh by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Uji kualitas koneksi", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = denganUnduh,
                    onCheckedChange = { denganUnduh = it },
                    enabled = !state.running
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ikut uji kecepatan unduh (memakai hingga sekitar 10 MB kuota)")
            }

            if (state.running) {
                Text(state.progress)
                OutlinedButton(onClick = onBatal) { Text("Batalkan") }
            } else {
                Button(onClick = { onMulai(denganUnduh) }) { Text("Mulai uji") }
            }

            state.result?.let { r ->
                Text(
                    "Penilaian: ${ringkasan(r)}",
                    style = MaterialTheme.typography.titleSmall
                )
                r.hosts.forEach { h ->
                    Text(
                        "${h.host}: rata-rata ${angka(h.avgMs, "ms")}, " +
                            "jitter ${angka(h.jitterMs, "ms")}, gagal ${h.lossPercent}% " +
                            "dari ${h.samples} percobaan"
                    )
                }
                if (r.downloadMbps != null) {
                    Text("Kecepatan unduh: ${angka(r.downloadMbps, "Mbps")}")
                }
                r.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Latency diukur dari waktu membuka koneksi TCP, jadi sedikit lebih " +
                        "tinggi dari ping biasa. Penilaian hanya perkiraan kasar.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
