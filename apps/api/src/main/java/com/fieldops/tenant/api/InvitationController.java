package com.fieldops.tenant.api;

import java.util.List;
import java.util.UUID;
import com.fieldops.identity.api.RegistrationController.Token;
import com.fieldops.tenant.application.InvitationService;
import com.fieldops.tenant.domain.MembershipRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InvitationController {
    private final InvitationService service;
    public InvitationController(InvitationService service) { this.service = service; }
    @PostMapping("/invitations/resolve")
    public Reference resolve(@Valid @RequestBody Token request) { return new Reference(service.resolve(request.token())); }
    @GetMapping("/invitations")
    public List<InvitationService.Invitation> inbox() { return service.inbox(); }
    @GetMapping("/invitations/{id}")
    public InvitationService.Invitation get(@PathVariable UUID id) { return service.recipientInvitation(id); }
    @PostMapping("/invitations/{id}/accept")
    public InvitationService.Acceptance accept(@PathVariable UUID id) { return service.accept(id); }
    @GetMapping("/tenant/invitations")
    public List<InvitationService.Invitation> list() { return service.listForTenant(); }
    @PostMapping("/tenant/invitations")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public InvitationService.Invitation invite(@Valid @RequestBody Invite request) { return service.invite(request.email(), request.role()); }
    @PostMapping("/tenant/invitations/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) { service.revoke(id); return ResponseEntity.noContent().build(); }
    @PostMapping("/tenant/invitations/{id}/resend")
    public InvitationService.Invitation resend(@PathVariable UUID id) { return service.resend(id); }
    public record Invite(@NotBlank @Email @Size(max = 254) String email, @NotNull MembershipRole role) { }
    public record Reference(UUID id) { }
}
