package com.documind.chat;
import com.documind.common.ApiException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
@Component
public class ChatConcurrency {
    // Dua request RAG + satu worker masih menyisakan koneksi untuk transaksi kuota.
    private final Semaphore permits=new Semaphore(2);
    public <T> T execute(Supplier<T> action) {
        if(!permits.tryAcquire())throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"CHAT_BUSY","Sistem sedang memproses pertanyaan lain. Coba kembali.");
        try{return action.get();}finally{permits.release();}
    }
}
