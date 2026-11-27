# R+ Broadcast — QA Checklist UI/UX

Dokumen ini dipakai untuk memverifikasi layar broadcast R+ sebelum dianggap siap rilis.
Centang setiap baris; jika gagal, catat perangkat, versi Android, dan langkah reproduksinya.

## 1. Build & launch

- [ ] `./gradlew :TMessagesProj_App:assembleAfatDebug` berhasil tanpa error.
- [ ] APK terpasang di perangkat/emulator.
- [ ] FAB `R+` muncul di kanan tengah setelah login.
- [ ] FAB tidak muncul saat app lock aktif.
- [ ] Menekan FAB membuka layar **R+ Gram / Pusat broadcast**.
- [ ] Tombol back ActionBar dan gesture back sama-sama berfungsi.

## 2. Tema & tampilan

- [ ] Tema terang: background abu-abu, kartu putih, teks terbaca.
- [ ] Tema gelap: background gelap, kartu gelap, teks terang.
- [ ] Ganti tema saat layar terbuka, tutup lalu buka lagi: warna ikut tema baru.
- [ ] Status bar dan navigation bar tidak menutupi konten (edge-to-edge).
- [ ] Font scale 1.3x dan 2.0x: tidak ada teks terpotong/tumpang tindih.
- [ ] Rotasi portrait ↔ landscape: tidak crash, konten bisa di-scroll.
- [ ] Tablet/layar lebar: kartu tidak pecah, tombol tetap bisa ditekan.
- [ ] RTL (Arab): ikon dan teks tidak bertabrakan.

## 3. Home

- [ ] Kartu akun menampilkan nama dan username yang benar.
- [ ] Baris **Broadcast grup** membuka composer GCAST.
- [ ] Baris **Broadcast pengguna** membuka composer UCAST.
- [ ] Baris **Blacklist** menampilkan jumlah entri.
- [ ] Baris **Riwayat** dan **Pesan** membuka layar yang benar.
- [ ] Saat broadcast aktif, kartu progress muncul paling atas.
- [ ] Progress bar, jumlah `x / y`, dan target saat ini berubah real-time.
- [ ] Tombol **Jeda/Lanjutkan** mengubah status.
- [ ] Tombol **Batalkan** meminta konfirmasi lalu menghentikan broadcast.

## 4. Composer

- [ ] Ringkasan target menampilkan jumlah grup/pengguna dan jumlah dikecualikan.
- [ ] Jika target kosong, muncul keterangan yang jelas dan tombol mulai tidak menyesatkan.
- [ ] Editor menerima teks, counter `0 / 4096` berubah.
- [ ] Preview mengikuti teks dan format (bold/italic/link) secara benar.
- [ ] Draft terakhir termuat saat composer dibuka kembali.
- [ ] Draft tersimpan otomatis saat keluar dari composer atau aplikasi di-background.
- [ ] Tombol kalender **Jadwalkan** membuka bottom sheet jadwal Telegram (bukan DatePicker bawaan).
- [ ] Tanggal/waktu lampau ditolak dengan pesan.
- [ ] Tombol **Mulai broadcast** tetap terlihat (sticky) saat konten di-scroll.
- [ ] Keyboard tidak menutupi tombol aksi (API 23–36).
- [ ] Tombol **Mulai broadcast** meminta konfirmasi risiko.
- [ ] Setelah mulai, layar kembali ke Home dan kartu progress muncul.
- [ ] Saat broadcast lain aktif, tombol mulai tidak bisa dipakai diam-diam.

## 5. Blacklist

- [ ] Daftar grup dan pengguna tampil terpisah.
- [ ] Empty state muncul jika belum ada entri.
- [ ] Tombol **Hapus** meminta konfirmasi.
- [ ] Setelah dihapus, entri langsung hilang dari daftar.
- [ ] Menambahkan blacklist dari profil chat → entri muncul di layar ini.
- [ ] Broadcast berikutnya tidak mengirim ke chat yang di-blacklist.

## 6. Riwayat

- [ ] Jadwal yang belum berjalan tampil di bagian **Jadwal**.
- [ ] **Batalkan** pada jadwal meminta konfirmasi dan menghapus jadwal.
- [ ] Riwayat selesai/dibatalkan tampil dengan statistik yang benar.
- [ ] Alasan gagal tampil jika ada kegagalan.
- [ ] Riwayat tetap ada setelah aplikasi ditutup dan dibuka.

## 7. Pesan (mention/reply)

- [ ] Empty state tampil saat belum ada mention/balasan.
- [ ] Kartu menampilkan nama chat, pengirim, waktu, dan isi pesan.
- [ ] **Bersihkan semua** meminta konfirmasi lalu mengosongkan daftar.
- [ ] Badge di FAB dan jumlah di Home ikut berubah.

## 8. Notifikasi & service

- [ ] Notifikasi progress muncul saat broadcast berjalan.
- [ ] Aksi Jeda/Lanjutkan/Batalkan dari notifikasi berfungsi.
- [ ] Notifikasi selesai muncul setelah broadcast berakhir.
- [ ] Menekan notifikasi membuka layar broadcast yang benar.
- [ ] Broadcast tetap berjalan saat layar dimatikan (sesuai batasan OS).
- [ ] Service berhenti dan notifikasi progress hilang setelah selesai/batal.

## 9. Regresi

- [ ] Menu blacklist R+ di profil user/grup masih berfungsi.
- [ ] Tidak ada crash `ForegroundServiceStartNotAllowedException`.
- [ ] Tidak ada ANR saat membuka composer/riwayat.
- [ ] Tidak ada kebocoran listener (buka/tutup layar berulang kali).
