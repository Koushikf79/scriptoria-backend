package com.scriptoria.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserProfileResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private LocalDateTime createdAt;
    private int totalAnalyses;
}
