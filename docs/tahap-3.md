# Tahap 3 — Upload dan pengelolaan dokumen

> Arsip tahap 3. Status upload kini QUEUED; lihat tahap-4.md untuk perilaku terkini.

Tahap ini menambahkan API dokumen ke backend tahap 2. Semua kode aplikasi tetap
Java. Frontend, ekstraksi teks, chunking, embedding, dan AI Chat belum dibuat.

## Rancangan dan alur

JWT menentukan pengguna yang sedang login. Client tidak boleh menentukan pemilik.
Controller mengambil subject JWT, service menjalankan aturan, repository membaca
atau menulis PostgreSQL, dan FileStorage menyimpan file asli ke direktori privat.

Upload: autentikasi → validasi file → kunci baris pengguna → cek kuota →
simpan file → simpan metadata → commit transaksi → respons 201.
Penguncian baris pengguna membuat dua upload bersamaan tidak melewati kuota.

Download: autentikasi → cari dokumen dengan id DAN owner_id → baca file →
kirim sebagai attachment. Admin juga hanya dapat mengakses dokumennya sendiri.
Dokumen milik orang lain dan ID tidak ditemukan sama-sama menghasilkan 404.

Delete: cari dan tandai dokumen milik pengguna sebagai DELETING → hapus file →
hapus metadata. Bila disk gagal, respons 503 dan status DELETING tetap tersimpan.
Pengguna dapat mengulangi DELETE yang sama. File DELETING tidak dapat diunduh.
File yang sudah hilang tidak menghalangi penghapusan metadata.

## Database

Migrasi V3__create_documents.sql membuat tabel documents dan indeks daftar pengguna.

| Kolom | Fungsi |
|---|---|
| id | UUID acak buatan server, identitas dokumen |
| owner_id | Foreign key ke users.id |
| original_filename | Nama asli untuk tampilan/download, maksimal 255 karakter |
| media_type | MIME hasil validasi server |
| file_type | PDF, DOCX, atau TXT |
| size_bytes | Ukuran file asli |
| sha256 | Sidik file asli untuk kebutuhan integritas berikutnya |
| status | UPLOADED ketika file berhasil disimpan |
| created_at | Waktu upload dari database |

Indeks owner_id, created_at, id mempercepat daftar milik pengguna.
UNIQUE(id, owner_id) disediakan untuk relasi kepemilikan pada tahap berikutnya.
Foreign key mencegah penghapusan akun yang masih memiliki dokumen; belum ada endpoint
hapus akun. Admin nantinya perlu alur penghapusan dokumen sebelum menghapus akun.

Status QUEUED, PROCESSING, READY, FAILED disediakan dalam constraint untuk tahap
pemrosesan mendatang. Tahap ini hanya menulis UPLOADED dan DELETING.
UPLOADED tidak berarti konten sudah diekstrak atau dapat ditanya melalui AI.

## Package, class, dan method

Seluruh class baru berada di com.documind.document.
Record Java menghasilkan constructor, accessor, equals, dan hashCode secara otomatis.
Accessor seperti id() hanya mengembalikan nilai field.

| Class | Fungsi dan method |
|---|---|
| StorageProperties | Record konfigurasi root dan maxDocumentsPerUser. Validasi menolak root kosong dan kuota kurang dari 1. |
| StorageConfig | Mengaktifkan binding StorageProperties ke konfigurasi app.storage. |
| DocumentRecord | Bentuk data internal satu baris tabel, termasuk ownerId dan sha256. Tidak dikirim langsung ke client. |
| DocumentResponse | Bentuk metadata publik. from(record) memilih field yang boleh dikirim. |
| DocumentPage | Membungkus items, page, size, totalElements untuk daftar bertingkat halaman. |
| ValidatedFile | Hasil pemeriksaan nama, MIME, tipe, byte, dan SHA-256. toString() tidak mencetak isi file. |
| FileValidator | Constructor membaca batas upload. validate(file) mengatur urutan pemeriksaan dan menghasilkan ValidatedFile. |
| FileStorage | Constructor membuat direktori dan menentukan lokasi sebenarnya. save(id,bytes), read(id), delete(id) melakukan operasi file. pathFor(id) membuat nama UUID.bin dalam direktori yang ditentukan. |
| DocumentRepository | Constructor menerima JdbcTemplate. Semua query yang menerima ID dokumen juga membatasi owner_id. |
| DocumentService | Constructor menerima repository, validator, storage, konfigurasi, dan transaction manager. Menjalankan aturan bisnis serta koordinasi disk/database. |
| DocumentController | Constructor menerima service. Menghubungkan HTTP dengan service. owner(jwt) mengambil UUID pengguna dari token yang sudah diverifikasi. |

