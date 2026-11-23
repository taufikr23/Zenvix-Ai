package com.documind.security;

import com.documind.auth.TokenResponse;
import com.documind.auth.UserResponse;
import com.documind.user.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public TokenResponse issue(UserAccount user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenSeconds()))
                .id(UUID.randomUUID().toString())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenSeconds(), UserResponse.from(user));
    }
}
