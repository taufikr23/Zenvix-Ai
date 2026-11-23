# Zenvix

Zenvix adalah chatbot AI umum, dikembangkan dari DocuMind AI menggunakan source code yang sama.
Chat dapat dipakai tanpa mengunggah dokumen untuk bertanya, belajar pemrograman,
membuat atau memperbaiki kode, menulis, menerjemahkan, menganalisis masalah,
dan membantu pekerjaan sehari-hari.

Backend tetap Java 21, Spring Boot, Spring AI/Gemini, dan PostgreSQL + pgvector.
Frontend tetap React + Vite. Login, JWT, riwayat privat, kuota, dan data dokumen lama
memakai fondasi yang sudah ada. Nama package, database, volume Docker, dan
identitas JWT tetap dipertahankan agar konfigurasi serta data lama kompatibel.

## Menjalankan

Prasyarat: Docker Desktop dengan Linux Engine aktif.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-env.ps1
# Isi GEMINI_API_KEY dan CHAT_ENABLED=true pada .env lokal.
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 240
```

Buka http://localhost:3000 (atau FRONTEND_PORT Anda).
Backend tersedia pada http://localhost:8080; database lokal pada port 5433.
Untuk chat umum, EMBEDDING_ENABLED boleh false. Aktifkan embedding hanya bila
membutuhkan indexing, pencarian, dan chat dokumen. Konfigurasi .env lama tidak
ditimpa oleh revisi ini. CHAT_MAX_OUTPUT_TOKENS dapat diatur sampai 4096 untuk
jawaban kode atau tulisan yang lebih panjang; batas provider tetap berlaku.

Setelah masuk, klik **Chat baru** dan tulis pesan. Percakapan tersimpan di
**Riwayat** dengan judul dari pesan pertama. Menu **Dokumen saya** telah dihapus.
Gunakan tombol lampiran di kolom pesan untuk mengirim PDF, DOCX, TXT,
PNG, atau JPG langsung ke Zenvix. Gambar juga bisa ditempel dari clipboard dan
file bisa diseret ke kolom pesan. Lampiran tanpa teks memakai permintaan analisis.

Maksimal 3 file per pesan, 5 MB per file, total 10 MB, dan 500.000 karakter hasil
ekstraksi teks. Pesan dapat berisi hingga 100.000 karakter. Gambar dibatasi
20 megapiksel. Penyimpanan lampiran maksimal 100 MB per akun; menghapus
percakapan juga menghapus lampirannya. PDF asli diteruskan ke model, termasuk
PDF hasil pindai. Gambar PNG/JPG yang tertanam di DOCX dibaca langsung,
termasuk DOCX yang seluruh isinya berupa gambar halaman. Maksimal 50 gambar
dan 10 MB gambar per DOCX; total media per kiriman maksimal 10 MB. Lampiran
disimpan privat di PostgreSQL dan diteruskan ke provider AI saat digunakan.
Chat dokumen lama masih bisa dibaca, tetapi lampiran baru dikirim melalui chat umum baru.

Tema Zenvix memakai logo planet yang diberikan pengguna, latar midnight,
serta aksen biru elektrik, cyan, dan ungu di halaman login, sidebar, chat, dan dialog.

## Perubahan utama

- Percakapan umum tidak membutuhkan documentId, embedding, atau pencarian dokumen.
- Konteks chat umum berisi pertanyaan DAN jawaban sebelumnya, dengan role terpisah.
- Konteks dibatasi pada 12 giliran terakhir dan 750.000 karakter; pasangan pesan
  dipertahankan utuh. Seluruh riwayat tetap tersimpan walaupun konteks model dibatasi.
- Pesan dapat mencapai 100.000 karakter, termasuk potongan kode.
- Blok kode berpagar Markdown ditampilkan sebagai teks aman dan dapat disalin.
- Judul, login, panduan, dan halaman utama menggunakan konsep AI Assistant.
- Retry menggunakan requestId yang sama sehingga jawaban tidak tersimpan ganda.

## Migrasi dan data lama

Migrasi Flyway `V7__general_assistant.sql` membuat document_id opsional, menambahkan
judul dan foreign key pemilik, serta memperbesar kolom pertanyaan. Migrasi V1-V6
tidak diubah. Percakapan dokumen lama, pesan, akun, dan file tetap dipertahankan.
Migrasi dijalankan otomatis saat backend terbaru mulai. Jangan gunakan
`docker compose down -v` karena perintah itu menghapus volume data.

Backup database dan volume dokumen secara konsisten sebelum menerapkan migrasi
pada instalasi penting. Downgrade ke backend lama setelah membuat chat umum tidak
didukung karena backend lama mewajibkan dokumen untuk setiap percakapan.

Migrasi `V8__chat_attachments.sql` menambahkan lampiran yang terkait dengan pemilik
dan pesan, tanpa menghapus data sebelumnya. File dan jawaban disimpan dalam satu
transaksi. Retry memeriksa isi file serta requestId untuk mencegah pesan ganda.
Konteks lampiran mencakup giliran terbaru secara utuh dengan batas total 12 MB
media dan 750.000 karakter teks, termasuk pesan saat ini; batas 12 giliran terbaru
tetap berlaku. File dan pesan disimpan utuh meskipun giliran lama tidak lagi
muat dalam konteks model. Migrasi `V9__long_chat_messages.sql` mengubah kolom
pesan menjadi TEXT agar pesan panjang tidak gagal disimpan.

Batas kuota default adalah 2.000.000 karakter per akun per hari dan 10.000.000
karakter aplikasi per hari. Nilai eksplisit dalam `.env` tetap berlaku. Waktu
tunggu provider menjadi 90 detik; klien dan proxy menunggu hingga 190 detik
untuk memberi waktu ekstraksi dokumen. Retry otomatis provider dinonaktifkan;
pengguna dapat mencoba ulang dengan requestId yang sama.

## API chat

Semua endpoint percakapan membutuhkan Bearer JWT dan dibatasi ke pemiliknya.

| Method dan path | Fungsi |
|---|---|
| POST /api/conversations | Buat chat umum dengan `{}`; atau chat dokumen dengan `{"documentId":"UUID"}` |
| GET /api/conversations | Riwayat percakapan milik sendiri, termasuk title |
| GET /api/conversations/{id} | Detail percakapan |
| GET /api/conversations/{id}/messages | Riwayat pasangan pertanyaan dan jawaban |
| POST /api/conversations/{id}/messages | Kirim `{"requestId":"UUID","question":"Pesan Anda"}` |
| POST /api/conversations/{id}/messages (multipart) | Part `message` berisi JSON requestId/question dengan Content-Type application/json, serta maksimal 3 part `files` |
| GET /api/conversations/{id}/attachments/{attachmentId} | Unduh lampiran milik akun yang sedang masuk; Bearer JWT wajib |
| DELETE /api/conversations/{id} | Hapus percakapan beserta pesannya |

Untuk chat umum, documentId dan filename bernilai null. Jawaban umum memiliki
sources kosong dan found=false; found menandakan dukungan sumber dokumen, bukan
berhasil atau gagalnya AI menjawab. Endpoint auth dan dokumen tetap kompatibel.

## Pengembangan dan pengujian

Gunakan JDK 21 dan Maven untuk backend, serta Node.js dan npm untuk frontend.

```powershell
mvn -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend run dev
```

Integration test memerlukan akses Docker dan memakai database terpisah melalui
Testcontainers. Adapter test membuka server loopback lokal, sehingga lingkungan
harus mengizinkan koneksi tersebut. Vite berjalan di http://localhost:5173 dan
meneruskan `/api` ke backend port 8080.

Rincian evaluasi, perubahan, dan hasil verifikasi revisi ada di
[docs/revisi-ai-assistant.md](docs/revisi-ai-assistant.md).
Panduan `docs/tahap-*.md` adalah arsip implementasi DocuMind sebelumnya.

## Batas kemampuan saat ini

Area chat memakai tinggi layar penuh tanpa bar atas. Tombol pada sidebar menutup
navigasi; tombol mengambang di kiri atas membukanya kembali. Panduan tersedia
di sidebar, termasuk pada layar ponsel (navigasi juga bisa ditutup dengan Escape).

Setelah Enter, pesan dan lampiran langsung tampil sebagai pesan yang sedang
diproses dan kolom input dikosongkan. Progres unggah berasal dari jumlah byte
yang dikirim browser, lalu status berubah menjadi pemrosesan lampiran oleh AI.
Unggahan selesai belum berarti jawaban atau penyimpanan percakapan selesai.
Jika permintaan gagal, teks dan lampiran kembali ke kolom input; percobaan ulang
yang sama mempertahankan requestId untuk mencegah pesan ganda.

Respons dikirim utuh setelah model selesai, belum streaming. Tampilan mendukung
teks dan blok kode, belum seluruh format Markdown. Belum ada browsing web,
eksekusi kode, alat eksternal, pembuatan gambar, suara, atau pemilihan provider dari UI.
Analisis gambar PNG/JPG dari pengguna tersedia melalui model Gemini multimodal.
AI dapat membuat kesalahan. Kuota, batas jumlah percakapan, batas giliran, dan
masa berlaku JWT tetap mengikuti konfigurasi. Refresh token dan reset password
belum tersedia. Compose ditujukan untuk pengembangan lokal.
