package com.documind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Titik masuk backend: memuat konfigurasi, koneksi database, dan server HTTP. */
@SpringBootApplication
public class DocumindApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumindApplication.class, args);
    }
}
