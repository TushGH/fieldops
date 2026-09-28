package com.fieldops.identity.api;

import com.fieldops.identity.application.RegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {
    private final RegistrationService service;
    public RegistrationController(RegistrationService service) { this.service = service; }
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody Signup request) {
        service.requestRegistration(request.displayName(), request.email());
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/signup/complete")
    public ResponseEntity<Void> complete(@Valid @RequestBody Complete request) {
        service.completeRegistration(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/email-verification/request")
    public ResponseEntity<Void> requestVerification() {
        service.requestVerification();
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody Token request) {
        service.confirmVerification(request.token());
        return ResponseEntity.noContent().build();
    }
    public record Signup(@NotBlank @Size(max = 200) String displayName, @NotBlank @Email @Size(max = 254) String email) { }
    public record Complete(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "Complete[redacted]"; }
    }
    public record Token(@NotBlank @Size(max = 100) String token) {
        @Override public String toString() { return "Token[redacted]"; }
    }
}
