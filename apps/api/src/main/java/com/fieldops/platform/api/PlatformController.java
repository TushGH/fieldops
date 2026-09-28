package com.fieldops.platform.api;

import java.util.UUID;
import com.fieldops.platform.application.PlatformAccess;
import com.fieldops.tenant.application.InvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final PlatformAccess access;
    private final InvitationService invitations;
    public PlatformController(PlatformAccess access, InvitationService invitations) { this.access = access; this.invitations = invitations; }
    @GetMapping("/access")
    public Access check() { access.require(); return new Access(true); }
    @PostMapping("/businesses")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public InvitationService.Invitation create(@Valid @RequestBody Provision request) {
        return invitations.provision(request.name(), request.slug(), request.email());
    }
    @PostMapping("/businesses/{id}/owner-invitation")
    public InvitationService.Invitation reissue(@PathVariable UUID id, @Valid @RequestBody Owner request) {
        return invitations.reissueOwner(id, request.email());
    }
    public record Access(boolean platformAdmin) { }
    public record Provision(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 63) String slug,
                            @NotBlank @Email @Size(max = 254) String email) { }
    public record Owner(@NotBlank @Email @Size(max = 254) String email) { }
}
