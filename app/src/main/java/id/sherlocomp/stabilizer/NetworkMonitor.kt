package id.sherlocomp.stabilizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
import android.telephony.SubscriptionManager
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
 * Memakai langganan (SIM) yang dipilih untuk data seluler, bukan SIM bawaan telepon.
 * Semua status internal hanya disentuh dari satu thread (executor);
 * hasil akhir dikirim ke thread utama lewat [snapshot].
 */
class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val baseTelephony: TelephonyManager? =
        appContext.getSystemService(TelephonyManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var telephony: TelephonyManager? = null

    @Volatile
    private var subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID

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
    private var cellTotal = 0
    private var cellTerdaftar = 0
    private var cellSumber = "-"
    private var note: String? = null

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun idLanggananData(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId()
    } catch (e: SecurityException) {
        SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    fun start() {
        if (running) return
        val base = baseTelephony
        if (base == null) {
            snapshot = snapshot.copy(note = "Perangkat ini tidak punya layanan telepon.")
            return
        }
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            snapshot = snapshot.copy(note = "Izin Telepon belum diberikan.")
            return
        }
        try {
            val id = idLanggananData()
            subId = id
            val tm = if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                base.createForSubscriptionId(id)
            } else {
                base
            }
            telephony = tm
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
                    var daftar: List<CellInfo> = cellInfo
                    var sumber = "update"
                    if (daftar.isEmpty()) {
                        // Beberapa ROM mengembalikan daftar kosong; coba data cache sistem.
                        daftar = try {
                            tm.allCellInfo ?: emptyList()
                        } catch (e: SecurityException) {
                            emptyList()
                        }
                        sumber = "cache"
                    }
                    simpanSel(daftar, sumber)
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

    private fun simpanSel(daftar: List<CellInfo>, sumber: String) {
        cells = daftar.mapNotNull { parseCell(it) }.sortedByDescending { it.terdaftar }
        cellTotal = daftar.size
        cellTerdaftar = daftar.count { it.isRegistered }
        cellSumber = sumber
        note = null
        publish()
    }

    private fun parseCell(info: CellInfo): CellDetail? = when (info) {
        is CellInfoNr -> {
            val id = info.cellIdentity as? CellIdentityNr
            val bands = id?.bands
            val arfcn = id?.nrarfcn?.validOrNull()
            CellDetail(
                tech = "5G NR",
                band = if (bands != null && bands.isNotEmpty()) {
                    bands.joinToString(", ") { "n$it" }
                } else {
                    // Sebagian ROM tidak mengisi band NR; perkirakan dari NR-ARFCN.
                    nrBandDariArfcn(arfcn) ?: "-"
                },
                channel = arfcn,
                pci = id?.pci?.validOrNull(),
                terdaftar = info.isRegistered
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
                pci = id.pci.validOrNull(),
                terdaftar = info.isRegistered
            )
        }
        else -> CellDetail(
            tech = info.javaClass.simpleName.removePrefix("CellInfo"),
            terdaftar = info.isRegistered
        )
    }

    /** Selalu dipanggil dari thread executor. */
    private fun publish() {
        val tm = telephony
        val operator = listOf(tm?.networkOperatorName, tm?.simOperatorName, tm?.networkOperator)
            .firstOrNull { !it.isNullOrBlank() } ?: "-"
        val lokasiAktif =
            appContext.getSystemService(LocationManager::class.java)?.isLocationEnabled
        val teksLokasi = if (lokasiAktif == null) "?" else if (lokasiAktif) "aktif" else "MATI"
        val diag = "Sub data: $subId | Lokasi sistem: $teksLokasi | " +
            "Sel dibaca: $cellTotal (terdaftar $cellTerdaftar, sumber $cellSumber)"
        val snap = NetworkSnapshot(
            operatorName = operator,
            networkLabel = labelJaringan(networkType, overrideType),
            nr = nrMetrics,
            lte = lteMetrics,
            cells = cells,
            note = note,
            diag = diag,
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

/**
 * Perkiraan band NR dari NR-ARFCN (rumus 3GPP TS 38.104 untuk frekuensi di bawah 24 GHz),
 * dipetakan ke band yang umum dipakai operator Indonesia. Hanya tebakan bila ROM
 * tidak mengisi band; diberi label "perkiraan".
 */
private fun nrBandDariArfcn(arfcn: Int?): String? {
    if (arfcn == null || arfcn < 0) return null
    val mhz: Double = when {
        arfcn < 600000 -> arfcn * 0.005
        arfcn < 2016667 -> 3000.0 + (arfcn - 600000) * 0.015
        else -> return null
    }
    val band = when {
        mhz in 758.0..803.0 -> "n28"
        mhz in 925.0..960.0 -> "n8"
        mhz in 1805.0..1880.0 -> "n3"
        mhz in 2110.0..2170.0 -> "n1"
        mhz in 2300.0..2400.0 -> "n40"
        mhz in 2496.0..2690.0 -> "n41"
        mhz in 3300.0..3800.0 -> "n78"
        else -> return null
    }
    return "$band (perkiraan)"
}
