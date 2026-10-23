package dev.requestportal.document.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Who may see a request's files; a local copy kept from workflow events. */
@Entity
@Table(name = "request_access")
public class RequestAccess {

    @Id
    @Column(name = "request_id")
    private long requestId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "request_code", length = 32)
    private String requestCode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> departments = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RequestAccess() {
    }

    RequestAccess(long requestId, String requestCode, UUID requesterId, List<String> departments, Instant now) {
        this.requestId = requestId;
        this.requestCode = requestCode;
        this.requesterId = requesterId;
        this.departments = new ArrayList<>(departments);
        this.updatedAt = now;
    }

    void addDepartment(String departmentCode, Instant now) {
        if (!departments.contains(departmentCode)) {
            departments = new ArrayList<>(departments);
            departments.add(departmentCode);
            updatedAt = now;
        }
    }

    public long getRequestId() {
        return requestId;
    }

    /** The request's code, e.g. ACAD-0042, or a stand-in for requests seen before codes were recorded. */
    public String getRequestCode() {
        return requestCode != null ? requestCode : "request-" + requestId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public List<String> getDepartments() {
        return List.copyOf(departments);
    }
}
