# Hasil verifikasi tahap 6

Tanggal: 16 November 2026. Perubahan dikerjakan langsung di C:\Users\ThinkPad\Documents\documind-ai.

## Hasil akhir

Backend tahap 6 berhasil dibangun dan dijalankan di Docker. Database dan backend berstatus healthy. GET /actuator/health/readiness menghasilkan HTTP 200 dengan status UP.

Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, PostgreSQL 17.12, dan pgvector tetap digunakan. Model chat final adalah gemini-3.5-flash-lite; embedding tetap gemini-embedding-001 dengan 768 dimensi.

## Tes otomatis

Perintah Maven verify pada source final menghasilkan BUILD SUCCESS:

| Kelompok | Jumlah | Hasil |
|---|---:|---|
| ExtractionTest | 5 | Lulus |
| GeminiAdapterTest | 4 | Lulus |
| GeminiChatAdapterTest | 2 | Lulus |
| GroundedAnswerTest | 5 | Lulus |
| JwtConfigTest | 2 | Lulus |
| VectorHealthIndicatorTest | 3 | Lulus |
| InfrastructureIT | 48 | Lulus |
| Total | 69 | 0 gagal, 0 error, 0 dilewati |

Integration test memakai PostgreSQL dan pgvector sungguhan dalam container terpisah. Panggilan model dalam integration test diganti model tiruan agar hasil deterministik dan tidak menghabiskan kuota provider. Tes adapter menggunakan SDK/ChatModel asli dengan HTTP server lokal untuk memeriksa bentuk request.

Cakupan tambahan tahap 6:

- Migrasi V6 dan hubungan owner/dokumen/percakapan.
- Task embedding pertanyaan RETRIEVAL_QUERY.
- JSON response mode, model endpoint, batas token, serta tidak dikirimkannya parameter sampling lama.
- Satu percobaan API ketika provider membalas 429.
- Kutipan asli, metadata dari server, normalisasi whitespace, penolakan sumber/kutipan rekaan.
- Pencarian dibatasi pada satu dokumen READY.
- Pengguna lain dan ADMIN tidak dapat membaca/mencari/mengubah percakapan milik orang lain.
- Riwayat dan dua pertanyaan terakhir untuk follow-up.
- Replay requestId tanpa generasi baru; requestId sama dengan pertanyaan berbeda ditolak.
- Dua request identik bersamaan menghasilkan satu jawaban baru dan satu replay.
- Fallback tanpa hit vector dan fallback ketika model menyatakan informasi tidak ada.
- Respons AI tidak valid/provider gagal tidak menghasilkan turn tersimpan; kuota reservasi tetap tercatat.
- Kuota chat menolak sebelum memanggil model dan membatalkan reservasi global bila kuota pengguna penuh.
- Hapus percakapan mempertahankan dokumen; hapus dokumen menghapus riwayat terkait.

## Uji Gemini sungguhan

Dua akun sementara dibuat untuk menguji pemilik dan pengguna lain. Dokumen TXT contoh berisi biaya seminar, waktu seminar, dan batas pendaftaran. Dokumen berhasil mencapai READY dengan satu chunk dan satu vector.

| Pemeriksaan | Hasil aktual |
|---|---|
| Pencarian biaya seminar | HTTP 200; satu chunk dari dokumen yang benar, cosine similarity sekitar 0,7802. |
| Buat percakapan | HTTP 201. |
| “Berapa biaya pendaftaran seminar?” | HTTP 201; “Biaya pendaftaran seminar adalah Rp150.000 per peserta. [1]” |
| Referensi jawaban | documentId/chunkId benar, filename seminar-tahap6.txt, quote sesuai teks asli, pageNumber null untuk TXT. |
| Kirim ulang requestId pertama | HTTP 200, ID turn dan jawaban sama. |
| “Kapan pendaftarannya ditutup?” | HTTP 201; kutipan tanggal 18 November 2026. |
| “Siapa nama pembicara seminar tersebut?” | HTTP 201; found=false, sources=[], informasi tidak ditemukan. |
| Pengguna lain membaca riwayat | HTTP 404. |
| Pengguna lain mencari dokumen | HTTP 404. |
| Riwayat sebelum restart | Tiga turn. |
| Restart database dan backend | Keduanya kembali healthy; readiness UP. |
| Riwayat sesudah restart | Tiga turn beserta jawaban dan sumber sama persis. |
| Dokumen sesudah restart | Tetap READY. |
| Hapus dokumen contoh | HTTP 204; percakapan menjadi 404 dan jumlah turn terkait menjadi 0. |

Akun dan dokumen contoh sudah dihapus setelah pengujian. Ledger kuota dipertahankan. Untuk akun uji tercatat empat reservasi chat: satu kegagalan model awal dan tiga panggilan final. Replay yang berhasil tidak menambah reservasi.

## Penyesuaian model berdasarkan hasil nyata

Uji pertama pada gemini-2.5-flash-lite menghasilkan HTTP 404 dari Google: akses model itu tidak tersedia bagi pengguna baru. Munculnya nama model pada daftar models tidak cukup membuktikan akun dapat menggunakannya.

Setelah memeriksa dokumentasi resmi dan satu request diagnostik, default diubah menjadi gemini-3.5-flash-lite. Parameter temperature, topP, topK, dan candidateCount tidak dikirim. Tiga skenario chat di atas kemudian berhasil melalui backend Java/Spring AI, bukan hanya melalui request diagnostik.

Spring AI 1.1.8 menggunakan generateContent. Endpoint ini berhasil pada konfigurasi yang diuji; rekomendasi Google untuk Interactions API dicatat pada panduan tahap 6 untuk evaluasi pengembangan selanjutnya.

## Artefak dan batas hasil

- Source, konfigurasi, migrasi, tes, README, dan panduan belajar ada di folder proyek pengguna.
- .env berisi rahasia lokal dan tidak dimasukkan ke ZIP. Pemeriksaan source yang dikemas tidak menemukan nilai API key aktif.
- Tidak ada Python atau layanan Python dalam backend, pemrosesan dokumen, embedding, retrieval, maupun chat.
- Jawaban tahap ini berupa kutipan sumber. Tes ini membuktikan skenario yang diuji, bukan jaminan semua pertanyaan akan memilih kutipan yang paling relevan.
- Frontend belum dibuat. Halaman health masih respons JSON. Ringkasan otomatis, OCR, dan pencarian lintas dokumen belum termasuk tahap ini.

Panduan setiap class/method dan contoh endpoint: [tahap-6.md](tahap-6.md).
