package com.documind.processing;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.processing.enabled", havingValue = "true", matchIfMissing = true)
public class ProcessingScheduler {
    private final DocumentProcessor processor;
    public ProcessingScheduler(DocumentProcessor processor) { this.processor = processor; }
    @Scheduled(fixedDelayString = "${app.processing.poll-delay-ms:1000}", initialDelayString = "${app.processing.poll-delay-ms:1000}")
    public void poll() {
        try { processor.processNext(); }
        catch (RuntimeException exception) {
            // Antrean tetap ada karena rollback. Jangan mencatat teks, SQL, token, atau path.
            LoggerFactory.getLogger(ProcessingScheduler.class).warn(
                    "Pemrosesan tertunda; transaksi di-rollback. Jenis kegagalan={}", exception.getClass().getSimpleName());
        }
    }
}
