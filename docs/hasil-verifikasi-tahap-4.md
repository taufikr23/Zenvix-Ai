# Hasil verifikasi tahap 4

> Arsip tahap 4. Indexing tersedia pada tahap 5; lihat tahap-5.md.

Tanggal: 16 November 2026. Proyek: C:\Users\ThinkPad\Documents\documind-ai.
Runtime pengujian: JDK 21 dan Docker Desktop Linux Engine.

## Ringkasan hasil aktual

| Pemeriksaan | Hasil |
|---|---|
| Maven verify | BUILD SUCCESS, exit code 0 |
| Unit test | 10 lulus; 0 gagal/error/dilewati |
| Integration test | 32 lulus; 0 gagal/error/dilewati |
| Total | 42 test lulus |
| Migrasi pada database pengujian | V1, V2, V3, V4 berhasil |
| Upgrade database Compose | V4 berhasil pada database tahap 3 |
| Compose build/start | Exit code 0 |
| Health backend | UP |
| Backend dan db | Keduanya healthy |

## Unit test baru

ExtractionTest menambah lima pengujian di atas lima unit test sebelumnya:

1. PDF tiga halaman, halaman tengah kosong: chunk merujuk halaman 1 dan 3;
   tidak menggeser nomor halaman asli.
2. DOCX berisi paragraf sebelum tabel, dua sel tabel, dan paragraf setelahnya:
   urutan teks dipertahankan dan pageNumber/sourcePages tidak dibuat-buat.
3. Teks dengan emoji: overlap cocok, rekonstruksi tidak kehilangan isi,
   ukuran chunk dipatuhi, dan tidak ada pasangan surrogate yang terbelah.
4. Batas teks dan jumlah chunk: proses gagal eksplisit, tidak memotong isi diam-diam.
5. Teks kosong ditolak; BOM dan line ending dinormalisasi.

## Integration test baru

Enam test ditambahkan, sehingga 26 test sebelumnya menjadi 32:

1. Upload QUEUED → processNext → EXTRACTED; statistik dan isi chunk benar.
   Endpoint chunks/process menolak akun lain dan pengguna anonim.
   Dokumen EXTRACTED tidak diproses ulang. Penghapusan menghapus chunk melalui
   foreign key dan pekerjaan tidak muncul kembali.
2. File hilang menghasilkan FAILED/FILE_UNAVAILABLE. Setelah file dipulihkan,
   retry menerima 202, QUEUED bersifat idempotent, dan hasil akhirnya EXTRACTED.
3. PDF kosong menghasilkan FAILED/NO_EXTRACTABLE_TEXT tanpa chunk.
4. Perubahan isi file dengan ukuran sama terdeteksi melalui SHA-256.
5. Dua worker bersamaan menghasilkan satu pemrosesan dan satu hasil kosong,
   tanpa chunk duplikat.
6. Simulasi kegagalan database setelah INSERT chunk me-rollback hasil dan
   mengembalikan status terlihat menjadi QUEUED. Percobaan berikutnya berhasil.

Pengujian menggunakan PostgreSQL 17.12/pgvector melalui Testcontainers.
Scheduler dimatikan hanya pada context integration test, agar processNext dapat
dipanggil secara deterministik. File uji memakai direktori sementara terpisah.
Test tahap 3 diperbarui untuk status awal QUEUED; aturan akses/upload tetap diuji.

## Smoke test Docker dengan scheduler aktif

Sebelum upgrade, dua akun sementara dibuat pada backend tahap 3.
Satu TXT diunggah dan status awal UPLOADED dikonfirmasi.

Setelah image tahap 4 dibangun dan migrasi selesai:

- Dokumen lama otomatis menjadi EXTRACTED, menghasilkan 4 chunk.
- Upload TXT baru mengembalikan QUEUED, kemudian otomatis menjadi EXTRACTED
  dengan 3 chunk.
- Teks yang direkonstruksi dengan menghapus overlap cocok dengan teks sumber
  setelah normalisasi whitespace tepi.
- TXT memiliki pageNumber=null.
- GET chunks dan POST process dari akun lain sama-sama menghasilkan 404.
- Memproses ulang dokumen EXTRACTED menghasilkan 409.
- Database dan backend direstart. ID chunk lama tetap sama; tidak ada proses
  ulang atau chunk duplikat.
- Byte file asli tetap identik setelah restart.
- DELETE menghapus file asli, metadata, dan semua chunk. Endpoint chunks
  setelah penghapusan menghasilkan 404.
- Kedua akun sementara dibersihkan; container tetap healthy.

## Batas verifikasi

Smoke test Compose memakai TXT. Ekstraksi PDF dan DOCX diperiksa pada unit test
dengan dokumen buatan Java; upload/download ketiga format juga tetap diuji
pada integration test.

Rollback database diuji melalui kegagalan terkontrol setelah INSERT. Belum ada
uji pematian paksa pada setiap instruksi, uji beban, atau pengujian semua variasi
dokumen kantor/PDF. Timeout parser adalah kooperatif, bukan isolasi proses paksa.

Belum ada OCR, embedding Gemini, vector search, RAG, chat, dan frontend.
EXTRACTED hanya menunjukkan hasil teks/chunk sudah disimpan.
