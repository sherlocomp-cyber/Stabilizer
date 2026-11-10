package id.sherlocomp.stabilizer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Kartu kontrol penjaga koneksi: saklar layanan, tombol baterai, riwayat putus, tips. */
@Composable
fun GuardCard() {
    val context = LocalContext.current
    val berjalan = StabilizerService.berjalan
    val riwayat = StabilizerService.riwayat

    // Muat riwayat yang tersimpan (bertahan walau aplikasi sempat dimatikan sistem).
    LaunchedEffect(Unit) { StabilizerService.muat(context) }

    val peminta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Layanan tetap jalan walau izin notifikasi ditolak; hanya notifikasinya tidak tampil.
        StabilizerService.mulai(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Penjaga koneksi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Menjaga aplikasi tetap hidup di latar belakang, menampilkan sinyal di " +
                    "notifikasi, dan mencatat saat data seluler putus. Tidak menambah " +
                    "kekuatan sinyal.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(
                    checked = berjalan,
                    onCheckedChange = { nyala ->
                        if (nyala) {
                            val perluIzinNotif = Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            if (perluIzinNotif) {
                                peminta.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                StabilizerService.mulai(context)
                            }
                        } else {
                            StabilizerService.henti(context)
                        }
                    }
                )
                Text(if (berjalan) "Aktif" else "Mati")
            }

            val abaikanBaterai = sudahAbaikanBaterai(context)
            if (abaikanBaterai) {
                Text(
                    "Optimasi baterai: sudah dikecualikan.",
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                Button(onClick = { mintaAbaikanBaterai(context) }) {
                    Text("Izinkan abaikan optimasi baterai")
                }
            }

            Text(
                "Jika layanan sering mati sendiri (tips umum, nama menu bisa berbeda " +
                    "di tiap versi sistem): kunci aplikasi ini di daftar aplikasi terbaru " +
                    "(tahan kartu aplikasi lalu pilih kunci), dan di Pengaturan > Aplikasi > " +
                    "Stabilizer > Baterai izinkan aktivitas latar belakang tanpa pembatasan.",
                style = MaterialTheme.typography.labelSmall
            )

            if (riwayat.isNotEmpty()) {
                Text("Riwayat koneksi seluler", style = MaterialTheme.typography.labelMedium)
                riwayat.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { StabilizerService.hapusRiwayat(context) }) {
                    Text("Hapus riwayat")
                }
            }
        }
    }
}

private fun sudahAbaikanBaterai(context: Context): Boolean = try {
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
} catch (e: Exception) {
    false
}

private fun mintaAbaikanBaterai(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        // Cadangan: buka daftar pengaturan optimasi baterai.
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e2: Exception) {
            // Tidak ada layar yang bisa dibuka di ROM ini.
        }
    }
}
