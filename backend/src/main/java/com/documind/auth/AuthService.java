package com.documind.auth;

import com.documind.common.ApiException;
import com.documind.security.TokenService;
import com.documind.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        checkPasswordBytes(request.password());
        String hash = passwords.encode(request.password());
        try {
            return UserResponse.from(users.create(request.email(), hash));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email sudah terdaftar.");
        }
    }

    public TokenResponse login(LoginRequest request) {
        checkPasswordBytes(request.password());
        var user = users.findByEmail(request.email());
        // Tetap lakukan BCrypt untuk email yang tidak ditemukan.
        boolean matches = passwords.matches(request.password(),
                user.map(account -> account.passwordHash()).orElse(dummyHash));
        if (!matches || user.isEmpty() || !user.get().enabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Email atau password salah.");
        }
        return tokens.issue(user.get());
    }

    public UserResponse me(UUID userId) {
        return users.findById(userId).filter(user -> user.enabled()).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED", "Token atau akun tidak valid."));
    }

    private void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
                    "Password maksimal 72 byte UTF-8.");
        }
    }
}
