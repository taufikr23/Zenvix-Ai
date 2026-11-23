package com.documind.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Semua parameter pengguna dikirim melalui placeholder SQL, bukan penggabungan string. */
@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UserAccount create(String email, String passwordHash) {
        return jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,role) VALUES (?,?,'USER')
                RETURNING id,email,password_hash,role,enabled,created_at
                """, this::mapRow, email, passwordHash);
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE lower(email) = lower(?)",
                this::mapRow, email).stream().findFirst();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", this::mapRow, id)
                .stream().findFirst();
    }

    private UserAccount mapRow(ResultSet row, int index) throws SQLException {
        return new UserAccount(row.getObject("id", UUID.class), row.getString("email"),
                row.getString("password_hash"), row.getString("role"),
                row.getBoolean("enabled"), row.getTimestamp("created_at").toInstant());
    }
}
