# R+ Gram — Catatan Perbaikan

<!-- ENTRI BARU DI BAWAH BARIS INI -->
### Fix #7 — Per-Target Progress Log and Speed Presets

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-28 | `RPlusBroadcastManager.java`, `RPlusBroadcastActivity.java`, `RPlusBroadcastService.java`, `RPlusStore.java` | Progress hanya menampilkan kursor target dan sempat “Menyiapkan target…”; GCAST terasa lambat karena jeda tetap 3–5 detik. | Progress memakai `index` (kursor), bukan jumlah selesai; status tidak menyimpan target terakhir/berikutnya; delay hardcoded. | Progress memakai jumlah selesai, status “✓ X terkirim · lanjut ke Y”, log 4 target terakhir, notifikasi ikut; preset kecepatan Cepat 0,8–1,5 dtk / Normal 2–3,5 dtk / Aman 3,5–5,5 dtk. | `compileDebugJavaWithJavac` sukses. Build CI berikutnya pending. | Progress harus mencerminkan hasil per target, bukan posisi antrean; kecepatan harus bisa dipilih dengan peringatan risiko. | `getCompleted`, `RPlusTargetResult`, `delay_mode`, `DELAY_FAST` | ⏳ CI NEXT |

### Fix #6 — GCAST Target Filter Aligned with Userbot `.gcast`

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-28 | `RPlusBroadcastManager.java` | Daftar target GCAST kosong untuk grup yang sebenarnya bisa dikirimi. | Pre-filter `ChatObject.canSendPlain` terlalu ketat; userbot `.gcast` (Ultroid/Getter) tidak memfilter, tetapi mencoba kirim lalu mencatat error per chat. | Semua dialog grup yang masih diikuti dimasukkan; channel broadcast hanya jika punya hak post; kegagalan dicatat per target seperti userbot. | `compileDebugJavaWithJavac` sukses. Build CI berikutnya pending. | Untuk broadcast, attempt-and-count lebih benar daripada pre-filter hak kirim yang bisa false-negative. | `.gcast`, `canSendPlain`, `canPost`, target | ⏳ CI NEXT |

### Fix #5 — Swipe Handle, Channel Targets and Broadcast Diagnostics

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-28 | `RPlusPanelLauncher.java`, `RPlusBroadcastManager.java`, `RPlusBroadcastActivity.java`, `ProfileActivity.java` | Tombol FAB R+ terlihat seperti komponen custom; akun yang isinya channel melihat 0 target GCAST; kegagalan start tidak selalu terlihat; cek jaringan bisa memblokir broadcast secara palsu. | GCAST mengecualikan channel; target dihitung sekali saat composer dibuka; start failure hanya lewat notifikasi service; precheck `isNetworkOnline` bisa false. | Handle geser di tepi kanan (drag/tap) menggantikan FAB; channel dengan hak posting ikut target; tombol “Muat ulang target”; cek 3 detik setelah start; reset lock khusus debug; logging target/kirim/error; precheck jaringan dihapus dan mengandalkan error transport. | Compile lokal terakhir timeout karena mesin; compile sebelumnya sukses dan CI arm64 `638f638` sukses. Build CI berikutnya pending. | Target harus mencakup semua jenis chat yang benar-benar bisa dikirimi; kegagalan start harus punya umpan balik di layar. | `canPost`, `requestInFlight`, `Muat ulang target`, `RPlus send` | ⏳ CI NEXT • MANUAL TEST |

### Fix #4 — Broadcast Backend Hardening: Pipeline, Target Order, FGS and Exact Alarms

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-28 | `RPlusBroadcastManager.java`, `RPlusBroadcastService.java`, `RPlusScheduleManager.java`, `RPlusScheduleReceiver.java`, `RPlusBootReceiver.java`, `RPlusMessageAlerts.java`, `RPlusBroadcastActivity.java`, `RPlusPanelLauncher.java`, `AndroidManifest.xml` | Antrean bisa berjalan ganda setelah pause/resume; callback run lama bisa mengubah statistik run baru; UCAST memilih 500 user berdasarkan ID, bukan chat terbaru; start ganda bisa mematikan broadcast aktif; jadwal gagal tanpa izin exact alarm; error jaringan menandai semua target gagal. | Pipeline hanya mengandalkan flag `paused`; `LongSparseArray` mengiterasi berdasarkan ID; `postImmediateError` selalu `stopSelf`; permission exact alarm tidak ada di manifest utama; error transport dianggap kegagalan permanen; alert reply fallback menulis dari storage thread. | Menambah generation token + `requestInFlight`/`nextScheduled`; memakai urutan hasil sort dialog; guard duplicate start; permission exact alarm + dialog pengaturan; retry target yang sama dengan `random_id` yang sama; pacing 3–5 detik; try/catch pada service/receiver/alarm/peer; pindah alert ke UI thread; reschedule saat app start/update. | Compile lokal sukses; lint skill 0 findings; manifest merge memuat `SCHEDULE_EXACT_ALARM`. Build CI dan uji perangkat manual pending. | Pipeline async harus punya identitas run dan satu pemilik jadwal; jangan mengandalkan urutan iterasi struktur data yang salah. | `requestInFlight`, `FLOOD_WAIT`, `SCHEDULE_EXACT_ALARM`, `random_id`, `MY_PACKAGE_REPLACED` | ⏳ CI BUILD • MANUAL TEST |

