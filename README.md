# Andi Android · trial 0.2.4

Push proyek ke repo GitHub dengan GitHub Actions aktif untuk membangun APK di server GitHub. Workflow **Build Andi debug APK** berjalan saat push ke `main` dan dapat dijalankan manual dari tab **Actions**. Setelah build sukses, unduh artefak **Andi-debug**, ekstrak, lalu buka `Andi-debug.apk` di HP.

Andi adalah robot pari berwarna dark blue dan cocoa brown. Ketuk untuk membuka atau menyembunyikan kartu musik, geser untuk memindahkan Andi, atau tahan tiga detik untuk menyembunyikan Andi. Setelah didiamkan, Andi tidur. Bubble mengikuti posisi Andi saat melayang.

## Build APK debug

1. Buka folder `Andi-Android` di Android Studio.
2. Gunakan JDK 17 atau JDK 21 dan pasang Android SDK Platform 35 serta Build Tools 35.0.0 lewat SDK Manager.
3. Sinkronkan proyek saat PC terhubung ke internet agar Gradle dapat mengambil dependensi. Proyek sudah menyertakan Gradle Wrapper 8.11.1.
4. Di terminal Windows dalam folder proyek, jalankan `.\gradlew.bat :app:assembleDebug`. Hasilnya ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Coba di HP

Trial ini menargetkan Android 8.0 ke atas. Buka APK debug yang sudah dibuat, lalu ikuti langkah berikut.

1. Pilih **Buka pengaturan izin**, aktifkan **Tampilkan di atas aplikasi lain** untuk Andi, kembali ke app, lalu pilih **Tampilkan Andi**.
2. Pilih **Akses notifikasi dan musik** dan aktifkan Andi di pengaturan Android jika ingin mencoba bubble notifikasi dan musik.
3. Dengan layar terbuka, kirim pesan tes ke HP. Bubble menampilkan ikon aplikasi, nama pengirim jika tersedia, dan isi pesan selama 3,5 detik, lalu kembali ke kartu musik jika tersedia.
4. Putar lagu dari aplikasi musik untuk melihat CD berputar. Foto album muncul jika pemutar menyediakannya. Judul lagu dan nama artis tampil jika pemutar menyediakannya. Ketuk Andi sekali untuk menyembunyikan kartu musik, dan sekali lagi untuk membukanya. Geser Andi untuk melihat kartu pindah sisi. Coba juga tombol jeda, lanjut, sebelumnya, dan berikutnya.
5. Geser Andi, putar layar, lalu tahan karakter selama tiga detik untuk menyembunyikan. Beberapa layar sistem dan aplikasi dapat menyembunyikan overlay.

Izin akses notifikasi Android terpisah dari izin Andi untuk menampilkan notifikasinya sendiri. Akses notifikasi dan sesi musik diproses di perangkat, tanpa menyimpan isi pesan. Build APK dan perilaku perangkat asli perlu diuji dengan toolchain Android dan HP yang terhubung.
