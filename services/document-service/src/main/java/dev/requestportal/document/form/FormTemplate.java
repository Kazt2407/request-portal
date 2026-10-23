package dev.requestportal.document.form;

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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "form_template")
public class FormTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FormAudience audience;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "template_file_id", nullable = false)
    private UUID templateFileId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> placeholders = new ArrayList<>();

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<FormField> fields = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected FormTemplate() {
    }

    FormTemplate(FormViews.FormRequest request, List<String> placeholders, Instant now) {
        this.createdAt = now;
        apply(request, placeholders, now);
    }

    void apply(FormViews.FormRequest request, List<String> placeholders, Instant now) {
        title = request.title().strip();
        description = request.description();
        audience = request.audience();
        active = request.active();
        templateFileId = request.templateFileId();
        this.placeholders = new ArrayList<>(placeholders);
        fields.clear();
        for (int i = 0; i < request.fields().size(); i++) {
            fields.add(new FormField(this, i + 1, request.fields().get(i)));
        }
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

    public FormAudience getAudience() {
        return audience;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getTemplateFileId() {
        return templateFileId;
    }

    public List<String> getPlaceholders() {
        return List.copyOf(placeholders);
    }

    public List<FormField> getFields() {
        return List.copyOf(fields);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
