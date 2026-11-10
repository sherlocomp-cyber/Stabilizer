package id.sherlocomp.stabilizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val IZIN_DIBUTUHKAN = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_FINE_LOCATION
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StabilizerApp()
                }
            }
        }
    }
}

@Composable
private fun StabilizerApp() {
    val context = LocalContext.current
    val monitor = remember { NetworkMonitor(context) }
    val tester = remember { QualityTester() }

    fun semuaIzinAda(): Boolean = IZIN_DIBUTUHKAN.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var izinOk by remember { mutableStateOf(semuaIzinAda()) }

    // Sinyal dicatat saat hasil uji baru keluar, bukan sinyal yang terus berubah.
    // Pada saat yang sama hasil uji disimpan ke riwayat uji.
    var konteksUji by remember { mutableStateOf<String?>(null) }
    val hasilUji = tester.state.result
    LaunchedEffect(hasilUji) {
        konteksUji = if (hasilUji == null) {
            null
        } else {
            val s = monitor.snapshot
            RiwayatUji.catat(context, hasilUji, s.nr, s.lte)
            konteksSinyal(s.nr, s.lte)
        }
    }

    val peminta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        izinOk = semuaIzinAda()
        monitor.start()
    }

    DisposableEffect(Unit) {
        monitor.start()
        onDispose {
            monitor.stop()
            tester.cancel()
        }
    }

    MonitorScreen(
        snapshot = monitor.snapshot,
        izinLengkap = izinOk,
        onMintaIzin = { peminta.launch(IZIN_DIBUTUHKAN) },
        quality = tester.state,
        onMulaiUji = { tester.start(it) },
        onBatalUji = { tester.cancel() },
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        konteksUji = konteksUji,
        penjaga = {
            GuardCard()
            RiwayatUjiCard()
        }
    )
}
