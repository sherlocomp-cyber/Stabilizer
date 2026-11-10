package id.sherlocomp.stabilizer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Kartu Mode Fokus Aplikasi: pilih satu aplikasi, aplikasi lain diblokir saat aktif. */
@Composable
fun FokusCard() {
    val context = LocalContext.current
    val berjalan = FokusService.berjalan
    val aktifPaket = FokusService.paket
    val catatan = FokusService.catatan
    var pilihan by remember { mutableStateOf(FokusService.muatPilihan(context)) }
    var dialog by remember { mutableStateOf(false) }

    val izinVpn = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasil ->
        val p = pilihan
        if (hasil.resultCode == Activity.RESULT_OK && p != null) {
            FokusService.mulai(context, p.paket)
        }
    }

    fun nyalakan() {
        val p = pilihan ?: return
        val intent = VpnService.prepare(context)
        if (intent != null) {
            izinVpn.launch(intent)
        } else {
            FokusService.mulai(context, p.paket)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Mode Fokus Aplikasi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Saat aktif, semua aplikasi lain TIDAK punya internet (termasuk pesan dan " +
                    "notifikasi), supaya aplikasi pilihanmu tidak berebut bandwidth. Tidak " +
                    "menambah kekuatan sinyal. Matikan lewat saklar ini, tombol Matikan di " +
                    "notifikasi, atau Pengaturan > VPN. Bila Stabilizer ditutup paksa, " +
                    "internet aplikasi lain kembali normal.",
                style = MaterialTheme.typography.bodyMedium
            )

            val teksAplikasi = when {
                berjalan && aktifPaket != null ->
                    "Aplikasi fokus: ${pilihan?.takeIf { it.paket == aktifPaket }?.nama ?: aktifPaket}"
                pilihan != null -> "Aplikasi fokus: ${pilihan?.nama}"
                else -> "Belum ada aplikasi fokus dipilih."
            }
            Text(teksAplikasi, style = MaterialTheme.typography.labelLarge)

            Button(onClick = { dialog = true }, enabled = !berjalan) {
                Text(if (pilihan == null) "Pilih aplikasi" else "Ganti aplikasi")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(
                    checked = berjalan,
                    enabled = berjalan || pilihan != null,
                    onCheckedChange = { nyala ->
                        if (nyala) nyalakan() else FokusService.henti(context)
                    }
                )
                Text(if (berjalan) "Aktif: aplikasi lain diblokir" else "Mati")
            }

            catatan?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                "Bila aplikasi fokus butuh Layanan Google atau aplikasi pendamping lain, " +
                    "sebagian fitur bisa gagal karena aplikasi itu ikut diblokir.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            confirmButton = {
                TextButton(onClick = { dialog = false }) { Text("Tutup") }
            },
            title = { Text("Pilih aplikasi fokus") },
            text = {
                val aplikasi = remember { muatAplikasi(context) }
                if (aplikasi.isEmpty()) {
                    Text("Daftar aplikasi tidak bisa dibaca.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(aplikasi) { a ->
                            Text(
                                a.nama,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        FokusService.simpanPilihan(context, a)
                                        pilihan = a
                                        dialog = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

/** Aplikasi yang punya ikon peluncur, tanpa Stabilizer sendiri, urut nama. */
@Suppress("DEPRECATION")
private fun muatAplikasi(context: Context): List<PilihanFokus> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return try {
        pm.queryIntentActivities(intent, 0)
            .map { PilihanFokus(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.paket != context.packageName }
            .distinctBy { it.paket }
            .sortedBy { it.nama.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }
}
