package com.documind.document;

import com.documind.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileValidator {
    private static final long MAX_ZIP_BYTES = 50L * 1024 * 1024;
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final long maxFileBytes;

    public FileValidator(MultipartProperties properties) {
        this.maxFileBytes = properties.getMaxFileSize().toBytes();
        if (maxFileBytes <= 0 || maxFileBytes > 50L * 1024 * 1024) {
            throw new IllegalStateException("MAX_UPLOAD_SIZE harus lebih dari 0 dan maksimal 50MB.");
        }
    }

    public ValidatedFile validate(MultipartFile file) {
        String filename = validateName(file.getOriginalFilename());
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("pdf", "docx", "txt").contains(extension)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE",
                    "Format yang didukung: PDF, DOCX, dan TXT.");
        }
        if (file.isEmpty()) { throw invalid("File tidak boleh kosong."); }
        if (file.getSize() > maxFileBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Ukuran file melebihi batas upload.");
        }
        try (var stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes((int) maxFileBytes + 1);
            if (bytes.length == 0) { throw invalid("File tidak boleh kosong."); }
            if (bytes.length > maxFileBytes) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                        "Ukuran file melebihi batas upload.");
            }
            String mediaType = switch (extension) {
                case "pdf" -> { validatePdf(bytes); yield "application/pdf"; }
                case "docx" -> { validateDocx(bytes); yield DOCX_MIME; }
                default -> { validateText(bytes); yield "text/plain"; }
            };
            return new ValidatedFile(filename, mediaType, extension.toUpperCase(Locale.ROOT),
                    bytes, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("Isi file rusak, tidak sesuai format, atau tidak dapat dibaca.");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 tidak tersedia.", exception);
        }
    }

    private String validateName(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255 ||
                filename.contains("/") || filename.contains("\\") || filename.contains(":") ||
                filename.codePoints().anyMatch(Character::isISOControl) ||
                filename.lastIndexOf('.') <= 0 || filename.endsWith(".")) {
            throw invalid("Nama file tidak valid.");
        }
        return filename.strip();
    }

    private void validatePdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw invalid("File bukan PDF yang valid.");
        }
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted()) { throw invalid("PDF terenkripsi belum didukung."); }
            if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 2000) {
                throw invalid("PDF harus memiliki 1 sampai 2000 halaman.");
            }
        }
    }

    private void validateDocx(byte[] bytes) throws IOException {
        // Hitung ukuran hasil dekompresi, bukan hanya ukuran ZIP yang diklaim.
        var names = new HashSet<String>();
        long total = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (!names.add(name) || names.size() > 2000 || name.startsWith("/") ||
                        name.contains("..") || name.contains("\\") ||
                        name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) {
                    throw invalid("Struktur DOCX tidak didukung.");
                }
                int length;
                while ((length = zip.read(buffer)) != -1) {
                    total += length;
                    if (total > MAX_ZIP_BYTES) { throw invalid("Isi DOCX setelah dekompresi terlalu besar."); }
                }
            }
        }
        if (!names.contains("[Content_Types].xml") || !names.contains("word/document.xml")) {
            throw invalid("File bukan DOCX yang valid.");
        }
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            if (!document.getPackagePart().getContentType().equals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")) {
                throw invalid("Hanya DOCX tanpa macro yang didukung.");
            }
        } catch (org.apache.poi.ooxml.POIXMLException exception) {
            throw invalid("Struktur DOCX tidak dapat dibaca.");
        }
    }

    private void validateText(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (text.replace("\uFEFF", "").isBlank() ||
                text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw invalid("TXT harus berisi teks UTF-8 yang dapat dibaca.");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message);
    }
}
