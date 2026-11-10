package id.sherlocomp.stabilizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Penjaga koneksi: foreground service yang
 * 1) menjaga proses aplikasi tetap hidup di latar belakang,
 * 2) menampilkan jenis jaringan dan sinyal terakhir di notifikasi,
 * 3) mencatat dan memberi tahu saat jaringan seluler putus / tersambung kembali.
 * Tidak menambah kekuatan sinyal radio.
 */
class StabilizerService : Service() {

    companion object {
        private const val CH_JAGA = "penjaga"
        private const val CH_PUTUS = "putus"
        private const val ID_JAGA = 1
        private const val ID_PUTUS = 2
        private const val MAKS_RIWAYAT = 20
        private const val PREFS = "stabilizer"
        private const val KEY_RIWAYAT = "riwayat"

        /** Dibaca UI untuk menampilkan status saklar. */
        var berjalan by mutableStateOf(false)
            private set

        /** Catatan putus/sambung terbaru di atas. Hanya diubah dari thread utama. */
        var riwayat by mutableStateOf<List<String>>(emptyList())
            private set

        fun mulai(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StabilizerService::class.java)
            )
        }

        fun henti(context: Context) {
            context.stopService(Intent(context, StabilizerService::class.java))
        }

        /** Memuat riwayat tersimpan bila di memori masih kosong. Panggil dari thread utama. */
        fun muat(context: Context) {
            if (riwayat.isNotEmpty()) return
            val teks = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RIWAYAT, null) ?: return
            riwayat = teks.split("\n").filter { it.isNotBlank() }.take(MAKS_RIWAYAT)
        }

        /** Menghapus riwayat di memori dan di penyimpanan. Panggil dari thread utama. */
        fun hapusRiwayat(context: Context) {
            riwayat = emptyList()
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RIWAYAT)
                .apply()
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    // Monitor bersama dengan layar (lihat MonitorBersama). Tidak null berarti layanan
    // ini sudah terhitung sebagai pemakai dan harus memanggil selesai() saat berhenti.
    private var monitor: NetworkMonitor? = null
    private var aktif = false
    private var terdaftarCallback = false
    private var putusSejakMs = 0L

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                val sejak = putusSejakMs
                if (sejak > 0L) {
                    val detik = (System.currentTimeMillis() - sejak) / 1000
                    catat("Seluler tersambung kembali (putus $detik dtk)")
                    putusSejakMs = 0L
                } else {
                    catat("Seluler tersedia")
                }
            }
        }

        override fun onLost(network: Network) {
            handler.post {
                putusSejakMs = System.currentTimeMillis()
                catat("Seluler putus")
                tampilkanNotifPutus()
            }
        }
    }

    private val pembaruNotif = object : Runnable {
        override fun run() {
            if (!aktif) return
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(ID_JAGA, buatNotifikasi(teksStatus()))
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        muat(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CH_JAGA, "Penjaga koneksi", NotificationManager.IMPORTANCE_LOW)
        )
        nm?.createNotificationChannel(
            NotificationChannel(CH_PUTUS, "Koneksi putus", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (aktif) return START_NOT_STICKY
        aktif = true
        val notif = buatNotifikasi("Memulai pemantauan...")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_JAGA, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_JAGA, notif)
        }
        berjalan = true
        MonitorBersama.mulai(this)
        monitor = MonitorBersama.instans(this)
        daftarkanCallback()
        handler.postDelayed(pembaruNotif, 3000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        aktif = false
        berjalan = false
        handler.removeCallbacksAndMessages(null)
        if (monitor != null) MonitorBersama.selesai()
        monitor = null
        if (terdaftarCallback) {
            try {
                getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(netCallback)
            } catch (e: IllegalArgumentException) {
                // Sudah tidak terdaftar.
            }
            terdaftarCallback = false
        }
        super.onDestroy()
    }

    private fun daftarkanCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, netCallback)
            terdaftarCallback = true
        } catch (e: SecurityException) {
            catat("Gagal memantau koneksi: ${e.message}")
        }
    }

    private fun catat(teks: String) {
        val jam = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        riwayat = (listOf("$jam $teks") + riwayat).take(MAKS_RIWAYAT)
        try {
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RIWAYAT, riwayat.joinToString("\n"))
                .apply()
        } catch (e: Exception) {
            // Gagal menyimpan tidak boleh mengganggu layanan.
        }
    }

    private fun teksStatus(): String {
        val s = monitor?.snapshot
        val bagian = mutableListOf<String>()
        bagian.add(s?.networkLabel ?: "-")
        val nr = s?.nr?.rsrp
        val lte = s?.lte?.rsrp
        if (nr != null) bagian.add("NR $nr dBm")
        if (lte != null) bagian.add("LTE $lte dBm")
        if (putusSejakMs > 0L) bagian.add("SELULER PUTUS")
        return bagian.joinToString(" | ")
    }

    private fun intentBuka(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buatNotifikasi(teks: String): Notification =
        Notification.Builder(this, CH_JAGA)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Stabilizer aktif")
            .setContentText(teks)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(intentBuka())
            .build()

    private fun tampilkanNotifPutus() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val notif = Notification.Builder(this, CH_PUTUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Koneksi seluler putus")
            .setContentText("Stabilizer akan mencatat saat tersambung kembali.")
            .setAutoCancel(true)
            .setContentIntent(intentBuka())
            .build()
        nm.notify(ID_PUTUS, notif)
    }
}
