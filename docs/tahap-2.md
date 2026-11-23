# Tahap 2 — registrasi, login, dan JWT

> Arsip tahap 2. Fitur terkini dijelaskan pada tahap-3.md dan hasil-verifikasi-tahap-3.md.

Tahap ini menambahkan autentikasi pada fondasi tahap 1. Seluruh backend tetap Java 21.
Tidak ada frontend, upload, embedding, atau panggilan Gemini pada tahap ini.

## Endpoint yang tersedia

| Method dan path | Akses | Hasil normal |
|---|---|---|
| POST /api/auth/register | Publik | 201 dan profil akun baru |
| POST /api/auth/login | Publik | 200, access token, masa berlaku, dan profil |
| GET /api/auth/me | Authorization: Bearer TOKEN | 200 dan profil pemilik token |
| GET /actuator/health | Publik | 200 dan status UP |
| GET /actuator/health/readiness | Publik | 200 ketika database dan vector siap |

Register tidak langsung memberikan token. Pengguna mendaftar, lalu login.
Endpoint lain tetap ditutup. Tanpa autentikasi responsnya 401; pengguna dengan token
valid tetapi tanpa izin memperoleh 403.

### Request registrasi

```json
{"email":"belajar@example.test","password":"Belajar-Java21!"}
```

Respons 201 berbentuk:

```json
{
  "id":"UUID-akun",
  "email":"belajar@example.test",
  "role":"USER",
  "createdAt":"waktu-pembuatan-UTC"
}
```

ID dan waktu di atas hanya penanda, bukan data akun yang sudah dibuat.
Email dibersihkan dari spasi luar dan diubah ke huruf kecil. Password tidak dipangkas.
Password registrasi minimal 8 karakter dan maksimal 72 byte UTF-8. Huruf non-ASCII
dapat memakai lebih dari satu byte. Input yang melampaui batas ditolak, bukan dipotong.

Field role, enabled, id, atau field asing lainnya ditolak. Client tidak dapat meminta
akun ADMIN melalui registrasi. Email duplikat menghasilkan HTTP 409.

### Request login

Body memiliki dua field yang sama, email dan password. Respons 200:

```json
{
  "accessToken":"JWT-dari-backend",
  "tokenType":"Bearer",
  "expiresIn":900,
  "user":{
    "id":"UUID-akun",
    "email":"belajar@example.test",
    "role":"USER",
    "createdAt":"waktu-pembuatan-UTC"
  }
}
```

Password salah, email tidak ditemukan, dan akun nonaktif memberikan pesan sama:
"Email atau password salah." Respons autentikasi memakai Cache-Control: no-store.

### Request profil

Kirim header Authorization dengan nilai Bearer diikuti token. Backend menentukan
pemilik profil dari sub pada token yang telah diverifikasi. Parameter userId dari
client tidak digunakan untuk memilih pengguna.

HTTP 401 berarti token tidak ada/tidak valid atau akun sudah tidak aktif.
HTTP 403 berarti identitas dikenal tetapi endpoint tidak diizinkan.

## Alur kode

### Registrasi

1. SecurityConfig mengizinkan POST /api/auth/register.
2. Jackson membentuk RegisterRequest; constructor menormalisasi email.
3. Validasi memeriksa email dan panjang password.
4. AuthController memanggil AuthService.register.
5. AuthService memeriksa panjang byte dan membuat hash BCrypt.
6. UserRepository memasukkan email dan hash dengan role USER.
7. Constraint database memastikan email unik, termasuk saat dua request bersamaan.
8. UserResponse mengembalikan profil tanpa password atau hash.

### Login

1. LoginRequest menormalisasi email dan memvalidasi input.
2. AuthService.login mencari akun melalui UserRepository.
3. PasswordEncoder membandingkan password dengan hash BCrypt.
4. Untuk email yang tidak ditemukan, BCrypt tetap berjalan menggunakan dummy hash.
5. Password salah atau akun nonaktif menghasilkan respons generik 401.
6. TokenService membuat JWT bertanda tangan HS256 dengan masa berlaku 900 detik.
7. Controller mengembalikan TokenResponse tanpa membuat session cookie.

### Memakai token

