package com.fieldops.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.fieldops.identity.domain.User;
import org.springframework.data.repository.Repository;

public interface UserRepository extends Repository<User, UUID> {
    boolean existsByIdAndStatusAndEmailVerifiedAtIsNotNull(UUID id, com.fieldops.identity.domain.UserStatus status);
    User saveAndFlush(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
}
