package com.fieldops.tenant.api;

import com.fieldops.tenant.application.BusinessOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusinessOnboardingController {
    private final BusinessOnboardingService onboarding;

    public BusinessOnboardingController(BusinessOnboardingService onboarding) { this.onboarding = onboarding; }

    @PostMapping("/api/v1/onboarding")
    public ResponseEntity<Void> onboard(@Valid @RequestBody OnboardingRequest request) {
        onboarding.onboard(request.businessName(), request.slug(), request.ownerName(), request.email(), request.password());
        return ResponseEntity.status(201).header("Cache-Control", "no-store").build();
    }

    public record OnboardingRequest(@NotBlank @Size(max = 200) String businessName,
                                    @NotBlank @Size(max = 63) String slug,
                                    @NotBlank @Size(max = 200) String ownerName,
                                    @NotBlank @Size(max = 254) String email,
                                    @NotBlank @Size(max = 72) String password) {
        // Prevent accidental credential disclosure when a request object is logged.
        @Override public String toString() { return "OnboardingRequest[redacted]"; }
    }
}
