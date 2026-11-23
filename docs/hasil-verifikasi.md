# Hasil verifikasi tahap 1

Tanggal: 16 November 2026.
Status: **LULUS — setup backend dan koneksi database selesai.**

## Hasil aktual

| Pemeriksaan | Hasil |
|---|---|
| Build Java 21, Spring Boot 3.5.16, Spring AI BOM 1.1.8 | Berhasil |
| Unit test VectorHealthIndicatorTest | 3 lulus, 0 gagal, 0 error |
| Integration test InfrastructureIT | 4 lulus, 0 gagal, 0 error |
| Maven verify | BUILD SUCCESS |
| Build backend menggunakan Dockerfile | Berhasil |
| docker compose up --build --wait | Berhasil |
| Container backend | running / healthy |
| Container database | running / healthy |
| PostgreSQL aktual | 17.12 |
| Ekstensi pgvector aktual | 0.8.7 |
| Migrasi V1 dan V2 | Keduanya success=true |
| GET /actuator/health | HTTP 200, status UP |
| GET /actuator/health/readiness | HTTP 200, status UP |
| GET /api/documents | HTTP 403, sesuai aturan tahap 1 |
| Data tetap tersedia setelah restart | Lulus |
| Pembersihan baris uji | DELETE 1; jumlah users kembali 0 |
| scripts/verify-compose.ps1 | Selesai dengan exit code 0 |

## Apa yang dibuktikan oleh tes?

- Ekstensi vector dapat menghitung jarak vektor.
- Kedua migrasi diterapkan pada PostgreSQL sungguhan.
- Health bisa diakses tanpa membocorkan rincian koneksi.
- Endpoint bisnis dan konfigurasi internal belum terbuka.
- Database menolak email duplikat meski kapital berbeda.
- Named volume mempertahankan data saat database dan backend di-restart.

Testcontainers menjalankan database khusus pengujian yang terpisah dari Compose.
Uji persistensi menggunakan satu baris sementara pada database Compose.
Baris tersebut sudah dihapus; tidak dibuat akun login bawaan.

## Perubahan build Docker

Langkah dependency:go-offline pada Dockerfile awal tertahan cukup lama.
Dockerfile diperbarui agar langsung menjalankan Maven package dengan cache BuildKit
pada /root/.m2. Build image sesudah perubahan berhasil. Eksekusi tes tetap dilakukan
terpisah melalui Maven verify, karena Dockerfile melewati eksekusi tes saat packaging.

## Cara melihat hasil

Buka http://localhost:8080/actuator/health.

Respons saat verifikasi:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

Respons JSON ini adalah pemeriksaan backend. Frontend belum dibuat pada tahap pertama.
Alamat root atau endpoint bisnis belum merupakan halaman aplikasi.

## Lingkungan pengembangan

- Docker Engine 29.7.2; Docker Compose v5.4.0.
- JDK pengujian: Temurin 21.0.13 di C:/workspace/documind-runtime/jdk-21.0.13+9.
- Cache Maven lokal pengujian: C:/workspace/documind-runtime/m2.
- Build image menggunakan Java 21 di Docker dan tidak membutuhkan Java lokal.
- Port backend: localhost:8080; port database untuk host: localhost:5433.
- Docker Desktop harus tetap berjalan agar kedua layanan dapat diakses.

Kendala Docker pada pengujian sebelumnya sudah tidak menghalangi pengujian ini.
Tidak dilakukan reset pabrik atau penghapusan data Docker.

## Batas hasil

Yang selesai adalah setup backend dan koneksi database. Registrasi, login, JWT,
upload dokumen, embedding, RAG, dan frontend belum diimplementasikan.
Tahap berikutnya adalah autentikasi, setelah pengguna menyetujui hasil tahap pertama.
