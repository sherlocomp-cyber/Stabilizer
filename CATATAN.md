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
- Cadangan tanpa add_repo: konektor `put_file` / `get_file` (server "Tes"). Satu commit per file, kirim berurutan (jangan paralel).
- Setiap commit diakhiri trailer:
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`
- Sandbox tidak punya Android SDK. Kode tidak bisa dikompilasi lokal; satu-satunya uji kompilasi adalah GitHub Actions. Tulis kode hati-hati, lalu cek hasil run.

## Batas teknis (penting, jangan dijanjikan berlebihan ke pengguna)
Aplikasi biasa tanpa root **tidak bisa**:
- Menambah kekuatan sinyal radio, mengunci band, atau memaksa 5G SA/NSA. API-nya hanya untuk aplikasi sistem atau privileged.
- Mengubah preferred network type (butuh izin MODIFY_PHONE_STATE).
- Menyalakan/mematikan mode pesawat lewat kode.
- Mengedit APN langsung (butuh izin privileged). Hanya bisa membuka layar pengaturannya.

Aplikasi biasa **bisa**:
- Memantau: jenis jaringan (LTE/NR), indikasi NSA/SA (TelephonyDisplayInfo), RSRP/RSRQ/SINR, band/ARFCN dari cell info. Perlu izin READ_PHONE_STATE dan lokasi.
- Mengukur kualitas: latency, jitter, packet loss, throughput singkat.
- Menjaga stabilitas: foreground service, WakeLock, deteksi koneksi putus lewat NetworkCallback, notifikasi, minta pengecualian optimasi baterai.
- Mode Fokus Aplikasi lewat VpnService (addAllowedApplication / addDisallowedApplication): lalu lintas satu aplikasi diprioritaskan, aplikasi lain bisa dibatasi. Ini mengurangi rebutan bandwidth, **tidak** menambah kapasitas radio.
- Membuka pintasan pengaturan jaringan (dan menu info telepon bila ada di ROM tersebut).
- Opsional (tebakan, perlu diuji per perangkat): Shizuku untuk perintah privileged tanpa root.

## Keputusan teknis
- Kotlin + Jetpack Compose, Gradle Kotlin DSL. Package: `id.sherlocomp.stabilizer`.
- Versi yang dipakai (terbukti build hijau): AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.10.2, Java 17, compileSdk/targetSdk 35.
- **minSdk 31** (naik dari 29 di Tahap 2) supaya cukup memakai `TelephonyCallback`, tanpa jalur `PhoneStateListener` lama. Cocok untuk POCO F5 (Android 13+) dan iQOO 15R.
- Tidak butuh root.
- **Tidak ada Gradle wrapper** di repo (jar biner). Workflow memasang Gradle 8.10.2 dan menjalankan `gradle assembleDebug`.
- APK dibuat lewat **GitHub Actions** (`.github/workflows/build-apk.yml`), jalan otomatis di tiap push ke main. APK debug: tab Actions > run terbaru > Artifacts > `stabilizer-debug-apk`.
- Nama repo di GitHub memakai huruf besar `Stabilizer`; `stabilizer` juga diarahkan ke sana.

## Peta file
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- `app/build.gradle.kts` (versionName 0.2.0)
- `app/src/main/AndroidManifest.xml` (izin: READ_PHONE_STATE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_NETWORK_STATE; **belum ada INTERNET**)
- `app/src/main/java/id/sherlocomp/stabilizer/`
  - `MainActivity.kt`: minta izin, memulai/menghentikan monitor, menampilkan `MonitorScreen`
  - `NetworkSnapshot.kt`: model data (`SignalMetrics`, `CellDetail`, `NetworkSnapshot`), `labelJaringan`, `kualitasRsrp`, `kualitasSinr`, `validOrNull`
  - `NetworkMonitor.kt`: `TelephonyCallback` (kekuatan sinyal + display info NSA/SA) dan `requestCellInfoUpdate` tiap 3 detik (band, channel, PCI)
  - `MonitorScreen.kt`: layar Compose (jaringan, kartu sinyal 5G dan 4G, sel terdaftar)
- `app/src/main/res/values/strings.xml`, `themes.xml`
- `.github/workflows/build-apk.yml`

## Roadmap
1. Rangka proyek + workflow build APK. **Selesai, build hijau.**
2. Monitor jaringan: jenis 4G/5G, RSRP/RSRQ/SINR, band, operator. **Kode selesai, build hijau. Belum diuji di HP.**
3. Uji kualitas: ping, jitter, packet loss, throughput singkat, riwayat grafik sederhana.
4. Layanan penstabil (foreground service): WakeLock, deteksi putus, notifikasi, whitelist baterai.
5. Mode Fokus Aplikasi (VpnService per aplikasi): pilih satu aplikasi, prioritaskan, opsi batasi aplikasi lain.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Tahap 1: rangka proyek + workflow. Build hijau (dua run sukses).
- [x] Tahap 2: monitor jaringan. Commit `53cc975`, build hijau (run 42781221654).
- [ ] Belum ada hasil uji di HP asli (iQOO 15R, POCO F5). Risiko: nilai kosong atau berbeda di ROM tertentu, atau data band/channel tidak keluar.
- [ ] Tahap 3 sampai 8 belum dikerjakan.

## Temuan perangkat (isi setelah pengguna menguji)
- iQOO 15R: belum diuji
- POCO F5: belum diuji

## Langkah berikutnya
1. Minta pengguna mengunduh APK Tahap 2 (GitHub > Actions > run terbaru yang hijau > Artifacts > `stabilizer-debug-apk`), memasangnya, memberi izin Telepon dan Lokasi, lalu melaporkan untuk tiap HP: apakah jenis jaringan, RSRP/RSRQ/SINR, band, channel muncul atau kosong, dan apa yang tertulis. Catat di "Temuan perangkat". Kalau ada yang kosong atau salah, perbaiki `NetworkMonitor.kt` sebelum lanjut.
2. Kerjakan **Tahap 3** (uji kualitas). Rencana awal (tebakan, sesuaikan):
   - Tambah izin `android.permission.INTERNET` di manifest.
   - Kelas `QualityTester.kt`: ukur latency lewat waktu connect TCP ke beberapa host (ICMP ping butuh root), ambil sekitar 20 sampel, hitung rata-rata, jitter, dan persen gagal (packet loss versi TCP).
   - Throughput singkat: unduh file kecil dari server publik dan hitung Mbps. Pakai coroutine atau thread terpisah, jangan di thread utama.
   - Layar baru atau kartu baru di `MonitorScreen`, dengan tombol "Mulai uji".

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat.
- 2026-11-10: Tahap 1 di-push. Build hijau.
- 2026-11-10: Tahap 2 (monitor jaringan) di-push lewat git. minSdk jadi 31. Build hijau. APK tidak bisa diunduh dari sandbox; pengguna ambil dari Actions.
