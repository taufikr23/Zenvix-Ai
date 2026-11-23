# Tahap 6 — Pencarian dokumen dan AI Chat RAG

Tahap ini menambahkan pencarian semantik dalam satu dokumen, percakapan, jawaban dengan kutipan sumber, serta riwayat di PostgreSQL. Seluruh alur tetap Java. Frontend dikerjakan pada tahap berikutnya.

## Keputusan rancangan

Satu percakapan terikat pada satu dokumen milik pengguna. Dokumen harus READY sebelum dicari atau digunakan untuk percakapan. Pencarian lintas banyak dokumen belum masuk tahap ini.

Jawaban awal menggunakan kutipan asli yang dipilih Gemini. Model mengembalikan JSON berisi ID sumber sementara dan kutipan. Backend memeriksa bahwa kutipan terdapat pada chunk hasil pencarian, lalu membentuk teks jawaban dan referensi dari metadata database. Model tidak menentukan nama file, nomor halaman, atau ID dokumen.

Cara ini membatasi penambahan fakta yang tidak ada dalam dokumen. Namun, kecocokan teks tidak membuktikan bahwa kutipan pasti menjawab pertanyaan dengan tepat. Evaluasi relevansi tetap diperlukan. Tahap ini belum melakukan parafrasa bebas, perhitungan, atau ringkasan seluruh dokumen.

## Alur request

1. Spring Security memvalidasi JWT dan status akun.
2. Backend mengambil owner dari subject JWT, bukan dari JSON pengguna.
3. Backend memeriksa kepemilikan percakapan dan mengunci baris dokumen READY.
4. requestId diperiksa. Pengiriman ulang request yang sudah berhasil mengembalikan hasil lama.
5. Dua pertanyaan terakhir, masing-masing maksimal 250 karakter Java, membantu memahami pertanyaan lanjutan. Jawaban AI sebelumnya tidak dijadikan sumber fakta.
6. Kuota embedding dicadangkan. Pertanyaan dibuat menjadi vector dengan task RETRIEVAL_QUERY.
7. PostgreSQL mencari chunk hanya pada dokumen dan owner tersebut, menggunakan cosine similarity.
8. Maksimal lima chunk dengan skor minimal 0,45 dipilih; keseluruhan teks konteks dibatasi 6.000 karakter Java.
9. Jika tidak ada chunk, backend langsung menghasilkan jawaban informasi tidak ditemukan.
10. Jika ada chunk, kuota chat dicadangkan dan konteks dikirim ke Gemini.
11. Backend memvalidasi JSON, ID sumber, dan kutipan sebelum menyimpan pertanyaan, jawaban, serta sumber sebagai satu transaksi.
12. Client menerima jawaban dan referensi. Riwayat tetap tersedia setelah restart.

Ambang similarity adalah parameter pencarian, bukan persentase kebenaran. Nilainya perlu dievaluasi dengan dokumen pengguna. Chunk yang melebihi sisa batas konteks dilewati, bukan dipotong.

## Model dan konfigurasi resmi

Versi tetap Java 21, Spring Boot 3.5.16, dan Spring AI 1.1.8. Dependency spring-ai-google-genai ditambahkan untuk ChatModel; dependency embedding tetap digunakan. Model chat default gemini-3.5-flash-lite tersedia pada akun yang diuji.

ChatConfig membuat GoogleGenAiChatModel dengan client Google GenAI, tanpa parameter sampling lama, maxOutputTokens 1024, responseMimeType application/json, dan eksekusi tool internal dimatikan. Tidak ada Google Search atau tool eksternal.

Model 2.5 Flash-Lite ditolak HTTP 404 pada uji akun ini karena aksesnya dibatasi bagi pengguna lama. Model 3.5 Flash-Lite berhasil pada uji API. Parameter temperature/topP/topK/candidateCount tidak dikirim, mengikuti panduan migrasi model. Spring AI 1.1.8 memakai generateContent, yang masih didukung meskipun Google merekomendasikan Interactions untuk integrasi baru. Migrasi API memerlukan evaluasi terpisah.

Referensi yang diperiksa:

