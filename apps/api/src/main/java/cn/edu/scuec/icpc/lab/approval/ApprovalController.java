package cn.edu.scuec.icpc.lab.approval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/todo")
    @PreAuthorize("hasAuthority('approval:read')")
    public List<ApprovalService.TodoView> todo() {
        return approvalService.todo();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApprovalService.ApprovalDetail detail(@PathVariable UUID id) {
        return approvalService.detail(id);
    }
}