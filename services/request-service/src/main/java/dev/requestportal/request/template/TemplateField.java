package dev.requestportal.request.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "template_field")
public class TemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private RequestTemplate template;

    @Column(nullable = false)
    private int position;

    @Column(name = "field_key", nullable = false, length = 50)
    private String key;

    @Column(nullable = false, length = 200)
    private String label;

    private String description;

    @Column(length = 200)
    private String hint;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private FieldType type;

    @Column(nullable = false)
    private boolean required;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> options = new ArrayList<>();

    @Column(name = "upload_folder", length = 120)
    private String uploadFolder;

    @Column(name = "upload_note")
    private String uploadNote;

    protected TemplateField() {
    }

    TemplateField(RequestTemplate template, int position, TemplateRequest.FieldDefinition definition) {
        this.template = template;
        this.position = position;
        this.key = definition.key();
        this.label = definition.label().strip();
        this.description = definition.description();
        this.hint = definition.hint();
        this.type = definition.type();
        this.required = definition.required();
        this.options = type == FieldType.SELECT
                ? definition.optionsOrEmpty().stream().map(String::strip).toList()
                : List.of();
        this.uploadFolder = type.holdsFiles() ? definition.uploadFolder() : null;
        this.uploadNote = type.holdsFiles() ? definition.uploadNote() : null;
    }

    public int getPosition() {
        return position;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getHint() {
        return hint;
    }

    public FieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getOptions() {
        return List.copyOf(options);
    }

    public String getUploadFolder() {
        return uploadFolder;
    }

    public String getUploadNote() {
        return uploadNote;
    }
}
