package com.example.taskapi.users;

import com.example.taskapi.common.web.PageResponse;
import com.example.taskapi.users.dto.UserResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return users.list(page, limit);
    }
}