### Fix #3 — Broadcast Runtime Hardening: Predictive Back, Service Guard, FAB Lifecycle

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-28 | `RPlusBroadcastActivity.java`, `RPlusBroadcastService.java`, `RPlusPanelLauncher.java` | Back gesture Android 13+ tidak konsisten; aksi pause/resume/cancel berpotensi memicu `startForegroundService` tanpa `startForeground` bila service sudah mati; FAB terus polling saat aplikasi di background. | Activity hanya mengandalkan `onBackPressed` lama; service memakai `startForegroundService` untuk semua aksi; updater FAB hanya berhenti saat view di-detach. | Menambah `OnBackPressedCallback`, guard service (`ensureForeground`/`stopSelf`), jeda polling berdasarkan window visibility, badge yang aman untuk font besar, dan margin RTL. | Compile lokal `compileDebugJavaWithJavac` sukses; lint skill 0 findings; manifest/resource merge sukses. Build CI dipicu lewat push; uji perangkat manual oleh pengguna. | Semua entry point service dan lifecycle view harus punya jalur aman saat state sudah tidak valid. | `OnBackPressedCallback`, `startForegroundService`, `onWindowVisibilityChanged` | ⏳ CI BUILD • MANUAL TEST |

### Fix #2 — UI Overhaul: Native Telegram-Style Broadcast Screens

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-28 | `RPlusBroadcastActivity.java`, `RPlusPanelLauncher.java`, `AndroidManifest.xml` | Panel broadcast lama memakai drawer custom, warna ungu hardcoded, ikon berupa karakter teks, tombol ALL CAPS, dan pemilih tanggal bawaan Android sehingga tampilan tidak konsisten dengan Telegram dan terasa buggy. | UI dibangun manual tanpa `ActionBar` dan tanpa color key tema; logika tab blacklist bergantung pada indeks child yang rapuh; handle mengambang memakai corner radius tidak simetris. | Mengganti dengan layar penuh bergaya Telegram: `ActionBar` asli, warna dari `Theme`, card/row native, ikon drawable, bottom sheet jadwal Telegram, edge-to-edge dengan insets, empty state, serta perbaikan listener progres broadcast. Handle diubah menjadi FAB bulat dengan badge. | Compile lokal sukses (25m 21s); manifest/resource merge sukses; lint skill 0 findings untuk file R+. Build CI dipicu lewat push; uji perangkat manual oleh pengguna. | Gunakan komponen dan color key Telegram agar UI otomatis mengikuti tema, bukan warna hardcoded. | `ActionBar`, `Theme.getColor`, `WindowInsetsCompat`, `AlertsCreator` | ⏳ CI BUILD • MANUAL TEST |

### Fix #1 — Broadcast Module Compile Fix: Missing Dialog Date and Wrong Activity Import

| Tanggal | File | Masalah | Akar | Fix | Verifikasi | Pelajaran | Log Keyword | Deploy |
|---|---|---|---|---|---|---|---|---|
| 2026-11-26 | `RPlusBroadcastManager.java`, `RPlusBroadcastActivity.java` | Build CI gagal setelah modul Broadcast ditambahkan. | `TLRPC.Dialog.date` tidak ada; field yang benar adalah `last_message_date`. `BasePermissionsActivity` juga diimpor dari package yang salah. | Mengganti tiga penggunaan tanggal dialog dan menghapus import package yang salah. | Build `rplusgram-dev@80a362562` berhasil melalui GitHub Actions; artifact debug 123.062.337 byte berhasil diunggah. | Selalu cocokkan field custom Telegram Android dan package Activity sebelum push. | `cannot find symbol`, `TLRPC.Dialog.date`, `BasePermissionsActivity` | ✅ LIVE 2026-11-26 |
