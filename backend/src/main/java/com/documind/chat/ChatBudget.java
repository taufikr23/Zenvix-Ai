package com.documind.chat;
import com.documind.common.ApiException;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class ChatBudget {
    private final JdbcTemplate jdbc;private final ChatProperties properties;private final TransactionTemplate transaction;
    public ChatBudget(JdbcTemplate jdbc,ChatProperties properties,org.springframework.transaction.PlatformTransactionManager manager){
        this.jdbc=jdbc;this.properties=properties;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void reserve(UUID owner,int characters) {
        transaction.executeWithoutResult(status->{
            var day=LocalDate.now(ZoneOffset.UTC);
            take(day,"application",characters,properties.dailyRequests(),properties.dailyCharacters());
            take(day,"owner:"+owner,characters,properties.userDailyRequests(),properties.userDailyCharacters());
        });
    }
    private void take(LocalDate day,String scope,int characters,long requests,long maxCharacters) {
        if(characters<1||characters>maxCharacters)throw limit();
        int updated=jdbc.update("""
                INSERT INTO chat_daily_usage VALUES (?,?,1,?)
                ON CONFLICT(usage_day,scope) DO UPDATE SET requests=chat_daily_usage.requests+1,
                    input_characters=chat_daily_usage.input_characters+EXCLUDED.input_characters
                WHERE chat_daily_usage.requests<? AND chat_daily_usage.input_characters<=?-EXCLUDED.input_characters
                """,day,scope,characters,requests,maxCharacters);
        if(updated!=1)throw limit();
    }
    private ApiException limit(){return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"CHAT_DAILY_LIMIT","Batas penggunaan chat harian tercapai.");}
}
