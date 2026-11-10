package id.sherlocomp.stabilizer

import android.telephony.TelephonyManager

/** Satu set angka kekuatan sinyal. Nilai null berarti tidak tersedia. */
data class SignalMetrics(
    val rsrp: Int? = null, // dBm, makin dekat 0 makin kuat
    val rsrq: Int? = null, // dB
    val sinr: Int? = null, // dB
    val level: Int? = null // 0..4 versi Android
)

/** Info satu sel yang sedang terdaftar (dipakai HP). */
data class CellDetail(
    val tech: String = "-",
    val band: String = "-",
    val channel: Int? = null, // ARFCN / EARFCN
    val pci: Int? = null
)

data class NetworkSnapshot(
    val operatorName: String = "-",
    val networkLabel: String = "-",
    val nr: SignalMetrics? = null,
    val lte: SignalMetrics? = null,
    val cells: List<CellDetail> = emptyList(),
    val note: String? = null,
    val updatedAtMs: Long = 0L
)

/** Android memakai Int.MAX_VALUE untuk "tidak tersedia". */
fun Int.validOrNull(): Int? = if (this == Int.MAX_VALUE) null else this

/**
 * Label jenis jaringan dari TelephonyDisplayInfo.
 * overrideType: 0 none, 1 LTE-CA, 2 LTE Advanced Pro (5Ge), 3 NR NSA, 4 NR advanced.
 */
fun labelJaringan(networkType: Int, overrideType: Int): String = when {
    networkType == TelephonyManager.NETWORK_TYPE_NR -> "5G SA (NR murni)"
    overrideType == 3 || overrideType == 4 -> "5G NSA (LTE + NR)"
    overrideType == 2 -> "LTE Advanced Pro (ikon 5Ge)"
    overrideType == 1 -> "4G LTE-A (carrier aggregation)"
    networkType == TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
    networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Belum diketahui"
    else -> "Jaringan lain (kode $networkType)"
}

/** Perkiraan kasar, bukan standar resmi. */
fun kualitasRsrp(rsrp: Int?): String = when {
    rsrp == null -> "-"
    rsrp >= -80 -> "Sangat baik"
    rsrp >= -90 -> "Baik"
    rsrp >= -100 -> "Cukup"
    else -> "Lemah"
}

/** Perkiraan kasar, bukan standar resmi. */
fun kualitasSinr(sinr: Int?): String = when {
    sinr == null -> "-"
    sinr >= 20 -> "Sangat baik"
    sinr >= 13 -> "Baik"
    sinr >= 0 -> "Cukup"
    else -> "Buruk"
}
