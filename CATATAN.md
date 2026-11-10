# CATATAN: Stabilizer (handoff antar akun)

Terakhir diperbarui: 2026-11-10

## Tujuan
Aplikasi Android (APK) untuk membantu menstabilkan koneksi 5G XL di **iQOO 15R** dan **POCO F5**.
Fitur yang diminta pengguna: memaksimalkan koneksi untuk satu aplikasi, dan fitur pendukung lain.

## Cara kerja (aturan handoff)
- Satu langkah per giliran. Setelah tiap langkah: push ke repo ini, lalu perbarui bagian **Status** dan **Langkah berikutnya** di file ini.
- Kuota hampir habis: hentikan pekerjaan baru, perbarui CATATAN.md, push. Jangan mulai langkah besar.
- Akun lain: baca file ini saja, lalu kerjakan **Langkah berikutnya**. Jangan menjelajah ulang seluruh repo.
- Jangan menulis kode panjang di perintah bash (heredoc). Tulis lewat alat pembuat file atau konektor put_file.
- put_file membuat satu commit per file. Kirim berurutan, jangan paralel, supaya tidak bentrok.

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
- Versi yang dipakai (tebakan aman, sesuaikan bila build gagal): AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.10.2, Java 17, compileSdk/targetSdk 35, minSdk 29.
- Tidak butuh root.
- **Tidak ada Gradle wrapper** di repo (file jar biner tidak bisa dikirim lewat put_file). Workflow memasang Gradle 8.10.2 langsung dan menjalankan `gradle assembleDebug`.
- APK dibuat lewat **GitHub Actions** (`.github/workflows/build-apk.yml`), karena sandbox chat tidak punya Android SDK dan jaringan Bash terbatas. APK debug diunduh dari tab Actions > run terbaru > Artifacts > `stabilizer-debug-apk`.
- Push ke repo lewat konektor `put_file` (nama server: Tes). `git clone` anonim di sandbox hanya bisa baca.

## Peta file (Tahap 1)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/id/sherlocomp/stabilizer/MainActivity.kt` (layar awal Compose)
- `app/src/main/res/values/strings.xml`, `themes.xml`
- `.github/workflows/build-apk.yml`

## Roadmap
1. Rangka proyek Android + workflow GitHub Actions yang menghasilkan APK debug (layar kosong "Stabilizer"). **Kode terkirim, build belum diverifikasi.**
2. Monitor jaringan: jenis 4G/5G, RSRP/RSRQ/SINR, band, operator, tampil real-time.
3. Uji kualitas: ping, jitter, packet loss, throughput singkat, riwayat grafik sederhana.
4. Layanan penstabil (foreground service): WakeLock, deteksi putus, notifikasi, whitelist baterai.
5. Mode Fokus Aplikasi (VpnService per aplikasi): pilih satu aplikasi, prioritaskan, opsi batasi aplikasi lain.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Repo dibuat, handoff dan roadmap ditulis.
- [x] Tahap 1: semua file rangka proyek dan workflow sudah di-push.
- [ ] Tahap 1 belum terbukti jalan: belum ada yang mengecek tab Actions. Ada kemungkinan build gagal karena versi dependensi atau izin push file workflow.
- [ ] Tahap 2 sampai 8 belum dikerjakan.

## Langkah berikutnya
1. Minta pengguna membuka repo di GitHub > tab **Actions** dan melihat run "Build APK".
   - Merah: minta pengguna menempelkan pesan error (baris merah di langkah "Build debug APK"). Perbaiki file yang bermasalah lewat put_file, lalu tunggu run baru.
   - Tidak ada run sama sekali: kemungkinan Actions nonaktif di repo, atau file workflow tidak terbaca. Cek `.github/workflows/build-apk.yml` ada di repo; kalau tidak, minta pengguna menambahkannya manual lewat GitHub web (Add file > Create new file) dengan isi dari catatan ini.
   - Hijau: pengguna mengunduh APK dari Artifacts dan mencoba memasangnya di HP (aktifkan "pasang dari sumber tidak dikenal"). Layar harus menampilkan "Stabilizer / Tahap 1: rangka proyek".
2. Setelah build terbukti hijau, kerjakan **Tahap 2** (monitor jaringan). Mulai dengan izin READ_PHONE_STATE dan ACCESS_FINE_LOCATION di manifest, lalu satu kelas `NetworkMonitor.kt` yang membaca TelephonyManager, dan satu layar Compose untuk menampilkannya.

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat.
- 2026-11-10: Tahap 1 di-push (rangka Gradle/Compose + workflow build APK). Build belum dicek.
