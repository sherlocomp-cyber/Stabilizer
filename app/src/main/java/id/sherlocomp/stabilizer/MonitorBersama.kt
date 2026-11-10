package id.sherlocomp.stabilizer

import android.content.Context

/**
 * Satu [NetworkMonitor] yang dipakai bersama oleh layar (MainActivity) dan layanan
 * penjaga (StabilizerService), supaya tidak ada dua langganan telepon dan dua pembacaan
 * info sel yang berjalan bersamaan. Monitor baru benar-benar berhenti bila tidak ada
 * pemakai lagi. Hanya dipanggil dari thread utama.
 */
object MonitorBersama {
    private var monitor: NetworkMonitor? = null
    private var pemakai = 0

    /** Instans bersama; dibuat saat pertama diminta. Tidak mengubah jumlah pemakai. */
    fun instans(context: Context): NetworkMonitor {
        val ada = monitor
        if (ada != null) return ada
        val baru = NetworkMonitor(context.applicationContext)
        monitor = baru
        return baru
    }

    /** Pemakai baru mulai memakai monitor. Aman dipanggil walau monitor sudah jalan. */
    fun mulai(context: Context) {
        pemakai++
        instans(context).start()
    }

    /** Pemakai selesai. Monitor dihentikan hanya bila pemakai terakhir sudah selesai. */
    fun selesai() {
        if (pemakai > 0) pemakai--
        if (pemakai == 0) monitor?.stop()
    }
}
