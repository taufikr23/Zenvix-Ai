package com.documind.indexing;
public class IndexingFailure extends RuntimeException {
    private final String code;
    public IndexingFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
