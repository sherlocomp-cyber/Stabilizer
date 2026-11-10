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
- Cadangan tanpa add_repo: konektor `put_file` / `get_file` (server "Tes"). Satu commit per file, kirim berurutan (jangan paralel). `put_file` menimpa seluruh file, jadi baca dulu dengan `get_file` lalu kirim isi lengkap. Dengan cara ini status build Actions tidak bisa dipantau lewat konektor; minta pengguna melapor bila build merah. Urutan push yang dipakai: file baru dulu, lalu file yang memakainya, supaya tiap commit tetap bisa dikompilasi.
- **Cek kasar status build tanpa add_repo (repo publik):** dari `bash_tool`, `curl -sL "https://github.com/sherlocomp-cyber/Stabilizer/actions/workflows/build-apk.yml/badge.svg?branch=main" | grep -oiE "passing|failing"` menampilkan hasil run terakhir yang **selesai** di branch main. Run yang baru di-push mungkin belum selesai, jadi "passing" bisa berarti push sebelumnya. Tidak menggantikan log. `api.github.com` dari sandbox kena batas laju (rate limit), jangan diandalkan.
- Setiap commit diakhiri trailer:
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`
- Sandbox tidak punya Android SDK. Kode tidak bisa dikompilasi lokal; satu-satunya uji kompilasi adalah GitHub Actions. Tulis kode hati-hati, lalu cek hasil run.

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
- Mode Fokus Aplikasi lewat VpnService (addAllowedApplication / addDisallowedApplication): lalu lintas satu aplikasi diprioritaskan, aplikasi lain bisa dibatasi. Ini mengurangi rebutan bandwidth, **tidak** menambah kapasitas radio.
- Membuka pintasan pengaturan jaringan (dan menu info telepon bila ada di ROM tersebut).
- Opsional (tebakan, perlu diuji per perangkat): Shizuku untuk perintah privileged tanpa root.

## Keputusan teknis
- Kotlin + Jetpack Compose, Gradle Kotlin DSL. Package: `id.sherlocomp.stabilizer`.
- Versi yang dipakai (terbukti build hijau sampai Tahap 4a): AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.10.2, Java 17, compileSdk/targetSdk 35.
- **minSdk 31** supaya cukup memakai `TelephonyCallback`. Cocok untuk POCO F5 (Android 13+) dan iQOO 15R.
- Tanpa library tambahan (tanpa coroutines eksplisit): thread memakai `Executors` + `Handler`, status UI memakai `mutableStateOf`. (`LaunchedEffect` dari Compose dipakai di MainActivity, GuardCard, dan RiwayatUjiCard, itu bagian runtime Compose, bukan library baru.)
- Penyimpanan ringan: `SharedPreferences` bernama `stabilizer` (kunci `riwayat` untuk riwayat putus, kunci `riwayat_uji` untuk riwayat hasil uji). Tanpa database.
- **Satu `NetworkMonitor` bersama** lewat `MonitorBersama` (hitung pemakai: layar dan layanan). Monitor hanya berhenti bila pemakai terakhir selesai.
- Tidak butuh root.
- **Tidak ada Gradle wrapper** di repo (jar biner). Workflow memasang Gradle 8.10.2 dan menjalankan `gradle assembleDebug`.
- APK dibuat lewat **GitHub Actions** (`.github/workflows/build-apk.yml`), jalan otomatis di tiap push ke main. APK debug: tab Actions > run terbaru > Artifacts > `stabilizer-debug-apk`.
- Nama repo di GitHub memakai huruf besar `Stabilizer`; `stabilizer` juga diarahkan ke sana.
- Layanan penstabil: foreground service tipe `specialUse` (sideload), `START_NOT_STICKY` (menghindari kegagalan start FGS dari latar belakang di Android 12+). Status layanan dan riwayat putus disimpan di `companion object` (`mutableStateOf`) supaya UI bisa membaca langsung.

## Peta file
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- `app/build.gradle.kts` (**versionName 0.4.0, versionCode 5**; sebelumnya 0.3.1 / 4. Layar HP menampilkan "Stabilizer v<versionName>")
- `app/src/main/AndroidManifest.xml` (izin: READ_PHONE_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE, INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; service `.StabilizerService` specialUse)
- `app/src/main/java/id/sherlocomp/stabilizer/`
  - `MainActivity.kt`: minta izin, memakai `MonitorBersama.instans(context)` untuk membaca `snapshot`, `MonitorBersama.mulai/selesai` di `DisposableEffect`, membuat `QualityTester`. Memakai `LaunchedEffect(hasilUji)` untuk mencatat `konteksSinyal(...)` dari sinyal saat hasil uji baru keluar dan memanggil `RiwayatUji.catat(...)`, lalu menampilkan `MonitorScreen` dengan `konteksUji` dan `penjaga = { GuardCard(); RiwayatUjiCard() }`.
  - `MonitorBersama.kt` (baru): `object` dengan `instans(context)` (dibuat sekali), `mulai(context)` (pemakai+1 lalu `start()`, aman dipanggil berulang), `selesai()` (pemakai-1, `stop()` hanya bila 0). Hanya dari thread utama.
  - `NetworkSnapshot.kt`: model data (`SignalMetrics`, `CellDetail`, `NetworkSnapshot`), `labelJaringan`, `kualitasRsrp`, `kualitasSinr`, `validOrNull`
  - `NetworkMonitor.kt`: `TelephonyCallback` (kekuatan sinyal + display info NSA/SA) dan `requestCellInfoUpdate` tiap 3 detik (band, channel, PCI). Bila band NR kosong, `nrBandDariArfcn()` memperkirakannya dari NR-ARFCN (label "perkiraan"). Terbukti jalan di iQOO 15R: tampil "n40 (perkiraan)". `start()` aman dipanggil berulang (dijaga flag `running`); bila izin belum ada, `start()` gagal diam-diam dan bisa dipanggil lagi setelah izin diberikan.
  - `QualityTester.kt`: model hasil, `ringkasan()` (penilaian dari loss/jitter terburuk: Stabil / Kurang stabil >15 ms atau >1% / Tidak stabil >40 ms atau >5% / Sangat buruk >=20%), `konteksSinyal(nr, lte)` (teks RSRP/SINR 5G dan 4G beserta label kualitas, plus catatan bila SINR 5G < 0 dB), uji latency TCP (3 host x 20 sampel), jitter, persen gagal, unduh opsional dari speed.cloudflare.com (maks 10 MB atau 8 detik). `ringkasan()` tidak memakai sinyal; sinyal hanya ditampilkan di sebelahnya.
  - `QualityCard.kt`: kartu UI uji kualitas (tombol mulai/batal, saklar unduh, hasil). Parameter `konteks: String?` menampilkan blok "Sinyal saat uji selesai".
  - `RiwayatUji.kt`: `object RiwayatUji` (`daftar` berupa `mutableStateOf<List<String>>`, `muat`, `catat`, `hapus`) dan composable `RiwayatUjiCard()`. Satu baris per hasil: tanggal/jam, ringkasan, rata-rata latency, jitter terburuk, persen gagal terburuk, unduh, sinyal "5G rsrp/sinr, 4G rsrp/sinr". Terbaru di atas, maks 15, disimpan di SharedPreferences `stabilizer` kunci `riwayat_uji` (dipisah newline). Hasil yang dibatalkan ("Dibatalkan...") atau tanpa sampel tidak dicatat. Kartu hanya tampil bila ada riwayat; tombol "Hapus riwayat uji".
  - `StabilizerService.kt` (4a + 4b): foreground service. **Memakai monitor bersama (`MonitorBersama.mulai(this)` di `onStartCommand`, `selesai()` di `onDestroy` bila `monitor != null`).** Memperbarui notifikasi tiap 5 detik (jenis jaringan, RSRP NR/LTE), `registerNetworkCallback` transport CELLULAR: `onLost` mencatat riwayat dan menampilkan notifikasi "Koneksi seluler putus", `onAvailable` mencatat tersambung kembali beserta lama putus. Riwayat 20 entri, format "dd/MM HH:mm:ss teks", disimpan ke SharedPreferences tiap dicatat dan dimuat saat service dibuat / kartu tampil. API: `StabilizerService.mulai(context)`, `henti(context)`, `muat(context)`, `hapusRiwayat(context)`, `berjalan`, `riwayat`.
  - `GuardCard.kt` (4a + 4b): kartu UI "Penjaga koneksi": saklar layanan (meminta izin notifikasi di Android 13+), tombol "Izinkan abaikan optimasi baterai" (diganti teks bila sudah dikecualikan), teks tips umum (kunci aplikasi di recent apps, izinkan aktivitas latar belakang; nama menu **belum diverifikasi** di iQOO/OriginOS), daftar riwayat + tombol "Hapus riwayat".
  - `MonitorScreen.kt`: layar Compose (jaringan, kartu sinyal 5G dan 4G, sel terdaftar, kartu uji kualitas, slot `penjaga`, parameter `konteksUji`). Menampilkan petunjuk merah di kartu sel bila Lokasi sistem mati. Slot `penjaga()` ada di dalam Column utama (bisa berisi beberapa composable).
- `app/src/main/res/values/strings.xml`, `themes.xml`
- `.github/workflows/build-apk.yml`

## Roadmap
1. Rangka proyek + workflow build APK. **Selesai, build hijau.**
2. Monitor jaringan: jenis 4G/5G, RSRP/RSRQ/SINR, band, operator. **Selesai dan terbukti di iQOO 15R (termasuk band NR perkiraan).**
3. Uji kualitas: ping (TCP), jitter, packet loss, throughput singkat. **Selesai, terbukti di iQOO 15R.** Sinyal saat uji dan riwayat hasil uji: di-push, build belum dicek. Grafik belum dibuat (hanya daftar teks).
4. Layanan penstabil (foreground service): notifikasi sinyal, deteksi putus, whitelist baterai. **4a: build hijau, terpasang di iQOO 15R. 4b: riwayat putus tersimpan, tombol hapus, tips, monitor bersama, versi 0.4.0 (di-push, build belum dicek).** Belum dibuktikan di HP: deteksi putus, kelangsungan di latar belakang, pengecualian baterai, notifikasi tetap menampilkan sinyal.
5. Mode Fokus Aplikasi (VpnService per aplikasi): pilih satu aplikasi, prioritaskan, opsi batasi aplikasi lain.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Tahap 1: rangka proyek + workflow. Build hijau.
- [x] Tahap 2: monitor jaringan. Commit `53cc975`, build hijau.
- [x] Tahap 3: uji kualitas. Commit `6ee1721`, build hijau (run 42782019449).
- [x] Perkiraan band NR dari ARFCN: build hijau, tampil "n40 (perkiraan)" di iQOO 15R.
- [x] Tahap 4a: build hijau (pengguna memasangnya). Screenshot 15:49: kartu Penjaga "Aktif", riwayat "15:45:41 Seluler tersedia", ikon notifikasi bulat "i" di status bar (kemungkinan notifikasi penjaga, belum dikonfirmasi isinya).
- [ ] Tahap 4a belum teruji penuh: (a) isi notifikasi tetap; (b) deteksi putus + notifikasi putus; (c) layanan bertahan setelah aplikasi ditutup dari recent apps; (d) tombol pengecualian baterai (di screenshot 15:49 masih tampil, artinya belum dikecualikan). Pengguna belum melaporkan hasil uji (a)-(d).
- [ ] **Konteks sinyal di kartu uji kualitas** (QualityTester.kt, QualityCard.kt, MonitorScreen.kt, MainActivity.kt) sudah di-push. Risiko kompilasi kecil: tipe parameter `kualitasRsrp`/`kualitasSinr` dan perbandingan `sinrNr < 0` (diasumsikan Int).
- [ ] **Tahap 4b** (StabilizerService.kt, GuardCard.kt, MonitorBersama.kt baru, MainActivity.kt, app/build.gradle.kts) sudah di-push. Catatan perilaku: format waktu riwayat putus kini ada tanggal, entri lama dari APK sebelumnya tidak terbawa; versi layar HP sekarang **v0.4.0**.
- [ ] **Riwayat hasil uji** (RiwayatUji.kt baru, MainActivity.kt diubah) sudah di-push. Risiko kompilasi kecil: interpolasi `nr?.rsrp` dan `nr?.sinr` di string (tipe apa pun aman), `Math.round(Double)`, `private set` pada properti `by mutableStateOf` di dalam `object`. Baris dicatat sekali tiap hasil uji selesai; bila Activity dibuat ulang (rotasi layar) saat hasil tampil, tester baru dibuat sehingga tidak dicatat dobel.
- **Status build (cek 2026-11-10 lewat badge):** badge workflow menunjukkan "passing" saat dicek setelah push riwayat uji, tetapi run terakhir mungkin belum selesai saat itu. Anggap build push Tahap 4b + MonitorBersama + versi 0.4.0 **belum terkonfirmasi** sampai pengguna memasang APK atau badge dicek ulang.
- [ ] POCO F5 belum diuji.
- [ ] Tahap 5 sampai 8 belum dikerjakan.

## Temuan perangkat (isi setelah pengguna menguji)
### iQOO 15R (operator XL, jaringan "5G NSA (LTE + NR)")
- Uji 15:29 (Lokasi MATI, baterai 48%): NR RSRP -94, RSRQ -12, SINR 10; LTE RSRP -96, RSRQ -16, SINR 8. Kartu sel kosong. Kualitas "Kurang stabil": 1.1.1.1 52.9 ms, jitter 31.3, gagal 5%; 8.8.8.8 41.8 ms, jitter 19.0; 9.9.9.9 18.8 ms, jitter 7.3. Unduh **157.9 Mbps**.
- Uji 15:34 (Lokasi aktif, 47%): NR RSRP -98, RSRQ -16, SINR **-2 sampai -3 (Buruk)**; LTE RSRP -100, RSRQ -16/-18, SINR 4-5. Sel: LTE B3 channel 1313 (PCI 15 terdaftar, 249, 79), NR channel 472110 PCI 15. Kualitas "Kurang stabil": 1.1.1.1 50.2 ms, jitter 24.4; 8.8.8.8 71.5 ms, jitter 17.5; 9.9.9.9 41.8 ms, jitter 7.3. Unduh **30.6 Mbps**.
- Uji 15:49 (app dengan Penjaga aktif, baterai 44%): NR RSRP **-107 (Lemah)**, RSRQ -14, SINR 0, level 3/4; LTE RSRP **-104 (Lemah)**, RSRQ -13, SINR 3. Sel: LTE B3 channel 1313 dengan PCI 15 (terdaftar), 250, 249, 116, 251; NR n40 (perkiraan) channel 472110 PCI 15. Kualitas **"Stabil"**: 1.1.1.1 16.0 ms, jitter 2.4; 8.8.8.8 29.4 ms, jitter 4.4; 9.9.9.9 15.8 ms, jitter 2.1; gagal 0% semua. Unduh **65.3 Mbps**.
- Pola dari 3 sampel: RSRP malah paling lemah di uji ke-3, tapi latency/jitter paling bagus. Latency dan kecepatan berubah-ubah antar menit, tidak mengikuti RSRP; SINR/beban sel lebih mungkin berperan. Semua sel LTE yang terlihat ada di B3 channel 1313 (satu frekuensi, banyak PCI tetangga), kondisi yang rawan interferensi sesama-frekuensi. Ini dugaan; daftar sel hanya berisi yang sempat terukur. Tiga sampel, belum cukup untuk kesimpulan.
- Baterai: 48% (15:29) ke 44% (15:49). Tidak bisa dikaitkan ke Penjaga karena layar menyala dan ada uji unduh.
- POCO F5: belum diuji.

## Langkah berikutnya
1. Minta pengguna memasang APK terbaru (Actions > run terbaru > `stabilizer-debug-apk`). Layar harus menampilkan **Stabilizer v0.4.0**. Jika build merah, minta log error dan perbaiki dulu. Jika hijau, uji di iQOO 15R: (a) "Mulai uji" lalu cek blok "Sinyal saat uji selesai"; (a2) setelah uji selesai, kartu "Riwayat uji kualitas" muncul dengan satu baris baru; ulangi uji beberapa kali, cek urutan terbaru di atas, tutup aplikasi sepenuhnya lalu buka lagi, riwayat uji masih ada, tombol "Hapus riwayat uji" berfungsi; (b) kartu Penjaga: teks tips tampil, riwayat bertanggal; matikan lalu hidupkan data seluler, cek riwayat "putus" dan "tersambung kembali (putus N dtk)"; tutup aplikasi sepenuhnya lalu buka lagi, cek riwayat masih ada (uji penyimpanan); tombol "Hapus riwayat" berfungsi; (c) screenshot isi notifikasi "Stabilizer aktif" dan cek RSRP di notifikasi ikut berubah; (d) tekan "Izinkan abaikan optimasi baterai"; (e) **uji monitor bersama:** nyalakan Penjaga, tutup aplikasi dari recent apps, tunggu 1-2 menit, cek notifikasi masih menampilkan sinyal yang berubah (artinya monitor tetap jalan di service setelah layar ditutup), lalu buka aplikasi lagi, kartu sinyal terisi; matikan Penjaga, sinyal di layar tetap terbarui; (f) layar mati 5-10 menit, cek notifikasi penjaga masih ada. Catat hasil di "Temuan perangkat". Cek juga apakah teks tips cocok dengan nama menu di HP; koreksi bila beda.
2. Setelah beberapa baris riwayat uji terkumpul di HP, baca polanya bersama pengguna (apakah latency/unduh mengikuti SINR, jam, atau sel). Opsional: grafik sederhana dari riwayat uji.
3. Lalu **Tahap 5** (Mode Fokus Aplikasi lewat VpnService). Jujur ke pengguna: ini mengurangi rebutan bandwidth antar aplikasi, tidak menambah kapasitas radio. Sebelum mulai, pastikan poin 1 minimal build hijau dan terpasang; Tahap 5 menyentuh Manifest dan butuh izin VPN dari pengguna, jadi risikonya lebih besar dari tahap sebelumnya.

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat.
- 2026-11-10: Tahap 1 di-push. Build hijau.
- 2026-11-10: Tahap 2 (monitor jaringan) di-push lewat git. minSdk jadi 31. Build hijau. APK tidak bisa diunduh dari sandbox; pengguna ambil dari Actions.
- 2026-11-10: Tahap 3 (uji kualitas) di-push. Build hijau.
- 2026-11-10: Pengguna mengirim screenshot uji HP pertama. Temuan dicatat. MonitorScreen.kt: petunjuk Lokasi mati di kartu sel.
- 2026-11-10: Pengguna memastikan perangkat = iQOO 15R, Lokasi dinyalakan, band/channel/PCI muncul. NetworkMonitor.kt: perkiraan band NR dari NR-ARFCN.
- 2026-11-10: Tahap 4a di-push lewat put_file (StabilizerService.kt, GuardCard.kt, AndroidManifest.xml, MonitorScreen.kt, MainActivity.kt).
- 2026-11-10: Pengguna memasang APK dengan 4a (build hijau). Screenshot 15:49: Penjaga aktif, band NR "n40 (perkiraan)" tampil, uji kualitas "Stabil". Belum ada uji putus/latar belakang/baterai. Temuan dicatat.
- 2026-11-10: Pengguna minta lanjut tanpa melaporkan sisa uji 4a. Ditambahkan konteks sinyal di kartu uji kualitas (QualityTester.kt, QualityCard.kt, MonitorScreen.kt, MainActivity.kt). Build belum dicek.
- 2026-11-10: Pengguna minta lanjut lagi tanpa laporan uji. Tahap 4b sebagian: riwayat putus tersimpan (SharedPreferences, 20 entri, bertanggal), tombol hapus, teks tips (StabilizerService.kt, GuardCard.kt). Build belum dicek.
- 2026-11-10: Pengguna minta lanjut lagi. Riwayat hasil uji: RiwayatUji.kt baru (object + RiwayatUjiCard), MainActivity.kt memanggil `RiwayatUji.catat` dan menampilkan kartu di slot penjaga. Build belum dicek.
- 2026-11-10: Pengguna minta lanjut lagi. Sisa Tahap 4b selesai: MonitorBersama.kt baru, StabilizerService.kt dan MainActivity.kt memakainya, versi jadi 0.4.0 (versionCode 5). Push berurutan: MonitorBersama, service, activity, build.gradle. Badge workflow dicek lewat curl: "passing" (belum pasti mencakup push terakhir). Catatan cara cek badge ditambahkan.
