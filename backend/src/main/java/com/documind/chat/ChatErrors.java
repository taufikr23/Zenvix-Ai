package com.documind.chat;
import com.documind.common.ApiException;
import org.springframework.http.HttpStatus;
final class ChatErrors {
    private ChatErrors() {}
    static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Dokumen atau percakapan tidak ditemukan."); }
    static ApiException invalidAnswer() { return new ApiException(HttpStatus.BAD_GATEWAY,"AI_RESPONSE_INVALID","Format jawaban AI tidak valid. Coba kembali."); }
    static void page(int page,int size) {
        if(page<0||page>100000||size<1||size>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","page harus 0–100000 dan size 1–100.");
    }
}
