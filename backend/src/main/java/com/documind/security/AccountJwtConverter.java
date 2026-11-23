package com.documind.security;

import com.documind.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Periksa akun terbaru dari database, bukan mempercayai role kiriman client. */
@Component
public class AccountJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;
    public AccountJwtConverter(UserRepository users) { this.users = users; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try { userId = UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw invalidAccount(); }
        var user = users.findById(userId).filter(account -> account.enabled())
                .orElseThrow(this::invalidAccount);
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role())), user.id().toString());
    }

    private OAuth2AuthenticationException invalidAccount() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),
                "Token atau akun tidak valid.");
    }
}
