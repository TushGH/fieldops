package com.fieldops.tenant.api;

import java.util.List;
import com.fieldops.tenant.application.BusinessOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BusinessController {
    private final BusinessOnboardingService service;
    public BusinessController(BusinessOnboardingService service) { this.service = service; }
    @GetMapping("/businesses")
    public List<BusinessOnboardingService.Business> list() { return service.list(); }
    @PostMapping("/businesses")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public BusinessOnboardingService.Business create(@Valid @RequestBody CreateBusiness request) {
        return service.create(request.name(), request.slug());
    }
    @GetMapping("/tenant/onboarding")
    public BusinessOnboardingService.Readiness readiness() { return service.readiness(); }
    @PostMapping("/tenant/onboarding/business/complete")
    public BusinessOnboardingService.Readiness complete() { return service.complete(); }
    public record CreateBusiness(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 63) String slug) { }
}
