package cn.edu.scuec.icpc.lab.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public List<RoleService.RoleView> list() {
        return roleService.list();
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('role:read')")
    public List<RoleService.AssignmentView> assignments(@RequestParam UUID personId) {
        return roleService.assignments(personId);
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('role:assign')")
    public RoleService.AssignmentView assign(@RequestBody AssignRequest request) {
        return roleService.assign(request.personId(), request.roleCode(), request.scopeType(),
                request.scopeId(), request.reason());
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize("hasAuthority('role:assign')")
    public void revoke(@PathVariable UUID id, @RequestParam(required = false) String reason) {
        roleService.revoke(id, reason);
    }

    public record AssignRequest(UUID personId, String roleCode, String scopeType, UUID scopeId, String reason) {
    }
}