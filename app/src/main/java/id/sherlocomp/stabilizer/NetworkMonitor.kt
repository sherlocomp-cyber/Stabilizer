package id.sherlocomp.stabilizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Membaca jenis jaringan, kekuatan sinyal, band, dan channel.
 * Semua status internal hanya disentuh dari satu thread (executor);
 * hasil akhir dikirim ke thread utama lewat [snapshot].
 */
class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val telephony: TelephonyManager? =
        appContext.getSystemService(TelephonyManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var snapshot by mutableStateOf(NetworkSnapshot())
        private set

    private var callback: Callback? = null
    private var running = false

    // Status terakhir; hanya diakses dari thread executor.
    private var networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN
    private var overrideType = 0
    private var nrMetrics: SignalMetrics? = null
    private var lteMetrics: SignalMetrics? = null
    private var cells: List<CellDetail> = emptyList()
    private var note: String? = null

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running) return
        val tm = telephony
        if (tm == null) {
            snapshot = snapshot.copy(note = "Perangkat ini tidak punya layanan telepon.")
            return
        }
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            snapshot = snapshot.copy(note = "Izin Telepon belum diberikan.")
            return
        }
        try {
            val cb = Callback()
            tm.registerTelephonyCallback(executor, cb)
            callback = cb
            running = true
            mainHandler.post(poller)
        } catch (e: SecurityException) {
            snapshot = snapshot.copy(note = "Izin ditolak sistem: ${e.message}")
        }
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacks(poller)
        callback?.let { telephony?.unregisterTelephonyCallback(it) }
        callback = null
    }

    private val poller = object : Runnable {
        override fun run() {
            if (!running) return
            requestCells()
            mainHandler.postDelayed(this, 3000L)
        }
    }

    private fun requestCells() {
        val tm = telephony ?: return
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            executor.execute {
                note = "Izin Lokasi belum diberikan: band dan channel tidak bisa dibaca."
                publish()
            }
            return
        }
        try {
            tm.requestCellInfoUpdate(executor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                    cells = cellInfo.filter { it.isRegistered }.mapNotNull { parseCell(it) }
                    note = null
                    publish()
                }

                override fun onError(errorCode: Int, detail: Throwable?) {
                    note = "Gagal membaca info sel (kode $errorCode)."
                    publish()
                }
            })
        } catch (e: SecurityException) {
            executor.execute {
                note = "Izin ditolak sistem: ${e.message}"
                publish()
            }
        }
    }

    private fun parseCell(info: CellInfo): CellDetail? = when (info) {
        is CellInfoNr -> {
            val id = info.cellIdentity as? CellIdentityNr
            val bands = id?.bands
            CellDetail(
                tech = "5G NR",
                band = if (bands != null && bands.isNotEmpty()) {
                    bands.joinToString(", ") { "n$it" }
                } else {
                    "-"
                },
                channel = id?.nrarfcn?.validOrNull(),
                pci = id?.pci?.validOrNull()
            )
        }
        is CellInfoLte -> {
            val id: CellIdentityLte = info.cellIdentity
            val bands = id.bands
            CellDetail(
                tech = "4G LTE",
                band = if (bands.isNotEmpty()) {
                    bands.joinToString(", ") { "B$it" }
                } else {
                    "-"
                },
                channel = id.earfcn.validOrNull(),
                pci = id.pci.validOrNull()
            )
        }
        else -> null
    }

    /** Selalu dipanggil dari thread executor. */
    private fun publish() {
        val operator = telephony?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "-"
        val snap = NetworkSnapshot(
            operatorName = operator,
            networkLabel = labelJaringan(networkType, overrideType),
            nr = nrMetrics,
            lte = lteMetrics,
            cells = cells,
            note = note,
            updatedAtMs = System.currentTimeMillis()
        )
        mainHandler.post { snapshot = snap }
    }

    private inner class Callback : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.DisplayInfoListener {

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            var nr: SignalMetrics? = null
            var lte: SignalMetrics? = null
            for (s in signalStrength.cellSignalStrengths) {
                when (s) {
                    is CellSignalStrengthNr -> nr = SignalMetrics(
                        rsrp = s.ssRsrp.validOrNull(),
                        rsrq = s.ssRsrq.validOrNull(),
                        sinr = s.ssSinr.validOrNull(),
                        level = s.level
                    )
                    is CellSignalStrengthLte -> lte = SignalMetrics(
                        rsrp = s.rsrp.validOrNull(),
                        rsrq = s.rsrq.validOrNull(),
                        sinr = s.rssnr.validOrNull(),
                        level = s.level
                    )
                    else -> Unit
                }
            }
            nrMetrics = nr
            lteMetrics = lte
            publish()
        }

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            networkType = telephonyDisplayInfo.networkType
            overrideType = telephonyDisplayInfo.overrideNetworkType
            publish()
        }
    }
}
