package com.documind.indexing;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class EmbeddingBudget {
    private final JdbcTemplate jdbc;
    private final IndexingProperties properties;
    private final TransactionTemplate transaction;
    public EmbeddingBudget(JdbcTemplate jdbc, IndexingProperties properties,
                           org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.properties = properties;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Reservasi tetap dihitung jika API gagal atau transaksi penyimpanan vector rollback. */
    public void reserve(UUID owner, int characters) {
        if (characters < 1) { throw new IndexingFailure("INVALID_EMBEDDING_INPUT"); }
        transaction.executeWithoutResult(status -> {
            var day = LocalDate.now(ZoneOffset.UTC);
            take(day, "application", characters, properties.dailyCharacters());
            take(day, "owner:" + owner, characters, properties.userDailyCharacters());
        });
    }

    private void take(LocalDate day, String scope, int characters, long limit) {
        if (characters > limit) { throw new IndexingFailure("EMBEDDING_DAILY_LIMIT"); }
        int updated = jdbc.update("""
                INSERT INTO embedding_daily_usage(usage_day,scope,reserved_characters) VALUES (?,?,?)
                ON CONFLICT(usage_day,scope) DO UPDATE
                SET reserved_characters=embedding_daily_usage.reserved_characters+EXCLUDED.reserved_characters
                WHERE embedding_daily_usage.reserved_characters <= ?-EXCLUDED.reserved_characters
                """, day, scope, characters, limit);
        if (updated != 1) { throw new IndexingFailure("EMBEDDING_DAILY_LIMIT"); }
    }
}