Method privat FileValidator:

| Method | Fungsi |
|---|---|
| validateName | Menolak nama kosong, panjang berlebih, separator path, karakter kontrol, atau nama tanpa ekstensi yang sesuai. |
| validatePdf | Memeriksa header, struktur dengan PDFBox, enkripsi, dan jumlah halaman. Tidak mengekstrak teks. |
| validateDocx | Memeriksa isi arsip ZIP secara terbatas, kemudian struktur OOXML dengan POI. |
| validateText | Memastikan UTF-8 valid, tidak kosong, dan tidak mengandung karakter kontrol biner. |
| invalid | Membentuk error 400 dengan kode INVALID_FILE. |

Method DocumentRepository:

| Method | Fungsi |
|---|---|
| lockActiveOwner | Mengunci baris akun aktif selama transaksi upload. |
| countOwned | Menghitung semua dokumen pengguna, termasuk DELETING. |
| create | Menyimpan metadata dan mengembalikan baris hasil INSERT. |
| listOwned | Mengambil halaman dokumen pemilik, urutan terbaru lalu ID. |
| findOwned | Mengambil satu dokumen dengan kombinasi ID dan pemilik. |
| markDeleting | Menyimpan status penghapusan sebelum operasi filesystem. |
| deleteOwned | Menghapus metadata yang sudah berstatus DELETING. |
| mapRow | Mengubah hasil SQL menjadi DocumentRecord. |

Method DocumentService dan controller:

| Method | Fungsi |
|---|---|
| upload | Validasi, batas jumlah, transaksi metadata, simpan file, dan pembersihan file bila transaksi gagal. Controller mengirim 201 dan header Location. |
| list | Memvalidasi page/size dan mengirim halaman milik pengguna. |
| get | Mengirim metadata satu dokumen milik pengguna. |
| download | Menolak DELETING, membaca file, memeriksa ukuran, dan membuat Download. Controller menambahkan MIME, attachment, dan no-store. |
| delete | Menjalankan penghapusan bertahap; controller mengirim 204 setelah berhasil. |
| owned | Helper service untuk pemeriksaan ID sekaligus pemilik. |
| notFound | Membentuk error 404 generik. |
| storageError | Membentuk error 503 tanpa membocorkan path server. |
| Download.toString | Hanya menampilkan ID, tanpa isi dokumen. |

Perubahan class lama:

- SecurityConfig.securityFilterChain: mengizinkan endpoint dokumen hanya dengan
  autentikasi; pemeriksaan kepemilikan tetap di query repository.
- GlobalExceptionHandler.handleTooLarge: respons 413 untuk ukuran multipart.
- handleMultipart: respons 400 untuk format multipart atau field file yang salah.
- handleParameter: respons 400 untuk UUID dan parameter halaman yang tidak valid.
- handleDatabase: respons 503 generik bila akses database gagal.
- Method autentikasi tahap 2 tetap dijelaskan pada tahap-2.md.

## Endpoint dan kontrak

Semua endpoint berikut memerlukan Authorization: Bearer TOKEN.

| Method | Endpoint | Hasil |
|---|---|---|
| POST | /api/documents | multipart/form-data, field file; respons 201 metadata |
| GET | /api/documents?page=0&size=20 | Daftar milik sendiri |
| GET | /api/documents/{id} | Metadata; 200 atau 404 |
| GET | /api/documents/{id}/download | Byte file asli sebagai attachment |
| DELETE | /api/documents/{id} | 204 tanpa body setelah metadata dan file terhapus |

Page dimulai dari 0, maksimal 100000. Size 1–100, default 20.
Field ownerId tambahan tidak digunakan: pemilik selalu subject JWT.
Nama file sama boleh diunggah lagi dan menjadi dokumen berbeda; kuota tetap berlaku.

Contoh metadata:

```json
{
  "id": "UUID-DARI-SERVER",
  "filename": "catatan.txt",
  "mediaType": "text/plain",
  "fileType": "TXT",
  "sizeBytes": 36,
  "status": "UPLOADED",
  "uploadedAt": "2026-11-16T10:00:00Z"
}
```

