package com.example.taskapi.auth;

import com.example.taskapi.config.AppProperties;
import com.example.taskapi.config.SecurityConfig;
import com.example.taskapi.users.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class JwtService {

    record IssuedToken(String value, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    JwtService(JwtEncoder encoder, Clock clock, AppProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.ttl = properties.jwt().ttl();
    }

    IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(SecurityConfig.ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }
}
