package com.documind.document;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Hanya UUID server yang menjadi nama fisik; nama dari browser tidak dipakai sebagai path. */
@Component
public class FileStorage {
    private final Path root;

    public FileStorage(StorageProperties properties) throws IOException {
        Path configured = Path.of(properties.root()).toAbsolutePath().normalize();
        Files.createDirectories(configured);
        this.root = configured.toRealPath();
    }

    public void save(UUID id, byte[] bytes) throws IOException {
        Path target = pathFor(id);
        boolean created = false;
        try {
            try (var stream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                stream.write(bytes);
            }
        } catch (IOException exception) {
            if (created) {
                try { Files.deleteIfExists(target); }
                catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            }
            throw exception;
        }
    }

    public byte[] read(UUID id) throws IOException {
        try (var stream = Files.newInputStream(pathFor(id), LinkOption.NOFOLLOW_LINKS)) {
            return stream.readAllBytes();
        }
    }

    public void delete(UUID id) throws IOException {
        Files.deleteIfExists(pathFor(id));
    }

    private Path pathFor(UUID id) {
        Path target = root.resolve(id.toString() + ".bin").normalize();
        if (!target.getParent().equals(root)) { throw new IllegalArgumentException("Lokasi file tidak valid."); }
        return target;
    }
}