1. Filter bawaan Spring Security membaca Authorization: Bearer.
2. JwtDecoder memeriksa tanda tangan, algoritma, waktu, issuer, dan audience.
3. AccountJwtConverter mengubah subject menjadi UUID dan membaca akun dari database.
4. Akun harus masih ada dan enabled=true. Role diambil dari database.
5. SecurityConfig menentukan apakah endpoint diizinkan.
6. AuthController.me mengambil UUID dari JWT, lalu AuthService.me mengembalikan profil.

Menonaktifkan atau menghapus akun memutus akses pada request berikutnya walaupun
token belum kedaluwarsa. Database tetap dibaca setiap request terautentikasi.

## Package dan class

Constructor pada class service/repository/controller menerima dependensi dari Spring.
Ini disebut dependency injection: class tidak membuat koneksi database atau objek
keamanan sendiri.

### Package user

| Class/method | Penjelasan |
|---|---|
| UserAccount | Record internal berisi id, email, passwordHash, role, enabled, createdAt |
| UserAccount.toString | Menghasilkan ringkasan tanpa email atau hash; passwordHash juga diberi JsonIgnore |
| UserRepository(JdbcTemplate) | Menerima alat JDBC untuk menjalankan SQL |
| create(email,passwordHash) | INSERT satu akun USER dan mengembalikan baris yang tersimpan |
| findByEmail(email) | Mencari akun tanpa membedakan kapital, mengembalikan Optional |
| findById(id) | Mencari akun berdasarkan UUID, mengembalikan Optional |
| mapRow(row,index) | Mengubah satu baris ResultSet menjadi UserAccount |

Semua nilai pengguna pada query memakai placeholder ?. Nilai tidak digabung ke SQL.
Optional berarti hasil dapat ada atau kosong. Repository tidak mengirim model
internal langsung sebagai respons HTTP.

### Package auth

| Class/method | Penjelasan |
|---|---|
| RegisterRequest | DTO email/password registrasi, dengan anotasi validasi |
| Constructor RegisterRequest | strip dan lower-case email, mempertahankan password apa adanya |
| LoginRequest | DTO email/password login dengan validasi input |
| Constructor LoginRequest | Normalisasi email sebelum pencarian |
| toString pada kedua request | Menyamarkan isi agar password tidak ikut saat objek dicetak |
| UserResponse.from(user) | Memilih hanya id, email, role, dan createdAt |
| TokenResponse | DTO accessToken, tokenType, expiresIn, dan user |
| TokenResponse.toString | Menyamarkan token saat objek dicetak |
| AuthService(users,passwords,tokens) | Menerima repository, encoder, dan penerbit token; membuat dummy hash sekali |
| register(request) | Memvalidasi byte password, hashing, INSERT; duplikasi diubah menjadi 409 |
| login(request) | Memeriksa password/status akun lalu meminta token |
| me(userId) | Memuat kembali profil akun aktif berdasarkan UUID terautentikasi |
| checkPasswordBytes(password) | Menolak password di atas 72 byte UTF-8 |
| AuthController(auth) | Menerima service autentikasi |
| register(request) | Endpoint POST register; menghasilkan 201 dan no-store |
| login(request) | Endpoint POST login; menghasilkan token dengan no-store |
| me(jwt) | Endpoint GET profil; UUID berasal dari subject JWT |

DTO berarti objek bentuk data masuk/keluar. Record Java menyediakan constructor,
akses field, equals, dan hashCode secara otomatis. Data rahasia tidak dimasukkan
ke UserResponse.

### Package security

