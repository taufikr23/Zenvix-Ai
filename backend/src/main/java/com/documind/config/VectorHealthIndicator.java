package com.documind.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Memastikan ekstensi vector tersedia dan dapat melakukan operasi vektor. */
@Component("vector")
public class VectorHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;

    public VectorHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Double distance = jdbcTemplate.queryForObject(
                    "SELECT '[1,0]'::vector <-> '[1,0]'::vector", Double.class);
            return distance != null && distance == 0.0
                    ? Health.up().build()
                    : Health.down().build();
        } catch (Exception exception) {
            // Jangan memasukkan URL, password, atau pesan database ke respons publik.
            return Health.down().build();
        }
    }
}
