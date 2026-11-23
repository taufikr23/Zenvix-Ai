package com.documind;

import com.documind.security.JwtConfig;
import com.documind.security.JwtProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {
    @Test
    void refusesWeakSigningKey() {
        assertThatThrownBy(() -> new JwtConfig().jwtKey(
                new JwtProperties("c2hvcnQ=", "documind-ai", "documind-api", 900)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET harus berisi minimal 32 byte acak.");
    }

    @Test
    void malformedSecretDoesNotAppearInErrorMessage() {
        assertThatThrownBy(() -> new JwtConfig().jwtKey(
                new JwtProperties("secret-not-base64!", "documind-ai", "documind-api", 900)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET harus berformat Base64.");
    }
}
