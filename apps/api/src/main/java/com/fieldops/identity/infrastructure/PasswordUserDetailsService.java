package com.fieldops.identity.infrastructure;

import java.util.List;

import com.fieldops.identity.domain.User;
import com.fieldops.identity.domain.UserStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordCredentialRepository credentials;

    public PasswordUserDetailsService(UserRepository users, PasswordCredentialRepository credentials) {
        this.users = users;
        this.credentials = credentials;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        final String canonicalEmail;
        try {
            canonicalEmail = User.normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var user = users.findByEmail(canonicalEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var credential = credentials.findById(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        // Store stable identity in the session, with no tenant or role claims.
        return org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
                .password(credential.getPasswordHash())
                .disabled(user.getStatus() != UserStatus.ACTIVE)
                .authorities(List.of()).build();
    }
}
