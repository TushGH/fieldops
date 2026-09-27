package com.fieldops.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.fieldops.identity.domain.PasswordCredential;
import org.springframework.data.repository.Repository;

public interface PasswordCredentialRepository extends Repository<PasswordCredential, UUID> {
    PasswordCredential saveAndFlush(PasswordCredential credential);
    Optional<PasswordCredential> findById(UUID userId);
}
