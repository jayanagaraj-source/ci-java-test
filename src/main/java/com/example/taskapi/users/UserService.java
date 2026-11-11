package com.example.taskapi.users;

import com.example.taskapi.common.error.ApiException;
import com.example.taskapi.common.web.PageResponse;
import com.example.taskapi.users.dto.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Creates a user. The first user ever registered becomes ADMIN, everyone else USER. */
    @Transactional
    public User register(String email, String passwordHash) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("email-taken", "An account with this email already exists");
        }
        Role role = users.claimAdminRole() == 1 ? Role.ADMIN : Role.USER;
        try {
            User user = users.saveAndFlush(new User(normalized, passwordHash, role, Instant.now(clock)));
            log.atInfo().addKeyValue("user.id", user.getId()).addKeyValue("user.role", role).log("user registered");
            return user;
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration of the same email.
            throw ApiException.conflict("email-taken", "An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return users.findByEmail(normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int limit) {
        PageRequest request = PageRequest.of(page - 1, limit, Sort.by(Sort.Order.asc("id")));
        return PageResponse.of(users.findAll(request).map(UserResponse::from));
    }
}
