# CATATAN: Stabilizer (handoff antar akun)

Terakhir diperbarui: 2026-11-10

## Tujuan
Aplikasi Android (APK) untuk membantu menstabilkan koneksi 5G XL di **iQOO 15R** dan **POCO F5**.
Fitur yang diminta pengguna: memaksimalkan koneksi untuk satu aplikasi, dan fitur pendukung lain.

## Cara kerja (aturan handoff)
- Satu langkah per giliran. Setelah tiap langkah: push ke repo ini, lalu perbarui bagian **Status** dan **Langkah berikutnya** di file ini.
- Kuota hampir habis: hentikan pekerjaan baru, perbarui CATATAN.md, push. Jangan mulai langkah besar.
- Akun lain: baca file ini saja, lalu kerjakan **Langkah berikutnya**. Jangan menjelajah ulang seluruh repo.
- Jangan menulis kode panjang di perintah bash (heredoc). Tulis lewat alat pembuat file.

## Cara mengakses repo dari sesi chat
- Cara cepat: panggil `add_repo` (owner `sherlocomp-cyber`, repo `Stabilizer`, `access: "push"`), lalu `git clone --depth 1` ke `/home/claude/stabilizer`. Setelah itu bisa tulis banyak file, satu commit, `git push origin HEAD:main`.
- Dengan akses itu, `gh run list -R sherlocomp-cyber/Stabilizer` dan `gh run view` bisa dipakai untuk memantau build Actions. Log build merah: `gh run view <id> --log-failed`.
- `gh run download` (unduh APK) **diblokir** dari sandbox chat. Pengguna harus mengunduh APK sendiri dari halaman Actions. Jangan mencoba jalan memutar.
- Cadangan tanpa add_repo: konektor `put_file` / `get_file` (server "Tes"). Satu commit per file, kirim berurutan (jangan paralel). `put_file` menimpa seluruh file, jadi baca dulu dengan `get_file` lalu kirim isi lengkap. Urutan push yang dipakai: file baru dulu, lalu file yang memakainya, supaya tiap commit tetap bisa dikompilasi.
- **Cek status build tanpa add_repo (repo publik), dari `bash_tool`:** `curl -sL https://github.com/sherlocomp-cyber/Stabilizer/actions -o /tmp/a.html`, lalu parse per run dengan regex berurutan: `"runNumber":N`, lalu `"status":"WORKFLOW_RUN_STATUS_..."` dan `WORKFLOW_RUN_CONCLUSION_SUCCESS` / `..._FAILURE` milik run itu; judul commit ada di blok run yang sama (nomor run terbesar = terbaru). `WORKFLOW_RUN_STATUS_IN_PROGRESS` berarti masih jalan. **Jangan** mencocokkan judul commit dengan jendela karakter di sekitarnya (pernah salah kaitan ke run tetangga). Judul commit muncul dua kali (daftar dan data lain), pakai kemunculan di dalam blok `runNumber`. Ini bukan log; untuk pesan error minta pengguna. Satu run butuh beberapa menit.
- Badge kasar: `curl -sL "https://github.com/sherlocomp-cyber/Stabilizer/actions/workflows/build-apk.yml/badge.svg?branch=main" | grep -oiE "passing|failing"` (run terakhir yang selesai).
- `api.github.com` dari sandbox kena batas laju (rate limit), jangan diandalkan.
- Setiap commit diakhiri trailer:
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`
- Sandbox tidak punya Android SDK. Kode tidak bisa dikompilasi lokal; satu-satunya uji kompilasi adalah GitHub Actions. Build hijau **tidak** membuktikan perilaku benar di HP (contoh: Mode Fokus 0.5.0 build hijau tapi tidak bisa dimatikan).

## Batas teknis (penting, jangan dijanjikan berlebihan ke pengguna)
Aplikasi biasa tanpa root **tidak bisa**:
- Menambah kekuatan sinyal radio, mengunci band, atau memaksa 5G SA/NSA. API-nya hanya untuk aplikasi sistem atau privileged.
- Mengubah preferred network type (butuh izin MODIFY_PHONE_STATE).
- Menyalakan/mematikan mode pesawat lewat kode.
- Mengedit APN langsung (butuh izin privileged). Hanya bisa membuka layar pengaturannya.
- ICMP ping (butuh root). Latency diukur lewat waktu connect TCP port 443.

Aplikasi biasa **bisa**:
- Memantau: jenis jaringan (LTE/NR), indikasi NSA/SA (TelephonyDisplayInfo), RSRP/RSRQ/SINR, band/ARFCN dari cell info. Perlu izin READ_PHONE_STATE dan lokasi (dan **Lokasi sistem harus menyala**, kalau mati cell info kosong).
- Mengukur kualitas: latency, jitter, packet loss (versi TCP), throughput singkat.
- Menjaga stabilitas: foreground service, deteksi koneksi putus lewat NetworkCallback, notifikasi, minta pengecualian optimasi baterai. (WakeLock sengaja belum dipakai: foreground service sudah menjaga proses hidup, WakeLock hanya menambah pemakaian baterai dan tidak memperbaiki koneksi.)
- Mode Fokus Aplikasi lewat VpnService: lalu lintas aplikasi lain bisa diblokir supaya satu aplikasi tidak berebut bandwidth. Ini mengurangi rebutan, **tidak** menambah kapasitas radio.
- Membuka pintasan pengaturan jaringan (dan menu info telepon bila ada di ROM tersebut).
- Opsional (tebakan, perlu diuji per perangkat): Shizuku untuk perintah privileged tanpa root.

## Keputusan teknis
- Kotlin + Jetpack Compose, Gradle Kotlin DSL. Package: `id.sherlocomp.stabilizer`.
- Versi yang dipakai (terbukti build hijau sampai 0.5.0): AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.10.2, Java 17, compileSdk/targetSdk 35.
- **minSdk 31** supaya cukup memakai `TelephonyCallback`. Cocok untuk POCO F5 (Android 13+) dan iQOO 15R.
- Tanpa library tambahan (tanpa coroutines eksplisit): thread memakai `Executors` + `Handler`, status UI memakai `mutableStateOf`. (`LaunchedEffect` dari Compose dipakai di MainActivity, GuardCard, RiwayatUjiCard.)
- Penyimpanan ringan: `SharedPreferences` bernama `stabilizer` (kunci `riwayat` untuk riwayat putus, `riwayat_uji` untuk riwayat hasil uji, `fokus_paket` dan `fokus_nama` untuk aplikasi fokus). Tanpa database.
- **Satu `NetworkMonitor` bersama** lewat `MonitorBersama` (hitung pemakai: layar dan layanan). Monitor hanya berhenti bila pemakai terakhir selesai.
- **Mode Fokus = versi "blokir aplikasi lain" (pilihan pengguna, 2026-11-10).** VPN lokal menangkap semua aplikasi kecuali aplikasi fokus dan Stabilizer; terowongan **tidak dibaca** (antrean kernel menjatuhkan paket). Versi "prioritas tanpa blokir" (butuh penerus paket TCP/UDP sendiri) ditolak karena rumit dan berisiko; jangan dikerjakan tanpa diminta lagi.
- **Pelajaran VpnService (dari bug 0.5.0, pengguna harus force close):** (1) selama VPN aktif sistem memegang bind ke VpnService, jadi `stopService` saja tidak menghancurkan layanan; matikan lewat `startService` dengan aksi HENTI yang memanggil `tun.close()` lalu `stopSelf()`. (2) Jangan ada utas yang tertahan membaca fd terowongan: itu menahan antarmuka VPN tetap hidup walau fd ditutup. Menutup fd adalah satu-satunya yang mematikan VPN.
- Tidak butuh root.
- **Tidak ada Gradle wrapper** di repo (jar biner). Workflow memasang Gradle 8.10.2 dan menjalankan `gradle assembleDebug`.
- APK dibuat lewat **GitHub Actions** (`.github/workflows/build-apk.yml`), jalan otomatis di tiap push ke main. APK debug: tab Actions > run terbaru > Artifacts > `stabilizer-debug-apk`.
- Nama repo di GitHub memakai huruf besar `Stabilizer`; `stabilizer` juga diarahkan ke sana.
- Layanan penstabil: foreground service tipe `specialUse` (sideload), `START_NOT_STICKY`. Status layanan dan riwayat putus disimpan di `companion object` (`mutableStateOf`) supaya UI bisa membaca langsung.

## Peta file
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- `app/build.gradle.kts` (**versionName 0.5.1, versionCode 7**; 0.5.0 / 6 adalah APK dengan bug Mode Fokus tidak bisa dimatikan. Layar HP menampilkan "Stabilizer v<versionName>")
- `app/src/main/AndroidManifest.xml` (izin: READ_PHONE_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE, INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; `<queries>` intent MAIN/LAUNCHER untuk daftar aplikasi; service `.StabilizerService` specialUse; service `.FokusService` dengan izin BIND_VPN_SERVICE dan intent-filter `android.net.VpnService`)
- `app/src/main/java/id/sherlocomp/stabilizer/`
  - `MainActivity.kt`: minta izin, memakai `MonitorBersama.instans(context)` untuk membaca `snapshot`, `MonitorBersama.mulai/selesai` di `DisposableEffect`, membuat `QualityTester`. Memakai `LaunchedEffect(hasilUji)` untuk mencatat `konteksSinyal(...)` dan memanggil `RiwayatUji.catat(...)`, lalu menampilkan `MonitorScreen` dengan `konteksUji` dan `penjaga = { GuardCard(); FokusCard(); RiwayatUjiCard() }`.
  - `MonitorBersama.kt`: `object` dengan `instans(context)`, `mulai(context)`, `selesai()`. Hanya dari thread utama.
  - `NetworkSnapshot.kt`: model data (`SignalMetrics`, `CellDetail`, `NetworkSnapshot`), `labelJaringan`, `kualitasRsrp`, `kualitasSinr`, `validOrNull`
  - `NetworkMonitor.kt`: `TelephonyCallback` + `requestCellInfoUpdate` tiap 3 detik (band, channel, PCI); `nrBandDariArfcn()` untuk perkiraan band NR. Terbukti di iQOO 15R: "n40 (perkiraan)". `start()` aman dipanggil berulang.
  - `QualityTester.kt`: model hasil, `ringkasan()`, `konteksSinyal(nr, lte)`, uji latency TCP (3 host x 20 sampel), jitter, persen gagal, unduh opsional (maks 10 MB atau 8 detik).
  - `QualityCard.kt`: kartu UI uji kualitas dengan blok "Sinyal saat uji selesai".
  - `RiwayatUji.kt`: `object RiwayatUji` (`daftar`, `muat`, `catat`, `hapus`) dan `RiwayatUjiCard()`. Maks 15 baris, kunci `riwayat_uji`. Hasil dibatalkan tidak dicatat.
  - `StabilizerService.kt` (4a + 4b): foreground service penjaga, memakai `MonitorBersama`, notifikasi tiap 5 detik, `registerNetworkCallback` transport CELLULAR, riwayat putus 20 entri bertanggal tersimpan.
  - `GuardCard.kt` (4a + 4b): kartu "Penjaga koneksi".
  - `FokusService.kt` (Tahap 5, diperbaiki di 0.5.1): `VpnService`. `data class PilihanFokus(paket, nama)`. Companion: `berjalan`, `paket`, `catatan`, `mulai(context, paket)` (startService), **`henti(context)` = startService dengan aksi HENTI**, `muatPilihan`, `simpanPilihan`. `Builder`: alamat 10.77.0.2/32 dan fd00:77::2/128, rute 0.0.0.0/0 dan ::/0, `addDisallowedApplication` untuk Stabilizer dan aplikasi fokus. **Tanpa utas pembaca**, fd hanya disimpan di `tun` dan ditutup di `tutupTerowongan()`. Notifikasi "Mode Fokus aktif" dengan tombol "Matikan" (aksi HENTI). `onRevoke()` dan `onDestroy()` menutup terowongan. `START_NOT_STICKY`.
  - `FokusCard.kt` (Tahap 5): kartu "Mode Fokus Aplikasi": peringatan, tombol "Pilih/Ganti aplikasi" (dialog daftar aplikasi berikon peluncur), saklar (`VpnService.prepare` + dialog izin VPN), pesan gagal merah.
  - `MonitorScreen.kt`: layar Compose, slot `penjaga` di dalam Column utama.
- `app/src/main/res/values/strings.xml`, `themes.xml`
- `.github/workflows/build-apk.yml`

## Roadmap
1. Rangka proyek + workflow build APK. **Selesai, build hijau.**
2. Monitor jaringan. **Selesai dan terbukti di iQOO 15R.**
3. Uji kualitas + konteks sinyal + riwayat hasil uji. **Build hijau.** Riwayat belum dilihat di HP. Grafik belum dibuat.
4. Layanan penstabil. 4a terbukti terpasang; 4b (riwayat putus, tips, monitor bersama) build hijau. Belum dibuktikan di HP: deteksi putus, kelangsungan di latar belakang, pengecualian baterai, notifikasi tetap menampilkan sinyal.
5. Mode Fokus Aplikasi (versi blokir aplikasi lain). **0.5.0 terpasang di iQOO 15R: nyala, tapi TIDAK bisa dimatikan (pengguna force close).** Perbaikan di 0.5.1 di-push, build belum dicek, belum diuji di HP.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Tahap 1 sampai 4a: build hijau (lihat Roadmap). Screenshot 15:49: kartu Penjaga "Aktif", riwayat "15:45:41 Seluler tersedia".
- [x] Konteks sinyal di kartu uji, riwayat hasil uji, Tahap 4b, MonitorBersama, versi 0.4.0: build hijau. Belum diuji pengguna di HP.
- [x] Tahap 5 versi 0.5.0: build hijau, terpasang di iQOO 15R. **Bug: mode fokus tidak bisa dimatikan, harus force close** (2026-11-10). Dugaan penyebab (belum dikonfirmasi, tapi keduanya diperbaiki): saklar memakai `stopService` padahal sistem memegang bind ke VpnService; dan utas pembaca terowongan yang tertahan menahan antarmuka VPN walau fd ditutup (memengaruhi juga tombol "Matikan" di notifikasi). Pengguna belum merinci jalur mana yang gagal (saklar, notifikasi, atau keduanya) dan belum melaporkan apakah aplikasi lain benar-benar offline serta aplikasi fokus tetap online.
- [ ] **0.5.1 (perbaikan Mode Fokus)**: FokusService.kt dan app/build.gradle.kts di-push. **Build belum dicek.** Risiko kompilasi kecil (yang dihapus: utas, FileInputStream, setBlocking).
- [ ] Yang **perlu dibuktikan di HP** untuk Mode Fokus (0.5.1): (1) saklar mematikan mode dan internet aplikasi lain pulih seketika; (2) tombol "Matikan" di notifikasi juga; (3) mematikan lewat Pengaturan > VPN; (4) aplikasi lain benar-benar offline dan aplikasi fokus online selama aktif; (5) Stabilizer sendiri (uji kualitas, notifikasi sinyal) tetap jalan; (6) riwayat putus Penjaga tidak mencatat "Seluler putus" palsu saat VPN menyala/mati (dugaan: `NetworkRequest.Builder()` bawaan memuat NOT_VPN; belum diverifikasi); (7) apakah aplikasi fokus butuh Layanan Google (gms). **Uji dengan durasi pendek dan pastikan tahu jalan keluar (Pengaturan > VPN) sebelum menyalakan.**
- [ ] Tahap 4a belum teruji penuh di HP: (a) isi notifikasi tetap; (b) deteksi putus + notifikasi putus; (c) layanan bertahan setelah aplikasi ditutup dari recent apps; (d) tombol pengecualian baterai.
- [ ] POCO F5 belum diuji.
- [ ] Tahap 6 sampai 8 belum dikerjakan.

## Temuan perangkat (isi setelah pengguna menguji)
### iQOO 15R (operator XL, jaringan "5G NSA (LTE + NR)")
- Uji 15:29 (Lokasi MATI, baterai 48%): NR RSRP -94, RSRQ -12, SINR 10; LTE RSRP -96, RSRQ -16, SINR 8. Kartu sel kosong. Kualitas "Kurang stabil": 1.1.1.1 52.9 ms, jitter 31.3, gagal 5%; 8.8.8.8 41.8 ms, jitter 19.0; 9.9.9.9 18.8 ms, jitter 7.3. Unduh **157.9 Mbps**.
- Uji 15:34 (Lokasi aktif, 47%): NR RSRP -98, RSRQ -16, SINR **-2 sampai -3 (Buruk)**; LTE RSRP -100, RSRQ -16/-18, SINR 4-5. Sel: LTE B3 channel 1313 (PCI 15 terdaftar, 249, 79), NR channel 472110 PCI 15. Kualitas "Kurang stabil": 1.1.1.1 50.2 ms, jitter 24.4; 8.8.8.8 71.5 ms, jitter 17.5; 9.9.9.9 41.8 ms, jitter 7.3. Unduh **30.6 Mbps**.
- Uji 15:49 (app dengan Penjaga aktif, baterai 44%): NR RSRP **-107 (Lemah)**, RSRQ -14, SINR 0, level 3/4; LTE RSRP **-104 (Lemah)**, RSRQ -13, SINR 3. Sel: LTE B3 channel 1313 dengan PCI 15 (terdaftar), 250, 249, 116, 251; NR n40 (perkiraan) channel 472110 PCI 15. Kualitas **"Stabil"**: 1.1.1.1 16.0 ms, jitter 2.4; 8.8.8.8 29.4 ms, jitter 4.4; 9.9.9.9 15.8 ms, jitter 2.1; gagal 0% semua. Unduh **65.3 Mbps**.
- Pola dari 3 sampel: RSRP malah paling lemah di uji ke-3, tapi latency/jitter paling bagus. Latency dan kecepatan berubah-ubah antar menit, tidak mengikuti RSRP; SINR/beban sel lebih mungkin berperan. Semua sel LTE yang terlihat ada di B3 channel 1313 (satu frekuensi, banyak PCI tetangga), kondisi yang rawan interferensi sesama-frekuensi. Ini dugaan; tiga sampel, belum cukup untuk kesimpulan.
- Baterai: 48% (15:29) ke 44% (15:49). Tidak bisa dikaitkan ke Penjaga karena layar menyala dan ada uji unduh.
- Mode Fokus 0.5.0: tidak bisa dimatikan (force close). Detail lain belum dilaporkan.
- POCO F5: belum diuji.

## Langkah berikutnya
1. Cek build 0.5.1 (lihat cara cek di atas). Jika merah, minta log error dan perbaiki dulu. Jika hijau, minta pengguna memasang APK (`stabilizer-debug-apk`); layar harus menampilkan **Stabilizer v0.5.1**. Lalu uji Mode Fokus **singkat** (1 sampai 2 menit) dengan poin 1 sampai 7 di bagian Status. Tanyakan hasilnya, termasuk jalur mana yang berhasil mematikan. Bila **masih** tidak bisa dimatikan, jangan menebak lagi: minta pengguna menyebut jalur yang gagal (saklar/notifikasi/Pengaturan VPN) dan apa yang terlihat (saklar kembali ke Mati tapi aplikasi lain tetap offline, atau saklar tetap Aktif). Pertimbangkan pengaman tambahan: timer otomatis mati (misal 10 menit, pengguna bisa atur) supaya tidak pernah terjebak.
2. Bila aplikasi fokus gagal karena bergantung ke Layanan Google atau aplikasi pendamping: tambah opsi "izinkan juga Layanan Google" (`addDisallowedApplication("com.google.android.gms")` dan sejenisnya). Baca daftar paket yang relevan dari pengguna dulu, jangan menebak.
3. Uji sisa Tahap 3 dan 4: riwayat hasil uji bertambah tiap uji, bertahan setelah aplikasi ditutup; deteksi putus; notifikasi tetap menampilkan sinyal; tombol pengecualian baterai. Setelah beberapa baris riwayat uji terkumpul, baca polanya bersama pengguna (SINR, jam, sel, ada/tidaknya mode fokus). Opsional: grafik sederhana.
4. Lalu **Tahap 6** (pintasan dan profil per perangkat iQOO 15R dan POCO F5 + tips pengaturan). Nama menu tiap ROM harus diverifikasi pengguna, jangan dikarang.

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat. Tahap 1, 2, 3 di-push, build hijau. APK tidak bisa diunduh dari sandbox; pengguna ambil dari Actions.
- 2026-11-10: Pengguna mengirim screenshot uji HP pertama; memastikan perangkat = iQOO 15R, Lokasi dinyalakan. Perkiraan band NR dari NR-ARFCN. Tahap 4a di-push dan terpasang (build hijau).
- 2026-11-10: Pengguna minta lanjut berulang tanpa laporan uji. Di-push berurutan: konteks sinyal di kartu uji; Tahap 4b (riwayat putus tersimpan, tombol hapus, tips); riwayat hasil uji (RiwayatUji.kt); MonitorBersama.kt + versi 0.4.0. Semua build hijau.
- 2026-11-10: Pengguna memilih Mode Fokus versi "blokir aplikasi lain (sederhana)". Tahap 5 di-push (FokusService.kt, FokusCard.kt, Manifest, MainActivity.kt, versi 0.5.0), build hijau.
- 2026-11-10: Pengguna melapor Mode Fokus 0.5.0 **tidak bisa dihentikan, harus force close**. Perbaikan 0.5.1: `FokusService.henti` kini startService dengan aksi HENTI (bukan stopService), utas pembaca terowongan dihapus (terowongan tidak dibaca), versi 0.5.1 / code 7. Build belum dicek. Pelajaran VpnService dicatat di "Keputusan teknis".
