package com.documind.document;

/** Hasil validasi internal; bytes tidak pernah dikembalikan sebagai metadata. */
public record ValidatedFile(String filename, String mediaType, String fileType, byte[] bytes, String sha256) {
    @Override public String toString() { return "ValidatedFile[type=" + fileType + ", size=" + bytes.length + "]"; }
}
