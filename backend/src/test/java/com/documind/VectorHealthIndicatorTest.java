package com.documind;

import com.documind.config.VectorHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorHealthIndicatorTest {
    @Test
    void workingVectorQueryReportsUp() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(0.0);
        assertThat(new VectorHealthIndicator(jdbc).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void databaseFailureDoesNotLeakConnectionDetails() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Double.class)))
                .thenThrow(new IllegalStateException("jdbc:postgresql://private-host password=secret"));
        var health = new VectorHealthIndicator(jdbc).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }

    @Test
    void missingVectorResultReportsDown() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(null);
        assertThat(new VectorHealthIndicator(jdbc).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
