package id.sherlocomp.stabilizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.FileInputStream
import java.io.IOException

/** Aplikasi yang dipilih pengguna sebagai aplikasi fokus. */
data class PilihanFokus(val paket: String, val nama: String)

/**
 * Mode Fokus Aplikasi (versi sederhana: blokir aplikasi lain).
 *
 * Membuat VPN lokal yang menangkap lalu lintas SEMUA aplikasi kecuali aplikasi fokus dan
 * Stabilizer sendiri, lalu membuang semua paket yang masuk ke terowongan. Akibatnya aplikasi
 * lain tidak punya internet selama mode ini aktif, dan aplikasi fokus memakai jalur seluler
 * biasa tanpa rebutan bandwidth. Tidak ada penerusan paket, jadi tidak ada data yang keluar
 * lewat VPN ini. Tidak menambah kekuatan sinyal radio.
 *
 * Pengaman: bila layanan mati atau proses dimatikan, terowongan ikut tertutup dan internet
 * aplikasi lain kembali normal. START_NOT_STICKY, tidak menyala sendiri.
 */
class FokusService : VpnService() {

    companion object {
        private const val AKSI_HENTI = "id.sherlocomp.stabilizer.FOKUS_HENTI"
        private const val EKSTRA_PAKET = "paket"
        private const val CH_FOKUS = "fokus"
        private const val ID_NOTIF = 3
        private const val PREFS = "stabilizer"
        private const val KEY_PAKET = "fokus_paket"
        private const val KEY_NAMA = "fokus_nama"

        /** Dibaca UI untuk status saklar. */
        var berjalan by mutableStateOf(false)
            private set

        /** Paket aplikasi fokus yang sedang aktif, null bila mati. */
        var paket by mutableStateOf<String?>(null)
            private set

        /** Pesan kegagalan terakhir untuk ditampilkan di kartu, null bila tidak ada. */
        var catatan by mutableStateOf<String?>(null)
            private set

        /** Menyalakan mode fokus. Izin VPN harus sudah diberikan (VpnService.prepare). */
        fun mulai(context: Context, paketFokus: String) {
            catatan = null
            context.startService(
                Intent(context, FokusService::class.java).putExtra(EKSTRA_PAKET, paketFokus)
            )
        }

        fun henti(context: Context) {
            context.stopService(Intent(context, FokusService::class.java))
        }

        fun muatPilihan(context: Context): PilihanFokus? {
            val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val p = sp.getString(KEY_PAKET, null) ?: return null
            return PilihanFokus(p, sp.getString(KEY_NAMA, null) ?: p)
        }

        fun simpanPilihan(context: Context, pilihan: PilihanFokus) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PAKET, pilihan.paket)
                .putString(KEY_NAMA, pilihan.nama)
                .apply()
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var pembaca: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AKSI_HENTI) {
            berhenti()
            return START_NOT_STICKY
        }
        val target = intent?.getStringExtra(EKSTRA_PAKET)
        if (target.isNullOrEmpty()) {
            berhenti()
            return START_NOT_STICKY
        }
        if (tun != null) return START_NOT_STICKY
        mulaiTerowongan(target)
        return START_NOT_STICKY
    }

    private fun mulaiTerowongan(target: String) {
        try {
            val b = Builder()
                .setSession("Stabilizer Fokus")
                .setBlocking(true)
                .addAddress("10.77.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:77::2", 128)
                .addRoute("::", 0)
            // Dua aplikasi ini tetap memakai jaringan biasa: aplikasi fokus dan Stabilizer
            // (supaya uji kualitas dan pemantauan tetap jalan).
            b.addDisallowedApplication(packageName)
            b.addDisallowedApplication(target)
            val pfd = b.establish()
            if (pfd == null) {
                catatan = "Izin VPN belum diberikan atau dicabut."
                berhenti()
                return
            }
            tun = pfd
            val masuk = FileInputStream(pfd.fileDescriptor)
            val t = Thread {
                val buf = ByteArray(32767)
                try {
                    // Semua paket dibuang; tidak ada yang diteruskan.
                    while (masuk.read(buf) >= 0) {
                        // buang
                    }
                } catch (e: IOException) {
                    // Terowongan ditutup.
                }
            }
            t.isDaemon = true
            t.start()
            pembaca = t
            paket = target
            berjalan = true
            tampilkanNotif(muatPilihan(this)?.takeIf { it.paket == target }?.nama ?: target)
        } catch (e: PackageManager.NameNotFoundException) {
            catatan = "Aplikasi fokus tidak ditemukan. Pilih ulang aplikasinya."
            berhenti()
        } catch (e: Exception) {
            catatan = "Gagal menyalakan mode fokus: ${e.message}"
            berhenti()
        }
    }

    private fun tampilkanNotif(nama: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_FOKUS, "Mode Fokus", NotificationManager.IMPORTANCE_LOW)
        )
        val henti = PendingIntent.getService(
            this,
            1,
            Intent(this, FokusService::class.java).setAction(AKSI_HENTI),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val buka = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        @Suppress("DEPRECATION")
        val aksi = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Matikan",
            henti
        ).build()
        val notif = Notification.Builder(this, CH_FOKUS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Mode Fokus aktif")
            .setContentText("Fokus: $nama. Aplikasi lain tidak punya internet.")
            .setOngoing(true)
            .setContentIntent(buka)
            .addAction(aksi)
            .build()
        nm.notify(ID_NOTIF, notif)
    }

    private fun tutupTerowongan() {
        pembaca?.interrupt()
        pembaca = null
        try {
            tun?.close()
        } catch (e: IOException) {
            // Sudah tertutup.
        }
        tun = null
        berjalan = false
        paket = null
        getSystemService(NotificationManager::class.java)?.cancel(ID_NOTIF)
    }

    private fun berhenti() {
        tutupTerowongan()
        stopSelf()
    }

    /** Dipanggil sistem saat pengguna mematikan VPN dari pengaturan atau VPN lain menggantikan. */
    override fun onRevoke() {
        berhenti()
    }

    override fun onDestroy() {
        tutupTerowongan()
        super.onDestroy()
    }
}