| Class/method | Penjelasan |
|---|---|
| JwtProperties | Mengikat konfigurasi app.jwt: secret, issuer, audience, accessTokenSeconds |
| JwtProperties.toString | Menyamarkan kunci |
| JwtConfig.jwtKey(properties) | Decode Base64, wajib minimal 32 byte, lalu membentuk SecretKey HmacSHA256 |
| jwtEncoder(key) | Membuat NimbusJwtEncoder untuk menandatangani token |
| jwtDecoder(key,properties) | Membuat decoder HS256 serta validator claim |
| passwordEncoder() | Menyediakan BCryptPasswordEncoder dengan cost 12 |
| TokenService(encoder,properties) | Menerima encoder dan konfigurasi |
| issue(user) | Membuat claim, menandatangani token, dan membentuk TokenResponse |
| AccountJwtConverter(users) | Menerima repository untuk pemeriksaan akun terbaru |
| convert(jwt) | Memeriksa UUID/akun aktif dan membentuk JwtAuthenticationToken dengan role database |
| invalidAccount() | Membuat kegagalan autentikasi yang ditangani Spring sebagai 401 |
| SecurityErrorHandler(mapper) | Menerima ObjectMapper untuk menulis JSON |
| commence(request,response,exception) | Respons 401 generik beserta WWW-Authenticate: Bearer |
| handle(request,response,exception) | Respons 403 generik |
| write(response,status,code,message) | Menulis ApiError sebagai JSON UTF-8 |
| SecurityConfig.securityFilterChain(http,converter,errors) | Menentukan akses URL, stateless session, JWT, dan handler error |

Token memiliki claim iss (penerbit), aud (API tujuan), sub (UUID akun), iat (waktu
terbit), exp (kedaluwarsa), dan jti (ID token). Tidak ada password, hash, atau kunci
penandatanganan di dalam token. JWT bertanda tangan bukan enkripsi; payload dapat
dibaca pemegang token.

Decoder hanya menerima HS256 dan memeriksa issuer/audience yang dikonfigurasi.
Claim exp, iat, dan sub wajib ada. Validasi waktu memberi toleransi selisih jam
30 detik. Waktu berlaku default 15 menit, dapat diatur antara 60–3600 detik.

Role tidak disimpan sebagai sumber otorisasi dalam JWT; role terbaru dibaca dari
database. Konfigurasi ini juga menolak token akun yang sudah dihapus/nonaktif.

Form login, HTTP Basic, dan endpoint logout bawaan dinonaktifkan. CSRF dinonaktifkan
untuk API Bearer header ini karena autentikasi tidak memakai cookie/session otomatis.
Keputusan tersebut harus ditinjau ulang jika kelak autentikasi memakai cookie.

### Package common

| Class/method | Penjelasan |
|---|---|
| ApiException(status,code,message) | Membawa kesalahan bisnis dengan status HTTP dan pesan aman |
| status(), code() | Mengambil status dan kode error |
| ApiError | Bentuk JSON error: timestamp, status, code, message, fields |
| ApiError.of(status,code,message) | Membuat error tanpa rincian field |
| GlobalExceptionHandler.handleApi(exception) | Mengubah ApiException menjadi respons HTTP |
| handleValidation(exception) | Mengubah validasi input menjadi 400 tanpa mengembalikan nilai password |
| messageFor(field) | Memilih pesan validasi sederhana dalam bahasa Indonesia |
| handleUnreadable() | Mengembalikan 400 untuk JSON tidak terbaca atau field tidak didukung |

Error autentikasi filter ditangani SecurityErrorHandler. Error bisnis/DTO ditangani
GlobalExceptionHandler. Keduanya tidak menampilkan token, password, stack trace,
atau detail koneksi.

## Konfigurasi dan database

Tabel users dari V2 sudah mencukupi; tahap 2 tidak mengubah migrasi V1/V2 dan tidak
memerlukan penghapusan volume. Indeks lower(email) menangani duplikasi secara atomik.

| Environment | Fungsi | Default |
|---|---|---|
| JWT_SECRET | Secret acak Base64 minimal 32 byte | Wajib diisi |
| JWT_ISSUER | Penerbit token yang diterima | documind-ai |
| JWT_AUDIENCE | API tujuan token | documind-api |
| JWT_ACCESS_TOKEN_SECONDS | Lama berlaku access token | 900 |

Secret tidak boleh memakai prefix VITE_ atau dimasukkan ke frontend.
Mengganti JWT_SECRET membuat token lama tidak valid. Restart dengan secret yang sama
mempertahankan validitas token sampai masa berlakunya habis.

scripts/setup-env.ps1 sekarang dapat memperbarui konfigurasi tahap 1:
- New-RandomSecret(Base64): membuat 32 byte acak; memilih keluaran heksadesimal/Base64.
- Set-SecretIfMissing(Name,Base64): mengisi secret yang belum ada/kosong.
- Nilai DB_PASSWORD dan JWT_SECRET yang sudah terisi dipertahankan.
- Pemanggilan ulang tidak merotasi rahasia secara diam-diam.
- File .env dan nilainya tidak disertakan dalam ZIP.

