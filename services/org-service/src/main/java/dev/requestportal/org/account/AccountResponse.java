package dev.requestportal.org.account;

import dev.requestportal.org.department.Department;
import dev.requestportal.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String email,
        String fullName,
        List<Role> roles,
        DepartmentSummary department,
        Instant lastSeenAt,
        Instant createdAt) {

    public record DepartmentSummary(Long id, String code, String name) {
    }

    static AccountResponse from(Account account) {
        Department department = account.getDepartment();
        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                account.getRoles().stream().sorted().toList(),
                department == null
                        ? null
                        : new DepartmentSummary(department.getId(), department.getCode(), department.getName()),
                account.getLastSeenAt(),
                account.getCreatedAt());
    }
}
