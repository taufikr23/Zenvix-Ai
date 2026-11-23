# Revisi DocuMind AI menjadi AI Assistant

## Evaluasi source awal

Frontend React dan backend Spring Boot sudah tersedia. README awal masih
menyebut frontend belum tersedia, sehingga tidak lagi menggambarkan source.
Backend chat mewajibkan dokumen READY dan mengaitkan startup chat dengan embedding.
Saat pencarian tidak menemukan sumber, jawaban fallback dikembalikan tanpa
memanggil model. Konteks model hanya berisi dua pertanyaan lama yang dipotong.
Antarmuka membuka daftar dokumen dan menyebut AI sebagai asisten dokumen.

## Pendekatan refactoring

Perubahan dilakukan di proyek yang sama. Tidak mengganti framework, provider,
dependensi, skema autentikasi, namespace Java, nama database, atau volume Docker.

| Bagian | Keputusan |
|---|---|
| Auth, JWT, isolasi pemilik | Dipertahankan |
| ChatBudget, concurrency, requestId | Dipakai kembali pada chat umum |
| ConversationService | Memisahkan alur umum dan dokumen berdasarkan documentId |
| ChatGateway | Menambahkan prompt umum dan riwayat dengan role user/assistant |
| ConversationRepository | LEFT JOIN dokumen, judul pesan pertama, konteks terbaru berbatas |
| Skema database | V7 tambahan; V1-V6 tidak diubah |
| Dokumen dan retrieval | Tetap tersedia sebagai fitur opsional |
| UI | Chat menjadi halaman awal; nama dan panduan menjadi AI Assistant |
| Kode jawaban | Blok kode aman dan tombol salin; HTML tidak dieksekusi |

Chat umum tidak menjalankan pencarian atau memanggil embedding. Model menerima
system prompt, pasangan user/assistant terbaru, lalu pertanyaan saat ini.
Konteks dibatasi maksimal 12 giliran dan 24.000 karakter, diambil dari yang terbaru
hingga batas terpenuhi. Riwayat lengkap tetap tersimpan. Kuota menghitung teks
prompt dan konteks yang dikirim ke model. Tidak ada klaim akses web atau eksekusi kode.

Percakapan baru dapat dibuat sebelum provider diaktifkan. Pengiriman pesan tetap
memerlukan CHAT_ENABLED dan API key. ChatConfig kini memeriksa API key langsung,
karena validasi IndexingProperties mengizinkan key kosong saat embedding nonaktif.

Dokumen lama tetap memakai alur retrieval dan pemeriksaan kutipan. Menghapus
dokumen tetap menghapus percakapan terkait, tetapi tidak menyentuh chat umum.
Judul dibuat dari maksimal 80 Unicode code point pesan pertama. Batas pesan
menjadi 12.000 karakter pada UI, validasi API, dan kolom database.

## Verifikasi 21 November 2026

| Pemeriksaan | Hasil |
|---|---|
| Tes frontend | 22 lulus, termasuk alur chat umum, judul, retry, dan keamanan HTML |
| Build produksi frontend | Berhasil |
| Tes backend yang tidak memerlukan koneksi lokal | 29 lulus, termasuk 11 tes baru gateway/service chat umum |
| Kompilasi backend dan seluruh source test | Berhasil dengan JDK 21 |
| Validasi Docker Compose | Berhasil |
| Adapter test dengan server HTTP lokal | Terhalang izin koneksi loopback (Permission denied) |
| Integration test PostgreSQL/Testcontainers | Terhalang akses named pipe Docker (AccessDeniedException) |
| Migrasi pada database pengguna dan Gemini langsung | Belum dijalankan |
| Pratinjau browser | Server Vite dapat dimulai; browser sesi tidak dapat menjangkaunya |

Tes frontend juga menemukan race antara refresh riwayat dan pembuatan chat baru.
Perbaikan revision guard mencegah hasil refresh yang lama menimpa perubahan lokal.

Tes backend terpilih dijalankan dengan Byte Buddy agent pada startup JVM karena
self-attachment Mockito terbatas di lingkungan ini. Tidak ada perubahan dependensi
atau konfigurasi aplikasi untuk melewati pembatasan lingkungan.

Tes integrasi tambahan meliputi
chat tanpa dokumen, replay requestId, konteks jawaban sebelumnya, isolasi akun,
penghapusan berantai, respons AI tidak valid, dan batas panjang pesan.

## Penerapan

1. Pastikan backup database dan dokumen tersedia untuk instalasi penting.
2. Gunakan konfigurasi .env yang ada; aktifkan CHAT_ENABLED dan isi GEMINI_API_KEY.
3. Jalankan build dan startup Docker Compose dari root proyek.
4. Flyway menerapkan V7 otomatis tanpa menghapus data lama.
5. Periksa login, chat umum, pertanyaan lanjutan, riwayat, serta chat dokumen lama.

Revisi source tidak mengubah .env atau menghapus volume. Jika lingkungan tidak
mengizinkan Docker, migrasi dan uji provider nyata belum dapat dinyatakan berhasil.