- [Gemini 3.5 Flash-Lite](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite)
- [Panduan migrasi parameter Gemini](https://aistudio.google.com/docs/whats-new-gemini-3.6)

- [Spring AI 1.1 — Google GenAI Chat](https://docs.spring.io/spring-ai/reference/1.1/api/chat/google-genai-chat.html)
- [Source Spring AI 1.1.8](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-google-genai/1.1.8/spring-ai-google-genai-1.1.8-sources.jar)
- [Gemini — Embeddings](https://ai.google.dev/gemini-api/docs/embeddings)
- [Gemini — Jadwal penghentian model](https://ai.google.dev/gemini-api/docs/deprecations)

Nama model dapat diubah lewat environment, tetapi model pengganti harus diuji kompatibilitas dan ketersediaannya. Pemilihan default ini bukan jaminan ketersediaan selamanya.

## Database

Migrasi V6 menambahkan tiga tabel.

| Tabel | Data dan fungsi |
|---|---|
| conversations | ID percakapan, owner, document, waktu dibuat. FK gabungan document dan owner memastikan hubungan ke dokumen pemilik yang sama. |
| chat_turns | Satu pasangan pertanyaan/jawaban beserta requestId, urutan, found, sumber JSONB, dan waktu dibuat. |
| chat_daily_usage | Jumlah request chat dan karakter input yang dicadangkan per tanggal UTC untuk aplikasi dan setiap pengguna. |

Kombinasi conversation_id dan request_id unik. Kombinasi conversation_id dan turn_index juga unik. Hapus percakapan menghapus seluruh turn. Hapus dokumen menghapus chunk, percakapan, dan turn terkait melalui ON DELETE CASCADE. Ledger kuota tidak dihapus bersama riwayat sehingga kuota tidak dapat direset dengan menghapus percakapan.

Pencarian vector menggunakan cosine distance <=>, lalu similarity = 1 - distance. Query menerapkan owner_id, document_id, status READY, embedding tidak null, dan model embedding yang sama. Pencarian exact scan cukup untuk skala portfolio awal; belum menggunakan indeks ANN/HNSW.

## Package, class, dan method

Package baru com.documind.chat mengelompokkan pencarian dan percakapan. Constructor menerima dependency dari Spring; constructor tidak memanggil Gemini.

| Class | Method/anggota utama dan fungsi |
|---|---|
| ChatProperties | Record konfigurasi. Accessor seperti topK(), minSimilarity(), maxTurns() membaca nilai tervalidasi dari application.yml. |
| ChatConfig | chatClient(): koneksi Gemini hanya ketika chat aktif, memeriksa embedding dan key; chatModel(): model Spring AI dengan output JSON dan satu percobaan. |
| ChatDtos | CreateConversation, Ask, Search: JSON masuk dan validasinya. Conversation, ConversationPage, Source, Turn, TurnPage: JSON keluar. AskResult membedakan hasil baru/replay; Answer membawa hasil validator. toString pada data teks disamarkan. |
| ChatErrors | missing(): error 404 generik; invalidAnswer(): error 502 keluaran AI tidak sah; page(): validasi pagination. |
| ChatConcurrency | execute(): maksimal dua operasi pencarian/chat bersamaan per instance; slot dilepas dalam finally meskipun operasi gagal. |
| RetrievalRepository | search(): SQL vector terfilter owner dan dokumen; Hit menyimpan chunk, teks, skor, metadata sumber. toString tidak mencetak konten. |
| RetrievalService | search(): transaksi endpoint pencarian; lockReady(): validasi owner/status dan lock dokumen; requireEnabled(): validasi fitur aktif; retrieve(): reservasi embedding, pencarian vector, dan batas konteks. retrieve dipanggil dalam transaksi yang sudah memegang lock dokumen. |
| ChatBudget | reserve(): transaksi terpisah untuk kuota aplikasi dan pengguna; take(): INSERT/UPDATE atomik dengan batas; limit(): error kuota 429. |
| ChatGateway | answer(): payload JSON dari pertanyaan/riwayat/chunk, reservasi kuota, panggil ChatModel, validasi hasil. SYSTEM berisi aturan penggunaan konteks. |
| GroundedAnswerValidator | validate(): struktur JSON, ID sumber, jumlah kutipan, keberadaan teks asli; normalize(): samakan whitespace; noAnswer(): jawaban baku tanpa sumber. |
| ConversationRepository | count(), list(), find(): baca percakapan milik owner; create(): simpan percakapan; countTurns(), turns(), existing(): riwayat dan request berhasil; recentQuestions(): dua pertanyaan terakhir; save(): simpan satu pasangan pesan; delete(): hapus milik owner; conversation()/turn(): row SQL menjadi DTO. |
| ConversationService | create(): validasi dokumen dan batas percakapan; get()/list()/messages(): akses privat; delete(): hapus percakapan; ask(): lock, idempotency, batas turn, retrieval, jawaban AI, penyimpanan. |
| ConversationController | create(), list(), get(), messages(), ask(), delete(): HTTP ke service; owner(): UUID dari JWT. |
| SearchController | search(): pencarian dengan query tervalidasi dan owner dari JWT. |

Perubahan package lama:

- indexing.GeminiEmbeddingModel.call() memilih RETRIEVAL_DOCUMENT untuk dokumen dan RETRIEVAL_QUERY untuk pertanyaan. Keduanya memakai gemini-embedding-001 dan 768 dimensi.
- indexing.EmbeddingGateway.embedQuery() membentuk EmbeddingRequest Spring AI bertask query, memeriksa hasil, dan menormalisasi vector. embed()/normalize() tetap digunakan indexing.
- indexing.IndexingConfig.embeddingModel() memakai Qualifier embeddingClient agar tidak tertukar dengan chatClient.
- security.SecurityConfig.securityFilterChain() menambahkan izin endpoint yang tersedia. Endpoint lain tetap ditolak.

Penjelasan package lama selengkapnya ada di panduan tahap 1–5.

## Endpoint baru

Semua membutuhkan Authorization: Bearer TOKEN. Respons konten dokumen/riwayat memakai Cache-Control: no-store.

| Method dan path | Body/parameter | Hasil |
|---|---|---|
| POST /api/documents/{id}/search | {"query":"Berapa biaya pendaftaran?"} | 200, daftar chunk relevan beserta score dan metadata. |
| POST /api/conversations | {"documentId":"UUID_DOKUMEN"} | 201, percakapan baru dan Location. |
| GET /api/conversations | page=0&size=20 | Daftar percakapan sendiri. |
| GET /api/conversations/{id} | Tidak ada body | Detail percakapan sendiri. |
| GET /api/conversations/{id}/messages | page=0&size=20 | Riwayat berurutan dari turn awal. |
| POST /api/conversations/{id}/messages | {"requestId":"UUID_REQUEST","question":"Berapa biayanya?"} | 201 hasil baru; 200 replay hasil berhasil. |
| DELETE /api/conversations/{id} | Tidak ada body | 204; dokumen tetap ada. |

page dimulai dari 0, maksimal 100000. size 1–100. query dan question maksimal 1000 karakter Java dan tidak boleh kosong. Field JSON tidak dikenal ditolak. requestId dibuat client sekali untuk satu pertanyaan, misalnya crypto.randomUUID() di frontend nanti.

RequestId sama pada percakapan sama dan pertanyaan sama mengembalikan turn tersimpan tanpa panggilan AI tambahan. Pertanyaan berbeda menghasilkan 409 REQUEST_ID_REUSED. Jika request pertama gagal sebelum turn tersimpan, retry masih dapat memanggil provider dan memakai kuota tambahan.

Contoh PowerShell setelah memiliki token dan documentId READY:

~~~powershell
$headers = @{ Authorization = "Bearer $token" }
$body = @{ documentId = $documentId } | ConvertTo-Json
$conversation = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/conversations -Headers $headers -ContentType application/json -Body $body
$requestId = [guid]::NewGuid().ToString()
$body = @{ requestId = $requestId; question = 'Berapa biaya pendaftaran?' } | ConvertTo-Json
$answer = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/conversations/$($conversation.id)/messages" -Headers $headers -ContentType application/json -Body $body
$answer.answer
$answer.sources
~~~

Sumber berisi documentId, filename, chunkId, chunkIndex, pageNumber, quote. chunkIndex dimulai dari 0; halaman PDF dari 1. DOCX/TXT memakai pageNumber null. Riwayat menyimpan sumber yang sudah divalidasi.

## Jawaban dan fallback

Model hanya boleh menghasilkan maksimal tiga kutipan, dengan ID sumber berbeda dan panjang masing-masing 12–500 karakter Java. Backend menambahkan [1], [2], [3] sesuai urutan sumber. Nama file atau nomor halaman yang dikarang model tidak digunakan.

Jika konteks tidak ditemukan atau model menyatakan informasi tidak tersedia:

> Informasi tersebut tidak ditemukan dalam dokumen ini.

Respons memakai found=false dan sources=[]. Jika keluaran model rusak, kutipan tidak ada di chunk, atau ID sumber tidak valid, backend mengembalikan 502 AI_RESPONSE_INVALID. Pasangan pesan gagal tidak disimpan sebagai jawaban berhasil.

## Pengaturan environment

Ubah .env, kemudian jalankan docker compose up -d --build --wait. Jangan menyalin API key ke frontend, kode sumber, atau percakapan.

| Variable | Default | Fungsi |
|---|---|---|
| CHAT_ENABLED | false | Aktifkan chat/pencarian. Membutuhkan EMBEDDING_ENABLED=true dan GEMINI_API_KEY. |
| GEMINI_CHAT_MODEL | gemini-3.5-flash-lite | Model chat. |
| CHAT_TIMEOUT_MS | 30000 | Timeout client chat. |
| CHAT_MAX_OUTPUT_TOKENS | 1024 | Batas output respons Gemini. |
| CHAT_TOP_K | 5 | Maksimal hasil vector, rentang 1–8. |
| CHAT_MIN_SIMILARITY | 0.45 | Ambang kemiripan 0–1. |
| CHAT_MAX_CONTEXT_CHARACTERS | 6000 | Batas gabungan teks chunk. |
| CHAT_MAX_TURNS | 50 | Pasangan pesan per percakapan. |
| CHAT_MAX_CONVERSATIONS | 100 | Percakapan per pengguna. |
| CHAT_DAILY_REQUESTS | 500 | Request chat aplikasi per hari UTC. |
| CHAT_USER_DAILY_REQUESTS | 50 | Request chat pengguna per hari UTC. |
| CHAT_DAILY_CHARACTERS | 2000000 | Karakter input chat aplikasi per hari UTC. |
| CHAT_USER_DAILY_CHARACTERS | 200000 | Karakter input chat pengguna per hari UTC. |

Kuota chat menghitung panjang string Java (UTF-16) system prompt + JSON payload, bukan token tagihan. Kuota embedding menghitung Unicode code point. Model output dibatasi terpisah. Kuota ini bukan pengganti pembatasan biaya provider.

Pencarian memakai kuota embedding yang sama dengan indexing. Fallback tanpa chunk tidak memakai request chat. SDK Gemini dan RetryTemplate Spring AI masing-masing memakai satu percobaan; tidak ada retry otomatis berlapis.

Reservasi kuota menggunakan REQUIRES_NEW: tetap tercatat bila API gagal atau transaksi jawaban dibatalkan. Jika kuota pengguna menolak, reservasi aplikasi untuk operasi itu ikut dibatalkan secara atomik.

CHAT_ENABLED=false mencegah pencarian, percakapan baru, dan generasi jawaban baru. Daftar, baca, hapus, dan replay jawaban tersimpan tetap tersedia bagi pemilik.

## Transaksi dan keterbatasan

Owner selalu berasal dari JWT dan dipakai pada filter vector, percakapan, dan pesan. ADMIN tidak memperoleh akses isi dokumen pengguna lain.

Dokumen dikunci sebelum percakapan saat menjawab, agar penghapusan tidak berjalan di tengah penyimpanan turn. Lock bertahan selama panggilan AI; request pada dokumen sama dapat menunggu. Dua operasi AI bersamaan diperbolehkan per instance. Ini cocok untuk satu backend skala awal, bukan throughput besar. Batas concurrency perlu dirancang ulang ketika backend direplikasi.

Instruksi dalam dokumen tidak dipercaya. Prompt meminta model memperlakukannya sebagai data. Validator mengizinkan kutipan hanya dari konteks terpilih. Proteksi ini mengurangi risiko tetapi tidak menjamin pemilihan kutipan selalu relevan. Jangan merender jawaban sebagai HTML mentah pada frontend.

Belum termasuk frontend, streaming, OCR, pencarian lintas dokumen, ringkasan otomatis, refresh token, reset password, admin dashboard, atau rate limit login. Tahap berikutnya menunggu tinjauan pengguna.
