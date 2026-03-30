package com.scriptoria.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
    private UUID userId;
    private long expiresIn;
}
