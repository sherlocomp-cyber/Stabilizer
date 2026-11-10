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
- Cadangan tanpa add_repo: konektor `put_file` / `get_file` (server "Tes"). Satu commit per file, kirim berurutan (jangan paralel). `put_file` menimpa seluruh file, jadi baca dulu dengan `get_file` lalu kirim isi lengkap. Dengan cara ini status build Actions tidak bisa dipantau dari chat; minta pengguna melapor bila build merah.
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
- Menjaga stabilitas: foreground service, WakeLock, deteksi koneksi putus lewat NetworkCallback, notifikasi, minta pengecualian optimasi baterai.
- Mode Fokus Aplikasi lewat VpnService (addAllowedApplication / addDisallowedApplication): lalu lintas satu aplikasi diprioritaskan, aplikasi lain bisa dibatasi. Ini mengurangi rebutan bandwidth, **tidak** menambah kapasitas radio.
- Membuka pintasan pengaturan jaringan (dan menu info telepon bila ada di ROM tersebut).
- Opsional (tebakan, perlu diuji per perangkat): Shizuku untuk perintah privileged tanpa root.

## Keputusan teknis
- Kotlin + Jetpack Compose, Gradle Kotlin DSL. Package: `id.sherlocomp.stabilizer`.
- Versi yang dipakai (terbukti build hijau): AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.10.2, Java 17, compileSdk/targetSdk 35.
- **minSdk 31** supaya cukup memakai `TelephonyCallback`. Cocok untuk POCO F5 (Android 13+) dan iQOO 15R.
- Tanpa library tambahan (tanpa coroutines eksplisit): thread memakai `Executors` + `Handler`, status UI memakai `mutableStateOf`.
- Tidak butuh root.
- **Tidak ada Gradle wrapper** di repo (jar biner). Workflow memasang Gradle 8.10.2 dan menjalankan `gradle assembleDebug`.
- APK dibuat lewat **GitHub Actions** (`.github/workflows/build-apk.yml`), jalan otomatis di tiap push ke main. APK debug: tab Actions > run terbaru > Artifacts > `stabilizer-debug-apk`.
- Nama repo di GitHub memakai huruf besar `Stabilizer`; `stabilizer` juga diarahkan ke sana.

## Peta file
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- `app/build.gradle.kts` (di catatan lama versionName 0.3.0, versionCode 3; layar HP iQOO menampilkan **v0.3.1**, jadi cek isi file sebelum mengubah versi)
- `app/src/main/AndroidManifest.xml` (izin: READ_PHONE_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE, INTERNET)
- `app/src/main/java/id/sherlocomp/stabilizer/`
  - `MainActivity.kt`: minta izin, memulai/menghentikan monitor, membuat `QualityTester`, menampilkan `MonitorScreen`
  - `NetworkSnapshot.kt`: model data (`SignalMetrics`, `CellDetail`, `NetworkSnapshot`), `labelJaringan`, `kualitasRsrp`, `kualitasSinr`, `validOrNull`
  - `NetworkMonitor.kt`: `TelephonyCallback` (kekuatan sinyal + display info NSA/SA) dan `requestCellInfoUpdate` tiap 3 detik (band, channel, PCI). Bila band NR kosong, `nrBandDariArfcn()` memperkirakannya dari NR-ARFCN (label "perkiraan").
  - `QualityTester.kt`: model hasil, `ringkasan()`, uji latency TCP (3 host x 20 sampel), jitter, persen gagal, unduh opsional dari speed.cloudflare.com (maks 10 MB atau 8 detik)
  - `QualityCard.kt`: kartu UI uji kualitas (tombol mulai/batal, saklar unduh, hasil)
  - `MonitorScreen.kt`: layar Compose (jaringan, kartu sinyal 5G dan 4G, sel terdaftar, kartu uji kualitas). Menampilkan petunjuk merah di kartu sel bila Lokasi sistem mati.
- `app/src/main/res/values/strings.xml`, `themes.xml`
- `.github/workflows/build-apk.yml`

## Roadmap
1. Rangka proyek + workflow build APK. **Selesai, build hijau.**
2. Monitor jaringan: jenis 4G/5G, RSRP/RSRQ/SINR, band, operator. **Selesai. Sudah diuji di iQOO 15R: jenis jaringan, sinyal, band LTE, channel, PCI tampil. Band NR tampil "-" (diperbaiki dengan perkiraan dari ARFCN, belum dicek di HP).**
3. Uji kualitas: ping (TCP), jitter, packet loss, throughput singkat. **Selesai, sudah diuji di iQOO 15R, hasil tampil.** Riwayat grafik belum dibuat (ditunda ke Tahap 8 atau menyatu dengan Tahap 4).
4. Layanan penstabil (foreground service): WakeLock, deteksi putus, notifikasi, whitelist baterai.
5. Mode Fokus Aplikasi (VpnService per aplikasi): pilih satu aplikasi, prioritaskan, opsi batasi aplikasi lain.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Tahap 1: rangka proyek + workflow. Build hijau.
- [x] Tahap 2: monitor jaringan. Commit `53cc975`, build hijau.
- [x] Tahap 3: uji kualitas. Commit `6ee1721`, build hijau (run 42782019449).
- [x] Uji di iQOO 15R (2 screenshot, 15:29 dan 15:34): semua kartu jalan. Band/channel muncul setelah Lokasi dinyalakan. Lihat "Temuan perangkat".
- [x] Petunjuk Lokasi mati di kartu sel sudah di-push (MonitorScreen.kt). Dipakai pengguna? Versi di HP v0.3.1, belum pasti sudah memuat petunjuk ini.
- [ ] Perkiraan band NR dari NR-ARFCN di-push (NetworkMonitor.kt), **belum dicek hasil build Actions** dan belum diuji di HP.
- [ ] POCO F5 belum diuji.
- [ ] Tahap 4 sampai 8 belum dikerjakan.

