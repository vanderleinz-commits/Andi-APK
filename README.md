# Andi Android · trial 0.1

Push proyek ke repo GitHub dengan GitHub Actions aktif untuk membangun APK di server GitHub. Workflow **Build Andi debug APK** berjalan saat push ke `main` dan dapat dijalankan manual dari tab **Actions**. Setelah build sukses, unduh artefak **Andi-debug**, ekstrak, lalu buka `Andi-debug.apk` di HP.

Andi adalah robot pari berwarna dark blue dan cocoa brown. Ketuk untuk menyapa, geser untuk memindahkan, atau tahan untuk menyembunyikan. Setelah didiamkan, Andi tidur. Bubble mengikuti posisi Andi saat melayang.

## Build APK debug

1. Buka folder `Andi-Android` di Android Studio.
2. Gunakan JDK 17 atau JDK 21 dan pasang Android SDK Platform 35 serta Build Tools 35.0.0 lewat SDK Manager.
3. Sinkronkan proyek saat PC terhubung ke internet agar Gradle dapat mengambil dependensi. Proyek sudah menyertakan Gradle Wrapper 8.11.1.
4. Di terminal Windows dalam folder proyek, jalankan `.\gradlew.bat :app:assembleDebug`. Hasilnya ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Coba di HP

Trial ini menargetkan Android 8.0 ke atas. Buka APK debug yang sudah dibuat, lalu ikuti langkah berikut.

1. Pilih **Buka pengaturan izin**, aktifkan **Tampilkan di atas aplikasi lain** untuk Andi, kembali ke app, lalu pilih **Tampilkan Andi**.
2. Pilih **Akses notifikasi dan musik** dan aktifkan Andi di pengaturan Android jika ingin mencoba bubble notifikasi dan musik.
3. Buka aplikasi lain, kirim pesan tes ke HP, lalu lihat apakah bubble menampilkan nama aplikasi dan hilang sekitar lima detik. Isi pesan tidak ditampilkan atau disimpan.
4. Putar lagu dari aplikasi musik. Ketuk bubble judul untuk melihat hingga empat judul dari antrean jika pemutar memberikannya ke Android. Coba jeda, lanjutkan, dan ganti lagu.
5. Geser Andi, putar layar, lalu tahan karakter untuk menyembunyikan. Beberapa layar sistem dan aplikasi dapat menyembunyikan overlay.

Izin akses notifikasi Android terpisah dari izin Andi untuk menampilkan notifikasinya sendiri. Akses notifikasi dan sesi musik diproses di perangkat, tanpa menyimpan isi pesan. Build APK dan perilaku perangkat asli perlu diuji dengan toolchain Android dan HP yang terhubung.
