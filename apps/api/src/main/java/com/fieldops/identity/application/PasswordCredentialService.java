package com.fieldops.identity.application;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.fieldops.identity.domain.PasswordCredential;
import com.fieldops.identity.infrastructure.PasswordCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trusted internal provisioning only, not public signup or password reset. */
@Service
public class PasswordCredentialService {
    private final PasswordCredentialRepository credentials;
    private final PasswordEncoder encoder;

    public PasswordCredentialService(PasswordCredentialRepository credentials, PasswordEncoder encoder) {
        this.credentials = credentials;
        this.encoder = encoder;
    }

    @Transactional
    public void provision(UUID userId, String password) {
        Objects.requireNonNull(userId, "User ID is required");
        // Never trim or truncate a password; BCrypt accepts at most 72 bytes.
        if (password == null || password.codePointCount(0, password.length()) < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must contain at least 15 characters and at most 72 UTF-8 bytes");
        }
        // A null version forces an INSERT for this assigned ID. Calling this
        // use case again cannot overwrite an existing credential.
        credentials.saveAndFlush(new PasswordCredential(userId, encoder.encode(password)));
    }
}
