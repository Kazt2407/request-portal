package dev.requestportal.org.account;

import dev.requestportal.web.PageResponse;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** The signed-in user's account. The web app calls this right after sign-in. */
    @GetMapping("/me")
    AccountResponse me(CurrentUser user) {
        return accountService.syncSignedInUser(user);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    PageResponse<AccountResponse> search(
            @RequestParam(name = "q", required = false) String text,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Long departmentId,
            @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return accountService.search(text, role, departmentId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @PutMapping("/{id}/access")
    @PreAuthorize("hasRole('ADMIN')")
    AccountResponse updateAccess(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccessRequest request, CurrentUser actor) {
        return accountService.updateAccess(id, request, actor);
    }
}
