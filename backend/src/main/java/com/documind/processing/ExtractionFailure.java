package com.documind.processing;
/** Kode aman untuk client; tidak menyertakan isi dokumen atau path filesystem. */
public class ExtractionFailure extends RuntimeException {
    private final String code;
    public ExtractionFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
