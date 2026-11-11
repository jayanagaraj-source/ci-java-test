package com.example.taskapi.auth.dto;

import com.example.taskapi.users.dto.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
