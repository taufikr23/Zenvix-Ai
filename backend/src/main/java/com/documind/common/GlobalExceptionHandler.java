package com.documind.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(
                ApiError.of(exception.status().value(), exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(
                error -> fields.putIfAbsent(error.getField(), messageFor(error.getField())));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400,
                "VALIDATION_ERROR", "Periksa kembali data yang dikirim.", fields));
    }

    private String messageFor(String field) {
        return switch (field) {
            case "email" -> "Email wajib valid dan maksimal 254 karakter.";
            case "password" -> "Password wajib diisi dan mengikuti batas panjang endpoint.";
            case "question" -> "Pesan wajib diisi dan maksimal 100.000 karakter.";
            default -> "Nilai tidak valid.";
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable() {
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_JSON",
                "JSON tidak valid atau memiliki field yang tidak didukung."));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge() {
        return ResponseEntity.status(413).body(ApiError.of(413, "FILE_TOO_LARGE",
                "Ukuran file atau request melebihi batas upload."));
    }

    @ExceptionHandler({
            org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ApiError> handleMultipart() {
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_MULTIPART",
                "Kirim multipart/form-data dengan field file yang valid."));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleParameter() {
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_PARAMETER",
                "ID dokumen atau parameter halaman tidak valid."));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabase() {
        return ResponseEntity.status(503).body(ApiError.of(503, "DATA_UNAVAILABLE",
                "Data sementara tidak tersedia. Coba kembali."));
    }

}
