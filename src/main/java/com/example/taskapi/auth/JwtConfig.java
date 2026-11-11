package com.example.taskapi.auth;

import com.example.taskapi.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** HS256 JWT signing and verification. Startup fails without a strong JWT_SECRET. */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    static final int MIN_SECRET_LENGTH = 32;

    @Bean
    SecretKey jwtSigningKey(AppProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
            // Never include the value itself in the message.
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_LENGTH + " characters long");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }
}
