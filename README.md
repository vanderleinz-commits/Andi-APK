# Andi Android · trial 0.4.4

Push proyek ke repo GitHub dengan GitHub Actions aktif untuk membangun APK di server GitHub. Workflow **Build Andi debug APK** berjalan saat push ke `main` dan dapat dijalankan manual dari tab **Actions**. Setelah build sukses, unduh artefak **Andi-debug**, ekstrak, lalu buka `Andi-debug.apk` di HP.

Andi adalah robot pari berwarna dark blue dan cocoa brown. Ketuk untuk menu cepat, ketuk dua kali untuk kartu musik, geser untuk memindahkan Andi, atau tahan tiga detik untuk menyembunyikan Andi. Setelah didiamkan, Andi tidur. Bubble mengikuti posisi Andi saat melayang.

## Build APK debug

1. Buka folder `Andi-Android` di Android Studio.
2. Gunakan JDK 17 atau JDK 21 dan pasang Android SDK Platform 35 serta Build Tools 35.0.0 lewat SDK Manager.
3. Sinkronkan proyek saat PC terhubung ke internet agar Gradle dapat mengambil dependensi. Proyek sudah menyertakan Gradle Wrapper 8.11.1.
4. Di terminal Windows dalam folder proyek, jalankan `.\gradlew.bat :app:assembleDebug`. Hasilnya ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Coba di HP

Trial ini menargetkan Android 8.0 ke atas. Buka APK debug yang sudah dibuat, lalu ikuti langkah berikut.

1. Buka **Izin & panduan instalasi**, pilih **Tampil di atas aplikasi**, aktifkan izin untuk Andi, lalu kembali dan pilih **Tampilkan Andi**.
2. Dari halaman yang sama, aktifkan **Akses notifikasi dan musik**. Status izin diperbarui saat kembali dari Setelan Android. **Panduan pemasangan APK** mengarah ke unduhan build di GitHub.
3. Dengan layar terbuka, kirim pesan percakapan dari aplikasi seperti WhatsApp atau Discord. Bubble menampilkan ikon, nama pengirim jika tersedia, dan pesan. Notifikasi aplikasi biasa tampil dengan judul dan isi aslinya, sementara notifikasi sistem dan notifikasi yang berlangsung terus tetap berada di panel Android. Bubble tampil selama 3,5 detik lalu kembali ke kartu musik jika tersedia.
4. Putar lagu dari aplikasi musik untuk melihat CD berputar. Foto album muncul jika pemutar menyediakannya. Judul lagu dan nama artis tampil jika pemutar menyediakannya. Ketuk Andi dua kali untuk menyembunyikan atau membuka kartu musik. Geser Andi untuk melihat kartu pindah sisi. Coba juga tombol jeda, lanjut, sebelumnya, dan berikutnya.
5. Geser Andi, putar layar, lalu tahan karakter selama tiga detik untuk menyembunyikan. Beberapa layar sistem dan aplikasi dapat menyembunyikan overlay.

Aktifkan **Mode Tenang** untuk menjeda bubble notifikasi. Buka **Notifikasi per aplikasi** untuk memilih **Sembunyikan**, **Ikon saja**, **Nama pengirim (percakapan)**, atau **Pratinjau lengkap**. Untuk notifikasi biasa, pilihan nama pengirim hanya menampilkan ikon. Perubahan membersihkan pratinjau yang masih tampil atau antre.

Ketuk bubble untuk membuka tujuan yang disediakan aplikasi, atau geser bubble ke samping untuk menutupnya. Aktifkan **Menepi otomatis** agar Andi menepi ketika Android melaporkan keyboard atau tampilan layar penuh. Ketika **Mode Tenang** berakhir, Andi menampilkan jumlah notifikasi per aplikasi. **Mulai fokus 25 menit** menepikan Andi hingga sesi selesai atau dihentikan.

Izin akses notifikasi Android terpisah dari izin Andi untuk menampilkan notifikasinya sendiri. Akses notifikasi dan sesi musik diproses di perangkat, tanpa menyimpan isi pesan. Build APK dan perilaku perangkat asli perlu diuji dengan toolchain Android dan HP yang terhubung.

## Tampilan Andi

Dashboard menggunakan palet navy, cocoa, dan mint dengan outline serta bayangan tegas. **Kacamata Andi** mengubah tampilan karakter di dashboard dan saat melayang. Saat melayang, Andi tampil langsung di atas layar dengan latar transparan. **Animasi bubble** mengatur gerakan ikon pintasan serta transisi kartu.

## Menu cepat dan pengingat

Ketuk Andi sekali untuk membuka atau menutup ikon pintasan yang menyebar ke sisi layar yang lebih lapang. Ketuk dua kali untuk membuka atau menyembunyikan kartu musik, dan tahan tiga detik untuk menyembunyikan Andi. Kartu musik punya tombol sebelumnya, putar atau jeda, berikutnya, acak, dan ulang jika pemutar mendukungnya.

Buka **Kalender cepat**, pilih tanggal serta jam, lalu isi nama acara. Android dapat meminta izin **Alarm & pengingat** dan izin notifikasi. Saat pengingat berbunyi, Andi mencoba muncul selama 10 detik meskipun sebelumnya tersembunyi. Notifikasi sistem menjadi cadangan ketika layar terkunci atau sistem membatasi overlay. Pengingat yang belum berbunyi dijadwalkan lagi setelah reboot.

Tombol **Daya** membuka menu daya Android. **Kunci** dan **Screenshot** memerlukan Android 9 atau lebih baru. Aktifkan **Andi · Tombol cepat** melalui menu aksesibilitas untuk memakai ketiga aksi tersebut. Layanan tombol cepat tidak membaca isi layar. Home dan senter tersedia langsung jika didukung perangkat.

## Pintasan dan lirik

Buka **Pilih pintasan menu** untuk memilih ikon yang muncul saat Andi diketuk. Di kartu musik, ketuk ikon lirik untuk memunculkan panel di bawah judul, artis, dan CD. Setelah izin pencarian disetujui, Andi mencari lirik lagu aktif di LRCLIB menggunakan judul dan artis selama panel ditampilkan. Lirik mengikuti posisi lagu jika data waktu tersedia.

Jika muncul **Restricted setting**, dari **Izin & panduan instalasi** pilih **Buka info aplikasi Andi**, ketuk ⋮, pilih **Allow restricted settings**, lalu aktifkan akses yang diminta melalui Setelan Android. Setelah Aksesibilitas **Andi · Tombol cepat** aktif, tombol Daya, Kunci, dan Screenshot memanggil aksi sistem. Daya tetap meminta pilihan pada menu daya.
