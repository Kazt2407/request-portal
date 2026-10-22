package dev.requestportal.org.account;

import dev.requestportal.org.department.Department;
import dev.requestportal.org.department.DepartmentRepository;
import dev.requestportal.org.web.BusinessRuleException;
import dev.requestportal.org.web.NotFoundException;
import dev.requestportal.org.web.PageResponse;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final DepartmentRepository departments;
    private final IdentityDirectory identityDirectory;

    public AccountService(
            AccountRepository accounts, DepartmentRepository departments, IdentityDirectory identityDirectory) {
        this.accounts = accounts;
        this.departments = departments;
        this.identityDirectory = identityDirectory;
    }

    /** Creates the account on first sign-in, then refreshes it from the token's claims. */
    @Transactional
    public AccountResponse syncSignedInUser(CurrentUser user) {
        accounts.insertIfAbsent(user.id(), user.email(), user.fullName());
        Account account = find(user.id());
        account.syncFromIdentity(
                user.email(), user.fullName(), user.roles(), departmentByCode(user.departmentCode()), Instant.now());
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> search(String text, Role role, Long departmentId, Pageable pageable) {
        return PageResponse.of(
                accounts.findAll(AccountSpecifications.matching(text, role, departmentId), pageable),
                AccountResponse::from);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return AccountResponse.from(find(id));
    }

    /**
     * Writes the new access to Keycloak first, then to the local copy. If the local update fails, the next
     * sign-in re-syncs it from the token, so Keycloak and this table cannot drift apart for long.
     */
    @Transactional
    public AccountResponse updateAccess(UUID id, UpdateAccessRequest request, CurrentUser actor) {
        Account account = find(id);
        Set<Role> roles = EnumSet.copyOf(request.roles());
        Department department = request.departmentId() == null
                ? null
                : departments.findById(request.departmentId()).orElseThrow(() -> new BusinessRuleException(
                        "Department " + request.departmentId() + " does not exist"));

        if (roles.contains(Role.STAFF) && department == null) {
            throw new BusinessRuleException("Staff accounts must belong to a department");
        }
        if (actor.id().equals(id) && !roles.contains(Role.ADMIN)) {
            throw new BusinessRuleException("You cannot remove your own ADMIN role");
        }

        identityDirectory.updateAccess(id, roles, department == null ? null : department.getCode());
        account.changeAccess(roles, department);
        return AccountResponse.from(account);
    }

    private Account find(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    private Department departmentByCode(String code) {
        if (code == null) {
            return null;
        }
        return departments.findByCode(code).orElseGet(() -> {
            log.warn("Token has unknown department code {}; leaving the account without a department", code);
            return null;
        });
    }
}
