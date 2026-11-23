package com.documind.document;

import java.util.List;

public record DocumentPage(List<DocumentResponse> items, int page, int size, long totalElements) {}