Jackson disetel fail-on-unknown-properties agar field role atau field asing ditolak.
Dependensi baru: starter-validation untuk Bean Validation dan
starter-oauth2-resource-server untuk verifikasi Bearer JWT/Nimbus.

## Cara mencoba dengan PowerShell

Backend harus sudah healthy. Contoh berikut benar-benar membuat akun di database lokal;
ganti email jika contoh sudah pernah digunakan.

```powershell
$base = 'http://localhost:8080'
$body = @{
    email = 'belajar@example.test'
    password = 'Belajar-Java21!'
} | ConvertTo-Json

Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $body

$session = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body
$headers = @{ Authorization = "Bearer $($session.accessToken)" }
Invoke-RestMethod "$base/api/auth/me" -Headers $headers
```

Jangan membagikan nilai accessToken. Token dicatat di variabel session pada contoh,
bukan dicetak. Tidak ada akun default atau kredensial bawaan aplikasi.

## Pengujian otomatis

Maven verify menjalankan 5 unit test dan 17 integration test pada PostgreSQL sungguhan.

Unit test:
- Tiga tes VectorHealthIndicator memeriksa UP, kegagalan tanpa bocoran, dan hasil kosong.
- JwtConfigTest.refusesWeakSigningKey menolak kunci terlalu pendek.
- malformedSecretDoesNotAppearInErrorMessage menolak Base64 tidak valid tanpa menampilkan secret.

Integration test tahap 1 tetap mencakup migrasi/vector, health, endpoint tertutup,
dan email unik. Ekspektasi endpoint anonim kini 401 sesuai adanya autentikasi.

Tambahan tahap 2:
- registrationStoresHashAndNormalizesEmail: hashing, normalisasi, role USER, respons aman.
- duplicateRegistrationIgnoresEmailCase: duplikasi menghasilkan 409.
- simultaneousRegistrationsOnlyCreateOneAccount: dua request bersamaan tetap hanya satu akun.
- registrationRejectsInvalidFieldsAndAdminInjection: data salah dan role ADMIN ditolak.
- registrationRejectsPasswordsExceedingUtf8Limit: batas ASCII/UTF-8.
- loginReturnsShortLivedTokenAndOwnProfileWithoutSession: token, profil, no-store, tanpa session cookie.
- invalidLoginsUseTheSameGenericMessage: salah password, email asing, akun nonaktif.
- profileRequiresBearerHeader: cookie, query token, dan Basic tidak menggantikan Bearer header.
- tamperedUnsignedAndMalformedTokensAreRejected: token dimodifikasi, unsigned, dan rusak.
- invalidJwtClaimsAreRejected: kedaluwarsa, issuer/audience salah, exp hilang, nbf masa depan, subject salah.
- disablingOrDeletingAnAccountImmediatelyInvalidatesAccess: status akun langsung berlaku.
- clientCannotSelectAnotherUsersProfile: parameter client tidak mengganti identitas.
- authenticatedUserStillCannotUseUnimplementedEndpoints: token valid tidak membuka semua endpoint.

Helper test email(), post(), register(), login(), get(), dan signedToken() menyiapkan
data/request/token khusus pengujian. cleanAuthFixtures() membersihkan akun auth-it
setelah setiap tes pada database Testcontainers. Secret pengujian hanya berada di
anotasi test; tidak digunakan aplikasi Docker.

## Batas tahap ini

Belum ada refresh token, endpoint logout yang mencabut satu token, reset password,
verifikasi email, atau pembatasan percobaan login. Menghapus token di client tidak
mencabut salinan token yang masih valid. Akun nonaktif/dihapus langsung ditolak;
rotasi signing key membatalkan semua token.

Layanan masih terikat localhost. Sebelum dibuka ke publik, lengkapi HTTPS dan
pembatasan percobaan autentikasi. Fitur admin serta kepemilikan dokumen dikerjakan
pada tahap masing-masing. Frontend belum tersedia.

## Referensi resmi

- https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html
