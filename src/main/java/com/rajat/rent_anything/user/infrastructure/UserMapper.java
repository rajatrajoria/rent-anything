package com.rajat.rent_anything.user.infrastructure;

import com.rajat.rent_anything.user.domain.User;

public class UserMapper {
    public static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setName(user.getName());
        entity.setEmail(user.getEmail());
        entity.setPassword(user.getPassword());
        entity.setMobileNumber(user.getMobileNumber());
        entity.setRole(user.getRole());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        entity.setVerified(user.isVerified());
        entity.setTrustStatus(user.getTrustStatus());
        return entity;
    }

    public static User toDomain(UserEntity entity) {
        return User.rehydrate(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getMobileNumber(),
                entity.isVerified(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getTrustStatus()
        );
    }
}
