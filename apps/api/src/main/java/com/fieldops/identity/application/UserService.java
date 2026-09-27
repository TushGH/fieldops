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

    public UserService(UserRepository users, Validator validator) {
        this.users = users;
        this.validator = validator;
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
