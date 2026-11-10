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
- minSdk 29, targetSdk 35 (tebakan aman; sesuaikan bila build gagal).
- Tidak butuh root.
- APK dibuat lewat **GitHub Actions** (`.github/workflows/build-apk.yml`), karena sandbox chat tidak punya Android SDK dan jaringan Bash terbatas. APK debug diunduh dari tab Actions > Artifacts. Cukup untuk pemakaian pribadi.
- Push ke repo lewat konektor `put_file` (nama server: Tes). `git clone` anonim di sandbox hanya bisa baca.

## Roadmap
1. Rangka proyek Android + workflow GitHub Actions yang menghasilkan APK debug (layar kosong "Stabilizer").
2. Monitor jaringan: jenis 4G/5G, RSRP/RSRQ/SINR, band, operator, tampil real-time.
3. Uji kualitas: ping, jitter, packet loss, throughput singkat, riwayat grafik sederhana.
4. Layanan penstabil (foreground service): WakeLock, deteksi putus, notifikasi, whitelist baterai.
5. Mode Fokus Aplikasi (VpnService per aplikasi): pilih satu aplikasi, prioritaskan, opsi batasi aplikasi lain.
6. Pintasan dan profil per perangkat (iQOO 15R, POCO F5) + tips pengaturan.
7. Opsional: integrasi Shizuku.
8. Rapikan UI, ikon, rilis APK.

## Status
- [x] Repo dibuat, handoff dan roadmap ditulis (file ini).
- [ ] Tahap 1 sampai 8 belum dikerjakan. Belum ada kode.

## Langkah berikutnya
**Tahap 1**: buat rangka proyek Android (settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, AndroidManifest.xml, MainActivity.kt dengan Compose) dan workflow `.github/workflows/build-apk.yml` yang menjalankan `./gradlew assembleDebug` dan mengunggah APK sebagai artifact. Setelah push, minta pengguna membuka tab Actions untuk memastikan build hijau dan mengunduh APK.

## Log
- 2026-11-10: repo kosong; CATATAN.md pertama dibuat.
