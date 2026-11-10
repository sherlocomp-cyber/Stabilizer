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
- Cadangan tanpa add_repo: konektor `put_file` / `get_file` (server "Tes"). Satu commit per file, kirim berurutan (jangan paralel). `put_file` menimpa seluruh file, jadi baca dulu dengan `get_file` lalu kirim isi lengkap.
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
- Memantau: jenis jaringan (LTE/NR), indikasi NSA/SA (TelephonyDisplayInfo), RSRP/RSRQ/SINR, band/ARFCN dari cell info. Perlu izin READ_PHONE_STATE dan lokasi.
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
- `app/build.gradle.kts` (versionName 0.3.0, versionCode 3)
- `app/src/main/AndroidManifest.xml` (izin: READ_PHONE_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE, INTERNET)
- `app/src/main/java/id/sherlocomp/stabilizer/`
  - `MainActivity.kt`: minta izin, memulai/menghentikan monitor, membuat `QualityTester`, menampilkan `MonitorScreen`
  - `NetworkSnapshot.kt`: model data (`SignalMetrics`, `CellDetail`, `NetworkSnapshot`), `labelJaringan`, `kualitasRsrp`, `kualitasSinr`, `validOrNull`
  - `NetworkMonitor.kt`: `TelephonyCallback` (kekuatan sinyal + display info NSA/SA) dan `requestCellInfoUpdate` tiap 3 detik (band, channel, PCI)
  - `QualityTester.kt`: model hasil, `ringkasan()`, uji latency TCP (3 host x 20 sampel), jitter, persen gagal, unduh opsional dari speed.cloudflare.com (maks 10 MB atau 8 detik)
  - `QualityCard.kt`: kartu UI uji kualitas (tombol mulai/batal, saklar unduh, hasil)
  - `MonitorScreen.kt`: layar Compose (jaringan, kartu sinyal 5G dan 4G, sel terdaftar, kartu uji kualitas). Menampilkan petunjuk merah di kartu sel bila Lokasi sistem mati.
- `app/src/main/res/values/strings.xml`, `themes.xml`
- `.github/workflows/build-apk.yml`

## Roadmap
1. Rangka proyek + workflow build APK. **Selesai, build hijau.**
2. Monitor jaringan: jenis 4G/5G, RSRP/RSRQ/SINR, band, operator. **Selesai, build hijau. Sudah diuji di 1 HP (sinyal tampil), band/channel belum.**
3. Uji kualitas: ping (TCP), jitter, packet loss, throughput singkat. **Selesai, build hijau. Sudah diuji di 1 HP (hasil tampil).** Riwayat grafik belum dibuat (ditunda ke Tahap 8 atau menyatu dengan Tahap 4).
4. Layanan penstabil (foreground service): WakeLock, deteksi putus, notifikasi, whitelist baterai.
5. Mode Fokus Aplikasi (VpnService per aplikasi): pilih satu aplikasi, prioritaskan, opsi batasi aplikasi lain.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Tahap 1: rangka proyek + workflow. Build hijau.
- [x] Tahap 2: monitor jaringan. Commit `53cc975`, build hijau.
- [x] Tahap 3: uji kualitas. Commit `6ee1721`, build hijau (run 42782019449).
- [x] Uji HP pertama (screenshot 2026-11-10 15:29, perangkat belum disebut): sinyal, uji kualitas, dan unduh jalan. Lihat "Temuan perangkat".
- [ ] Perbaikan kecil: petunjuk Lokasi mati di kartu sel sudah di-push (MonitorScreen.kt), **belum dicek hasil build Actions**.
- [ ] Belum ada hasil uji di POCO F5 / iQOO 15R yang dipastikan per perangkat.
- [ ] Tahap 4 sampai 8 belum dikerjakan.

