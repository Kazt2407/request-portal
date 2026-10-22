package dev.requestportal.org.account;

import dev.requestportal.org.department.Department;
import dev.requestportal.security.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Local copy of a Keycloak user. Rows are created on first sign-in (see {@link AccountRepository#insertIfAbsent})
 * and refreshed from the access token on every sign-in; admin changes are written to Keycloak first.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ElementCollection
    @CollectionTable(name = "account_role", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "search_text", insertable = false, updatable = false)
    private String searchText;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Account() {
    }

    void syncFromIdentity(String email, String fullName, Set<Role> roles, Department department, Instant seenAt) {
        this.email = email;
        this.fullName = fullName;
        this.department = department;
        this.lastSeenAt = seenAt;
        replaceRoles(roles);
    }

    void changeAccess(Set<Role> roles, Department department) {
        this.department = department;
        replaceRoles(roles);
    }

    private void replaceRoles(Set<Role> newRoles) {
        roles.retainAll(newRoles);
        roles.addAll(newRoles);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Department getDepartment() {
        return department;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
