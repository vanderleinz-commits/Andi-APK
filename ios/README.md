# Andi untuk iPhone

Andi iOS 0.1.0 mendukung iOS 17 ke atas. Pilih Andi Klasik, Momo, Piko, atau Lumi dari layar utama. Karakter pilihan tersimpan dan ikut tampil pada Live Activity Mode Fokus.

Mulai sesi fokus 25 menit untuk melihat hitung mundur. Jika Live Activities aktif, hitung mundur muncul di Lock Screen dan Dynamic Island pada perangkat yang mendukung. Setelah izin notifikasi diberikan, iOS mengirim pengingat ketika sesi selesai. Widget layar utama membuka Andi.

Andi memakai tampilan resmi iOS untuk aktivitas fokus. Overlay bebas di atas aplikasi lain, kartu musik Android lintas aplikasi, dan pembacaan notifikasi dari aplikasi lain adalah fitur Android.

## Jalankan di Mac

1. Pasang Xcode dan XcodeGen, lalu jalankan `xcodegen generate` dari direktori `ios`.
2. Buka `Andi.xcodeproj` dan jalankan scheme **Andi** di iPhone Simulator.
3. Untuk menguji di iPhone fisik, pilih tim penandatanganan pada target **Andi** dan **AndiWidgets**, lalu pilih iPhone yang terhubung. Gunakan bundle ID yang terdaftar pada tim tersebut. Jika ID aplikasi diubah, sesuaikan juga ID ekstensi widget.

Workflow **Build Andi for iPhone** memvalidasi build untuk perangkat, memasang aplikasi di iPhone Simulator, lalu menyimpan arsip **Andi-iOS-0.1.0-Simulator**. File `Andi.app` dalam arsip dapat dipasang pada simulator di Mac dengan `xcrun simctl install booted Andi.app`. Pemasangan pada iPhone fisik memerlukan build bertanda tangan melalui Xcode atau distribusi melalui TestFlight.
