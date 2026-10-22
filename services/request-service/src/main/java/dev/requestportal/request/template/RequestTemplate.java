package dev.requestportal.request.template;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "request_template")
public class RequestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Column(name = "department_code", nullable = false, length = 16)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Audience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TemplateStatus status;

    @Column(name = "show_processing_time", nullable = false)
    private boolean showProcessingTime;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<TemplateField> fields = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<TemplateStep> steps = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected RequestTemplate() {
    }

    public RequestTemplate(TemplateRequest definition, Instant now) {
        this.createdAt = now;
        apply(definition, now);
    }

    /** Replaces the whole definition. Requests already submitted keep their own copy of fields and steps. */
    void apply(TemplateRequest definition, Instant now) {
        title = definition.title().strip();
        description = definition.description();
        departmentCode = definition.departmentCode();
        audience = definition.audience();
        status = definition.status();
        showProcessingTime = definition.showProcessingTime();
        fields.clear();
        for (int i = 0; i < definition.fields().size(); i++) {
            fields.add(new TemplateField(this, i + 1, definition.fields().get(i)));
        }
        steps.clear();
        for (int i = 0; i < definition.steps().size(); i++) {
            steps.add(new TemplateStep(this, i + 1, definition.steps().get(i)));
        }
        // Always dirty the root, so the version increments even when only fields or steps changed.
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public Audience getAudience() {
        return audience;
    }

    public TemplateStatus getStatus() {
        return status;
    }

    public boolean isShowProcessingTime() {
        return showProcessingTime;
    }

    public List<TemplateField> getFields() {
        return List.copyOf(fields);
    }

    public List<TemplateStep> getSteps() {
        return List.copyOf(steps);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
