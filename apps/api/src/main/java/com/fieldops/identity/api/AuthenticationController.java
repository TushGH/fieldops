package com.fieldops.identity.api;

import java.util.UUID;

import com.fieldops.identity.application.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final UserService users;

    public AuthenticationController(UserService users) {
        this.users = users;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(Authentication authentication) {
        return users.findById(UUID.fromString(authentication.getName()))
                .map(user -> ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(new CurrentUserResponse(user.id(), user.displayName(), user.email())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    public record CsrfResponse(String headerName, String token) { }
    public record CurrentUserResponse(UUID id, String displayName, String email) { }
}
