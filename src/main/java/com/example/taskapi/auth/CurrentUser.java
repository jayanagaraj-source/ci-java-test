package com.example.taskapi.auth;

import com.example.taskapi.config.SecurityConfig;
import com.example.taskapi.users.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, derived from a validated JWT. */
public record CurrentUser(long id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(Long.parseLong(jwt.getSubject()),
                Role.valueOf(jwt.getClaimAsString(SecurityConfig.ROLE_CLAIM)));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
