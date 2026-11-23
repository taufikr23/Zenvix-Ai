# Hasil verifikasi tahap 5

Tanggal: 16 November 2026.
Source dikerjakan langsung di C:\Users\ThinkPad\Documents\documind-ai.

## Hasil aktual

| Pemeriksaan | Hasil |
|---|---|
| Maven verify JDK 21 | BUILD SUCCESS, exit code 0 |
| Unit test | 14 lulus, 0 gagal/error/dilewati |
| Integration test | 39 lulus, 0 gagal/error/dilewati |
| Total | 53 test lulus |
| Build/start Docker Compose | Exit code 0 |
| Health backend | UP |
| Backend dan db | healthy |
| Migrasi database Compose | Versi 5 berhasil |
| Kolom embedding | vector(768) |
| Gemini sungguhan | Berhasil: READY, 1 vector 768 dimensi |

## Pemeriksaan versi dan kontrak SDK

Spring AI tetap 1.1.8 dengan Spring Boot 3.5.16 dan Java 21.
Modul spring-ai-google-genai-embedding menggunakan Google GenAI SDK 1.37.0.

Source JAR Spring AI 1.1.8 diperiksa: model bawaan belum meneruskan taskType
ke EmbedContentConfig. Adapter aplikasi memakai kontrak EmbeddingModel Spring AI
dan SDK Google secara eksplisit. SDK tidak diganti dengan HTTP buatan sendiri.

GeminiAdapterTest memiliki 4 test:

1. SDK asli dikirim ke server HTTP lokal. Path batchEmbedContents, satu input,
   taskType RETRIEVAL_DOCUMENT, outputDimensionality 768, dan header API key
   dummy diperiksa. Tidak ada request ke Google.
2. Server lokal mengembalikan 429; jumlah request tepat satu, membuktikan
   konfigurasi attempts(1) tidak menggandakan panggilan.
3. Vector dinormalisasi; dimensi salah, zero vector, NaN, dan infinity ditolak.
4. Konfigurasi aktif membutuhkan key dan toString menyembunyikan key.

Sepuluh unit test sebelumnya tetap lulus.

## Integration test baru

Tujuh test baru memakai EmbeddingModel tiruan dan PostgreSQL/pgvector sungguhan:

1. Dua chunk diproses per giliran. Progres 1/2 tetap EXTRACTED, kemudian READY.
   vector_dims=768, norm mendekati 1, dan cosine distance vector dengan dirinya
   mendekati 0. Metadata/chunk tidak mengembalikan vector. Retry lintas pemilik
   ditolak; penghapusan menghapus chunk beserta vector.
2. Provider gagal setelah chunk pertama berhasil: INDEX_FAILED dengan satu hasil
   tersimpan. Retry hanya mengirim chunk yang tersisa. Pesan provider mentah
   tidak muncul di respons.
3. Vector dengan dimensi salah tidak disimpan dan dokumen tidak menjadi READY.
4. Kuota pemilik habis: provider tidak dipanggil, status gagal terkontrol,
   reservasi global ikut dibatalkan.
5. Simulasi kegagalan database setelah UPDATE vector: vector/progres rollback,
   kuota tetap tercatat. Percobaan berikutnya berhasil dan dihitung lagi.
6. Dua worker bersamaan hanya memanggil provider sekali untuk satu chunk.
7. Dua reservasi kuota bersamaan untuk satu slot tersisa menghasilkan tepat
   satu keberhasilan; batas global/pemilik tetap konsisten.

32 integration test sebelumnya tetap lulus. Scheduler dimatikan pada test;
processNext dipanggil secara deterministik. Database dan file test terisolasi
dari data aplikasi yang berjalan.

## Smoke test awal pada Docker tanpa API key

Dengan EMBEDDING_ENABLED=false dan key kosong:

- Backend dan database berhasil startup setelah migrasi V5.
- Endpoint health menghasilkan UP.
- Satu akun uji dibuat melalui registrasi/login.
- Upload TXT menghasilkan 201, kemudian diproses otomatis menjadi EXTRACTED.
- Chunk dapat dibaca pemilik dan indexedChunkCount tetap 0.
- POST index menghasilkan 503 dengan kode EMBEDDING_DISABLED.
- Query skema mengonfirmasi kolom vector(768) dan migrasi versi 5.
- Tabel kuota embedding kosong: tidak ada reservasi request AI.
- Dokumen dan akun uji dibersihkan. Kedua container tetap healthy.

## Uji Gemini sungguhan setelah aktivasi

Pembaruan 16 November 2026: GEMINI_API_KEY sudah terisi pada .env lokal dan
EMBEDDING_ENABLED=true. Nilai key tidak dicetak, dimasukkan ke dokumentasi, atau
disertakan dalam ZIP. Konfigurasi diterapkan dengan recreate backend.

Satu akun sementara mengunggah TXT buatan pengujian, tanpa data pribadi:

- Upload 201 → ekstraksi → status READY.
- chunkCount=1 dan indexedChunkCount=1; indexingError=null.
- PostgreSQL mengonfirmasi satu vector dengan 768 dimensi dan model
  gemini-embedding-001.
- Selisih norm vector dari 1 sekitar 1,45 × 10^-9.
- Isi chunk identik dengan teks uji.
- Ledger kuota pemilik mencatat 130 karakter.
- Database dan backend direstart. Dokumen tetap READY, ID chunk/vector tetap,
  dan ledger tetap 130 karakter tanpa reservasi tambahan.
- File, metadata, chunk/vector, dan akun sementara dibersihkan.
  Ledger penggunaan tetap dipertahankan agar percobaan API nyata tidak dihapus
  dari pencatatan kuota.
- Kedua container kembali healthy.

Ini uji melalui API upload DocuMind dan worker Java dalam container, memakai
adapter SDK Google sebenarnya. Bukan penyisipan vector manual atau model tiruan.
53 tes otomatis adalah hasil tahap implementasi sebelumnya; tidak diulang karena
pembaruan ini hanya menerapkan environment dan memverifikasi provider nyata.

## Hal yang belum diverifikasi

Satu request embedding sungguhan sudah berhasil. Kualitas retrieval semantik,
performa berulang, batas kuota Google pada beban tinggi, dan tagihan aktual
provider belum dievaluasi.

Belum ada retrieval endpoint, embedding pertanyaan, AI Chat, atau frontend.
Indexing sudah aktif pada lingkungan lokal; panduan konfigurasi dan batas operasional
tersedia pada tahap-5.md.

Tidak dilakukan uji beban besar atau uji crash di setiap instruksi. SDK/provider
dan database bukan satu transaksi atomik; request dapat terulang bila API berhasil
tetapi penyimpanan gagal. Kuota sengaja tetap menghitung percobaan tersebut.
