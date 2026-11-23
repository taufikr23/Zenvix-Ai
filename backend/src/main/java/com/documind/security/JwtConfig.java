package com.documind.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    @Bean
    public SecretKey jwtKey(JwtProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET harus berformat Base64.");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET harus berisi minimal 32 byte acak.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtKey, JwtProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>("aud",
                        value -> value != null && value.contains(properties.audience())),
                new JwtClaimValidator<Instant>("exp", value -> value != null),
                new JwtClaimValidator<Instant>("iat", value -> value != null),
                new JwtClaimValidator<String>("sub", value -> value != null && !value.isBlank())));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
