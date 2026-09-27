package com.fieldops.tenant.api;

import java.util.UUID;

import com.fieldops.tenant.application.MembershipDetails;
import com.fieldops.tenant.application.TenantDetails;
import com.fieldops.tenant.application.TenantWorkspaceService;
import com.fieldops.tenant.domain.MembershipRole;
import com.fieldops.tenant.infrastructure.AuthenticatedTenantContext.Selection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantWorkspaceController {
    private final TenantWorkspaceService workspace;

    public TenantWorkspaceController(TenantWorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/context")
    public Selection context() { return workspace.currentContext(); }

    @GetMapping
    public TenantDetails currentTenant() { return workspace.currentTenant(); }

    @PatchMapping
    public TenantDetails rename(@Valid @RequestBody RenameTenant request) { return workspace.rename(request.name()); }

    @GetMapping("/memberships")
    public TenantWorkspaceService.MembershipPage memberships(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return workspace.listMemberships(page, size);
    }

    @GetMapping("/memberships/{id}")
    public MembershipDetails membership(@PathVariable UUID id) { return workspace.membership(id); }

    @PutMapping("/memberships/{id}/role")
    public MembershipDetails changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRole request) {
        return workspace.changeRole(id, request.role());
    }

    @PostMapping("/memberships/{id}/deactivate")
    public MembershipDetails deactivate(@PathVariable UUID id) { return workspace.deactivate(id); }

    @PostMapping("/memberships/{id}/reactivate")
    public MembershipDetails reactivate(@PathVariable UUID id) { return workspace.reactivate(id); }

    public record RenameTenant(@NotBlank @Size(max = 200) String name) { }
    public record ChangeRole(@NotNull MembershipRole role) { }
}
