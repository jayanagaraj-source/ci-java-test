package com.example.taskapi.auth;

import com.example.taskapi.auth.dto.AuthResponse;
import com.example.taskapi.auth.dto.LoginRequest;
import com.example.taskapi.auth.dto.RegisterRequest;
import com.example.taskapi.common.error.ApiException;
import com.example.taskapi.users.User;
import com.example.taskapi.users.UserService;
import com.example.taskapi.users.dto.UserResponse;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Auth has no table of its own: it persists through the users feature
 * ({@link UserService} / UserRepository).
 */
@Service
class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /**
     * Hash compared against when the email is unknown, so that path costs one
     * BCrypt verification exactly like a wrong password does.
     */
    private final String timingEqualizationHash;

    AuthService(UserService users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    AuthResponse register(RegisterRequest request) {
        // Hash outside the DB transaction: BCrypt is deliberately slow.
        String hash = passwordEncoder.encode(request.password());
        return tokenFor(users.register(request.email(), hash));
    }

    AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(request.email());
        String hash = user.map(User::getPasswordHash).orElse(timingEqualizationHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            log.atWarn().log("login failed");
            throw ApiException.unauthorized("invalid-credentials", "Invalid email or password");
        }
        return tokenFor(user.get());
    }

    UserResponse me(CurrentUser currentUser) {
        return users.get(currentUser.id());
    }

    private AuthResponse tokenFor(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }
}
