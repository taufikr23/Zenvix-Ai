package com.documind.auth;

import com.documind.user.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String role, Instant createdAt) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.id(), user.email(), user.role(), user.createdAt());
    }
}
