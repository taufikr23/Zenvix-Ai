package com.documind.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/** Model internal database; hash tidak boleh masuk JSON respons. */
public record UserAccount(UUID id, String email, @JsonIgnore String passwordHash,
                          String role, boolean enabled, Instant createdAt) {
    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", role=" + role + ", enabled=" + enabled + "]";
    }
}