## Temuan perangkat (isi setelah pengguna menguji)
- Uji 2026-11-10 15:29 (perangkat belum disebut; status bar menampilkan 5G, baterai 48%):
  - NR: RSRP -94 dBm (Cukup), RSRQ -12 dB, SINR 10 dB (Cukup), level 4/4.
  - LTE: RSRP -96 dBm (Cukup), RSRQ -16 dB, SINR 8 dB (Cukup), level 4/4.
  - Kartu "Sel terdeteksi": **kosong**. Diag: `Sub data: 2 | Lokasi sistem: MATI | Sel dibaca: 0 (terdaftar 0, sumber cache)`. Kemungkinan besar penyebabnya Lokasi sistem mati, jadi cell info tidak diberikan Android. Perlu dicek ulang dengan Lokasi menyala.
  - Uji kualitas: penilaian "Kurang stabil". 1.1.1.1: 52.9 ms, jitter 31.3, gagal 5% dari 20. 8.8.8.8: 41.8 ms, jitter 19.0, gagal 0%. 9.9.9.9: 18.8 ms, jitter 7.3, gagal 0%. Unduh 157.9 Mbps.
  - Catatan: jitter tinggi dan gagal 5% hanya di 1.1.1.1, sedangkan 9.9.9.9 bagus. Bisa jalur ke Cloudflare yang kurang baik atau sinyal sedang fluktuatif (RSRQ -16 dB di LTE cukup rendah). Satu sampel, belum bisa disimpulkan. Penilaian gabungan saat ini mungkin terlalu keras bila satu host saja yang bermasalah; pertimbangkan menilai dari median/host terbaik.
- iQOO 15R: belum diuji (atau belum dipastikan)
- POCO F5: belum diuji (atau belum dipastikan)

## Langkah berikutnya
1. Minta pengguna: (a) menyebut HP yang dipakai pada uji tadi; (b) menyalakan Lokasi, lalu mengecek apakah band/channel/PCI muncul di kartu sel; (c) mengulang "Mulai uji" 2-3 kali pada waktu berbeda untuk melihat apakah 1.1.1.1 konsisten buruk; (d) mengunduh APK terbaru dari Actions (run terbaru hijau, artifact `stabilizer-debug-apk`) bila ingin mencoba petunjuk Lokasi. Catat hasilnya di "Temuan perangkat". Cek juga status build Actions untuk commit petunjuk Lokasi.
2. Perbaikan kecil opsional: ubah logika `ringkasan()` di `QualityTester.kt` agar penilaian tidak terlalu keras bila hanya satu host yang buruk (baca file dulu, jangan menebak).
3. Kerjakan **Tahap 4** (layanan penstabil). Rencana awal (tebakan, sesuaikan):
   - `StabilizerService` sebagai foreground service dengan notifikasi tetap (menampilkan jenis jaringan dan RSRP terakhir). Android 14+ mewajibkan tipe foreground service: pakai `foregroundServiceType="specialUse"` (sideload, tidak butuh Play Store) dengan `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`.
   - Izin baru di manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` (minta saat runtime di Android 13+), `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   - Pindahkan `NetworkMonitor` agar bisa hidup di service (bukan hanya di Activity).
   - `ConnectivityManager.registerNetworkCallback` untuk transport CELLULAR: catat waktu `onLost` / `onAvailable`, tampilkan notifikasi "koneksi seluler putus" dan riwayat putus.
   - Tombol "Izinkan abaikan optimasi baterai" yang membuka `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   - Jujur ke pengguna: ini menjaga proses tetap hidup dan memberi tahu saat putus; tidak memperbaiki sinyal radio.

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat.
- 2026-11-10: Tahap 1 di-push. Build hijau.
- 2026-11-10: Tahap 2 (monitor jaringan) di-push lewat git. minSdk jadi 31. Build hijau. APK tidak bisa diunduh dari sandbox; pengguna ambil dari Actions.
- 2026-11-10: Tahap 3 (uji kualitas) di-push. Build hijau.
- 2026-11-10: Pengguna mengirim screenshot uji HP pertama. Temuan dicatat. MonitorScreen.kt: petunjuk Lokasi mati di kartu sel (di-push lewat put_file, build belum dicek).
