package cn.edu.scuec.icpc.lab.identity;

import cn.edu.scuec.icpc.lab.common.PageResponse;
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
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('account:read')")
    public PageResponse<AccountService.AccountView> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accountService.list(query, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('account:write')")
    public AccountService.AccountView create(@RequestBody AccountService.CreateAccountRequest request) {
        return accountService.create(request);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('account:write')")
    public AccountService.AccountView disable(@PathVariable UUID id,
                                              @RequestBody(required = false) ReasonRequest request) {
        return accountService.disable(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('account:write')")
    public AccountService.AccountView enable(@PathVariable UUID id,
                                             @RequestBody(required = false) ReasonRequest request) {
        return accountService.enable(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('account:reset')")
    public void resetPassword(@PathVariable UUID id, @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(id, request.newPassword(), request.reason());
    }

    public record ReasonRequest(String reason) {
    }

    public record ResetPasswordRequest(String newPassword, String reason) {
    }
}