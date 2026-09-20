package com.kabadiwala.backend.user;

import com.kabadiwala.backend.auth.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        UserRole role,
        String status,
        Instant createdAt
) {
    public static UserDto fromEntity(UserProfile entity) {
        if (entity == null) return null;
        return new UserDto(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getPhoneNumber(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
