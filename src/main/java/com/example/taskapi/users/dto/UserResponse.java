package com.example.taskapi.users.dto;

import com.example.taskapi.users.Role;
import com.example.taskapi.users.User;
import java.time.Instant;

public record UserResponse(long id, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
