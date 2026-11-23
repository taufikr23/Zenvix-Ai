# Verifikasi tahap 2 — autentikasi

> Arsip tahap 2. Fitur terkini dijelaskan pada tahap-3.md dan hasil-verifikasi-tahap-3.md.

Tanggal: 16 November 2026.
Status: **LULUS**.

## Pengujian otomatis

Maven verify selesai dengan BUILD SUCCESS menggunakan JDK 21.

| Kelompok | Jumlah | Gagal/error |
|---|---:|---:|
| VectorHealthIndicatorTest | 3 | 0 |
| JwtConfigTest | 2 | 0 |
| InfrastructureIT | 17 | 0 |
| Total | 22 | 0 |

InfrastructureIT memakai PostgreSQL + pgvector sungguhan melalui Testcontainers.
Cakupan mencakup regresi tahap 1 dan autentikasi: hashing, validasi, duplikasi
bersamaan, JWT, status akun, dan pemilihan profil berdasarkan identitas server.

## Pemeriksaan aplikasi Docker

| Pemeriksaan | Hasil aktual |
|---|---|
| Build image tahap 2 | Berhasil |
| Backend dan database | running / healthy |
| POST register akun sementara | 201 |
| POST login | 200 |
| GET profil dengan Bearer token | 200, ID sesuai akun |
| Password salah | 401 |
| Token tidak valid | 401 |
| Cache-Control login | no-store |
| Masa berlaku token yang diterbitkan | 900 detik |
| Hash/password dalam respons profil | Tidak ada |
| Token yang sama sesudah restart backend | 200 |
| Login akun yang sama sesudah restart backend | 200 |
| Menghapus akun pengujian | DELETE 1 |
| Token akun sesudah penghapusan | 401 |
| Sisa akun smoke-auth | 0 |
| Migrasi V1/V2 | Tetap 2 migrasi sukses |

Akun smoke test hanya digunakan untuk verifikasi dan sudah dihapus.
Tidak ada akun login atau ADMIN bawaan yang dibuat.

## Konfigurasi dan data lama

- Password database lama dipertahankan ketika JWT_SECRET ditambahkan.
- Menjalankan setup-env.ps1 kembali menghasilkan konfigurasi identik.
- JWT_SECRET hanya berada dalam .env lokal dan environment backend.
- .env, hasil build target, cache, dan log tidak masuk ZIP.
- Migrasi lama tidak diubah dan volume database tidak dihapus.
- Backend yang sedang berjalan sudah menggunakan source tahap 2.

## Pembacaan hasil

Tahap 2 sudah selesai pada registrasi, login, profil, dan autentikasi JWT.
Lihat tahap-2.md untuk penjelasan semua class/method serta contoh permintaan API.

Belum tersedia frontend, upload dokumen, RAG, refresh token, verifikasi email,
reset password, logout dengan pencabutan token, dan pembatasan percobaan login.
Koneksi layanan tetap dibatasi localhost. Pengelolaan dokumen dan kepemilikannya
menjadi tahap berikutnya setelah persetujuan pengguna.
