package com.fieldops.identity.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fieldops.identity.domain.User;
import com.fieldops.identity.infrastructure.UserRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal user persistence, not registration, authentication, or an authorized user directory. */
@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final Validator validator;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public UserService(UserRepository users, Validator validator, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.users = users;
        this.validator = validator;
    }

    public boolean isVerifiedActive(UUID id) {
        return users.existsByIdAndStatusAndEmailVerifiedAtIsNotNull(id, com.fieldops.identity.domain.UserStatus.ACTIVE);
    }

    /** Coordinates platform grant consumption with a concurrent global account disable. */
    @Transactional
    public boolean lockVerifiedActive(UUID id) {
        return !jdbc.query("SELECT id FROM users WHERE id = ? AND status = 'ACTIVE' AND email_verified_at IS NOT NULL FOR SHARE",
                (rs, row) -> rs.getObject(1, UUID.class), id).isEmpty();
    }

    @Transactional
    public UserDetails create(String displayName, String email) {
        var user = User.create(displayName, email);
        var violations = validator.validate(user);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        // Uniqueness is database-enforced. Never automatically link or merge by email.
        return UserDetails.from(users.saveAndFlush(user));
    }

    public Optional<UserDetails> findById(UUID id) {
        return users.findById(Objects.requireNonNull(id, "User ID is required")).map(UserDetails::from);
    }
}
