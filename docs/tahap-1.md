# Penjelasan tahap 1 — fondasi aplikasi

> Arsip pembelajaran tahap 1. SecurityConfig dan setup-env.ps1 kini berkembang pada tahap 2.
> Untuk perilaku saat ini, baca tahap-2.md.

## Mengapa dimulai dari database?

Upload, akun, embedding, dan percakapan semuanya memerlukan penyimpanan yang benar.
Tahap pertama membuktikan fondasinya sebelum menambah fitur bisnis.

## Struktur file

| Lokasi | Fungsi |
|---|---|
| backend/pom.xml | Menentukan Java 21, dependensi, versi Spring, dan cara build Maven |
| backend/Dockerfile | Membuat JAR dengan Maven, kemudian menjalankannya sebagai pengguna non-root |
| backend/.dockerignore | Mencegah target build dan environment lokal masuk ke konteks build |
| backend/src/main/java/com/documind | Package utama yang dipindai Spring Boot |
| backend/src/main/java/com/documind/config | Konfigurasi dan indikator kesehatan vector |
| backend/src/main/java/com/documind/security | Aturan akses endpoint tahap awal |
| backend/src/main/resources/application.yml | Konfigurasi database, Flyway, batas upload, dan Actuator |
| backend/src/main/resources/db/migration | SQL berurutan untuk membentuk database |
| backend/src/test/java/com/documind | Pengujian kesehatan, migrasi, keamanan, dan constraint |
| compose.yaml | Menjalankan backend dan database beserta volume serta health check |
| .env.example | Contoh konfigurasi tanpa password asli |
| scripts/setup-env.ps1 | Membuat konfigurasi lokal dengan password acak |
| scripts/verify-compose.ps1 | Menguji health dan persistensi dengan restart container |
| .gitignore | Mengecualikan rahasia dan hasil build dari Git |

Package fitur auth, user, document, rag, chat, dan admin akan ditambahkan pada tahapnya.
Tidak ada class kosong untuk fitur yang belum dikerjakan.

## Dependensi Maven

- Web: menyediakan server HTTP Spring MVC.
- JDBC: menyediakan DataSource, connection pool HikariCP, dan JdbcTemplate.
- PostgreSQL driver: penghubung JDBC dengan PostgreSQL.
- Actuator: menyediakan endpoint kesehatan aplikasi.
- Security: membatasi endpoint yang boleh diakses.
- Flyway core + PostgreSQL: menjalankan dan memvalidasi migrasi database.
- Spring AI BOM: menyelaraskan versi dependensi AI untuk tahap berikutnya; tidak menjalankan model.
- Starter test: JUnit, AssertJ, Mockito, dan fasilitas pengujian Spring.
- Testcontainers: menjalankan PostgreSQL sungguhan khusus integration test.
- Failsafe: menjalankan class berakhiran IT pada fase integration-test dan memeriksa hasil pada verify.
- Surefire bawaan Spring Boot: menjalankan unit test berakhiran Test saat fase test.

JDBC dipakai dahulu untuk membuktikan koneksi dan health. Entity/repository bisnis belum diperlukan.

## Class dan method aplikasi

### DocumindApplication

Anotasi SpringBootApplication menandai titik awal aplikasi dan pemindaian komponen.

Method main(String[] args):
- Input: argumen ketika Java dijalankan.
- Proses: SpringApplication.run memuat konfigurasi, membuat komponen, lalu menjalankan server.
- Hasil: backend aktif jika konfigurasi serta migrasi berhasil; startup gagal jika fondasi database gagal.

### SecurityConfig

Anotasi Configuration menyatakan class ini menyediakan konfigurasi Spring.

Method securityFilterChain(HttpSecurity http):
- Input: pembangun aturan keamanan milik Spring.
- Mengizinkan GET pada endpoint health.
- Menolak endpoint lain, termasuk endpoint bisnis yang belum dibuat.
- Menggunakan sesi stateless; aplikasi tidak membuat sesi login server.
- Mengembalikan SecurityFilterChain yang memeriksa request sebelum diteruskan.
- Belum ada login, form login, HTTP Basic, atau JWT.
- Proteksi CSRF bawaan tidak dinonaktifkan; kebutuhan endpoint mutasi dibahas saat autentikasi.

UserDetailsServiceAutoConfiguration dinonaktifkan pada application.yml agar Spring
tidak membuat akun development dan mencetak password otomatis.

### VectorHealthIndicator

Implements HealthIndicator agar hasilnya masuk pemeriksaan Actuator.
Nama komponen vector digunakan oleh grup readiness.

Constructor VectorHealthIndicator(JdbcTemplate jdbcTemplate):
menerima penghubung database dari Spring melalui dependency injection.