## Temuan perangkat (isi setelah pengguna menguji)
### iQOO 15R (operator XL, jaringan "5G NSA (LTE + NR)")
- Uji 15:29 (Lokasi sistem MATI, baterai 48%):
  - NR: RSRP -94 dBm, RSRQ -12 dB, SINR 10 dB (Cukup). LTE: RSRP -96, RSRQ -16, SINR 8 (Cukup). Level 4/4 keduanya.
  - Kartu sel kosong. Diag: `Lokasi sistem: MATI | Sel dibaca: 0 (terdaftar 0, sumber cache)`.
  - Kualitas "Kurang stabil": 1.1.1.1 52.9 ms, jitter 31.3, gagal 5%; 8.8.8.8 41.8 ms, jitter 19.0, 0%; 9.9.9.9 18.8 ms, jitter 7.3, 0%. Unduh **157.9 Mbps**.
- Uji 15:34 (Lokasi sistem aktif, baterai 47%, app v0.3.1):
  - NR: RSRP -98 dBm, RSRQ -16 dB, SINR **-2 sampai -3 dB (Buruk)**. LTE: RSRP -100, RSRQ -16 sampai -18, SINR 4-5 dB (Cukup). Level 4/4.
  - Kartu sel (Lokasi aktif, sumber update, 4 sel, 1 terdaftar): LTE B3 channel 1313 (PCI 15 terdaftar, PCI 249, PCI 79); NR channel 472110 PCI 15, band "-". Jadi anchor LTE di B3 (1800 MHz). NR-ARFCN 472110 setara sekitar 2360,5 MHz, kemungkinan band n40 (2300 MHz TDD), ini hitungan, belum dikonfirmasi dari sistem. PCI NR sama dengan PCI LTE terdaftar (15), wajar untuk situs yang sama.
  - Kualitas "Kurang stabil": 1.1.1.1 50.2 ms, jitter 24.4, gagal 0%; 8.8.8.8 71.5 ms, jitter 17.5, 0%; 9.9.9.9 41.8 ms, jitter 7.3, 0%. Unduh **30.6 Mbps**.
- Kesimpulan sementara: kemungkinan kecil ini bug aplikasi. Kecepatan turun dari 157.9 ke 30.6 Mbps dalam 5 menit, bersamaan SINR NR turun dari +10 ke negatif dan RSRQ memburuk. Itu pola sinyal ber-interferensi atau sel padat (RSRP-nya sendiri tidak buruk), bukan sinyal lemah. Gagal 5% di 1.1.1.1 tidak berulang, jadi bukan masalah host tertentu. Dua sampel, belum cukup untuk memastikan.
- POCO F5: belum diuji.

## Langkah berikutnya
1. Minta pengguna memastikan APK terbaru terpasang (Actions > run terbaru hijau > `stabilizer-debug-apk`) dan melaporkan: apakah band NR sekarang tampil (mis. "n40 (perkiraan)"); apakah build merah (bila ya, kirim log). Cek hasil build commit "Perkirakan band NR dari NR-ARFCN".
2. Perbaikan kecil opsional: ubah logika `ringkasan()` di `QualityTester.kt` agar penilaian lebih informatif, misalnya ikut mempertimbangkan SINR dan menyebut penyebab ("SINR NR buruk, kemungkinan interferensi/sel padat") alih-alih hanya "Kurang stabil" (baca file dulu, jangan menebak).
3. Kerjakan **Tahap 4** (layanan penstabil). Rencana awal (tebakan, sesuaikan):
   - `StabilizerService` sebagai foreground service dengan notifikasi tetap (menampilkan jenis jaringan dan RSRP terakhir). Android 14+ mewajibkan tipe foreground service: pakai `foregroundServiceType="specialUse"` (sideload, tidak butuh Play Store) dengan `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`.
   - Izin baru di manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` (minta saat runtime di Android 13+), `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   - Pindahkan `NetworkMonitor` agar bisa hidup di service (bukan hanya di Activity).
   - `ConnectivityManager.registerNetworkCallback` untuk transport CELLULAR: catat waktu `onLost` / `onAvailable`, tampilkan notifikasi "koneksi seluler putus" dan riwayat putus.
   - Tombol "Izinkan abaikan optimasi baterai" yang membuka `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   - Jujur ke pengguna: ini menjaga proses tetap hidup dan memberi tahu saat putus; tidak memperbaiki sinyal radio.
4. Gagasan lanjutan (usulan, belum diminta pengguna): riwayat sinyal/kecepatan berkala supaya pola "SINR turun, kecepatan turun" bisa dilihat, bisa menyatu dengan Tahap 4.

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat.
- 2026-11-10: Tahap 1 di-push. Build hijau.
- 2026-11-10: Tahap 2 (monitor jaringan) di-push lewat git. minSdk jadi 31. Build hijau. APK tidak bisa diunduh dari sandbox; pengguna ambil dari Actions.
- 2026-11-10: Tahap 3 (uji kualitas) di-push. Build hijau.
- 2026-11-10: Pengguna mengirim screenshot uji HP pertama. Temuan dicatat. MonitorScreen.kt: petunjuk Lokasi mati di kartu sel (di-push lewat put_file, build belum dicek).
- 2026-11-10: Pengguna memastikan perangkat = iQOO 15R, Lokasi dinyalakan, band/channel/PCI muncul. NetworkMonitor.kt: perkiraan band NR dari NR-ARFCN (di-push lewat put_file, build belum dicek). Temuan uji 15:34 dicatat.
