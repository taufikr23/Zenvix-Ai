# Hasil verifikasi tahap 3

> Arsip tahap 3. Status upload kini QUEUED; lihat tahap-4.md untuk perilaku terkini.

Tanggal: 16 November 2026. Lingkungan: Windows, JDK 21, Docker Desktop Linux Engine.
Pengujian memakai source lokal di Documents/documind-ai.

## Hasil

| Pemeriksaan | Hasil aktual |
|---|---|
| Maven verify | BUILD SUCCESS, exit code 0 |
| Unit test | 5 lulus, 0 gagal, 0 error, 0 dilewati |
| Integration test | 26 lulus, 0 gagal, 0 error, 0 dilewati |
| PostgreSQL integrasi | PostgreSQL 17.12 dengan pgvector, melalui Testcontainers |
| Migrasi | V1, V2, dan V3 berhasil pada database pengujian; V3 berhasil pada Compose |
| Build dan start Compose | Berhasil, exit code 0 |
| Health aplikasi | UP |
| Container backend dan db | healthy |
| Volume file | documind-ai_documents_data → /app/data/documents |

## Cakupan pengujian otomatis

17 integration test fondasi/autentikasi tahap 2 tetap lulus.
Test yang dahulu menolak semua endpoint dokumen kini memeriksa endpoint admin
yang memang belum tersedia; pengujian anonim dokumen tetap mengharapkan 401.

9 integration test dokumen ditambahkan:

1. PDF, DOCX, dan TXT dapat diunggah dan diunduh dengan byte identik; metadata
   tidak memuat ownerId, path, hash, atau password. Attachment/no-store/nosniff
   serta pagination diperiksa.
2. Akun USER dan ADMIN lain mendapat 404 untuk detail, download, dan delete;
   daftar hanya berisi dokumen sendiri. ownerId dari client tidak dipercaya.
3. Upload, detail, download, dan delete tanpa token mendapat 401.
4. Format palsu, ekstensi tidak didukung, file kosong, TXT biner/UTF-8 rusak,
   path berbahaya, parameter tidak valid, dan multipart tanpa file ditolak.
5. File 10MiB + 1 byte mendapat 413 dan tidak membuat metadata.
6. Dua upload bersamaan saat tersisa satu slot menghasilkan satu 201 dan satu
   409; nama file sama tidak menimpa dokumen sebelumnya.
7. Penghapusan menghapus metadata dan file, pengulangan mendapat 404;
   file yang sudah hilang tetap memungkinkan metadata dibersihkan.
8. Simulasi kegagalan disk saat delete menghasilkan 503/DELETING,
   download ditolak, lalu pengulangan DELETE berhasil.
9. Simulasi kegagalan INSERT metadata me-rollback transaksi dan menghapus
   file yang sempat tersimpan; respons tidak membocorkan detail SQL/path.

Kuota pada integration test diatur menjadi 3 untuk menguji batas; default aplikasi
tetap 50. Direktori upload test terpisah dan dibersihkan setelah pengujian.

## Verifikasi langsung pada Docker

Dua akun uji sementara dibuat melalui API registrasi dan login:

- Upload TXT dengan MIME client application/octet-stream berhasil 201 dan
  menghasilkan status UPLOADED.
- Field ownerId yang menunjuk akun kedua tidak mengubah kepemilikan.
- Daftar akun pertama berisi satu dokumen; akun kedua kosong.
- Detail, download, dan delete lintas akun menghasilkan 404.
- Download oleh pemilik mengembalikan byte asli.
- Database dan backend direstart. Token pemilik masih berlaku, metadata tersedia,
  dan byte download tetap identik.
- DELETE pemilik berhasil 204; detail berikutnya 404 dan file fisik sudah hilang.
- Kedua akun uji dibersihkan. Container tetap berjalan dan sehat.

## Batas bukti

Hasil ini memverifikasi lingkup tahap 3, bukan pemrosesan AI. Belum ada ekstraksi teks,
chunking, embedding, Gemini, chat, atau frontend. PDF/DOCX/TXT diuji otomatis;
smoke test langsung pada Compose memakai TXT.

Simulasi kegagalan mencakup kegagalan operasi biasa, bukan mati mendadak di setiap
instruksi atau crash filesystem. Database dan file belum memiliki transaksi atomik
lintas penyimpanan. Pemulihan DELETING dan potensi file yatim dijelaskan di tahap-3.md.
Belum dilakukan uji beban besar, audit keamanan menyeluruh, atau pemindaian antivirus.

Maven mencatat peringatan Mockito tentang dynamic agent pada JDK masa depan dan
commons-logging dari dependency parser. Peringatan tidak menggagalkan pengujian JDK 21.
Build image melewati tes; bukti tes berasal dari Maven verify terpisah.