Method health():
- Menjalankan SELECT dengan cast ke tipe vector dan operator jarak.
- Jarak sebuah vektor ke dirinya sendiri harus bernilai nol.
- Mengembalikan UP bila operasi bekerja.
- Mengembalikan DOWN jika database tidak bisa diakses, ekstensi hilang, atau hasil tidak sesuai.
- Tidak mengembalikan exception atau detail koneksi kepada pengguna.

Actuator juga menyediakan indikator db bawaan untuk koneksi database.

## Migrasi database

V1__enable_pgvector.sql memasang ekstensi vector jika belum ada.
V2__create_users.sql membuat tabel awal akun.

| Kolom users | Arti |
|---|---|
| id | UUID unik, dibuat PostgreSQL jika tidak diberikan |
| email | Alamat email; indeks lower(email) mencegah duplikasi beda kapital |
| password_hash | Tempat hash password; belum ada endpoint yang mengisi akun |
| role | USER atau ADMIN, divalidasi constraint |
| enabled | Menandai apakah akun aktif |
| created_at | Waktu pembuatan dengan zona waktu |
| updated_at | Waktu perubahan; nanti diperbarui service saat akun diubah |

Constraint email saat ini hanya batas dasar dan indeks unik; validasi format email dilakukan
pada tahap registrasi. Nilai updated_at tidak berubah otomatis pada setiap UPDATE.

Flyway membuat tabel flyway_schema_history untuk versi, checksum, dan hasil migrasi.
Jangan mengubah migrasi yang sudah diterapkan; tambahkan V3, V4, dan seterusnya.
Clean dinonaktifkan agar aplikasi tidak menghapus schema lewat Flyway.

Tabel dokumen, chunk, embedding, dan percakapan dibuat pada tahap masing-masing.
Belum ada embedding vector(768) karena model dan pipeline belum diuji langsung.

## Build Docker dan cache Maven

Dockerfile membuat JAR langsung dengan Maven package. Dependensi disimpan melalui
cache BuildKit pada /root/.m2 supaya build selanjutnya dapat menggunakannya kembali.
Langkah dependency:go-offline dihapus setelah build awal tertahan pada langkah tersebut.
Unit dan integration test dijalankan terpisah dengan Maven verify; build image tidak
menggantikan pengujian tersebut.

## Alur startup dan request

1. Compose membaca .env dan memulai PostgreSQL.
2. PostgreSQL menggunakan named volume agar data bertahan setelah restart.
3. pg_isready menandai database siap menerima koneksi.
4. Compose memulai backend menggunakan hostname database db.
5. Spring membuat DataSource dan Flyway menjalankan migrasi yang belum diterapkan.
6. Backend menyediakan Actuator. Readiness mengecek keadaan aplikasi, koneksi, dan operasi vector.
7. Request GET health melewati aturan SecurityConfig.
8. Actuator menggabungkan status, lalu mengembalikan JSON tanpa detail internal.

Readiness berbeda dari liveness: database mati membuat backend belum siap melayani pekerjaan,
tetapi tidak selalu berarti proses Java harus dimulai ulang.

## Method pengujian

VectorHealthIndicatorTest:
- workingVectorQueryReportsUp: operasi vector normal harus menghasilkan UP.
- databaseFailureDoesNotLeakConnectionDetails: kegagalan tidak membocorkan pesan berisi rahasia.
- missingVectorResultReportsDown: hasil database kosong harus dianggap tidak sehat.

InfrastructureIT:
- migrationsAndVectorOperationsWork: memeriksa dua migrasi dan operator jarak pgvector nyata.
- healthIsPublicWithoutDatabaseDetails: health/readiness bisa diakses tanpa membuka detail koneksi.
- otherEndpointsAreClosed: endpoint dokumen dan konfigurasi internal tetap tertutup.
- emailUniquenessIgnoresLetterCase: database menolak akun duplikat dengan kapital berbeda.
Container pengujian terpisah dari database Compose dan dibersihkan oleh Testcontainers.

## Fungsi script PowerShell

setup-env.ps1 tidak memiliki fungsi tambahan. Script memeriksa .env, membuat 32 byte acak,
mengubahnya menjadi heksadesimal, lalu menulis konfigurasi UTF-8 tanpa BOM.
Konfigurasi yang sudah ada tidak ditimpa.

verify-compose.ps1:
- Invoke-Compose(ComposeArguments): menjalankan Docker Compose dan menghentikan script bila gagal.
- Assert-Health(): meminta endpoint health, memastikan UP, dan memastikan detail internal tidak terbuka.
- Alur utama: build/start, tulis satu baris pengguna sementara, restart, cek keberadaan baris,
  lalu hapus hanya baris pengujian tersebut.
- Script tidak menghapus container volume. Jika pembersihan baris gagal, script menampilkan peringatan.

## Tahap berikutnya setelah persetujuan

Registrasi dan login, hashing password, JWT, validasi akun, lalu pengujian akses.
Setiap endpoint akan dijelaskan dengan contoh request, response, dan alur pemanggilan method.
