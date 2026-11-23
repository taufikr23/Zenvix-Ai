package com.documind;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pengujian memakai PostgreSQL + pgvector sungguhan, bukan database pengganti. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "app.processing.enabled=false",
                "app.indexing.enabled=true", "app.indexing.api-key=test-only-key",
                "app.indexing.scheduler-enabled=false", "app.chat.enabled=true"})
class InfrastructureIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;

    @Test
    void migrationsAndVectorOperationsWork() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6','7','8','9')",
                Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT '[1,0]'::vector <-> '[0,1]'::vector", Double.class))
                .isCloseTo(Math.sqrt(2), org.assertj.core.data.Offset.offset(0.00001));
    }

    @Test
    void healthIsPublicWithoutDatabaseDetails() {
        var result = http.getForEntity("/actuator/health", String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).contains("\"status\":\"UP\"")
                .doesNotContain("jdbc", "password", "components");
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void otherEndpointsAreClosed() {
        assertThat(http.getForEntity("/api/documents", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emailUniquenessIgnoresLetterCase() {
        String email = "check-" + java.util.UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)", email, "test-only-hash");
        try {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO users(email,password_hash) VALUES (?,?)",
                    email.toUpperCase(java.util.Locale.ROOT), "test-only-hash"))
                    .isInstanceOf(DuplicateKeyException.class);
        } finally {
            jdbc.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;

    private static final String PASSWORD = "Belajar-Java21!";

    @org.junit.jupiter.api.AfterEach
    void cleanAuthFixtures() throws java.io.IOException {
        jdbc.update("DELETE FROM embedding_daily_usage");
        jdbc.update("DELETE FROM chat_daily_usage");
        try (var files = java.nio.file.Files.list(uploadRoot)) {
            for (var file : files.toList()) { java.nio.file.Files.deleteIfExists(file); }
        }
        jdbc.update("DELETE FROM documents WHERE owner_id IN (SELECT id FROM users WHERE email LIKE 'auth-it-%')");
        jdbc.update("DELETE FROM users WHERE email LIKE 'auth-it-%'");
    }

    private String email() {
        return "auth-it-" + java.util.UUID.randomUUID() + "@example.test";
    }

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> post(
            String route, Object body) {
        return http.postForEntity(route, body, com.fasterxml.jackson.databind.JsonNode.class);
    }

    private com.fasterxml.jackson.databind.JsonNode register(String email) {
        var response = post("/api/auth/register", java.util.Map.of("email", email, "password", PASSWORD));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String login(String email) {
        var response = post("/api/auth/login", java.util.Map.of("email", email, "password", PASSWORD));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("accessToken").asText();
    }

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> get(
            String route, String token) {
        var headers = new org.springframework.http.HttpHeaders();
        if (token != null) { headers.setBearerAuth(token); }
        return http.exchange(route, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                com.fasterxml.jackson.databind.JsonNode.class);
    }

    private String signedToken(String subject, String issuer, String audience,
                               java.time.Instant expires, java.time.Instant notBefore) {
        var builder = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .subject(subject).issuer(issuer).audience(java.util.List.of(audience))
                .issuedAt(java.time.Instant.now().minusSeconds(600));
        if (expires != null) { builder.expiresAt(expires); }
        if (notBefore != null) { builder.notBefore(notBefore); }
        return encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader
                        .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),
                builder.build())).getTokenValue();
    }

    @Test
    void registrationStoresHashAndNormalizesEmail() {
        String email = email();
        var account = register("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ");
        assertThat(account.get("email").asText()).isEqualTo(email);
        assertThat(account.get("role").asText()).isEqualTo("USER");
        assertThat(account.toString()).doesNotContain("password", "hash", PASSWORD);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email);
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2a$12$");
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void duplicateRegistrationIgnoresEmailCase() {
        String email = email();
        register(email);
        var duplicate = post("/api/auth/register",
                java.util.Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", PASSWORD));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code").asText()).isEqualTo("EMAIL_EXISTS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void simultaneousRegistrationsOnlyCreateOneAccount() {
        String email = email();
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                post("/api/auth/register", java.util.Map.of("email", email, "password", PASSWORD)).getStatusCode());
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                post("/api/auth/register", java.util.Map.of("email", email, "password", PASSWORD)).getStatusCode());
        assertThat(java.util.List.of(first.join(), second.join()))
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void registrationRejectsInvalidFieldsAndAdminInjection() {
        for (var body : java.util.List.of(
                java.util.Map.of("email", "bukan-email", "password", PASSWORD),
                java.util.Map.of("email", email(), "password", "short"),
                java.util.Map.of("email", email(), "password", PASSWORD, "role", "ADMIN"))) {
            var result = post("/api/auth/register", body);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody().toString()).doesNotContain(PASSWORD, "password_hash");
        }
    }

    @Test
    void registrationRejectsPasswordsExceedingUtf8Limit() {
        for (String password : java.util.List.of("a".repeat(73), "é".repeat(40))) {
            var result = post("/api/auth/register", java.util.Map.of("email", email(), "password", password));
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody().toString()).doesNotContain(password);
        }
    }

    @Test
    void loginReturnsShortLivedTokenAndOwnProfileWithoutSession() {
        String email = email();
        var account = register(email);
        var response = post("/api/auth/login",
                java.util.Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", PASSWORD));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.getBody().get("expiresIn").asLong()).isEqualTo(900);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
        var profile = get("/api/auth/me", response.getBody().get("accessToken").asText());
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().get("id").asText()).isEqualTo(account.get("id").asText());
        assertThat(profile.getBody().toString()).doesNotContain("password", "hash", PASSWORD);
    }

    @Test
    void invalidLoginsUseTheSameGenericMessage() {
        String email = email();
        register(email);
        var wrong = post("/api/auth/login", java.util.Map.of("email", email, "password", "WrongPassword!"));
        var unknown = post("/api/auth/login", java.util.Map.of("email", email(), "password", PASSWORD));
        jdbc.update("UPDATE users SET enabled=false WHERE email=?", email);
        var disabled = post("/api/auth/login", java.util.Map.of("email", email, "password", PASSWORD));
        for (var result : java.util.List.of(wrong, unknown, disabled)) {
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(result.getBody().get("message").asText()).isEqualTo("Email atau password salah.");
            assertThat(result.getBody().toString()).doesNotContain(PASSWORD);
        }
    }

    @Test
    void profileRequiresBearerHeader() {
        String email = email();
        register(email);
        String token = login(email);
        assertThat(get("/api/auth/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/auth/me?access_token=" + token, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var headers = new org.springframework.http.HttpHeaders();
        headers.add("Cookie", "access_token=" + token);
        assertThat(http.exchange("/api/auth/me", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        headers.clear();
        headers.setBasicAuth(email, PASSWORD);
        assertThat(http.exchange("/api/auth/me", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tamperedUnsignedAndMalformedTokensAreRejected() {
        String email = email();
        register(email);
        String token = login(email);
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        String unsigned = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + parts[1] + ".";
        for (String invalid : java.util.List.of(String.join(".", parts), unsigned, "bukan-jwt")) {
            var response = get("/api/auth/me", invalid);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().get("code").asText()).isEqualTo("UNAUTHORIZED");
            assertThat(response.getBody().toString()).doesNotContain(invalid);
        }
    }

    @Test
    void invalidJwtClaimsAreRejected() {
        String id = register(email()).get("id").asText();
        var now = java.time.Instant.now();
        var tokens = java.util.List.of(
                signedToken(id, "documind-ai", "documind-api", now.minusSeconds(120), null),
                signedToken(id, "wrong-issuer", "documind-api", now.plusSeconds(900), null),
                signedToken(id, "documind-ai", "wrong-audience", now.plusSeconds(900), null),
                signedToken(id, "documind-ai", "documind-api", null, null),
                signedToken(id, "documind-ai", "documind-api", now.plusSeconds(900), now.plusSeconds(120)),
                signedToken("not-a-uuid", "documind-ai", "documind-api", now.plusSeconds(900), null),
                signedToken(java.util.UUID.randomUUID().toString(), "documind-ai", "documind-api", now.plusSeconds(900), null));
        for (String token : tokens) {
            assertThat(get("/api/auth/me", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void disablingOrDeletingAnAccountImmediatelyInvalidatesAccess() {
        String email = email();
        register(email);
        String token = login(email);
        jdbc.update("UPDATE users SET enabled=false WHERE email=?", email);
        assertThat(get("/api/auth/me", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        jdbc.update("DELETE FROM users WHERE email=?", email);
        assertThat(get("/api/auth/me", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clientCannotSelectAnotherUsersProfile() {
        String aliceEmail = email();
        var alice = register(aliceEmail);
        var bob = register(email());
        var response = get("/api/auth/me?userId=" + bob.get("id").asText(), login(aliceEmail));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id").asText()).isEqualTo(alice.get("id").asText());
        assertThat(response.getBody().get("id").asText()).isNotEqualTo(bob.get("id").asText());
    }

    @Test
    void authenticatedUserStillCannotUseUnimplementedEndpoints() {
        String email = email();
        register(email);
        var response = get("/api/admin/users", login(email));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code").asText()).isEqualTo("FORBIDDEN");
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.documind.document.FileStorage fileStorage;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.documind.document.DocumentRepository documentRepository;

    static final java.nio.file.Path uploadRoot = createUploadRoot();

    private static java.nio.file.Path createUploadRoot() {
        try { return java.nio.file.Files.createTempDirectory("documind-it-"); }
        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void storageProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> uploadRoot.toString());
        registry.add("app.storage.max-documents-per-user", () -> 3);
    }

    @org.junit.jupiter.api.AfterAll
    static void removeUploadRoot() throws java.io.IOException {
        java.nio.file.Files.deleteIfExists(uploadRoot);
    }

    private org.springframework.http.HttpHeaders bearer(String token) {
        var headers = new org.springframework.http.HttpHeaders();
        if (token != null) { headers.setBearerAuth(token); }
        return headers;
    }

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> upload(
            String token, String filename, byte[] bytes) {
        var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        body.add("ownerId", "client-cannot-select-owner");
        var headers = bearer(token);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/documents", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body, headers), com.fasterxml.jackson.databind.JsonNode.class);
    }

    private org.springframework.http.ResponseEntity<byte[]> download(String id, String token) {
        return http.exchange("/api/documents/" + id + "/download", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(bearer(token)), byte[].class);
    }

    private org.springframework.http.HttpStatusCode deleteDocument(String id, String token) {
        return http.exchange("/api/documents/" + id, org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(token)), String.class).getStatusCode();
    }

    private String newToken() {
        String email = email();
        register(email);
        return login(email);
    }

    @Test
    void allThreeFormatsRoundTripWithPrivateMetadata() throws Exception {
        var files = new java.util.LinkedHashMap<String, byte[]>();
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            pdf.save(out);
            files.put("contoh.pdf", out.toByteArray());
        }
        try (var docx = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            docx.createParagraph().createRun().setText("Belajar dokumen Java.");
            docx.write(out);
            files.put("contoh.docx", out.toByteArray());
        }
        files.put("catatan.txt", "Catatan UTF-8: café.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = newToken();
        for (var file : files.entrySet()) {
            var response = upload(token, file.getKey(), file.getValue());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            var metadata = response.getBody();
            String id = metadata.get("id").asText();
            assertThat(metadata.get("status").asText()).isEqualTo("QUEUED");
            assertThat(metadata.get("filename").asText()).isEqualTo(file.getKey());
            assertThat(metadata.get("sizeBytes").asInt()).isEqualTo(file.getValue().length);
            assertThat(metadata.toString()).doesNotContain("ownerId", "sha256", "storage", ".bin", "password");
            assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/documents/" + id);
            assertThat(get("/api/documents/" + id, token).getStatusCode()).isEqualTo(HttpStatus.OK);
            var downloaded = download(id, token);
            assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(downloaded.getBody()).isEqualTo(file.getValue());
            assertThat(downloaded.getHeaders().getFirst("Content-Disposition")).startsWith("attachment;");
            assertThat(downloaded.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(downloaded.getHeaders().getCacheControl()).contains("no-store");
            assertThat(downloaded.getHeaders().getContentType().toString()).isEqualTo(metadata.get("mediaType").asText());
        }
        var page = get("/api/documents?page=0&size=2", token).getBody();
        assertThat(page.get("items").size()).isEqualTo(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
        assertThat(get("/api/documents?page=1&size=2", token).getBody().get("items").size()).isEqualTo(1);
    }

    @Test
    void usersAndAdminsCannotReadDownloadOrDeleteSomeoneElsesDocuments() {
        String aliceEmail = email(), bobEmail = email();
        var alice = register(aliceEmail);
        register(bobEmail);
        String aliceToken = login(aliceEmail), bobToken = login(bobEmail);
        String id = upload(aliceToken, "privat.txt", "Rahasia Alice".getBytes()).getBody().get("id").asText();
        assertThat(jdbc.queryForObject("SELECT owner_id::text FROM documents WHERE id=?::uuid",
                String.class, id)).isEqualTo(alice.get("id").asText());
        for (String role : java.util.List.of("USER", "ADMIN")) {
            jdbc.update("UPDATE users SET role=? WHERE email=?", role, bobEmail);
            assertThat(get("/api/documents?ownerId=" + alice.get("id").asText(), bobToken)
                    .getBody().get("items").size()).isZero();
            assertThat(get("/api/documents/" + id, bobToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(download(id, bobToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(deleteDocument(id, bobToken)).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(download(id, aliceToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void documentOperationsRequireAuthentication() {
        String token = newToken();
        String id = upload(token, "aman.txt", "Isi".getBytes()).getBody().get("id").asText();
        assertThat(upload(null, "baru.txt", "Isi".getBytes()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/documents/" + id, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(download(id, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deleteDocument(id, null)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidFilesAndParametersAreRejectedWithoutSaving() {
        String token = newToken();
        assertThat(upload(token, "program.exe", new byte[]{1,2}).getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        var invalid = java.util.Map.of(
                "palsu.pdf", "bukan pdf".getBytes(),
                "palsu.docx", new byte[]{80,75,3,4},
                "kosong.txt", new byte[0],
                "biner.txt", new byte[]{65,0,66},
                "encoding.txt", new byte[]{(byte) 0xC3,0x28},
                "../keluar.txt", "path tidak aman".getBytes(),
                "spasi.txt", " \n\t".getBytes());
        invalid.forEach((name, bytes) ->
                assertThat(upload(token, name, bytes).getStatusCode()).as(name).isEqualTo(HttpStatus.BAD_REQUEST));
        for (String route : java.util.List.of("/api/documents/bukan-uuid", "/api/documents?page=-1",
                "/api/documents?size=101", "/api/documents?page=abc")) {
            assertThat(get(route, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var headers = bearer(token);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        var missing = http.exchange("/api/documents", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new org.springframework.util.LinkedMultiValueMap<>(), headers),
                String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/documents", token).getBody().get("totalElements").asInt()).isZero();
    }

    @Test
    void oversizedFileReturns413WithoutSaving() {
        String token = newToken();
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        java.util.Arrays.fill(bytes, (byte) 'a');
        var result = upload(token, "besar.txt", bytes);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(result.getBody().get("code").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(get("/api/documents", token).getBody().get("totalElements").asInt()).isZero();
    }

    @Test
    void concurrentUploadsRespectQuotaAndDuplicateNamesRemainSeparate() {
        String token = newToken();
        var first = upload(token, "sama.txt", "Pertama".getBytes());
        String firstId = first.getBody().get("id").asText();
        upload(token, "sama.txt", "Kedua".getBytes());
        var one = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                upload(token, "sama.txt", "Ketiga".getBytes()).getStatusCode());
        var two = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                upload(token, "sama.txt", "Keempat".getBytes()).getStatusCode());
        assertThat(java.util.List.of(one.join(), two.join()))
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(get("/api/documents", token).getBody().get("totalElements").asInt()).isEqualTo(3);
        assertThat(download(firstId, token).getBody()).isEqualTo("Pertama".getBytes());
    }

    @Test
    void deletionRemovesMetadataAndFileAndHandlesAlreadyMissingFile() throws Exception {
        String token = newToken();
        String id = upload(token, "hapus.txt", "Hapus".getBytes()).getBody().get("id").asText();
        assertThat(java.nio.file.Files.exists(uploadRoot.resolve(id + ".bin"))).isTrue();
        assertThat(deleteDocument(id, token)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(java.nio.file.Files.exists(uploadRoot.resolve(id + ".bin"))).isFalse();
        assertThat(get("/api/documents/" + id, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(id, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteDocument(id, token)).isEqualTo(HttpStatus.NOT_FOUND);
        String missingId = upload(token, "hilang.txt", "Hilang".getBytes()).getBody().get("id").asText();
        java.nio.file.Files.delete(uploadRoot.resolve(missingId + ".bin"));
        assertThat(download(missingId, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteDocument(missingId, token)).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void failedDeletionCanBeRetriedAndIsNotDownloadable() throws Exception {
        String token = newToken();
        String id = upload(token, "retry.txt", "Isi".getBytes()).getBody().get("id").asText();
        var uuid = java.util.UUID.fromString(id);
        org.mockito.Mockito.doThrow(new java.io.IOException("simulasi disk"))
                .doCallRealMethod().when(fileStorage).delete(uuid);
        assertThat(deleteDocument(id, token)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(get("/api/documents/" + id, token).getBody().get("status").asText()).isEqualTo("DELETING");
        assertThat(download(id, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteDocument(id, token)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(java.nio.file.Files.exists(uploadRoot.resolve(id + ".bin"))).isFalse();
    }

    @Test
    void failedMetadataInsertRollsBackAndRemovesUploadedFile() throws Exception {
        String token = newToken();
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("simulasi DB"))
                .when(documentRepository).create(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var result = upload(token, "gagal.txt", "Isi".getBytes());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getBody().toString()).doesNotContain("simulasi", "SQL", uploadRoot.toString());
        assertThat(get("/api/documents", token).getBody().get("totalElements").asInt()).isZero();
        try (var files = java.nio.file.Files.list(uploadRoot)) { assertThat(files.count()).isZero(); }
    }

    @Autowired com.documind.processing.DocumentProcessor processor;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.documind.processing.ChunkRepository chunkRepository;

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> retryProcessing(
            String id, String token) {
        return http.exchange("/api/documents/" + id + "/process", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(bearer(token)), com.fasterxml.jackson.databind.JsonNode.class);
    }

    @Test
    void queuedTextIsProcessedAndChunksArePrivateAndDeletedWithDocument() {
        String token = newToken(), other = newToken();
        String text = "Materi pengujian chunk dan kepemilikan. ".repeat(80).strip();
        var upload = upload(token, "materi.txt", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String id = upload.getBody().get("id").asText();
        assertThat(get("/api/documents/" + id + "/chunks", token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(processor.processNext()).isTrue();
        var metadata = get("/api/documents/" + id, token).getBody();
        assertThat(metadata.get("status").asText()).isEqualTo("EXTRACTED");
        assertThat(metadata.get("chunkCount").asInt()).isGreaterThan(1);
        assertThat(metadata.get("extractedCharacters").asInt()).isEqualTo(text.codePointCount(0, text.length()));
        assertThat(metadata.get("sourcePages").isNull()).isTrue();
        var page = get("/api/documents/" + id + "/chunks?size=1", token);
        assertThat(page.getHeaders().getCacheControl()).contains("no-store");
        assertThat(page.getBody().get("items").size()).isEqualTo(1);
        assertThat(page.getBody().get("items").get(0).get("content").asText()).isEqualTo(text.substring(0,1000));
        assertThat(page.getBody().get("items").get(0).get("pageNumber").isNull()).isTrue();
        assertThat(get("/api/documents/" + id + "/chunks", other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(retryProcessing(id, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/documents/" + id + "/chunks", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(retryProcessing(id, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(retryProcessing(id, token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get("/api/documents/" + id + "/chunks?size=101", token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(deleteDocument(id, token)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?::uuid",
                Integer.class, id)).isZero();
        assertThat(processor.processNext()).isFalse();
    }

    @Test
    void missingFileFailsSafelyAndCanBeRetriedAfterRestore() throws Exception {
        String token = newToken();
        byte[] bytes = "File dapat diproses kembali.".getBytes();
        String id = upload(token, "retry.txt", bytes).getBody().get("id").asText();
        java.nio.file.Files.delete(uploadRoot.resolve(id + ".bin"));
        assertThat(processor.processNext()).isTrue();
        var failed = get("/api/documents/" + id, token).getBody();
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("processingError").asText()).isEqualTo("FILE_UNAVAILABLE");
        assertThat(failed.toString()).doesNotContain(uploadRoot.toString());
        java.nio.file.Files.write(uploadRoot.resolve(id + ".bin"), bytes);
        assertThat(retryProcessing(id, token).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(retryProcessing(id, token).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(processor.processNext()).isTrue();
        var done = get("/api/documents/" + id, token).getBody();
        assertThat(done.get("status").asText()).isEqualTo("EXTRACTED");
        assertThat(done.get("processingError").isNull()).isTrue();
        assertThat(done.get("chunkCount").asInt()).isEqualTo(1);
    }

    @Test
    void blankPdfHasExplicitNoTextFailure() throws Exception {
        byte[] bytes;
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            pdf.save(out); bytes = out.toByteArray();
        }
        String token = newToken();
        String id = upload(token, "scan.pdf", bytes).getBody().get("id").asText();
        assertThat(processor.processNext()).isTrue();
        var metadata = get("/api/documents/" + id, token).getBody();
        assertThat(metadata.get("status").asText()).isEqualTo("FAILED");
        assertThat(metadata.get("processingError").asText()).isEqualTo("NO_EXTRACTABLE_TEXT");
        assertThat(metadata.get("chunkCount").asInt()).isZero();
    }

    @Test
    void changedFileIsRejectedBeforeExtraction() throws Exception {
        String token = newToken();
        String id = upload(token, "asli.txt", "abc".getBytes()).getBody().get("id").asText();
        java.nio.file.Files.writeString(uploadRoot.resolve(id + ".bin"), "xyz");
        assertThat(processor.processNext()).isTrue();
        assertThat(get("/api/documents/" + id, token).getBody().get("processingError").asText())
                .isEqualTo("FILE_INTEGRITY_ERROR");
    }

    @Test
    void twoWorkersDoNotDuplicateChunks() {
        String token = newToken();
        String id = upload(token, "parallel.txt", "Hanya sekali.".getBytes()).getBody().get("id").asText();
        var one = java.util.concurrent.CompletableFuture.supplyAsync(() -> processor.processNext());
        var two = java.util.concurrent.CompletableFuture.supplyAsync(() -> processor.processNext());
        assertThat(java.util.List.of(one.join(), two.join())).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?::uuid",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void databaseFailureRollsBackQueueAndChunksForNextAttempt() {
        String token = newToken();
        String id = upload(token, "rollback.txt", "Transaksi harus atomik.".getBytes()).getBody().get("id").asText();
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new org.springframework.dao.DataIntegrityViolationException("simulasi setelah INSERT");
        }).doCallRealMethod().when(chunkRepository).replace(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
        assertThatThrownBy(() -> processor.processNext())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(get("/api/documents/" + id, token).getBody().get("status").asText()).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?::uuid",
                Integer.class, id)).isZero();
        assertThat(processor.processNext()).isTrue();
        assertThat(get("/api/documents/" + id, token).getBody().get("status").asText()).isEqualTo("EXTRACTED");
    }

    @Autowired com.documind.indexing.IndexingService indexing;
    @Autowired com.documind.indexing.EmbeddingBudget embeddingBudget;
    @Autowired com.documind.indexing.IndexingProperties indexingProperties;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.ai.embedding.EmbeddingModel embeddingModel;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.documind.indexing.IndexRepository indexRepository;

    private float[] vector() { float[] value=new float[768]; value[0]=3;value[1]=4;return value; }

    private String extractedDocument(String token,String text) {
        String id=upload(token,"embedding.txt",text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .getBody().get("id").asText();
        assertThat(processor.processNext()).isTrue();
        return id;
    }

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> retryIndex(String id,String token) {
        return http.exchange("/api/documents/"+id+"/index",org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(bearer(token)),com.fasterxml.jackson.databind.JsonNode.class);
    }

    @Test
    void indexingStoresNormalizedVectorsAndDeletesThemWithDocument() {
        String token=newToken(),other=newToken();
        String id=extractedDocument(token,"a".repeat(1100));
        org.mockito.Mockito.when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(vector());
        assertThat(indexing.processNext()).isTrue();
        var partial=get("/api/documents/"+id,token).getBody();
        assertThat(partial.get("status").asText()).isEqualTo("EXTRACTED");
        assertThat(partial.get("indexedChunkCount").asInt()).isEqualTo(1);
        assertThat(indexing.processNext()).isTrue();
        var ready=get("/api/documents/"+id,token).getBody();
        assertThat(ready.get("status").asText()).isEqualTo("READY");
        assertThat(ready.get("indexedChunkCount").asInt()).isEqualTo(2);
        assertThat(ready.get("indexedAt").isNull()).isFalse();
        assertThat(jdbc.queryForObject("SELECT min(vector_dims(embedding)) FROM document_chunks WHERE document_id=?::uuid",
                Integer.class,id)).isEqualTo(768);
        assertThat(jdbc.queryForObject("SELECT max(abs(vector_norm(embedding)-1)) FROM document_chunks WHERE document_id=?::uuid",
                Double.class,id)).isLessThan(0.00001);
        assertThat(jdbc.queryForObject("SELECT max(embedding <=> embedding) FROM document_chunks WHERE document_id=?::uuid",
                Double.class,id)).isCloseTo(0,org.assertj.core.data.Offset.offset(0.00001));
        var chunks=get("/api/documents/"+id+"/chunks",token);
        assertThat(chunks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chunks.getBody().toString()).doesNotContain("embedding_model","embedding","apiKey");
        assertThat(retryIndex(id,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(retryIndex(id,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(retryIndex(id,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(indexing.processNext()).isFalse();
        org.mockito.Mockito.verify(embeddingModel,org.mockito.Mockito.times(2)).embed(org.mockito.ArgumentMatchers.anyString());
        assertThat(deleteDocument(id,token)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?::uuid",
                Integer.class,id)).isZero();
    }

    @Test
    void providerFailureKeepsCompletedChunksAndRetryOnlyEmbedsTheRemainder() {
        String token=newToken(),id=extractedDocument(token,"b".repeat(1100));
        org.mockito.Mockito.when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(vector()).thenThrow(new IllegalStateException("provider-secret-body"))
                .thenReturn(vector());
        assertThat(indexing.processNext()).isTrue();
        assertThat(indexing.processNext()).isTrue();
        var failed=get("/api/documents/"+id,token).getBody();
        assertThat(failed.get("status").asText()).isEqualTo("INDEX_FAILED");
        assertThat(failed.get("indexedChunkCount").asInt()).isEqualTo(1);
        assertThat(failed.get("indexingError").asText()).isEqualTo("EMBEDDING_PROVIDER_ERROR");
        assertThat(failed.toString()).doesNotContain("provider-secret-body");
        assertThat(get("/api/documents/"+id+"/chunks",token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(indexing.processNext()).isFalse();
        assertThat(retryIndex(id,token).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(indexing.processNext()).isTrue();
        assertThat(get("/api/documents/"+id,token).getBody().get("status").asText()).isEqualTo("READY");
        org.mockito.Mockito.verify(embeddingModel,org.mockito.Mockito.times(3)).embed(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void invalidVectorNeverBecomesReady() {
        String token=newToken(),id=extractedDocument(token,"coba");
        org.mockito.Mockito.when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(new float[5]);
        assertThat(indexing.processNext()).isTrue();
        var failed=get("/api/documents/"+id,token).getBody();
        assertThat(failed.get("indexingError").asText()).isEqualTo("INVALID_EMBEDDING");
        assertThat(failed.get("status").asText()).isEqualTo("INDEX_FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?::uuid AND embedding IS NOT NULL",
                Integer.class,id)).isZero();
    }

    @Test
    void dailyLimitBlocksApiAndRollsBackGlobalReservation() {
        String email=email();
        var account=register(email);
        String token=login(email),id=extractedDocument(token,"kuota");
        jdbc.update("INSERT INTO embedding_daily_usage VALUES (?,?,?)",
                java.time.LocalDate.now(java.time.ZoneOffset.UTC),"owner:"+account.get("id").asText(),
                indexingProperties.userDailyCharacters());
        assertThat(indexing.processNext()).isTrue();
        assertThat(get("/api/documents/"+id,token).getBody().get("indexingError").asText())
                .isEqualTo("EMBEDDING_DAILY_LIMIT");
        org.mockito.Mockito.verifyNoInteractions(embeddingModel);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM embedding_daily_usage WHERE scope='application'",
                Integer.class)).isZero();
    }

    @Test
    void vectorSaveRollbackRetainsBudgetAndCanBeRetried() {
        String token=newToken(),id=extractedDocument(token,"rollback");
        org.mockito.Mockito.when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(vector());
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new org.springframework.dao.DataIntegrityViolationException("simulasi sesudah simpan vector");
        }).doCallRealMethod().when(indexRepository).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> indexing.processNext()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(get("/api/documents/"+id,token).getBody().get("status").asText()).isEqualTo("EXTRACTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?::uuid AND embedding IS NOT NULL",
                Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_characters FROM embedding_daily_usage WHERE scope='application'",
                Long.class)).isEqualTo(8);
        assertThat(indexing.processNext()).isTrue();
        assertThat(jdbc.queryForObject("SELECT reserved_characters FROM embedding_daily_usage WHERE scope='application'",
                Long.class)).isEqualTo(16);
        assertThat(get("/api/documents/"+id,token).getBody().get("status").asText()).isEqualTo("READY");
    }

    @Test
    void concurrentIndexWorkersCallProviderOnlyOnce() {
        String token=newToken();
        extractedDocument(token,"sekali");
        org.mockito.Mockito.when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(vector());
        var one=java.util.concurrent.CompletableFuture.supplyAsync(() -> indexing.processNext());
        var two=java.util.concurrent.CompletableFuture.supplyAsync(() -> indexing.processNext());
        assertThat(java.util.List.of(one.join(),two.join())).containsExactlyInAnyOrder(true,false);
        org.mockito.Mockito.verify(embeddingModel,org.mockito.Mockito.times(1)).embed(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void quotaReservationIsAtomicAcrossConcurrentCalls() {
        var owner=java.util.UUID.randomUUID();
        int characters=10;
        jdbc.update("INSERT INTO embedding_daily_usage VALUES (?,?,?)",
                java.time.LocalDate.now(java.time.ZoneOffset.UTC),"owner:"+owner,
                indexingProperties.userDailyCharacters()-characters);
        java.util.function.Supplier<Boolean> reserve=() -> {
            try { embeddingBudget.reserve(owner,characters);return true; }
            catch(com.documind.indexing.IndexingFailure failure) { return false; }
        };
        var one=java.util.concurrent.CompletableFuture.supplyAsync(reserve);
        var two=java.util.concurrent.CompletableFuture.supplyAsync(reserve);
        assertThat(java.util.List.of(one.join(),two.join())).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("SELECT reserved_characters FROM embedding_daily_usage WHERE scope='application'",
                Long.class)).isEqualTo(characters);
    }


    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.ai.chat.model.ChatModel chatModel;
    @Autowired com.documind.chat.ChatProperties chatProperties;
    private static final String FACT="Biaya pendaftaran seminar adalah Rp150.000 per peserta.";
    private String readyDocument(String token,String content) {
        String id=extractedDocument(token,content);
        org.mockito.Mockito.when(embeddingModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(vector());
        while(indexing.processNext()) {}
        org.mockito.Mockito.when(embeddingModel.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.embedding.EmbeddingRequest.class)))
                .thenReturn(new org.springframework.ai.embedding.EmbeddingResponse(java.util.List.of(new org.springframework.ai.embedding.Embedding(vector(),0))));
        return id;
    }
    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> authPost(String route,String token,Object body) {
        return http.exchange(route,org.springframework.http.HttpMethod.POST,new org.springframework.http.HttpEntity<>(body,bearer(token)),
                com.fasterxml.jackson.databind.JsonNode.class);
    }
    private String conversation(String token,String document) {
        var response=authPost("/api/conversations",token,java.util.Map.of("documentId",document));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }
    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> ask(String token,String id,String request,String question) {
        return authPost("/api/conversations/"+id+"/messages",token,java.util.Map.of("requestId",request,"question",question));
    }
    private void answerWith(String raw) {
        org.mockito.Mockito.when(chatModel.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(
                        new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage(raw)))));
    }
    private void answerFact() { answerWith("{\"found\":true,\"passages\":[{\"sourceId\":\"S1\",\"quote\":\""+FACT+"\"}]}"); }
    @Test
    void generalConversationWorksWithoutDocumentsAndKeepsAssistantHistory() {
        String token=newToken();
        var created=authPost("/api/conversations",token,java.util.Map.of());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("documentId").isNull()).isTrue();
        String id=created.getBody().get("id").asText(),request=java.util.UUID.randomUUID().toString();
        answerWith("{\"answer\":\"def hello(): return 'hello'\"}");
        var first=ask(token,id,request,"Buat fungsi Python");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("sources").isEmpty()).isTrue();
        assertThat(ask(token,id,request,"Buat fungsi Python").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ask(token,id,request,"Pertanyaan berbeda").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        answerWith("{\"answer\":\"Fungsi itu mengembalikan hello.\"}");
        assertThat(ask(token,id,java.util.UUID.randomUUID().toString(),"Jelaskan kode tadi").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var capture=org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        org.mockito.Mockito.verify(chatModel,org.mockito.Mockito.times(2)).call(capture.capture());
        assertThat(capture.getValue().getInstructions().get(2).getText()).isEqualTo("def hello(): return 'hello'");
        assertThat(get("/api/conversations/"+id,token).getBody().get("title").asText()).isEqualTo("Buat fungsi Python");
        assertThat(get("/api/conversations",token).getBody().get("items").get(0).get("id").asText()).isEqualTo(id);
        assertThat(get("/api/conversations/"+id+"/messages",token).getBody().get("totalElements").asInt()).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(embeddingModel);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM embedding_daily_usage",Integer.class)).isZero();
    }
    @Test
    void generalConversationIsPrivateAndDeleteCascades() {
        String token=newToken(),other=newToken();
        String id=authPost("/api/conversations",token,java.util.Map.of()).getBody().get("id").asText();
        assertThat(get("/api/conversations/"+id,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/conversations/"+id+"/messages",other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/conversations",other).getBody().get("totalElements").asInt()).isZero();
        assertThat(ask(other,id,java.util.UUID.randomUUID().toString(),"Hello").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/conversations/"+id,org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(other)),String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        answerWith("{\"answer\":\"Hello\"}");
        ask(token,id,java.util.UUID.randomUUID().toString(),"Halo");
        assertThat(http.exchange("/api/conversations/"+id,org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(token)),String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_turns WHERE conversation_id=?::uuid",Integer.class,id)).isZero();
    }
    @Test
    void failedGeneralAnswerIsNotSavedAndLongCodeInputIsAccepted() {
        String token=newToken();
        String id=authPost("/api/conversations",token,java.util.Map.of()).getBody().get("id").asText();
        answerWith("{}");
        assertThat(ask(token,id,java.util.UUID.randomUUID().toString(),"Halo").getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(get("/api/conversations/"+id+"/messages",token).getBody().get("totalElements").asInt()).isZero();
        answerWith("{\"answer\":\"Kode diterima.\"}");
        assertThat(ask(token,id,java.util.UUID.randomUUID().toString(),"x".repeat(100000)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ask(token,id,java.util.UUID.randomUUID().toString(),"x".repeat(100001)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test
    void chatPersistsServerSourcesAndIdempotentlyReplays() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc),request=java.util.UUID.randomUUID().toString();
        jdbc.update("UPDATE document_chunks SET page_number=3 WHERE document_id=?::uuid",doc);
        answerFact();
        var first=ask(token,conv,request,"Berapa biaya pendaftaran?");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("found").asBoolean()).isTrue();
        var source=first.getBody().get("sources").get(0);
        assertThat(source.get("documentId").asText()).isEqualTo(doc);
        assertThat(source.get("pageNumber").asInt()).isEqualTo(3);
        assertThat(source.get("filename").asText()).isEqualTo("embedding.txt");
        assertThat(source.get("quote").asText()).isEqualTo(FACT);
        var replay=ask(token,conv,request,"Berapa biaya pendaftaran?");
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo(first.getBody());
        assertThat(ask(token,conv,request,"Pertanyaan berbeda").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get("/api/conversations/"+conv+"/messages",token).getBody().get("totalElements").asInt()).isEqualTo(1);
        org.mockito.Mockito.verify(chatModel,org.mockito.Mockito.times(1)).call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        var capture=org.mockito.ArgumentCaptor.forClass(org.springframework.ai.embedding.EmbeddingRequest.class);
        org.mockito.Mockito.verify(embeddingModel).call(capture.capture());
        assertThat(((org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions)capture.getValue().getOptions()).getTaskType().name()).isEqualTo("RETRIEVAL_QUERY");
    }
    @Test
    void adminAndOtherOwnersCannotReadSearchOrMutateConversation() {
        String token=newToken(),other=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?::uuid",get("/api/auth/me",other).getBody().get("id").asText());
        for(String route:java.util.List.of("/api/conversations/"+conv,"/api/conversations/"+conv+"/messages"))
            assertThat(get(route,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/conversations",other).getBody().get("totalElements").asInt()).isZero();
        assertThat(authPost("/api/documents/"+doc+"/search",other,java.util.Map.of("query","biaya")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(authPost("/api/conversations",other,java.util.Map.of("documentId",doc)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ask(other,conv,java.util.UUID.randomUUID().toString(),"biaya").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/conversations/"+conv,org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(other)),String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ask(null,conv,java.util.UUID.randomUUID().toString(),"biaya").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        org.mockito.Mockito.verifyNoInteractions(chatModel);
        org.mockito.Mockito.verify(embeddingModel,org.mockito.Mockito.never()).call(org.mockito.ArgumentMatchers.any(org.springframework.ai.embedding.EmbeddingRequest.class));
    }
    @Test
    void retrievalIsRestrictedToSelectedReadyDocument() {
        String token=newToken(),doc=readyDocument(token,FACT);
        readyDocument(token,"Dokumen lain berisi informasi rahasia perusahaan.");
        var result=authPost("/api/documents/"+doc+"/search",token,java.util.Map.of("query","biaya"));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().size()).isEqualTo(1);
        assertThat(result.getBody().get(0).get("documentId").asText()).isEqualTo(doc);
        assertThat(result.getBody().toString()).doesNotContain("rahasia perusahaan","embedding_model");
        String queued=upload(token,"antri.txt",FACT.getBytes()).getBody().get("id").asText();
        assertThat(authPost("/api/conversations",token,java.util.Map.of("documentId",queued)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(authPost("/api/documents/"+queued+"/search",token,java.util.Map.of("query","biaya")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(authPost("/api/documents/"+doc+"/search",token,java.util.Map.of("query"," ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test
    void noRelevantVectorReturnsFallbackWithoutChatCall() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc);
        float[] orthogonal=new float[768];orthogonal[2]=1;
        org.mockito.Mockito.when(embeddingModel.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.embedding.EmbeddingRequest.class)))
                .thenReturn(new org.springframework.ai.embedding.EmbeddingResponse(java.util.List.of(new org.springframework.ai.embedding.Embedding(orthogonal,0))));
        var result=ask(token,conv,java.util.UUID.randomUUID().toString(),"Pertanyaan tidak terkait?");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().get("found").asBoolean()).isFalse();
        assertThat(result.getBody().get("sources").isEmpty()).isTrue();
        assertThat(result.getBody().get("answer").asText()).isEqualTo(com.documind.chat.GroundedAnswerValidator.NOT_FOUND);
        org.mockito.Mockito.verifyNoInteractions(chatModel);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_daily_usage",Integer.class)).isZero();
    }
    @Test
    void invalidAnswerAndProviderFailureNeverPersistAndKeepReservedUsage() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc);
        answerWith("{\"found\":true,\"passages\":[{\"sourceId\":\"S1\",\"quote\":\"Jawaban rekaan yang tidak ada di sumber.\"}]}");
        assertThat(ask(token,conv,java.util.UUID.randomUUID().toString(),"biaya").getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        org.mockito.Mockito.when(chatModel.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new IllegalStateException("secret-provider-content"));
        var error=ask(token,conv,java.util.UUID.randomUUID().toString(),"biaya");
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getBody().toString()).doesNotContain("secret-provider-content");
        assertThat(get("/api/conversations/"+conv+"/messages",token).getBody().get("totalElements").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT requests FROM chat_daily_usage WHERE scope='application'",Long.class)).isEqualTo(2);
    }
    @Test
    void modelCanDeclineAndFollowupUsesOnlyPreviousQuestions() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc);
        answerFact();
        ask(token,conv,java.util.UUID.randomUUID().toString(),"Berapa biaya pendaftaran?");
        answerWith("{\"found\":false,\"passages\":[]}");
        var response=ask(token,conv,java.util.UUID.randomUUID().toString(),"Siapa pembicaranya?");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("found").asBoolean()).isFalse();
        var capture=org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        org.mockito.Mockito.verify(chatModel,org.mockito.Mockito.times(2)).call(capture.capture());
        assertThat(capture.getValue().getUserMessage().getText()).contains("Berapa biaya pendaftaran?","previousQuestions").doesNotContain("Berdasarkan dokumen:");
    }
    @Test
    void quotaBlocksProviderBeforeCallingAndRollsBackGlobalReservation() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc);
        String owner=get("/api/auth/me",token).getBody().get("id").asText();
        jdbc.update("INSERT INTO chat_daily_usage VALUES (?,?,?,?)",java.time.LocalDate.now(java.time.ZoneOffset.UTC),
                "owner:"+owner,chatProperties.userDailyRequests(),0);
        var response=ask(token,conv,java.util.UUID.randomUUID().toString(),"biaya");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        org.mockito.Mockito.verifyNoInteractions(chatModel);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_daily_usage WHERE scope='application'",Integer.class)).isZero();
    }
    @Test
    void concurrentIdenticalRequestsOnlyGenerateOnceAndDocumentDeleteCascades() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc),request=java.util.UUID.randomUUID().toString();
        answerFact();
        var one=java.util.concurrent.CompletableFuture.supplyAsync(()->ask(token,conv,request,"biaya").getStatusCode());
        var two=java.util.concurrent.CompletableFuture.supplyAsync(()->ask(token,conv,request,"biaya").getStatusCode());
        assertThat(java.util.List.of(one.join(),two.join())).containsExactlyInAnyOrder(HttpStatus.CREATED,HttpStatus.OK);
        org.mockito.Mockito.verify(chatModel,org.mockito.Mockito.times(1)).call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        assertThat(deleteDocument(doc,token)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/conversations/"+conv,token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_turns WHERE conversation_id=?::uuid",Integer.class,conv)).isZero();
    }
    @Test
    void deletingConversationRemovesMessagesButKeepsDocument() {
        String token=newToken(),doc=readyDocument(token,FACT),conv=conversation(token,doc);
        answerFact();ask(token,conv,java.util.UUID.randomUUID().toString(),"biaya");
        assertThat(http.exchange("/api/conversations/"+conv,org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(token)),String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/documents/"+doc,token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_turns WHERE conversation_id=?::uuid",Integer.class,conv)).isZero();
    }

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> askAttached(String token,String conv,String request,String text){
        var body=new org.springframework.util.LinkedMultiValueMap<String,Object>();
        var messageHeaders=new org.springframework.http.HttpHeaders();messageHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        body.add("message",new org.springframework.http.HttpEntity<>(java.util.Map.of("requestId",request,"question","Ringkas file ini"),messageHeaders));
        body.add("files",new org.springframework.core.io.ByteArrayResource(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)){
            @Override public String getFilename(){return "catatan.txt";}
        });
        var headers=bearer(token);headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/conversations/"+conv+"/messages",org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body,headers),com.fasterxml.jackson.databind.JsonNode.class);
    }
    @Test void chatAttachmentsArePrivateIdempotentAndDeletedWithTheirConversation(){
        String token=newToken(),other=newToken();
        String conv=authPost("/api/conversations",token,java.util.Map.of()).getBody().get("id").asText();
        String request=java.util.UUID.randomUUID().toString();
        answerWith("{\"answer\":\"Rapat pukul sepuluh.\"}");
        assertThat(askAttached(other,conv,request,"Rapat pukul 10").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var result=askAttached(token,conv,request,"Rapat pukul 10");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String attachment=result.getBody().get("attachments").get(0).get("id").asText();
        String route="/api/conversations/"+conv+"/attachments/"+attachment;
        assertThat(get(route,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var download=http.exchange(route,org.springframework.http.HttpMethod.GET,new org.springframework.http.HttpEntity<>(bearer(token)),byte[].class);
        assertThat(download.getBody()).isEqualTo("Rapat pukul 10".getBytes());
        assertThat(download.getHeaders().getCacheControl()).contains("no-store");
        assertThat(askAttached(token,conv,request,"Rapat pukul 10").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(askAttached(token,conv,request,"Isi berbeda").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        org.mockito.Mockito.verify(chatModel,org.mockito.Mockito.times(1)).call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        assertThat(get("/api/conversations/"+conv+"/messages",token).getBody().get("items").get(0).get("attachments")).hasSize(1);
        assertThat(http.exchange("/api/conversations/"+conv,org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(bearer(token)),String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(route,token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_attachments WHERE id=?::uuid",Integer.class,attachment)).isZero();
    }

}
