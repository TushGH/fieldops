package com.fieldops.tenant.api;

import java.util.List;
import java.util.UUID;

import com.fieldops.tenant.application.BusinessDirectory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BusinessDirectoryController {
    private final BusinessDirectory businesses;

    public BusinessDirectoryController(BusinessDirectory businesses) { this.businesses = businesses; }

    @GetMapping("/api/v1/businesses")
    public ResponseEntity<List<BusinessDirectory.Business>> mine(Authentication authentication) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(businesses.forUser(UUID.fromString(authentication.getName())));
    }
}
