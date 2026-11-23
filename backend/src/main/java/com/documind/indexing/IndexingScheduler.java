package com.documind.indexing;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name={"app.indexing.enabled","app.indexing.scheduler-enabled"},havingValue="true")
public class IndexingScheduler {
    private final IndexingService service;
    public IndexingScheduler(IndexingService service) { this.service=service; }
    @Scheduled(fixedDelayString="${app.indexing.poll-delay-ms:1000}",initialDelayString="${app.indexing.poll-delay-ms:1000}")
    public void poll() {
        try { service.processNext(); }
        catch (RuntimeException failure) {
            LoggerFactory.getLogger(IndexingScheduler.class).warn(
                    "Indexing tertunda; transaksi rollback. Jenis kegagalan={}",failure.getClass().getSimpleName());
        }
    }
}