Contoh PowerShell (gunakan akun sendiri dan jangan menyimpan token di repository):

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType application/json -Body (@{ email = 'anda@example.com'; password = 'password-anda' } | ConvertTo-Json)
$token = $login.accessToken
curl.exe -H "Authorization: Bearer $token" -F "file=@C:/path/catatan.txt" http://localhost:8080/api/documents
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod http://localhost:8080/api/documents -Headers $headers
# Ganti ID dengan id dari hasil upload.
Invoke-WebRequest http://localhost:8080/api/documents/ID/download -Headers $headers -OutFile .\hasil.txt
Invoke-RestMethod -Method Delete http://localhost:8080/api/documents/ID -Headers $headers
```

| Status error | Arti |
|---|---|
| 400 | File rusak/kosong, nama tidak valid, multipart tidak lengkap, atau parameter tidak valid |
| 401 | Token tidak ada, tidak valid, kedaluwarsa, atau akun tidak aktif |
| 404 | ID tidak ada atau dokumen bukan milik pengguna; file DELETING tidak dapat diunduh |
| 409 | Kuota jumlah dokumen tercapai |
| 413 | Ukuran file/request melebihi konfigurasi |
| 415 | Ekstensi tidak didukung |
| 503 | Penyimpanan atau database sementara gagal |

## Validasi dan konfigurasi

- Default MAX_UPLOAD_SIZE=10MB, MAX_REQUEST_SIZE=11MB. MB di konfigurasi Spring
  berarti kelipatan 1024. Batas request harus lebih besar dari file untuk overhead
  multipart. Validasi internal hanya mengizinkan batas file hingga 50MB.
- MAX_DOCUMENTS_PER_USER=50 dapat diubah di .env. Dokumen DELETING masih dihitung.
- PDF: header dan struktur valid, tidak terenkripsi, 1–2000 halaman.
  PDF scan dapat disimpan; OCR belum tersedia.
- DOCX: OOXML Word tanpa macro; maksimal 2000 entri ZIP dan 50MiB total
  hasil dekompresi. Proteksi ZIP POI juga tetap aktif.
- TXT: UTF-8, isi tidak kosong; newline, tab, dan carriage return diperbolehkan.
- MIME dari browser tidak dipercaya. MIME respons ditentukan setelah validasi isi.
- File asli disimpan tanpa perubahan dan tidak disajikan melalui direktori publik.
  Nama fisik UUID.bin mencegah nama upload menjadi path.
- CREATE_NEW mencegah penimpaan file jika UUID berbenturan. Download menolak symlink.
- STORAGE_ROOT default ./data/documents untuk menjalankan langsung dengan Java;
  path relatif mengikuti working directory proses.
- Compose menetapkan /app/data/documents dengan named volume documents_data.
  Volume postgres_data menyimpan metadata database.
- Backend dalam container berjalan sebagai pengguna non-root. Direktori volume
  baru dibuat dengan pemilik yang sesuai pada image.

## Batas tahap ini dan pemulihan

Validasi struktur bukan antivirus. Konten dokumen belum dieksekusi, ditampilkan inline,
atau dikirim ke Gemini. Belum ada OCR, preview, streaming download, rate limiter upload,
dan batas pemakaian AI. File dibaca di memori dengan batas ukuran upload; penerapan
publik perlu meninjau jumlah request bersamaan dan kapasitas memori.

Database dan filesystem bukan satu transaksi atomik. Kegagalan biasa saat INSERT
akan me-rollback metadata dan membersihkan file. Proses mati mendadak di antara
penyimpanan file dan commit masih bisa meninggalkan file tanpa metadata.
Belum ada pembersih otomatis untuk kondisi tersebut. Sebelum membersihkan, cocokkan
UUID file dengan tabel documents dan buat backup. Jangan menghapus file hanya dari
nama asli atau usia file.

Jika DELETE gagal, ulangi endpoint yang sama sampai berhasil. Status DELETING tetap
tersimpan setelah restart dan tidak otomatis diproses worker. Kuota baru bebas
setelah metadata berhasil dihapus.

Backup dan restore harus mencakup postgres_data serta documents_data pada keadaan
yang konsisten. Restart/recreate container mempertahankan volume. Perintah
docker compose down -v menghapus keduanya; jangan gunakan jika data diperlukan.

## Pengujian

InfrastructureIT memakai PostgreSQL/pgvector sungguhan melalui Testcontainers,
direktori upload sementara yang terpisah dari data aplikasi, serta HTTP pada port acak.
Test menguji ketiga format, kepemilikan termasuk ADMIN, token, pagination, kuota
bersamaan, batas ukuran, penghapusan, dan kegagalan disk/metadata.
Spy hanya digunakan dalam test untuk mensimulasikan kegagalan terkontrol.

## Referensi resmi

- PDFBox 3.0.8: https://pdfbox.apache.org/download.html
- Loader pada PDFBox 3: https://pdfbox.apache.org/3.0/migration.html
- Apache POI: https://poi.apache.org/
- Proteksi ZIP POI: https://poi.apache.org/apidocs/dev/org/apache/poi/openxml4j/util/ZipSecureFile.html

Tahap berikutnya setelah persetujuan: ekstraksi teks Java, nomor halaman PDF,
pemrosesan status, dan chunking sebelum integrasi embedding/RAG.
