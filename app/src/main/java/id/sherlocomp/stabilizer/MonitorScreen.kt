package id.sherlocomp.stabilizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(
    snapshot: NetworkSnapshot,
    izinLengkap: Boolean,
    onMintaIzin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Stabilizer", style = MaterialTheme.typography.headlineMedium)

        if (!izinLengkap) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Butuh izin Telepon dan Lokasi untuk membaca sinyal, band, " +
                            "dan channel. Data tidak dikirim ke mana pun."
                    )
                    Button(onClick = onMintaIzin) { Text("Berikan izin") }
                }
            }
        }

        snapshot.note?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Jaringan", style = MaterialTheme.typography.labelMedium)
                Text(snapshot.networkLabel, style = MaterialTheme.typography.headlineSmall)
                Text("Operator: ${snapshot.operatorName}")
            }
        }

        SignalCard("Sinyal 5G (NR)", snapshot.nr)
        SignalCard("Sinyal 4G (LTE)", snapshot.lte)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Sel terdaftar (band dan channel)",
                    style = MaterialTheme.typography.titleMedium
                )
                if (snapshot.cells.isEmpty()) {
                    Text("Belum ada data")
                } else {
                    snapshot.cells.forEach { c ->
                        Text(
                            "${c.tech}: band ${c.band}, channel ${c.channel ?: "-"}, " +
                                "PCI ${c.pci ?: "-"}"
                        )
                    }
                }
            }
        }

        if (snapshot.updatedAtMs > 0L) {
            val jam = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(snapshot.updatedAtMs))
            Text("Diperbarui $jam", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SignalCard(title: String, metrics: SignalMetrics?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (metrics == null) {
                Text("Tidak terdeteksi")
            } else {
                val rsrp = metrics.rsrp
                val rsrq = metrics.rsrq
                val sinr = metrics.sinr
                Text("RSRP: " + if (rsrp != null) "$rsrp dBm (${kualitasRsrp(rsrp)})" else "-")
                Text("RSRQ: " + if (rsrq != null) "$rsrq dB" else "-")
                Text("SINR: " + if (sinr != null) "$sinr dB (${kualitasSinr(sinr)})" else "-")
                Text("Level Android: ${metrics.level ?: "-"} dari 4")
            }
        }
    }
}
